package fr.alexdoru.partymod;

import fr.alexdoru.partymod.data.BlockHistory;
import fr.alexdoru.partymod.data.ListShare;
import fr.alexdoru.partymod.data.TrustedStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ListShareTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private TrustedStore trusted(String name) {
        return new TrustedStore(new File(tmp.getRoot(), name + "-trusted.json"));
    }

    private BlockHistory history(String name) {
        return new BlockHistory(new File(tmp.getRoot(), name + "-history.json"));
    }

    @Test
    public void roundTripMergesWithoutOverwriting() {
        TrustedStore aliceTrusted = trusted("alice");
        BlockHistory aliceHistory = history("alice");
        aliceTrusted.add("Friend");
        aliceTrusted.add("Shared");
        aliceHistory.record("Cheater", "\u00a7cHigh MW stats (FKDR 20.00)", "Alice", 1000);
        aliceHistory.record("Alt", "New account (2d old)", "", 2000);
        aliceHistory.record("MyFriend", "Mistake", "Alice", 3000); // Bob trusts them - must not be imported
        String export = ListShare.export(aliceTrusted, aliceHistory, "Alice", 5000);
        assertFalse("colour codes are stripped from exports", export.contains("\u00a7"));

        TrustedStore bobTrusted = trusted("bob");
        BlockHistory bobHistory = history("bob");
        bobTrusted.add("Shared");
        bobTrusted.add("MyFriend");
        bobHistory.record("Cheater", "Bob's own reason", "Bob", 900);

        ListShare.Result r = ListShare.importInto(export, bobTrusted, bobHistory);
        assertTrue(r.error, r.ok());
        assertEquals(1, r.trustedAdded); // Friend (Shared already there)
        assertEquals(1, r.blockedAdded); // Alt (Cheater kept as Bob's, MyFriend is trusted)
        assertEquals("Bob's own reason", bobHistory.get("Cheater").reason);
        assertNull(bobHistory.get("MyFriend"));
        assertTrue(bobHistory.get("Alt").automatic());
        assertEquals(2000, bobHistory.get("Alt").at);

        // Persisted, and importing the same thing again changes nothing.
        assertTrue(trusted("bob").isTrusted("Friend"));
        assertEquals(2, history("bob").size());
        ListShare.Result again = ListShare.importInto(export, bobTrusted, bobHistory);
        assertEquals(0, again.trustedAdded + again.blockedAdded);
    }

    @Test
    public void rejectsForeignOrBrokenText() {
        TrustedStore t = trusted("x");
        BlockHistory h = history("x");
        assertFalse(ListShare.importInto("", t, h).ok());
        assertFalse(ListShare.importInto("hello there", t, h).ok());
        assertFalse(ListShare.importInto("{\"trusted\":[\"Alex\"]}", t, h).ok()); // no format marker
        assertFalse(ListShare.importInto("[1,2,3]", t, h).ok());
        assertTrue(t.all().isEmpty());
    }

    @Test
    public void skipsInvalidEntries() {
        String text = "{\"format\":\"megawallspartymod-lists\",\"trusted\":[\"Good\",\"bad name\",5],"
                + "\"blocked\":[{\"name\":\"Ok_1\"},{\"name\":\"../../evil\"},\"junk\"]}";
        TrustedStore t = trusted("y");
        BlockHistory h = history("y");
        ListShare.Result r = ListShare.importInto(text, t, h);
        assertTrue(r.ok());
        assertEquals(1, r.trustedAdded);
        assertEquals(1, r.blockedAdded);
        assertEquals(4, r.skipped);
    }

    @Test
    public void historyIsOrderedByRemovalTime() {
        BlockHistory h = history("z");
        h.record("Recent", "", "", 5000);
        h.record("Old", "", "", 1000);
        assertEquals("Recent", h.recent(10).get(0).name);
    }
}
