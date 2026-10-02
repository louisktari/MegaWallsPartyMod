package fr.alexdoru.partymod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a player's stats into reasons to review them. Each reason is either
 * <b>strong</b> (almost certainly worth kicking: never joined, brand-new account) or
 * <b>weak</b> (worth a look: no rank, low level, odd ratios).
 */
public final class Flagger {

    /** Thresholds; populated from the OneConfig settings. */
    public static final class Options {
        public boolean noProfile = true;
        public boolean newAccount = true;
        public int newAccountDays = 30;
        /** Accounts younger than this are a strong flag rather than a weak one. */
        public int strongNewAccountDays = 7;
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

    public static final class Flag {
        public final String text;
        public final boolean strong;

        public Flag(String text, boolean strong) {
            this.text = text;
            this.strong = strong;
        }
    }

    /** High/low ratios on a tiny sample are noise; require this many games first. */
    static final int RATIO_MIN_GAMES = 10;

    private Flagger() {}

    public static List<Flag> assess(PlayerStats s, Options o, long now) {
        List<Flag> flags = new ArrayList<>();
        if (s == null) return flags;
        if (!s.hasProfile) {
            if (o.noProfile) flags.add(new Flag("Has never joined Hypixel", true));
            return flags;
        }
        if (o.newAccount && s.firstLogin != null && s.firstLogin <= now) {
            long days = (now - s.firstLogin) / 86_400_000L;
            if (days < o.newAccountDays) flags.add(new Flag("New account (" + days + "d old)", days < o.strongNewAccountDays));
        }
        if (o.noRank && s.rank == null) flags.add(new Flag("No rank", false));
        if (o.lowLevel && s.networkLevel != null && s.networkLevel < o.minNetworkLevel) {
            flags.add(new Flag(String.format(Locale.ROOT, "Low network level (%.1f)", s.networkLevel), false));
        }
        Integer games = s.games();
        if (o.fewGames && games != null && games < o.minGames) flags.add(new Flag("Few MW games (" + games + ")", false));
        Double fkd = s.fkd();
        Double wl = s.wl();
        boolean enoughGames = games != null && games >= RATIO_MIN_GAMES;
        if (o.lowStats && enoughGames && fkd != null && fkd < o.lowFkd) {
            flags.add(new Flag(String.format(Locale.ROOT, "Low MW stats (FKDR %.2f)", fkd), false));
        }
        if (o.highStats && enoughGames) {
            if (fkd != null && fkd > o.highFkd) flags.add(new Flag(String.format(Locale.ROOT, "High MW stats (FKDR %.2f)", fkd), false));
            if (wl != null && wl > o.highWl) flags.add(new Flag(String.format(Locale.ROOT, "High MW stats (WLR %.2f)", wl), false));
        }
        return flags;
    }

    /** Just the reason texts. */
    public static List<String> evaluate(PlayerStats s, Options o, long now) {
        List<String> reasons = new ArrayList<>();
        for (Flag f : assess(s, o, now)) reasons.add(f.text);
        return reasons;
    }

    public static boolean anyStrong(List<Flag> flags) {
        for (Flag f : flags) if (f.strong) return true;
        return false;
    }

    /** Built-in threshold presets. Index 0 is "Custom" (leave values alone). */
    public static final String[] PRESETS = {"Custom", "Lenient", "Balanced", "Strict"};

    public static void applyPreset(int preset, Options o) {
        switch (preset) {
            case 1: // Lenient
                o.newAccountDays = 7; o.strongNewAccountDays = 3; o.noRank = false; o.minNetworkLevel = 5;
                o.minGames = 10; o.lowFkd = 0.2; o.highFkd = 8; o.highWl = 4;
                break;
            case 2: // Balanced (defaults)
                Options d = new Options();
                o.newAccountDays = d.newAccountDays; o.strongNewAccountDays = d.strongNewAccountDays; o.noRank = d.noRank;
                o.minNetworkLevel = d.minNetworkLevel; o.minGames = d.minGames; o.lowFkd = d.lowFkd; o.highFkd = d.highFkd; o.highWl = d.highWl;
                break;
            case 3: // Strict
                o.newAccountDays = 90; o.strongNewAccountDays = 14; o.noRank = true; o.minNetworkLevel = 25;
                o.minGames = 150; o.lowFkd = 0.7; o.highFkd = 4; o.highWl = 2;
                break;
            default:
                break;
        }
    }
}
