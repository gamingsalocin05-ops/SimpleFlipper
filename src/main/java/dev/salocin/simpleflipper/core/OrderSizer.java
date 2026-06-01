package dev.salocin.simpleflipper.core;

import dev.salocin.simpleflipper.SimpleFlipper;
import dev.salocin.simpleflipper.api.Product;
import dev.salocin.simpleflipper.config.Config;
import dev.salocin.simpleflipper.feature.Purse;

import java.util.Locale;

/**
 * Works out how many units to place in a buy order so that ALL concurrent orders fit the purse.
 *
 * <p>Budget per order = {@code purse * budgetFraction / slotsRemaining}, where
 * {@code slotsRemaining = maxConcurrentOrders - ordersAlreadyLive}. Because a placed buy order
 * escrows its coins immediately, both the purse and the remaining slot count drop together — so
 * every order ends up at the same share of the starting purse, so all concurrent orders fit.
 * Capped at the Hypixel per-order max (71,680) and the {@code orderUnits} ceiling.
 *
 * <p>The price is the order price we'll place at — the current top buy order (live API) + 0.1 —
 * so the cost estimate matches what's actually escrowed.
 */
public final class OrderSizer {
    private OrderSizer() {}

    /** Hypixel hard cap per buy order. */
    public static final long MAX_ORDER_UNITS = 71_680;

    /**
     * Units to place for {@code item}, budgeting purse across the remaining order slots.
     *
     * @param item            display name
     * @param slotsRemaining  how many orders still need to be placed (incl. this one) before the
     *                        purse is fully committed — i.e. {@code maxConcurrent - liveOrders}.
     *                        The current purse is split evenly across these, so each ≈ purse/slots.
     */
    public static long unitsFor(String item, int slotsRemaining) {
        double price = priceFor(item);
        if (price <= 0) {
            SimpleFlipper.LOGGER.warn("[sizer] no price for '{}' (API not ready?) — cannot size order", item);
            return 0;
        }
        long purse = Purse.read();
        long ceiling = Math.min(Math.max(1, Config.get().orderUnits), MAX_ORDER_UNITS);
        if (purse <= 0) {
            SimpleFlipper.LOGGER.warn("[sizer] purse unreadable — using ceiling for '{}'", item);
            return ceiling;
        }
        int slots = Math.max(1, slotsRemaining);
        double fraction = Config.get().orderBudgetFraction;     // small safety buffer (e.g. 0.95)
        long coinBudget = (long) Math.floor(purse * fraction / slots);
        long affordable = (long) Math.floor(coinBudget / price);
        long units = Math.min(affordable, ceiling);
        SimpleFlipper.LOGGER.info(String.format(Locale.ROOT,
                "[sizer] '%s' price=%.1f purse=%d slots=%d budget=%d -> affordable=%d, placing=%d",
                item, price, purse, slots, coinBudget, affordable, Math.max(units, 0)));
        return Math.max(units, 0);
    }

    /** The price our order will sit at: top buy order + 0.1 (matches FlipSequences' "Top Order +0.1"). */
    private static double priceFor(String item) {
        Product p = SimpleFlipper.API.get(item);
        if (p == null) return 0;
        double top = p.topBuyOrder();
        return top > 0 ? top + 0.1 : 0;
    }
}
