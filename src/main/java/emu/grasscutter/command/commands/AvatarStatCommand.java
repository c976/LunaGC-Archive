package emu.grasscutter.command.commands;

import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/** Internal avatar subcommand, not a standalone command or alias. */
final class AvatarStatCommand {
    private static final String PERMISSION = "player.setstats";
    private final Map<String, Stat> stats = new HashMap<>();

    static String operationPermission() {
        return PERMISSION;
    }

    private record StatArg(Stat stat) {}

    private AvatarStatCommand() {
        for (String key : FightProperty.getShortNames()) {
            stats.put(key, new Stat(FightProperty.getPropByShortName(key)));
        }
        for (FightProperty prop : FightProperty.values()) {
            String name = prop.toString().substring(10);
            String key = name.toLowerCase();
            name = name.substring(1);
            stats.put(key, new Stat(name, prop));
        }

        stats.put("mhp", stats.get("maxhp"));
        stats.put("hp", stats.get("_cur_hp"));
        stats.put("atk", stats.get("_cur_attack"));
        stats.put("def", stats.get("_cur_defense"));
        stats.put("atkb", stats.get("_base_attack"));
        stats.put("eanemo", stats.get("anemo%"));
        stats.put("ecryo", stats.get("cryo%"));
        stats.put("edendro", stats.get("dendro%"));
        stats.put("edend", stats.get("dendro%"));
        stats.put("eelectro", stats.get("electro%"));
        stats.put("eelec", stats.get("electro%"));
        stats.put("ethunder", stats.get("electro%"));
        stats.put("egeo", stats.get("geo%"));
        stats.put("ehydro", stats.get("hydro%"));
        stats.put("epyro", stats.get("pyro%"));
        stats.put("ephys", stats.get("phys%"));
    }

    static CommandLine create(Player sender, Player targetPlayer) {
        var command = new AvatarStatCommand();
        var cli = new CommandLine(new Root(sender));
        cli.addSubcommand("set", command.new Set(sender, targetPlayer));
        cli.addSubcommand("lock", command.new Lock(sender, targetPlayer));
        cli.addSubcommand("unlock", command.new Unlock(sender, targetPlayer));
        // Register after constructing the tree: Picocli converters are local to each command.
        CommandHandler.registerConverterTree(
                cli,
                StatArg.class,
                value -> {
                    Stat stat = command.stats.get(value.toLowerCase(Locale.ROOT));
                    if (stat == null) {
                        throw new CommandLine.TypeConversionException("Unknown stat: " + value);
                    }
                    return new StatArg(stat);
                });
        return cli;
    }

