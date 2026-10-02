package fr.alexdoru.partymod;

import fr.alexdoru.partymod.ui.OverlayScreen;
import fr.alexdoru.partymod.ui.Panels;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = PartyMod.MODID, name = PartyMod.NAME, useMetadata = true, clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]")
public final class PartyMod {
    public static final String MODID = "partymod";
    public static final String NAME = "Mega Walls Party Mod";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    public static PartyConfig config;
    public static PartyRuntime runtime;
    private File dataDir;

    /** Jar version from the manifest (set by Gradle); "dev" when run from an IDE. */
    public static String version() {
        String v = PartyMod.class.getPackage() == null ? null : PartyMod.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        dataDir = new File(event.getModConfigurationDirectory(), "megawallspartymod");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        runtime = new PartyRuntime(dataDir);
        config = new PartyConfig();
        // The trusted file is the source of truth; show it in the OneConfig text box.
        config.syncTrustedText(runtime.trusted.all());
        MinecraftForge.EVENT_BUS.register(runtime);
        MinecraftForge.EVENT_BUS.register(new Panels());
        ClientCommandHandler.instance.registerCommand(new PartyCommand());
        ClientCommandHandler.instance.registerCommand(new PlayCommand());
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::shutdown, "MegaWallsPartyMod-shutdown"));
        LOGGER.info("{} v{} loaded. Press P in-game for the party panels.", NAME, version());
    }

    /** Opens the interactive panel screen (frees the mouse). */
    public static void openPanels() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> mc.displayGuiScreen(new OverlayScreen(config.overlayKeyCode())));
    }
}
