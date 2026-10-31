package emu.grasscutter.command.commands;

import static emu.grasscutter.config.Configuration.GAME_INFO;
import static emu.grasscutter.config.Configuration.SERVER;

import emu.grasscutter.Grasscutter.ServerDebugMode;
import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import com.google.protobuf.Descriptors;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.TreeSet;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@Command(
        label = "proto",
        permission = "grasscutter.command.debug",
        targetRequirement = Command.TargetRequirement.NONE)
public final class ProtoCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.addSubcommand("mode", new Mode(sender));
        commandLine.addSubcommand("payload", new Payload(sender));
        commandLine.addSubcommand("loops", new Loops(sender));
        commandLine.addSubcommand("whitelist", new Whitelist(sender));
        commandLine.addSubcommand("blacklist", new Blacklist(sender));
        commandLine.addSubcommand("info", new Info(sender));
        return commandLine;
    }

    private static void showStatus(Player sender) {
        CommandOutput.sendMessage(sender, "Proto logging mode: " + GAME_INFO.logPackets);
        CommandOutput.sendMessage(sender, "Payload logging: " + (GAME_INFO.isShowPacketPayload ? "on" : "off"));
        CommandOutput.sendMessage(sender, "Loop packet logging: " + (GAME_INFO.isShowLoopPackets ? "on" : "off"));
        CommandOutput.sendMessage(sender, "Whitelist: " + new TreeSet<>(currentWhitelist()));
        CommandOutput.sendMessage(sender, "Blacklist: " + new TreeSet<>(currentBlacklist()));
        CommandOutput.sendMessage(sender, "Changes apply immediately and are not saved to config.json.");
    }

    private static Set<Integer> currentWhitelist() {
        return SERVER.debugWhitelist == null ? Set.of() : SERVER.debugWhitelist;
    }

    private static Set<Integer> currentBlacklist() {
        return SERVER.debugBlacklist == null ? Set.of() : SERVER.debugBlacklist;
    }

    private static void report(Player sender, String message) {
        CommandOutput.sendMessage(sender, message);
    }

    static String formatProtoInfo(String selector) {
        String input = selector.strip();
        Integer opcode = parseOpcode(input);
        String messageName;
        String opcodeLabel;
        if (opcode != null) {
            opcodeLabel = opcode.toString();
            messageName = PacketOpcodesUtils.getOpcodeName(opcode);
        } else {
            opcode = PacketOpcodesUtils.getOpcode(input);
            opcodeLabel = opcode >= 0 ? opcode.toString() : "UNKNOWN";
            messageName = input;
        }

        var output = new StringBuilder()
                .append("Opcode: ").append(opcodeLabel)
                .append("\nName: ").append(messageName);
        var descriptor = findDescriptor(messageName);
        if (descriptor == null) {
            return output.append("\nStructure: unavailable (no protobuf definition found for ")
                    .append(messageName).append(").").toString();
        }

        output.append("\nmessage ").append(descriptor.getName()).append(" {");
        for (var field : descriptor.getFields()) {
            output.append("\n  ");
            if (field.isRepeated() && !field.isMapField()) {
                output.append("repeated ");
            }
            output.append(fieldType(field)).append(' ')
                    .append(field.getName()).append(" = ")
                    .append(field.getNumber()).append(';');
        }
        return output.append("\n}").toString();
    }

    private static Integer parseOpcode(String input) {
        try {
            return Integer.valueOf(input);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Descriptors.Descriptor findDescriptor(String messageName) {
        if (messageName == null || messageName.equals("UNKNOWN") || messageName.isBlank()) {
            return null;
        }
        String outerClassName = "emu.grasscutter.net.proto." + messageName + "OuterClass";
        try {
            Class<?> outerClass = Class.forName(outerClassName);
            var fileDescriptor = (Descriptors.FileDescriptor) outerClass.getMethod("getDescriptor").invoke(null);
            return fileDescriptor.findMessageTypeByName(messageName);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                | InvocationTargetException exception) {
            return null;
        }
    }

    private static String fieldType(Descriptors.FieldDescriptor field) {
        if (field.isMapField()) {
            var key = field.getMessageType().findFieldByNumber(1);
            var value = field.getMessageType().findFieldByNumber(2);
            return "map<" + scalarType(key) + ", " + scalarType(value) + ">";
        }
        return scalarType(field);
    }

    private static String scalarType(Descriptors.FieldDescriptor field) {
        return switch (field.getType()) {
            case MESSAGE, GROUP -> field.getMessageType().getName();
            case ENUM -> field.getEnumType().getName();
            default -> field.getType().name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    @CommandLine.Command(name = "info", description = "Show the opcode name and protobuf structure.")
    private static final class Info implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<opcode|name>")
        private String selector;

        private Info(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            report(sender, formatProtoInfo(selector));
        }
    }

    @CommandLine.Command(name = "proto", description = "Show the current runtime packet logging settings.")
    private static final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            showStatus(sender);
        }
    }

    @CommandLine.Command(name = "mode", description = "Set packet logging mode: none, all, whitelist, or blacklist.")
    private static final class Mode implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<mode>")
        private String mode;

        private Mode(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            ServerDebugMode selected = switch (mode.toLowerCase(java.util.Locale.ROOT)) {
                case "none" -> ServerDebugMode.NONE;
                case "all" -> ServerDebugMode.ALL;
                case "whitelist" -> ServerDebugMode.WHITELIST;
                case "blacklist" -> ServerDebugMode.BLACKLIST;
                default -> null;
            };
            if (selected == null) {
                report(sender, "Unknown packet logging mode. Use none, all, whitelist, or blacklist.");
                return;
            }
            GAME_INFO.logPackets = selected;
            report(sender, "Packet logging mode set to " + GAME_INFO.logPackets + ".");
        }
    }

    @CommandLine.Command(name = "payload", description = "Enable or disable decoded packet content logging.")
    private static final class Payload implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<on|off>")
        private String value;

        private Payload(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Boolean enabled = parseToggle(value);
            if (enabled == null) {
                report(sender, "Expected on or off.");
                return;
            }
            GAME_INFO.isShowPacketPayload = enabled;
            report(sender, "Decoded packet content logging " + (enabled ? "enabled." : "disabled."));
        }
    }

    @CommandLine.Command(name = "loops", description = "Enable or disable logging of loop packets.")
    private static final class Loops implements Runnable {
        private final Player sender;

        @Parameters(index = "0", paramLabel = "<on|off>")
        private String value;

        private Loops(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            Boolean enabled = parseToggle(value);
            if (enabled == null) {
                report(sender, "Expected on or off.");
                return;
            }
            GAME_INFO.isShowLoopPackets = enabled;
            report(sender, "Loop packet logging " + (enabled ? "enabled." : "disabled."));
        }
    }

    private abstract static class OpcodeFilter implements Runnable {
        private final Player sender;
        private final boolean whitelist;

        @Parameters(index = "0", paramLabel = "<add|remove>")
        private String action;

        @Parameters(index = "1", paramLabel = "<opcode>")
        private int opcode;

        private OpcodeFilter(Player sender, boolean whitelist) {
            this.sender = sender;
            this.whitelist = whitelist;
        }

        @Override
        public void run() {
            if (opcode <= 0) {
                report(sender, "Opcode must be a positive integer.");
                return;
            }

            Set<Integer> configured = whitelist ? currentWhitelist() : currentBlacklist();
            var updated = new TreeSet<>(configured);
            String normalizedAction = action.toLowerCase(java.util.Locale.ROOT);
            if (!normalizedAction.equals("add") && !normalizedAction.equals("remove")) {
                report(sender, "Expected add or remove.");
                return;
            }
            boolean changed =
                    normalizedAction.equals("add") ? updated.add(opcode) : updated.remove(opcode);

            Set<Integer> result = Set.copyOf(updated);
            if (whitelist) {
                SERVER.debugWhitelist = result;
            } else {
                SERVER.debugBlacklist = result;
            }

            String filterName = whitelist ? "Whitelist" : "Blacklist";
            report(sender, changed
                    ? filterName + (normalizedAction.equals("add") ? " added " : " removed ") + opcode + "."
                    : filterName + " already had no change for opcode " + opcode + ".");
        }
    }

    @CommandLine.Command(name = "whitelist", description = "Add or remove an opcode from the packet whitelist.")
    private static final class Whitelist extends OpcodeFilter {
        private Whitelist(Player sender) {
            super(sender, true);
        }
    }

    @CommandLine.Command(name = "blacklist", description = "Add or remove an opcode from the packet blacklist.")
    private static final class Blacklist extends OpcodeFilter {
        private Blacklist(Player sender) {
            super(sender, false);
        }
    }

    private static Boolean parseToggle(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "on" -> true;
            case "off" -> false;
            default -> null;
        };
    }
}
