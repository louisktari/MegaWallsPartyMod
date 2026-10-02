package fr.alexdoru.partymod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.alexdoru.partymod.core.Flagger;
import fr.alexdoru.partymod.core.PlayerStats;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FlaggerTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = 86_400_000L;

    private static PlayerStats parse(String playerJson) {
        JsonObject response = new JsonParser().parse("{\"success\":true,\"player\":" + playerJson + "}").getAsJsonObject();
        return PlayerStats.fromHypixel("0123456789abcdef0123456789abcdef", "Steve", response, NOW);
    }

    private static String veteran(String extra) {
        return "{\"firstLogin\":" + (NOW - 1000 * DAY) + ",\"networkExp\":5000000,\"newPackageRank\":\"MVP_PLUS\""
                + ",\"stats\":{\"Walls3\":{\"wins\":300,\"losses\":400,\"final_kills\":900,\"final_deaths\":600}}" + extra + "}";
    }

    private static boolean has(List<String> reasons, String prefix) {
        for (String r : reasons) if (r.startsWith(prefix)) return true;
        return false;
    }

    @Test
    public void establishedPlayerIsClean() {
        PlayerStats s = parse(veteran(""));
        assertEquals("MVP+", s.rank);
        assertEquals(Integer.valueOf(700), s.games());
        assertTrue(Flagger.evaluate(s, new Flagger.Options(), NOW).isEmpty());
    }

    @Test
    public void freshUnrankedAccountIsFlagged() {
        PlayerStats s = parse("{\"firstLogin\":" + (NOW - 3 * DAY) + ",\"networkExp\":0}");
        assertNull(s.rank);
        List<String> reasons = Flagger.evaluate(s, new Flagger.Options(), NOW);
        assertTrue(reasons.toString(), has(reasons, "New account (3d old)"));
        assertTrue(has(reasons, "No rank"));
        assertTrue(has(reasons, "Low network level"));
        assertTrue(has(reasons, "Few MW games (0)"));
        // Ratios are meaningless on zero games.
        assertFalse(has(reasons, "Low MW stats"));
        assertFalse(has(reasons, "High MW stats"));
    }

    @Test
    public void noHypixelProfileIsFlagged() {
        JsonObject response = new JsonParser().parse("{\"success\":true,\"player\":null}").getAsJsonObject();
        PlayerStats s = PlayerStats.fromHypixel("0123456789abcdef0123456789abcdef", "Steve", response, NOW);
        assertFalse(s.hasProfile);
        assertEquals(1, Flagger.evaluate(s, new Flagger.Options(), NOW).size());
    }

    @Test
    public void highAndLowStatsAreFlagged() {
        PlayerStats high = parse("{\"firstLogin\":" + (NOW - 900 * DAY) + ",\"networkExp\":5000000,\"monthlyPackageRank\":\"SUPERSTAR\""
                + ",\"stats\":{\"Walls3\":{\"wins\":90,\"losses\":10,\"final_kills\":800,\"final_deaths\":40}}}");
        assertEquals("MVP++", high.rank);
        List<String> highReasons = Flagger.evaluate(high, new Flagger.Options(), NOW);
        assertTrue(highReasons.toString(), has(highReasons, "High MW stats (FKDR 20.00)"));
        assertTrue(has(highReasons, "High MW stats (WLR 9.00)"));

        PlayerStats low = parse("{\"firstLogin\":" + (NOW - 900 * DAY) + ",\"networkExp\":5000000,\"newPackageRank\":\"VIP\""
                + ",\"stats\":{\"Walls3\":{\"wins\":10,\"losses\":90,\"final_kills\":20,\"final_deaths\":100}}}");
        List<String> lowReasons = Flagger.evaluate(low, new Flagger.Options(), NOW);
        assertTrue(lowReasons.toString(), has(lowReasons, "Low MW stats (FKDR 0.20)"));
        assertFalse(has(lowReasons, "High MW stats"));
    }

    @Test
    public void disabledRulesDoNotFlag() {
        PlayerStats s = parse("{\"firstLogin\":" + (NOW - 3 * DAY) + ",\"networkExp\":0}");
        Flagger.Options off = new Flagger.Options();
        off.newAccount = off.noRank = off.lowLevel = off.fewGames = off.lowStats = off.highStats = false;
        assertTrue(Flagger.evaluate(s, off, NOW).isEmpty());
    }

    @Test
    public void staffAndLegacyRanksResolve() {
        assertEquals("ADMIN", parse("{\"rank\":\"ADMIN\",\"newPackageRank\":\"MVP_PLUS\"}").rank);
        assertEquals("VIP+", parse("{\"packageRank\":\"VIP_PLUS\"}").rank);
        assertNull(parse("{\"rank\":\"NORMAL\",\"newPackageRank\":\"NONE\"}").rank);
    }

    @Test
    public void networkLevelFormula() {
        assertEquals(1.0, parse("{\"networkExp\":0}").networkLevel, 1e-9);
        assertEquals(2.0, parse("{\"networkExp\":10000}").networkLevel, 1e-9);
    }
}
