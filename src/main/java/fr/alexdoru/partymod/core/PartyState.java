package fr.alexdoru.partymod.core;

import java.util.*;

/** Owned by the Minecraft thread. A roster response commits as one transaction. */
public final class PartyState {
    private final LinkedHashMap<String,Member> members = new LinkedHashMap<>();
    private LinkedHashMap<String,Member.Role> staged;
    private long revision, stagedRevision, nextMemberId;
    private int expected;
    public long generation=1, rosterAt, syncStartedAt;
    public boolean synced;
    public String round="";
    private int nextRound;
    public Collection<Member> members() { return Collections.unmodifiableCollection(members.values()); }
    public Member get(String name) { return name == null ? null : members.get(Member.key(name)); }
    public boolean fresh(long now,long seconds){return synced&&rosterAt>0&&now>=rosterAt&&now-rosterAt<Math.max(15,Math.min(300,seconds))*1000L;}
    public Member join(String name,long now) {
        Member existing=get(name);
        if(existing!=null) { existing.disconnectedAt=0; return existing; }
        if(members.size()>=256) throw new IllegalStateException("Roster limit reached");
        Member member=new Member(name,now,++nextMemberId); members.put(member.key,member); revision++;
        return member;
    }
    public Member leave(String name) { Member removed=members.remove(Member.key(name)); if(removed!=null) revision++; return removed; }
    public void role(String name,Member.Role role,long now) {
        if(role==Member.Role.LEADER&&members.values().stream().anyMatch(m->m.role==Member.Role.LEADER&&!m.key.equals(Member.key(name))))clearReady();
        if(role==Member.Role.LEADER) for(Member member:members.values()) if(member.role==role) member.role=Member.Role.MEMBER;
        join(name,now).role=role; revision++;
    }
    public void reset() { generation++; members.clear(); staged=null; syncStartedAt=0; synced=false; rosterAt=0; round=""; revision++; }
    public void invalidateWork() { generation++; for(Member member:members.values()) if(member.status==Member.Status.PENDING) member.status=Member.Status.CANCELLED; staged=null; syncStartedAt=0; synced=false; rosterAt=0; round=""; for(Member member:members.values()) member.ready=false; }
    public void beginSync(int count,long now) {
        if(count<1 || count>256) throw new IllegalArgumentException("Invalid party size");
        staged=new LinkedHashMap<>(); expected=count; stagedRevision=revision; syncStartedAt=now; synced=false;
    }
    public boolean staging() { return staged!=null; }
    public void stage(String name,Member.Role role) {
        if(staged!=null && Member.validName(name) && staged.size()<256) staged.put(name,role);
    }
    public boolean commitSync(long now) {
        if(staged==null || staged.size()!=expected || stagedRevision!=revision) return false;
        Set<String> unique=new HashSet<>();for(String name:staged.keySet())if(!unique.add(Member.key(name)))return false;
        long leaders=staged.values().stream().filter(role->role==Member.Role.LEADER).count();
        if(leaders!=1) return false;
        LinkedHashMap<String,Member> replacement=new LinkedHashMap<>();
        for(Map.Entry<String,Member.Role> entry:staged.entrySet()) {
            Member member=get(entry.getKey()); if(member==null) member=new Member(entry.getKey(),now,++nextMemberId);
            member.name=entry.getKey(); member.role=entry.getValue(); replacement.put(member.key,member);
        }
        members.clear(); members.putAll(replacement); staged=null; syncStartedAt=0; synced=true; rosterAt=now; revision++;
        return true;
    }
    public void syncFailed() { staged=null; syncStartedAt=0; synced=false; }
    public boolean valid(long run,String name,long membership) { Member m=get(name); return run==generation && m!=null && m.membershipId==membership; }
    public boolean canRemove(String self,Member target) {
        Member actor=get(self); if(actor==null || target==null || get(target.name)!=target || actor==target) return false;
        return actor.role==Member.Role.LEADER || actor.role==Member.Role.MODERATOR && target.role==Member.Role.MEMBER;
    }
    public String startReadyRound() { round="PM-"+UUID.randomUUID().toString().substring(0,6).toUpperCase(Locale.ROOT)+"-"+(++nextRound); for(Member m:members.values()) m.ready=false; return round; }
    public void clearReady(){round="";for(Member m:members.values())m.ready=false;}
    public void readyResult(String identifier,boolean sent){if(!sent&&identifier.equals(round))clearReady();}
    public boolean acknowledge(String name,String text) { Member m=get(name); if(m==null || round.isEmpty() || !text.trim().equalsIgnoreCase("ready "+round)) return false; m.ready=true; return true; }
    public int countReview() { return (int)members.values().stream().filter(Member::needsReview).count(); }
    public int countPending() { return (int)members.values().stream().filter(m->m.status==Member.Status.PENDING).count(); }
}
