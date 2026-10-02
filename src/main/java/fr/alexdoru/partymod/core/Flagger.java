package fr.alexdoru.partymod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Turns a player's stats into a list of human-readable reasons to review them. */
public final class Flagger {

    /** Thresholds; populated from the OneConfig settings. */
    public static final class Options {
        public boolean noProfile = true;
        public boolean newAccount = true;
        public int newAccountDays = 30;
        public boolean noRank = true;
        public boolean lowLevel = true;
        public double minNetworkLevel = 10;
        public boolean fewGames = true;
        public int minGames = 50;
        public boolean lowStats = true;
        public double lowFkd = 0.5;
        public boolean highStats = true;
        public double highFkd = 5.0;
        public double highWl = 2.5;
    }

    /** High/low ratios on a tiny sample are noise; require this many games first. */
    static final int RATIO_MIN_GAMES = 10;

    private Flagger() {}

    public static List<String> evaluate(PlayerStats s, Options o, long now) {
        List<String> reasons = new ArrayList<>();
        if (s == null) return reasons;
        if (!s.hasProfile) {
            if (o.noProfile) reasons.add("Has never joined Hypixel");
            return reasons;
        }
        if (o.newAccount && s.firstLogin != null && s.firstLogin <= now) {
            long days = (now - s.firstLogin) / 86_400_000L;
            if (days < o.newAccountDays) reasons.add("New account (" + days + "d old)");
        }
        if (o.noRank && s.rank == null) reasons.add("No rank");
        if (o.lowLevel && s.networkLevel != null && s.networkLevel < o.minNetworkLevel) {
            reasons.add(String.format(Locale.ROOT, "Low network level (%.1f)", s.networkLevel));
        }
        Integer games = s.games();
        if (o.fewGames && games != null && games < o.minGames) {
            reasons.add("Few MW games (" + games + ")");
        }
        Double fkd = s.fkd();
        Double wl = s.wl();
        boolean enoughGames = games != null && games >= RATIO_MIN_GAMES;
        if (o.lowStats && enoughGames && fkd != null && fkd < o.lowFkd) {
            reasons.add(String.format(Locale.ROOT, "Low MW stats (FKDR %.2f)", fkd));
        }
        if (o.highStats && enoughGames) {
            if (fkd != null && fkd > o.highFkd) reasons.add(String.format(Locale.ROOT, "High MW stats (FKDR %.2f)", fkd));
            if (wl != null && wl > o.highWl) reasons.add(String.format(Locale.ROOT, "High MW stats (WLR %.2f)", wl));
        }
        return reasons;
    }
}
