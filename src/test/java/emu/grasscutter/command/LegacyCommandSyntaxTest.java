package emu.grasscutter.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;
import picocli.CommandLine;

/** Pre-picocli command lines, as players typed them, must still reach a command that accepts them. */
class LegacyCommandSyntaxTest {
    private static CommandMap map;

    @BeforeAll
    static void registerBuiltIns() throws Exception {
        map = new CommandMap(false);
        var handlers = new ArrayList<CommandHandler>();
        for (Class<?> type : new Reflections("emu.grasscutter.command.commands").getTypesAnnotatedWith(Command.class)) {
            handlers.add((CommandHandler) type.getDeclaredConstructor().newInstance());
        }
        map.registerCommands(handlers);
    }

    /** Mirrors CommandMap.invoke up to the point where picocli takes over. */
    private static List<String> route(String line) {
        var tokens = new ArrayList<>(Arrays.asList(line.split(" ")));
        String label = tokens.remove(0).toLowerCase(Locale.ROOT);
        List<String> args = tokens;
        if (map.getHandler(label) == null) {
            var rewritten = LegacyCommandSyntax.rewriteRemovedLabel(label, args);
            if (rewritten != null) {
                label = rewritten.get(0);
                args = rewritten.subList(1, rewritten.size());
            }
        }
        var handler = map.getHandler(label);
        assertTrue(handler != null, () -> "no command for: " + line);
        String resolved = handler.getLabel().toLowerCase(Locale.ROOT);
        var rerouted = LegacyCommandSyntax.rerouteBuiltIn(resolved, args);
        if (rerouted != null && map.getHandler(rerouted.get(0)) != null) {
            handler = map.getHandler(rerouted.get(0));
            resolved = handler.getLabel().toLowerCase(Locale.ROOT);
            args = rerouted.subList(1, rerouted.size());
        }
        args = new ArrayList<>(LegacyCommandSyntax.rewriteArguments(resolved, args));
        if (handler.getClass().getAnnotation(Command.class).inlineTarget()) {
            args.removeIf(CommandMap::isTargetSelector);
        }
        var out = new ArrayList<String>();
        out.add(resolved);
        out.addAll(args);
        return out;
    }

    private static void assertParses(String line) {
        var routed = route(line);
        var cli = map.getHandler(routed.get(0)).createCompletionCommandLine();
        cli.setSubcommandsCaseInsensitive(true);
        try {
            cli.parseArgs(routed.subList(1, routed.size()).toArray(String[]::new));
        } catch (CommandLine.ParameterException e) {
            throw new AssertionError(line + " -> " + routed + ": " + e.getMessage(), e);
        }
    }

