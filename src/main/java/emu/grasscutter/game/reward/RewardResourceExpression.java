package emu.grasscutter.game.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Typed structure for dollar-variable reward paths and angle-bracket template calls.
 * Pool-call names use the same angle-bracket syntax in every layer.
 */
final class RewardResourceExpression {
    private static final Pattern VARIABLE =
            Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_-]*)(?:\\[([0-9]+)\\])?");
    private static final Pattern ARG_LITERAL = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*|[0-9]+");
    private static final Pattern PATH = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_.-]*");

    private sealed interface Part permits Text, Reference {}
    private record Text(String value) implements Part {}
    private record Reference(String name, Integer index, boolean spread) implements Part {}
    private record Argument(Part value) {}

    private final String original;
    private final List<Part> path;
    private final List<Argument> arguments;
    private final boolean call;

    private RewardResourceExpression(
            String original, List<Part> path, List<Argument> arguments, boolean call) {
        this.original = original;
        this.path = List.copyOf(path);
        this.arguments = List.copyOf(arguments);
        this.call = call;
    }

    static RewardResourceExpression parse(String source, boolean table) {
        int open = source.indexOf('<');
        boolean call = open >= 0;
        if (call && (!table || !source.substring(0, open).contains(".loot.")
                || !source.endsWith(">") || source.indexOf('<', open + 1) >= 0)) {
            throw invalid(source);
        }
        int close = call ? source.length() - 1 : source.length();
        String pathText = source.substring(0, call ? open : close);
        List<Part> path = splitPath(pathText, source);
        List<Argument> args = new ArrayList<>();
        if (call) {
            String rawArgs = source.substring(open + 1, close);
            if (!rawArgs.isBlank()) {
                for (String raw : rawArgs.split(",", -1)) {
                    String text = raw.trim();
                    if (text.startsWith("...")) {
                        Reference ref = reference(text.substring(3), true, source);
                        if (ref.index != null) throw invalid(source);
                        args.add(new Argument(ref));
                    } else if (text.startsWith("$")) {
                        args.add(new Argument(reference(text, false, source)));
                    } else if (ARG_LITERAL.matcher(text).matches()) {
                        args.add(new Argument(new Text(text)));
                    } else {
                        throw invalid(source);
                    }
                }
            }
        }
        if (path.isEmpty()) throw invalid(source);
        return new RewardResourceExpression(source, path, args, call);
    }

    private static Reference reference(String text, boolean spread, String source) {
        Matcher match = VARIABLE.matcher(text);
        if (!match.matches()) throw invalid(source);
        Integer index = null;
        if (match.group(2) != null) {
            try { index = Integer.valueOf(match.group(2)); }
            catch (NumberFormatException ex) { throw invalid(source); }
        }
        return new Reference(match.group(1), index, spread);
    }

    private static List<Part> splitPath(String text, String source) {
        Matcher matcher = VARIABLE.matcher(text);
        var parts = new ArrayList<Part>();
        var check = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            String before = text.substring(cursor, matcher.start());
            if (!before.isEmpty()) parts.add(new Text(before));
            check.append(before).append("placeholder");
            parts.add(reference(matcher.group(), false, source));
            cursor = matcher.end();
        }
        String after = text.substring(cursor);
        if (!after.isEmpty()) parts.add(new Text(after));
        check.append(after);
        if (!PATH.matcher(check.toString()).matches() || text.indexOf('<') >= 0
                || text.indexOf('>') >= 0 || text.indexOf('(') >= 0 || text.indexOf(')') >= 0) {
            throw invalid(source);
        }
        return parts;
    }

    private static IllegalArgumentException invalid(String source) {
        return new IllegalArgumentException("Invalid reward resource expression: " + source);
    }

    boolean hasVariables() {
        for (Part part : path) if (part instanceof Reference) return true;
        for (Argument arg : arguments) if (arg.value instanceof Reference) return true;
        return false;
    }

    void requireDeclared(Set<String> declarations) {
        for (Part part : path) check(part, declarations);
        for (Argument arg : arguments) check(arg.value, declarations);
    }

    private void check(Part part, Set<String> declarations) {
        if (part instanceof Reference ref && !declarations.contains(ref.name)) {
            throw new IllegalArgumentException(
                    "Undeclared reward variable $" + ref.name + " in " + original);
        }
    }

    private List<String> values(Reference reference, Map<String, List<String>> bindings,
                                boolean pathReference) {
        List<String> bound = bindings.get(reference.name);
        if (bound == null) {
            throw new IllegalArgumentException(
                    "Undeclared reward variable $" + reference.name + " in " + original);
        }
        if (bound.isEmpty()) return List.of();
        if (reference.index != null) {
            if (reference.index >= bound.size()) {
                throw new IllegalArgumentException("Reward binding index out of range: " + original);
            }
            return List.of(bound.get(reference.index));
        }
        if (!pathReference && !reference.spread && bound.size() != 1) {
            throw new IllegalArgumentException(
                    "List argument needs ...$name or an explicit index: " + original);
        }
        return bound;
    }

    List<String> expand(Map<String, List<String>> bindings) {
        List<String> names = List.of("");
        for (Part part : path) {
            List<String> fragments = part instanceof Text literal
                    ? List.of(literal.value)
                    : values((Reference) part, bindings, true);
            names = append(names, fragments);
            if (names.isEmpty()) return names;
        }
        if (!call) return names;

        var expanded = new ArrayList<String>();
        for (Argument arg : arguments) {
            if (arg.value instanceof Text literal) {
                expanded.add(literal.value);
            } else {
                Reference ref = (Reference) arg.value;
                List<String> bound = values(ref, bindings, false);
                if (bound.isEmpty()) return List.of();
                // An explicit spread splices the entire argument pack into this call.
                expanded.addAll(bound);
            }
        }
        String suffix = "<" + String.join(",", expanded) + ">";
        return names.stream().map(name -> name + suffix).toList();
    }

    private static List<String> append(List<String> prefix, List<String> values) {
        var result = new ArrayList<String>();
        for (String part : prefix) for (String value : values) result.add(part + value);
        return result;
    }
}
