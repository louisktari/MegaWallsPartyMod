package fr.alexdoru.partymod;

import fr.alexdoru.partymod.core.ChatEvents;
import fr.alexdoru.partymod.core.CommandQueue;
import fr.alexdoru.partymod.core.Flagger;
import fr.alexdoru.partymod.core.MatchDetector;
import fr.alexdoru.partymod.core.PartyTracker;
import fr.alexdoru.partymod.core.PartyTracker.Member;
import fr.alexdoru.partymod.core.PartyTracker.Status;
import fr.alexdoru.partymod.core.SessionStats;
import fr.alexdoru.partymod.core.StatFormat;
import fr.alexdoru.partymod.data.ApiBudget;
import fr.alexdoru.partymod.data.BlockHistory;
import fr.alexdoru.partymod.data.HypixelClient;
import fr.alexdoru.partymod.data.ListShare;
import fr.alexdoru.partymod.data.TrustedStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reacts to party chat, runs stat checks and sends block + kick commands. Client thread only. */
public final class PartyRuntime {

    public enum KeyStatus { UNKNOWN, OK, MISSING, REJECTED }

    public final PartyTracker party = new PartyTracker();
    public final TrustedStore trusted;
    public final BlockHistory history;
    private final CommandQueue commands = new CommandQueue();
    private final HypixelClient api = new HypixelClient(PartyMod.version());

    private KeyStatus keyStatus = KeyStatus.UNKNOWN;
    private boolean inMatch, inHousing, onboarded;
    /** Times of recent automatic removals; caps how fast the mod can act on its own. */
    private final java.util.ArrayDeque<Long> autoRemovals = new java.util.ArrayDeque<>();
    static final int AUTO_REMOVAL_LIMIT = 5;
    static final long AUTO_REMOVAL_WINDOW = 60_000L;
    /** Competitive-ban names waiting for /p list to confirm they're really in the party. */
    private final java.util.Set<String> pendingCompetitive = new java.util.HashSet<>();
    private long queueBypassUntil;
    private int ticks;

    /** Same branded style as EDITH: "MWP » message". */
    static final String PREFIX = "\u00a7dMWP \u00a77\u00bb \u00a77";

    /** Last colour-coded name seen per player, so the log can show ranks even after they leave. */
    private final java.util.Map<String, String> knownNames = new java.util.HashMap<>();
    /** Names seen during the current /p list, to drop members we missed leaving. */
    private java.util.Set<String> syncSeen;
    private int syncExpected;
    private String lastKick = "";
    /** Who leads the party (may be you); empty when unknown. */
    private String leader = "";
    /** True while you're in a party, even if nobody else has joined yet. */
    private boolean inParty;
    private int partyCap;
    /** Timer, joins, peaks and moderation counts for the current party. */
    public final SessionStats session = new SessionStats();
    /** Summary of the last party that ended, shown on the Party overview until a new one starts. */
    private List<String> lastSummary;

    // Auto-sync: a quiet /p list every few minutes and when the party fills up.
    private long lastSyncAt, syncStartedAt, quietUntil;
    private boolean quietPending, syncedAtCap;

    // ---------------------------------------------------------------- party session

    public boolean inParty() {
        return inParty;
    }

    /** Party size including you. */
    public int partySize() {
        return inParty ? party.members().size() + 1 : 0;
    }

    /** Max size from "Party is capped at N players.", or 0 if unknown. */
    public int partyCap() {
        return partyCap;
    }

    public long partyStartedAt() {
        return session.startedAt();
    }

    public boolean startApprox() {
        return session.approx();
    }

    public int totalJoins() {
        return session.joins();
    }

    /** Joins in the last minute. */
    public double joinRate() {
        return session.rate(System.currentTimeMillis());
    }

    /** Hypixel's remaining request budget, from the last API reply. */
    public ApiBudget apiBudget() {
        return api.budget;
    }

    /** Summary of the last party that ended, or null. */
    public List<String> lastSummary() {
        return lastSummary;
    }

    private void startParty(boolean approx) {
        if (inParty) return;
        inParty = true;
        session.start(System.currentTimeMillis(), approx);
        lastSummary = null;
        lastSyncAt = System.currentTimeMillis();
        syncedAtCap = false;
    }
    private long lastKickAt;

    public PartyRuntime(File dataDir) {
        this.dataDir = dataDir;
        trusted = new TrustedStore(new File(dataDir, "trusted.json"));
        history = new BlockHistory(new File(dataDir, "blocked-history.json"));
    }

    // ---------------------------------------------------------------- sharing lists

    private final File dataDir;

