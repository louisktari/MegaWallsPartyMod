package fr.alexdoru.partymod.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns stats into colour-coded text (Minecraft section-sign codes) so the host can
 * judge a player at a glance: green is fine, yellow is borderline, red is a flag.
 * Pure Java so it can be unit-tested without Minecraft.
 */
public final class StatFormat {
    public static final String GREEN = "\u00a7a", YELLOW = "\u00a7e", RED = "\u00a7c", GOLD = "\u00a76",
            GRAY = "\u00a77", DARK_GRAY = "\u00a78", WHITE = "\u00a7f", AQUA = "\u00a7b", RESET = "\u00a7r";

    private static final Map<String, String> HYPIXEL_COLORS;

    static {
        Map<String, String> m = new HashMap<>();
        String[][] pairs = {{"BLACK", "0"}, {"DARK_BLUE", "1"}, {"DARK_GREEN", "2"}, {"DARK_AQUA", "3"}, {"DARK_RED", "4"},
                {"DARK_PURPLE", "5"}, {"GOLD", "6"}, {"GRAY", "7"}, {"DARK_GRAY", "8"}, {"BLUE", "9"}, {"GREEN", "a"},
                {"AQUA", "b"}, {"RED", "c"}, {"LIGHT_PURPLE", "d"}, {"YELLOW", "e"}, {"WHITE", "f"}};
        for (String[] p : pairs) m.put(p[0], "\u00a7" + p[1]);
        HYPIXEL_COLORS = Collections.unmodifiableMap(m);
    }

    private StatFormat() {}

    /** Hypixel colour name such as "RED" to a section code, or the fallback. */
    static String color(String hypixelName, String fallback) {
        String c = hypixelName == null ? null : HYPIXEL_COLORS.get(hypixelName.toUpperCase(Locale.ROOT));
        return c == null ? fallback : c;
    }

    /** "[MVP+] Name" coloured the way Hypixel shows it; plain gray name when there is no rank. */
    public static String rankedName(PlayerStats s, String name) {
        if (s == null || s.rank == null) return GRAY + name;
        String plus = color(s.plusColor, RED);
        switch (s.rank) {
            case "VIP": return GREEN + "[VIP] " + name;
            case "VIP+": return GREEN + "[VIP" + GOLD + "+" + GREEN + "] " + name;
            case "MVP": return AQUA + "[MVP] " + name;
            case "MVP+": return AQUA + "[MVP" + plus + "+" + AQUA + "] " + name;
            case "MVP++": {
                String base = color(s.monthlyColor, GOLD);
                return base + "[MVP" + plus + "++" + base + "] " + name;
            }
            case "YOUTUBE": return RED + "[" + WHITE + "YOUTUBE" + RED + "] " + name;
            case "ADMIN": case "OWNER": return RED + "[" + s.rank + "] " + name;
            case "MODERATOR": return "\u00a72[MOD] " + name;
            case "GAME_MASTER": return "\u00a72[GM] " + name;
            case "HELPER": return "\u00a79[HELPER] " + name;
            default: return GRAY + "[" + s.rank + "] " + name;
        }
    }

    /** Lower is worse: red below {@code bad}, yellow below {@code ok}, green otherwise. */
    static String atLeast(double value, double bad, double ok) {
        return value < bad ? RED : value < ok ? YELLOW : GREEN;
    }

    /** Ratio colour: red outside [low, high], yellow within 25% of high, green otherwise. */
    static String ratio(double value, double low, double high, boolean checkLow) {
        if (value > high || checkLow && value < low) return RED;
        if (value > high * 0.75) return YELLOW;
        return GREEN;
    }

    public static String age(long days) {
        if (days >= 365) return String.format(Locale.ROOT, "%.1fy", days / 365.0);
        return days + "d";
    }

    private static String sep() {
        return DARK_GRAY + " | ";
    }

