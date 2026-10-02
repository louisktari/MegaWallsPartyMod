package fr.alexdoru.partymod;

import fr.alexdoru.partymod.core.ChatEvents;
import fr.alexdoru.partymod.core.CommandQueue;
import fr.alexdoru.partymod.core.Flagger;
import fr.alexdoru.partymod.core.MatchDetector;
import fr.alexdoru.partymod.core.PartyTracker;
import fr.alexdoru.partymod.core.PartyTracker.Member;
import fr.alexdoru.partymod.core.PartyTracker.Status;
import fr.alexdoru.partymod.core.StatFormat;
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
    private boolean inMatch, onboarded;
    private long queueBypassUntil;
    private int ticks;

    private static final String PREFIX = EnumChatFormatting.DARK_PURPLE + "[" + EnumChatFormatting.LIGHT_PURPLE + "MWP"
            + EnumChatFormatting.DARK_PURPLE + "] " + EnumChatFormatting.GRAY;

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
        party.log(EnumChatFormatting.GREEN + "Imported lists: +" + r.trustedAdded + " trusted, +" + r.blockedAdded + " blocked");
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
            inMatch = false;
        });
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active()) return;
        commands.tick(System.currentTimeMillis(), (long) PartyMod.config.commandSpacing,
                command -> Minecraft.getMinecraft().thePlayer.sendChatMessage(command));
        if (++ticks % 20 == 0) inMatch = detectMatch();
        if (!onboarded && ticks > 100) {
            onboarded = true;
            if (keyStatus() == KeyStatus.MISSING) {
                IChatComponent line = new ChatComponentText(PREFIX + "Set your Hypixel API key to start stat-checking party members. ");
                line.appendSibling(button("[Open settings]", EnumChatFormatting.LIGHT_PURPLE, "/mwp settings", "Opens Getting started"));
                chat(line);
            }
        }
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
        switch (e.type) {
            case COMPETITIVE_BLOCK:
                party.log(EnumChatFormatting.RED + e.value + EnumChatFormatting.GRAY + " cannot play competitive games");
                if (PartyMod.config.autoCompetitive) remove(e.value, "Cannot play competitive games", true, true);
                break;
            case JOIN:
                onJoin(e.value, true);
                break;
            case SELF_JOIN:
                party.clearMembers();
                party.log("You joined " + e.value + "'s party");
                commands.add("/p list");
                break;
            case MEMBER_LIST:
                for (String name : ChatEvents.names(e.value)) onJoin(name, false);
                break;
            case LEAVE:
            case REMOVED:
                if (isSelf(e.value)) {
                    endParty("You left the party");
                } else {
                    Member gone = party.remove(e.value);
                    if (gone != null) party.log(StatFormat.rankedName(gone.stats, gone.name) + EnumChatFormatting.GRAY
                            + (e.type == ChatEvents.Type.LEAVE ? " left" : " was removed"));
                }
                break;
            case DISBAND:
                endParty("Party ended");
                break;
            case THROTTLED:
                commands.throttled(System.currentTimeMillis(), 3000);
                party.log(EnumChatFormatting.YELLOW + "Hypixel throttled commands; retrying in 3s");
                break;
            default:
                break;
        }
    }

    private void endParty(String reason) {
        if (party.members().isEmpty()) return;
        party.clearMembers();
        party.log(reason);
    }

    private static String selfName() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.thePlayer == null ? "" : mc.thePlayer.getName();
    }

    private boolean isSelf(String name) {
        return selfName().equalsIgnoreCase(name);
    }

    private void onJoin(String name, boolean live) {
        if (isSelf(name)) return;
        Member m = party.add(name, live);
        if (m == null) return;
        if (live) party.log(EnumChatFormatting.WHITE + name + EnumChatFormatting.GRAY + " joined");
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
        m.status = trusted.isTrusted(m.name) ? Status.TRUSTED : Status.CHECKING;
        if (m.status == Status.TRUSTED) m.setFlags(new ArrayList<>());
        api.lookup(m.name, tabUuid(m.name), PartyMod.config.hypixelApiKey, (int) PartyMod.config.cacheMinutes,
                (int) PartyMod.config.requestsPerMinute,
                result -> Minecraft.getMinecraft().addScheduledTask(() -> onResult(m, result)));
    }

    private void onResult(Member m, HypixelClient.Result result) {
        noteKeyStatus(result);
        if (party.get(m.name) != m) return; // left or party changed meanwhile
        m.stats = result.stats;
        if (result.stats == null && m.status != Status.TRUSTED) {
            m.status = Status.UNAVAILABLE;
            m.error = result.error;
            party.log(EnumChatFormatting.YELLOW + "Could not check " + m.name + ": " + result.error);
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
        if (!announce) return;
        String summary = String.join(", ", m.reasons);
        party.log(StatFormat.rankedName(m.stats, m.name) + EnumChatFormatting.GRAY + " flagged: "
                + (m.strong() ? EnumChatFormatting.RED : EnumChatFormatting.GOLD) + summary);
        if (PartyMod.config.autoRemoveFlagged && m.joinedLive && m.strong()) {
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
        IChatComponent line = new ChatComponentText(PREFIX + StatFormat.rankedName(m.stats, m.name) + EnumChatFormatting.GRAY
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
        if (m != null) m.status = Status.REMOVED;
        history.record(name, reason, automatic ? "" : selfName(), System.currentTimeMillis());
        String shown = m != null ? StatFormat.rankedName(m.stats, m.name) : EnumChatFormatting.WHITE + name;
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
        Member m = party.get(name);
        if (m != null && kickPending) {
            m.status = Status.DISMISSED;
        }
        party.log(EnumChatFormatting.GREEN + "Undid removal of " + name + (kickPending ? "" : " - re-invited"));
    }

    public void dismiss(String name) {
        Member m = party.get(name);
        if (m == null || m.status != Status.FLAGGED) {
            chat(name + " is not waiting for review.");
            return;
        }
        m.status = Status.DISMISSED;
        party.log(name + " kept in party");
    }

    public void removeAllFlagged() {
        List<Member> queue = new ArrayList<>(party.toReview());
        for (Member m : queue) remove(m.name, String.join(", ", m.reasons), false, false);
    }

    public void trust(String name) {
        if (!ChatEvents.validName(name)) return;
        trusted.add(name);
        PartyMod.config.syncTrustedText(trusted.all());
        Member m = party.get(name);
        if (m != null && m.status != Status.REMOVED) {
            m.status = Status.TRUSTED;
            m.setFlags(new ArrayList<>());
        }
        party.log(EnumChatFormatting.AQUA + name + EnumChatFormatting.GRAY + " trusted - never flagged again");
    }

    public void untrust(String name) {
        if (!trusted.remove(name)) {
            chat(name + " is not on your trusted list.");
            return;
        }
        PartyMod.config.syncTrustedText(trusted.all());
        party.log(name + " removed from trusted list");
        if (party.get(name) != null) recheck(name);
    }

    public void unblock(String name) {
        if (!ChatEvents.validName(name)) return;
        commands.add("/block remove " + name);
        history.remove(name);
        party.log(EnumChatFormatting.GREEN + "Unblocked " + name);
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
        commands.add("/p list");
        party.log("Syncing party list");
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

    private static void chat(IChatComponent component) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(component);
    }
}