    private String exportText() {
        return ListShare.export(trusted, history, selfName(), System.currentTimeMillis());
    }

    /** Writes an export file into the mod folder and offers to open it. */
    public void exportToFile() {
        String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.ROOT).format(new java.util.Date());
        File file = new File(dataDir, "export-" + stamp + ".json");
        try {
            java.nio.file.Files.createDirectories(dataDir.toPath());
            java.nio.file.Files.write(file.toPath(), exportText().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException | RuntimeException e) {
            chat(EnumChatFormatting.RED + "Could not write the export: " + e.getMessage());
            return;
        }
        IChatComponent line = new ChatComponentText(PREFIX + "Exported " + trusted.all().size() + " trusted and "
                + history.size() + " blocked players to " + EnumChatFormatting.WHITE + file.getName() + " ");
        IChatComponent open = new ChatComponentText("[Open folder]");
        open.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.LIGHT_PURPLE).setBold(true)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, dataDir.getAbsolutePath()))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(dataDir.getAbsolutePath()))));
        line.appendSibling(open);
        chat(line);
        chat("Send that file to a co-host; they drop it in " + EnumChatFormatting.WHITE + "config/megawallspartymod/"
                + EnumChatFormatting.GRAY + " and press Import latest file.");
    }

    /** Copies the export to the clipboard, ready to paste into Discord. */
    public void exportToClipboard() {
        net.minecraft.client.gui.GuiScreen.setClipboardString(exportText());
        chat("Copied " + trusted.all().size() + " trusted and " + history.size()
                + " blocked players to your clipboard. A co-host copies it and presses Import from clipboard.");
    }

    public void importFromClipboard() {
        report(ListShare.importInto(net.minecraft.client.gui.GuiScreen.getClipboardString(), trusted, history), "clipboard");
    }

    /**
     * Imports a file from the mod folder.
     *
     * @param fileName a file name inside config/megawallspartymod, or empty for the newest export-*.json
     */
    public void importFromFile(String fileName) {
        File file;
        if (fileName == null || fileName.isEmpty()) {
            File[] exports = dataDir.listFiles((dir, n) -> n.startsWith("export-") && n.endsWith(".json"));
            if (exports == null || exports.length == 0) {
                chat("No export-*.json files in config/megawallspartymod/ yet. Drop a co-host's export there first.");
                return;
            }
            file = exports[0];
            for (File f : exports) if (f.lastModified() > file.lastModified()) file = f;
        } else if (!fileName.matches("[A-Za-z0-9._ -]{1,80}\\.json")) {
            chat("Give a .json file name from config/megawallspartymod/.");
            return;
        } else {
            file = new File(dataDir, fileName);
        }
        if (!file.isFile()) {
            chat("Can't find " + file.getName() + " in config/megawallspartymod/.");
            return;
        }
        try {
            if (file.length() > 1_000_000) throw new java.io.IOException("file is too large");
            String text = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            report(ListShare.importInto(text, trusted, history), file.getName());
        } catch (java.io.IOException e) {
            chat(EnumChatFormatting.RED + "Could not read " + file.getName() + ": " + e.getMessage());
        }
    }

    private void report(ListShare.Result r, String source) {
        if (!r.ok()) {
            chat(EnumChatFormatting.RED + r.error + ".");
            return;
        }
        PartyMod.config.syncTrustedText(trusted.all());
        reassessAll();
        chat(EnumChatFormatting.GREEN + "Imported from " + source + ": " + r.trustedAdded + " trusted, " + r.blockedAdded
                + " blocked" + (r.skipped > 0 ? EnumChatFormatting.GRAY + " (" + r.skipped + " invalid entries skipped)" : "")
                + EnumChatFormatting.GREEN + ". Existing entries were kept.");
        party.logSystem("Imported lists: +" + r.trustedAdded + " trusted, +" + r.blockedAdded + " blocked");
    }

    // ---------------------------------------------------------------- lifecycle

    public boolean active() {
        Minecraft mc = Minecraft.getMinecraft();
        return PartyMod.config != null && PartyMod.config.enabled && mc.thePlayer != null
                && mc.getCurrentServerData() != null && ChatEvents.isHypixel(mc.getCurrentServerData().serverIP);
    }

    public boolean inMatch() {
        return inMatch;
    }

    public KeyStatus keyStatus() {
        if (PartyMod.config != null && PartyMod.config.hypixelApiKey.trim().isEmpty()) return KeyStatus.MISSING;
        return keyStatus;
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            commands.clear();
            party.clearMembers();
            inParty = false;
            partyCap = 0;
            inMatch = false;
        });
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active()) return;
        commands.tick(System.currentTimeMillis(), (long) PartyMod.config.commandSpacing,
                command -> {
                    if (command.startsWith("/p kick ")) {
                        lastKick = command.substring(8);
                        lastKickAt = System.currentTimeMillis();
                    }
                    if (command.equals("/p list") && quietPending) {
                        quietPending = false;
                        quietUntil = System.currentTimeMillis() + 3000L;
                    }
                    Minecraft.getMinecraft().thePlayer.sendChatMessage(command);
                });
        if (++ticks % 20 == 0) {
            inMatch = detectMatch();
            boolean housing = detectHousing();
            if (housing && !inHousing) party.logSystem("In Housing - automatic removals paused (house scripts can fake chat)");
            inHousing = housing;
            dropStuckRemovals();
            autoSyncTick();
        }
        if (!onboarded && ticks > 100) {
            onboarded = true;
            if (keyStatus() == KeyStatus.MISSING) {
                IChatComponent line = new ChatComponentText(PREFIX + "Set your Hypixel API key to start stat-checking party members. ");
                line.appendSibling(button("[Open settings]", EnumChatFormatting.LIGHT_PURPLE, "/mwp settings", "Opens Getting started"));
                chat(line);
            }
        }
    }

    /** Keeps the member list honest without the host pressing Sync. */
    private void autoSyncTick() {
        long now = System.currentTimeMillis();
        if (syncSeen != null && now - syncStartedAt > 10_000L) syncSeen = null; // a /p list that never finished
        if (!inParty || inMatch || PartyMod.config == null || !PartyMod.config.autoSync) return;
        int size = partySize();
        if (partyCap > 0 && size >= partyCap) {
            if (!syncedAtCap) {
                syncedAtCap = true;
                quietSync();
                return;
            }
        } else {
            syncedAtCap = false;
        }
        if (commands.size() == 0 && now - lastSyncAt >= (long) (PartyMod.config.autoSyncMinutes * 60_000L)) quietSync();
    }

    /** Sends /p list and hides its output from chat; only changes it finds are logged. */
    private void quietSync() {
        if (syncSeen != null) return;
        lastSyncAt = System.currentTimeMillis();
        quietPending = true;
        commands.add("/p list");
    }

    private boolean detectHousing() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return false;
        ScoreObjective objective = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
        return objective != null && MatchDetector.inHousing(EnumChatFormatting.getTextWithoutFormattingCodes(objective.getDisplayName()));
    }

    /**
     * Gate for anything the mod does without a click. Refuses in Housing, for you or trusted
     * players, for anyone not in your party, and beyond {@value #AUTO_REMOVAL_LIMIT} a minute.
     */
    private boolean mayAutoRemove(String name, String why) {
        String reason = null;
        if (isSelf(name) || trusted.isTrusted(name)) reason = "trusted";
        else if (inHousing) reason = "in Housing";
        else if (party.get(name) == null) reason = "not in your party";
        else {
            long now = System.currentTimeMillis();
            while (!autoRemovals.isEmpty() && now - autoRemovals.peekFirst() > AUTO_REMOVAL_WINDOW) autoRemovals.pollFirst();
            if (autoRemovals.size() >= AUTO_REMOVAL_LIMIT) reason = "too many automatic removals this minute";
            else autoRemovals.addLast(now);
        }
        if (reason == null) return true;
        party.logSystem("Did not auto-remove " + nameOf(name) + EnumChatFormatting.GRAY + " (" + why + ") - " + reason);
        return false;
    }

    private boolean detectMatch() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return false;
        Scoreboard board = mc.theWorld.getScoreboard();
        ScoreObjective objective = board.getObjectiveInDisplaySlot(1);
        if (objective == null) return false;
        List<String> lines = new ArrayList<>();
        for (Score score : board.getSortedScores(objective)) {
            lines.add(EnumChatFormatting.getTextWithoutFormattingCodes(
                    ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(score.getPlayerName()), score.getPlayerName())));
        }
        return MatchDetector.inMegaWallsMatch(EnumChatFormatting.getTextWithoutFormattingCodes(objective.getDisplayName()), lines);
    }

    public void shutdown() {
        api.shutdown();
    }

    // ---------------------------------------------------------------- chat

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (event.type == 2 || !active()) return; // ignore action bar
        ChatEvents.Event e = ChatEvents.parse(event.message.getUnformattedText());
        if (System.currentTimeMillis() < quietUntil && (e.type == ChatEvents.Type.MEMBER_COUNT
                || e.type == ChatEvents.Type.MEMBER_LIST || ChatEvents.isListFiller(event.message.getUnformattedText()))) {
            event.setCanceled(true); // output of an automatic /p list
        }
        switch (e.type) {
            case COMPETITIVE_BLOCK:
                remember(e.value, ChatEvents.formattedName(event.message.getFormattedText(), e.value));
                party.log(nameOf(e.value) + EnumChatFormatting.RED + " cannot play competitive games");
                if (PartyMod.config.autoCompetitive) {
                    if (party.get(e.value) == null && !isSelf(e.value) && !trusted.isTrusted(e.value) && !inHousing) {
                        // Not tracked (maybe joined before we were watching): confirm with Hypixel first.
                        pendingCompetitive.add(e.value.toLowerCase(java.util.Locale.ROOT));
                        commands.addUrgent("/p list");
                        party.logSystem("Confirming " + nameOf(e.value) + EnumChatFormatting.GRAY + " is in your party before removing");
                    } else if (mayAutoRemove(e.value, "competitive ban")) {
                        remove(e.value, "Cannot play competitive games", true, true);
                    }
                }
                break;
            case JOIN:
                onJoin(e.value, true, ChatEvents.formattedName(event.message.getFormattedText(), e.value));
                break;
            case SELF_JOIN:
                party.clearMembers();
                inParty = false;
                startParty(true);
                leader = e.value;
                party.logSystem("You joined " + nameOf(e.value) + EnumChatFormatting.GRAY + "'s party");
                commands.add("/p list");
                break;
            case PARTY_CREATED:
                // /stream open: a brand-new party starts the timer; an existing one just goes public.
                if (inParty) {
                    party.logSystem("Party is now public");
                } else {
                    startParty(false);
                    party.logSystem("Public party started");
                }
                leader = e.value;
                break;
            case PARTY_CAP:
                partyCap = Integer.parseInt(e.value);
                startParty(true);
                break;
            case MEMBER_COUNT:
                startParty(true);
                syncStartedAt = System.currentTimeMillis();
                lastSyncAt = syncStartedAt;
                syncSeen = new java.util.HashSet<>();
                syncExpected = Integer.parseInt(e.value);
                break;
            case MEMBER_LIST: {
                String formatted = event.message.getFormattedText();
                for (String name : ChatEvents.names(e.value)) {
                    if (e.role == ChatEvents.Role.LEADER) leader = name;
                    if (isSelf(name)) remember(name, ChatEvents.formattedName(formatted, name));
                    onJoin(name, false, ChatEvents.formattedName(formatted, name));
                    Member listed = party.get(name);
                    if (listed != null) listed.role = e.role;
                    if (listed != null && pendingCompetitive.remove(name.toLowerCase(java.util.Locale.ROOT))
                            && mayAutoRemove(name, "competitive ban")) {
                        remove(name, "Cannot play competitive games", true, true);
                    }
                    if (syncSeen != null) syncSeen.add(name.toLowerCase(java.util.Locale.ROOT));
                }
                if (syncSeen != null && syncSeen.size() >= syncExpected) finishSync();
                break;
            }
            case ROLE_CHANGE: {
                if (e.role == ChatEvents.Role.LEADER) {
                    Member old = party.get(leader);
                    if (old != null) old.role = ChatEvents.Role.MEMBER;
                    leader = e.value;
                }
                Member changed = party.get(e.value);
                if (changed != null) changed.role = e.role;
                String what = e.role == ChatEvents.Role.LEADER ? EnumChatFormatting.GOLD + "is now Party Leader"
                        : e.role == ChatEvents.Role.MODERATOR ? EnumChatFormatting.DARK_GREEN + "promoted to Moderator"
                        : EnumChatFormatting.GRAY + "demoted to Member";
                party.log(nameOf(e.value) + " " + what);
                break;
            }
            case NOT_IN_PARTY:
                // Our /p kick hit someone who had already gone: stop showing them as "removing".
                if (!lastKick.isEmpty() && System.currentTimeMillis() - lastKickAt < 5000 && party.remove(lastKick) != null) {
                    party.log(nameOf(lastKick) + EnumChatFormatting.GRAY + " had already left the party");
                }
                lastKick = "";
                break;
            case LEAVE:
            case REMOVED:
                if (isSelf(e.value)) {
                    endParty("You left the party");
                } else {
                    Member gone = party.remove(e.value);
                    if (gone != null && (e.type == ChatEvents.Type.LEAVE || gone.status != Status.REMOVED)) session.leave();
                    if (gone != null) party.log(gone.shown() + " " + (e.type == ChatEvents.Type.LEAVE
                            ? EnumChatFormatting.RED + "left" : EnumChatFormatting.RED + "was removed"));
                }
                break;
            case DISBAND:
                endParty("Party ended");
                break;
            case THROTTLED:
                commands.throttled(System.currentTimeMillis(), 3000);
                party.logSystem("Hypixel throttled commands; retrying in 3s");
                break;
            default:
                break;
        }
    }

    /** Kicks that never got a confirmation (e.g. they'd already gone) shouldn't linger as "removing". */
    private void dropStuckRemovals() {
        long now = System.currentTimeMillis();
        List<Member> stuck = new ArrayList<>();
        for (Member m : party.members()) {
            if (m.status == Status.REMOVED && m.removedAt > 0 && now - m.removedAt > 15_000L && commands.size() == 0) stuck.add(m);
        }
        for (Member m : stuck) party.remove(m.name);
    }

    /** After a full /p list, drop anyone we still track who isn't actually in the party. */
    private void finishSync() {
        List<Member> gone = new ArrayList<>();
        for (Member m : party.members()) if (!syncSeen.contains(m.name.toLowerCase(java.util.Locale.ROOT))) gone.add(m);
        for (Member m : gone) {
            party.remove(m.name);
            party.log(m.shown() + EnumChatFormatting.GRAY + " is no longer in the party");
        }
        for (String name : pendingCompetitive) {
            party.logSystem("Ignored competitive-ban message for " + nameOf(name) + EnumChatFormatting.GRAY + " - not in your party");
        }
        pendingCompetitive.clear();
        syncSeen = null;
    }

    private void remember(String name, String shown) {
        if (name != null && shown != null && !shown.isEmpty()) knownNames.put(name.toLowerCase(java.util.Locale.ROOT), shown);
    }

    /** Rank-coloured name for the log, even for players who have left. */
    public String nameOf(String name) {
        Member m = party.get(name);
        if (m != null) return m.shown();
        String known = knownNames.get(name.toLowerCase(java.util.Locale.ROOT));
        return known != null ? known : EnumChatFormatting.GRAY + name;
    }

    private void endParty(String reason) {
        boolean was = inParty || !party.members().isEmpty();
        long now = System.currentTimeMillis();
        boolean summarised = inParty && session.eventful(now);
        if (summarised) {
            lastSummary = session.summary(now, partyCap);
            postSummary(lastSummary);
        }
        leader = "";
        inParty = false;
        partyCap = 0;
        quietPending = false;
        syncSeen = null;
        party.clearMembers();
        if (was) party.logSystem(reason);
        if (summarised) party.logSystem("Party summary: " + EnumChatFormatting.getTextWithoutFormattingCodes(
                String.join(" | ", lastSummary)));
    }

    /** EDITH-style card in chat when a party ends. */
    private static void postSummary(List<String> lines) {
        chatRaw(PartyCommand.DIVIDER);
        chat(EnumChatFormatting.LIGHT_PURPLE + "Party summary");
        for (String line : lines) chatRaw("  " + line);
        chatRaw(PartyCommand.DIVIDER);
    }

    private static String selfName() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer == null ? "" : mc.thePlayer.getName();
    }

    public boolean isSelf(String name) {
        return selfName().equalsIgnoreCase(name);
    }

    /** 1-based place in the stat-check queue (1 = being checked now), or 0. */
    public int queuePosition(String name) {
        return api.queuePosition(name);
    }

    public long queueWaitMs(int position) {
        return api.queueWaitMs(position);
    }

    public int queueSize() {
        return api.queueSize();
    }

    /** Current party leader's name, or "" until a /p list or role message has told us. */
    public String leader() {
        return leader;
    }

    private void onJoin(String name, boolean live, String display) {
        if (isSelf(name)) return;
        startParty(!live);
        Member m = party.add(name, live);
        if (m == null) return;
        if (live) session.join(System.currentTimeMillis());
        session.size(partySize());
        if (display != null) m.display = display;
        remember(name, display);
        if (live) party.log(m.shown() + " " + EnumChatFormatting.GREEN + "joined");
        check(m);
    }

    // ---------------------------------------------------------------- stat checks

    /** The player's v4 UUID from the tab list, if they are in this lobby; saves a Mojang call. */
    private static String tabUuid(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        NetworkPlayerInfo info = mc.getNetHandler() == null ? null : mc.getNetHandler().getPlayerInfo(name);
        UUID id = info == null || info.getGameProfile() == null ? null : info.getGameProfile().getId();
        return id != null && id.version() == 4 ? id.toString().replace("-", "") : null;
    }

    private void check(Member m) {
        m.retryAt = 0;
        m.status = trusted.isTrusted(m.name) ? Status.TRUSTED : Status.CHECKING;
        if (m.status == Status.TRUSTED) m.setFlags(new ArrayList<>());
        api.lookup(m.name, tabUuid(m.name), PartyMod.config.hypixelApiKey, (int) PartyMod.config.cacheMinutes,
                (int) PartyMod.config.requestsPerMinute, () -> party.isPresent(m.name),
                result -> Minecraft.getMinecraft().addScheduledTask(() -> onResult(m.name, result)));
    }

    private void onResult(String name, HypixelClient.Result result) {
        // Resolve by name: if they left and rejoined while queued, the result belongs to the new card.
        Member m = party.get(name);
        if (m == null) return; // left meanwhile
        if (result.retrying) {
            if (m.status != Status.TRUSTED) m.status = Status.CHECKING;
            if (m.retryAt == 0) party.log(EnumChatFormatting.YELLOW + "Rate limited checking " + m.shown()
                    + EnumChatFormatting.YELLOW + " - will retry automatically");
            m.retryAt = result.retryAt;
            return;
        }
        m.retryAt = 0;
        noteKeyStatus(result);
        m.stats = result.stats;
        if (result.stats != null) remember(m.name, m.shown());
        if (result.stats == null && m.status != Status.TRUSTED) {
            m.status = Status.UNAVAILABLE;
            m.error = result.error;
            party.log(EnumChatFormatting.YELLOW + "Could not check " + m.shown() + EnumChatFormatting.YELLOW + ": " + result.error);
            return;
        }
        evaluate(m, true);
    }

    /** Applies the flag rules to a member who has stats. */
    private void evaluate(Member m, boolean announce) {
        if (m.status == Status.REMOVED) return;
        if (trusted.isTrusted(m.name)) {
            m.status = Status.TRUSTED;
            m.setFlags(new ArrayList<>());
            return;
        }
        if (m.stats == null) return;
        List<Flagger.Flag> flags = Flagger.assess(m.stats, PartyMod.config.flagOptions(), System.currentTimeMillis());
        BlockHistory.Entry before = history.get(m.name);
        if (before != null && PartyMod.config.flagPreviouslyRemoved) {
            flags.add(0, new Flagger.Flag("Removed before (" + before.when(System.currentTimeMillis()) + ")", true));
        }
        m.setFlags(flags);
        if (flags.isEmpty()) {
            if (m.status != Status.DISMISSED) m.status = Status.CLEAN;
            return;
        }
        if (m.status == Status.DISMISSED && !announce) return;
        m.status = Status.FLAGGED;
        session.flagged(m.name);
        if (!announce) return;
        String summary = String.join(", ", m.reasons);
        party.log(m.shown() + EnumChatFormatting.GRAY + " flagged: "
                + (m.strong() ? EnumChatFormatting.RED : EnumChatFormatting.GOLD) + summary);
        if (PartyMod.config.autoRemoveFlagged && m.joinedLive && m.strong() && mayAutoRemove(m.name, "strong flag")) {
            remove(m.name, summary, true, false);
            return;
        }
        if (PartyMod.config.flagSound && Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.playSound("note.pling", 1.0F, m.strong() ? 0.6F : 0.9F);
        }
        if (PartyMod.config.chatPrompt) promptInChat(m, summary);
    }

    private void noteKeyStatus(HypixelClient.Result result) {
        if (result.stats != null) keyStatus = KeyStatus.OK;
        else if (result.error.contains("rejected")) keyStatus = KeyStatus.REJECTED;
        else if (result.error.contains("No Hypixel API key")) keyStatus = KeyStatus.MISSING;
    }

    /** Re-applies the rules after a settings change, without new API calls or chat spam. */
    public void reassessAll() {
        for (Member m : party.members()) evaluate(m, false);
    }

    private void promptInChat(Member m, String summary) {
        IChatComponent line = new ChatComponentText(PREFIX + m.shown() + EnumChatFormatting.GRAY
                + (m.strong() ? " should probably go: " + EnumChatFormatting.RED : " looks suspicious: " + EnumChatFormatting.GOLD)
                + summary + " ");
        line.appendSibling(button("[Block + Kick]", EnumChatFormatting.RED, "/mwp remove " + m.name,
                "Sends /block add " + m.name + " then /p kick " + m.name));
        line.appendSibling(new ChatComponentText(" "));
        line.appendSibling(button("[Keep]", EnumChatFormatting.GREEN, "/mwp dismiss " + m.name,
                "Keep " + m.name + " in the party this time"));
        line.appendSibling(new ChatComponentText(" "));
        line.appendSibling(button("[Trust]", EnumChatFormatting.AQUA, "/mwp trust " + m.name,
                "Always keep " + m.name + " - never flag them again"));
        chat(line);
    }

    private static IChatComponent button(String label, EnumChatFormatting color, String command, String hover) {
        IChatComponent c = new ChatComponentText(label);
        c.setChatStyle(new ChatStyle().setColor(color).setBold(true)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(hover))));
        return c;
    }

    public void recheckAll() {
        if (!active()) {
            chat("Join Hypixel first.");
            return;
        }
        for (Member m : party.members()) {
            api.forget(m.name);
            check(m);
        }
        chat("Re-checking " + party.members().size() + " party member(s).");
    }

    public void recheck(String name) {
        Member m = party.get(name);
        if (m == null) m = party.add(name, false);
        api.forget(name);
        check(m);
    }

    /** Looks the host up to prove the key works, and reports in chat. */
    public void testKey() {
        String self = selfName();
        if (self.isEmpty()) {
            chat("Join a world first, then press Test key.");
            return;
        }
        api.forget(self);
        chat("Testing your API key...");
        api.lookup(self, null, PartyMod.config.hypixelApiKey, 0, (int) PartyMod.config.requestsPerMinute,
                result -> Minecraft.getMinecraft().addScheduledTask(() -> {
                    if (result.retrying) {
                        chat(EnumChatFormatting.YELLOW + result.error);
                        return;
                    }
                    noteKeyStatus(result);
                    if (result.stats != null) chat(EnumChatFormatting.GREEN + "API key works - looked you up as "
                            + StatFormat.rankedName(result.stats, result.stats.name) + EnumChatFormatting.GREEN + ".");
                    else chat(EnumChatFormatting.RED + "API key problem: " + result.error);
                }));
    }

    public static void openUrl(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(new URI(url));
        } catch (Exception | LinkageError e) {
            chat("Open " + url + " in your browser.");
        }
    }

    // ---------------------------------------------------------------- actions

    /**
     * Sends /block add then /p kick for the player, in that order.
     *
     * @param reason    why (flag summary, or "" for a plain manual kick)
     * @param automatic true when the mod acted on its own rather than a host click
     * @param urgent    jump the queue (used for the competitive-ban queue refusal)
     */
    public void remove(String name, String reason, boolean automatic, boolean urgent) {
        if (!ChatEvents.validName(name) || isSelf(name)) return;
        String[] pair = {"/block add " + name, "/p kick " + name};
        if (urgent) commands.addUrgent(pair);
        else commands.add(pair);
        Member m = party.get(name);
        if (m != null) {
            m.status = Status.REMOVED;
            m.removedAt = System.currentTimeMillis();
        }
        history.record(name, reason, automatic ? "" : selfName(), System.currentTimeMillis());
        session.removed(name, automatic);
        String shown = nameOf(name);
        party.log(EnumChatFormatting.RED + "Removed " + shown + EnumChatFormatting.GRAY + " - " + describe(history.get(name)), name);
    }

    /** "No rank - Removed by §6Louis" / "Cannot play competitive games - Auto-removed". */
    public static String describe(BlockHistory.Entry e) {
        if (e == null) return "";
        String who = e.automatic() ? EnumChatFormatting.GRAY + "Auto-removed"
                : EnumChatFormatting.GRAY + "Removed by " + EnumChatFormatting.GOLD + e.by + EnumChatFormatting.GRAY;
        return e.reason.isEmpty() ? who : EnumChatFormatting.GRAY + e.reason + " - " + who;
    }

    /** Reverses a removal: cancels unsent commands, otherwise unblocks and re-invites. */
    public void undo(String name) {
        boolean blockPending = commands.cancel("/block add " + name);
        boolean kickPending = commands.cancel("/p kick " + name);
        if (!blockPending) commands.add("/block remove " + name);
        if (!kickPending) commands.add("/p invite " + name);
        history.remove(name);
        session.unremoved(name);
        Member m = party.get(name);
        if (m != null && kickPending) {
            m.status = Status.DISMISSED;
        }
        party.log(EnumChatFormatting.GREEN + "Undid removal of " + nameOf(name) + EnumChatFormatting.GREEN + (kickPending ? "" : " - re-invited"));
    }

    /** True when you lead the party (or we don't know yet, so the buttons stay available). */
    public boolean selfIsLeader() {
        return leader.isEmpty() || isSelf(leader);
    }

    public void promote(String name) {
        if (ChatEvents.validName(name)) commands.add("/p promote " + name);
    }

    public void demote(String name) {
        if (ChatEvents.validName(name)) commands.add("/p demote " + name);
    }

    public void dismiss(String name) {
        Member m = party.get(name);
        if (m == null || m.status != Status.FLAGGED) {
            chat(name + " is not waiting for review.");
            return;
        }
        m.status = Status.DISMISSED;
        session.kept(name);
        party.log(nameOf(name) + EnumChatFormatting.GREEN + " kept in party");
    }

    public void removeAllFlagged() {
        List<Member> queue = new ArrayList<>(party.toReview());
        for (Member m : queue) remove(m.name, String.join(", ", m.reasons), false, false);
    }

    public void trust(String name) {
        if (!ChatEvents.validName(name)) return;
        trusted.add(name);
        session.trusted(name);
        PartyMod.config.syncTrustedText(trusted.all());
        Member m = party.get(name);
        if (m != null && m.status != Status.REMOVED) {
            m.status = Status.TRUSTED;
            m.setFlags(new ArrayList<>());
        }
        party.log(nameOf(name) + EnumChatFormatting.AQUA + " trusted" + EnumChatFormatting.GRAY + " - never flagged again");
    }

    public void untrust(String name) {
        if (!trusted.remove(name)) {
            chat(name + " is not on your trusted list.");
            return;
        }
        PartyMod.config.syncTrustedText(trusted.all());
        party.log(nameOf(name) + EnumChatFormatting.GRAY + " removed from trusted list");
        if (party.get(name) != null) recheck(name);
    }

    public void unblock(String name) {
        if (!ChatEvents.validName(name)) return;
        commands.add("/block remove " + name);
        history.remove(name);
        party.log(EnumChatFormatting.GREEN + "Unblocked " + nameOf(name));
    }

    public void clearHistory() {
        int n = history.size();
        history.clear();
        chat("Forgot " + n + " removed player(s). They stay blocked on Hypixel.");
    }

    public void removeNext() {
        List<Member> queue = party.toReview();
        if (queue.isEmpty()) chat("Nobody to review.");
        else remove(queue.get(0).name, String.join(", ", queue.get(0).reasons), false, false);
    }

    public void dismissNext() {
        List<Member> queue = party.toReview();
        if (queue.isEmpty()) chat("Nobody to review.");
        else dismiss(queue.get(0).name);
    }

    public void trustNext() {
        List<Member> queue = party.toReview();
        if (queue.isEmpty()) chat("Nobody to review.");
        else trust(queue.get(0).name);
    }

    public void syncParty() {
        if (!active()) {
            chat("Join Hypixel first.");
            return;
        }
        quietPending = false;
        lastSyncAt = System.currentTimeMillis();
        commands.add("/p list");
        party.logSystem("Syncing party list");
    }

    // ---------------------------------------------------------------- queue guard

    /**
     * Called by the client-side /play command.
     *
     * @return true if the queue was let through
     */
    public boolean tryQueue(String args) {
        String mode = args.replaceAll("[^A-Za-z0-9_ ]", "").trim();
        List<Member> waiting = party.toReview();
        if (!PartyMod.config.queueGuard || waiting.isEmpty() || System.currentTimeMillis() < queueBypassUntil) {
            sendNow("/play " + mode);
            return true;
        }
        IChatComponent line = new ChatComponentText(PREFIX + EnumChatFormatting.GOLD + waiting.size()
                + " player(s) still to review. ");
        line.appendSibling(button("[Kick them + queue]", EnumChatFormatting.RED, "/mwp queue kick " + mode,
                "Block + kick everyone waiting for review, then /play " + mode));
        line.appendSibling(new ChatComponentText(" "));
        line.appendSibling(button("[Queue anyway]", EnumChatFormatting.GREEN, "/mwp queue force " + mode,
                "/play " + mode + " with them still in the party"));
        chat(line);
        return false;
    }

    public void queueAfterKicks(String mode) {
        removeAllFlagged();
        commands.add("/play " + mode.replaceAll("[^A-Za-z0-9_ ]", "").trim());
    }

    public void queueAnyway(String mode) {
        queueBypassUntil = System.currentTimeMillis() + 30_000L;
        sendNow("/play " + mode.replaceAll("[^A-Za-z0-9_ ]", "").trim());
    }

    private static void sendNow(String command) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) mc.thePlayer.sendChatMessage(command.trim());
    }

    // ---------------------------------------------------------------- output

    public static void chat(String text) {
        chat(new ChatComponentText(PREFIX + text));
    }

    /** Unprefixed line (dividers). */
    public static void chatRaw(String text) {
        chat(new ChatComponentText(text));
    }

    /** Prefixed component (clickable help rows). */
    public static void chatComponent(IChatComponent component) {
        IChatComponent line = new ChatComponentText(PREFIX);
        line.appendSibling(component);
        chat(line);
    }

    private static void chat(IChatComponent component) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(component);
    }
}
