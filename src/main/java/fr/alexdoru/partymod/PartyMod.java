package fr.alexdoru.partymod;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import java.io.File;

@Mod(modid="partymod",name="PartyMod",version="2.0.4",clientSideOnly=true,acceptedMinecraftVersions="[1.8.9]")
public final class PartyMod {
    public static PartyConfig config;
    public static PartyRuntime runtime;
    private File records;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event){records=new File(event.getModConfigurationDirectory(),"partymod/local-records.json");}
    @Mod.EventHandler public void init(FMLInitializationEvent event){runtime=new PartyRuntime(records);config=new PartyConfig();MinecraftForge.EVENT_BUS.register(runtime);ClientCommandHandler.instance.registerCommand(new PartyModCommand());Runtime.getRuntime().addShutdownHook(new Thread(()->runtime.shutdown(),"PartyMod-save"));}
    public static void openRoster(){Minecraft.getMinecraft().addScheduledTask(()->Minecraft.getMinecraft().displayGuiScreen(new fr.alexdoru.partymod.ui.PartyScreen()));}
}
