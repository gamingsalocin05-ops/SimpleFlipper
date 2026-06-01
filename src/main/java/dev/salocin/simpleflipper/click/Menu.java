package dev.salocin.simpleflipper.click;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only helpers for the currently-open chest GUI. Hypixel menus are plain
 * containers whose "buttons" are items with display names + lore, so everything
 * the click engine needs is just slot/item inspection.
 */
public final class Menu {
    private Menu() {}

    /** The open container screen, or null if none (or it's not a container). */
    public static AbstractContainerScreen<?> currentContainer() {
        Minecraft mc = Minecraft.getInstance();
        return (mc.screen instanceof AbstractContainerScreen<?> s) ? s : null;
    }

    /** The open container's menu, or null. */
    public static AbstractContainerMenu currentMenu() {
        AbstractContainerScreen<?> s = currentContainer();
        return s == null ? null : s.getMenu();
    }

    /** Sync/container id of the open menu, or -1. */
    public static int containerId() {
        AbstractContainerMenu m = currentMenu();
        return m == null ? -1 : m.containerId;
    }

    /** Color-stripped, trimmed title of the open container, or "". */
    public static String title() {
        AbstractContainerScreen<?> s = currentContainer();
        return s == null ? "" : strip(s.getTitle().getString());
    }

    /** Color-stripped display name of a slot's item ("" if empty). */
    public static String nameOf(Slot slot) {
        if (slot == null || !slot.hasItem()) return "";
        return strip(slot.getItem().getHoverName().getString());
    }

    /** Color-stripped lore lines of a slot's item (empty list if none). */
    public static List<String> loreOf(Slot slot) {
        List<String> out = new ArrayList<>();
        if (slot == null || !slot.hasItem()) return out;
        ItemLore lore = slot.getItem().get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) out.add(strip(line.getString()));
        }
        return out;
    }

    /** Strip Minecraft section-sign (§) color/format codes and trim. */
    public static String strip(String s) {
        if (s == null) return "";
        return s.replaceAll("§.", "").trim();
    }

    /** Strip + lowercase, for case-insensitive matching. */
    public static String lower(String s) {
        return strip(s).toLowerCase(Locale.ROOT);
    }
}
