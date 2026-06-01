package dev.salocin.simpleflipper.click;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

/**
 * Drives Hypixel's "Custom Amount" / "Custom Price" sign-entry screen. Hypixel opens
 * a real {@link AbstractSignEditScreen} (a server-recognized screen, unlike the
 * client-side quick-buy buttons other mods inject) to type an arbitrary value.
 *
 * <p>We type into the screen's own line buffer via {@code setMessage} (the same path
 * the screen uses for keyboard input), then close it. The screen's {@code removed()}
 * builds and sends the {@code ServerboundSignUpdatePacket} from that buffer — so we
 * get exactly one correct packet. (We deliberately do NOT hand-craft and send our own
 * packet: closing the screen always fires {@code removed()}, so a manual send would be
 * followed by a second packet from the still-empty buffer and clobber our value.)
 */
public final class SignInput {
    private SignInput() {}

    /** True when a sign editor is the open screen. */
    public static boolean isOpen() {
        return Minecraft.getInstance().screen instanceof AbstractSignEditScreen;
    }

    /**
     * Type {@code text} into the open sign and submit it. Returns false if no sign
     * editor is currently open.
     *
     * <p>Hypixel's "Custom Amount"/"Custom Price" sign arrives pre-populated: the
     * screen's {@code messages[]} array is built from the sign block entity's existing
     * text (see the {@code AbstractSignEditScreen} constructor), so lines 1-3 hold
     * Hypixel's placeholder/caret lines. Because {@code removed()} sends ALL four lines
     * in the {@code ServerboundSignUpdatePacket}, writing only line 0 leaves that
     * placeholder junk on the other lines — which Hypixel can reject as an invalid
     * request, leaving us stuck on the amount menu instead of advancing to the price
     * screen. So we clear every line, write our value to line 0 (splitting on newlines
     * for the general case), and restore the cursor line.
     *
     * <p>We also defer {@code onClose()} to the next client task rather than closing in
     * the same frame we set the text. {@code onClose()} synchronously fires
     * {@code removed()} (which sends the packet); deferring one tick lets the screen
     * settle so the value is committed before the packet goes out. Closing the screen
     * always fires {@code removed()} from the (now correct) buffer, so we get exactly
     * one packet with the right value — we deliberately do NOT hand-craft our own.
     */
    public static boolean submit(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractSignEditScreen screen)) return false;
        String[] lines = text.split("\n", 4);
        int original = screen.line;
        for (int i = 0; i < 4; i++) {
            screen.line = i;
            screen.setMessage(i < lines.length ? lines[i] : "");
        }
        screen.line = original;
        mc.execute(screen::onClose);
        return true;
    }
}
