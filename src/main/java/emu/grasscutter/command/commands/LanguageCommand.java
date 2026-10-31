package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.Utils;
import java.util.Locale;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "language",
        aliases = {"lang"},
        targetRequirement = Command.TargetRequirement.NONE)
public final class LanguageCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender));
    }

    @CommandLine.Command(name = "language")
    private static final class Args implements Runnable {
        private final Player sender;

        @Parameters(index = "0", arity = "0..1", paramLabel = "[language-code]")
        private String languageCode;

        private Args(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            if (languageCode == null) {
                String current =
                        sender != null
                                ? Utils.getLanguageCode(sender.getAccount().getLocale())
                                : Grasscutter.getLanguage().getLanguageCode();
                CommandOutput.sendMessage(
                        sender, translate(sender, "commands.language.current_language", current));
                return;
            }

            var language = Grasscutter.getLanguage(languageCode);
            var actualCode = language.getLanguageCode();
            var locale = Locale.forLanguageTag(actualCode);

            if (sender != null) {
                var account = sender.getAccount();
                account.setLocale(locale);
                DatabaseHelper.saveAccountFieldsAsync(account, "locale");
            } else {
                Grasscutter.setLanguage(language);
                var config = Grasscutter.getConfig();
                config.language.language = locale;
                Grasscutter.saveConfig(config);
            }

            if (!languageCode.equals(actualCode)) {
                CommandOutput.sendMessage(
                        sender,
                        translate(sender, "commands.language.language_not_found", languageCode));
            }
            CommandOutput.sendMessage(
                    sender, translate(sender, "commands.language.language_changed", actualCode));
        }
    }
}
