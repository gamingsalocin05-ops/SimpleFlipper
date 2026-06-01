package dev.salocin.simpleflipper.feature;

import dev.salocin.simpleflipper.click.Menu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the player's coin purse from the Skyblock sidebar scoreboard. Each sidebar
 * line is rendered as {@code teamPrefix + owner + teamSuffix}; we stitch that with
 * {@link PlayerTeam#formatNameForTeam}, strip color codes, and scan for a "Purse:"
 * (or "Piggy:") line. Returns -1 when it can't be read (not on Skyblock, sidebar
 * hidden) so callers can decline to act.
 */
public final class Purse {
    private Purse() {}

    // "Purse: 4,521,338" / "Piggy: 4,521,338 (+1,234)" — also tolerate k/m/b suffixes.
    private static final Pattern PURSE = Pattern.compile(
            "(?:purse|piggy)\\s*:\\s*([0-9][0-9,\\.]*)\\s*([kmb])?", Pattern.CASE_INSENSITIVE);

    /** Current purse in coins, or -1 if it can't be read. */
    public static long read() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return -1;
        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return -1;

        for (PlayerScoreEntry entry : sb.listPlayerScores(obj)) {
            PlayerTeam team = sb.getPlayersTeam(entry.owner());
            Component line = PlayerTeam.formatNameForTeam(team, Component.literal(entry.owner()));
            String text = Menu.strip(line.getString());
            long coins = parse(text);
            if (coins >= 0) return coins;
        }
        return -1;
    }

    /** Parse a purse value from one sidebar line; -1 if the line isn't a purse line. */
    static long parse(String line) {
        Matcher m = PURSE.matcher(line);
        if (!m.find()) return -1;
        String digits = m.group(1).replace(",", "");
        String suffix = m.group(2);
        try {
            double v = Double.parseDouble(digits);
            if (suffix != null) {
                switch (suffix.toLowerCase()) {
                    case "k" -> v *= 1_000d;
                    case "m" -> v *= 1_000_000d;
                    case "b" -> v *= 1_000_000_000d;
                    default -> { }
                }
            }
            return (long) v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