    @Test
    void everyOldFormStillParses() {
        String[] lines = {
            "give 1001 lv90 c6 sl10", "give 11509 lv90 r5 x2", "give 101 100x", "give @10001 1001 lv90",
            "give 81234 lv20 15001 501021,2 501031", "item 101 x1", "giveitem 101",
            "spawn 21010101 x5 lv90 hp0 maxhp100 atk10 def10 ai1", "drop 101",
            "spawn 70220005 state0 blk1 grp2 cfg3 ~ ~1 ~ 0 90 0", "spawn 21010101 100 200 300",
            "entity 123 state1 maxhp10 hp0 atk1 def1", "entity 123 ai2",
            "clear wp lv90 r5 4*", "clear all",
            "dps", "dps 30 x3", "dps stop",
            "announce hello world", "announce tpl 1", "announce refresh",
            "achievement grantall", "achievement revokeall", "am grant 80001",
            "setSceneTag unlockall", "tag add 3",
            "account create bob 10002", "account delete bob", "account resetpass bob pw",
            "coop 10001",
            "heal", "heal all", "h",
            "killall", "killall 3", "killCharacter", "suicide",
            "list", "players",
            "max", "max all", "maxavatar",
            "setConst 6", "setConst 6 all", "resetConst all", "resetConst",
            "extralevel 10000002", "el",
            "setFetterLevel 10",
            "talent n 10", "talent set 10024 9", "talent getid", "talent all 10", "talent q 8",
            "setStats hp 1000", "stats set atk 50", "stat lock hp", "stat freeze hp 100", "stat unlock hp",
            "unlockall",
            "taa change 1", "trialAvatarActivity toggleDungeon all", "taa toggleReward 1",
            "ER", "energy",
            "GameSpeed 2.0", "speed 0.5", "HideUI", "NoFog", "RemoveCensorship", "fps",
            "uid set Hello world", "uid default",
            "bpbuy 10", "bppaid on",
            "enter_dungeon 5000", "enterdungeon 5000", "sceneteleporter 3", "scene 3", "sc 3",
            "teleportAll", "tpall",
            "sendMessage hi there", "b hi", "broadcast hi",
            "banip pw 1.2.3.4 cheating", "unbanip pw 1.2.3.4",
            "sysmail update kw body text", "sysmail deleteall",
            "pierro on", "ronova off",
            "setProp god on", "prop god",
            "teapot setLevel 10", "teapot unlockModule 2", "teapot giveAllFurniture 1",
            "tp 100 200 300", "tp ~ ~10 ~", "tp 3", "tp 100 200 300 3",
            "windy myscript.luac", "windy uid", "windy speed 2.0",
            "weather 1", "barrier 1", "pb 0",
            "namecard 210001", "setnamecard 210001", "nickname Asta", "mattrack 101", "unlockwp",
            "troubleshoot", "helpme", "restart @10001",
        };
        var failures = new ArrayList<String>();
        for (String line : lines) {
            try {
                assertParses(line);
            } catch (AssertionError e) {
                failures.add(e.getMessage());
            }
        }
        assertTrue(failures.isEmpty(), () -> "Old forms that no longer parse:\n" + String.join("\n", failures));
    }

    /** What GrasscutterTools (jie65535/grasscuttercommandgenerator v1.20.3) writes for each of its pages. */
    @Test
    void grasscutterToolsCommandsParse() {
        String[] lines = {
            // PageGiveItem / PageGiveWeapon / PageAvatar / PageArtifact / GOOD import
            "give 1001 lv90 c6 sl10", "give 1001 lv90 c6", "give avatars lv90 c6 sl10", "give avatars lv90 c6",
            "give weapons x1 lv90 r5", "give 11509 x1 lv90 r5", "give 101 x10 lv1",
            "give 81234 lv20 15001 501021,2 501031,1", "give 81234 lv20",
            "give all x9999 lv90 c6 r5 sl10", "give 102 x1880200",
            // PageSpawn
            "spawn 21010101 x5 lv90", "spawn 2008 x25", "spawn 21010101 x1 lv90 hp100 maxhp100 atk10 def10 ai1",
            "spawn 70220005 x1 state201 100 200 300",
            // PageTasks / CustomCommands
            "prop god on", "prop ns on", "prop ue on", "prop wl 8", "prop unlockmap 1", "prop ut 12", "prop bp 50",
            "prop fly on", "prop is_game_time_locked on", "prop is_weather_locked on", "prop SetOpenState 47",
            "prop UnsetOpenState 48", "prop bplevel 10", "prop towerlevel 8", "prop worldlevel 8",
            "setfetterlevel 10", "h", "kill 0", "pos", "reload", "resetconst", "resetconst all",
            "clear all lv90 r5 5*", "clear wp lv90 r5 5*", "clear art lv20 5*", "clear mat",
            "killall", "tpall", "list uid", "say hi", "a welcome", "stop", "unlockall", "troubleshoot",
            "setstats cdr 100%", "setstats lock hp 100", "setstats unlock hp", "setstats atk 50",
            // PageScene / PageWeather / PageAchievement / PageQuest / PageSceneTag
            "tp 0 400 0 3", "tp ~ ~10 ~", "tp ~ ~100 ~", "tp ^10 ^ ^", "tp ^ ^ ^-10", "scene 3", "dungeon 5001",
            "cutscene 101", "weather", "weather 0 1", "weather sunny",
            "achievement grantall", "achievement revokeall", "achievement grant 80001", "achievement revoke 80001",
            "achievement progress 80001 3", "quest add 35104", "quest finish 35104",
            "tag unlockall", "tag reset", "tag add 1", "tag remove 1",
            "talent n 10", "talent e 10", "talent q 10", "talent all 10", "setConst 6", "setConst 6 all",
            "se pyro",
            // PageManagement / PagePermission / PageMail
            "account create bob", "account create bob 10002", "account delete bob",
            "permission add @10001 player.give", "unban @10001", "ban @10001 1999999999",
            "sendMail all", "sendMail 10001", "sendMail finish",
        };
        var failures = new ArrayList<String>();
        for (String line : lines) {
            try {
                assertParses(line);
            } catch (AssertionError e) {
                failures.add(e.getMessage());
            }
        }
        assertTrue(failures.isEmpty(), () -> "GrasscutterTools commands that do not parse:\n" + String.join("\n", failures));
    }

