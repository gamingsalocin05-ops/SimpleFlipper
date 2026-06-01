package dev.salocin.simpleflipper;

import dev.salocin.simpleflipper.api.BazaarApi;
import dev.salocin.simpleflipper.click.ChatWatcher;
import dev.salocin.simpleflipper.click.ClickEngine;
import dev.salocin.simpleflipper.core.Flipper;
import dev.salocin.simpleflipper.feature.Keybinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple Flipper — a deliberately minimal, single-item Bazaar flipper. It autonomously keeps one
 * competitive (top-of-book) buy order alive for Enchanted Hard Stone, drains filled goods to the
 * Builder, and replaces the order when outbid — until you pause, kill, or finalize.
 *
 * <p>No GUI, no config file, no commands. Three keys: {@code =} start/pause, {@code DELETE} kill,
 * {@code -} finalize.
 *
 * <p>Preconditions: you stand facing the Builder NPC, and {@code /managebazaarorders} works (booster
 * cookie active) so the loop can reopen the orders menu without moving.
 */
public final class SimpleFlipper implements ClientModInitializer {
    public static final String MOD_ID = "simpleflipper";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("SimpleFlipper");

    /** Keyless bazaar poll, for order sizing + outbid detection. Started by {@link Flipper#start()}. */
    public static final BazaarApi API = new BazaarApi();

    @Override
    public void onInitializeClient() {
        LOGGER.info("Simple Flipper {} initializing", VERSION);

        Keybinds.register();
        ChatWatcher.register();   // DM-abort guard used by the click engine
        dev.salocin.simpleflipper.hud.StatusHud.register();

        ClientTickEvents.END_CLIENT_TICK.register(ClickEngine::tick);
        ClientTickEvents.END_CLIENT_TICK.register(Flipper::tick);

        LOGGER.info("Simple Flipper ready — flipping {}. (= start/pause, DELETE kill, - finalize)",
                Flipper.ITEM);
    }
}
