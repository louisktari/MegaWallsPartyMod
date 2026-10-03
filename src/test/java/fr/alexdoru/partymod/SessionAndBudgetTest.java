package fr.alexdoru.partymod;

import fr.alexdoru.partymod.core.ChatEvents;
import fr.alexdoru.partymod.core.SessionStats;
import fr.alexdoru.partymod.data.ApiBudget;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionAndBudgetTest {

    private static String plain(String s) {
        return ChatEvents.stripFormatting(s);
    }

    @Test
    public void joinRateCountsOnlyTheLastMinuteAndPeakIsKept() {
        SessionStats s = new SessionStats();
        s.start(1L, false);
        for (int i = 0; i < 6; i++) s.join(i * 5_000L); // 6 joins in 25s
        assertEquals(6.0, s.rate(30_000L), 1e-9);
        assertEquals(1.0, s.rate(85_000L), 1e-9); // only the join at 25s is within the last minute
        assertEquals(0.0, s.rate(200_000L), 1e-9);
        List<String> lines = s.summary(200_000L, 100);
        assertTrue(plain(lines.get(1)), plain(lines.get(1)).contains("Joins 6 (peak 6/min)"));
    }

    @Test
    public void summaryCountsEachPlayerOnceAndUndoIsNotARemoval() {
        SessionStats s = new SessionStats();
        s.start(1_000L, true);
        s.size(5);
        s.size(42);
        s.size(30);
        s.flagged("Bob");
        s.flagged("bob");
        s.flagged("Eve");
        s.removed("Bob", true);
        s.removed("BOB", false);
        s.removed("Eve", false);
        s.unremoved("Eve");
        s.kept("Carl");
        s.trusted("Dan");
        s.leave();
        List<String> lines = s.summary(1_000L + 3_725_000L, 100);
        assertEquals("Ran 1:02:05+ | Peak size 42/100", plain(lines.get(0)));
        assertTrue(plain(lines.get(1)).endsWith("Left 1"));
        assertEquals("Flagged 2 | Removed 1 (1 auto) | Kept 1 | Trusted 1", plain(lines.get(2)));
    }

    @Test
    public void onlyEventfulPartiesGetASummary() {
        SessionStats s = new SessionStats();
        assertFalse(s.eventful(10_000L));
        s.start(1_000L, false);
        assertFalse(s.eventful(30_000L));
        assertTrue(s.eventful(61_000L));
        s.start(1_000L, false);
        s.join(1_000L);
        assertTrue(s.eventful(2_000L));
        assertEquals("0:59", SessionStats.clock(59_999L));
    }

    @Test
    public void apiBudgetReadsHeadersAndHoldsBackTheReserve() {
        ApiBudget b = new ApiBudget();
        assertFalse(b.known());
        b.update(null, "12", "30", 0);
        assertFalse(b.known()); // incomplete headers are ignored
        b.update("300", "212", "180", 0);
        assertTrue(b.known());
        assertEquals(212, b.remaining(1_000L));
        assertEquals(0, b.waitMs(1_000L));
        b.update("300", String.valueOf(ApiBudget.RESERVE), "40", 0);
        assertEquals(39_000L, b.waitMs(1_000L));
        assertEquals(300, b.remaining(40_000L)); // window reset: full again
        assertEquals(0, b.waitMs(40_000L));
        b.update("300", "abc", "40", 50_000L);
        assertEquals(300, b.remaining(50_000L)); // garbage keeps the last reading
        b.exhausted(120_000L);
        assertEquals(0, b.remaining(60_000L));
        assertEquals(60_000L, b.waitMs(60_000L));
    }

    @Test
    public void partyListDividersAreRecognisedButRealLinesAreNot() {
        assertTrue(ChatEvents.isListFiller("\u00a79\u00a7m-----------------------------------------------------"));
        assertTrue(ChatEvents.isListFiller("   "));
        assertFalse(ChatEvents.isListFiller("Party Members (3)"));
        assertFalse(ChatEvents.isListFiller("[VIP] Steve joined the party."));
        assertFalse(ChatEvents.isListFiller("---"));
    }
}
