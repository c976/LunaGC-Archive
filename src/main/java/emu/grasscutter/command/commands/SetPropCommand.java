package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.tower.TowerLevelRecord;
import emu.grasscutter.server.packet.send.*;
import java.util.*;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "prop",
        permission = "player.setprop",
        permissionTargeted = "player.setprop.others")
public final class SetPropCommand implements CommandHandler {

    private final Map<String, Prop> props;

    private record PropArg(Prop value) {}

    private record ValueArg(Integer value, String text) {}

    public SetPropCommand() {
        this.props = new HashMap<>();

        for (PlayerProperty prop : PlayerProperty.values()) {
            String name = prop.toString().substring(5);
            String key = name.toLowerCase();
            this.props.put(key, new Prop(name, prop));
        }

        Prop worldlevel =
                new Prop("World Level", PlayerProperty.PROP_PLAYER_WORLD_LEVEL, PseudoProp.WORLD_LEVEL);
        this.props.put("worldlevel", worldlevel);
        this.props.put("wl", worldlevel);

        Prop abyss = new Prop("Tower Level", PseudoProp.TOWER_LEVEL);
        this.props.put("abyss", abyss);
        this.props.put("abyssfloor", abyss);
        this.props.put("ut", abyss);
        this.props.put("tower", abyss);
        this.props.put("towerlevel", abyss);
        this.props.put("unlocktower", abyss);

        Prop bplevel = new Prop("BP Level", PseudoProp.BP_LEVEL);
        this.props.put("bplevel", bplevel);
        this.props.put("bp", bplevel);
        this.props.put("battlepass", bplevel);

        Prop godmode = new Prop("GodMode", PseudoProp.GOD_MODE);
        this.props.put("godmode", godmode);
        this.props.put("god", godmode);

        Prop nostamina = new Prop("UnlimitedStamina", PseudoProp.UNLIMITED_STAMINA);
        this.props.put("unlimitedstamina", nostamina);
        this.props.put("us", nostamina);
        this.props.put("nostamina", nostamina);
        this.props.put("nostam", nostamina);
        this.props.put("ns", nostamina);

        Prop unlimitedenergy = new Prop("UnlimitedEnergy", PseudoProp.UNLIMITED_ENERGY);
        this.props.put("unlimitedenergy", unlimitedenergy);
        this.props.put("ue", unlimitedenergy);

        Prop flyable = new Prop("IsFlyable", PlayerProperty.PROP_IS_FLYABLE, PseudoProp.IS_FLYABLE);
        this.props.put("canfly", flyable);
        this.props.put("fly", flyable);
        this.props.put("glider", flyable);
        this.props.put("canglide", flyable);

        Prop dive = new Prop("CanDive", PlayerProperty.PROP_PLAYER_CAN_DIVE, PseudoProp.CAN_DIVE);
        this.props.put("dive", dive);
        this.props.put("swim", dive);
        this.props.put("water", dive);
        this.props.put("candive", dive);
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender, targetPlayer));
        commandLine.setExpandAtFiles(false);
        commandLine.registerConverter(
                PropArg.class,
                value -> {
                    Prop property = props.get(value.toLowerCase(Locale.ROOT));
                    if (property == null) {
                        throw new CommandLine.TypeConversionException(
                                "Unknown player property: " + value);
                    }
                    return new PropArg(property);
                });
        commandLine.registerConverter(
                ValueArg.class,
                value -> {
                    String normalized = value.toLowerCase(Locale.ROOT);
                    Integer parsed =
                            switch (normalized) {
                                case "on", "true" -> 1;
                                case "off", "false" -> 0;
                                case "toggle" -> -1;
                                case "all" -> -2;
                                default -> {
                                    try {
                                        yield Integer.parseInt(normalized);
                                    } catch (NumberFormatException ignored) {
                                        yield null;
                                    }
                                }
                            };
                    return new ValueArg(parsed, normalized);
                });
        return commandLine;
    }

    @picocli.CommandLine.Command(
            name = "prop",
            description = "Query a player property, or set it by supplying a value.")
    private final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<property>")
        private PropArg property;

        @Parameters(index = "1", arity = "0..1", paramLabel = "[value]")
        private ValueArg requestedValue;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            Prop prop = property.value();
            if (requestedValue == null) {
                String current = readProperty(targetPlayer, prop);
                if (current == null) {
                    CommandOutput.sendMessage(sender,
                            "Property " + prop.name + " does not have a readable value.");
                } else {
                    CommandOutput.sendMessage(sender, prop.name + " = " + current);
                }
                return;
            }
            if (requestedValue.value() == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.execution.argument_error");
                return;
            }

            int value = requestedValue.value();
            boolean success =
                    switch (prop.pseudoProp) {
                        case WORLD_LEVEL -> targetPlayer.setWorldLevel(value);
                        case BP_LEVEL -> targetPlayer.getBattlePassManager().setLevel(value);
                        case TOWER_LEVEL -> setTowerLevel(sender, targetPlayer, value);
                        case GOD_MODE, UNLIMITED_STAMINA, UNLIMITED_ENERGY ->
                                setBool(sender, targetPlayer, prop.pseudoProp, value);
                        case CAN_DIVE -> canDive(targetPlayer, value);
                        default -> prop.prop == PlayerProperty.PROP_PLAYER_LEVEL
                                ? targetPlayer.setLevel(value)
                                : targetPlayer.setProperty(prop.prop, value);
                    };

            if (success) {
                if (targetPlayer == sender) {
                    CommandOutput.sendTranslatedMessage(
                            sender, "commands.generic.set_to", prop.name, requestedValue.text());
                } else {
                    String uidStr = targetPlayer.getAccount().getId();
                    CommandOutput.sendTranslatedMessage(
                            sender,
                            "commands.generic.set_for_to",
                            prop.name,
                            uidStr,
                            requestedValue.text());
                }
            } else if (prop.prop != PlayerProperty.PROP_NONE) {
                int min = targetPlayer.getPropertyMin(prop.prop);
                int max = targetPlayer.getPropertyMax(prop.prop);
                CommandOutput.sendTranslatedMessage(
                        sender, "commands.generic.invalid.value_between", prop.name, min, max);
            }
        }
    }

    /** Returns the live value behind each readable property; action-only operations return null. */
    static String readProperty(Player player, Prop prop) {
        return switch (prop.pseudoProp) {
            case WORLD_LEVEL -> Integer.toString(player.getWorldLevel());
            case BP_LEVEL -> player.getBattlePassManager() == null
                    ? null : Integer.toString(player.getBattlePassManager().getLevel());
            case TOWER_LEVEL -> {
                var floorIds = player.getServer().getTowerSystem().getAllFloors();
                var records = player.getTowerManager().getRecordMap();
                int unlocked = 0;
                for (int id : floorIds) {
                    if (!records.containsKey(id)) break;
                    unlocked++;
                }
                yield Integer.toString(unlocked);
            }
            case GOD_MODE -> Boolean.toString(player.isInGodMode());
            case UNLIMITED_STAMINA -> Boolean.toString(player.isUnlimitedStamina());
            case UNLIMITED_ENERGY ->
                    Boolean.toString(!player.getEnergyManager().isEnergyUsage());
            case IS_FLYABLE -> Boolean.toString(
                    player.getProperty(PlayerProperty.PROP_IS_FLYABLE) != 0);
            case CAN_DIVE -> Boolean.toString(
                    player.getProperty(PlayerProperty.PROP_PLAYER_CAN_DIVE) != 0);
            case NONE -> Integer.toString(player.getProperty(prop.prop));
        };
    }

    private boolean setTowerLevel(Player sender, Player targetPlayer, int topFloor) {
        List<Integer> floorIds = targetPlayer.getServer().getTowerSystem().getAllFloors();
        if (topFloor < 0 || topFloor > floorIds.size()) {
            CommandOutput.sendTranslatedMessage(
                    sender, "commands.generic.invalid.value_between", "Tower Level", 0, floorIds.size());
            return false;
        }

        Map<Integer, TowerLevelRecord> recordMap = targetPlayer.getTowerManager().getRecordMap();

        for (int floor : floorIds.subList(0, topFloor)) {
            if (!recordMap.containsKey(floor)) {
                recordMap.put(floor, new TowerLevelRecord(floor));
            }
        }

        for (int floor : floorIds.subList(topFloor, floorIds.size())) {
            recordMap.remove(floor);
        }

        return true;
    }

    private boolean setBool(Player sender, Player targetPlayer, PseudoProp pseudoProp, int value) {
        boolean enabled =
                switch (pseudoProp) {
                    case GOD_MODE -> targetPlayer.isInGodMode();
                    case UNLIMITED_STAMINA -> targetPlayer.isUnlimitedStamina();
                    case UNLIMITED_ENERGY -> !targetPlayer.getEnergyManager().isEnergyUsage();
                    default -> false;
                };
        enabled =
                switch (value) {
                    case -1 -> !enabled;
                    case 0 -> false;
                    default -> true;
                };

        switch (pseudoProp) {
            case GOD_MODE:
                targetPlayer.setInGodMode(enabled);
                break;
            case UNLIMITED_STAMINA:
                targetPlayer.setUnlimitedStamina(enabled);
                break;
            case UNLIMITED_ENERGY:
                targetPlayer.getEnergyManager().setEnergyUsage(!enabled);
                break;
            default:
                return false;
        }
        return true;
    }

    private boolean canDive(Player targetPlayer, int value) {
        if (value == 0) {
            targetPlayer.setProperty(PlayerProperty.PROP_PLAYER_CAN_DIVE, 0);
            targetPlayer.setProperty(PlayerProperty.PROP_DIVE_MAX_STAMINA, 0);
            targetPlayer.setProperty(PlayerProperty.PROP_DIVE_CUR_STAMINA, 0);
        } else {
            targetPlayer.setProperty(PlayerProperty.PROP_PLAYER_CAN_DIVE, 1);
            targetPlayer.setProperty(PlayerProperty.PROP_DIVE_MAX_STAMINA, 10000);
            targetPlayer.setProperty(PlayerProperty.PROP_DIVE_CUR_STAMINA, 10000);
        }
        return true;
    }

    enum PseudoProp {
        NONE,
        WORLD_LEVEL,
        TOWER_LEVEL,
        BP_LEVEL,
        GOD_MODE,
        UNLIMITED_STAMINA,
        UNLIMITED_ENERGY,
        IS_FLYABLE,
        CAN_DIVE
    }

    static class Prop {
        String name;
        PlayerProperty prop;
        PseudoProp pseudoProp;

        public Prop(PlayerProperty prop) {
            this(prop.toString(), prop, PseudoProp.NONE);
        }

        public Prop(String name) {
            this(name, PlayerProperty.PROP_NONE, PseudoProp.NONE);
        }

        public Prop(String name, PseudoProp pseudoProp) {
            this(name, PlayerProperty.PROP_NONE, pseudoProp);
        }

        public Prop(String name, PlayerProperty prop) {
            this(name, prop, PseudoProp.NONE);
        }

        public Prop(String name, PlayerProperty prop, PseudoProp pseudoProp) {
            this.name = name;
            this.prop = prop;
            this.pseudoProp = pseudoProp;
        }
    }
}
