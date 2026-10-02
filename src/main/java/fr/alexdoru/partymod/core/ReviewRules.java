package fr.alexdoru.partymod.core;

import java.time.*;
import java.util.*;

public final class ReviewRules {
    public static final class Options {
        public boolean lowExperience=true, recentAccount=true, ratios=true, kits=true, skins=true, activity=true, calendarMonths=true;
        public int minGames=150, accountDays=90, activityDays=90, activityMonths=3, kitGames=15;
        public double minLevel=80, fkd25=3.5, fkd250=5, fkd500=8, product=4.95;
    }
    public static List<String> evaluate(PlayerStats s,Options o,long now,Long lastWitnessed) {
        List<String> reasons=new ArrayList<>();
        if(s!=null) {
            Integer games=s.games(); Double fkd=s.fkd(),wl=s.wl();
            if(o.lowExperience) {if(s.networkLevel!=null&&s.networkLevel<o.minLevel) reasons.add(String.format(Locale.ROOT,"Network level %.1f below %.0f",s.networkLevel,o.minLevel));if(games!=null&&games<o.minGames) reasons.add("Recorded wins + losses: "+games+" below "+o.minGames);}
            if(o.recentAccount&&s.firstLogin!=null&&s.firstLogin>0&&s.firstLogin<=now&&now-s.firstLogin<o.accountDays*86400000L) reasons.add("First Hypixel login within "+o.accountDays+" days");
            if(o.ratios&&games!=null&&fkd!=null) {double threshold=Double.POSITIVE_INFINITY;if(games<=25)threshold=Math.min(threshold,o.fkd25);if(games<=250)threshold=Math.min(threshold,o.fkd250);if(games<=500)threshold=Math.min(threshold,o.fkd500);if(fkd>threshold) reasons.add(String.format(Locale.ROOT,"FKD %.2f above %.2f across %d recorded games",fkd,threshold,games));if(wl!=null&&fkd*wl>o.product) reasons.add(String.format(Locale.ROOT,"FKD × W/L %.2f above %.2f (history heuristic)",fkd*wl,o.product));}
            if(o.kits&&games!=null&&games<o.kitGames&&s.networkLevel!=null&&s.networkLevel<=100&&(games<3||s.quests!=null&&s.quests<30&&s.networkLevel>25)) for(Map.Entry<String,PlayerStats.Kit> entry:s.kits.entrySet()) {if(entry.getKey().equals("Cow")||entry.getKey().equals("Hunter")||entry.getKey().equals("Shark")) continue;PlayerStats.Kit k=entry.getValue();if(games==0&&Boolean.TRUE.equals(k.unlocked)&&(k.kit!=null&&k.kit>=4||k.ability!=null&&k.ability>=4))reasons.add("Advanced "+entry.getKey()+" kit/ability with zero recorded games; coin sources unknown");else if(k.maxed()) reasons.add("Fully upgraded "+entry.getKey()+" with "+games+" recorded games; coin sources unknown");}
            if(o.skins&&games!=null&&games>=0&&s.legendary!=null&&s.legendary>0&&s.legendary*12L>=Math.max(1,games)) reasons.add(s.legendary+" legendary achievements across "+games+" recorded games (history heuristic)");
        }
        if(o.activity&&lastWitnessed!=null&&lastWitnessed>0) {
            long cutoff=o.calendarMonths?Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).minusMonths(o.activityMonths).toInstant().toEpochMilli():now-o.activityDays*86400000L;
            if(lastWitnessed<cutoff) reasons.add("Local MW attendance gap; games outside your events unknown");
        }
        return reasons;
    }
}
