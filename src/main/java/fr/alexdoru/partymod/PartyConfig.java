package fr.alexdoru.partymod;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Button;
import cc.polyfrost.oneconfig.config.annotations.Info;
import cc.polyfrost.oneconfig.config.annotations.KeyBind;
import cc.polyfrost.oneconfig.config.annotations.Slider;
import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.config.annotations.Text;
import cc.polyfrost.oneconfig.config.core.OneKeyBind;
import cc.polyfrost.oneconfig.config.data.InfoType;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import fr.alexdoru.partymod.core.Flagger;
import fr.alexdoru.partymod.ui.OverlayScreen;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public final class PartyConfig extends Config {

    // --- Flags ---
    @Info(type = InfoType.INFO, size = 2, category = "Flags", subcategory = "About",
            text = "Every player who joins your party is stat-checked. Matching any enabled rule puts them in the review overlay.")
    public boolean flagsInfo;

    @Switch(name = "Never joined Hypixel", category = "Flags", subcategory = "Account")
    public boolean flagNoProfile = true;

    @Switch(name = "New Hypixel account", category = "Flags", subcategory = "Account")
    public boolean flagNewAccount = true;

    @Slider(name = "New account if younger than (days)", min = 1, max = 365, step = 1, category = "Flags", subcategory = "Account")
    public float newAccountDays = 30;

    @Switch(name = "No rank", category = "Flags", subcategory = "Account")
    public boolean flagNoRank = true;

    @Switch(name = "Low network level", category = "Flags", subcategory = "Account")
    public boolean flagLowLevel = true;

    @Slider(name = "Minimum network level", min = 1, max = 100, step = 1, category = "Flags", subcategory = "Account")
    public float minNetworkLevel = 10;

    @Switch(name = "Few Mega Walls games", category = "Flags", subcategory = "Mega Walls")
    public boolean flagFewGames = true;

    @Slider(name = "Minimum games (wins + losses)", min = 1, max = 1000, step = 5, category = "Flags", subcategory = "Mega Walls")
    public float minGames = 50;

    @Switch(name = "Low Mega Walls stats", category = "Flags", subcategory = "Mega Walls")
    public boolean flagLowStats = true;

    @Slider(name = "Low if FKDR below", min = 0.1f, max = 3, step = 0, category = "Flags", subcategory = "Mega Walls")
    public float lowFkd = 0.5f;

    @Switch(name = "High Mega Walls stats", category = "Flags", subcategory = "Mega Walls")
    public boolean flagHighStats = true;

    @Slider(name = "High if FKDR above", min = 1, max = 30, step = 0, category = "Flags", subcategory = "Mega Walls")
    public float highFkd = 5;

    @Slider(name = "High if WLR above", min = 0.5f, max = 20, step = 0, category = "Flags", subcategory = "Mega Walls")
    public float highWl = 2.5f;

    // --- Actions ---
    @Info(type = InfoType.INFO, size = 2, category = "Actions", subcategory = "About",
            text = "Removing a player always sends /block add <name>, then /p kick <name>, one after the other.")
    public boolean actionsInfo;

    @Switch(name = "Instantly remove competitive-banned players",
            description = "When queueing fails because a party member cannot play competitive games, block + kick them.",
            category = "Actions", subcategory = "Automatic")
    public boolean autoCompetitive = true;

    @Switch(name = "Automatically remove flagged players",
            description = "Block + kick anyone who joins and matches a flag rule, without asking.",
            category = "Actions", subcategory = "Automatic")
    public boolean autoRemoveFlagged = false;

    @Switch(name = "Clickable prompt in chat for flagged players", category = "Actions", subcategory = "Prompts")
    public boolean chatPrompt = true;

    @Switch(name = "Sound when a player is flagged", category = "Actions", subcategory = "Prompts")
    public boolean flagSound = true;

    @KeyBind(name = "Remove next player to review", category = "Actions", subcategory = "Keybinds")
    public OneKeyBind removeNextKey = new OneKeyBind(Keyboard.KEY_NONE);

    @KeyBind(name = "Dismiss next player to review", category = "Actions", subcategory = "Keybinds")
    public OneKeyBind dismissNextKey = new OneKeyBind(Keyboard.KEY_NONE);

    @Slider(name = "Gap between commands (ms)", min = 300, max = 3000, step = 50, category = "Actions", subcategory = "Commands")
    public float commandSpacing = 750;

    // --- Overlays ---
    @Info(type = InfoType.INFO, size = 2, category = "Overlays", subcategory = "About",
            text = "Open chat (or press the overlay key) to use the panels: drag a title bar to move, hover a player for stats, click buttons to act.")
    public boolean overlaysInfo;

    @KeyBind(name = "Open interactive overlay", category = "Overlays", subcategory = "About")
    public OneKeyBind overlayKey = new OneKeyBind(Keyboard.KEY_P);

    @Switch(name = "To review", category = "Overlays", subcategory = "Panels")
    public boolean showReview = true;

    @Switch(name = "Party members", category = "Overlays", subcategory = "Panels")
    public boolean showMembers = true;

    @Switch(name = "Party log", category = "Overlays", subcategory = "Panels")
    public boolean showLog = true;

    @Switch(name = "Blocked players", category = "Overlays", subcategory = "Panels")
    public boolean showBlocked = true;

    @Switch(name = "Hide empty panels while playing", category = "Overlays", subcategory = "Look")
    public boolean hideEmpty = false;

    @Slider(name = "Panel scale", min = 0.5f, max = 1.5f, step = 0, category = "Overlays", subcategory = "Look")
    public float panelScale = 0.85f;

    @Slider(name = "Panel width", min = 120, max = 320, step = 5, category = "Overlays", subcategory = "Look")
    public float panelWidth = 200;

    @Slider(name = "Max rows per panel", min = 3, max = 25, step = 1, category = "Overlays", subcategory = "Look")
    public float maxRows = 8;

    @Slider(name = "Background opacity", min = 0, max = 1, step = 0, category = "Overlays", subcategory = "Look")
    public float backgroundOpacity = 0.55f;

    @Button(name = "Panel positions", text = "Reset", category = "Overlays", subcategory = "Look")
    public Runnable resetPositionsButton = () -> {
        resetPositions();
        save();
    };

    // Panel positions as fractions of the screen; changed by dragging, saved with the profile.
    public float reviewX, reviewY, membersX, membersY, logX, logY, blockedX, blockedY;

    private void resetPositions() {
        reviewX = 0.005f;
        reviewY = 0.08f;
        membersX = 0.005f;
        membersY = 0.33f;
        logX = 0.005f;
        logY = 0.58f;
        blockedX = 0.78f;
        blockedY = 0.55f;
    }

    // --- Hypixel API ---
    @Text(name = "Hypixel API key", secure = true, placeholder = "Paste your key from developer.hypixel.net",
            description = "Stored locally in your OneConfig profile. Only sent to api.hypixel.net.",
            category = "Hypixel API", subcategory = "Key")
    public String hypixelApiKey = "";

    @Slider(name = "Lookups per minute", min = 5, max = 120, step = 5, category = "Hypixel API", subcategory = "Limits")
    public float requestsPerMinute = 60;

    @Slider(name = "Cache results (minutes)", min = 1, max = 240, step = 1, category = "Hypixel API", subcategory = "Limits")
    public float cacheMinutes = 30;

    @Button(name = "Re-check current party", text = "Re-check", category = "Hypixel API", subcategory = "Key")
    public Runnable recheck = () -> PartyMod.runtime.recheckAll();

    public PartyConfig() {
        super(new Mod(PartyMod.NAME, ModType.HYPIXEL, "/assets/partymod/icon.svg"), "megawallspartymod.json");
        resetPositions();
        initialize();
        registerKeyBind(overlayKey, () -> {
            int key = overlayKey.getKeyBinds().isEmpty() ? Keyboard.KEY_NONE : overlayKey.getKeyBinds().get(0);
            Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().displayGuiScreen(new OverlayScreen(key)));
        });
        registerKeyBind(removeNextKey, () -> PartyMod.runtime.removeNext());
        registerKeyBind(dismissNextKey, () -> PartyMod.runtime.dismissNext());
        hideIf("newAccountDays", () -> !flagNewAccount);
        hideIf("minNetworkLevel", () -> !flagLowLevel);
        hideIf("minGames", () -> !flagFewGames);
        hideIf("lowFkd", () -> !flagLowStats);
        hideIf("highFkd", () -> !flagHighStats);
        hideIf("highWl", () -> !flagHighStats);
    }

    public Flagger.Options flagOptions() {
        Flagger.Options o = new Flagger.Options();
        o.noProfile = flagNoProfile;
        o.newAccount = flagNewAccount;
        o.newAccountDays = (int) newAccountDays;
        o.noRank = flagNoRank;
        o.lowLevel = flagLowLevel;
        o.minNetworkLevel = minNetworkLevel;
        o.fewGames = flagFewGames;
        o.minGames = (int) minGames;
        o.lowStats = flagLowStats;
        o.lowFkd = lowFkd;
        o.highStats = flagHighStats;
        o.highFkd = highFkd;
        o.highWl = highWl;
        return o;
    }
}
