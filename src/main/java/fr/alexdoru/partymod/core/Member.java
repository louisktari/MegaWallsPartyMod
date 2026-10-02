package fr.alexdoru.partymod.core;

import java.util.*;

public final class Member {
    public enum Role { LEADER, MODERATOR, MEMBER }
    public enum Status { NOT_ASSESSED, PENDING, LOADED, CACHED, UNAVAILABLE, FAILED, CANCELLED }
    public final String key;
    public String name;
    public Role role = Role.MEMBER;
    public Status status = Status.NOT_ASSESSED;
    public long joinedAt, disconnectedAt, assessedAt, membershipId, assessmentId;
    public boolean ready, approved,ageCandidate,ageKickAttempted;
    public String dismissedFingerprint = "", error = "", uuid = "";
    public PlayerStats stats;
    private String lastDataEvidence="",lastLocalEvidence="";
    public final List<String> reasons = new ArrayList<>();
    public Member(String name, long now, long id) {
        if (!validName(name)) throw new IllegalArgumentException("Invalid Minecraft name");
        this.name=name; key=key(name); joinedAt=now; membershipId=id;
    }
    public static boolean validName(String value) { return value != null && value.matches("[A-Za-z0-9_]{1,16}"); }
    public static String key(String value) { return value.toLowerCase(Locale.ROOT); }
    public void updateReview(List<String> dataReasons,List<String> localReasons,boolean dataAvailable){
        String data=String.join("|",dataReasons),local=String.join("|",localReasons);
        if(!lastLocalEvidence.equals(local)||dataAvailable&&!lastDataEvidence.equals(data))approved=false;
        if(dataAvailable)lastDataEvidence=data;
        lastLocalEvidence=local;
        reasons.clear();reasons.addAll(dataReasons);reasons.addAll(localReasons);
    }
    public void acceptStats(PlayerStats incoming,boolean cached,long now){
        if(!uuid.isEmpty()&&!uuid.equals(incoming.uuid)){approved=false;dismissedFingerprint="";lastDataEvidence="";}
        stats=incoming;uuid=incoming.uuid;assessedAt=now;status=cached?Status.CACHED:Status.LOADED;
    }
    public boolean needsReview() { return !approved && !reasons.isEmpty() && !fingerprint().equals(dismissedFingerprint); }
    public String fingerprint() { return String.join("|", reasons); }
    public String label() {
        if (approved) return "Reviewed by host";
        if (needsReview()) return "Review suggested";
        if (!reasons.isEmpty() && fingerprint().equals(dismissedFingerprint)) return "Warnings dismissed";
        if (status==Status.LOADED || status==Status.CACHED) return "No configured flags";
        return status.name().replace('_',' ').toLowerCase(Locale.ROOT);
    }
}
