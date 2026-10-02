package fr.alexdoru.partymod.ui;

import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import fr.alexdoru.partymod.PartyMod;

public final class PartyHud extends SingleTextHud {
    @Switch(name="Party count") public boolean count=true;
    @Switch(name="Review count") public boolean reviews=true;
    @Switch(name="Pending checks") public boolean pending=true;
    @Switch(name="Ready acknowledgements") public boolean ready=false;
    @Switch(name="Roster freshness") public boolean freshness=true;
    public PartyHud(){super("",false,18,18,1,true,true,6,10,7,new OneColor(20,23,35,210),true,1,new OneColor(141,124,247,100));}
    @Override protected String getText(boolean example){if(example)return "Party 86/100  ·  Review 7  ·  Pending 11";if(PartyMod.runtime==null||!PartyMod.runtime.active())return "";StringBuilder text=new StringBuilder();if(count)text.append("Party ").append(PartyMod.runtime.state.members().size()).append('/').append((int)PartyMod.config.capacity);if(reviews)append(text,"Review "+PartyMod.runtime.state.countReview());if(pending)append(text,"Pending "+PartyMod.runtime.state.countPending());if(ready&&!PartyMod.runtime.state.round.isEmpty())append(text,"Ready "+PartyMod.runtime.state.members().stream().filter(m->m.ready).count());if(freshness&&!PartyMod.runtime.rosterFresh())append(text,"Sync needed");return text.toString();}
    private void append(StringBuilder text,String part){if(text.length()>0)text.append("  ·  ");text.append(part);}
}
