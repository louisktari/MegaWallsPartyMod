package fr.alexdoru.partymod.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What happened during one party: running time, joins, peaks and moderation counts.
 * Feeds the live session card and the end-of-party summary. Pure Java for unit tests.
 */
public final class SessionStats {

    /** Join rate counts joins over this window, so the figure is "joins in the last minute". */
    public static final long RATE_WINDOW_MS = 60_000L;

    private long startedAt;
    private boolean approx;
    private int joins, leaves, peakSize, autoRemoved;
    private double peakRate;
    private final ArrayDeque<Long> recentJoins = new ArrayDeque<>();
    private final Set<String> flagged = new HashSet<>(), removed = new HashSet<>(), kept = new HashSet<>(), trusted = new HashSet<>();

    public void start(long now, boolean approx) {
        startedAt = now;
        this.approx = approx;
        joins = leaves = peakSize = autoRemoved = 0;
        peakRate = 0;
        recentJoins.clear();
        flagged.clear();
        removed.clear();
        kept.clear();
        trusted.clear();
    }

    public long startedAt() {
        return startedAt;
    }

    public boolean approx() {
        return approx;
    }

    public int joins() {
        return joins;
    }

    public void join(long now) {
        joins++;
        recentJoins.addLast(now);
        peakRate = Math.max(peakRate, rate(now));
    }

    public void leave() {
        leaves++;
    }

    /** Joins per minute over the last {@link #RATE_WINDOW_MS}. */
    public double rate(long now) {
        while (!recentJoins.isEmpty() && now - recentJoins.peekFirst() > RATE_WINDOW_MS) recentJoins.pollFirst();
        return recentJoins.size() * 60_000.0 / RATE_WINDOW_MS;
    }

    /** Party size including you; tracks the peak. */
    public void size(int size) {
        peakSize = Math.max(peakSize, size);
    }

    public int peakSize() {
        return peakSize;
    }

    public void flagged(String name) {
        flagged.add(key(name));
    }

    public void removed(String name, boolean automatic) {
        if (removed.add(key(name)) && automatic) autoRemoved++;
    }

    /** Undo: the player no longer counts as removed. */
    public void unremoved(String name) {
        removed.remove(key(name));
    }

    public void kept(String name) {
        kept.add(key(name));
    }

    public void trusted(String name) {
        trusted.add(key(name));
    }

    /** Worth a summary? Skips parties that were over before anything happened. */
    public boolean eventful(long now) {
        return startedAt > 0 && (joins > 0 || now - startedAt >= 60_000L);
    }

    public static String clock(long ms) {
        long secs = Math.max(0, ms / 1000);
        return secs >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", secs / 3600, secs / 60 % 60, secs % 60)
                : String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
    }

    /** Summary lines (colour-coded) for chat and the party panel. The first line is the heading. */
    public List<String> summary(long endedAt, int cap) {
        String g = StatFormat.GRAY, w = StatFormat.WHITE, sep = StatFormat.DARK_GRAY + " | " + g;
        List<String> lines = new ArrayList<>();
        lines.add(g + "Ran " + w + clock(endedAt - startedAt) + (approx ? g + "+" : "")
                + sep + "Peak size " + w + peakSize + (cap > 0 ? g + "/" + cap : ""));
        lines.add(g + "Joins " + w + joins + g + " (peak " + w + String.format(Locale.ROOT, "%.0f/min", peakRate) + g + ")"
                + sep + "Left " + w + leaves);
        lines.add(g + "Flagged " + (flagged.isEmpty() ? w : StatFormat.GOLD) + flagged.size()
                + sep + "Removed " + (removed.isEmpty() ? w : StatFormat.RED) + removed.size()
                + (autoRemoved > 0 ? g + " (" + autoRemoved + " auto)" : "")
                + sep + "Kept " + w + kept.size() + sep + "Trusted " + w + trusted.size());
        return lines;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
