package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ClimateType;
import java.util.Locale;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "weather",
        aliases = {"w"},
        permission = "player.weather",
        permissionTargeted = "player.weather.others")
public final class WeatherCommand implements CommandHandler {
    private record WeatherToken(Integer weatherId, ClimateType climate) {}

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender, targetPlayer));
        commandLine.registerConverter(
                WeatherToken.class,
                value -> {
                    ClimateType climate =
                            ClimateType.getTypeByShortName(value.toLowerCase(Locale.ROOT));
                    if (climate != ClimateType.CLIMATE_NONE) {
                        return new WeatherToken(null, climate);
                    }
                    try {
                        return new WeatherToken(Integer.parseInt(value), null);
                    } catch (NumberFormatException ignored) {
                        throw new CommandLine.TypeConversionException("Invalid weather id or climate: " + value);
                    }
                });
        return commandLine;
    }

    @CommandLine.Command(name = "weather")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0..1", arity = "0..2", paramLabel = "[weatherId|climate]")
        private WeatherToken[] tokens = new WeatherToken[0];

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (tokens.length == 0) {
                CommandOutput.sendTranslatedMessage(
                        sender,
                        "commands.weather.status",
                        targetPlayer.getWeatherId(),
                        targetPlayer.getClimate().getShortName());
                return;
            }

            int weatherId = targetPlayer.getWeatherId();
            ClimateType climate = ClimateType.CLIMATE_NONE;
            for (WeatherToken token : tokens) {
                if (token.weatherId() != null) weatherId = token.weatherId();
                if (token.climate() != null) climate = token.climate();
            }

            targetPlayer.setWeather(weatherId, climate);
            CommandOutput.sendTranslatedMessage(
                    sender,
                    "commands.weather.success",
                    weatherId,
                    targetPlayer.getClimate().getShortName());
        }
    }
}
