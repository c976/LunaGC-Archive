# Plugin command API v5

AstaPS currently requires plugin API `5`. The picocli-only command contract was introduced in API v4 and remains the command contract in v5. API v5 was bumped for the Javalin 7 routing API, so plugins must still be rebuilt with `"api": 5` even when they only use commands.

## Command API

- `CommandHandler` describes a picocli command tree through `createCommandLine(Player sender, Player targetPlayer)`.
- The old `execute(Player, Player, List<String>)` command contract is removed.
- Command output helpers live in `CommandOutput`.
- `ServerHelper.registerCommand(...)` accepts the current `CommandHandler` directly.
- Parsing, generated usage, JLine completion, and parse/execution error handling all go through the same picocli path.
- Picocli argument-file expansion is disabled because AstaPS reserves `@...` for player/UID targeting.
- Command labels and aliases must be unique, case-insensitively. Registration fails on collisions instead of silently replacing an existing route.

Set `"api": 5` in `plugin.json` and rebuild the plugin against the current server API.

## Minimal migration

Legacy command style:

```java
@Command(label = "foo")
public final class FooCommand implements CommandHandler {
    @Override
    public void execute(Player sender, Player targetPlayer, List<String> args) {
        CommandHandler.sendMessage(sender, "hello");
    }
}
```

Current command style:

```java
@Command(label = "foo")
public final class FooCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine((Runnable) () -> CommandOutput.sendMessage(sender, "hello"));
    }
}
```

Registration remains straightforward:

```java
ServerHelper.getInstance().registerCommand(new FooCommand());
```

For real commands, prefer typed picocli `@Parameters`, `@Option`, subcommands, and converters instead of parsing a `List<String>` manually.

## Target selectors

AstaPS may consume a bare `@UID` argument as the command target before picocli parses command-local arguments. Commands that use `@UID` as their own argument must opt out:

```java
@Command(label = "account", inlineTarget = false)
```

With `inlineTarget = false`, command-local values such as `account clone source target @123` remain in the picocli argument list.

The standalone target command accepts both `target 123` and `target @123` for compatibility.
