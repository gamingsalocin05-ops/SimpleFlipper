package dev.salocin.simpleflipper.click;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.Locale;

/**
 * Observes incoming game chat for the click engine:
 *  - flags an abort if a direct message arrives mid-sequence (a person — likely
 *    staff — is talking to you), and
 *  - reports when an expected confirmation line (e.g. "Buy Order Setup!") shows up.
 *
 * Non-cancelling: it only reads chat, never suppresses it.
 */
public final class ChatWatcher {
    private ChatWatcher() {}

    private static volatile boolean dmAbort = false;
    private static volatile String expect = null;   // lowercased substring, or null
    private static volatile boolean expectSeen = false;

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            onLine(Menu.strip(message.getString()));
        });
    }

    static void onLine(String line) {
        String l = line.toLowerCase(Locale.ROOT);
        // Hypixel whisper format: "From [RANK] Name: ..." (and "To ..." for your own).
        if (l.startsWith("from ")) {
            dmAbort = true;
        }
        if (expect != null && l.contains(expect)) {
            expectSeen = true;
        }
    }

    /** True if a DM arrived since the last {@link #clearDmAbort()}. */
    public static boolean dmAbortRequested() {
        return dmAbort;
    }

    public static void clearDmAbort() {
        dmAbort = false;
    }

    /** Begin watching for a confirmation substring (null clears it). */
    public static void expect(String sub) {
        expect = (sub == null) ? null : sub.toLowerCase(Locale.ROOT);
        expectSeen = false;
    }

    public static boolean expectSeen() {
        return expectSeen;
    }
}
