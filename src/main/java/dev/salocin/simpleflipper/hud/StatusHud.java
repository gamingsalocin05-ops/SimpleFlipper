package dev.salocin.simpleflipper.hud;

import dev.salocin.simpleflipper.core.Flipper;
import dev.salocin.simpleflipper.feature.Purse;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tiny always-on text HUD for the single-item flipper: state, live order count + fill, claimable,
 * total sold, and purse. Top-left corner. Not a config GUI — just a status readout.
 *
 * <p>1.21.11 render notes: register via {@link HudElementRegistry} (the old HudRenderCallback is
 * dead), the id class is {@link Identifier}, and {@code drawString} needs a non-zero alpha
 * ({@code 0xFFFFFFFF}) or it draws nothing.
 */
public final class StatusHud {
    private StatusHud() {}

    private static final int PAD = 4;
    private static final int LINE = 10;
    private static final int X = 4, Y = 4;

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("simpleflipper", "status_hud"),
                new Panel());
    }

    private static final class Panel implements HudElement {
        @Override
        public void render(GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) return;

            List<String> lines = new ArrayList<>();
            String state = switch (Flipper.stateLabel()) {
                case "running"   -> "§arunning";
                case "finishing" -> "§efinishing";
                case "paused"    -> "§epaused";
                default          -> "§7idle";
            };
            lines.add("§b§lSimple Flipper §r" + state);

            if (Flipper.isRunning()) {
                long claimed = Math.max(0, Flipper.filled() - Flipper.claimable());
                lines.add(String.format(Locale.ROOT, "§7E.Hard Stone §f%,d§7/§f%,d §8(%,d claimed)",
                        Flipper.filled(), Flipper.total(), claimed));
                lines.add(String.format(Locale.ROOT, "§7orders §f%d §7| claimable §f%,d",
                        Flipper.orderCount(), Flipper.claimable()));
            }
            lines.add(String.format(Locale.ROOT, "§7cycles §f%d §7| sold §f%,d",
                    Flipper.cycles(), Flipper.totalSold()));
            long purse = Purse.read();
            if (purse >= 0) lines.add(String.format(Locale.ROOT, "§7purse §f%,d", purse));

            int w = 0;
            for (String s : lines) w = Math.max(w, mc.font.width(strip(s)));
            g.fill(X - PAD, Y - PAD, X + w + PAD, Y + lines.size() * LINE + PAD - 2, 0x90000000);
            int yy = Y;
            for (String s : lines) {
                g.drawString(mc.font, Component.literal(s), X, yy, 0xFFFFFFFF);
                yy += LINE;
            }
        }
    }

    private static String strip(String s) {
        return s.replaceAll("§.", "").trim();
    }
}
