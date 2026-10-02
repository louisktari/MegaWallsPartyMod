package fr.alexdoru.partymod.smoke;

import fr.alexdoru.partymod.*;
import fr.alexdoru.partymod.core.*;
import fr.alexdoru.partymod.ui.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.io.File;
import java.lang.reflect.Field;

/** Isolated render fixture; never packaged in PartyMod's deliverable JAR. */
@Mod(modid="partymodsmoke",version="1",dependencies="required-after:partymod",clientSideOnly=true)
public class PartySmokeProbe {
    private int stage;private long deadline;
    @Mod.EventHandler public void init(FMLInitializationEvent event){MinecraftForge.EVENT_BUS.register(this);System.out.println("PARTYMOD_SMOKE_INITIALISED");deadline=System.currentTimeMillis()+14000;}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END||System.currentTimeMillis()<deadline)return;Minecraft mc=Minecraft.getMinecraft();try{switch(stage++){
        case 0:
            long now=System.currentTimeMillis();PartyMod.runtime.state.beginSync(100,now);PartyMod.runtime.state.stage("CodexSmoke",Member.Role.LEADER);for(int i=0;i<99;i++)PartyMod.runtime.state.stage(String.format("Player%02d",i),Member.Role.MEMBER);PartyMod.runtime.state.commitSync(now);
            for(Member member:PartyMod.runtime.state.members()){member.status=Member.Status.LOADED;member.stats=PlayerStats.parseSummary(new JsonParser().parse("{\"uuid\":\"00000000000000000000000000000001\",\"name\":\""+member.name+"\",\"networkLevel\":65,\"firstLogin\":1600000000000,\"fetchedAt\":"+now+",\"megaWalls\":{\"wins\":20,\"losses\":17,\"finalKills\":340,\"finalDeaths\":50}}").getAsJsonObject());}
            PartyMod.runtime.reassess();PartyMod.runtime.store.addWait("Replacement");mc.displayGuiScreen(new PartyScreen());System.out.println("PARTYMOD_ROSTER_OPENED");deadline=now+3500;break;
        case 1:testNoteBinding(mc);testStaleUiAction(mc);shot(mc,"roster.png");setTab(mc,"Review queue");deadline=System.currentTimeMillis()+2000;break;
        case 2:shot(mc,"reviews.png");setTab(mc,"Event desk");deadline=System.currentTimeMillis()+2000;break;
        case 3:shot(mc,"event-desk.png");PartyMod.config.openGui();System.out.println("PARTYMOD_ONECONFIG_OPENED");deadline=System.currentTimeMillis()+5000;break;
        case 4:shot(mc,"oneconfig.png");category("Reviews");deadline=System.currentTimeMillis()+2000;break;
        case 5:shot(mc,"oneconfig-reviews.png");category("Actions");deadline=System.currentTimeMillis()+2000;break;
        case 6:shot(mc,"oneconfig-actions.png");category("HUD");deadline=System.currentTimeMillis()+2000;break;
        case 7:shot(mc,"oneconfig-hud.png");category("Data");deadline=System.currentTimeMillis()+2000;break;
        case 8:shot(mc,"oneconfig-data.png");PartyMod.config.editHud.run();deadline=System.currentTimeMillis()+3000;break;
        case 9:shot(mc,"hud-editor.png");System.out.println("PARTYMOD_HUD_EDITOR_SCREEN "+mc.currentScreen.getClass().getName());PartyMod.config.enabled=false;PartyMod.runtime.applyEnabled();PartyMod.config.enabled=true;PartyMod.runtime.applyEnabled();System.out.println("PARTYMOD_TOGGLE_CYCLE_OK");deadline=System.currentTimeMillis()+1000;break;
        default:System.out.println("PARTYMOD_RENDER_SMOKE_COMPLETE");mc.shutdown();deadline=Long.MAX_VALUE;break;
    }}catch(Throwable failure){failure.printStackTrace();System.out.println("PARTYMOD_RENDER_SMOKE_FAILED");mc.shutdown();deadline=Long.MAX_VALUE;}}
    private void setTab(Minecraft mc,String tab)throws Exception{Field f=PartyScreen.class.getDeclaredField("tab");f.setAccessible(true);f.set(mc.currentScreen,tab);}
    private void testNoteBinding(Minecraft mc)throws Exception{
        PartyScreen screen=(PartyScreen)mc.currentScreen;
        Field selected=PartyScreen.class.getDeclaredField("selected");selected.setAccessible(true);
        Member before=PartyMod.runtime.state.get((String)selected.get(screen));
        PartyMod.runtime.store.record(before).note="Resize keeps this host note";
        ((net.minecraft.client.gui.GuiScreen)screen).initGui();Field note=PartyScreen.class.getDeclaredField("note");note.setAccessible(true);
        net.minecraft.client.gui.GuiTextField field=(net.minecraft.client.gui.GuiTextField)note.get(screen);
        if(!field.getText().equals("Resize keeps this host note"))throw new AssertionError("Note lost during resize");
        PartyMod.runtime.state.leave(before.name);Member replacement=PartyMod.runtime.state.join(before.name,System.currentTimeMillis());
        PartyMod.runtime.store.delete(replacement);field.setFocused(true);
        java.lang.reflect.Method key;try{key=PartyScreen.class.getDeclaredMethod("func_73869_a",char.class,int.class);}catch(NoSuchMethodException dev){key=PartyScreen.class.getDeclaredMethod("keyTyped",char.class,int.class);}key.setAccessible(true);key.invoke(screen,'x',org.lwjgl.input.Keyboard.KEY_X);
        if(!PartyMod.runtime.store.record(replacement).note.isEmpty())throw new AssertionError("Old note written into new membership");
        PartyMod.runtime.state.role(replacement.name,Member.Role.LEADER,System.currentTimeMillis());
        replacement.status=before.status;replacement.stats=before.stats;PartyMod.runtime.reassess();
        System.out.println("PARTYMOD_NOTE_RESIZE_REJOIN_OK");
    }
    private void testStaleUiAction(Minecraft mc)throws Exception{
        PartyScreen screen=(PartyScreen)mc.currentScreen;
        Field selected=PartyScreen.class.getDeclaredField("selected");selected.setAccessible(true);
        Member member=PartyMod.runtime.state.get((String)selected.get(screen));
        fr.alexdoru.partymod.data.LocalStore.Record before=PartyMod.runtime.store.record(member);before.blocked=false;
        ((net.minecraft.client.gui.GuiScreen)screen).drawScreen(0,0,0);
        String previousUuid=member.uuid;member.uuid="00000000000000000000000000000002";
        fr.alexdoru.partymod.data.LocalStore.Record current=new fr.alexdoru.partymod.data.LocalStore.Record();current.uuid=member.uuid;
        PartyMod.runtime.store.records.put("uuid:"+member.uuid,current);
        Field scale=PartyScreen.class.getDeclaredField("scale"),ox=PartyScreen.class.getDeclaredField("ox"),oy=PartyScreen.class.getDeclaredField("oy");scale.setAccessible(true);ox.setAccessible(true);oy.setAccessible(true);
        int x=(int)(ox.getFloat(screen)+630*scale.getFloat(screen)),y=(int)(oy.getFloat(screen)+488*scale.getFloat(screen));
        java.lang.reflect.Method click;try{click=PartyScreen.class.getDeclaredMethod("func_73864_a",int.class,int.class,int.class);}catch(NoSuchMethodException dev){click=PartyScreen.class.getDeclaredMethod("mouseClicked",int.class,int.class,int.class);}click.setAccessible(true);click.invoke(screen,x,y,0);
        if(before.blocked||current.blocked)throw new AssertionError("Stale rendered action changed a host blocklist record");
        ((net.minecraft.client.gui.GuiScreen)screen).drawScreen(0,0,0);click.invoke(screen,x,y,0);
        if(!current.blocked)throw new AssertionError("Fresh host blocklist action did not execute");
        current.blocked=false;
        member.uuid=previousUuid;((net.minecraft.client.gui.GuiScreen)screen).drawScreen(0,0,0);
        PartyMod.runtime.reassess();
        System.out.println("PARTYMOD_STALE_UI_ACTION_OK");
    }
    private void category(String category)throws Exception{Field f=cc.polyfrost.oneconfig.gui.OneConfigGui.class.getDeclaredField("currentPage");f.setAccessible(true);((cc.polyfrost.oneconfig.gui.pages.ModConfigPage)f.get(cc.polyfrost.oneconfig.gui.OneConfigGui.INSTANCE)).switchCategory(category);}
    private void shot(Minecraft mc,String name){ScreenShotHelper.saveScreenshot(mc.mcDataDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());System.out.println("PARTYMOD_SCREENSHOT "+new File(mc.mcDataDir,"screenshots/"+name));}
}