    /** Two compact colour-coded lines for a player card. */
    public static List<String> card(PlayerStats s, Flagger.Options o, long now) {
        List<String> lines = new ArrayList<>();
        if (s == null) return lines;
        if (!s.hasProfile) {
            lines.add(RED + "Never joined Hypixel");
            return lines;
        }
        StringBuilder a = new StringBuilder();
        if (s.networkLevel != null) {
            a.append(GRAY).append("Level ").append(atLeast(s.networkLevel, o.minNetworkLevel, o.minNetworkLevel * 3))
                    .append((int) Math.floor(s.networkLevel));
        }
        if (s.firstLogin != null) {
            long days = Math.max(0, (now - s.firstLogin) / 86_400_000L);
            if (a.length() > 0) a.append(sep());
            a.append(GRAY).append("Hypixel age ").append(atLeast(days, o.newAccountDays, Math.max(180, o.newAccountDays * 2))).append(age(days));
        }
        Integer games = s.games();
        if (games != null) {
            if (a.length() > 0) a.append(sep());
            a.append(GRAY).append("Games ").append(atLeast(games, o.minGames, o.minGames * 4)).append(String.format(Locale.ROOT, "%,d", games));
        }
        lines.add(a.toString());
        StringBuilder b = new StringBuilder();
        boolean enough = games != null && games >= Flagger.RATIO_MIN_GAMES;
        Double fkd = s.fkd(), wl = s.wl();
        b.append(GRAY).append("FKDR ").append(fkd == null ? GRAY + "?" : (enough ? ratio(fkd, o.lowFkd, o.highFkd, true) : GRAY)
                + String.format(Locale.ROOT, "%.2f", fkd));
        b.append(sep()).append(GRAY).append("WLR ").append(wl == null ? GRAY + "?" : (enough ? ratio(wl, 0, o.highWl, false) : GRAY)
                + String.format(Locale.ROOT, "%.2f", wl));
        b.append(sep()).append(GRAY).append("Finals ").append(WHITE).append(String.format(Locale.ROOT, "%,d", s.finalKills));
        lines.add(b.toString());
        return lines;
    }

    /** Full colour-coded detail for the hover tooltip. */
    public static List<String> describe(PlayerStats s, Flagger.Options o, long now) {
        List<String> lines = new ArrayList<>();
        if (s == null) return lines;
        if (!s.hasProfile) {
            lines.add(RED + "Has never joined Hypixel");
            return lines;
        }
        lines.add(GRAY + "Rank: " + (s.rank == null ? RED + "None" : rankedName(s, "").trim()));
        if (s.networkLevel != null) {
            lines.add(GRAY + "Network level: " + atLeast(s.networkLevel, o.minNetworkLevel, o.minNetworkLevel * 3)
                    + String.format(Locale.ROOT, "%.1f", s.networkLevel));
        }
        if (s.firstLogin != null) {
            long days = Math.max(0, (now - s.firstLogin) / 86_400_000L);
            lines.add(GRAY + "Hypixel account age: " + atLeast(days, o.newAccountDays, Math.max(180, o.newAccountDays * 2))
                    + age(days) + GRAY + " (" + days + " days)");
        }
        Integer games = s.games();
        boolean enough = games != null && games >= Flagger.RATIO_MIN_GAMES;
        if (games != null) {
            lines.add(GRAY + "MW games: " + atLeast(games, o.minGames, o.minGames * 4) + String.format(Locale.ROOT, "%,d", games)
                    + GRAY + "  (" + GREEN + "W " + s.wins + GRAY + " / " + RED + "L " + s.losses + GRAY + ")");
        }
        Double fkd = s.fkd(), wl = s.wl();
        if (fkd != null) {
            lines.add(GRAY + "FKDR: " + (enough ? ratio(fkd, o.lowFkd, o.highFkd, true) : GRAY) + String.format(Locale.ROOT, "%.2f", fkd)
                    + GRAY + "  (" + WHITE + String.format(Locale.ROOT, "%,d", s.finalKills) + GRAY + " finals / "
                    + WHITE + String.format(Locale.ROOT, "%,d", s.finalDeaths) + GRAY + " final deaths)");
        }
        if (wl != null) {
            lines.add(GRAY + "WLR: " + (enough ? ratio(wl, 0, o.highWl, false) : GRAY) + String.format(Locale.ROOT, "%.2f", wl));
        }
        return lines;
    }
}
