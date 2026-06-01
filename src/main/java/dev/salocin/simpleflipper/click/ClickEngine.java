package dev.salocin.simpleflipper.click;

import dev.salocin.simpleflipper.SimpleFlipper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Confirmation-gated container-click engine (Mojang-mapped). Runs a queue of
 * {@link Step}s entirely on the client tick
 * thread, using the game's own click path
 * ({@code gameMode.handleInventoryMouseClick}) — not raw packets.
 *
 * <p>Each step optionally waits for an expected menu, fires one click, then waits
 * for a confirmation signal (a new menu, a title, a slot change, or a chat line)
 * before the next step, with per-step timeouts. Between confirmed steps it inserts
 * a randomized "Balanced" delay (~150-400ms).
 *
 * <p>Hard abort guards checked every tick while a sequence runs: master switch off
 * (kill-switch), player took damage, player moved (server knockback / teleport),
 * or a direct message arrived. Unexpected/missing menus abort via per-step
 * pre-wait timeouts.
 */
public final class ClickEngine {
    private ClickEngine() {}

    private static final Random RANDOM = new Random();

    /** Balanced pacing: ~150-400ms between confirmed steps => 3..8 ticks @ 20 tps. */
    private static final int MIN_GAP_TICKS = 3;
    private static final int MAX_GAP_TICKS = 8;
    /** Abort if the player drifts more than ~0.5 blocks from where the sequence began. */
    private static final double MOVE_ABORT_DISTSQR = 0.25;

    /** Master switch for sequence automation. One-shot test clicks ignore this. */
    public static volatile boolean enabled = false;

    private enum Phase { PRE_WAIT, GAP, AWAIT_CONFIRM }

    // ---- active-sequence state (client thread only) ----
    private static boolean active = false;
    private static String seqLabel = "";
    private static final Deque<Step> queue = new ArrayDeque<>();
    private static Step current;
    private static Phase phase;

    private static Vec3 startPos;
    private static int phaseTicks;
    private static int gapTarget;
    private static int baselineContainerId = -1;
    private static int clickSlotId = -1;
    private static ItemStack baselineStack = ItemStack.EMPTY;
    /** Notified once when the active sequence ends: true = finished, false = aborted. */
    private static Consumer<Boolean> onDone;

    // ---------------- public API ----------------

    /**
     * Arm and run a sequence. Returns false if one is already running, automation
     * is disabled, or there's no player/gameMode.
     */
    public static boolean run(String label, List<Step> steps) {
        return run(label, steps, null);
    }

    /**
     * Arm and run a sequence with a completion callback. The callback fires exactly
     * once when the sequence ends (true = all steps finished, false = aborted).
     */
    public static boolean run(String label, List<Step> steps, Consumer<Boolean> done) {
        if (active || steps == null || steps.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.player == null || mc.gameMode == null) return false;
        queue.clear();
        queue.addAll(steps);
        seqLabel = label;
        startPos = mc.player.position();
        onDone = done;
        ChatWatcher.clearDmAbort();
        active = true;
        SimpleFlipper.LOGGER.info("[seq] '{}' armed ({} steps)", seqLabel, steps.size());
        beginNextStep();
        return true;
    }

    public static boolean isActive() {
        return active;
    }

    /** Panic stop: disable automation and clear any running sequence. */
    public static void killSwitch() {
        enabled = false;
        if (active) abort("kill switch");
        else reset();
    }

    /** Immediate one-shot left/right click on a menu-slot id. Ignores the master switch. */
    public static boolean oneShotClick(int slotId, int button) {
        return clickSlot(slotId, button);
    }

    // ---------------- tick state machine ----------------

    public static void tick(Minecraft mc) {
        if (!active) return;
        String reason = guard(mc);
        if (reason != null) {
            abort(reason);
            return;
        }
        switch (phase) {
            case PRE_WAIT -> tickPreWait();
            case GAP -> tickGap();
            case AWAIT_CONFIRM -> tickAwaitConfirm();
        }
    }

    private static String guard(Minecraft mc) {
        if (!enabled) return "automation disabled";
        if (mc.player == null || mc.gameMode == null) return "no player";
        if (mc.player.hurtTime > 0) return "took damage";
        if (startPos != null && mc.player.position().distanceToSqr(startPos) > MOVE_ABORT_DISTSQR) return "player moved";
        if (ChatWatcher.dmAbortRequested()) return "incoming message";
        return null;
    }

    private static void beginNextStep() {
        current = queue.poll();
        if (current == null) {
            finish();
            return;
        }
        phase = Phase.PRE_WAIT;
        phaseTicks = 0;
    }

    private static void tickPreWait() {
        if (current.expectTitle != null) {
            String title = Menu.title().toLowerCase(Locale.ROOT);
            if (!title.contains(current.expectTitle)) {
                if (++phaseTicks >= current.timeoutTicks) {
                    abort("menu '" + current.expectTitle + "' for step '" + current.label + "' never appeared");
                }
                return;
            }
        }
        gapTarget = MIN_GAP_TICKS + RANDOM.nextInt(MAX_GAP_TICKS - MIN_GAP_TICKS + 1);
        phaseTicks = 0;
        phase = Phase.GAP;
    }

