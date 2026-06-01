package dev.salocin.simpleflipper.core;

import dev.salocin.simpleflipper.SimpleFlipper;
import dev.salocin.simpleflipper.api.FlipMonitor;
import dev.salocin.simpleflipper.click.ClickEngine;
import dev.salocin.simpleflipper.click.Menu;
import dev.salocin.simpleflipper.config.Config;
import dev.salocin.simpleflipper.flip.Actions;
import dev.salocin.simpleflipper.flip.BazaarMenus;
import dev.salocin.simpleflipper.flip.FlipSequences;
import dev.salocin.simpleflipper.flip.OrderBook;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Continuous SINGLE-item flipper, hardcoded to Enchanted Hard Stone. Keeps one competitive
 * (top-of-book) buy order alive, drains filled goods to the Builder, and replaces the order when
 * it's outbid — forever, until the user pauses / kills / finalizes.
 *
 * <p>Built from small, focused pieces: Actions, FlipSequences, OrderBook, FlipMonitor, ClickEngine,
 * OrderSizer, driven by the state loop below.
 *
 * <p><b>Outbid handling — place-new-before-clearing-old (max 2 orders):</b> when our order is
 * outbid we PLACE a fresh top-of-book order first (so a live order always catches fills — no gap),
 * bringing us to 2 orders; the older (lower-priced) one is then drained to zero and cancelled. We
 * only place a replacement when there's exactly one order, so the count never exceeds 2 — well under
 * Hypixel's cap. (Hypixel blocks Cancel Order while goods remain to claim, so the old order is
 * always drained to claimable=0 before cancelling.)
 *
 * <p>Each pass reads the orders from one Manage Orders menu, performs ONE action (claim / place /
 * cancel), then sells to the Builder, then reopens — the menu is a static snapshot, so it
 * closes+reopens (random 2-6s) for fresh fill data. NEVER self-stops; failures retry.
 */
public final class Flipper {
    private Flipper() {}

    /** The one and only item this mod flips. */
    public static final String ITEM = "Enchanted Hard Stone";

    private enum State {
        IDLE,
        REFILL,         // idle pause before reopening orders for a fresh snapshot
        OPEN_ORDERS,    // send /managebazaarorders
        WAIT_ORDERS,    // wait for the orders menu
        READ,           // read rows, decide the next single action
        CLAIM_FILL,     // wait for a claim to deliver goods to inventory
        SELL_OPEN,      // open the Builder
        SELL_WAIT,      // wait for the Builder menu
        SELL_DRAIN,     // sell all matching items in inventory
        PLACE_OPEN,     // close orders, send /bz <item>, arm the place sequence
        PLACE_WAIT,     // wait for a place-order sequence
        CANCEL_WAIT     // wait for a cancel-order sequence
    }

    private static volatile State state = State.IDLE;
    private static volatile boolean paused = false;
    private static volatile boolean finishing = false;   // graceful wind-down: claim all, cancel, stop

    private static int waitTicks;
    private static int settleTicks;
    private static int graceTicks;
    private static long claimBaseline;   // inventory count of ITEM at claim time
    private static int cycles;
    private static long totalSold;
    private static volatile Boolean seqResult;   // place/cancel sequence result (null = pending)

    // Snapshot of the current (top-priced) order, for the HUD + logging.
    private static volatile int orderCount;
    private static volatile long curFilled, curTotal, curClaimable;

    private static final Random RANDOM = new Random();

    // ---------------- public API ----------------

    public static boolean isRunning()  { return state != State.IDLE; }
    public static boolean isPaused()   { return paused; }
    public static boolean isFinishing(){ return finishing; }
    public static int cycles()         { return cycles; }
    public static long totalSold()     { return totalSold; }
    public static int orderCount()     { return orderCount; }
    public static long filled()        { return curFilled; }
    public static long total()         { return curTotal; }
    public static long claimable()     { return curClaimable; }

