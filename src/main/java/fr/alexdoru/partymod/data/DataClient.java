package fr.alexdoru.partymod.data;

import com.google.gson.*;
import fr.alexdoru.partymod.core.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Bounded client supporting direct Hypixel and an optional application service. */
public final class DataClient {
    public static final class Result {public final PlayerStats stats;public final String error;public final boolean cached;Result(PlayerStats s,String e,boolean c){stats=s;error=e;cached=c;}}
    private static final class Cache {final PlayerStats stats;final long time;Cache(PlayerStats s){stats=s;time=System.currentTimeMillis();}}
    private static final class Job {final String name;final long epoch,deadline;int attempts;long due;final List<Consumer<Result>> consumers=new ArrayList<>();Job(String n,long e,long allowance){name=n;epoch=e;deadline=System.currentTimeMillis()+allowance;}}
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"PartyMod-data");t.setDaemon(true);return t;});
    private final LinkedHashMap<String,Job> jobs=new LinkedHashMap<>();
    private final LinkedHashMap<String,Cache> cache=new LinkedHashMap<>();
    private long epoch,nextRequest;
    private String endpoint="",token="";private boolean direct,closed;private int ttlMinutes=120,requestsPerMinute=30;
    public DataClient(){worker.scheduleWithFixedDelay(this::pump,100,100,TimeUnit.MILLISECONDS);}
    public synchronized boolean configure(String endpoint,String token,int ttl,int budget){return configureMode(false,endpoint,token,ttl,budget);}
    private boolean configureMode(boolean direct,String endpoint,String token,int ttl,int budget){endpoint=endpoint==null?"":endpoint.trim();token=token==null?"":token;boolean changed=this.direct!=direct||!this.endpoint.equals(endpoint)||!this.token.equals(token);if(changed){cancel();cache.clear();}this.direct=direct;this.endpoint=endpoint;this.token=token;ttlMinutes=Math.max(15,Math.min(1440,ttl));requestsPerMinute=Math.max(1,Math.min(60,budget));return changed;}
    public synchronized boolean configureSource(boolean direct,String apiKey,String endpoint,String token,int ttl,int budget){apiKey=apiKey==null?"":apiKey.trim();return configureMode(direct,direct?(apiKey.isEmpty()?"":"https://api.hypixel.net"):endpoint,direct?apiKey:token,ttl,budget);}
    public synchronized void request(String name,Consumer<Result> done) {
        if(closed){deliver(done,new Result(null,"Cancelled",false));return;}
        if(!Member.validName(name)){deliver(done,new Result(null,"Invalid player name",false));return;}
        if(endpoint.isEmpty()){deliver(done,new Result(null,"No data service configured",false));return;}
        String key=Member.key(name);Cache c=cache.get(key);if(c!=null&&System.currentTimeMillis()-c.time<ttlMinutes*60000L){deliver(done,new Result(c.stats,"",true));return;}
        Job existing=jobs.get(key);if(existing!=null){if(existing.consumers.size()>=64)deliver(done,new Result(null,"Too many duplicate data requests; retry later",false));else existing.consumers.add(done);return;}
        if(jobs.size()>=128){deliver(done,new Result(null,"Data queue full; retry later",false));return;}
        long allowance=Math.min(1800000L,120000L+(jobs.size()+1)*60000L/requestsPerMinute);
        Job job=new Job(name,epoch,allowance);job.consumers.add(done);jobs.put(key,job);
    }
    public synchronized void cancel(){epoch++;List<Job> cancelled=new ArrayList<>(jobs.values());jobs.clear();for(Job j:cancelled)deliver(j,new Result(null,"Cancelled",false));}
    public synchronized void shutdown(){closed=true;cancel();worker.shutdownNow();}
    public synchronized int queued(){return jobs.size();}
    private void pump(){Job job=null;String base,access;boolean useDirect;long now=System.currentTimeMillis();List<Job> expired=new ArrayList<>();synchronized(this){base=endpoint;access=token;useDirect=direct;Iterator<Job> it=jobs.values().iterator();while(it.hasNext()){Job j=it.next();if(now>j.deadline){it.remove();expired.add(j);}}if(now>=nextRequest&&!closed){for(Job j:jobs.values())if(j.due<=now){job=j;break;}if(job!=null)nextRequest=now+60000L/requestsPerMinute;}}
        for(Job j:expired)deliver(j,new Result(null,"Data request expired",false));if(job==null)return;
        synchronized(this){if(closed||job.epoch!=epoch||jobs.get(Member.key(job.name))!=job)return;}
        Result result;try{PlayerStats stats;
            if(useDirect){if(!access.matches("[A-Za-z0-9_-]{8,128}"))throw new ConfigurationError("Invalid Hypixel API-key format; update OneConfig > Data");JsonObject profile=get(new URL("https://api.mojang.com/users/profiles/minecraft/"+job.name),"");String uuid=PlayerStats.string(profile,"id","");if(!uuid.matches("[a-f0-9]{32}")||!PlayerStats.string(profile,"name","").equalsIgnoreCase(job.name))throw new IOException("Name identity unresolved");synchronized(this){if(job.epoch!=epoch)return;}JsonObject response=get(new URL("https://api.hypixel.net/v2/player?uuid="+uuid),access,true);stats=DirectHypixel.parse(profile,response,System.currentTimeMillis());}
            else{URI root=validateEndpoint(base);JsonObject response=get(new URL(root.toString().replaceAll("/$","")+"/partymod/v1/players/"+job.name),access);stats=PlayerStats.parseSummary(response);}
            if(!stats.uuid.matches("[a-f0-9]{32}")||!stats.name.equalsIgnoreCase(job.name))throw new IOException("Identity mismatch; not assessed");if(stats.fetchedAt==null||stats.fetchedAt>System.currentTimeMillis()+60000L||now-stats.fetchedAt>7*86400000L)throw new IOException("Invalid or stale service timestamp");result=new Result(stats,"",false);
        }catch(Retry ex){synchronized(this){if(job.epoch==epoch&&jobs.get(Member.key(job.name))==job&&++job.attempts<=2){job.due=System.currentTimeMillis()+Math.max(ex.delay,job.attempts*5000L);nextRequest=Math.max(nextRequest,job.due);return;}}result=new Result(null,"Rate limit or service unavailable; retry later",false);
        }catch(IOException|RuntimeException ex){result=new Result(null,ex instanceof KeyError||ex instanceof ConfigurationError?ex.getMessage():"Data unavailable; verify API key or service configuration",false);}
        synchronized(this){if(job.epoch!=epoch||jobs.get(Member.key(job.name))!=job)return;jobs.remove(Member.key(job.name));if(result.stats!=null){cache.put(Member.key(job.name),new Cache(result.stats));while(cache.size()>512)cache.remove(cache.keySet().iterator().next());}deliver(job,result);}
    }
    private void deliver(Job j,Result r){for(Consumer<Result> c:new ArrayList<>(j.consumers))deliver(c,r);}
    private void deliver(Consumer<Result> c,Result r){try{c.accept(r);}catch(RuntimeException ignored){/* UI callback must not strand the request pump. */}}
    public static URI validateEndpoint(String base) {try{URI uri=new URI(base);String host=uri.getHost();boolean loopback="127.0.0.1".equals(host)||"localhost".equals(host)||"[::1]".equals(host);if(host==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!("https".equalsIgnoreCase(uri.getScheme())||loopback&&"http".equalsIgnoreCase(uri.getScheme())))throw new IllegalArgumentException("Service must use HTTPS (HTTP allowed only on localhost)");return uri;}catch(URISyntaxException ex){throw new IllegalArgumentException("Invalid service URL");}}
    private static final class Retry extends IOException {final long delay;Retry(long d){delay=d;}}
    private static final class KeyError extends IOException {KeyError(){super("Hypixel API key rejected; update it in OneConfig > Data");}}
    private static final class ConfigurationError extends IOException {ConfigurationError(String message){super(message);}}
    private JsonObject get(URL url,String access)throws IOException {
        return get(url,access,false);
    }
    private JsonObject get(URL url,String access,boolean hypixel)throws IOException {
        if(hypixel&&(!url.getProtocol().equals("https")||!url.getHost().equals("api.hypixel.net")||(url.getPort()!=-1&&url.getPort()!=443)))throw new IOException("Invalid upstream host");
        if(access.length()>512||!access.matches("[\\x20-\\x7E]*"))throw new ConfigurationError("Invalid access-token configuration; update OneConfig > Data");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(6000);c.setReadTimeout(6000);c.setRequestProperty("User-Agent","PartyMod/2.0.4");c.setRequestProperty("Accept","application/json");if(!access.isEmpty())c.setRequestProperty(hypixel?"API-Key":"Authorization",hypixel?access:"Bearer "+access);
        try{int status=c.getResponseCode();if(status==429||status==502||status==503||status==504){long delay=status==429&&hypixel?60000:10000;String retry=c.getHeaderField("Retry-After");if(retry==null&&hypixel)retry=c.getHeaderField("RateLimit-Reset");try{delay=Math.max(1000,Math.min(300000,Long.parseLong(retry)*1000));}catch(RuntimeException ignored){}throw new Retry(delay);}if(hypixel&&(status==401||status==403))throw new KeyError();if(status!=200)throw new IOException("Service response "+status);
            if(hypixel)try{if(Integer.parseInt(c.getHeaderField("RateLimit-Remaining"))<=0){long reset=Math.max(1,Math.min(300,Integer.parseInt(c.getHeaderField("RateLimit-Reset"))));synchronized(this){nextRequest=Math.max(nextRequest,System.currentTimeMillis()+reset*1000);}}}catch(RuntimeException ignored){}
            long deadline=System.nanoTime()+8_000_000_000L;ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream input=c.getInputStream()){byte[] buffer=new byte[4096];int length;while((length=input.read(buffer))!=-1){if(out.size()+length>(hypixel?1048576:262144)||System.nanoTime()>deadline)throw new IOException("Response bounds exceeded");out.write(buffer,0,length);}}
            JsonElement json=new JsonParser().parse(new String(out.toByteArray(),StandardCharsets.UTF_8));if(!json.isJsonObject())throw new IOException("Expected object");return json.getAsJsonObject();
        }finally{c.disconnect();}
    }
}
