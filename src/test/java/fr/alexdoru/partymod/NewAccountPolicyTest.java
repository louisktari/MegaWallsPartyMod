package fr.alexdoru.partymod;

import fr.alexdoru.partymod.core.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NewAccountPolicyTest {
    private final long now=2000000000000L;
    private PartyState party(){PartyState s=new PartyState();s.role("Host",Member.Role.LEADER,now);Member m=s.join("Alice",now);m.stats=new PlayerStats();m.status=Member.Status.LOADED;m.stats.fetchedAt=now;m.stats.firstLogin=now-86400000L;m.ageCandidate=true;return s;}
    @Test public void onlyAuthorizedFreshNewJoinsCanBeKicked(){PartyState s=party();Member m=s.get("Alice");assertTrue(NewAccountPolicy.canSend(s,"Host",m,true,90,now,true));assertFalse(NewAccountPolicy.canSend(s,"Host",m,true,90,now,false));assertFalse(NewAccountPolicy.canSend(s,"Alice",m,true,90,now,true));assertFalse(NewAccountPolicy.canSend(s,"Host",m,false,90,now,true));}
    @Test public void approvalOverridesAutomaticKicks(){PartyState s=party();Member m=s.get("Alice");m.approved=true;assertFalse(NewAccountPolicy.eligible(m,true,90,now));}
    @Test public void listMembersAreNotAutomaticallyKicked(){PartyState s=party();Member m=s.get("Alice");m.ageCandidate=false;assertFalse(NewAccountPolicy.eligible(m,true,90,now));}
    @Test public void departingOrPromotedMemberCannotBeRemoved(){PartyState s=party();Member m=s.get("Alice");s.leave("Alice");assertFalse(NewAccountPolicy.canSend(s,"Host",m,true,90,now,true));s=party();m=s.get("Alice");s.role("Alice",Member.Role.LEADER,now);assertFalse(NewAccountPolicy.canSend(s,"Host",m,true,90,now,true));}
    @Test public void ageIsRecheckedWhenTheThresholdChanges(){PartyState s=party();Member m=s.get("Alice");m.stats.firstLogin=now-30*86400000L;assertTrue(NewAccountPolicy.eligible(m,true,90,now));assertFalse(NewAccountPolicy.eligible(m,true,7,now));m.stats=null;assertFalse(NewAccountPolicy.eligible(m,true,90,now));}
}
