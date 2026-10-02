package fr.alexdoru.partymod.ui;

import java.util.*;

public final class Notifications {
    public static final class Entry {public final long time;public final String text;public final boolean operational;public Entry(String text,boolean operational){time=System.currentTimeMillis();this.text=text;this.operational=operational;}}
    public final Deque<Entry> log=new ArrayDeque<>();
    private final Map<String,Entry> last=new LinkedHashMap<>();
    private Entry toast;private int grouped;
    public void add(String key,String text,boolean operational){long now=System.currentTimeMillis();Entry previous=last.get(key);if(previous!=null&&previous.text.equals(text)&&previous.operational==operational&&now-previous.time<30000)return;Entry e=new Entry(text,operational);last.put(key,e);while(last.size()>512)last.remove(last.keySet().iterator().next());log.addFirst(e);while(log.size()>300)log.removeLast();if(!operational&&toast!=null&&toast.operational&&now-toast.time<6000)return;if(!operational&&toast!=null&&!toast.operational&&now-toast.time<2500){grouped++;toast=new Entry(grouped+" updates — open PartyMod to review",false);}else{grouped=1;toast=e;}}
    public Entry toast(){return toast!=null&&System.currentTimeMillis()-toast.time<6000?toast:null;}
    public void clear(){toast=null;last.clear();grouped=0;}
}
