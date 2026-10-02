package fr.alexdoru.partymod.core;

import java.util.*;

/** All outbound commands are serialized; moderation never retries blindly. */
public final class ActionQueue {
    public enum Kind { SYNC, REMOVE, NEW_ACCOUNT_REMOVE, BLOCK, IGNORE, INVITE, READY }
    public static final class Action {
        public final Kind kind; public final String name, command; public final long generation,membership,queuedAt;
        public long sentAt,expiresAt; public boolean automatic; public String context=""; public Object owner; public String result="Queued";
        public Action(Kind kind,String name,String command,long generation,long membership,long now) {this.kind=kind;this.name=name;this.command=command;this.generation=generation;this.membership=membership;queuedAt=now;}
    }
    public interface Transport { boolean valid(Action action); void send(String command); void result(Action action,String result); }
    private final ArrayDeque<Action> queue=new ArrayDeque<>();
    private Action pending; private long nextSend;
    private final LinkedHashMap<String,Long> blocked=new LinkedHashMap<>();
    public int size(){return queue.size()+(pending==null?0:1);}
    public boolean contains(Kind kind,String name){if(pending!=null&&pending.kind==kind&&pending.name.equalsIgnoreCase(name))return true;for(Action a:queue)if(a.kind==kind&&a.name.equalsIgnoreCase(name))return true;return false;}
    public boolean enqueue(Action a) {
        if(size()>=128) return false;
        if(pending!=null&&same(pending,a)) return false;
        for(Action current:queue) if(same(current,a)) return false;
        if(a.kind==Kind.BLOCK) {String key=a.generation+":"+Member.key(a.name);Long previous=blocked.get(key);if(previous!=null&&a.queuedAt-previous<120000) return false;}
        a.expiresAt=a.queuedAt+120000L+(a.kind==Kind.BLOCK?size()*13000L:0);
        queue.add(a);return true;
    }
    private boolean same(Action a,Action b){return a.generation==b.generation&&(a.kind==b.kind||removal(a.kind)&&removal(b.kind))&&a.name.equalsIgnoreCase(b.name);}
    private boolean removal(Kind kind){return kind==Kind.REMOVE||kind==Kind.NEW_ACCOUNT_REMOVE;}
    public void tick(long now,long interval,Transport transport) {
        interval=Math.max(750,Math.min(3000,interval));
        if(pending!=null&&now-pending.sentAt>=10000) {transport.result(pending,"Unconfirmed — no server acknowledgement");pending=null;nextSend=now+interval;}
        if(pending!=null||now<nextSend||queue.isEmpty()) return;
        Action a=queue.remove();if(now>a.expiresAt||!transport.valid(a)){transport.result(a,"Cancelled — context changed or action expired");return;}
        nextSend=now+interval;try{transport.send(a.command);a.sentAt=now;}catch(RuntimeException ex){transport.result(a,"Failed — command could not be sent; no automatic retry");return;}
        if(a.kind==Kind.BLOCK){blocked.put(a.generation+":"+Member.key(a.name),now);while(blocked.size()>256)blocked.remove(blocked.keySet().iterator().next());}
        if(a.kind==Kind.READY) transport.result(a,"Sent — waiting for member replies");else pending=a;
    }
    public boolean acknowledge(Kind kind,String name,String result,Transport transport) {if(pending==null||pending.kind!=kind||!pending.name.equalsIgnoreCase(name)) return false;transport.result(pending,result);pending=null;return true;}
    public boolean acknowledgeSync(Transport transport){return acknowledge(Kind.SYNC,"","Roster synchronised",transport);}
    public void cooldown(long until){nextSend=Math.max(nextSend,until);}
    public void cancel(Transport transport){if(pending!=null)transport.result(pending,"Cancelled");pending=null;for(Action a:queue)transport.result(a,"Cancelled");queue.clear();blocked.clear();}
    public Action pending(){return pending;}
}
