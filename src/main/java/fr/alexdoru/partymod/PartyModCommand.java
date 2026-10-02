package fr.alexdoru.partymod;

import net.minecraft.command.*;
import net.minecraft.util.*;
import java.util.*;

public final class PartyModCommand extends CommandBase {
    @Override public String getCommandName(){return "partymod";}
    @Override public List<String> getCommandAliases(){return Arrays.asList("pmorganiser");}
    @Override public String getCommandUsage(ICommandSender sender){return "/partymod [settings|enable|disable|sync|ready|precheck|wait <name>|help]";}
    @Override public boolean canCommandSenderUseCommand(ICommandSender sender){return true;}
    @Override public void processCommand(ICommandSender sender,String[] args){String command=args.length==0?"open":args[0].toLowerCase(Locale.ROOT);switch(command){
        case "open":PartyMod.openRoster();break;
        case "settings":PartyMod.config.openGui();break;
        case "enable":case "disable":PartyMod.config.enabled=command.equals("enable");PartyMod.config.save();PartyMod.runtime.applyEnabled();break;
        case "sync":PartyMod.runtime.sync();break;
        case "ready":PartyMod.runtime.startReady();break;
        case "precheck":PartyMod.runtime.precheck().forEach(PartyRuntime::chat);break;
        case "wait":if(args.length==2&&PartyMod.runtime.store.addWait(args[1]))PartyRuntime.chat("Added to local waitlist: "+args[1]);else PartyRuntime.chat("Use /partymod wait <username>; duplicates are not added.");break;
        case "setapikey":if(args.length!=2||args[1].length()>128||!args[1].matches("[A-Za-z0-9_-]{8,128}")){PartyRuntime.chat("Use /partymod setapikey <key>, or enter it in OneConfig > Data.");break;}PartyMod.config.hypixelApiKey=args[1];PartyMod.config.statsSource=0;PartyMod.config.save();PartyMod.runtime.assessAll();PartyRuntime.chat("Hypixel API key saved; direct stats checks selected.");break;
        default:PartyRuntime.chat(getCommandUsage(sender));PartyRuntime.chat("Removal and ignore actions are separate controls in the organiser.");break;
    }}
    @Override public List<String> addTabCompletionOptions(ICommandSender sender,String[] args,BlockPos pos){return args.length==1?getListOfStringsMatchingLastWord(args,"settings","enable","disable","sync","ready","precheck","wait","setapikey","help"):null;}
}
