package fr.alexdoru.partymod.data;

/**
 * Hypixel's request budget, read from the RateLimit-Limit / -Remaining / -Reset headers
 * on every reply. Lets the panels show "API 212/300" and lets the client hold back the
 * last few requests until the window resets, instead of running into a 429.
 */
public final class ApiBudget {

    /** Requests kept in hand; below this the client waits for the window to reset. */
    public static final int RESERVE = 5;

    private int limit = -1, remaining = -1;
    private long resetAt;

    /** Updates from response headers; unparseable or missing headers leave the last reading. */
    public synchronized void update(String limitHeader, String remainingHeader, String resetHeader, long now) {
        Integer l = parse(limitHeader), r = parse(remainingHeader), s = parse(resetHeader);
        if (l == null || r == null || l <= 0 || r < 0) return;
        limit = l;
        remaining = Math.min(r, l);
        resetAt = now + Math.max(1, s == null ? 60 : Math.min(s, 3600)) * 1000L;
    }

    /** After a 429 the budget is spent until the cooldown ends. */
    public synchronized void exhausted(long until) {
        if (limit > 0) remaining = 0;
        resetAt = Math.max(resetAt, until);
    }

    public synchronized boolean known() {
        return limit > 0;
    }

    /** Requests left now; a window that has reset counts as full again. */
    public synchronized int remaining(long now) {
        if (limit <= 0) return -1;
        return now >= resetAt ? limit : remaining;
    }

    public synchronized int limit() {
        return limit;
    }

    public synchronized long resetAt() {
        return resetAt;
    }

    /** How long to hold back before the next request (0 = go now). */
    public synchronized long waitMs(long now) {
        if (limit <= 0 || now >= resetAt || remaining > RESERVE) return 0;
        return resetAt - now;
    }

    private static Integer parse(String header) {
        if (header == null) return null;
        try {
            return Integer.parseInt(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
