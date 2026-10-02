package fr.alexdoru.partymod;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Button;
import cc.polyfrost.oneconfig.config.annotations.Dropdown;
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
import fr.alexdoru.partymod.data.TrustedStore;
import org.lwjgl.input.Keyboard;

/**
 * OneConfig settings. Categories appear in declaration order: Getting started first,
 * then the flag rules, actions, overlays and trusted players.
 */
public final class PartyConfig extends Config {

    // ================================================================ Getting started
    @Info(type = InfoType.INFO, size = 2, category = "Getting started", subcategory = "How to host",
            text = "1. Paste your Hypixel API key below and press Test.  2. Press P in-game to open the panels.  "
                    + "3. When someone joins, their stat card appears - Kick, Keep or Trust with one click.")
    public boolean gettingStartedInfo;

    @Text(name = "Hypixel API key", secure = true, size = 2, placeholder = "Paste your key from developer.hypixel.net",
            description = "Stored locally in your OneConfig profile and only ever sent to api.hypixel.net.",
            category = "Getting started", subcategory = "Hypixel API key")
    public String hypixelApiKey = "";

    @Button(name = "Check the key works", text = "Test key", category = "Getting started", subcategory = "Hypixel API key",
            description = "Looks you up on Hypixel and reports the result in chat.")
    public Runnable testKey = () -> PartyMod.runtime.testKey();

    @Button(name = "Get a key", text = "Open site", category = "Getting started", subcategory = "Hypixel API key",
            description = "Opens developer.hypixel.net in your browser.")
    public Runnable openKeySite = () -> PartyRuntime.openUrl("https://developer.hypixel.net/dashboard");

    @KeyBind(name = "Open the party panels", description = "Frees the mouse so you can click, drag and scroll the panels. Default: P",
            category = "Getting started", subcategory = "Panels")
    public OneKeyBind overlayKey = new OneKeyBind(Keyboard.KEY_P);

    @Button(name = "Open the party panels now", text = "Open", category = "Getting started", subcategory = "Panels")
    public Runnable openPanels = () -> PartyMod.openPanels();

    @Dropdown(name = "Flag preset", options = {"Custom", "Lenient", "Balanced", "Strict"},
            description = "Sets every flag threshold at once. Balanced is the default; fine-tune under Flags.",
            category = "Getting started", subcategory = "Flag preset")
    public int preset = 2;

    // ================================================================ Flags
    @Info(type = InfoType.INFO, size = 2, category = "Flags", subcategory = "About",
            text = "Strong flags (red, KICK?) are almost always worth a kick. Weak flags (gold, REVIEW) are worth a look.")
    public boolean flagsInfo;

    @Switch(name = "Flag: never joined Hypixel", description = "Strong flag. Default: on", category = "Flags", subcategory = "Account")
    public boolean flagNoProfile = true;

    @Switch(name = "Flag: rejoined after being removed", description = "Strong flag, using your saved blocked history. Default: on",
            category = "Flags", subcategory = "Account")
    public boolean flagPreviouslyRemoved = true;

    @Switch(name = "Flag: new Hypixel account", description = "Default: on", category = "Flags", subcategory = "Account age")
    public boolean flagNewAccount = true;

    @Slider(name = "...if younger than (days)", min = 1, max = 365, step = 1, description = "Weak flag. Default: 30",
            category = "Flags", subcategory = "Account age")
    public float newAccountDays = 30;

    @Slider(name = "...strong if younger than (days)", min = 1, max = 90, step = 1, description = "Default: 7",
            category = "Flags", subcategory = "Account age")
    public float strongNewAccountDays = 7;

    @Switch(name = "Flag: no rank", description = "Weak flag. Default: on", category = "Flags", subcategory = "Rank and level")
    public boolean flagNoRank = true;

    @Switch(name = "Flag: low network level", description = "Default: on", category = "Flags", subcategory = "Rank and level")
    public boolean flagLowLevel = true;

