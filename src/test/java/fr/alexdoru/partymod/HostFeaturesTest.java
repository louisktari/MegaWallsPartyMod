package fr.alexdoru.partymod;

import fr.alexdoru.partymod.core.CommandQueue;
import fr.alexdoru.partymod.core.Flagger;
import fr.alexdoru.partymod.core.MatchDetector;
import fr.alexdoru.partymod.core.PartyTracker;
import fr.alexdoru.partymod.data.BlockHistory;
import fr.alexdoru.partymod.data.TrustedStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    public void trustedListEditableAsText() throws Exception {
        File file = new File(tmp.getRoot(), "trusted.json");
        TrustedStore store = new TrustedStore(file);
        store.setAll(TrustedStore.parse("Alex\n  Steve , bad-name!!\nNotch_1"));
        assertEquals(Arrays.asList("Alex", "Notch_1", "Steve"), new TrustedStore(file).all());
        assertTrue(TrustedStore.parse(null).isEmpty());
    }

    @Test
    public void corruptFilesAreTolerated() throws Exception {
        File file = tmp.newFile("trusted.json");
        java.nio.file.Files.write(file.toPath(), "{not json".getBytes("UTF-8"));
        TrustedStore store = new TrustedStore(file);
        assertTrue(store.all().isEmpty());
        assertTrue(store.add("Alex"));
        assertTrue(new TrustedStore(file).isTrusted("Alex"));

        File hist = tmp.newFile("history.json");
        java.nio.file.Files.write(hist.toPath(), "[1,2,{\"name\":\"bad name\"}]".getBytes("UTF-8"));
        assertEquals(0, new BlockHistory(hist).size());
    }

    @Test
    public void blockHistoryPersistsNewestFirst() throws Exception {
        File file = new File(tmp.getRoot(), "blocked.json");
        BlockHistory h = new BlockHistory(file);
        h.record("Alex", "No rank", "Louis", 1000);
        h.record("Steve", "Cannot play competitive games", "", 2000);
        h.record("alex", "Again", "Louis", 3000); // re-removal moves to newest
        BlockHistory reloaded = new BlockHistory(file);
        assertEquals(2, reloaded.size());
        assertEquals("alex", reloaded.recent(10).get(0).name);
        assertEquals("Again", reloaded.get("ALEX").reason);
        assertTrue(reloaded.get("Steve").automatic());
        assertTrue(reloaded.remove("steve"));
        assertNull(new BlockHistory(file).get("Steve"));
    }

    @Test
    public void strongAndWeakFlags() {
        long now = 1_800_000_000_000L, day = 86_400_000L;
        com.google.gson.JsonObject r = new com.google.gson.JsonParser().parse("{\"success\":true,\"player\":{\"firstLogin\":"
                + (now - 2 * day) + ",\"networkExp\":0}}").getAsJsonObject();
        List<Flagger.Flag> flags = Flagger.assess(fr.alexdoru.partymod.core.PlayerStats.fromHypixel(
                "0123456789abcdef0123456789abcdef", "Alex", r, now), new Flagger.Options(), now);
        assertTrue(Flagger.anyStrong(flags));
        assertTrue(flags.get(0).text.startsWith("New account") && flags.get(0).strong);
        for (int i = 1; i < flags.size(); i++) assertFalse(flags.get(i).text, flags.get(i).strong);

        com.google.gson.JsonObject older = new com.google.gson.JsonParser().parse("{\"success\":true,\"player\":{\"firstLogin\":"
                + (now - 20 * day) + ",\"networkExp\":0}}").getAsJsonObject();
        assertFalse(Flagger.anyStrong(Flagger.assess(fr.alexdoru.partymod.core.PlayerStats.fromHypixel(
                "0123456789abcdef0123456789abcdef", "Alex", older, now), new Flagger.Options(), now)));
    }

    @Test
    public void presetsChangeThresholds() {
        Flagger.Options o = new Flagger.Options();
        Flagger.applyPreset(3, o);
        assertEquals(90, o.newAccountDays);
        Flagger.applyPreset(1, o);
        assertFalse(o.noRank);
        Flagger.applyPreset(2, o);
        assertEquals(new Flagger.Options().minGames, o.minGames);
        Flagger.applyPreset(0, o); // Custom leaves values alone
        assertEquals(new Flagger.Options().minGames, o.minGames);
    }

    @Test
    public void reviewQueuePutsStrongFlagsFirst() {
        PartyTracker party = new PartyTracker();
        PartyTracker.Member weak = party.add("Weak", true);
        PartyTracker.Member strong = party.add("Strong", true);
        assertNull(party.add("weak", true));
        weak.status = strong.status = PartyTracker.Status.FLAGGED;
        weak.setFlags(Collections.singletonList(new Flagger.Flag("No rank", false)));
        strong.setFlags(Collections.singletonList(new Flagger.Flag("Has never joined Hypixel", true)));
        assertEquals("Strong", party.toReview().get(0).name);
        assertEquals(Collections.singletonList("No rank"), weak.reasons);
    }

    @Test
    public void logEntriesOfferUndoBriefly() {
        PartyTracker party = new PartyTracker();
        PartyTracker.LogEntry kick = party.log("Removed Alex", "Alex");
        PartyTracker.LogEntry plain = party.log("Steve joined");
        assertTrue(kick.undoable(kick.at + 5_000));
        assertFalse(kick.undoable(kick.at + 11_000));
        assertFalse(plain.undoable(plain.at));
        assertEquals("Steve joined", party.log(1).get(0).text);
    }

    @Test
    public void unsentCommandsCanBeCancelled() {
        CommandQueue q = new CommandQueue();
        List<String> sent = new ArrayList<>();
        q.add("/block add Alex", "/p kick Alex");
        q.tick(0, 1000, sent::add);
        assertFalse(q.cancel("/block add Alex")); // already sent
        assertTrue(q.cancel("/p kick Alex"));
        q.tick(5000, 1000, sent::add);
        assertEquals(Collections.singletonList("/block add Alex"), sent);
    }

    @Test
    public void rateLimitCooldownUsesServerHints() {
        assertEquals(12_000L, fr.alexdoru.partymod.data.HypixelClient.retryDelayMs("12", null, false));
        assertEquals(45_000L, fr.alexdoru.partymod.data.HypixelClient.retryDelayMs(null, "45", false));
        assertEquals(60_000L, fr.alexdoru.partymod.data.HypixelClient.retryDelayMs("soon", null, false));
        assertEquals(30_000L, fr.alexdoru.partymod.data.HypixelClient.retryDelayMs(null, null, true));
        assertEquals(1_000L, fr.alexdoru.partymod.data.HypixelClient.retryDelayMs("0", null, false));
        assertEquals(300_000L, fr.alexdoru.partymod.data.HypixelClient.retryDelayMs("99999", null, false));
    }

    @Test
    public void housingDetection() {
        assertTrue(MatchDetector.inHousing("HOUSING"));
        assertTrue(MatchDetector.inHousing("Evil's House - HOUSING"));
        assertFalse(MatchDetector.inHousing("MEGA WALLS"));
        assertFalse(MatchDetector.inHousing(null));
    }

    @Test
    public void matchDetection() {
        assertTrue(MatchDetector.inMegaWallsMatch("MEGA WALLS", Arrays.asList("Walls Fall: 05:12", "Kills: 0")));
        assertTrue(MatchDetector.inMegaWallsMatch("MEGA WALLS", Arrays.asList("[R] Wither: 600")));
        assertFalse(MatchDetector.inMegaWallsMatch("MEGA WALLS", Arrays.asList("Coins: 120", "Class: Squid")));
        assertFalse(MatchDetector.inMegaWallsMatch("BED WARS", Arrays.asList("Walls Fall")));
    }

    @Test
    public void presenceTracksJoinsLeavesAndDisband() {
        PartyTracker party = new PartyTracker();
        party.add("Alpha", true);
        party.add("Beta", true);
        assertTrue(party.isPresent("alpha"));
        party.remove("ALPHA");
        assertFalse(party.isPresent("Alpha"));
        assertTrue(party.isPresent("Beta"));
        party.clearMembers();
        assertFalse(party.isPresent("Beta"));
        assertFalse(party.isPresent(null));
    }

    @Test
    public void queuedLookupForPlayerWhoLeftSpendsNoRequests() throws Exception {
        fr.alexdoru.partymod.data.HypixelClient api = new fr.alexdoru.partymod.data.HypixelClient("test");
        java.util.concurrent.CountDownLatch skipped = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean called = new java.util.concurrent.atomic.AtomicBoolean();
        api.lookup("GoneAlready", null, "abcdefgh-1234", 0, 60, () -> {
            skipped.countDown();
            return false;
        }, r -> called.set(true));
        assertTrue(skipped.await(5, java.util.concurrent.TimeUnit.SECONDS));
        for (int i = 0; i < 50 && api.queueSize() > 0; i++) Thread.sleep(20);
        assertEquals(0, api.queueSize());
        assertFalse("no result is delivered for a skipped lookup", called.get());
        api.shutdown();
    }
}
