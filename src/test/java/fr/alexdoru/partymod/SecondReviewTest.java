package fr.alexdoru.partymod;

import fr.alexdoru.partymod.core.*;
import fr.alexdoru.partymod.data.*;
import fr.alexdoru.partymod.ui.Notifications;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.File;
import java.util.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

public class SecondReviewTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final String id="00000000000000000000000000000001",other="00000000000000000000000000000002";
    private File file(){return new File(temp.getRoot(),"records.json");}
    private Member member(){return new Member("Alice",100,1);}
    @Test public void aTemporaryDataOutageCannotUndoApproval(){Member m=member();m.updateReview(Arrays.asList("Young account"),Collections.emptyList(),true);m.approved=true;m.updateReview(Collections.emptyList(),Collections.emptyList(),false);assertTrue(m.approved);m.updateReview(Arrays.asList("Young account"),Collections.emptyList(),true);assertTrue(m.approved);}
    @Test public void identityChangesInvalidateApprovalAndDismissal(){Member m=member();m.uuid=id;m.approved=true;m.dismissedFingerprint="Dismissed";PlayerStats stats=new PlayerStats();stats.uuid=other;m.acceptStats(stats,false,1000);assertFalse(m.approved);assertEquals("",m.dismissedFingerprint);}
    @Test public void sameIdentityAndSameEvidenceKeepTheHostDecision(){Member m=member();m.uuid=id;m.updateReview(Arrays.asList("A"),Collections.emptyList(),true);m.approved=true;PlayerStats stats=new PlayerStats();stats.uuid=id;m.acceptStats(stats,true,1000);m.updateReview(Arrays.asList("A"),Collections.emptyList(),true);assertTrue(m.approved);m.updateReview(Arrays.asList("A","B"),Collections.emptyList(),true);assertFalse(m.approved);}
    @Test public void localBlockChangesStillInvalidateApprovalWithoutApiData(){Member m=member();m.updateReview(Collections.emptyList(),Collections.emptyList(),true);m.approved=true;m.updateReview(Collections.emptyList(),Arrays.asList("Host blocklist"),false);assertFalse(m.approved);}
    @Test public void caseDuplicateRosterCannotCommitAsComplete(){PartyState s=new PartyState();s.join("Existing",1);s.beginSync(2,2);s.stage("HOST",Member.Role.LEADER);s.stage("host",Member.Role.MEMBER);assertFalse(s.commitSync(3));assertNotNull(s.get("Existing"));}
    @Test public void noneIsANameWhenTheRowContainsPlayerEvidence(){assertEquals(Collections.singletonList("None"),PartyMessages.names("[VIP] None ●"));assertEquals(Collections.singletonList("None"),PartyMessages.names("None ●"));assertTrue(PartyMessages.names("None").isEmpty());}
    @Test public void aLeaderTransferEndsTheOldReadyRound(){PartyState s=new PartyState();s.role("Host",Member.Role.LEADER,1);s.join("Alice",1);String old=s.startReadyRound();s.get("Host").ready=true;s.role("Alice",Member.Role.LEADER,2);assertEquals("",s.round);assertFalse(s.get("Host").ready);assertFalse(s.acknowledge("Host","ready "+old));}
    @Test public void stateChangeNotificationsCannotRemainStuckOnDisabled(){Notifications n=new Notifications();n.add("enabled","Enabled",true);n.add("enabled","Disabled",true);n.add("enabled","Enabled",true);assertEquals(3,n.log.size());assertEquals("Enabled",n.toast().text);}
    @Test public void routineJoinBurstsCannotHideAnOperationalProblem(){Notifications n=new Notifications();n.add("failure","Roster failed",true);for(int i=0;i<100;i++)n.add("join:"+i,"Joined "+i,false);assertEquals("Roster failed",n.toast().text);assertEquals(101,n.log.size());}
    @Test public void ownedNameHistoryCannotMoveToAnotherUuid(){LocalStore store=new LocalStore(file());LocalStore.Record old=new LocalStore.Record();old.uuid=id;old.note="Original owner";old.blocked=true;store.records.put("name:alice",old);Member m=member();m.uuid=other;LocalStore.Record current=store.record(m);assertEquals("",current.note);assertFalse(current.blocked);assertEquals("Original owner",store.records.get("name:alice").note);}
    @Test public void unresolvedNamesMustNotEraseKnownRecordOwnership(){LocalStore store=new LocalStore(file());LocalStore.Record old=new LocalStore.Record();old.uuid=id;old.note="Original owner";store.records.put("name:alice",old);assertEquals("",store.record(member()).note);assertEquals(id,old.uuid);}
    @Test public void recordOverflowMergesIntoAnExistingUuid(){LocalStore store=new LocalStore(file());for(int i=0;i<1999;i++)store.record(new Member("P"+i,1,i));LocalStore.Record known=new LocalStore.Record();known.uuid=id;known.note="Known owner";store.records.put("uuid:"+id,known);Member m=member();store.record(m).note="Unsaved new note";m.uuid=id;assertTrue(store.record(m).note.contains("Unsaved new note"));}
    @Test public void deletingAResolvedIdentityAlsoDeletesOwnedAliases(){LocalStore store=new LocalStore(file());LocalStore.Record canonical=new LocalStore.Record();canonical.uuid=id;store.records.put("uuid:"+id,canonical);LocalStore.Record alias=new LocalStore.Record();alias.uuid=id;alias.note="Retained old name";store.records.put("name:oldname",alias);Member m=member();m.uuid=id;store.delete(m);assertTrue(store.records.isEmpty());}
    @Test public void escapedNotesSavedByTheModCanBeLoadedAgain()throws Exception{LocalStore store=new LocalStore(file());String note=String.join("",Collections.nCopies(1000,"<"));for(int i=0;i<1400;i++)store.record(new Member("P"+i,1,i)).note=note;store.touch();store.flush(true);assertEquals(1400,new LocalStore(file()).records.size());}
    @Test public void expiryCancellationCannotStartAnotherHttpRequest()throws Exception{HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger hits=new AtomicInteger();server.createContext("/",e->{hits.incrementAndGet();e.sendResponseHeaders(200,2);e.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));e.close();});server.start();DataClient client=new DataClient();try{synchronized(client){client.configure("http://127.0.0.1:"+server.getAddress().getPort(),"",120,60);Field next=DataClient.class.getDeclaredField("nextRequest");next.setAccessible(true);next.setLong(client,Long.MAX_VALUE);client.request("Alice",r->client.cancel());client.request("Bob",r->{});Field jobs=DataClient.class.getDeclaredField("jobs");jobs.setAccessible(true);Object first=((Map<?,?>)jobs.get(client)).values().iterator().next();Field deadline=first.getClass().getDeclaredField("deadline");deadline.setAccessible(true);deadline.setLong(first,0);next.setLong(client,0);Method pump=DataClient.class.getDeclaredMethod("pump");pump.setAccessible(true);pump.invoke(client);assertEquals(0,hits.get());}}finally{client.shutdown();server.stop(0);}}
}