    @Slider(name = "...if level below", min = 1, max = 100, step = 1, description = "Weak flag. Default: 10",
            category = "Flags", subcategory = "Rank and level")
    public float minNetworkLevel = 10;

    @Switch(name = "Flag: few Mega Walls games", description = "Default: on", category = "Flags", subcategory = "Mega Walls")
    public boolean flagFewGames = true;

    @Slider(name = "...if fewer than (wins + losses)", min = 1, max = 1000, step = 5, description = "Weak flag. Default: 50",
            category = "Flags", subcategory = "Mega Walls")
    public float minGames = 50;

    @Switch(name = "Flag: low Mega Walls stats", description = "Needs 10+ games. Default: on", category = "Flags", subcategory = "Mega Walls")
    public boolean flagLowStats = true;

    @Slider(name = "...if FKDR below", min = 0.1f, max = 3, step = 0, description = "Weak flag. Default: 0.5",
            category = "Flags", subcategory = "Mega Walls")
    public float lowFkd = 0.5f;

    @Switch(name = "Flag: high Mega Walls stats", description = "Needs 10+ games. Default: on", category = "Flags", subcategory = "Mega Walls")
    public boolean flagHighStats = true;

    @Slider(name = "...if FKDR above", min = 1, max = 30, step = 0, description = "Weak flag. Default: 5",
            category = "Flags", subcategory = "Mega Walls")
    public float highFkd = 5;

    @Slider(name = "...or WLR above", min = 0.5f, max = 20, step = 0, description = "Weak flag. Default: 2.5",
            category = "Flags", subcategory = "Mega Walls")
    public float highWl = 2.5f;

    // ================================================================ Actions
    @Info(type = InfoType.INFO, size = 2, category = "Actions", subcategory = "About",
            text = "Removing a player sends /block add <name>, then /p kick <name>. Every removal can be undone for 10 seconds from the party log.")
    public boolean actionsInfo;

    @Switch(name = "Instantly remove competitive-banned players",
            description = "When queueing fails because a member cannot play competitive games, block + kick them. Default: on",
            category = "Actions", subcategory = "Automatic")
    public boolean autoCompetitive = true;

    @Switch(name = "Automatically remove strong flags",
            description = "Block + kick anyone who joins with a strong flag (never joined, very new account, removed before). Default: off",
            category = "Actions", subcategory = "Automatic")
    public boolean autoRemoveFlagged = false;

    @Switch(name = "Warn before queueing with players to review",
            description = "Intercepts /play while someone is still waiting for review and asks what to do. Default: on",
            category = "Actions", subcategory = "Queue guard")
    public boolean queueGuard = true;

    @Switch(name = "Chat prompt for flagged players", description = "Clickable Block + Kick / Keep / Trust buttons in chat. Default: on",
            category = "Actions", subcategory = "Prompts")
    public boolean chatPrompt = true;

    @Switch(name = "Sound when a player is flagged", description = "Default: on", category = "Actions", subcategory = "Prompts")
    public boolean flagSound = true;

    @KeyBind(name = "Kick the next player to review", category = "Actions", subcategory = "Keybinds",
            description = "Works anywhere. Inside the panels (P) you can also press K / J / T to kick / keep / trust.")
    public OneKeyBind removeNextKey = new OneKeyBind(Keyboard.KEY_NONE);

    @KeyBind(name = "Keep the next player to review", category = "Actions", subcategory = "Keybinds")
    public OneKeyBind dismissNextKey = new OneKeyBind(Keyboard.KEY_NONE);

    @Slider(name = "Gap between commands (ms)", min = 300, max = 3000, step = 50,
            description = "Spacing between /block and /p kick so Hypixel doesn't throttle you. Default: 750",
            category = "Actions", subcategory = "Commands")
    public float commandSpacing = 750;

