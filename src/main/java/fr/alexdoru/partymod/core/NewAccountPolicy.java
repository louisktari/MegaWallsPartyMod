package fr.alexdoru.partymod.core;

import fr.alexdoru.partymod.data.DirectHypixel;

/** Only newly observed joins are eligible; a list sync never makes someone a candidate. */
public final class NewAccountPolicy {
    public static boolean eligible(Member member,boolean enabled,int days,long now){return enabled&&days>=1&&days<=365&&member!=null&&member.ageCandidate&&!member.approved&&(member.status==Member.Status.LOADED||member.status==Member.Status.CACHED)&&member.stats!=null&&member.stats.fetchedAt!=null&&member.stats.fetchedAt>0&&member.stats.fetchedAt<=now+60000L&&now-member.stats.fetchedAt<=86400000L&&DirectHypixel.tooNew(member.stats,days,now);}
    public static boolean canSend(PartyState state,String self,Member member,boolean enabled,int days,long now,boolean fresh){return fresh&&state.canRemove(self,member)&&eligible(member,enabled,days,now);}
}
