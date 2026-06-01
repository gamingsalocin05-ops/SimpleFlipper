package dev.salocin.simpleflipper.core;

import dev.salocin.simpleflipper.click.Menu;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Reads the player's <em>real</em> inventory by item display name — independent of
 * any open container menu. This is how the claim loop confirms a claim actually
 * delivered goods. Matching is on the stripped hover name so it works for both
 * vanilla and Hypixel-renamed items.
 *
 * <p>Distinct from {@link dev.salocin.simpleflipper.flip.Actions#countMatchingInInventory},
 * which reads the open Builder menu's inventory region.
 */
public final class InventoryScan {
    private InventoryScan() {}

    /** Total count of stacks in the player inventory whose name matches {@code itemName}. */
    public static long countByName(String itemName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        String needle = itemName.trim();
        Inventory inv = mc.player.getInventory();
        long n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (nameMatches(s, needle)) n += s.getCount();
        }
        return n;
    }

    /**
     * Of the given candidate item names, return the one the player is currently
     * holding at least {@code threshold} of (highest count wins), or null if none
     * reach the threshold. Used to pick the drain target right after a manual claim.
     */
    public static String detectBulk(List<String> names, long threshold) {
        String best = null;
        long bestCount = threshold - 1;
        for (String name : names) {
            long c = countByName(name);
            if (c >= threshold && c > bestCount) {
                best = name;
                bestCount = c;
            }
        }
        return best;
    }

    private static boolean nameMatches(ItemStack stack, String needle) {
        String name = Menu.strip(stack.getHoverName().getString());
        String ln = name.toLowerCase(Locale.ROOT);
        String ln2 = needle.toLowerCase(Locale.ROOT);
        return ln.equals(ln2) || ln.contains(ln2);
    }
}
