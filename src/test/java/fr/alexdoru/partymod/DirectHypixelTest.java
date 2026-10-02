package fr.alexdoru.partymod;

import com.google.gson.*;
import fr.alexdoru.partymod.core.*;
import fr.alexdoru.partymod.data.*;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class DirectHypixelTest {
    private static final String ID="0123456789abcdef0123456789abcdef";
    private JsonObject json(String s){return new JsonParser().parse(s).getAsJsonObject();}
    private PlayerStats parse(String extra)throws IOException{return DirectHypixel.parse(json("{\"id\":\""+ID+"\",\"name\":\"Alice\"}"),json("{\"success\":true,\"player\":{\"uuid\":\""+ID+"\""+extra+"}}"),2000000000000L);}
    @Test public void readsActualHypixelFields()throws Exception{PlayerStats s=parse(",\"firstLogin\":1700000000000,\"networkExp\":0,\"stats\":{\"Walls3\":{\"wins\":0,\"losses\":3,\"final_kills\":10,\"final_deaths\":2}}");assertEquals(Long.valueOf(1700000000000L),s.firstLogin);assertEquals(Double.valueOf(1),s.networkLevel);assertEquals(Integer.valueOf(3),s.games());assertEquals(Double.valueOf(5),s.fkd());assertEquals("Hypixel API (direct)",s.source);}
    @Test public void missingFieldsRemainUnknown()throws Exception{PlayerStats s=parse("");assertNull(s.firstLogin);assertNull(s.games());assertNull(s.networkLevel);assertFalse(DirectHypixel.tooNew(s,90,2000000000000L));}
    @Test public void invalidDateIsNotAccountEvidence()throws Exception{assertNull(parse(",\"firstLogin\":0").firstLogin);assertNull(parse(",\"firstLogin\":\"123\"").firstLogin);assertNull(parse(",\"firstLogin\":2000000000001").firstLogin);}
    @Test public void classUpgradesAndLockedStateAreTyped()throws Exception{PlayerStats s=parse(",\"stats\":{\"Walls3\":{\"classes\":{\"dragon\":{\"unlocked\":true,\"skill_level_d\":5,\"skill_level_a\":5,\"skill_level_b\":3,\"skill_level_c\":3,\"skill_level_g\":3}}}}");assertTrue(s.kits.get("Dragon").maxed());}
    @Test public void mismatchedIdentityIsRejected()throws Exception{try{DirectHypixel.parse(json("{\"id\":\""+ID+"\",\"name\":\"Alice\"}"),json("{\"success\":true,\"player\":{\"uuid\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}}"),1000);fail();}catch(IOException expected){}}
    @Test public void ageCutoffIsStrictAndUnknownSafe(){long now=2000000000000L;PlayerStats s=new PlayerStats();assertFalse(DirectHypixel.tooNew(null,90,now));assertFalse(DirectHypixel.tooNew(s,90,now));s.firstLogin=now+1;assertFalse(DirectHypixel.tooNew(s,90,now));s.firstLogin=now-90*86400000L;assertFalse(DirectHypixel.tooNew(s,90,now));s.firstLogin++;assertTrue(DirectHypixel.tooNew(s,90,now));assertFalse(DirectHypixel.tooNew(s,0,now));}
    @Test public void failureCannotProduceStats()throws Exception{try{DirectHypixel.parse(json("{\"id\":\""+ID+"\",\"name\":\"Alice\"}"),json("{\"success\":false}"),1000);fail();}catch(IOException expected){}}
    private static class Connection extends HttpURLConnection {
        final Map<String,String> headers=new HashMap<>();Connection(URL u){super(u);}
        public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}
        public void setRequestProperty(String key,String value){headers.put(key,value);}
        public int getResponseCode(){return 200;}
        public String getHeaderField(String key){return null;}
        public InputStream getInputStream(){return new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));}
    }
    @Test public void keyUsesOnlyHypixelHeaderAndRedirectsStayDisabled()throws Exception{
        final Connection[] opened=new Connection[1];URL url=new URL(null,"https://api.hypixel.net/v2/player",new URLStreamHandler(){protected URLConnection openConnection(URL u){return opened[0]=new Connection(u);}});
        DataClient client=new DataClient();try{Method get=DataClient.class.getDeclaredMethod("get",URL.class,String.class,boolean.class);get.setAccessible(true);get.invoke(client,url,"synthetic-key",true);assertEquals("synthetic-key",opened[0].headers.get("API-Key"));assertFalse(opened[0].headers.containsKey("Authorization"));assertFalse(opened[0].getInstanceFollowRedirects());get.invoke(client,url,"",false);assertFalse(opened[0].headers.containsKey("API-Key"));assertFalse(opened[0].headers.containsKey("Authorization"));}finally{client.shutdown();}
    }
    @Test public void changingSourceCancelsOldWork(){DataClient c=new DataClient();try{assertTrue(c.configureSource(true,"synthetic-key","","",120,30));List<String> results=new ArrayList<>();c.request("Alice",r->results.add(r.error));assertTrue(c.configureSource(false,"","http://localhost:8080","synthetic-service-token",120,30));assertEquals(Collections.singletonList("Cancelled"),results);}finally{c.shutdown();}}
}
