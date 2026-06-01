package dev.salocin.simpleflipper.flip;

import dev.salocin.simpleflipper.SimpleFlipper;
import dev.salocin.simpleflipper.click.ClickEngine;
import dev.salocin.simpleflipper.click.Menu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Side-effecting actions outside the container-click DSL: send a server command,
 * right-click the NPC the player is facing, and sell stacks out of the player's
 * inventory (the Builder buys whatever you left-click in your inventory:
 * button 0 / ClickType.PICKUP).
 */
public final class Actions {
    private Actions() {}

    /** Send a command to the server (no leading slash), e.g. {@code bz Enchanted Hard Stone}. */
    public static boolean sendCommand(String command) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return false;
        conn.sendCommand(command);
        SimpleFlipper.LOGGER.info("[flip] sent command '/{}'", command);
        return true;
    }

    /** Open the bazaar product page for an item directly via Hypixel's own command. */
    public static boolean openBazaarProduct(String displayName) {
        return sendCommand("bz " + displayName);
    }

    /** Right-click the entity currently under the crosshair (the Builder the player faces). */
    public static boolean interactFacingEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return false;
        HitResult hit = mc.hitResult;
        if (!(hit instanceof EntityHitResult ehr)) {
            SimpleFlipper.LOGGER.warn("[flip] no entity under crosshair to interact with");
            return false;
        }
        Entity target = ehr.getEntity();
        mc.gameMode.interact(mc.player, target, InteractionHand.MAIN_HAND);
        SimpleFlipper.LOGGER.info("[flip] interacted with entity '{}'", target.getName().getString());
        return true;
    }

    /** Trailing stack-count the Builder appends to a sell button's name, e.g. " x64" / " x1,024". */
    private static final Pattern COUNT_SUFFIX = Pattern.compile("\\s*x[0-9,]+$", Pattern.CASE_INSENSITIVE);

    /**
     * Sell every sellable stack in the player-inventory portion of the open Builder menu
     * for {@code itemName}, one left-click each. The Builder overlays each sellable
     * inventory stack with a name like "Enchanted Hard Stone x64" and lore "Click to sell!";
     * we strip the " xN" count suffix and require that sell lore, so we (a) match despite
     * the suffix and (b) NEVER click a "buyback" button (which spends coins). The base name
     * must match {@code itemName} exactly so look-alikes aren't sold. Returns slots clicked.
     * Skips the SkyBlock Menu item (last slot). Respects the kill switch via
     * {@link ClickEngine#oneShotClick}.
     */
    public static int sellMatchingInInventory(String itemName) {
        AbstractContainerMenu menu = Menu.currentMenu();
        if (menu == null) return 0;
        String needle = itemName.trim();
        int total = menu.slots.size();
        int start = BazaarMenus.inventoryStart(total);
        int end = BazaarMenus.inventoryEndExclusive(total);
        int clicked = 0;
        for (int i = start; i < end; i++) {
            if (!sellable(menu.slots.get(i), needle)) continue;
            if (ClickEngine.oneShotClick(i, 0)) clicked++;
        }
        if (clicked > 0) SimpleFlipper.LOGGER.info("[flip] sold {} stack(s) of '{}'", clicked, itemName);
        return clicked;
    }

    /** Count matching sellable stacks still in inventory (drain stop condition). */
    public static int countMatchingInInventory(String itemName) {
        AbstractContainerMenu menu = Menu.currentMenu();
        if (menu == null) return 0;
        String needle = itemName.trim();
        int total = menu.slots.size();
        int start = BazaarMenus.inventoryStart(total);
        int end = BazaarMenus.inventoryEndExclusive(total);
        int n = 0;
        for (int i = start; i < end; i++) {
            if (sellable(menu.slots.get(i), needle)) n++;
        }
        return n;
    }

    /**
     * True if this inventory-region slot is the target item to sell. The Builder is a
     * "Sell Item" shop ("Click items in your inventory to sell them to this Shop!"), so
     * the inventory slots are the player's <em>real</em> stacks with their normal names
     * and lore — there is NO "Click to sell!" overlay to key on. We therefore match on
     * the (exact) item name only; restricting to {@code needle} is what keeps us from
     * accidentally selling tools/other items. We still strip any " xN" count suffix
     * defensively.
     */
    private static boolean sellable(Slot slot, String needle) {
        if (slot == null || !slot.hasItem()) return false;
        String base = COUNT_SUFFIX.matcher(Menu.nameOf(slot)).replaceFirst("").trim();
        return base.equalsIgnoreCase(needle);
    }
}
