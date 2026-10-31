package emu.grasscutter.command.commands;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.command.CommandMap;
import emu.grasscutter.game.AccountUsernamePolicy;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public final class AccountCreateArgumentsTest {
    @Test
    void createArgumentsSupportPasswordlessAndReservedUidForms() {
        var noOptions = AccountCommand.parseCreateArguments(null, null);
        assertNull(noOptions.password());
        assertEquals(0, noOptions.uid());

        var password = AccountCommand.parseCreateArguments("secret", null);
        assertEquals("secret", password.password());
        assertEquals(0, password.uid());

        var uidOnly = AccountCommand.parseCreateArguments("@10001", null);
        assertNull(uidOnly.password());
        assertEquals(10001, uidOnly.uid());

        var both = AccountCommand.parseCreateArguments("secret", 10002);
        assertEquals("secret", both.password());
        assertEquals(10002, both.uid());

        assertThrows(IllegalArgumentException.class,
                () -> AccountCommand.parseCreateArguments("@10001", 10002));
        assertThrows(IllegalArgumentException.class,
                () -> AccountCommand.parseCreateArguments("@invalid", null));
    }

    @Test
    void createParsesAllFourDocumentedForms() {
        var command = new AccountCommand();
        assertDoesNotThrow(() -> command.createCommandLine(null, null)
                .parseArgs("create", "alice"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null)
                .parseArgs("create", "alice", "secret"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null)
                .parseArgs("create", "alice", "@10001"));
        assertDoesNotThrow(() -> command.createCommandLine(null, null)
                .parseArgs("create", "alice", "secret", "@10001"));
    }

    @Test
    void cloneUsesExplicitSourceAndSeparateOptionalUid() {
        var command = new AccountCommand();
        for (String source : List.of("@10001", "alice@", "alice@10001")) {
            assertDoesNotThrow(() -> command.createCommandLine(null, null)
                    .parseArgs("clone", source, "bob"), source);
            assertDoesNotThrow(() -> command.createCommandLine(null, null)
                    .parseArgs("clone", source, "bob", "@10002"), source);
        }
        for (String invalidSource : List.of("alice", "10001", "@0", "alice@other")) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> command.createCommandLine(null, null)
                            .parseArgs("clone", invalidSource, "bob"), invalidSource);
        }
    }

    @Test
    void uidMustBePositiveAndWrittenAsSeparateAtArgument() {
        var command = new AccountCommand();
        for (String invalidUid : List.of("10001", "@0", "@-1", "@abc", "@999999999999999999")) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> command.createCommandLine(null, null)
                            .parseArgs("create", "alice", "secret", invalidUid), invalidUid);
            assertThrows(CommandLine.ParameterException.class,
                    () -> command.createCommandLine(null, null)
                            .parseArgs("clone", "alice@", "bob", invalidUid), invalidUid);
        }
        assertThrows(CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null)
                        .parseArgs("create", "alice", "secret", "@10001", "extra"));
        assertThrows(CommandLine.ParameterException.class,
                () -> command.createCommandLine(null, null)
                        .parseArgs("clone", "alice@", "bob", "@10001", "extra"));
        assertFalse(AccountUsernamePolicy.isValid("bob@10002"));
        assertFalse(AccountUsernamePolicy.isValid("bob@"));
    }

    @Test
    void deleteAndResetPasswordContinueToRequireSelectors() {
        var command = new AccountCommand();
        for (String existing : List.of("@10001", "alice@", "alice@10001")) {
            assertDoesNotThrow(
                    () -> command.createCommandLine(null, null).parseArgs("delete", existing));
            assertDoesNotThrow(
                    () -> command.createCommandLine(null, null)
                            .parseArgs("resetpassword", existing, "newpass"));
            assertDoesNotThrow(
                    () -> command.createCommandLine(null, null)
                            .parseArgs("passwd", existing, "newpass"));
        }
        for (String invalid : List.of("alice", "10001", "@0", "alice@other")) {
            assertThrows(CommandLine.ParameterException.class,
                    () -> command.createCommandLine(null, null).parseArgs("delete", invalid));
            assertThrows(CommandLine.ParameterException.class,
                    () -> command.createCommandLine(null, null)
                            .parseArgs("resetpassword", invalid, "newpass"));
        }
    }

    @Test
    void usageAndAliasesShowRestoredCreateCloneForms() {
        var cli = new AccountCommand().createCommandLine(null, null);
        var createUsage = cli.getSubcommands().get("create").getUsageMessage();
        assertTrue(createUsage.contains("account create <username> [password] [@UID]"));
        assertFalse(createUsage.contains("password|@UID"));

        var cloneUsage = cli.getSubcommands().get("clone").getUsageMessage();
        assertTrue(cloneUsage.contains("account clone <sourceSelector> <newUsername> [@UID]"));

        assertTrue(cli.getSubcommands().get("delete").getUsageMessage()
                .contains("<accountSelector>"));
        assertTrue(cli.getSubcommands().get("resetpassword").getUsageMessage()
                .contains("<accountSelector>"));
        assertSame(cli.getSubcommands().get("resetpassword"), cli.getSubcommands().get("passwd"));
        assertFalse(cli.getSubcommands().containsKey("resetpass"));
        assertDoesNotThrow(() -> cli.parseArgs("passwd", "alice@", "newpass"));
        assertThrows(CommandLine.ParameterException.class,
                () -> cli.parseArgs("resetpass", "alice@", "newpass"));
        assertEquals("alice", CommandMap.parseExplicitTargetSelector("alice@").username());
    }
}
