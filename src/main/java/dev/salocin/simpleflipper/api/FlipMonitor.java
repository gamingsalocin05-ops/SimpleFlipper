package dev.salocin.simpleflipper.api;

import dev.salocin.simpleflipper.SimpleFlipper;

/**
 * Order-status check for one of our placed BUY orders, replicating BazaarUtils'
 * {@code OrderInfoContainer.findOutbidStatus()} — purely from the live order book, NO timing.
 *
 * <p>For a buy order, {@code market} = the top buy-order price (highest BUY order on the book),
 * and {@code count} = how many orders sit at that top price. Comparing our placed price:
 * <ul>
 *   <li>ours &gt; market  → we ARE the strict top → {@link Status#COMPETITIVE}</li>
 *   <li>ours &lt; market  → someone is strictly above us → {@link Status#OUTBID}</li>
 *   <li>ours == market &amp;&amp; count &gt; 1 → tied with someone else → {@link Status#MATCHED}</li>
 *   <li>ours == market &amp;&amp; count == 1 → that one order is us → {@link Status#COMPETITIVE}</li>
 * </ul>
 *
 * <p>We re-bid on OUTBID or MATCHED (matched = stuck behind a tie, fills dribble in). This is
 * orderbook-only, so a quiet market (nobody instaselling) does NOT look like an outbid — unlike
 * a fill-rate/timing heuristic.
 */
public final class FlipMonitor {
    private FlipMonitor() {}

    public enum Status { COMPETITIVE, MATCHED, OUTBID, UNKNOWN }

    /** Classify our buy order at {@code placedPrice}. UNKNOWN if we have no API data for it. */
    public static Status status(String idOrName, double placedPrice) {
        if (placedPrice <= 0) return Status.UNKNOWN;
        Product p = SimpleFlipper.API.get(idOrName);
        if (p == null) return Status.UNKNOWN;
        double market = p.topBuyOrder();
        if (market <= 0) return Status.UNKNOWN;
        long count = p.topBuyOrderCount();

        // Compare with a tiny epsilon so float noise doesn't read as a 0.1 gap.
        double eps = 0.05;
        if (placedPrice > market + eps) return Status.COMPETITIVE;   // we're strictly above → top
        if (placedPrice < market - eps) return Status.OUTBID;        // someone strictly above us
        // Equal (within eps): matched if more than one order sits here, else it's just us.
        return count > 1 ? Status.MATCHED : Status.COMPETITIVE;
    }

    /** True if our order should be replaced: OUTBID or MATCHED (not COMPETITIVE/UNKNOWN). */
    public static boolean shouldReBid(String idOrName, double placedPrice) {
        Status s = status(idOrName, placedPrice);
        return s == Status.OUTBID || s == Status.MATCHED;
    }
}
