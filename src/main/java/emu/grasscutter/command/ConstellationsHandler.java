package emu.grasscutter.command;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.commands.AvatarCommand;
import emu.grasscutter.command.commands.Element;
import emu.grasscutter.game.player.Player;

public class ConstellationsHandler {
    public static void change(Player targetPlayer, Element element, int constellation) {
        try {
            var command = new AvatarCommand();
            command.createCommandLine(null, targetPlayer).execute("constellation", "reset");
            command.createCommandLine(null, targetPlayer)
                    .execute("constellation", "set", String.valueOf(constellation));
        } catch (Exception e) {
            Grasscutter.getLogger().info("ConstellationHandler error");
        }
    }
}