    @CommandLine.Command(name = "stat")
    private static final class Root implements Runnable {
        private final Player sender;
        @Spec private CommandSpec spec;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            CommandOutput.sendMessage(sender, spec.commandLine().getUsageMessage().stripTrailing());
        }
    }

    private abstract class StatOperation implements Runnable {
        protected final Player sender;
        protected final Player target;

        @Option(names = "--avatar", paramLabel = "<avatarId>")
        protected Integer avatarId;

        private StatOperation(Player sender, Player target) {
            this.sender = sender;
            this.target = target;
        }

        protected boolean permitted() {
            return AvatarCommand.permitted(sender, target, PERMISSION);
        }

        protected Avatar avatar() {
            return AvatarCommand.selectOwnedAvatar(sender, target, avatarId);
        }
    }

    @CommandLine.Command(name = "set")
    private final class Set extends StatOperation {
        @Parameters(index = "0", paramLabel = "<stat>")
        private StatArg stat;

        @Parameters(index = "1", paramLabel = "<value>")
        private String value;

        private Set(Player sender, Player target) {
            super(sender, target);
        }

        @Override
        public void run() {
            if (!permitted()) return;
            Avatar avatar = avatar();
            if (avatar == null) return;
            float parsed;
            try {
                parsed = parsePercent(value);
            } catch (NumberFormatException invalid) {
                CommandOutput.sendTranslatedMessage(sender, "commands.generic.invalid.statValue");
                return;
            }

            // Active-team entities need the scene packet; off-team avatars have no entity.
            EntityAvatar entity = avatar.getAsEntity();
            if (entity == null) {
                avatar.setFightProperty(stat.stat().prop, parsed);
                target.sendPacket(new PacketAvatarFightPropNotify(avatar));
            } else {
                entity.setFightProperty(stat.stat().prop, parsed);
                entity.getWorld().broadcastPacket(
                        new PacketEntityFightPropUpdateNotify(entity, stat.stat().prop));
            }
            report(sender, target, Action.ACTION_SET, stat.stat(), parsed);
        }
    }

    @CommandLine.Command(name = "lock")
    private final class Lock extends StatOperation {
        @Parameters(index = "0", paramLabel = "<stat>")
        private StatArg stat;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[value]")
        private String value;

        private Lock(Player sender, Player target) {
            super(sender, target);
        }

        @Override
        public void run() {
            if (!permitted()) return;
            Avatar avatar = avatar();
            if (avatar == null) return;
            float parsed;
            if (value == null) {
                parsed = avatar.getFightProperty(stat.stat().prop);
            } else {
                try {
                    parsed = parsePercent(value);
                } catch (NumberFormatException invalid) {
                    CommandOutput.sendTranslatedMessage(sender, "commands.generic.invalid.statValue");
                    return;
                }
            }
            avatar.getFightPropOverrides().put(stat.stat().prop.getId(), parsed);
            avatar.recalcStats();
            report(sender, target, Action.ACTION_LOCK, stat.stat(), parsed);
        }
    }

    @CommandLine.Command(name = "unlock")
    private final class Unlock extends StatOperation {
        @Parameters(index = "0", paramLabel = "<stat>")
        private StatArg stat;

        private Unlock(Player sender, Player target) {
            super(sender, target);
        }

        @Override
        public void run() {
            if (!permitted()) return;
            Avatar avatar = avatar();
            if (avatar == null) return;
            float previous = avatar.getFightProperty(stat.stat().prop);
            avatar.getFightPropOverrides().remove(stat.stat().prop.getId());
            avatar.recalcStats();
            report(sender, target, Action.ACTION_UNLOCK, stat.stat(), previous);
        }
    }

    private void report(Player sender, Player targetPlayer, Action action, Stat stat, float value) {
        String valueStr =
                FightProperty.isPercentage(stat.prop)
                        ? String.format("%.1f%%", value * 100f)
                        : String.format("%.0f", value);
        if (targetPlayer == sender) {
            CommandOutput.sendTranslatedMessage(sender, action.messageKeySelf, stat.name, valueStr);
        } else {
            String uidStr = targetPlayer.getAccount().getId();
            CommandOutput.sendTranslatedMessage(
                    sender, action.messageKeyOther, stat.name, uidStr, valueStr);
        }
    }

    public static float parsePercent(String input) throws NumberFormatException {
        return input.endsWith("%")
                ? Float.parseFloat(input.substring(0, input.length() - 1)) / 100f
                : Float.parseFloat(input);
    }

    private enum Action {
        ACTION_SET("commands.generic.set_to", "commands.generic.set_for_to"),
        ACTION_LOCK("commands.setStats.locked_to", "commands.setStats.locked_for_to"),
        ACTION_UNLOCK("commands.setStats.unlocked", "commands.setStats.unlocked_for");

        private final String messageKeySelf;
        private final String messageKeyOther;

        Action(String messageKeySelf, String messageKeyOther) {
            this.messageKeySelf = messageKeySelf;
            this.messageKeyOther = messageKeyOther;
        }
    }

    private static final class Stat {
        private final String name;
        private final FightProperty prop;

        private Stat(FightProperty prop) {
            this(prop.toString(), prop);
        }

        private Stat(String name, FightProperty prop) {
            this.name = name;
            this.prop = prop;
        }
    }
}
