package fr.alexdoru.partymod;

import fr.alexdoru.partymod.core.ChatEvents;
import fr.alexdoru.partymod.core.CommandQueue;
import fr.alexdoru.partymod.core.Flagger;
import fr.alexdoru.partymod.core.PartyTracker;
import fr.alexdoru.partymod.core.PartyTracker.Member;
import fr.alexdoru.partymod.core.PartyTracker.Status;
import fr.alexdoru.partymod.core.StatFormat;
import fr.alexdoru.partymod.data.HypixelClient;
import fr.alexdoru.partymod.data.TrustedStore;
import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Reacts to party chat, runs stat checks and sends block + kick commands. Client thread only. */
public final class PartyRuntime {
    public final PartyTracker party = new PartyTracker();
    private final CommandQueue commands = new CommandQueue();
    private final HypixelClient api = new HypixelClient(PartyMod.version());
    public final TrustedStore trusted;

    public PartyRuntime(File trustedFile) {
        trusted = new TrustedStore(trustedFile);
    }

    private static final String PREFIX = EnumChatFormatting.DARK_PURPLE + "[" + EnumChatFormatting.LIGHT_PURPLE + "MWP"
            + EnumChatFormatting.DARK_PURPLE + "] " + EnumChatFormatting.GRAY;

    // ---------------------------------------------------------------- lifecycle

