package fr.alexdoru.partymod.core;

/** Binds a visible action to the member and evidence drawn in that frame. */
public final class MemberTicket {
    private final Member member;
    private final long generation,membership;
    private final String uuid,evidence;
    public MemberTicket(PartyState state,Member member){this.member=member;generation=state.generation;membership=member.membershipId;uuid=member.uuid;evidence=member.fingerprint();}
    public boolean valid(PartyState state){return state.get(member.name)==member&&state.valid(generation,member.name,membership)&&member.uuid.equals(uuid)&&member.fingerprint().equals(evidence);}
}
