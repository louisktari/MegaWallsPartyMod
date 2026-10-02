package fr.alexdoru.partymod;

import fr.alexdoru.partymod.core.*;
import fr.alexdoru.partymod.data.LocalStore;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class LocalStoreTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private File target(){return new File(temp.getRoot(),"records.json");}

    @Test public void notesAndAttendanceSurviveAtomicSave()throws Exception{
        Member member=new Member("Alice",1,1);
        LocalStore store=new LocalStore(target());
        LocalStore.Record record=store.record(member);
        record.note="Helpful host note";record.blocked=true;
        record.joins=2;record.lastPartyJoin=100;record.lastMwSighting=200;
        store.touch();store.flush(true);
        LocalStore.Record loaded=new LocalStore(target()).record(member);
        assertEquals(record.note,loaded.note);assertTrue(loaded.blocked);
        assertEquals(2,loaded.joins);assertEquals(200,loaded.lastMwSighting);
        assertFalse(new File(temp.getRoot(),"records.json.tmp").exists());
        assertTrue(new PartyState().members().isEmpty());
    }
    @Test public void resolvedIdentityMigrationIsSavedWithoutFurtherEdits(){
        Member member=new Member("Alice",1,1);
        LocalStore store=new LocalStore(target());
        store.record(member).note="Keep this";store.touch();store.flush(true);
        member.uuid="0123456789abcdef0123456789abcdef";
        assertEquals("Keep this",store.record(member).note);store.flush(true);
        LocalStore loaded=new LocalStore(target());
        assertFalse(loaded.records.containsKey("name:alice"));
        assertEquals("Keep this",loaded.record(member).note);
    }
    @Test public void corruptFileIsBackedUpBeforeNewRecordsAreSaved()throws Exception{
        byte[] original="{invalid".getBytes(StandardCharsets.UTF_8);
        Files.write(target().toPath(),original);
        LocalStore store=new LocalStore(target());assertFalse(store.loadError.isEmpty());
        assertArrayEquals(original,Files.readAllBytes(target().toPath()));
        store.record(new Member("Alice",1,1));store.flush(true);
        File[] backups=temp.getRoot().listFiles((dir,name)->name.contains(".unreadable-"));
        assertNotNull(backups);assertEquals(1,backups.length);
        assertArrayEquals(original,Files.readAllBytes(backups[0].toPath()));
        assertEquals(1,new LocalStore(target()).records.size());
    }
    @Test public void waitlistDeduplicatesBoundsAndRestartsUnconfirmed(){
        LocalStore store=new LocalStore(target());assertTrue(store.addWait("Alice"));
        assertFalse(store.addWait("alice"));assertFalse(store.addWait("bad name"));
        for(int i=1;i<100;i++)assertTrue(store.addWait("P"+i));
        assertFalse(store.addWait("Overflow"));
        store.waitlist.get(0).status="Invitation sent";store.flush(true);
        assertEquals("Waiting",new LocalStore(target()).waitlist.get(0).status);
    }
    @Test public void deleteRemovesBothFallbackAndResolvedRecords(){
        LocalStore store=new LocalStore(target());Member member=new Member("Alice",1,1);
        store.record(member);member.uuid="0123456789abcdef0123456789abcdef";
        store.record(member);store.delete(member);store.flush(true);
        assertTrue(new LocalStore(target()).records.isEmpty());
    }
    @Test public void recordLimitDoesNotSilentlyDeleteHostNotes(){
        LocalStore store=new LocalStore(target());
        for(int i=0;i<2000;i++)store.record(new Member("P"+i,1,i)).note="Keep";
        store.record(new Member("Overflow",1,2001));
        assertEquals(2000,store.records.size());assertFalse(store.loadError.isEmpty());
        assertEquals("Keep",store.records.get("name:p0").note);
    }
}
