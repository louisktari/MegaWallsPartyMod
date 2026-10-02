package fr.alexdoru.partymod.core;

/** Evaluate permission and current context when a queued action reaches the wire. */
public final class ActionPolicy {
    public static boolean valid(ActionQueue.Action a,PartyState state,String self,boolean active,boolean fresh,boolean autoBlock,boolean ageKick,int days,int capacity,long now,boolean invitePresent){
        if(!active||a.generation!=state.generation)return false;
        Member target=state.get(a.name),actor=state.get(self);
        if(a.kind==ActionQueue.Kind.BLOCK&&a.automatic&&!autoBlock)return false;
        if(a.kind==ActionQueue.Kind.READY)return fresh&&actor!=null&&actor.role==Member.Role.LEADER&&!state.round.isEmpty()&&a.context.equals(state.round);
        if(a.kind==ActionQueue.Kind.REMOVE||a.kind==ActionQueue.Kind.NEW_ACCOUNT_REMOVE)return fresh&&state.valid(a.generation,a.name,a.membership)&&state.canRemove(self,target)&&(a.kind!=ActionQueue.Kind.NEW_ACCOUNT_REMOVE||NewAccountPolicy.eligible(target,ageKick,days,now));
        if(a.membership>0)return state.valid(a.generation,a.name,a.membership);
        if(a.kind==ActionQueue.Kind.INVITE)return invitePresent&&fresh&&target==null&&state.members().size()<capacity&&actor!=null&&(actor.role==Member.Role.LEADER||actor.role==Member.Role.MODERATOR);
        return true;
    }
}
