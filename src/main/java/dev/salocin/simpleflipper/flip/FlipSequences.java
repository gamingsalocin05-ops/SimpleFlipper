package dev.salocin.simpleflipper.flip;

import dev.salocin.simpleflipper.click.SlotMatcher;
import dev.salocin.simpleflipper.click.Step;

import java.util.List;

/**
 * Builds the {@link Step} sequences the executor feeds to the click engine. Each
 * sequence assumes the prior navigation step (a sent command / interaction) has
 * opened the expected menu; steps gate on the menu title so a wrong/late menu
 * aborts safely. Buttons are matched by name first, with the verified slot index
 * as a fallback (see {@link BazaarMenus}).
 */
public final class FlipSequences {
    private FlipSequences() {}

    /** Largest native Hypixel amount preset ("Buy a thousand!"). Above this we use Custom Amount. */
    private static final long MAX_PRESET_UNITS = 1024;

    /**
     * Place a competitive buy order. Precondition: the executor sent {@code bz <item>}
     * first, which opens the search/category <em>browser</em> ("Bazaar ➜ …", 90 slots)
     * — NOT the product page. So we first click the product tile to open its page,
     * then: Create Buy Order → choose the amount → "Top Order +0.1" → submit.
     *
     * <p>Amount selection branches on size. For {@code targetUnits <= 1024} we click the
     * largest genuine Hypixel preset ≤ target. Above that we use the native "Custom
     * Amount" button, which opens a sign editor where we type the exact count (up to the
     * 71,680 per-order max). We deliberately do NOT use the quick-buy buttons other mods
     * inject into this menu — those are client-side fakes our container clicks can't drive.
     */
    public static List<Step> placeBuyOrder(String displayName, long targetUnits) {
        Step openProduct =
                // /bz <item> lands on the browser; click the matching product tile to open its page.
                Step.clickThenMenuChange("open-product",
                                SlotMatcher.name(displayName).andLore("click to view details"))
                        .requireTitle(BazaarMenus.TITLE_BAZAAR);
        Step createBuyOrder =
                Step.clickThenTitle("create-buy-order",
                                SlotMatcher.name("create buy order").orIndex(BazaarMenus.PRODUCT_CREATE_BUY_ORDER),
                                BazaarMenus.TITLE_AMOUNT)
                        .requireTitle(BazaarMenus.TITLE_PRODUCT_ARROW);
        Step topOrder =
                Step.clickThenTitle("top-order+0.1",
                                SlotMatcher.name("top order +0.1").orIndex(BazaarMenus.PRICE_TOP_PLUS_01),
                                BazaarMenus.TITLE_CONFIRM_BUY)
                        .requireTitle(BazaarMenus.TITLE_PRICE);
        Step submit =
                Step.clickThenChat("submit-order",
                                SlotMatcher.name("buy order").andLore("submit order").orIndex(BazaarMenus.CONFIRM_SUBMIT),
                                "buy order setup")
                        .requireTitle(BazaarMenus.TITLE_CONFIRM_BUY);

        if (targetUnits > MAX_PRESET_UNITS) {
            return List.of(openProduct, createBuyOrder,
                    // Custom Amount opens a sign editor; type the exact count, which lands on the price screen.
                    Step.clickThenSignOpen("open-custom-amount",
                                    SlotMatcher.name("custom amount").orIndex(BazaarMenus.AMOUNT_CUSTOM))
                            .requireTitle(BazaarMenus.TITLE_AMOUNT),
                    Step.fillSign("type-amount", String.valueOf(targetUnits), BazaarMenus.TITLE_PRICE),
                    topOrder, submit);
        }
        return List.of(openProduct, createBuyOrder,
                Step.clickThenTitle("pick-amount",
                                SlotMatcher.amountAtMost(targetUnits),
                                BazaarMenus.TITLE_PRICE)
                        .requireTitle(BazaarMenus.TITLE_AMOUNT),
                topOrder, submit);
    }

    /**
     * Cancel the buy order for {@code itemName}. Precondition: the Manage Orders
     * ("Co-op Bazaar Orders") menu is open. Clicks the matching order → Cancel Order.
     */
    public static List<Step> cancelOrder(String itemName) {
        return List.of(
                // Let the Manage Orders slots/lore finish loading before clicking a row —
                // the same load race that bit the read path (clicking too soon misfires).
                Step.delay("settle-orders", 20).requireTitle(BazaarMenus.TITLE_MANAGE_ORDERS),
                Step.clickThenTitle("open-order",
                                SlotMatcher.nameContains("buy " + itemName),   // BUY row only — never a SELL offer for the same item
                                BazaarMenus.TITLE_ORDER_OPTIONS)
                        .requireTitle(BazaarMenus.TITLE_MANAGE_ORDERS),
                Step.clickThenChat("cancel-order",
                                SlotMatcher.name("cancel order").orIndex(BazaarMenus.ORDER_CANCEL),
                                "cancelled")
                        .requireTitle(BazaarMenus.TITLE_ORDER_OPTIONS));
    }

    /**
     * Cancel the order at a SPECIFIC slot. Needed when an item can have two orders at once (the
     * place-new-then-clear-old flow): name matching would click whichever row is first, possibly
     * the wrong one. Clicking the exact slot opens its Order Options → Cancel Order. Precondition:
     * Manage Orders open AND that order has no goods to claim (Hypixel blocks cancel otherwise).
     */
    public static List<Step> cancelOrderAtSlot(int slot) {
        return List.of(
                Step.delay("settle-orders", 20).requireTitle(BazaarMenus.TITLE_MANAGE_ORDERS),
                Step.clickThenTitle("open-order",
                                SlotMatcher.index(slot),
                                BazaarMenus.TITLE_ORDER_OPTIONS)
                        .requireTitle(BazaarMenus.TITLE_MANAGE_ORDERS),
                Step.clickThenChat("cancel-order",
                                SlotMatcher.name("cancel order").orIndex(BazaarMenus.ORDER_CANCEL),
                                "cancelled")
                        .requireTitle(BazaarMenus.TITLE_ORDER_OPTIONS));
    }

    /**
     * Open Manage Orders from the main bazaar. Precondition: the main bazaar menu
     * (e.g. "Bazaar ➜ Farming", 90 slots) is open after sending {@code bz}.
     */
    public static List<Step> openManageOrders() {
        return List.of(
                // Wait for /bz to open the bazaar menu, then settle ~1.25s so slot 50 ("Manage
                // Orders") is actually loaded — clicking too soon lands on an unloaded slot.
                Step.delay("settle-bazaar", 25).requireTitle(BazaarMenus.TITLE_BAZAAR),
                Step.clickThenTitle("manage-orders",
                                SlotMatcher.name("manage orders").orIndex(50),
                                BazaarMenus.TITLE_MANAGE_ORDERS)
                        .requireTitle(BazaarMenus.TITLE_BAZAAR));
    }
}
