package fr.alexdoru.partymod.core;

import java.util.List;
import java.util.Locale;

/** Recognises an in-progress Mega Walls match from the sidebar scoreboard. */
public final class MatchDetector {
    private MatchDetector() {}

    /**
     * Housing owners can script chat messages with any text, so automatic actions are paused there.
     */
    public static boolean inHousing(String title) {
        return title != null && title.toUpperCase(Locale.ROOT).contains("HOUSING");
    }

    /**
     * @param title sidebar title (formatting already stripped)
     * @param lines sidebar lines (formatting already stripped)
     */
    public static boolean inMegaWallsMatch(String title, List<String> lines) {
        if (title == null || !title.toUpperCase(Locale.ROOT).contains("MEGA WALLS")) return false;
        for (String raw : lines) {
            String line = raw.toLowerCase(Locale.ROOT);
            if (line.contains("walls fall") || line.contains("wither") || line.contains("deathmatch")
                    || line.contains("game end")) {
                return true;
            }
        }
        return false;
    }
}
