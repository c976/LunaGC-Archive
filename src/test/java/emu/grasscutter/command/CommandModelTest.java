package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

public final class CommandModelTest {
    @Test
    @DisplayName("every built-in command has a valid unique registration")
    public void everyCommandRegistersWithoutNameCollisions() {
        var failures = new ArrayList<String>();
        var handlers = new ArrayList<CommandHandler>();
        var commandMap = new CommandMap(false);

        for (Class<?> commandType : getCommandTypes()) {
            try {
                Object instance = commandType.getDeclaredConstructor().newInstance();
                if (instance instanceof CommandHandler handler) {
                    handlers.add(handler);
                } else {
                    failures.add(commandType.getName() + ": does not implement CommandHandler");
                }
            } catch (Throwable failure) {
                failures.add(commandType.getName() + ": " + failure);
            }
        }

        if (failures.isEmpty()) {
            try {
                commandMap.registerCommands(handlers);
            } catch (Throwable failure) {
                failures.add("batch registration: " + failure);
            }
        }

        assertTrue(
                failures.isEmpty(),
                () -> "Invalid command registry entries:\n" + String.join("\n", failures));
    }

    private static ArrayList<Class<?>> getCommandTypes() {
        var reflections = new Reflections("emu.grasscutter.command.commands");
        var commandTypes = new ArrayList<>(reflections.getTypesAnnotatedWith(Command.class));
        commandTypes.sort(Comparator.comparing(Class::getName));
        return commandTypes;
    }
}
