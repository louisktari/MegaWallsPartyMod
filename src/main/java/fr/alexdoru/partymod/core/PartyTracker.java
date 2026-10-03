package fr.alexdoru.partymod.core;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Live party state the overlays show: current members, the review queue and a
 * timestamped event log. Owned by the client thread.
 */
public final class PartyTracker {

    public enum Status { CHECKING, CLEAN, FLAGGED, UNAVAILABLE, DISMISSED, TRUSTED, REMOVED }

    public static final class Member {
        public final String name;
        /** True when the player joined while we watched; false when found via /p list. */
        public final boolean joinedLive;
        public Status status = Status.CHECKING;
        public PlayerStats stats;
        public final List<Flagger.Flag> flags = new ArrayList<>();
        /** Reason texts, kept in step with {@link #flags}. */
        public final List<String> reasons = new ArrayList<>();
        public String error = "";
        /** Name as Hypixel coloured it in the join message, used until stats arrive. */
        public String display = "";
        /** When we sent the kick; used to drop members stuck as "removing". */
        public long removedAt;
        /** When a rate-limited lookup will be retried, or 0. */
        public long retryAt;
        public ChatEvents.Role role = ChatEvents.Role.MEMBER;
        public final long joinedAt = System.currentTimeMillis();

        /** Rank-coloured name: from stats when known, else from the join message, else gray. */
        public String shown() {
            if (stats != null) return StatFormat.rankedName(stats, name);
            return display.isEmpty() ? StatFormat.GRAY + name : display;
        }

        Member(String name, boolean joinedLive) {
            this.name = name;
            this.joinedLive = joinedLive;
        }

        public void setFlags(List<Flagger.Flag> newFlags) {
            flags.clear();
            reasons.clear();
            for (Flagger.Flag f : newFlags) {
                flags.add(f);
                reasons.add(f.text);
            }
        }

        public boolean strong() {
            return Flagger.anyStrong(flags);
        }

        /** Joined live within the last minute - highlighted so the host spots new arrivals. */
        public boolean isNew(long now) {
            return joinedLive && now - joinedAt < 60_000L;
        }
    }

    public static final class LogEntry {
        public final String text, time;
        public final long at = System.currentTimeMillis();
        /** Player whose removal this entry can undo, or null. */
        public final String undoName;

        LogEntry(String text, String time, String undoName) {
            this.text = text;
            this.time = time;
            this.undoName = undoName;
        }

        public boolean undoable(long now) {
            return undoName != null && now - at < 10_000L;
        }
    }

    private static final int LOG_LIMIT = 200;
    private final Map<String, Member> members = new LinkedHashMap<>();
    /** Lower-cased names currently in the party, safe to read from the API thread. */
    private final java.util.Set<String> present = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Deque<LogEntry> log = new ArrayDeque<>();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.ROOT);

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Adds a member; returns null if they were already tracked. */
    public Member add(String name, boolean joinedLive) {
        if (members.containsKey(key(name))) return null;
        Member m = new Member(name, joinedLive);
        members.put(key(name), m);
        present.add(key(name));
        return m;
    }

    public Member get(String name) {
        return name == null ? null : members.get(key(name));
    }

    public Member remove(String name) {
        present.remove(key(name));
        return members.remove(key(name));
    }

    /** Thread-safe: is this player still in the party? Lets queued lookups skip people who left. */
    public boolean isPresent(String name) {
        return name != null && present.contains(key(name));
    }

    public Collection<Member> members() {
        return members.values();
    }

    /** Members awaiting a decision: strong flags first, then oldest first. */
    public List<Member> toReview() {
        List<Member> strong = new ArrayList<>(), weak = new ArrayList<>();
        for (Member m : members.values()) {
            if (m.status == Status.FLAGGED) (m.strong() ? strong : weak).add(m);
        }
        strong.addAll(weak);
        return strong;
    }

    public void clearMembers() {
        members.clear();
        present.clear();
    }

    public LogEntry log(String line) {
        return log(line, null);
    }

    /** A non-player event (party ended, syncing...), prefixed with a red [SYSTEM] tag. */
    public LogEntry logSystem(String line) {
        return log(StatFormat.RED + "[SYSTEM] " + StatFormat.GRAY + line, null);
    }

    public LogEntry log(String line, String undoName) {
        LogEntry e = new LogEntry(line, clock.format(new Date()), undoName);
        log.addFirst(e);
        while (log.size() > LOG_LIMIT) log.removeLast();
        return e;
    }

    /** Newest first. */
    public List<LogEntry> log(int limit) {
        List<LogEntry> out = new ArrayList<>();
        for (LogEntry e : log) {
            if (out.size() >= limit) break;
            out.add(e);
        }
        return out;
    }

    public void clearLog() {
        log.clear();
    }
}