    // ================================================================ Overlays
    @Info(type = InfoType.INFO, size = 2, category = "Overlays", subcategory = "About",
            text = "Panels are placed by dragging their title bar while the panels are open (P or chat) - not through OneConfig's Edit HUD. Click a title to fold a panel.")
    public boolean overlaysInfo;

    @Switch(name = "To review", category = "Overlays", subcategory = "Panels")
    public boolean showReview = true;

    @Switch(name = "Party members", category = "Overlays", subcategory = "Panels")
    public boolean showMembers = true;

    @Switch(name = "Party log", category = "Overlays", subcategory = "Panels")
    public boolean showLog = true;

    @Switch(name = "Blocked players", category = "Overlays", subcategory = "Panels")
    public boolean showBlocked = true;

    @Switch(name = "Quiet during Mega Walls matches",
            description = "Mid-game, panels shrink to a small 'N waiting' badge until you open them. Default: on",
            category = "Overlays", subcategory = "Behaviour")
    public boolean quietInMatch = true;

    @Switch(name = "Hide empty panels while playing", description = "Default: off", category = "Overlays", subcategory = "Behaviour")
    public boolean hideEmpty = false;

    @Slider(name = "Panel scale", min = 0.5f, max = 1.5f, step = 0, description = "Default: 0.85", category = "Overlays", subcategory = "Look")
    public float panelScale = 0.85f;

    @Slider(name = "Panel width", min = 150, max = 320, step = 5, description = "Default: 230", category = "Overlays", subcategory = "Look")
    public float panelWidth = 230;

    @Slider(name = "Entries shown per panel (scroll for more)", min = 2, max = 25, step = 1, description = "Default: 6",
            category = "Overlays", subcategory = "Look")
    public float maxRows = 6;

    @Slider(name = "Background opacity", min = 0, max = 1, step = 0, description = "Default: 0.55", category = "Overlays", subcategory = "Look")
    public float backgroundOpacity = 0.55f;

    @Button(name = "Panel positions", text = "Reset", category = "Overlays", subcategory = "Look")
    public Runnable resetPositionsButton = () -> {
        resetPositions();
        save();
    };

    // Panel layout, changed by dragging/folding and saved with the profile.
    public float reviewX, reviewY, membersX, membersY, logX, logY, blockedX, blockedY;
    public boolean foldReview, foldMembers, foldLog, foldBlocked;

    // ================================================================ Trusted players
    @Info(type = InfoType.INFO, size = 2, category = "Trusted players", subcategory = "About",
            text = "Trusted players are never flagged or auto-removed. Edit the list here, or use the Trust button on a player card.")
    public boolean trustedInfo;

    @Text(name = "Trusted players", multiline = true, size = 2, placeholder = "One name per line",
            category = "Trusted players", subcategory = "List")
    public String trustedText = "";

    @Info(type = InfoType.INFO, size = 2, category = "Trusted players", subcategory = "Share with co-hosts",
            text = "Export your trusted and blocked lists, and import a co-host's. Importing only adds - nothing you have is removed.")
    public boolean shareInfo;

    @Button(name = "Copy lists to clipboard", text = "Copy", category = "Trusted players", subcategory = "Share with co-hosts",
            description = "Paste it into Discord or a DM for a co-host.")
    public Runnable exportClipboard = () -> PartyMod.runtime.exportToClipboard();

    @Button(name = "Import lists from clipboard", text = "Paste", category = "Trusted players", subcategory = "Share with co-hosts",
            description = "Copy a co-host's export first, then press Paste.")
    public Runnable importClipboard = () -> PartyMod.runtime.importFromClipboard();

    @Button(name = "Export lists to a file", text = "Export", category = "Trusted players", subcategory = "Share with co-hosts",
            description = "Saves export-<date>.json in config/megawallspartymod/.")
    public Runnable exportFile = () -> PartyMod.runtime.exportToFile();

