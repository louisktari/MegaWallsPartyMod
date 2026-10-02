package fr.alexdoru.partymod;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = PartyMod.MODID, name = PartyMod.NAME, useMetadata = true, clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]")
public final class PartyMod {
    public static final String MODID = "partymod";
    public static final String NAME = "Mega Walls Party Mod";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    public static PartyConfig config;
    public static PartyRuntime runtime;

    /** Jar version from the manifest (set by Gradle); "dev" when run from an IDE. */
    public static String version() {
        String v = PartyMod.class.getPackage() == null ? null : PartyMod.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        runtime = new PartyRuntime();
        config = new PartyConfig();
        MinecraftForge.EVENT_BUS.register(runtime);
        ClientCommandHandler.instance.registerCommand(new PartyCommand());
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::shutdown, "MegaWallsPartyMod-shutdown"));
        LOGGER.info("{} v{} loaded. Type /mwp help in-game.", NAME, version());
    }
}
