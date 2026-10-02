package fr.alexdoru.partymod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.alexdoru.partymod.core.Flagger;
import fr.alexdoru.partymod.core.PlayerStats;
import fr.alexdoru.partymod.core.StatFormat;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StatFormatTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = 86_400_000L;

    private static PlayerStats stats(String player) {
        JsonObject r = new JsonParser().parse("{\"success\":true,\"player\":" + player + "}").getAsJsonObject();
        return PlayerStats.fromHypixel("0123456789abcdef0123456789abcdef", "Steve", r, NOW);
    }

    @Test
    public void rankColoursMatchHypixel() {
        assertEquals("\u00a7b[MVP\u00a72+\u00a7b] Steve", StatFormat.rankedName(stats("{\"newPackageRank\":\"MVP_PLUS\",\"rankPlusColor\":\"DARK_GREEN\"}"), "Steve"));
        assertEquals("\u00a7b[MVP\u00a7c+\u00a7b] Steve", StatFormat.rankedName(stats("{\"newPackageRank\":\"MVP_PLUS\"}"), "Steve"));
        assertEquals("\u00a76[MVP\u00a7c++\u00a76] Steve", StatFormat.rankedName(stats("{\"monthlyPackageRank\":\"SUPERSTAR\"}"), "Steve"));
        assertEquals("\u00a7b[MVP\u00a7c++\u00a7b] Steve",
                StatFormat.rankedName(stats("{\"monthlyPackageRank\":\"SUPERSTAR\",\"monthlyRankColor\":\"AQUA\"}"), "Steve"));
        assertEquals("\u00a7a[VIP\u00a76+\u00a7a] Steve", StatFormat.rankedName(stats("{\"newPackageRank\":\"VIP_PLUS\"}"), "Steve"));
        assertEquals("\u00a77Steve", StatFormat.rankedName(stats("{}"), "Steve"));
        assertEquals("\u00a77Steve", StatFormat.rankedName(null, "Steve"));
    }

    @Test
    public void cardColoursBadValuesRed() {
        PlayerStats fresh = stats("{\"firstLogin\":" + (NOW - 3 * DAY) + ",\"networkExp\":0,"
                + "\"stats\":{\"Walls3\":{\"wins\":2,\"losses\":3,\"final_kills\":1,\"final_deaths\":5}}}");
        List<String> card = StatFormat.card(fresh, new Flagger.Options(), NOW);
        assertEquals(2, card.size());
        assertTrue(card.get(0), card.get(0).contains("Level \u00a7c1"));
        assertTrue(card.get(0), card.get(0).contains("Hypixel age \u00a7c3d"));
        assertTrue(card.get(0), card.get(0).contains("Games \u00a7c5"));
        // Ratios on 5 games are not judged.
        assertTrue(card.get(1), card.get(1).contains("FKDR \u00a770.20"));
    }

    @Test
    public void cardColoursGoodValuesGreen() {
        PlayerStats vet = stats("{\"firstLogin\":" + (NOW - 1200 * DAY) + ",\"networkExp\":5000000,\"newPackageRank\":\"MVP_PLUS\","
                + "\"stats\":{\"Walls3\":{\"wins\":300,\"losses\":400,\"final_kills\":900,\"final_deaths\":600}}}");
        List<String> card = StatFormat.card(vet, new Flagger.Options(), NOW);
        assertTrue(card.get(0), card.get(0).contains("Hypixel age \u00a7a3.3y"));
        assertTrue(card.get(0), card.get(0).contains("Games \u00a7a700"));
        assertTrue(card.get(1), card.get(1).contains("FKDR \u00a7a1.50"));
        assertTrue(card.get(1), card.get(1).contains("Finals \u00a7f900"));
    }

    @Test
    public void suspiciousRatiosAreRed() {
        PlayerStats pro = stats("{\"firstLogin\":" + (NOW - 1200 * DAY) + ",\"networkExp\":5000000,"
                + "\"stats\":{\"Walls3\":{\"wins\":90,\"losses\":10,\"final_kills\":800,\"final_deaths\":40}}}");
        List<String> card = StatFormat.card(pro, new Flagger.Options(), NOW);
        assertTrue(card.get(1), card.get(1).contains("FKDR \u00a7c20.00"));
        assertTrue(card.get(1), card.get(1).contains("WLR \u00a7c9.00"));
        assertTrue(StatFormat.describe(pro, new Flagger.Options(), NOW).get(0).contains("None"));
    }
}
