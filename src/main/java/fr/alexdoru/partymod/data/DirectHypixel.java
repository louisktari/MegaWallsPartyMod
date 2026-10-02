package fr.alexdoru.partymod.data;

import com.google.gson.*;
import fr.alexdoru.partymod.core.*;
import java.io.IOException;
import java.util.*;

/** Fixed upstream endpoints only; an API key is never sent to Mojang. */
public final class DirectHypixel {
    public static PlayerStats parse(JsonObject profile,JsonObject response,long now)throws IOException {
        String uuid=PlayerStats.string(profile,"id","");
        String name=PlayerStats.string(profile,"name","");
        JsonObject player=PlayerStats.object(response,"player");
        if(!uuid.matches("[a-f0-9]{32}")||!Member.validName(name)||!Boolean.TRUE.equals(PlayerStats.bool(response,"success"))||player==null)throw new IOException("Player data unavailable");
        if(!uuid.equals(PlayerStats.string(player,"uuid","").replace("-","")))throw new IOException("Player identity mismatch");
        JsonObject root=new JsonObject();root.addProperty("uuid",uuid);root.addProperty("name",name);root.addProperty("source","Hypixel API (direct)");root.addProperty("fetchedAt",now);
        Long first=PlayerStats.whole(player,"firstLogin",1,now);if(first!=null)root.addProperty("firstLogin",first);
        Double xp=PlayerStats.number(player,"networkExp",0,1e16);
        if(xp!=null){double level=Math.floor(-2.5+Math.sqrt(12.25+.0008*xp));double baseline=(1250*(level-2)+10000)*(level-1);root.addProperty("networkLevel",level+(xp-baseline)/(2500*(level-1)+10000));}
        JsonObject quests=PlayerStats.object(player,"quests");if(quests!=null){int completed=0;for(Map.Entry<String,JsonElement> entry:quests.entrySet()){JsonObject q=entry.getValue().isJsonObject()?entry.getValue().getAsJsonObject():null;JsonElement completions=q==null?null:q.get("completions");if(completions!=null&&completions.isJsonArray())completed+=completions.getAsJsonArray().size();}root.addProperty("completedQuests",completed);}
        JsonObject stats=PlayerStats.object(player,"stats"),mw=PlayerStats.object(stats,"Walls3"),normal=new JsonObject();
        copy(mw,"wins",normal,"wins");copy(mw,"losses",normal,"losses");copy(mw,"final_kills",normal,"finalKills");
        if(PlayerStats.integer(mw,"final_deaths")!=null)copy(mw,"final_deaths",normal,"finalDeaths");else copy(mw,"finalDeaths",normal,"finalDeaths");
        JsonElement achievements=player.get("achievementsOneTime");
        if(achievements!=null&&achievements.isJsonArray()){Set<String> unique=new HashSet<>();for(JsonElement a:achievements.getAsJsonArray())if(a.isJsonPrimitive()&&a.getAsJsonPrimitive().isString()&&a.getAsString().startsWith("walls3_legendary_"))unique.add(a.getAsString());normal.addProperty("legendarySkins",unique.size());}
        JsonObject classes=PlayerStats.object(mw,"classes"),kits=new JsonObject();
        for(String label:PlayerStats.CLASSES){String key=label.toLowerCase(Locale.ROOT);JsonObject c=PlayerStats.object(classes,key);if(c==null)continue;JsonObject kit=new JsonObject();Boolean unlocked=PlayerStats.bool(c,"unlocked");if(unlocked!=null)kit.addProperty("unlocked",unlocked);copy(c,"skill_level_d",kit,"kit");copy(c,"skill_level_a",kit,"ability");copy(c,"skill_level_b",kit,"passive1");copy(c,"skill_level_c",kit,"passive2");copy(c,"skill_level_g",kit,"gathering");kits.add(key,kit);}
        normal.add("classes",kits);root.add("megaWalls",normal);return PlayerStats.parseSummary(root);
    }
    private static void copy(JsonObject from,String key,JsonObject to,String dest){Integer n=PlayerStats.integer(from,key);if(n!=null)to.addProperty(dest,n);}
    public static boolean tooNew(PlayerStats stats,int days,long now){return stats!=null&&stats.firstLogin!=null&&stats.firstLogin>0&&stats.firstLogin<=now&&days>0&&now-stats.firstLogin<days*86400000L;}
}
