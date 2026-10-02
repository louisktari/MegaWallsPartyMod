package fr.alexdoru.partymod;

import fr.alexdoru.partymod.data.DataClient;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.sun.net.httpserver.HttpServer;

public class DeepJsonTest {
    @Test public void malformedDeepJsonCannotKillTheDataWorker()throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String deep="{\"bad\":"+String.join("",Collections.nCopies(5000,"["))+"0"+String.join("",Collections.nCopies(5000,"]"))+"}";
        server.createContext("/",e->{String body=e.getRequestURI().getPath().endsWith("Alice")?deep:"{\"uuid\":\"00000000000000000000000000000001\",\"name\":\"Bob\",\"fetchedAt\":"+System.currentTimeMillis()+"}";byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});server.start();
        DataClient client=new DataClient();try{
            client.configure("http://127.0.0.1:"+server.getAddress().getPort(),"",120,60);
            CountDownLatch bad=new CountDownLatch(1);AtomicBoolean rejected=new AtomicBoolean();client.request("Alice",r->{rejected.set(r.stats==null);bad.countDown();});
            assertTrue("Malformed input must finish with an error",bad.await(3,TimeUnit.SECONDS));assertTrue(rejected.get());
            CountDownLatch good=new CountDownLatch(1);AtomicBoolean loaded=new AtomicBoolean();client.request("Bob",r->{loaded.set(r.stats!=null);good.countDown();});assertTrue(good.await(3,TimeUnit.SECONDS));assertTrue(loaded.get());
        }finally{client.shutdown();server.stop(0);}
    }
}
