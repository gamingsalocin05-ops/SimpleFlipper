package dev.salocin.simpleflipper.config;

/**
 * Dead-simple, hardcoded settings holder. Simple Flipper has no GUI, no commands, and no JSON
 * persistence — everything is baked in here. Exposed as a {@code get()} singleton so the other
 * classes ({@code OrderSizer}, etc.) can read it.
 */
public final class Config {
    /** Units per buy order (Hypixel per-order max is 71,680). Kept modest so the purse covers
     *  up to two concurrent orders during a place-before-clear outbid overlap. */
    public final long orderUnits = 30_000;
    /** Fraction of the purse a single order may spend when auto-sizing (leaves a buffer). */
    public final double orderBudgetFraction = 0.95;
    /** Seconds between live bazaar polls (used for order sizing + outbid detection). */
    public final int apiPollSeconds = 15;

    // ---- drain-loop pacing (20 ticks = 1s) ----
    public final int sellSettleTicks = 3;
    public final int claimSettleTicks = 6;
    public final int claimGraceTicks = 40;
    public final int menuWaitTimeoutTicks = 80;
    public final int fillWaitTimeoutTicks = 60;
    /** Claim a batch once at least this many units are claimable (a completed order claims any remainder). */
    public final long minClaimUnits = 2240;
    /** Idle pause (ticks) between order-menu snapshots, randomized in [min,max] to look less robotic. */
    public final int refillPollMinTicks = 40;
    public final int refillPollMaxTicks = 120;
    /** Random jitter (ms, 0..this) sprinkled into settles to vary timing. */
    public final int clickJitterMs = 150;

    private static final Config INSTANCE = new Config();

    public static Config get() {
        return INSTANCE;
    }

    private Config() {}
}
