package dev.salocin.simpleflipper.flip;

/**
 * Slot ids and title fragments for the Hypixel Bazaar / Builder menus the flip
 * loop drives. All values verified from live menu dumps (the {@code `} dump key)
 * on MC 1.21.11. Match buttons by name/lore first (see {@link dev.salocin.simpleflipper.click.SlotMatcher})
 * and fall back to these indices.
 *
 * <p>Container layout: Hypixel chest GUIs report {@code menu.slots.size()} as the
 * chest area PLUS the 36 player-inventory slots. A 4-row chest = 36 chest slots
 * (0–35) + 36 inventory slots (36–71); the last inventory slot (71) always holds
 * the "SkyBlock Menu" item. A 6-row chest = 54 + 36 → inventory at 54–89.
 */
public final class BazaarMenus {
    private BazaarMenus() {}

    // ---- title fragments (lowercased, for contains-match) ----
    public static final String TITLE_BAZAAR = "bazaar";                // main bazaar menu after /bz (also matches "Co-op Bazaar Orders")
    public static final String TITLE_PRODUCT_ARROW = "➜";              // e.g. "Stone ➜ Enchanted Hard Stone"
    public static final String TITLE_AMOUNT = "how many do you want";
    public static final String TITLE_PRICE = "how much do you want to pay";
    public static final String TITLE_CONFIRM_BUY = "confirm buy order";
    public static final String TITLE_MANAGE_ORDERS = "bazaar orders";  // "Co-op Bazaar Orders"
    public static final String TITLE_ORDER_OPTIONS = "order options";
    public static final String TITLE_BUILDER = "builder";

    // ---- product page (e.g. "… ➜ Enchanted Hard Stone"), 72 slots ----
    public static final int PRODUCT_CREATE_BUY_ORDER = 15;
    public static final int PRODUCT_MANAGE_ORDERS = 32;

    // ---- amount menu ("How many do you want?"), 72 slots ----
    public static final int AMOUNT_CUSTOM = 16;   // opens a sign (Custom Amount)
    public static final int AMOUNT_BUY_MAX = 17;  // "Buy 71,680" (per-order max)
    public static final int AMOUNT_GO_BACK = 31;

    // ---- price menu ("How much do you want to pay?"), 72 slots ----
    public static final int PRICE_SAME_AS_TOP = 10;   // match the current top buy order
    public static final int PRICE_TOP_PLUS_01 = 12;   // "Top Order +0.1" — our competitive price
    public static final int PRICE_5PCT_SPREAD = 14;
    public static final int PRICE_CUSTOM = 16;        // opens a sign (Custom Price)
    public static final int PRICE_GO_BACK = 30;
    public static final int PRICE_CANCEL = 31;

    // ---- confirm menu ("Confirm Buy Order"), 72 slots ----
    public static final int CONFIRM_SUBMIT = 13;   // "Buy Order" → "Click to submit order!"
    public static final int CONFIRM_GO_BACK = 30;
    public static final int CONFIRM_CANCEL = 31;

    // ---- order options (click one order inside Manage Orders), 72 slots ----
    public static final int ORDER_CANCEL = 11;     // "Cancel Order" (refunds unfilled units)
    public static final int ORDER_FLIP = 15;       // "Flip Order" (create sell offer; only once filled)
    public static final int ORDER_GO_BACK = 31;

    /**
     * Player-inventory slot range for a chest menu, given its total slot count.
     * Inventory is the last 36 slots; the very last one is the SkyBlock Menu item
     * (never sell it). Returns {@code [start, endInclusiveExclusiveOfSkyblockMenu)}.
     */
    public static int inventoryStart(int totalSlots) {
        return Math.max(0, totalSlots - 36);
    }

    /** Last sellable inventory slot (exclusive of the SkyBlock Menu item at totalSlots-1). */
    public static int inventoryEndExclusive(int totalSlots) {
        return Math.max(0, totalSlots - 1);
    }
}
