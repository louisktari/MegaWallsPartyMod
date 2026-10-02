package fr.alexdoru.partymod;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.*;
import cc.polyfrost.oneconfig.config.core.OneKeyBind;
import cc.polyfrost.oneconfig.config.data.*;
import fr.alexdoru.partymod.core.ReviewRules;
import fr.alexdoru.partymod.ui.PartyHud;
import org.lwjgl.input.Keyboard;

public final class PartyConfig extends Config {
    @Info(type=InfoType.INFO,size=2,text="Your party, clearly organised. Open the roster to review members and prepare an event.",category="Party",subcategory="Overview") public boolean overview;
    @Button(name="Party organiser",text="Open roster",category="Party",subcategory="Overview") public Runnable open=PartyMod::openRoster;
    @KeyBind(name="Open organiser",category="Party",subcategory="Shortcuts") public OneKeyBind openKey=new OneKeyBind(Keyboard.KEY_P);
    @KeyBind(name="Toggle organiser",category="Party",subcategory="Shortcuts") public OneKeyBind toggleKey=new OneKeyBind(Keyboard.KEY_NONE);
    @Slider(name="Target party size",min=2,max=100,step=1,category="Party",subcategory="Event") public float capacity=100;
    @Slider(name="Roster freshness (seconds)",min=15,max=300,step=15,category="Party",subcategory="Event") public float freshness=120;
    @Slider(name="Disconnect grace (seconds)",min=10,max=300,step=10,category="Party",subcategory="Event") public float grace=90;
    @Switch(name="Low-experience reviews",category="Reviews",subcategory="Account history") public boolean lowExperience=true;
    @Slider(name="Minimum recorded games",min=0,max=500,step=5,category="Reviews",subcategory="Account history") public float minGames=150;
    @Slider(name="Minimum network level",min=1,max=250,step=1,category="Reviews",subcategory="Account history") public float minLevel=80;
    @Switch(name="Recent-account reviews",category="Reviews",subcategory="Account history") public boolean recentAccount=true;
    @Slider(name="Recent account (days)",min=1,max=365,step=1,category="Reviews",subcategory="Account history") public float accountDays=90;
    @Info(type=InfoType.INFO,size=2,text="Statistics suggest review. They do not establish cheating or competitive-ban status.",category="Reviews",subcategory="Statistics") public boolean heuristicInfo;
    @Switch(name="Ratio reviews",category="Reviews",subcategory="Statistics") public boolean ratios=true;
    @Slider(name="FKD at 25 games or fewer",min=1,max=30,step=0,category="Reviews",subcategory="Statistics") public float fkd25=3.5f;
    @Slider(name="FKD at 250 games or fewer",min=1,max=30,step=0,category="Reviews",subcategory="Statistics") public float fkd250=5;
    @Slider(name="FKD at 500 games or fewer",min=1,max=30,step=0,category="Reviews",subcategory="Statistics") public float fkd500=8;
    @Slider(name="FKD multiplied by W/L",min=1,max=50,step=0,category="Reviews",subcategory="Statistics") public float product=4.95f;
    @Switch(name="Upgraded-class reviews",description="Original experience/quest gates retained. Zero-game advanced kits and fully upgraded classes are reviewed; free starters excluded.",category="Reviews",subcategory="Progression") public boolean kits=true;
    @Slider(name="Upgraded-class game cutoff",min=1,max=100,step=1,category="Reviews",subcategory="Progression") public float kitGames=15;
    @Switch(name="Legendary achievement reviews",category="Reviews",subcategory="Progression") public boolean skins=true;
    @Switch(name="Local activity-gap reviews",description="Only locally witnessed MW sightings. Outside matches and ban status remain unknown.",category="Reviews",subcategory="Activity evidence") public boolean activity=true;
    @Dropdown(name="Gap threshold type",options={"Calendar months","Days"},category="Reviews",subcategory="Activity evidence") public int gapType=0;
    @Slider(name="Activity gap (months)",min=1,max=12,step=1,category="Reviews",subcategory="Activity evidence") public float gapMonths=3;
    @Slider(name="Activity gap (days)",min=1,max=365,step=1,category="Reviews",subcategory="Activity evidence") public float gapDays=90;
    @Button(name="Apply review rules",text="Reassess roster",description="Preview the changed reasons in the review queue. Sends no moderation commands.",category="Reviews",subcategory="Apply") public Runnable reassess=()->PartyMod.runtime.reassess();
    @Info(type=InfoType.INFO,size=2,text="A confirmed party kick queues /block add. Voluntary departures never block a player.",category="Actions",subcategory="Kick behaviour") public boolean kickInfo;
    @Switch(name="Block after confirmed kicks",category="Actions",subcategory="Kick behaviour") public boolean autoBlock=true;
    @Switch(name="Confirm manual moderation",category="Actions",subcategory="Moderation") public boolean confirmActions=true;
    @Slider(name="Command spacing (milliseconds)",min=750,max=3000,step=50,category="Actions",subcategory="Moderation") public float commandSpacing=1250;
    @Switch(name="Show notifications",category="Notifications",subcategory="Presentation") public boolean notifications=true;
    @Switch(name="Quiet during Mega Walls matches",category="Notifications",subcategory="Presentation") public boolean quietMatch=true;
    @HUD(name="Party overview",category="HUD",subcategory="Overview") public PartyHud hud=new PartyHud();
    @Info(type=InfoType.INFO,size=2,text="Move, scale and style the HUD using OneConfig's HUD editor. Individual fields are in the HUD settings.",category="HUD",subcategory="Overview") public boolean hudInfo;
    @Button(name="HUD editor",text="Edit HUD",category="HUD",subcategory="Overview") public Runnable editHud=this::openHudEditor;
    @Dropdown(name="Stats source",options={"Direct Hypixel API","Application service"},category="Data",subcategory="Stats setup") public int statsSource=0;
    @Text(name="Hypixel API key",secure=true,description="Direct mode only. Saved locally in your OneConfig profile; masking does not encrypt the file.",category="Data",subcategory="Stats setup") public String hypixelApiKey="";
    @Button(name="Apply stats setup",text="Check roster stats",category="Data",subcategory="Stats setup") public Runnable applyStats=()->PartyMod.runtime.assessAll();
    @Switch(name="Automatically kick new Hypixel accounts",description="New joins only, after a valid firstLogin check. Requires a fresh roster and removal authority.",category="Actions",subcategory="New-account screening") public boolean autoKickNewAccounts=true;
    @Slider(name="Minimum Hypixel account age (days)",min=1,max=365,step=1,category="Actions",subcategory="New-account screening") public float minimumAccountDays=90;
    @Info(type=InfoType.INFO,size=2,text="Application service is optional. Its access token is separate from a Hypixel API key.",category="Data",subcategory="Application service") public boolean dataInfo;
    @Text(name="Service URL",description="HTTPS endpoint, or localhost for your own development service.",category="Data",subcategory="Application service") public String serviceUrl="";
    @Text(name="Service access token",secure=true,description="An application token, never a Hypixel key. Stored locally in your OneConfig profile.",category="Data",subcategory="Application service") public String serviceToken="";
    @Slider(name="Client requests per minute",min=1,max=60,step=1,category="Data",subcategory="Request handling") public float requestBudget=30;
    @Slider(name="Cached results (minutes)",min=15,max=1440,step=15,category="Data",subcategory="Request handling") public float cacheMinutes=120;
    @Button(name="Retry unavailable members",text="Retry assessments",category="Data",subcategory="Request handling") public Runnable retry=()->PartyMod.runtime.assessAll();
    @Info(type=InfoType.INFO,size=2,text="Current membership is never restored from disk. Notes, host blocklist and attendance stay on this computer.",category="Data",subcategory="Local records") public boolean localInfo;
    @Button(name="Save local records",text="Save now",category="Data",subcategory="Local records") public Runnable saveLocal=()->PartyMod.runtime.store.flush(true);
    public PartyConfig(){super(new Mod("PartyMod",ModType.UTIL_QOL,"/assets/partymod/icon.svg"),"partymod.json",true);initialize();registerKeyBind(openKey,PartyMod::openRoster);registerKeyBind(toggleKey,()->{enabled=!enabled;save();PartyMod.runtime.applyEnabled();});addListener("enabled",()->PartyMod.runtime.applyEnabled());hideIf("gapMonths",()->gapType!=0);hideIf("gapDays",()->gapType!=1);hideIf("minGames",()->!lowExperience);hideIf("minLevel",()->!lowExperience);hideIf("accountDays",()->!recentAccount);hideIf("hypixelApiKey",()->statsSource!=0);hideIf("serviceUrl",()->statsSource!=1);hideIf("serviceToken",()->statsSource!=1);hideIf("minimumAccountDays",()->!autoKickNewAccounts);}
    // V0 exposes the editor through its own sidebar, but omits it from the API JAR.
    // Keep this compatibility bridge isolated and fall back safely on other versions.
    private void openHudEditor(){try{Object editor=Class.forName("cc.polyfrost.oneconfig.internal.gui.HudGui").getDeclaredConstructor().newInstance();cc.polyfrost.oneconfig.utils.gui.GuiUtils.displayScreen(editor);}catch(ReflectiveOperationException|LinkageError ex){openGui();PartyRuntime.chat("Use OneConfig's Edit HUD sidebar button on this OneConfig version.");}}
    public ReviewRules.Options rules(){ReviewRules.Options o=new ReviewRules.Options();o.lowExperience=lowExperience;o.recentAccount=recentAccount;o.ratios=ratios;o.kits=kits;o.skins=skins;o.activity=activity;o.minGames=(int)minGames;o.minLevel=minLevel;o.accountDays=(int)accountDays;o.fkd25=fkd25;o.fkd250=fkd250;o.fkd500=fkd500;o.product=product;o.kitGames=(int)kitGames;o.calendarMonths=gapType==0;o.activityMonths=(int)gapMonths;o.activityDays=(int)gapDays;return o;}
}


