package dev.salocin.simpleflipper.api;

import java.util.List;

/**
 * One bazaar product as returned by the keyless Hypixel API. Trimmed to what we need:
 * the top-of-book buy-order price, for outbid detection on our placed buy orders.
 *
 * <p>Hypixel's API is named from the ITEM's perspective, which inverts and swaps the
 * two book summaries. Verified live:
 * <pre>
 *   buy_summary[0]  = lowest SELL offer  (what you pay to insta-buy)
 *   sell_summary[0] = highest BUY order  (what you get to insta-sell) — the top of OUR book
 * </pre>
 */
public final class Product {
    public String product_id;
    public QuickStatus quick_status;
    public List<Order> buy_summary;
    public List<Order> sell_summary;

    public static final class QuickStatus {
        public String productId;
        public double buyPrice;
        public double sellPrice;
    }

    public static final class Order {
        public double amount;
        public double pricePerUnit;
        public long orders;
    }

    /** Best price to instantly sell into (top buy order), as a fallback for an empty book. */
    public double instantSell() {
        return quick_status != null ? quick_status.sellPrice : 0;
    }

    /**
     * Top of the BUY-order book — the price you must beat when placing a competitive buy
     * order. This includes our own order, so a caller compares it to our placed price with
     * a margin to decide "someone is meaningfully above me".
     */
    public double topBuyOrder() {
        return (sell_summary != null && !sell_summary.isEmpty())
                ? sell_summary.get(0).pricePerUnit
                : instantSell();
    }

    /** Number of distinct buy orders sitting at the top buy-order price (0 if unknown). */
    public long topBuyOrderCount() {
        return (sell_summary != null && !sell_summary.isEmpty())
                ? sell_summary.get(0).orders
                : 0;
    }
}
