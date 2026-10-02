package fr.alexdoru.partymod.core;

import java.util.*;
import java.util.regex.*;

/** Anchored allowlist: normal player chat cannot be mistaken for a kick. */
public final class PartyMessages {
    private static final String NAME="(?:\\[[A-Za-z0-9_+ -]{1,24}\\] )?([A-Za-z0-9_]{1,16})";
    private static final Pattern JOIN=Pattern.compile("^"+NAME+" joined the party\\.$");
    private static final Pattern LEFT=Pattern.compile("^"+NAME+" (?:has left|left) the party\\.$");
    private static final Pattern KICK=Pattern.compile("^(?:"+NAME+" has been removed from the party\\.|You (?:have )?kicked "+NAME+" from the party[!.])$");
    private static final Pattern HEADER=Pattern.compile("^Party Members \\((\\d{1,3})\\):?$");
    private static final Pattern ROLE=Pattern.compile("^Party (Leader|Moderators|Members): (.+)$");
    private static final Pattern PROMOTE=Pattern.compile("^"+NAME+" was promoted to Party (Leader|Moderator) by "+NAME+"[!.]$");
    private static final Pattern TRANSFER=Pattern.compile("^The party was transferred to "+NAME+" by "+NAME+"[!.]$");
    private static final Pattern CHAT=Pattern.compile("^Party > "+NAME+": (.+)$");
    private static final Pattern DISCONNECT=Pattern.compile("^"+NAME+" (disconnected|reconnected)\\.$");
    private static final Pattern SELF_JOIN=Pattern.compile("^You (?:have )?joined "+NAME+"'s party[!.]$");
    public enum Type { JOIN, SELF_JOIN, LEFT, KICK, HEADER, ROLE, PROMOTE, DISBAND, NO_PARTY, CHAT, DISCONNECT, RECONNECT, NONE }
    public static final class Event {
        public final Type type; public final String name,text; public final int count; public final Member.Role role;
        Event(Type type,String name,String text,int count,Member.Role role) {this.type=type;this.name=name;this.text=text;this.count=count;this.role=role;}
    }
    private static Event of(Type type,String name) { return new Event(type,name,"",0,Member.Role.MEMBER); }
    public static Event parse(String raw) {
        String text=raw.replaceAll("\\u00a7[0-9A-FK-ORa-fk-or]", "").trim(); Matcher m;
        if((m=JOIN.matcher(text)).matches()) return of(Type.JOIN,m.group(1));
        if((m=SELF_JOIN.matcher(text)).matches()) return of(Type.SELF_JOIN,m.group(1));
        if((m=LEFT.matcher(text)).matches()) return of(Type.LEFT,m.group(1));
        if((m=KICK.matcher(text)).matches()) return of(Type.KICK,m.group(1)!=null?m.group(1):m.group(2));
        if((m=HEADER.matcher(text)).matches()) return new Event(Type.HEADER,"","",Integer.parseInt(m.group(1)),Member.Role.MEMBER);
        if((m=ROLE.matcher(text)).matches()) return new Event(Type.ROLE,"",m.group(2),0,m.group(1).equals("Leader")?Member.Role.LEADER:m.group(1).equals("Moderators")?Member.Role.MODERATOR:Member.Role.MEMBER);
        if((m=PROMOTE.matcher(text)).matches()) return new Event(Type.PROMOTE,m.group(1),"",0,m.group(2).equals("Leader")?Member.Role.LEADER:Member.Role.MODERATOR);
        if((m=TRANSFER.matcher(text)).matches()) return new Event(Type.PROMOTE,m.group(1),"",0,Member.Role.LEADER);
        if((m=CHAT.matcher(text)).matches()) return new Event(Type.CHAT,m.group(1),m.group(2),0,Member.Role.MEMBER);
        if((m=DISCONNECT.matcher(text)).matches()) return of(m.group(2).equals("disconnected")?Type.DISCONNECT:Type.RECONNECT,m.group(1));
        if(text.equals("The party was disbanded.") || text.equals("You have disbanded the party!") || text.matches("^"+NAME+" has disbanded the party!$")) return of(Type.DISBAND,"");
        if(text.equals("You are not currently in a party.") || text.equals("You left the party.") || text.equals("You have been kicked from the party!") || text.equals("You have been removed from the party.")) return of(Type.NO_PARTY,"");
        return of(Type.NONE,"");
    }
    public static List<String> names(String row) {
        if(row.trim().equalsIgnoreCase("None"))return Collections.emptyList();
        String plain=row.replaceAll("\\[[^\\]]*\\]", "").replaceAll("[\\u25cf\\u25cb,]", " ").trim();
        List<String> result=new ArrayList<>();
        for(String token:plain.split("\\s+")) if(Member.validName(token)) result.add(token);
        return result;
    }
    public static boolean hypixel(String server) {
        if(server==null) return false; String host=server.toLowerCase(Locale.ROOT).trim().replaceFirst(":\\d+$","");
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }
}
