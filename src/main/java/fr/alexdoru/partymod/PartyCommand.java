package fr.alexdoru.partymod;

import fr.alexdoru.partymod.core.ChatEvents;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /mwp - the only command. Everything else lives in OneConfig and the overlays. */
public final class PartyCommand extends CommandBase {
    private static final String[] SUBCOMMANDS = {"help", "settings", "panels", "remove", "dismiss", "trust", "untrust", "unblock", "check", "sync", "clear", "export", "import", "setkey", "testkey"};

    @Override
    public String getCommandName() {
        return "mwp";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("mwparty", "partymod");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/mwp <" + String.join("|", SUBCOMMANDS) + ">";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        String name = args.length >= 2 ? args[1] : "";
        PartyRuntime runtime = PartyMod.runtime;
        switch (sub) {
            case "settings":
                PartyMod.config.openGui();
                break;
            case "panels":
                PartyMod.openPanels();
                break;
            case "testkey":
                runtime.testKey();
                break;
            case "export":
                if (name.equalsIgnoreCase("file")) runtime.exportToFile();
                else runtime.exportToClipboard();
                break;
            case "import":
                if (name.isEmpty() || name.equalsIgnoreCase("clipboard")) runtime.importFromClipboard();
                else if (name.equalsIgnoreCase("file")) runtime.importFromFile("");
                else runtime.importFromFile(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                break;
            case "queue": // used by the queue-guard chat buttons
                String mode = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";
                if (name.equals("kick")) runtime.queueAfterKicks(mode);
                else if (name.equals("force")) runtime.queueAnyway(mode);
                break;
            case "remove":
            case "kick":
                if (requireName(name)) runtime.remove(name, "", false, false);
                break;
            case "dismiss":
                if (requireName(name)) runtime.dismiss(name);
                break;
            case "trust":
                if (requireName(name)) runtime.trust(name);
                break;
            case "untrust":
                if (requireName(name)) runtime.untrust(name);
                break;
            case "unblock":
                if (requireName(name)) runtime.unblock(name);
                break;
            case "check":
                if (requireName(name)) runtime.recheck(name);
                break;
            case "sync":
                runtime.syncParty();
                PartyRuntime.chat("Requested the party list; members will be checked as they appear.");
                break;
            case "clear":
                runtime.party.clearLog();
                PartyRuntime.chat("Cleared the party log.");
                break;
            case "setkey":
                if (name.isEmpty()) {
                    PartyRuntime.chat("Usage: /mwp setkey <key>  (get one at developer.hypixel.net)");
                    break;
                }
                PartyMod.config.hypixelApiKey = name.trim();
                PartyMod.config.save();
                PartyRuntime.chat("Hypixel API key saved.");
                runtime.testKey();
                break;
            default:
                help();
                break;
        }
    }

    private static boolean requireName(String name) {
        if (ChatEvents.validName(name)) return true;
        PartyRuntime.chat("Give a valid player name.");
        return false;
    }

    static final String DIVIDER = "\u00a78\u00a7m                                        \u00a7r";

    private static void help() {
        PartyRuntime.chat("\u00a7dCommands \u00a78(\u00a77hover a command for details\u00a78) \u00a77v" + PartyMod.version());
        PartyRuntime.chatRaw(DIVIDER);
        helpRow("panels", "Open the interactive party panels (same as pressing " + PartyMod.config.overlayKey.getDisplay()
                + "). Every command below also has a button there.");
        helpRow("remove <name>", "Sends \u00a7f/block add\u00a77, then \u00a7f/p kick\u00a77. Undo it for 10 seconds from the party log.");
        helpRow("dismiss <name>", "Keep a flagged player in the party and take them out of the review queue.");
        helpRow("trust <name>", "Never flag this player again. Saved across sessions.");
        helpRow("untrust <name>", "Remove a player from your trusted list and re-check them.");
        helpRow("unblock <name>", "Sends \u00a7f/block remove\u00a77 and forgets the removal from your blocked history.");
        helpRow("check <name>", "Re-run the Hypixel stat check for one player.");
        helpRow("sync", "Sends \u00a7f/p list\u00a77, checks everyone, and drops anyone who has already left.");
        helpRow("clear", "Clear the party log panel.");
        helpRow("export [file]", "Copy your trusted + blocked lists to the clipboard, or save \u00a7fexport-<date>.json\u00a77 with \u00a7ffile\u00a77.");
        helpRow("import [file]", "Merge a co-host's lists from the clipboard, or the newest export file with \u00a7ffile\u00a77. Only adds, never removes.");
        helpRow("setkey <key>", "Save your Hypixel API key and test it.");
        helpRow("testkey", "Check that your Hypixel API key works.");
        helpRow("settings", "Open the OneConfig settings.");
        PartyRuntime.chatRaw(DIVIDER);
    }

    /** One EDITH-style help row: hover for the description, click to put the command in chat. */
    private static void helpRow(String args, String detail) {
        String label = "/mwp " + args;
        String base = "/mwp " + args.split(" ")[0];
        String suggest = (args.indexOf('<') >= 0 || args.indexOf('[') >= 0) ? base + " " : base;
        net.minecraft.util.ChatComponentText row = new net.minecraft.util.ChatComponentText("  \u00a77\u00bb \u00a7e" + label);
        row.setChatStyle(new net.minecraft.util.ChatStyle()
                .setChatClickEvent(new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.SUGGEST_COMMAND, suggest))
                .setChatHoverEvent(new net.minecraft.event.HoverEvent(net.minecraft.event.HoverEvent.Action.SHOW_TEXT,
                        new net.minecraft.util.ChatComponentText("\u00a7d" + label + "\n\u00a77" + detail + "\n\u00a78Click to put in chat"))));
        PartyRuntime.chatComponent(row);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        if (args.length == 2 && Arrays.asList("remove", "kick", "dismiss", "trust", "untrust", "unblock", "check").contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> names = new ArrayList<>();
            PartyMod.runtime.party.members().forEach(m -> names.add(m.name));
            return getListOfStringsMatchingLastWord(args, names.toArray(new String[0]));
        }
        return null;
    }
}
