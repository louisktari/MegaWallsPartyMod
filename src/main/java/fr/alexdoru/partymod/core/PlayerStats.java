package fr.alexdoru.partymod.core;

import com.google.gson.*;
import java.util.*;

/** Optional values stay null; absent data is never fabricated as measured zero. */
public final class PlayerStats {
    public String uuid="", name="", source="Application service";
    public Long firstLogin, fetchedAt;
    public Double networkLevel;
    public Integer wins, losses, finals, finalDeaths, quests, legendary;
    public final Map<String,Kit> kits=new LinkedHashMap<>();
    public final List<String> warnings=new ArrayList<>();
    public static final String[] CLASSES={"Arcanist","Assassin","Automaton","Blaze","Cow","Creeper","Dreadlord","Enderman","Golem","Herobrine","Hunter","Moleman","Phoenix","Pigman","Pirate","Renegade","Shaman","Shark","Skeleton","Snowman","Spider","Squid","Werewolf","Zombie","Angel","Dragon","Sheep"};
    public static final class Kit { public Boolean unlocked; public Integer kit,ability,passive1,passive2,gathering; public boolean complete(){return kit!=null&&ability!=null&&passive1!=null&&passive2!=null&&gathering!=null;} public boolean maxed(){return Boolean.TRUE.equals(unlocked)&&complete()&&kit>=5&&ability>=5&&passive1>=3&&passive2>=3&&gathering>=3;} }
    public Integer games() { return wins==null||losses==null?null:wins+losses; }
    public Double fkd() { return finals==null||finalDeaths==null?null:finalDeaths==0?null:(double)finals/finalDeaths; }
    public Double wl() { return wins==null||losses==null?null:losses==0?null:(double)wins/losses; }
    public static PlayerStats parseSummary(JsonObject root) {
        PlayerStats s=new PlayerStats(); s.uuid=string(root,"uuid",""); s.name=string(root,"name","");
        s.source=string(root,"source","Application service"); s.firstLogin=whole(root,"firstLogin",0,Long.MAX_VALUE);
        s.fetchedAt=whole(root,"fetchedAt",0,Long.MAX_VALUE); s.networkLevel=number(root,"networkLevel",1,100000);
        JsonObject mw=object(root,"megaWalls");
        if(mw!=null) {s.wins=integer(mw,"wins");s.losses=integer(mw,"losses");s.finals=integer(mw,"finalKills");s.finalDeaths=integer(mw,"finalDeaths");s.legendary=integer(mw,"legendarySkins");}
        s.quests=integer(root,"completedQuests"); JsonObject classes=mw==null?null:object(mw,"classes");
        if(classes!=null) for(String name:CLASSES) {JsonObject c=object(classes,name.toLowerCase(Locale.ROOT)); if(c==null) continue;Kit k=new Kit();k.unlocked=bool(c,"unlocked");k.kit=integer(c,"kit");k.ability=integer(c,"ability");k.passive1=integer(c,"passive1");k.passive2=integer(c,"passive2");k.gathering=integer(c,"gathering");s.kits.put(name,k);}
        if(s.games()==null) s.warnings.add("Recorded win/loss sample unavailable");
        if(s.firstLogin==null) s.warnings.add("First-login date unavailable");
        return s;
    }
    public static JsonObject object(JsonObject o,String key) {JsonElement e=o==null?null:o.get(key);return e!=null&&e.isJsonObject()?e.getAsJsonObject():null;}
    public static String string(JsonObject o,String key,String fallback) {JsonElement e=o==null?null:o.get(key);return e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString()?e.getAsString():fallback;}
    public static Double number(JsonObject o,String key,double min,double max) {JsonElement e=o==null?null:o.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()) return null;try{double d=e.getAsDouble();return Double.isFinite(d)&&d>=min&&d<=max?d:null;}catch(RuntimeException ex){return null;}}
    public static Long whole(JsonObject o,String key,long min,long max) {Double n=number(o,key,min,max);return n!=null&&n==Math.floor(n)?n.longValue():null;}
    public static Integer integer(JsonObject o,String key) {Long n=whole(o,key,0,Integer.MAX_VALUE/4);return n==null?null:n.intValue();}
    public static Boolean bool(JsonObject o,String key) {JsonElement e=o==null?null:o.get(key);return e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isBoolean()?e.getAsBoolean():null;}
}
