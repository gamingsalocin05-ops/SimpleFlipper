package dev.salocin.simpleflipper.flip;

import dev.salocin.simpleflipper.click.Menu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only parser for the "Co-op Bazaar Orders" (Manage Orders) menu. Each order
 * button's NAME carries the side ("BUY Enchanted Hard Stone" / "SELL ..."), and its
 * lore carries the fill state, e.g.:
 * <pre>
 *   BUY Enchanted Hard Stone
 *   Order amount: 30,000x
 *   Filled: 5.1k/30k (16.9%)        &lt;- abbreviated; we don't rely on this line
 *   You have 5,056 items to claim!   &lt;- exact unclaimed count (what a claim pulls)
 *   Click to claim!
 * </pre>
 * We only act on BUY orders (the flip places buy orders and claims filled units);
 * sell offers are recognized but skipped by the executor.
 */
public final class OrderBook {
    private OrderBook() {}

    /** One order row in the Manage Orders menu. {@code pricePerUnit} is the price WE placed at. */
    public record Order(int slot, String name, boolean buy, long total, long filled,
                        long claimable, double pricePerUnit) {
        public boolean complete() {
            return total > 0 && filled >= total;
        }
        public double fillFraction() {
            return total <= 0 ? 0 : (double) filled / total;
        }
    }

    private static final Pattern AMOUNT = Pattern.compile("order amount:\\s*([0-9,.kmb]+)");
    private static final Pattern FILLED = Pattern.compile("filled:\\s*([0-9,.kmb]+)\\s*/\\s*([0-9,.kmb]+)");
    // Accept abbreviated claim counts too ("4.7k items to claim"), matching AMOUNT/FILLED.
    private static final Pattern CLAIM = Pattern.compile("([0-9,.kmb]+)\\s+items?\\s+to\\s+claim");
    // The exact price we placed this order at, e.g. "Price per unit: 8.1 coins".
    private static final Pattern PRICE = Pattern.compile("price per unit:\\s*([0-9,.]+)");

    /** True if the currently-open menu looks like the Manage Orders screen. */
    public static boolean isOpen() {
        return Menu.title().toLowerCase(Locale.ROOT).contains("bazaar orders");
    }

    /** Parse every order row in the currently-open Manage Orders menu (empty if none). */
    public static List<Order> read() {
        List<Order> out = new ArrayList<>();
        AbstractContainerMenu menu = Menu.currentMenu();
        if (menu == null) return out;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem()) continue;
            List<String> lore = Menu.loreOf(slot);
            if (lore.isEmpty()) continue;

            String name = Menu.nameOf(slot);
            // Side comes from the name prefix ("BUY ..." / "SELL ..."); the lore has no
            // "buy order"/"sell offer" text on a live order row.
            String lname = name.toLowerCase(Locale.ROOT).trim();
            boolean buy = !lname.startsWith("sell");

            long total = -1, filled = -1, claimable = 0;
            double price = 0;
            boolean isOrder = false;
            for (String raw : lore) {
                String line = raw.toLowerCase(Locale.ROOT);
                Matcher a = AMOUNT.matcher(line);
                if (a.find()) { total = parse(a.group(1)); isOrder = true; }
                Matcher f = FILLED.matcher(line);
                if (f.find()) {
                    filled = parse(f.group(1));
                    if (total < 0) total = parse(f.group(2));
                    isOrder = true;
                }
                Matcher c = CLAIM.matcher(line);
                if (c.find()) { claimable = parse(c.group(1)); isOrder = true; }
                Matcher pr = PRICE.matcher(line);
                if (pr.find()) {
                    try { price = Double.parseDouble(pr.group(1).replace(",", "")); } catch (NumberFormatException ignored) {}
                }
            }
            if (!isOrder) continue;
            // The "Filled:" line is abbreviated (e.g. "5.1k") and rounds; "items to claim"
            // is exact. Fall back to claimable when filled didn't parse cleanly.
            if (filled < 0) filled = claimable;
            out.add(new Order(i, name, buy, total, Math.max(filled, 0), Math.max(claimable, 0), price));
        }
        return out;
    }

    /** Parse counts that may be abbreviated: "30,000", "5.1k", "1.2m", "3b". */
    private static long parse(String s) {
        String t = s.replace(",", "").trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return -1;
        double mult = 1;
        char last = t.charAt(t.length() - 1);
        if (last == 'k') { mult = 1_000; t = t.substring(0, t.length() - 1); }
        else if (last == 'm') { mult = 1_000_000; t = t.substring(0, t.length() - 1); }
        else if (last == 'b') { mult = 1_000_000_000; t = t.substring(0, t.length() - 1); }
        try {
            return (long) (Double.parseDouble(t) * mult);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
