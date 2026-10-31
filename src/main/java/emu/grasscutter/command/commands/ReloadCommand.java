package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.combine.CombineManger;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.reward.RewardConfiguration;
import java.util.Arrays;
import java.util.List;
import picocli.CommandLine;

/**
 * Hot-reload only the modules that already supported it. No game-resource, quest or script
 * reload is implied: those need an explicit restart to prevent stale scene state.
 */
@Command(
        label = "reload",
        permission = "server.reload",
        targetRequirement = Command.TargetRequirement.NONE)
public final class ReloadCommand implements CommandHandler {
    /** Single source of truth for the supported reload modules and their legacy execution order. */
    enum Module {
        CONFIG("config", Grasscutter::loadConfig),
        LANGUAGE("language", Grasscutter::loadLanguage),
        GACHA("gacha", () -> Grasscutter.getGameServer().getGachaSystem().load()),
        SHOP("shop", () -> Grasscutter.getGameServer().getShopSystem().load()),
        COMBINE("combine", CombineManger::initialize),
        REWARDS("rewards", RewardConfiguration::load);

        final String command;
        private final Runnable action;

        Module(String command, Runnable action) {
            this.command = command;
            this.action = action;
        }

        void reload() {
            action.run();
        }
    }

    static List<String> supportedModules() {
        return Arrays.stream(Module.values()).map(module -> module.command).toList();
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var root = new CommandLine(new Root(sender));
        root.addSubcommand("all", new ModuleAction(sender, null));
        for (var module : Module.values()) {
            root.addSubcommand(module.command, new ModuleAction(sender, module));
        }
        return root;
    }

    private static void runReload(Player sender, Module selected) {
        boolean all = selected == null;
        String label = all ? "all" : selected.command;
        CommandOutput.sendMessage(sender, translate(sender, "commands.reload.reload_start"));

        try {
            if (all) {
                for (var module : Module.values()) module.reload();
            } else {
                selected.reload();
            }
        } catch (RuntimeException failure) {
            Grasscutter.getLogger().error("Reload of {} failed.", label, failure);
            CommandOutput.sendMessage(sender,
                    "Reload " + label + " failed: " + failure.getMessage());
            return;
        }

        CommandOutput.sendMessage(sender, translate(sender, "commands.reload.reload_done"));
        if (!all) CommandOutput.sendMessage(sender, "Reloaded: " + label);
    }

    @CommandLine.Command(name = "reload", description = "Reload configurable modules; no quest or script reload.")
    private static final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            runReload(sender, null);
        }
    }

    @CommandLine.Command(name = "module", description = "Reload a specific module.")
    private static final class ModuleAction implements Runnable {
        private final Player sender;
        private final Module module;

        private ModuleAction(Player sender, Module module) {
            this.sender = sender;
            this.module = module;
        }

        @Override
        public void run() {
            runReload(sender, module);
        }
    }
}
