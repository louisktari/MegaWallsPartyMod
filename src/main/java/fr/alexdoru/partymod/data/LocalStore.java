package fr.alexdoru.partymod.data;

import com.google.gson.*;
import fr.alexdoru.partymod.core.Member;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Host-owned records only. Current membership is never restored from disk. */
public final class LocalStore {
    public static final class Record {public String note="",uuid="",migratedNote="";public boolean blocked;public long lastPartyJoin,lastMwSighting;public int joins,mwSightings,pendingJoins,pendingSightings;}
    public static final class WaitEntry {public String name;public String status="Waiting";public long invitedAt;public WaitEntry(String name){this.name=name;}}
    private final File file;
    private final LinkedHashMap<String,Record> unsaved=new LinkedHashMap<>();
    public final LinkedHashMap<String,Record> records=new LinkedHashMap<>();
    public final List<WaitEntry> waitlist=new ArrayList<>();
    public String filter="All",sort="Joined";
    public String loadError="";
    private long dirtyAt; private boolean dirty,unreadable;
    public LocalStore(File file){this.file=file;load();}
    public synchronized Record record(Member m) {
        boolean resolved=!m.uuid.isEmpty();
        String nameKey="name:"+m.key,pendingKey="pending:"+m.key;
        Record ownedName=lookup(nameKey);
        String key=resolved?"uuid:"+m.uuid:ownedName!=null&&!ownedName.uuid.isEmpty()?pendingKey:nameKey;
        Record r=lookup(key);
        if(r==null)r=new Record();
        if(resolved){
            for(String fallbackKey:new String[]{pendingKey,nameKey}){
                Record fallback=lookup(fallbackKey);
                if(fallback==null||fallback==r||!fallback.uuid.isEmpty()&&!fallback.uuid.equals(m.uuid))continue;
                String priorNote=r.note,priorMigrated=r.migratedNote,priorOwner=fallback.uuid,priorFallbackNote=fallback.note;
                boolean priorBlocked=r.blocked;
                int priorJoins=r.joins,priorSightings=r.mwSightings,deltaJoins=fallback.pendingJoins,deltaSightings=fallback.pendingSightings;
                long priorJoinTime=r.lastPartyJoin,priorSightingTime=r.lastMwSighting;
                boolean retain=!fallback.migratedNote.isEmpty();
                if(r.migratedNote.isEmpty()&&!fallback.migratedNote.isEmpty())r.migratedNote=fallback.migratedNote;
                if(!fallback.note.isEmpty()&&!r.note.equals(fallback.note)){
                    String combined=r.note.isEmpty()?fallback.note:r.note+" | "+fallback.note;
                    if(combined.length()<=1000){r.note=combined;if(retain)fallback.note="";}
                    else{r.migratedNote=fallback.note;retain=true;}
                }
                r.blocked|=fallback.blocked;
                r.joins=combineCount(r.joins,fallback.joins,fallback.pendingJoins);
                r.mwSightings=combineCount(r.mwSightings,fallback.mwSightings,fallback.pendingSightings);
                r.lastPartyJoin=Math.max(r.lastPartyJoin,fallback.lastPartyJoin);
                r.lastMwSighting=Math.max(r.lastMwSighting,fallback.lastMwSighting);
                fallback.pendingJoins=fallback.pendingSightings=0;fallback.uuid=m.uuid;
                if(!retain){records.remove(fallbackKey);unsaved.remove(fallbackKey);touch();}
                else if(fallbackKey.equals(pendingKey)){
                    String alias="alias:"+m.uuid+":"+m.key+":"+UUID.randomUUID().toString().replace("-","");
                    if(records.remove(fallbackKey)!=null)records.put(alias,fallback);else{unsaved.remove(fallbackKey);unsaved.put(alias,fallback);}
                    touch();
                }
                else if(!priorNote.equals(r.note)||!priorMigrated.equals(r.migratedNote)||!priorFallbackNote.equals(fallback.note)||priorBlocked!=r.blocked||priorJoins!=r.joins||priorSightings!=r.mwSightings||priorJoinTime!=r.lastPartyJoin||priorSightingTime!=r.lastMwSighting||deltaJoins!=0||deltaSightings!=0||!priorOwner.equals(fallback.uuid))touch();
            }
            if(!r.uuid.equals(m.uuid)){r.uuid=m.uuid;touch();}
            r.pendingJoins=r.pendingSightings=0;
        }
        if(!records.containsKey(key)){
            if(records.size()<2000){records.put(key,r);unsaved.remove(key);touch();}
            else{loadError="Local record limit reached; new notes are not saved until space is freed";unsaved.put(key,r);while(unsaved.size()>256)unsaved.remove(unsaved.keySet().iterator().next());}
        }
        if(unsaved.isEmpty()&&loadError.startsWith("Local record limit"))loadError="";
        return r;
    }
    private Record lookup(String key){Record r=records.get(key);return r==null?unsaved.get(key):r;}
    private int combineCount(int current,int fallback,int pending){long total=Math.max(Math.max(0,current),Math.max(0,fallback)-Math.max(0,pending))+(long)Math.max(0,pending);return (int)Math.min(Integer.MAX_VALUE,total);}
    public synchronized void observePartyJoin(Member m,long now){Record r=record(m);r.lastPartyJoin=now;if(r.joins<Integer.MAX_VALUE)r.joins++;if(m.uuid.isEmpty()&&r.pendingJoins<Integer.MAX_VALUE)r.pendingJoins++;touch();}
    public synchronized void observeMwSighting(Member m,long now){Record r=record(m);r.lastMwSighting=now;if(r.mwSightings<Integer.MAX_VALUE)r.mwSightings++;if(m.uuid.isEmpty()&&r.pendingSightings<Integer.MAX_VALUE)r.pendingSightings++;touch();}
    public synchronized boolean stored(Record record){return records.containsValue(record);}
    public synchronized void dropWait(WaitEntry entry){if(waitlist.remove(entry))touch();}
    public synchronized void touch(){dirty=true;dirtyAt=System.currentTimeMillis();}
    public synchronized void flush(boolean force) {if(!dirty||!force&&System.currentTimeMillis()-dirtyAt<1500)return;try{
        Files.createDirectories(file.toPath().getParent());Path tmp=file.toPath().resolveSibling(file.getName()+".tmp");
        if(unreadable&&file.isFile()){Files.copy(file.toPath(),file.toPath().resolveSibling(file.getName()+".unreadable-"+System.currentTimeMillis()));unreadable=false;}
        JsonObject root=new JsonObject();root.add("records",new Gson().toJsonTree(records));root.add("waitlist",new Gson().toJsonTree(waitlist));root.addProperty("filter",filter);root.addProperty("sort",sort);
        try(Writer w=new OutputStreamWriter(Files.newOutputStream(tmp),StandardCharsets.UTF_8)){new GsonBuilder().setPrettyPrinting().create().toJson(root,w);}
        try{Files.move(tmp,file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file.toPath(),StandardCopyOption.REPLACE_EXISTING);}dirty=false;if(loadError.equals("Local records could not be saved")||loadError.startsWith("Local records unreadable"))loadError="";
    }catch(IOException|RuntimeException ex){loadError="Local records could not be saved";}}
    private void load(){if(!file.isFile())return;try {if(file.length()>32_000_000)throw new IOException("Oversized records");JsonObject root;try(Reader r=new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8)){root=new JsonParser().parse(r).getAsJsonObject();}
        JsonObject rec=fr.alexdoru.partymod.core.PlayerStats.object(root,"records");if(rec!=null)for(Map.Entry<String,JsonElement> e:rec.entrySet()){if(records.size()>=2000)break;if(!e.getKey().matches("(?:(?:name|pending):[a-z0-9_]{1,16}|uuid:[a-f0-9]{32}|alias:[a-f0-9]{32}:[a-z0-9_]{1,16}:[a-f0-9]{32})")||!e.getValue().isJsonObject())continue;Record value=new Gson().fromJson(e.getValue(),Record.class);if(value.uuid==null||!value.uuid.matches("[a-f0-9]{32}"))value.uuid="";if(e.getKey().startsWith("uuid:"))value.uuid=e.getKey().substring(5);if(e.getKey().startsWith("alias:"))value.uuid=e.getKey().substring(6,38);value.joins=Math.max(0,value.joins);value.mwSightings=Math.max(0,value.mwSightings);value.pendingJoins=Math.max(0,Math.min(value.joins,value.pendingJoins));value.pendingSightings=Math.max(0,Math.min(value.mwSightings,value.pendingSightings));if(value.note==null)value.note="";if(value.migratedNote==null)value.migratedNote="";if(value.migratedNote.length()>1000)value.migratedNote=value.migratedNote.substring(0,1000);if(value.note.length()>1000)value.note=value.note.substring(0,1000);records.put(e.getKey(),value);}
        JsonElement waiting=root.get("waitlist");if(waiting!=null&&waiting.isJsonArray())for(JsonElement e:waiting.getAsJsonArray()){if(waitlist.size()>=100)break;if(!e.isJsonObject())continue;String name=fr.alexdoru.partymod.core.PlayerStats.string(e.getAsJsonObject(),"name","");if(Member.validName(name)&&waitlist.stream().noneMatch(entry->entry.name.equalsIgnoreCase(name)))waitlist.add(new WaitEntry(name));}
        filter=fr.alexdoru.partymod.core.PlayerStats.string(root,"filter","All");sort=fr.alexdoru.partymod.core.PlayerStats.string(root,"sort","Joined");
    }catch(IOException|RuntimeException ex){records.clear();waitlist.clear();unreadable=true;loadError="Local records unreadable; original retained and backed up before the next save";}}
    public synchronized boolean addWait(String name){if(!Member.validName(name)||waitlist.size()>=100||waitlist.stream().anyMatch(e->e.name.equalsIgnoreCase(name)))return false;waitlist.add(new WaitEntry(name));touch();return true;}
    public synchronized void delete(Member m){
        for(Map<String,Record> source:Arrays.asList(records,unsaved))source.entrySet().removeIf(entry->!m.uuid.isEmpty()&&(entry.getKey().equals("uuid:"+m.uuid)||entry.getValue().uuid.equals(m.uuid))||(entry.getKey().equals("name:"+m.key)||entry.getKey().equals("pending:"+m.key))&&(entry.getValue().uuid.isEmpty()||entry.getValue().uuid.equals(m.uuid)));
        touch();
    }
}
