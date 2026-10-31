package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamAbilityToggle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

/** Toggles team-bar support buttons backed by team abilities. */
@Command(
        label = "support",
        aliases = {"assist"},
        permission = "player.support",
        permissionTargeted = "player.support.others")
public final class SupportCommand implements CommandHandler {
    private static final Map<String, String> BUTTONS = new LinkedHashMap<>();

    static {
        BUTTONS.put("pierro", "Level_Ronova_SupportSkillHandler");
        BUTTONS.put("valeriy", "SkillObj_SupportSkill_Valeriy_TeamListener");
        BUTTONS.put("blast", "SkillObj_SupportSkill_Laodai_TeamListener");
    }

    /** Resolve all team support choices through one command, including the former Pierro route. */
    static List<String> selectAbilities(String which) {
        String key = which.toLowerCase(Locale.ROOT);
        if ("all".equals(key)) return List.copyOf(BUTTONS.values());
        String ability = BUTTONS.get(key);
        return ability == null ? List.of() : List.of(ability);
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "support")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<pierro|valeriy|blast|all>")
        private String which;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[on|off]")
        private String state;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            String key = which.toLowerCase(Locale.ROOT);
            List<String> abilities = selectAbilities(key);
            if (abilities.isEmpty()) {
                CommandOutput.sendMessage(sender, "Unknown support button: " + which);
                return;
            }

            boolean enable;
            if (state == null) {
                enable = !abilities.stream().allMatch(a -> TeamAbilityToggle.isOn(targetPlayer, a));
            } else {
                switch (state.toLowerCase()) {
                    case "on" -> enable = true;
                    case "off" -> enable = false;
                    default -> {
                        CommandOutput.sendMessage(sender, "State must be on or off.");
                        return;
                    }
                }
            }

            TeamAbilityToggle.set(targetPlayer, abilities, enable);
            CommandOutput.sendTranslatedMessage(
                    sender, enable ? "commands.support.on" : "commands.support.off", key);
        }
    }
}