    /** Short human label for the current state (for the HUD). */
    public static String stateLabel() {
        if (state == State.IDLE) return paused ? "paused" : "idle";
        if (finishing) return "finishing";
        return "running";
    }

    /** Start (or resume from pause) flipping. Places an order itself if none exists. */
    public static boolean start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (isRunning() && !paused) return false;
        paused = false;
        finishing = false;
        cycles = 0;
        totalSold = 0;
        ClickEngine.enabled = true;
        SimpleFlipper.API.start(Config.get().apiPollSeconds);   // needed for sizing + outbid
        state = State.OPEN_ORDERS;
        waitTicks = 0;
        notify("§astarted §7— flipping §e" + ITEM);
        SimpleFlipper.LOGGER.info("[flip] started for {}", ITEM);
        return true;
    }

    /** Pause: freeze the loop (no clicks), keep state, so start() resumes. */
    public static void pause() {
        if (state == State.IDLE) return;
        paused = true;
        ClickEngine.killSwitch();
        state = State.IDLE;
        notify("§epaused §7— press = to resume");
        SimpleFlipper.LOGGER.info("[flip] paused (cycles={}, ~sold={})", cycles, totalSold);
    }

    /** Hard stop / kill. */
    public static void kill(String reason) {
        if (state == State.IDLE && !paused) return;
        SimpleFlipper.LOGGER.info("[flip] killed: {} (cycles={}, ~sold={})", reason, cycles, totalSold);
        notify("§cstopped §7(" + reason + ")");
        state = State.IDLE;
        paused = false;
        finishing = false;
        orderCount = 0; curFilled = 0; curTotal = 0; curClaimable = 0;
        ClickEngine.enabled = false;
    }

    /**
     * Graceful wind-down ("-" key): stop placing/replacing, claim out everything, cancel every
     * order, sell the leftovers to the Builder, then stop. Idempotent.
     */
    public static void finish() {
        if (state == State.IDLE) { notify("§7not running"); return; }
        if (finishing) { notify("§7already finishing…"); return; }
        finishing = true;
        notify("§efinishing §7— claiming all + cancelling order, then stop");
        SimpleFlipper.LOGGER.info("[flip] finish requested (cycles={}, ~sold={})", cycles, totalSold);
    }

    // ---------------- tick state machine ----------------

    public static void tick(Minecraft mc) {
        if (state == State.IDLE) return;
        if (mc.player == null) { kill("no player"); return; }
        switch (state) {
            case REFILL      -> refill(mc);
            case OPEN_ORDERS -> openOrders(mc);
            case WAIT_ORDERS -> waitOrders(mc);
            case READ        -> read(mc);
            case CLAIM_FILL  -> claimFill(mc);
            case SELL_OPEN   -> sellOpen(mc);
            case SELL_WAIT   -> sellWait(mc);
            case SELL_DRAIN  -> sellDrain(mc);
            case PLACE_OPEN  -> placeOpen(mc);
            case PLACE_WAIT  -> placeWait(mc);
            case CANCEL_WAIT -> cancelWait(mc);
            default -> {}
        }
    }

    private static void refill(Minecraft mc) {
        if (mc.screen != null) closeScreen(mc);
        if (--waitTicks > 0) return;
        state = State.OPEN_ORDERS;
        waitTicks = 0;
    }

    // ---- orders menu ----

    private static void openOrders(Minecraft mc) {
        if (mc.screen != null) { closeScreen(mc); waitTicks = 2 + jitter(); return; }
        if (waitTicks > 0) { waitTicks--; return; }
        Actions.sendCommand("managebazaarorders");
        waitTicks = Config.get().menuWaitTimeoutTicks;
        state = State.WAIT_ORDERS;
    }

    private static void waitOrders(Minecraft mc) {
        if (OrderBook.isOpen()) {
            settleTicks = Config.get().claimSettleTicks + jitter();
            graceTicks = Config.get().claimGraceTicks;
            state = State.READ;
            return;
        }
        if (--waitTicks <= 0) state = State.OPEN_ORDERS;   // never give up; reopen
    }

    /**
     * Read our rows, then pick ONE action this menu-open in priority order:
     * (A) clear a stale (old, lower-priced) order — drain then cancel;
     * (B) claim a worthwhile batch from the current (top) order;
     * (C) place an order if we have none;
     * (D) replace-on-outbid: exactly one order and it's outbid → place a 2nd top order;
     * else nothing → close, wait, reopen.
     */
    private static void read(Minecraft mc) {
        if (!OrderBook.isOpen()) { state = State.OPEN_ORDERS; return; }
        if (settleTicks > 0) { settleTicks--; return; }

        List<OrderBook.Order> rows = OrderBook.read();
        boolean menuLoaded = !rows.isEmpty();
        if (!menuLoaded && graceTicks > 0) { graceTicks--; return; }   // let rows load

        Config cfg = Config.get();
        List<OrderBook.Order> mine = mineSorted(rows);
        orderCount = mine.size();
        if (!mine.isEmpty()) {
            OrderBook.Order cur = mine.get(0);
            curFilled = cur.filled();
            curTotal = cur.total();
            curClaimable = cur.claimable();
            SimpleFlipper.LOGGER.info("[flip] read: n={} f={}/{} clm={} @{}",
                    orderCount, cur.filled(), cur.total(), cur.claimable(), cur.pricePerUnit());
        } else {
            if (menuLoaded) { curFilled = 0; curTotal = 0; curClaimable = 0; }
            SimpleFlipper.LOGGER.info("[flip] read: no live order (loaded={})", menuLoaded);
        }

        // FINISH mode: claim out every order, cancel each (once drained), sell leftovers, then stop.
        if (finishing) {
            for (OrderBook.Order o : mine) {
                if (o.claimable() > 0) {
                    claimBaseline = InventoryScan.countByName(ITEM);
                    if (!ClickEngine.oneShotClick(o.slot(), 0)) { reopenAfterFail(); return; }
                    cycles++;
                    waitTicks = cfg.fillWaitTimeoutTicks;
                    state = State.CLAIM_FILL;
                    SimpleFlipper.LOGGER.info("[flip] finish: draining order (clm={})", o.claimable());
                    return;
                }
            }
            if (!mine.isEmpty()) {
                SimpleFlipper.LOGGER.info("[flip] finish: cancelling order");
                beginCancelSlot(mine.get(0).slot());
                return;
            }
            if (InventoryScan.countByName(ITEM) > 0) { state = State.SELL_OPEN; return; }
            kill("finished — all claimed, order cancelled, inventory sold");
            return;
        }

        // (A) Clear a STALE order (any order below the top-priced one). Drain to zero, then cancel.
        if (mine.size() >= 2) {
            OrderBook.Order stale = mine.get(mine.size() - 1);   // lowest price = old order
            if (stale.claimable() > 0) {
                claimBaseline = InventoryScan.countByName(ITEM);
                if (!ClickEngine.oneShotClick(stale.slot(), 0)) { reopenAfterFail(); return; }
                cycles++;
                waitTicks = cfg.fillWaitTimeoutTicks;
                state = State.CLAIM_FILL;
                SimpleFlipper.LOGGER.info("[flip] draining stale order (clm={}) before cancel", stale.claimable());
                return;
            }
            beginCancelSlot(stale.slot());
            return;
        }

        // (B) Claim a worthwhile batch from the CURRENT (top) order.
        if (!mine.isEmpty()) {
            OrderBook.Order cur = mine.get(0);
            boolean complete = cur.total() > 0 && cur.filled() >= cur.total();
            boolean worth = cur.claimable() >= Math.max(1, cfg.minClaimUnits)
                    || (complete && cur.claimable() > 0);
            if (worth) {
                claimBaseline = InventoryScan.countByName(ITEM);
                if (!ClickEngine.oneShotClick(cur.slot(), 0)) { reopenAfterFail(); return; }
                cycles++;
                waitTicks = cfg.fillWaitTimeoutTicks;
                state = State.CLAIM_FILL;
                return;
            }
        }

        // (C) Place an order if we have none.
        if (mine.isEmpty() && (menuLoaded || graceTicks <= 0)) {
            beginPlace();
            return;
        }

        // (D) Replace when our (single) order is OUTBID or MATCHED — orderbook-only, no timing.
        if (mine.size() == 1) {
            OrderBook.Order cur = mine.get(0);
            boolean complete = cur.total() > 0 && cur.filled() >= cur.total();
            if (!complete) {
                FlipMonitor.Status st = FlipMonitor.status(ITEM, cur.pricePerUnit());
                boolean rebid = st == FlipMonitor.Status.OUTBID || st == FlipMonitor.Status.MATCHED;
                SimpleFlipper.LOGGER.info("[flip] outbidchk @{} f={}/{} -> {} (rebid={})",
                        cur.pricePerUnit(), cur.filled(), cur.total(), st, rebid);
                if (rebid) {
                    notify("§c" + st.name().toLowerCase(Locale.ROOT)
                            + " §7— placing 2nd order, will clear old");
                    beginPlace();
                    return;
                }
            }
        }

        // Nothing to do — close, wait (random 2-6s), reopen. Loops forever.
        enterRefill();
    }

    /** Our BUY rows for ITEM, sorted by price DESCENDING (so [0] = current/top order). */
    private static List<OrderBook.Order> mineSorted(List<OrderBook.Order> rows) {
        String t = ITEM.toLowerCase(Locale.ROOT);
        List<OrderBook.Order> out = new ArrayList<>();
        for (OrderBook.Order o : rows) {
            if (o.buy() && o.name().toLowerCase(Locale.ROOT).contains(t)) out.add(o);
        }
        out.sort((a, b) -> Double.compare(b.pricePerUnit(), a.pricePerUnit()));
        return out;
    }

    private static void claimFill(Minecraft mc) {
        long now = InventoryScan.countByName(ITEM);
        if (now > claimBaseline) { state = State.SELL_OPEN; return; }
        if (--waitTicks <= 0) state = State.SELL_OPEN;   // nothing delivered; go sell scraps, re-loop
    }

    // ---- selling to the Builder ----

    private static void sellOpen(Minecraft mc) {
        if (builderOpen()) { settleTicks = Config.get().sellSettleTicks + jitter(); state = State.SELL_DRAIN; return; }
        if (mc.screen != null) { closeScreen(mc); waitTicks = 2; return; }
        if (waitTicks > 0) { waitTicks--; return; }
        if (!Actions.interactFacingEntity()) { waitTicks = 20; return; }   // retry, don't kill
        waitTicks = Config.get().menuWaitTimeoutTicks;
        state = State.SELL_WAIT;
    }

    private static void sellWait(Minecraft mc) {
        if (builderOpen()) { settleTicks = Config.get().sellSettleTicks + jitter(); state = State.SELL_DRAIN; return; }
        if (--waitTicks <= 0) state = State.SELL_OPEN;
    }

    private static void sellDrain(Minecraft mc) {
        if (!builderOpen()) { state = State.SELL_OPEN; return; }
        if (settleTicks > 0) { settleTicks--; return; }
        int sold = 0;
        if (Actions.countMatchingInInventory(ITEM) > 0) sold = Actions.sellMatchingInInventory(ITEM);
        if (sold > 0) {
            totalSold += sold;
            settleTicks = Config.get().sellSettleTicks + jitter();
            return;
        }
        state = State.OPEN_ORDERS;   // inventory empty of ITEM → back to managing the order
        waitTicks = 0;
    }

    // ---- place / cancel sequences ----

    private static void beginPlace() {
        waitTicks = 2 + jitter();
        state = State.PLACE_OPEN;
    }

    private static void placeOpen(Minecraft mc) {
        if (mc.screen != null) { closeScreen(mc); waitTicks = 2 + jitter(); return; }
        if (waitTicks > 0) { waitTicks--; return; }

        // Split the purse across the orders we may hold at once (1 base + up to 1 overlap = 2), so
        // the place-before-clear duplicate is still affordable.
        int slotsRemaining = Math.max(1, 2 - orderCount);
        long units = OrderSizer.unitsFor(ITEM, slotsRemaining);
        if (units <= 0) {
            notify("§ecan't size order yet (purse/price) — will retry");
            enterRefill();
            return;
        }

        Actions.openBazaarProduct(ITEM);
        seqResult = null;
        boolean armed = ClickEngine.run("place:" + ITEM,
                FlipSequences.placeBuyOrder(ITEM, units), ok -> seqResult = ok);
        if (!armed) { reopenAfterFail(); return; }
        SimpleFlipper.LOGGER.info("[flip] placing {} units of '{}'", units, ITEM);
        notify("§7placing §e" + units + "x " + ITEM + " §7order…");
        waitTicks = 240;
        state = State.PLACE_WAIT;
    }

    private static void placeWait(Minecraft mc) {
        if (Boolean.TRUE.equals(seqResult)) {
            SimpleFlipper.LOGGER.info("[flip] placed");
            state = State.OPEN_ORDERS;
            waitTicks = 0;
            return;
        }
        if (Boolean.FALSE.equals(seqResult)) { SimpleFlipper.LOGGER.warn("[flip] place aborted; retrying"); reopenAfterFail(); return; }
        if (--waitTicks <= 0) { SimpleFlipper.LOGGER.warn("[flip] place timed out; retrying"); reopenAfterFail(); }
    }

    private static void beginCancelSlot(int slot) {
        seqResult = null;
        boolean armed = ClickEngine.run("cancel:" + ITEM,
                FlipSequences.cancelOrderAtSlot(slot), ok -> seqResult = ok);
        if (!armed) { reopenAfterFail(); return; }
        SimpleFlipper.LOGGER.info("[flip] cancelling order (slot {})", slot);
        notify("§7clearing old order…");
        waitTicks = 120;
        state = State.CANCEL_WAIT;
    }

    private static void cancelWait(Minecraft mc) {
        if (Boolean.TRUE.equals(seqResult)) {
            state = State.OPEN_ORDERS;
            waitTicks = 0;
            return;
        }
        if (Boolean.FALSE.equals(seqResult) || --waitTicks <= 0) {
            SimpleFlipper.LOGGER.warn("[flip] cancel failed; will retry");
            reopenAfterFail();
        }
    }

    // ---- helpers ----

    private static void enterRefill() {
        Config cfg = Config.get();
        int lo = Math.max(1, cfg.refillPollMinTicks), hi = Math.max(lo, cfg.refillPollMaxTicks);
        waitTicks = lo + RANDOM.nextInt(hi - lo + 1);
        state = State.REFILL;
    }

    private static void reopenAfterFail() {
        ClickEngine.killSwitch();      // clear any half-run sequence
        ClickEngine.enabled = true;    // immediately re-arm (we're not stopping)
        state = State.OPEN_ORDERS;
        waitTicks = 10;
    }

    private static boolean builderOpen() {
        return Menu.title().toLowerCase(Locale.ROOT).contains(BazaarMenus.TITLE_BUILDER);
    }

    private static void closeScreen(Minecraft mc) {
        if (mc.player != null) mc.player.closeContainer();
        mc.setScreen(null);
    }

    private static int jitter() {
        int ms = Config.get().clickJitterMs;
        return ms <= 0 ? 0 : RANDOM.nextInt(ms + 1) / 50;
    }

    private static void notify(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§b§lSimple Flipper §r" + msg), false);
        }
    }
}