    @Test
    void noCommandHasSubcommandsThatDifferOnlyInCase() {
        for (var handler : map.getHandlersAsList()) {
            handler.createCompletionCommandLine().setSubcommandsCaseInsensitive(true);
        }
    }

    @Test
    void packedArgumentsBecomeOptions() {
        assertEquals(List.of("give", "1001", "--level=90", "--constellation=6", "--skill-level=10"),
                route("give 1001 lv90 c6 sl10"));
        assertEquals(List.of("give", "81234", "--level=20", "--main-stat=15001", "--substat=501021,2",
                        "--substat=501031"),
                route("give 81234 lv20 15001 501021,2 501031"));
        assertEquals(List.of("spawn", "70220005", "--state=0", "--pos", "~", "~1", "~", "--rot", "0", "90", "0"),
                route("spawn 70220005 state0 ~ ~1 ~ 0 90 0"));
        assertEquals(List.of("clear", "weapons", "--level=90", "--rarity=4"), route("clear weapons lv90 4*"));
        assertEquals(List.of("dps", "start", "30", "3"), route("dps 30 x3"));
    }

    @Test
    void renamedCommandsKeepTheirArguments() {
        assertEquals(List.of("restore", "hp", "--all"), route("heal all"));
        assertEquals(List.of("avatar", "stat", "set", "hp", "1000"), route("setStats hp 1000"));
        assertEquals(List.of("avatar", "extralevel", "--avatar=10000002"), route("extralevel 10000002"));
        assertEquals(List.of("ban", "1.2.3.4", "cheating"), route("banip pw 1.2.3.4 cheating"));
        assertEquals(List.of("windy", "uid", "Hello", "world"), route("uid set Hello world"));
        assertEquals(List.of("account", "resetpassword", "bob@", "pw"), route("account resetpass bob pw"));
        assertEquals(List.of("openstate", "47", "true"), route("prop SetOpenState 47"));
        assertEquals(List.of("unlock", "map"), route("prop unlockmap 1"));
        assertEquals(List.of("player", "list"), route("list uid"));
        assertEquals(List.of("kill"), route("kill 0"));
    }

    @Test
    void currentFormsAreLeftAlone() {
        var args = List.of("1001", "--level=90");
        assertTrue(LegacyCommandSyntax.rewriteArguments("give", args) == args);
        assertNull(LegacyCommandSyntax.rewriteRemovedLabel("nonsense", List.of()));
        // A non-numeric give target with a stray word is not an artifact.
        assertEquals(List.of("give", "Hu", "Tao"), route("give Hu Tao"));
    }
}
