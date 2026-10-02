package fr.alexdoru.partymod.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The subset of a Hypixel player profile the flag rules need. Missing values stay
 * null so absent data is never reported as a measured zero.
 */
public final class PlayerStats {
    public String uuid = "";
    public String name = "";
    /** False when Hypixel has no profile for this UUID (never joined the network). */
    public boolean hasProfile;
    public Long firstLogin;
    public Double networkLevel;
    /** Display rank, e.g. "MVP+", or null for a player with no rank. */
    public String rank;
    public Integer wins, losses, finalKills, finalDeaths;
    public long fetchedAt;

    public Integer games() {
        return wins == null || losses == null ? null : wins + losses;
    }

    /** Final kill/death ratio; finals alone when there are no final deaths. */
    public Double fkd() {
        if (finalKills == null || finalDeaths == null) return null;
        return finalDeaths == 0 ? (double) finalKills : (double) finalKills / finalDeaths;
    }

    /** Win/loss ratio; wins alone when there are no losses. */
    public Double wl() {
        if (wins == null || losses == null) return null;
        return losses == 0 ? (double) wins : (double) wins / losses;
    }

    /**
     * Builds stats from a Hypixel {@code /v2/player} response.
     *
     * @param response full JSON response ({"success":true,"player":{...}})
     */
    public static PlayerStats fromHypixel(String uuid, String name, JsonObject response, long now) {
        PlayerStats s = new PlayerStats();
        s.uuid = uuid;
        s.name = name;
        s.fetchedAt = now;
        JsonObject player = object(response, "player");
        if (player == null) return s; // never joined Hypixel
        s.hasProfile = true;
        Double first = number(player, "firstLogin");
        if (first != null && first > 0) s.firstLogin = first.longValue();
        Double xp = number(player, "networkExp");
        if (xp != null && xp >= 0) s.networkLevel = networkLevel(xp);
        s.rank = rank(player);
        JsonObject mw = object(object(player, "stats"), "Walls3");
        if (mw != null) {
            s.wins = integer(mw, "wins");
            s.losses = integer(mw, "losses");
            s.finalKills = firstInteger(mw, "final_kills", "finalKills");
            s.finalDeaths = firstInteger(mw, "final_deaths", "finalDeaths");
        }
        // Players who have never played Mega Walls genuinely have zero games.
        if (s.wins == null) s.wins = 0;
        if (s.losses == null) s.losses = 0;
        if (s.finalKills == null) s.finalKills = 0;
        if (s.finalDeaths == null) s.finalDeaths = 0;
        return s;
    }

    /** Standard Hypixel network level formula. */
    static double networkLevel(double xp) {
        return 1 + (-8750.0 + Math.sqrt(8750.0 * 8750.0 + 5000.0 * xp)) / 2500.0;
    }

    /** Resolves the player's visible rank, or null when they have none. */
    static String rank(JsonObject player) {
        String special = string(player, "rank");
        if (special != null && !special.equals("NORMAL") && !special.equals("NONE")) {
            return special.equals("YOUTUBER") ? "YOUTUBE" : special;
        }
        if ("SUPERSTAR".equals(string(player, "monthlyPackageRank"))) return "MVP++";
        String pkg = string(player, "newPackageRank");
        if (pkg == null || pkg.equals("NONE")) pkg = string(player, "packageRank");
        if (pkg == null || pkg.equals("NONE")) return null;
        return pkg.replace("_PLUS", "+");
    }

    // --- small, defensive JSON helpers (Gson 2.2.4 compatible) ---

    public static JsonObject object(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    public static String string(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
    }

    public static Double number(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
        double d = e.getAsDouble();
        return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
    }

    static Integer integer(JsonObject o, String key) {
        Double d = number(o, key);
        return d == null || d < 0 || d > Integer.MAX_VALUE ? null : d.intValue();
    }

    private static Integer firstInteger(JsonObject o, String... keys) {
        for (String key : keys) {
            Integer value = integer(o, key);
            if (value != null) return value;
        }
        return null;
    }
}
