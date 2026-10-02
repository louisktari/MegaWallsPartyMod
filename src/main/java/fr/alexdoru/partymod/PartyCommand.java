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
    private static final String[] SUBCOMMANDS = {"help", "settings", "remove", "dismiss", "trust", "untrust", "unblock", "check", "sync", "clear", "setkey"};

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
            case "remove":
            case "kick":
                if (requireName(name)) runtime.remove(name, runtime.removedBy(), false);
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
                runtime.party.clearLogs();
                PartyRuntime.chat("Cleared the party log and blocked list overlays.");
                break;
            case "setkey":
                if (name.isEmpty()) {
                    PartyRuntime.chat("Usage: /mwp setkey <key>  (get one at developer.hypixel.net)");
                    break;
                }
                PartyMod.config.hypixelApiKey = name.trim();
                PartyMod.config.save();
                PartyRuntime.chat("Hypixel API key saved.");
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

    private static void help() {
        PartyRuntime.chat("Mega Walls Party Mod v" + PartyMod.version());
        PartyRuntime.chat("Tip: open chat or press " + PartyMod.config.overlayKey.getDisplay()
                + " to click, hover and drag the party panels - no commands needed.");
        PartyRuntime.chat("/mwp remove <name> - /block add then /p kick");
        PartyRuntime.chat("/mwp dismiss <name> - keep a flagged player");
        PartyRuntime.chat("/mwp trust|untrust <name> - never flag / flag again");
        PartyRuntime.chat("/mwp unblock <name> - /block remove a player");
        PartyRuntime.chat("/mwp check <name> - re-run the stat check");
        PartyRuntime.chat("/mwp sync - send /p list and check everyone");
        PartyRuntime.chat("/mwp clear - clear the log and blocked overlays");
        PartyRuntime.chat("/mwp setkey <key> - set your Hypixel API key");
        PartyRuntime.chat("/mwp settings - open OneConfig");
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
