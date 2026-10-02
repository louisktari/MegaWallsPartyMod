package fr.alexdoru.partymod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.alexdoru.partymod.core.PartyTracker;
import fr.alexdoru.partymod.core.PlayerStats;
import fr.alexdoru.partymod.data.TrustedStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HostFeaturesTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void trustedListPersistsCaseInsensitively() throws Exception {
        File file = new File(tmp.getRoot(), "nested/trusted.json");
        TrustedStore store = new TrustedStore(file);
        assertTrue(store.add("Steve"));
        assertFalse(store.add("steve"));
        assertFalse(store.add("not a name"));
        TrustedStore reloaded = new TrustedStore(file);
        assertTrue(reloaded.isTrusted("STEVE"));
        assertTrue(reloaded.remove("steve"));
        assertFalse(new TrustedStore(file).isTrusted("Steve"));
    }

    @Test
    public void corruptTrustedFileIsTolerated() throws Exception {
        File file = tmp.newFile("trusted.json");
        java.nio.file.Files.write(file.toPath(), "{not json".getBytes("UTF-8"));
        TrustedStore store = new TrustedStore(file);
        assertTrue(store.all().isEmpty());
        assertTrue(store.add("Alex"));
        assertTrue(new TrustedStore(file).isTrusted("Alex"));
    }

    @Test
    public void statsSummaryAndTooltip() {
        JsonObject response = new JsonParser().parse("{\"success\":true,\"player\":{\"firstLogin\":0,\"networkExp\":10000,"
                + "\"newPackageRank\":\"MVP_PLUS\",\"stats\":{\"Walls3\":{\"wins\":3,\"losses\":1,\"final_kills\":6,\"final_deaths\":4}}}}")
                .getAsJsonObject();
        PlayerStats s = PlayerStats.fromHypixel("0123456789abcdef0123456789abcdef", "Steve", response, 86_400_000L * 10);
        assertNull(s.firstLogin); // 0 means unknown, not 1970
        assertEquals("[MVP+] 4g 1.50 FKDR", s.brief());
        List<String> lines = s.describe(86_400_000L * 10);
        assertEquals("Rank: MVP+", lines.get(0));
        assertTrue(lines.toString(), lines.contains("FKDR: 1.50   WLR: 3.00"));
    }

    @Test
    public void reviewQueueAndBlockedList() {
        PartyTracker party = new PartyTracker();
        PartyTracker.Member a = party.add("Alex", true);
        party.add("Steve", true);
        assertNull(party.add("alex", true));
        a.status = PartyTracker.Status.FLAGGED;
        assertEquals(1, party.toReview().size());
        party.recordBlocked("Alex", "No rank");
        assertTrue(party.wasBlocked("ALEX"));
        assertTrue(party.unrecordBlocked("alex"));
        assertFalse(party.wasBlocked("Alex"));
    }
}
