package dev.salocin.simpleflipper.feature;

import com.mojang.blaze3d.platform.InputConstants;
import dev.salocin.simpleflipper.click.ClickEngine;
import dev.salocin.simpleflipper.core.Flipper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * The three (and only three) controls, all GLFW-polled with edge detection so they fire even while
 * a container GUI is open (the game suppresses normal keybind dispatch during screens — and this mod
 * lives with the Bazaar/Builder menu open):
 * <ul>
 *   <li>= (EQUALS) — start / pause toggle</li>
 *   <li>DELETE / BACKSPACE — panic kill (stop everything)</li>
 *   <li>- (MINUS) — finalize (claim all + cancel, then stop)</li>
 * </ul>
 *
 * <p>Action keys are suppressed while a typing screen (chat) is focused so editing text doesn't trip
 * them.
 */
public final class Keybinds {
    private Keybinds() {}

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("simpleflipper", "main"));

    private static boolean startPauseDown = false;
    private static boolean killDown = false;
    private static boolean finishDown = false;

    public static void register() {
        // Registered so they appear (rebindable) in Controls; actual handling is GLFW-polled below.
        KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.simpleflipper.start_pause", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, CATEGORY));
        KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.simpleflipper.kill", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DELETE, CATEGORY));
        KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.simpleflipper.finalize", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(Keybinds::onTick);
    }

    private static void onTick(Minecraft mc) {
        if (mc.getWindow() == null) return;
        boolean typing = mc.screen instanceof ChatScreen;
        var win = mc.getWindow();

        // Start / pause toggle.
        boolean sp = !typing && InputConstants.isKeyDown(win, GLFW.GLFW_KEY_EQUAL);
        if (sp && !startPauseDown) {
            if (Flipper.isRunning() && !Flipper.isPaused()) Flipper.pause();
            else Flipper.start();
        }
        startPauseDown = sp;

        // Panic kill. macOS "delete" is Backspace, so honour both.
        boolean k = !typing && (InputConstants.isKeyDown(win, GLFW.GLFW_KEY_DELETE)
                || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_BACKSPACE));
        if (k && !killDown) {
            Flipper.kill("panic key");
            ClickEngine.killSwitch();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§b§lSimple Flipper §c§lSTOPPED"), false);
            }
        }
        killDown = k;

        // Finalize.
        boolean f = !typing && InputConstants.isKeyDown(win, GLFW.GLFW_KEY_MINUS);
        if (f && !finishDown) Flipper.finish();
        finishDown = f;
    }
}
