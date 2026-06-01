package dev.salocin.simpleflipper.click;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates a button slot inside an open Hypixel menu by its item's display name
 * and/or lore (color-stripped, case-insensitive), with an optional fixed slot
 * index as a fallback when the text match fails. This is the "name/lore match +
 * index fallback" strategy: robust to Hypixel shuffling slots, but still works
 * if a button ever loses stable text.
 */
public final class SlotMatcher {
    private final String nameEquals;     // exact name (lowercased) or null
    private final String nameContains;   // name substring (lowercased) or null
    private final String loreContains;   // lore substring (lowercased) or null
    private final int indexFallback;     // menu-slot id fallback, or -1
    private final ToIntFunction<AbstractContainerMenu> resolver; // dynamic slot picker, or null
    private final String resolverDesc;   // human label for the resolver

    private SlotMatcher(String nameEquals, String nameContains, String loreContains, int indexFallback) {
        this(nameEquals, nameContains, loreContains, indexFallback, null, null);
    }

    private SlotMatcher(String nameEquals, String nameContains, String loreContains, int indexFallback,
                        ToIntFunction<AbstractContainerMenu> resolver, String resolverDesc) {
        this.nameEquals = nameEquals;
        this.nameContains = nameContains;
        this.loreContains = loreContains;
        this.indexFallback = indexFallback;
        this.resolver = resolver;
        this.resolverDesc = resolverDesc;
    }

    /**
     * Pick the bazaar amount-menu "Buy N" button whose N is the largest that does
     * not exceed {@code targetUnits}. These are the fixed presets (64/160/1024/max)
     * plus any BazaarUtils custom-order buttons (named "Buy 30000" etc.). Falls back
     * to the smallest available preset if every preset exceeds the target.
     */
    public static SlotMatcher amountAtMost(long targetUnits) {
        return new SlotMatcher(null, null, null, -1,
                menu -> bestAmountSlot(menu, targetUnits), "amount≤" + targetUnits);
    }

    private static int bestAmountSlot(AbstractContainerMenu menu, long target) {
        int bestLeSlot = -1; long bestLe = Long.MIN_VALUE;   // largest N ≤ target
        int minSlot = -1; long minN = Long.MAX_VALUE;        // smallest N overall (fallback)
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem()) continue;
            long n = amountOf(slot);
            if (n <= 0) continue;
            if (n < minN) { minN = n; minSlot = i; }
            if (n <= target && n > bestLe) { bestLe = n; bestLeSlot = i; }
        }
        return bestLeSlot >= 0 ? bestLeSlot : minSlot;
    }

    /**
     * Units a genuine Hypixel amount-preset button represents, or -1. We key ONLY off the
     * "Amount: Nx" lore line that Hypixel's real presets carry ("Buy a stack!" 64x, "Buy a
     * big stack!" 160x, "Buy a thousand!" 1,024x). We deliberately do NOT match button names
     * like "Buy 5000": other bazaar mods inject quick-buy buttons with those names (and no
     * lore) into this menu, and clicking them doesn't advance to the price screen — matching
     * one aborts the place sequence. Custom Amount (a sign-entry button) has no "Amount:" line,
     * so it's skipped too.
     */
    private static long amountOf(Slot slot) {
        for (String line : Menu.loreOf(slot)) {
            String l = line.toLowerCase(Locale.ROOT);
            int idx = l.indexOf("amount:");
            if (idx >= 0) {
                Matcher a = Pattern.compile("([0-9,]+)").matcher(l.substring(idx));
                if (a.find()) return parse(a.group(1));
            }
        }
        return -1;
    }

    private static long parse(String s) {
        try { return Long.parseLong(s.replace(",", "")); } catch (NumberFormatException e) { return -1; }
    }

    public static SlotMatcher name(String exact) {
        return new SlotMatcher(exact.toLowerCase(Locale.ROOT), null, null, -1);
    }

    /** Match a fixed slot index directly (no text match) — used when we already know the exact row. */
    public static SlotMatcher index(int slotId) {
        return new SlotMatcher(null, null, null, slotId);
    }

    public static SlotMatcher nameContains(String sub) {
        return new SlotMatcher(null, sub.toLowerCase(Locale.ROOT), null, -1);
    }

    public static SlotMatcher lore(String sub) {
        return new SlotMatcher(null, null, sub.toLowerCase(Locale.ROOT), -1);
    }

    /** Additionally require a lore line to contain {@code sub}. */
    public SlotMatcher andLore(String sub) {
        return new SlotMatcher(nameEquals, nameContains, sub.toLowerCase(Locale.ROOT), indexFallback,
                resolver, resolverDesc);
    }

    /** Fall back to this menu-slot id if no text match is found. */
    public SlotMatcher orIndex(int slotId) {
        return new SlotMatcher(nameEquals, nameContains, loreContains, slotId, resolver, resolverDesc);
    }

    /**
     * The menu-slot id (position in {@code menu.slots}) of the first text match,
     * else the index fallback if set/valid, else -1. The returned id is exactly
     * what {@code handleInventoryMouseClick} expects.
     */
    public int find(AbstractContainerMenu menu) {
        if (menu == null) return -1;
        if (resolver != null) {
            int r = resolver.applyAsInt(menu);
            if (r >= 0 && r < menu.slots.size()) return r;
        }
        for (int i = 0; i < menu.slots.size(); i++) {
            if (matches(menu.slots.get(i))) return i;
        }
        if (indexFallback >= 0 && indexFallback < menu.slots.size()) return indexFallback;
        return -1;
    }

    private boolean matches(Slot slot) {
        if (slot == null || !slot.hasItem()) return false;
        if (nameEquals == null && nameContains == null && loreContains == null) return false;

        String name = Menu.lower(Menu.nameOf(slot));
        if (nameEquals != null && !name.equals(nameEquals)) return false;
        if (nameContains != null && !name.contains(nameContains)) return false;
        if (loreContains != null) {
            boolean found = false;
            for (String line : Menu.loreOf(slot)) {
                if (line.toLowerCase(Locale.ROOT).contains(loreContains)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    /** Human-readable description for logs/abort messages. */
    public String describe() {
        if (resolver != null) return resolverDesc != null ? resolverDesc : "dynamic";
        StringBuilder sb = new StringBuilder();
        if (nameEquals != null) sb.append("name=\"").append(nameEquals).append('"');
        if (nameContains != null) sb.append("name~\"").append(nameContains).append('"');
        if (loreContains != null) sb.append(" lore~\"").append(loreContains).append('"');
        if (indexFallback >= 0) sb.append(" |idx=").append(indexFallback);
        return sb.toString().trim();
    }
}
