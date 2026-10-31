package emu.grasscutter.command;

import emu.grasscutter.config.Configuration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites pre-picocli command lines into the current grammar.
 *
 * <p>The picocli port merged many commands (heal into restore, setStats into avatar stat, ...) and
 * replaced the packed {@code lv90 x10 c6} arguments with options. Players and scripts still type the
 * old forms, so before routing they are translated here. Renamed labels only apply when nothing else
 * registered that label, so a plugin's own command always wins. Target selectors such as
 * {@code @10001} pass through every rewrite untouched; the dispatcher removes them afterwards.
 */
final class LegacyCommandSyntax {
    private LegacyCommandSyntax() {}

    /** One flag per packed form, matched against the whole token. Longer prefixes come first. */
    private record Packed(Pattern pattern, String option) {}

    private static Packed packed(String regex, String option) {
        return new Packed(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), option);
    }

    private static final Packed LEVEL = packed("l(?:vl?)?(\\d+)", "--level");
    private static final Packed AMOUNT = packed("x(\\d+)|(\\d+)x", "--amount");
    private static final Packed REFINEMENT = packed("r(\\d+)", "--refinement");
    private static final Packed CONSTELLATION = packed("c(\\d+)", "--constellation");
    private static final Packed SKILL_LEVEL = packed("sl(\\d+)", "--skill-level");
    private static final Packed STATE = packed("state(\\d+)", "--state");
    private static final Packed AI = packed("ai(\\d+)", "--ai");
    private static final Packed MAX_HP = packed("maxhp(\\d+)", "--max-hp");
    private static final Packed HP = packed("hp(\\d+)", "--hp");
    private static final Packed ATK = packed("atk(\\d+)", "--atk");
    private static final Packed DEF = packed("def(\\d+)", "--def");
    private static final Packed BLOCK = packed("blk(\\d+)", "--block");
    private static final Packed GROUP = packed("grp(\\d+)", "--group");
    private static final Packed CONFIG = packed("cfg(\\d+)", "--config");
    private static final Packed RARITY = packed("(\\d+)\\*", "--rarity");

    private static final List<Packed> GIVE = List.of(SKILL_LEVEL, LEVEL, AMOUNT, REFINEMENT, CONSTELLATION);
    private static final List<Packed> SPAWN =
            List.of(STATE, MAX_HP, HP, ATK, DEF, BLOCK, GROUP, CONFIG, AI, LEVEL, AMOUNT);
    private static final List<Packed> ENTITY = List.of(STATE, MAX_HP, HP, ATK, DEF, AI);
    private static final List<Packed> CLEAR = List.of(LEVEL, REFINEMENT, RARITY);

    /** Labels whose whole command moved under another one with the same arguments. */
    private static final Map<String, List<String>> RENAMED = Map.ofEntries(
            Map.entry("item", List.of("give")),
            Map.entry("giveitem", List.of("give")),
            Map.entry("drop", List.of("spawn")),
            Map.entry("killall", List.of("kill", "all")),
            Map.entry("killcharacter", List.of("kill")),
            Map.entry("suicide", List.of("kill")),
            Map.entry("unlockall", List.of("unlock", "all")),
            Map.entry("trialavataractivity", List.of("trial")),
            Map.entry("taa", List.of("trial")),
            Map.entry("er", List.of("restore", "energy")),
            Map.entry("e", List.of("restore", "energy")),
            Map.entry("energy", List.of("restore", "energy")),
            Map.entry("gamespeed", List.of("windy", "speed")),
            Map.entry("speed", List.of("windy", "speed")),
            Map.entry("hideui", List.of("windy", "hide-ui")),
            Map.entry("hui", List.of("windy", "hide-ui")),
            Map.entry("nofog", List.of("windy", "no-fog")),
            Map.entry("fog", List.of("windy", "no-fog")),
            Map.entry("removecensorship", List.of("windy", "remove-censorship")),
            Map.entry("cancer", List.of("windy", "remove-censorship")),
            Map.entry("rc", List.of("windy", "remove-censorship")),
            Map.entry("fps", List.of("windy", "fps")),
            Map.entry("bpbuy", List.of("battlepass", "buy")),
            Map.entry("bppaid", List.of("battlepass", "paid")),
            Map.entry("enter_dungeon", List.of("dungeon")),
            Map.entry("enterdungeon", List.of("dungeon")),
            Map.entry("sceneteleporter", List.of("teleport", "scene")),
            Map.entry("sceneteleport", List.of("teleport", "scene")),
            Map.entry("scene", List.of("teleport", "scene")),
            Map.entry("sc", List.of("teleport", "scene")),
            Map.entry("teleportall", List.of("teleport", "all")),
            Map.entry("tpall", List.of("teleport", "all")),
            Map.entry("sendmessage", List.of("say")),
            Map.entry("sendservmsg", List.of("say")),
            Map.entry("sendservermessage", List.of("say")),
            Map.entry("broadcast", List.of("say")),
            Map.entry("b", List.of("say")),
            Map.entry("setfetterlevel", List.of("avatar", "friendship", "set")),
            Map.entry("setfetterlvl", List.of("avatar", "friendship", "set")),
            Map.entry("setfriendship", List.of("avatar", "friendship", "set")),
            Map.entry("pierro", List.of("support", "pierro")),
            Map.entry("ronova", List.of("support", "pierro")),
            Map.entry("setprop", List.of("prop")),
            Map.entry("troubleshoot", List.of("info")),
            Map.entry("helpme", List.of("info")),
            Map.entry("pb", List.of("barrier")),
            Map.entry("setnamecard", List.of("namecard")),
            Map.entry("nickname", List.of("name")),
            Map.entry("mattrack", List.of("trackmat")),
            Map.entry("unlockwp", List.of("waypoints")),
            Map.entry("restart", List.of("kick")));

    /**
     * The current command line for an old label that no longer exists, or null when {@code label}
     * was never an old command. {@code args} excludes the label.
     */
    static List<String> rewriteRemovedLabel(String label, List<String> args) {
        var renamed = RENAMED.get(label);
        if (renamed != null) return concat(renamed, args);

        return switch (label) {
            // "list uid" asked for UIDs, which player list always shows.
            case "list", "players" -> List.of("player", "list");
            case "heal", "h" -> withFlagWord(List.of("restore", "hp"), args, "all", "--all");
            case "max", "maxavatar", "maxchar" -> withFlagWord(List.of("avatar", "max"), args, "all", "--all");
            case "setconst", "setconstellation" ->
                    withFlagWord(List.of("avatar", "constellation", "set"), args, "all", "--all");
            case "resetconst", "resetconstellation" ->
                    withFlagWord(List.of("avatar", "constellation", "reset"), args, "all", "--all");
            case "extralevel", "el", "levelbreak" -> avatarOption(List.of("avatar", "extralevel"), args);
            case "talent" -> talent(args);
            case "setstats", "stats", "stat" -> stats(args);
            case "uid" -> concat(List.of("windy", "uid"), args.isEmpty() ? args : dropFirstIf(args, "set"));
            // The old password check is replaced by the server.banip permission.
            case "banip" -> concat(List.of("ban"), dropFirstPositional(args));
            case "unbanip" -> concat(List.of("unban"), dropFirstPositional(args));
            case "sysmail", "systemmail", "smail" -> sysmail(args);
            default -> null;
        };
    }

    /**
     * The command line for an old use of a current built-in command that now belongs to another
     * command, or null when the use is still that command's own.
     */
    static List<String> rerouteBuiltIn(String label, List<String> args) {
        return label.equals("prop") ? prop(args) : null;
    }

    /** Translates old argument forms of a current built-in command; returns {@code args} if none apply. */
    static List<String> rewriteArguments(String label, List<String> args) {
        return switch (label) {
            case "give" -> give(args);
            case "spawn" -> spawn(args);
            case "entity" -> unpack(args, ENTITY);
            case "clear" -> unpack(args, CLEAR);
            case "dps" -> dps(args);
            case "announce" -> announce(args);
            case "achievement" -> achievement(args);
            case "setscenetag" -> replaceFirst(args, "unlockall", List.of("unlock", "all"));
            case "account" -> account(args);
            case "coop" -> coop(args);
            // GrasscutterTools sends "kill 0"; the old kill ignored its argument.
            case "kill" -> args.size() == 1 && isDigits(args.get(0)) ? List.of() : args;
            default -> args;
        };
    }

    private static List<String> give(List<String> args) {
        var unpacked = unpack(args, GIVE);
        // An artifact's trailing positionals were its main stat, then substats as id[,rolls].
        var positionals = positionalIndexes(unpacked);
        if (positionals.size() < 2 || !isDigits(unpacked.get(positionals.get(0)))) return unpacked;
        var out = new ArrayList<String>(unpacked.size() + positionals.size());
        int seen = 0;
        for (int i = 0; i < unpacked.size(); i++) {
            String token = unpacked.get(i);
            if (!positionals.contains(i)) {
                out.add(token);
                continue;
            }
            switch (seen++) {
                case 0 -> out.add(token);
                case 1 -> out.add("--main-stat=" + token);
                default -> out.add("--substat=" + token);
            }
        }
        return out;
    }

    private static List<String> spawn(List<String> args) {
        var unpacked = unpack(args, SPAWN);
        // Old form: <id> [<x> <y> <z>] [<rotX> <rotY> <rotZ>] as bare trailing positionals.
        var positionals = positionalIndexes(unpacked);
        int coordinates = positionals.size() - 1;
        if (coordinates != 3 && coordinates != 6) return unpacked;
        var out = new ArrayList<String>(unpacked.size() + 2);
        var coords = new ArrayList<String>(coordinates);
        for (int i = 0; i < unpacked.size(); i++) {
            if (positionals.contains(i) && i != positionals.get(0)) coords.add(unpacked.get(i));
            else out.add(unpacked.get(i));
        }
        out.add("--pos");
        out.addAll(coords.subList(0, 3));
        if (coordinates == 6) {
            out.add("--rot");
            out.addAll(coords.subList(3, 6));
        }
        return out;
    }

    private static List<String> dps(List<String> args) {
        if (args.isEmpty()) return List.of("start");
        String first = args.get(0).toLowerCase(Locale.ROOT);
        if (first.equals("start") || first.equals("stop") || first.startsWith("-")) return args;
        var out = new ArrayList<String>(args.size() + 1);
        out.add("start");
        for (String token : args) {
            Matcher amount = AMOUNT.pattern().matcher(token);
            out.add(amount.matches() ? firstGroup(amount) : token);
        }
        return out;
    }

    private static List<String> announce(List<String> args) {
        if (args.isEmpty()) return args;
        return switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "send", "template", "tpl", "refresh", "revoke" -> args;
            default -> args.get(0).startsWith("-") ? args : concat(List.of("send"), args);
        };
    }

    private static List<String> achievement(List<String> args) {
        if (args.isEmpty()) return args;
        return switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "grantall" -> concat(List.of("grant", "all"), args.subList(1, args.size()));
            case "revokeall" -> concat(List.of("revoke", "all"), args.subList(1, args.size()));
            default -> args;
        };
    }

    private static List<String> account(List<String> args) {
        if (args.size() < 2) return args;
        String action = args.get(0).toLowerCase(Locale.ROOT);
        var out = new ArrayList<>(args);
        switch (action) {
            case "create" -> {
                // Without real passwords the old third argument was the UID to reserve.
                if (args.size() == 3
                        && !Configuration.ACCOUNT.EXPERIMENTAL_RealPassword
                        && isDigits(args.get(2))) {
                    out.set(2, "@" + args.get(2));
                } else if (args.size() == 4 && isDigits(args.get(3))) {
                    out.set(3, "@" + args.get(3));
                }
            }
            case "resetpass" -> {
                out.set(0, "resetpassword");
                out.set(1, asUsername(args.get(1)));
            }
            case "delete", "resetpassword", "passwd" -> out.set(1, asUsername(args.get(1)));
            default -> {
                return args;
            }
        }
        return out;
    }

    /** Open states and the map unlock were player properties before they got commands of their own. */
    private static List<String> prop(List<String> args) {
        if (args.size() != 2) return null;
        return switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "setopenstate", "so" -> List.of("openstate", args.get(1), "true");
            case "unsetopenstate", "uo" -> List.of("openstate", args.get(1), "false");
            case "unlockmap", "um" -> List.of("unlock", "map");
            default -> null;
        };
    }

    /** The old coop took a bare host UID. */
    private static List<String> coop(List<String> args) {
        if (args.size() == 1 && isDigits(args.get(0))) return List.of("@" + args.get(0));
        return args;
    }

    private static List<String> talent(List<String> args) {
        if (args.isEmpty()) return List.of("avatar", "talent");
        String sub = args.get(0).toLowerCase(Locale.ROOT);
        var rest = args.subList(1, args.size());
        return switch (sub) {
            case "getid" -> concat(List.of("avatar", "talent", "list"), rest);
            case "n", "e", "q", "all", "set", "normal", "skill", "burst", "list" ->
                    concat(List.of("avatar", "talent", sub), rest);
            default -> concat(List.of("avatar", "talent"), args);
        };
    }

    private static List<String> stats(List<String> args) {
        if (args.isEmpty()) return List.of("avatar", "stat");
        String sub = args.get(0).toLowerCase(Locale.ROOT);
        var rest = args.subList(1, args.size());
        return switch (sub) {
            case "set" -> concat(List.of("avatar", "stat", "set"), rest);
            case "lock", "freeze" -> concat(List.of("avatar", "stat", "lock"), rest);
            case "unlock", "unfreeze" -> concat(List.of("avatar", "stat", "unlock"), rest);
            // "[set]" was optional: setStats hp 1000.
            default -> concat(List.of("avatar", "stat", "set"), args);
        };
    }

    private static List<String> sysmail(List<String> args) {
        if (args.isEmpty()) return List.of("mail", "system");
        String sub = args.get(0).toLowerCase(Locale.ROOT);
        var rest = args.subList(1, args.size());
        return switch (sub) {
            case "update", "retitle", "delete" -> concat(List.of("mail", "system", sub), rest);
            case "deleteall" -> concat(List.of("mail", "system", "clear"), rest);
            default -> List.of("mail", "system");
        };
    }

    /** {@code extralevel 10000002} named the character positionally. */
    private static List<String> avatarOption(List<String> prefix, List<String> args) {
        var out = new ArrayList<>(prefix);
        for (String token : args) {
            out.add(isDigits(token) ? "--avatar=" + token : token);
        }
        return out;
    }

    private static List<String> withFlagWord(List<String> prefix, List<String> args, String word, String flag) {
        var out = new ArrayList<>(prefix);
        for (String token : args) {
            out.add(token.equalsIgnoreCase(word) ? flag : token);
        }
        return out;
    }

    private static List<String> unpack(List<String> args, List<Packed> forms) {
        List<String> out = null;
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (isPassThrough(token)) continue;
            for (Packed form : forms) {
                Matcher match = form.pattern().matcher(token);
                if (!match.matches()) continue;
                if (out == null) out = new ArrayList<>(args.subList(0, i));
                out.add(form.option() + "=" + firstGroup(match));
                token = null;
                break;
            }
            if (out != null && token != null) out.add(token);
        }
        return out == null ? args : out;
    }

    /** Bare tokens that picocli will bind as positionals. */
    private static List<Integer> positionalIndexes(List<String> args) {
        var indexes = new ArrayList<Integer>();
        for (int i = 0; i < args.size(); i++) {
            if (!isPassThrough(args.get(i))) indexes.add(i);
        }
        return indexes;
    }

    /** Options and target selectors are never packed values or positionals. */
    private static boolean isPassThrough(String token) {
        return (token.startsWith("-") && token.length() > 1 && !isNumber(token))
                || CommandMap.isTargetSelector(token);
    }

    private static String firstGroup(Matcher match) {
        for (int g = 1; g <= match.groupCount(); g++) {
            if (match.group(g) != null) return match.group(g);
        }
        return match.group();
    }

    private static List<String> replaceFirst(List<String> args, String old, List<String> replacement) {
        if (args.isEmpty() || !args.get(0).equalsIgnoreCase(old)) return args;
        return concat(replacement, args.subList(1, args.size()));
    }

    private static List<String> dropFirstIf(List<String> args, String word) {
        return !args.isEmpty() && args.get(0).equalsIgnoreCase(word) ? args.subList(1, args.size()) : args;
    }

    private static List<String> dropFirstPositional(List<String> args) {
        var out = new ArrayList<>(args);
        for (int i = 0; i < out.size(); i++) {
            if (!CommandMap.isTargetSelector(out.get(i))) {
                out.remove(i);
                break;
            }
        }
        return out;
    }

    private static String asUsername(String token) {
        return token.contains("@") || isDigits(token) ? token : token + "@";
    }

    private static boolean isDigits(String token) {
        return !token.isEmpty() && token.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    private static boolean isNumber(String token) {
        try {
            Double.parseDouble(token);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static List<String> concat(List<String> prefix, List<String> rest) {
        var out = new ArrayList<String>(prefix.size() + rest.size());
        out.addAll(prefix);
        out.addAll(rest);
        return out;
    }
}
