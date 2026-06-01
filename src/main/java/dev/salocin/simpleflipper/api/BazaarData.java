package dev.salocin.simpleflipper.api;

import java.util.Map;

/** Top-level shape of the Hypixel bazaar API response (only the fields we read). */
public final class BazaarData {
    public boolean success;
    public long lastUpdated;
    public Map<String, Product> products;
}
