package dev.salocin.simpleflipper.click;

import java.util.Locale;

/**
 * One declarative step in a click sequence: optionally wait for an expected menu,
 * click a matched slot, then wait for a confirmation signal before the next step.
 * Build via the static factories; tweak with the chained {@code requireTitle} /
 * {@code button} helpers.
 */
public final class Step {
    /** How we know a step's click has taken effect before moving on. */
    public enum Confirm {
        MENU_CHANGED,    // a new container (containerId) opened
        TITLE_CONTAINS,  // the open menu's title now contains confirmArg
        SLOT_CHANGED,    // the clicked slot's item changed
        CHAT_CONTAINS,   // a chat line contains confirmArg (correctness signal)
        SIGN_OPENED,     // a sign-edit screen is now open
        DELAY_ONLY       // just wait out a short fixed delay
    }

    public final String label;
    public final SlotMatcher matcher;   // null => pure wait step (no click)
    public final int button;            // 0 = left, 1 = right
    public final String expectTitle;    // require title contains this BEFORE clicking (null => none)
    public final Confirm confirm;
    public final String confirmArg;     // lowercased; for TITLE_CONTAINS / CHAT_CONTAINS
    public final int timeoutTicks;
    public final boolean abortOnTimeout;
    public final boolean fillSign;      // submit fillText into the open sign editor (no slot click)
    public final String fillText;       // text to type into the sign

    private Step(String label, SlotMatcher matcher, int button, String expectTitle,
                 Confirm confirm, String confirmArg, int timeoutTicks, boolean abortOnTimeout) {
        this(label, matcher, button, expectTitle, confirm, confirmArg, timeoutTicks, abortOnTimeout, false, null);
    }

    private Step(String label, SlotMatcher matcher, int button, String expectTitle,
                 Confirm confirm, String confirmArg, int timeoutTicks, boolean abortOnTimeout,
                 boolean fillSign, String fillText) {
        this.label = label;
        this.matcher = matcher;
        this.button = button;
        this.expectTitle = expectTitle;
        this.confirm = confirm;
        this.confirmArg = confirmArg;
        this.timeoutTicks = timeoutTicks;
        this.abortOnTimeout = abortOnTimeout;
        this.fillSign = fillSign;
        this.fillText = fillText;
    }

    /** Click, then wait for a new menu to open. */
    public static Step clickThenMenuChange(String label, SlotMatcher matcher) {
        return new Step(label, matcher, 0, null, Confirm.MENU_CHANGED, null, 40, true);
    }

    /** Click, then wait until the open menu's title contains {@code titleContains}. */
    public static Step clickThenTitle(String label, SlotMatcher matcher, String titleContains) {
        return new Step(label, matcher, 0, null, Confirm.TITLE_CONTAINS,
                titleContains.toLowerCase(Locale.ROOT), 40, true);
    }

    /** Click, then wait until a chat line contains {@code chatContains}. */
    public static Step clickThenChat(String label, SlotMatcher matcher, String chatContains) {
        return new Step(label, matcher, 0, null, Confirm.CHAT_CONTAINS,
                chatContains.toLowerCase(Locale.ROOT), 60, true);
    }

    /** Click, then wait until a sign-edit screen opens (Hypixel's Custom Amount entry). */
    public static Step clickThenSignOpen(String label, SlotMatcher matcher) {
        return new Step(label, matcher, 0, null, Confirm.SIGN_OPENED, null, 40, true);
    }

    /**
     * No slot click; type {@code text} into the open sign editor and submit, then wait
     * until the open menu's title contains {@code titleAfter} (the price screen).
     */
    public static Step fillSign(String label, String text, String titleAfter) {
        return new Step(label, null, 0, null, Confirm.TITLE_CONTAINS,
                titleAfter.toLowerCase(Locale.ROOT), 40, true, true, text);
    }

    /** Click, then wait until that slot's contents change (best-effort, no abort on timeout). */
    public static Step clickThenSlotChange(String label, SlotMatcher matcher) {
        return new Step(label, matcher, 0, null, Confirm.SLOT_CHANGED, null, 20, false);
    }

    /** No click; just wait out {@code ticks} client ticks (e.g. to let a menu's slots load). */
    public static Step delay(String label, int ticks) {
        // DELAY_ONLY satisfies at timeoutTicks/4, so scale up to wait ~ticks.
        return new Step(label, null, 0, null, Confirm.DELAY_ONLY, null, Math.max(4, ticks * 4), false);
    }

    /** No click; just wait until the open menu's title contains {@code titleContains}. */
    public static Step waitForTitle(String label, String titleContains) {
        String t = titleContains.toLowerCase(Locale.ROOT);
        return new Step(label, null, 0, t, Confirm.TITLE_CONTAINS, t, 60, true);
    }

    /** Require the menu title to contain {@code titleContains} before this step clicks. */
    public Step requireTitle(String titleContains) {
        return new Step(label, matcher, button, titleContains.toLowerCase(Locale.ROOT),
                confirm, confirmArg, timeoutTicks, abortOnTimeout, fillSign, fillText);
    }

    /** Use a different mouse button (0 left, 1 right). */
    public Step button(int b) {
        return new Step(label, matcher, b, expectTitle, confirm, confirmArg, timeoutTicks, abortOnTimeout,
                fillSign, fillText);
    }

    /** Override the confirmation timeout (in client ticks; 20 ticks = 1s). */
    public Step timeout(int ticks) {
        return new Step(label, matcher, button, expectTitle, confirm, confirmArg, ticks, abortOnTimeout,
                fillSign, fillText);
    }
}
