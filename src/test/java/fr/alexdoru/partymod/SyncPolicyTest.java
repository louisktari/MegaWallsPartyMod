package fr.alexdoru.partymod;

import fr.alexdoru.partymod.core.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SyncPolicyTest {
    private static class Transport implements ActionQueue.Transport {
        final List<String> sent=new ArrayList<>();
        public boolean valid(ActionQueue.Action a){return true;}
        public void send(String command){sent.add(command);}
        public void result(ActionQueue.Action a,String result){}
    }
    private ActionQueue.Action sync(long generation,long now){return new ActionQueue.Action(ActionQueue.Kind.SYNC,"","/p list",generation,0,now);}

    @Test public void replayLoginNoPartyResponseDoesNotResendEveryTick(){
        SyncPolicy policy=new SyncPolicy();PartyState party=new PartyState();
        ActionQueue commands=new ActionQueue();Transport transport=new Transport();
        for(long now=0;now<20000;now+=50){
            if(policy.activation(true))commands.enqueue(sync(party.generation,now));
            commands.tick(now,1250,transport);
            if(now==50||now==100||now==150){
                assertEquals(PartyMessages.Type.NO_PARTY,PartyMessages.parse("You are not currently in a party.").type);
                commands.cancel(transport);party.reset();policy.partyEnded();
            }
        }
        assertEquals(Collections.singletonList("/p list"),transport.sent);
    }
    @Test public void temporaryWorldLossDoesNotRestartLoginSync(){
        SyncPolicy policy=new SyncPolicy();assertTrue(policy.activation(true));
        for(int i=0;i<100;i++){assertFalse(policy.activation(false));assertFalse(policy.activation(true));}
    }
    @Test public void explicitReconnectOrEnableCanSyncAgain(){
        SyncPolicy policy=new SyncPolicy();assertFalse(policy.activation(false));
        assertTrue(policy.activation(true));assertFalse(policy.activation(true));
        policy.restartActivation();assertTrue(policy.activation(true));assertFalse(policy.activation(true));
    }
    @Test public void failedAutomaticRecoveryIsNotPolledIndefinitely(){
        SyncPolicy policy=new SyncPolicy();assertTrue(policy.recovery());
        for(int i=0;i<1000;i++)assertFalse(policy.recovery());
        policy.synced();assertTrue(policy.recovery());policy.partyEnded();assertFalse(policy.recovery());
        policy.partyJoined();assertTrue(policy.recovery());assertFalse(policy.activation(true));
    }
    @Test public void cancellingPartyStatePreservesCommandSpacing(){
        ActionQueue queue=new ActionQueue();Transport transport=new Transport();
        queue.enqueue(sync(1,0));queue.tick(0,1250,transport);queue.cancel(transport);
        queue.enqueue(sync(2,50));queue.tick(50,1250,transport);queue.tick(1249,1250,transport);
        assertEquals(1,transport.sent.size());queue.tick(1250,1250,transport);assertEquals(2,transport.sent.size());
    }
    @Test public void serverThrottleCooldownSurvivesCancellation(){
        ActionQueue queue=new ActionQueue();Transport transport=new Transport();
        queue.enqueue(sync(1,0));queue.tick(0,1250,transport);queue.cooldown(10000);queue.cancel(transport);
        queue.enqueue(sync(2,100));queue.tick(9999,1250,transport);assertEquals(1,transport.sent.size());
        queue.tick(10000,1250,transport);assertEquals(2,transport.sent.size());
    }
}
