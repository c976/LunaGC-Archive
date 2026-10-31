package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.data.GameData;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "dungeon",
        permission = "player.enterdungeon",
        permissionTargeted = "player.enterdungeon.others")
public final class EnterDungeonCommand implements CommandHandler {
    // The command has been renamed; preserve existing translated text keys.
    @Override
    public String getDescriptionKey() {
        return "commands.enter_dungeon.description";
    }
    static boolean validDungeonId(int id) {
        return id > 0;
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "dungeon")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<dungeonId>")
        private int dungeonId;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (targetPlayer == null || !targetPlayer.isOnline()
                    || targetPlayer.getSession() == null
                    || !targetPlayer.getSession().isActive()
                    || targetPlayer.getScene() == null || targetPlayer.getWorld() == null) {
                CommandOutput.sendMessage(sender, "Entering a dungeon requires an online player in a scene.");
                return;
            }
            if (!validDungeonId(dungeonId)) {
                CommandOutput.sendMessage(sender, "Dungeon ID must be positive.");
                return;
            }
            var data = GameData.getDungeonDataMap().get(dungeonId);
            if (data == null) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.enter_dungeon.not_found_error"));
                return;
            }
            var currentManager = targetPlayer.getScene().getDungeonManager();
            if (currentManager != null && currentManager.getDungeonData() != null
                    && currentManager.getDungeonData().getId() == dungeonId) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.enter_dungeon.in_dungeon_error"));
                return;
            }

            // savePrevious preserves the return scene; it does not bypass entry conditions.
            boolean entered = targetPlayer.getServer().getDungeonSystem()
                    .enterDungeon(targetPlayer, 0, dungeonId, true);
            if (entered) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.enter_dungeon.changed", dungeonId));
            } else {
                CommandOutput.sendMessage(sender, "Could not enter dungeon " + dungeonId + ".");
            }
        }
    }
}
