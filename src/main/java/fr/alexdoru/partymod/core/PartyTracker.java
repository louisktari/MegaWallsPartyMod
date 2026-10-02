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
 * Everything the overlays show: current members, the review queue, blocked players
 * and a timestamped event log. Owned by the client thread.
 */
public final class PartyTracker {

    public enum Status { CHECKING, CLEAN, FLAGGED, UNAVAILABLE, DISMISSED, REMOVED }

    public static final class Member {
        public final String name;
        /** True when the player joined while we watched; false when found via /p list. */
        public final boolean joinedLive;
        public Status status = Status.CHECKING;
        public PlayerStats stats;
        public final List<String> reasons = new ArrayList<>();
        public String error = "";

        Member(String name, boolean joinedLive) {
            this.name = name;
            this.joinedLive = joinedLive;
        }
    }

    public static final class Blocked {
        public final String name, reason, time;

        Blocked(String name, String reason, String time) {
            this.name = name;
            this.reason = reason;
            this.time = time;
        }
    }

    private static final int LOG_LIMIT = 200;
    private final Map<String, Member> members = new LinkedHashMap<>();
    private final Deque<String> log = new ArrayDeque<>();
    private final Deque<Blocked> blocked = new ArrayDeque<>();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.ROOT);

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Adds a member; returns null if they were already tracked. */
    public Member add(String name, boolean joinedLive) {
        if (members.containsKey(key(name))) return null;
        Member m = new Member(name, joinedLive);
        members.put(key(name), m);
        return m;
    }

    public Member get(String name) {
        return name == null ? null : members.get(key(name));
    }

    public Member remove(String name) {
        return members.remove(key(name));
    }

    public Collection<Member> members() {
        return members.values();
    }

    /** Members awaiting a decision, oldest first. */
    public List<Member> toReview() {
        List<Member> list = new ArrayList<>();
        for (Member m : members.values()) if (m.status == Status.FLAGGED) list.add(m);
        return list;
    }

    public void clearMembers() {
        members.clear();
    }

    public void log(String line) {
        log.addFirst("[" + clock.format(new Date()) + "] " + line);
        while (log.size() > LOG_LIMIT) log.removeLast();
    }

    /** Newest first. */
    public List<String> log(int limit) {
        List<String> out = new ArrayList<>();
        for (String line : log) {
            if (out.size() >= limit) break;
            out.add(line);
        }
        return out;
    }

    public void recordBlocked(String name, String reason) {
        blocked.addFirst(new Blocked(name, reason, clock.format(new Date())));
        while (blocked.size() > LOG_LIMIT) blocked.removeLast();
    }

    public boolean wasBlocked(String name) {
        for (Blocked b : blocked) if (b.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Newest first. */
    public List<Blocked> blocked(int limit) {
        List<Blocked> out = new ArrayList<>();
        for (Blocked b : blocked) {
            if (out.size() >= limit) break;
            out.add(b);
        }
        return out;
    }

    public void clearLogs() {
        log.clear();
        blocked.clear();
    }
}