    @Button(name = "Import the newest export file", text = "Import", category = "Trusted players", subcategory = "Share with co-hosts",
            description = "Reads the newest export-*.json in config/megawallspartymod/.")
    public Runnable importFile = () -> PartyMod.runtime.importFromFile("");

    // ================================================================ Advanced
    @Slider(name = "Lookups per minute", min = 5, max = 120, step = 5, description = "Hypixel allows about 60. Default: 60",
            category = "Advanced", subcategory = "Hypixel API")
    public float requestsPerMinute = 60;

    @Slider(name = "Cache results (minutes)", min = 1, max = 240, step = 1, description = "Default: 30",
            category = "Advanced", subcategory = "Hypixel API")
    public float cacheMinutes = 30;

    @Button(name = "Re-check the whole party", text = "Re-check", category = "Advanced", subcategory = "Hypixel API")
    public Runnable recheck = () -> PartyMod.runtime.recheckAll();

    @Button(name = "Saved blocked history", text = "Clear", category = "Advanced", subcategory = "History",
            description = "Forgets everyone you removed. Does not unblock them on Hypixel.")
    public Runnable clearHistory = () -> PartyMod.runtime.clearHistory();

    public PartyConfig() {
        super(new Mod(PartyMod.NAME, ModType.HYPIXEL, "/assets/partymod/icon.svg"), "megawallspartymod.json");
        resetPositions();
        initialize();
        registerKeyBind(overlayKey, PartyMod::openPanels);
        registerKeyBind(removeNextKey, () -> PartyMod.runtime.removeNext());
        registerKeyBind(dismissNextKey, () -> PartyMod.runtime.dismissNext());
        addListener("preset", this::applyPreset);
        addListener("trustedText", () -> PartyMod.runtime.trusted.setAll(TrustedStore.parse(trustedText)));
        hideIf("newAccountDays", () -> !flagNewAccount);
        hideIf("strongNewAccountDays", () -> !flagNewAccount);
        hideIf("minNetworkLevel", () -> !flagLowLevel);
        hideIf("minGames", () -> !flagFewGames);
        hideIf("lowFkd", () -> !flagLowStats);
        hideIf("highFkd", () -> !flagHighStats);
        hideIf("highWl", () -> !flagHighStats);
    }

    /** First key of the overlay keybind, so the panel screen can close on the same key. */
    public int overlayKeyCode() {
        return overlayKey.getKeyBinds().isEmpty() ? Keyboard.KEY_NONE : overlayKey.getKeyBinds().get(0);
    }

    private void resetPositions() {
        reviewX = 0.005f;
        reviewY = 0.08f;
        membersX = 0.005f;
        membersY = 0.40f;
        logX = 0.78f;
        logY = 0.55f;
        blockedX = 0.78f;
        blockedY = 0.30f;
    }

    private void applyPreset() {
        if (preset == 0) return;
        Flagger.Options o = flagOptions();
        Flagger.applyPreset(preset, o);
        newAccountDays = o.newAccountDays;
        strongNewAccountDays = o.strongNewAccountDays;
        flagNoRank = o.noRank;
        minNetworkLevel = (float) o.minNetworkLevel;
        minGames = o.minGames;
        lowFkd = (float) o.lowFkd;
        highFkd = (float) o.highFkd;
        highWl = (float) o.highWl;
        save();
        if (PartyMod.runtime != null) PartyMod.runtime.reassessAll();
    }

    /** Mirrors the trusted store into the text box (after Trust/Untrust buttons). */
    public void syncTrustedText(java.util.List<String> names) {
        trustedText = String.join("\n", names);
        save();
    }

    public Flagger.Options flagOptions() {
        Flagger.Options o = new Flagger.Options();
        o.noProfile = flagNoProfile;
        o.newAccount = flagNewAccount;
        o.newAccountDays = (int) newAccountDays;
        o.strongNewAccountDays = (int) strongNewAccountDays;
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