    public boolean active() {
        Minecraft mc = Minecraft.getMinecraft();
        return PartyMod.config != null && PartyMod.config.enabled && mc.thePlayer != null
                && mc.getCurrentServerData() != null && ChatEvents.isHypixel(mc.getCurrentServerData().serverIP);
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            commands.clear();
            party.clearMembers();
        });
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active()) return;
        commands.tick(System.currentTimeMillis(), (long) PartyMod.config.commandSpacing,
                command -> Minecraft.getMinecraft().thePlayer.sendChatMessage(command));
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
                if (PartyMod.config.autoCompetitive) remove(e.value, "Cannot play competitive games", true);
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
                } else if (party.remove(e.value) != null) {
                    party.log(e.value + (e.type == ChatEvents.Type.LEAVE ? " left" : " was removed"));
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

    private boolean isSelf(String name) {
        return Minecraft.getMinecraft().thePlayer != null && Minecraft.getMinecraft().thePlayer.getName().equalsIgnoreCase(name);
    }

    private void onJoin(String name, boolean live) {
        if (isSelf(name)) return;
        Member m = party.add(name, live);
        if (m == null) return;
        if (live) party.log(EnumChatFormatting.WHITE + name + EnumChatFormatting.GRAY + " joined");
        check(m);
    }

    // ---------------------------------------------------------------- stat checks

    private void check(Member m) {
        if (trusted.isTrusted(m.name)) {
            m.status = Status.TRUSTED;
            m.reasons.clear();
        } else {
            m.status = Status.CHECKING;
        }
        api.lookup(m.name, PartyMod.config.hypixelApiKey, (int) PartyMod.config.cacheMinutes,
                (int) PartyMod.config.requestsPerMinute,
                result -> Minecraft.getMinecraft().addScheduledTask(() -> onResult(m, result)));
    }

    private void onResult(Member m, HypixelClient.Result result) {
        if (party.get(m.name) != m) return; // left or party changed meanwhile
        m.stats = result.stats;
        m.reasons.clear();
        if (trusted.isTrusted(m.name)) {
            m.status = Status.TRUSTED;
            return;
        }
        if (result.stats == null) {
            m.status = Status.UNAVAILABLE;
            m.error = result.error;
            party.log(EnumChatFormatting.YELLOW + "Could not check " + m.name + ": " + result.error);
            return;
        }
        m.reasons.addAll(Flagger.evaluate(result.stats, PartyMod.config.flagOptions(), System.currentTimeMillis()));
        if (m.reasons.isEmpty()) {
            m.status = Status.CLEAN;
            return;
        }
        m.status = Status.FLAGGED;
        String summary = String.join(", ", m.reasons);
        party.log(StatFormat.rankedName(m.stats, m.name) + EnumChatFormatting.GRAY + " flagged: " + EnumChatFormatting.GOLD + summary);
        if (PartyMod.config.autoRemoveFlagged && m.joinedLive) {
            remove(m.name, summary, false);
            return;
        }
        if (PartyMod.config.flagSound && Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.playSound("note.pling", 1.0F, 0.8F);
        }
        if (PartyMod.config.chatPrompt) promptInChat(m, summary);
    }

    private void promptInChat(Member m, String summary) {
        IChatComponent line = new ChatComponentText(PREFIX + StatFormat.rankedName(m.stats, m.name)
                + EnumChatFormatting.GRAY + " looks suspicious: " + EnumChatFormatting.GOLD + summary + " ");
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

    // ---------------------------------------------------------------- actions

    /**
     * Sends /block add then /p kick for the player, in that order.
     *
     * @param urgent jump the queue (used for the competitive-ban queue refusal)
     */
    public void remove(String name, String reason, boolean urgent) {
        if (!ChatEvents.validName(name) || isSelf(name)) return;
        String[] pair = {"/block add " + name, "/p kick " + name};
        if (urgent) commands.addUrgent(pair);
        else commands.add(pair);
        Member m = party.get(name);
        if (m != null) m.status = Status.REMOVED;
        if (!party.wasBlocked(name)) party.recordBlocked(name, reason);
        String shown = m != null ? StatFormat.rankedName(m.stats, m.name) : EnumChatFormatting.WHITE + name;
        party.log(EnumChatFormatting.RED + "Blocking + kicking " + shown + EnumChatFormatting.GRAY + " (" + reason + EnumChatFormatting.GRAY + ")");
    }

    /** "Removed by <you>" with your name in gold, for the log and blocked list. */
    public String removedBy() {
        Minecraft mc = Minecraft.getMinecraft();
        String self = mc.thePlayer == null ? "you" : mc.thePlayer.getName();
        return EnumChatFormatting.GRAY + "Removed by " + EnumChatFormatting.GOLD + self + EnumChatFormatting.GRAY;
    }

    public void dismiss(String name) {
        Member m = party.get(name);
        if (m == null || m.status != Status.FLAGGED) {
            chat(name + " is not waiting for review.");
            return;
        }
        m.status = Status.DISMISSED;
        party.log(name + " dismissed - staying in party");
    }

    public void removeAllFlagged() {
        List<Member> queue = new ArrayList<>(party.toReview());
        for (Member m : queue) remove(m.name, String.join(", ", m.reasons) + " - " + removedBy(), false);
        if (!queue.isEmpty()) party.log(EnumChatFormatting.RED + "Kicking all " + queue.size() + " flagged players");
    }

    public void trust(String name) {
        if (!ChatEvents.validName(name)) return;
        trusted.add(name);
        Member m = party.get(name);
        if (m != null && m.status != Status.REMOVED) {
            m.status = Status.TRUSTED;
            m.reasons.clear();
        }
        party.log(EnumChatFormatting.AQUA + name + EnumChatFormatting.GRAY + " trusted - never flagged again");
    }

    public void untrust(String name) {
        if (!trusted.remove(name)) {
            chat(name + " is not on your trusted list.");
            return;
        }
        party.log(name + " removed from trusted list");
        Member m = party.get(name);
        if (m != null) recheck(name);
    }

    public void unblock(String name) {
        if (!ChatEvents.validName(name)) return;
        commands.add("/block remove " + name);
        party.unrecordBlocked(name);
        party.log(EnumChatFormatting.GREEN + "Unblocking " + name);
    }

    public void removeNext() {
        List<Member> queue = party.toReview();
        if (queue.isEmpty()) chat("Nobody to review.");
        else remove(queue.get(0).name, String.join(", ", queue.get(0).reasons) + " - " + removedBy(), false);
    }

    public void dismissNext() {
        List<Member> queue = party.toReview();
        if (queue.isEmpty()) chat("Nobody to review.");
        else dismiss(queue.get(0).name);
    }

    public void syncParty() {
        if (!active()) {
            chat("Join Hypixel first.");
            return;
        }
        commands.add("/p list");
        party.log("Syncing party list");
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
