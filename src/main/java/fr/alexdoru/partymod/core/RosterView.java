package fr.alexdoru.partymod.core;

import java.util.Locale;

/** Review queue always uses review state, independent of the Players filter. */
public final class RosterView {
    public static boolean matches(Member m,String query,String filter,boolean reviewQueue){
        if(!m.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))return false;
        if(reviewQueue)return m.needsReview();
        if(filter.equals("Review"))return m.needsReview();
        if(filter.equals("Pending"))return m.status==Member.Status.PENDING;
        if(filter.equals("Unassessed"))return m.status!=Member.Status.LOADED&&m.status!=Member.Status.CACHED;
        return true;
    }
}
