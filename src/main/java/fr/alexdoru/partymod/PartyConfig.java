package fr.alexdoru.partymod;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Button;
import cc.polyfrost.oneconfig.config.annotations.HUD;
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
import fr.alexdoru.partymod.ui.Overlays;
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
            text = "Each overlay is its own on-screen panel. Use OneConfig's Edit HUD to move and resize them.")
    public boolean overlaysInfo;

    @HUD(name = "Party log", category = "Overlays", subcategory = "Party log")
    public Overlays.LogHud logHud = new Overlays.LogHud();

    @HUD(name = "Party members", category = "Overlays", subcategory = "Party members")
    public Overlays.MembersHud membersHud = new Overlays.MembersHud();

    @HUD(name = "To review", category = "Overlays", subcategory = "To review")
    public Overlays.ReviewHud reviewHud = new Overlays.ReviewHud();

    @HUD(name = "Blocked players", category = "Overlays", subcategory = "Blocked players")
    public Overlays.BlockedHud blockedHud = new Overlays.BlockedHud();

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
        initialize();
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
