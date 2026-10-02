package fr.alexdoru.partymod;

import fr.alexdoru.partymod.core.CommandQueue;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CommandQueueTest {

    @Test
    public void sendsInOrderWithSpacing() {
        CommandQueue q = new CommandQueue();
        List<String> sent = new ArrayList<>();
        q.add("/block add Alex", "/p kick Alex");
        q.tick(0, 1000, sent::add);
        q.tick(500, 1000, sent::add); // too soon
        assertEquals(Arrays.asList("/block add Alex"), sent);
        q.tick(1000, 1000, sent::add);
        assertEquals(Arrays.asList("/block add Alex", "/p kick Alex"), sent);
    }

    @Test
    public void urgentPairJumpsTheQueueAndKeepsOrder() {
        CommandQueue q = new CommandQueue();
        List<String> sent = new ArrayList<>();
        q.add("/p list");
        q.tick(0, 1000, sent::add);
        q.add("/block add Slow", "/p kick Slow");
        q.addUrgent("/block add Banned", "/p kick Banned");
        q.tick(100, 1000, sent::add); // urgent ignores the remaining gap
        q.tick(1100, 1000, sent::add);
        q.tick(2100, 1000, sent::add);
        assertEquals(Arrays.asList("/p list", "/block add Banned", "/p kick Banned", "/block add Slow"), sent);
    }

    @Test
    public void duplicatesAreSkipped() {
        CommandQueue q = new CommandQueue();
        q.add("/block add Alex", "/p kick Alex");
        q.add("/block add Alex", "/p kick Alex");
        assertEquals(2, q.size());
    }

    @Test
    public void throttledCommandIsRetried() {
        CommandQueue q = new CommandQueue();
        List<String> sent = new ArrayList<>();
        q.add("/block add Alex", "/p kick Alex");
        q.tick(0, 500, sent::add);
        q.throttled(100, 3000);
        q.tick(1000, 500, sent::add); // still backing off
        q.tick(3100, 500, sent::add);
        q.tick(3600, 500, sent::add);
        assertEquals(Arrays.asList("/block add Alex", "/block add Alex", "/p kick Alex"), sent);
    }
}
