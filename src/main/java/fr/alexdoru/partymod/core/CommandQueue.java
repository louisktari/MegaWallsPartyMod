package fr.alexdoru.partymod.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Sends chat commands one at a time with a fixed gap, so a block + kick pair never
 * trips Hypixel's command spam limit. Single-threaded: owned by the client thread.
 */
public final class CommandQueue {
    private final Deque<String> queue = new ArrayDeque<>();
    private String last;
    private long nextAt;

    /** Queues commands at the back, skipping any already waiting. */
    public void add(String... commands) {
        for (String command : commands) {
            if (!queue.contains(command)) queue.addLast(command);
        }
    }

    /** Queues commands at the front, in the given order, and allows sending right away. */
    public void addUrgent(String... commands) {
        for (int i = commands.length - 1; i >= 0; i--) {
            queue.remove(commands[i]);
            queue.addFirst(commands[i]);
        }
        nextAt = 0;
    }

    /** Sends the next command if the spacing has elapsed. */
    public void tick(long now, long spacingMs, Consumer<String> sender) {
        if (queue.isEmpty() || now < nextAt) return;
        last = queue.pollFirst();
        nextAt = now + spacingMs;
        sender.accept(last);
    }

    /** Hypixel rejected the last command as too fast: wait, then send it again. */
    public void throttled(long now, long backoffMs) {
        if (last != null && !queue.contains(last)) queue.addFirst(last);
        last = null;
        nextAt = now + backoffMs;
    }

    public void clear() {
        queue.clear();
        last = null;
    }

    public int size() {
        return queue.size();
    }
}