    private static void tickGap() {
        if (++phaseTicks < gapTarget) return;
        doClick();
    }

    private static void doClick() {
        // Sign-fill step: type into the open sign editor (not a container click), then confirm.
        if (current.fillSign) {
            if (!SignInput.submit(current.fillText)) {
                abort("no sign editor open for '" + current.label + "'");
                return;
            }
            SimpleFlipper.LOGGER.info("[click] '{}' -> sign '{}' [{}]", current.label, current.fillText, seqLabel);
            armConfirm();
            return;
        }
        // Pure wait step: nothing to click, go straight to confirm.
        if (current.matcher == null) {
            armConfirm();
            return;
        }
        AbstractContainerMenu menu = Menu.currentMenu();
        if (menu == null) {
            abort("no menu open to click '" + current.label + "'");
            return;
        }
        int slotId = current.matcher.find(menu);
        if (slotId < 0) {
            abort("could not find '" + current.label + "' (" + current.matcher.describe() + ")");
            return;
        }
        baselineContainerId = menu.containerId;
        clickSlotId = slotId;
        baselineStack = (slotId < menu.slots.size()) ? menu.slots.get(slotId).getItem().copy() : ItemStack.EMPTY;
        if (current.confirm == Step.Confirm.CHAT_CONTAINS) {
            ChatWatcher.expect(current.confirmArg);
        }
        if (!clickSlot(slotId, current.button)) {
            abort("click failed on '" + current.label + "'");
            return;
        }
        SimpleFlipper.LOGGER.info("[click] '{}' -> slot {} [{}]", current.label, slotId, seqLabel);
        armConfirm();
    }

    private static void armConfirm() {
        phase = Phase.AWAIT_CONFIRM;
        phaseTicks = 0;
    }

    private static void tickAwaitConfirm() {
        if (confirmSatisfied()) {
            beginNextStep();
            return;
        }
        if (++phaseTicks >= current.timeoutTicks) {
            if (current.abortOnTimeout) {
                abort("step '" + current.label + "' not confirmed (" + current.confirm + ")");
            } else {
                beginNextStep();
            }
        }
    }

    private static boolean confirmSatisfied() {
        switch (current.confirm) {
            case DELAY_ONLY:
                return phaseTicks >= Math.max(1, current.timeoutTicks / 4);
            case MENU_CHANGED: {
                int id = Menu.containerId();
                return id != -1 && id != baselineContainerId;
            }
            case TITLE_CONTAINS:
                return Menu.title().toLowerCase(Locale.ROOT).contains(current.confirmArg);
            case CHAT_CONTAINS:
                return ChatWatcher.expectSeen();
            case SIGN_OPENED:
                return SignInput.isOpen();
            case SLOT_CHANGED: {
                AbstractContainerMenu menu = Menu.currentMenu();
                if (menu == null) return true; // menu closed/changed counts as "changed"
                if (clickSlotId >= menu.slots.size()) return true;
                return !ItemStack.matches(menu.slots.get(clickSlotId).getItem(), baselineStack);
            }
            default:
                return false;
        }
    }

    /**
     * Simulate a genuine mouse click on a slot by driving the screen's GUI input path
     * (press + release at the slot's pixel center), exactly as a human click would. This
     * goes through {@code AbstractContainerScreen.mouseClicked/mouseReleased} rather than
     * sending a direct server slot-click packet, so client-side handlers other mods attach
     * to GUI mouse events also fire.
     */
    private static boolean clickSlot(int slotId, int button) {
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerScreen<?> screen = Menu.currentContainer();
        if (screen == null || mc.gameMode == null || mc.player == null) return false;
        AbstractContainerMenu menu = screen.getMenu();
        if (slotId < 0 || slotId >= menu.slots.size()) return false;
        Slot slot = menu.slots.get(slotId);
        // Slot x/y are relative to the GUI's top-left origin; +8 lands on the 16px slot's center.
        double mouseX = screen.leftPos + slot.x + 8.0;
        double mouseY = screen.topPos + slot.y + 8.0;
        MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        return true;
    }

    private static void finish() {
        SimpleFlipper.LOGGER.info("[seq] '{}' complete", seqLabel);
        notifyPlayer("§6SimpleFlipper §7sequence §a" + seqLabel + " §7done");
        end(true);
    }

    private static void abort(String reason) {
        SimpleFlipper.LOGGER.warn("[seq] '{}' aborted: {}", seqLabel, reason);
        notifyPlayer("§6SimpleFlipper §cabort: §7" + reason);
        end(false);
    }

    /** Capture the callback, reset state, then notify (so the callback may safely re-arm). */
    private static void end(boolean finished) {
        Consumer<Boolean> cb = onDone;
        reset();
        if (cb != null) cb.accept(finished);
    }

    private static void notifyPlayer(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(msg), true);
        }
    }

    private static void reset() {
        active = false;
        queue.clear();
        current = null;
        phase = null;
        startPos = null;
        phaseTicks = 0;
        gapTarget = 0;
        baselineContainerId = -1;
        clickSlotId = -1;
        baselineStack = ItemStack.EMPTY;
        onDone = null;
        ChatWatcher.expect(null);
    }
}
