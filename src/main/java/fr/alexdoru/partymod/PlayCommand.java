package fr.alexdoru.partymod;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

/**
 * Client-side /play so the queue guard can warn before you queue with players still
 * waiting for review. When there is nothing to review it forwards straight to Hypixel.
 */
public final class PlayCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "play";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/play <mode>";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        PartyRuntime runtime = PartyMod.runtime;
        String mode = String.join(" ", args);
        if (runtime == null || !runtime.active()) {
            net.minecraft.client.Minecraft.getMinecraft().thePlayer.sendChatMessage(("/play " + mode).trim());
            return;
        }
        runtime.tryQueue(mode);
    }
}
