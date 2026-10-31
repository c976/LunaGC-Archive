package emu.grasscutter.game.reward;

import emu.grasscutter.Grasscutter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Parser for the reward stylesheet language. This is deliberately a small
 * independent grammar, not a CSS browser compatibility implementation.
 */
final class RewardCssParser {
    record Declaration(String name, String value) {}
    record Rule(List<Selector> selectors, List<Declaration> declarations) {}

    record Selector(String type, List<Condition> predicates) {
        Selector {
            predicates = List.copyOf(predicates);
        }

        boolean matches(String objectType, Map<String, ?> attributes) {
            if (!type.equals(objectType)) return false;
            for (Condition predicate : predicates) {
                if (!predicate.matches(attributes)) return false;
            }
            return true;
        }

        // Only type and attribute selectors exist in this grammar.
        int specificity() { return predicates.size(); }
    }

    sealed interface Condition permits Exists, Missing, Compare, Between, In {
        boolean matches(Map<String, ?> attributes);
    }

    record Exists(String name) implements Condition {
        public boolean matches(Map<String, ?> values) {
            return values.containsKey(name);
        }
    }

    record Missing(String name) implements Condition {
        public boolean matches(Map<String, ?> values) {
            return !values.containsKey(name);
        }
    }

    record Literal(Object value) {
        boolean isNumber() { return value instanceof Integer; }

        boolean matches(Object actual) { return value.equals(actual); }

        Integer compare(Object actual) {
            return isNumber() && actual instanceof Integer number
                    ? Integer.compare(number, (Integer) value) : null;
        }
    }

    record Compare(String name, String operator, Literal value) implements Condition {
        public boolean matches(Map<String, ?> attributes) {
            Object actual = attributes.get(name);
            if (actual == null) return false;
            if (operator.equals("=")) return value.matches(actual);
            if (operator.equals("!=")) return !value.matches(actual);
            Integer comparison = value.compare(actual);
            if (comparison == null) return false;
            return switch (operator) {
                case "<" -> comparison < 0;
                case "<=" -> comparison <= 0;
                case ">" -> comparison > 0;
                case ">=" -> comparison >= 0;
                default -> throw new IllegalStateException("Unsupported operator: " + operator);
            };
        }
    }

    record Between(String name, Literal low, boolean lowInclusive,
                   Literal high, boolean highInclusive) implements Condition {
        public boolean matches(Map<String, ?> attributes) {
            Object actual = attributes.get(name);
            Integer lower = low.compare(actual), upper = high.compare(actual);
            return lower != null && upper != null
                    && (lower > 0 || lower == 0 && lowInclusive)
                    && (upper < 0 || upper == 0 && highInclusive);
        }
    }

    record In(String name, List<Literal> values) implements Condition {
        In { values = List.copyOf(values); }
        public boolean matches(Map<String, ?> attributes) {
            Object actual = attributes.get(name);
            if (actual == null) return false;
            for (Literal value : values) if (value.matches(actual)) return true;
            return false;
        }
    }

    private static final Pattern NUMBER = Pattern.compile("-?[0-9]+");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
    private static final Pattern PROPERTY = Pattern.compile(
            "(?:--[A-Za-z_][A-Za-z0-9_-]*|genshin\\.[A-Za-z0-9_.$\\[\\]-]+(?:\\.[A-Za-z0-9_.$\\[\\]-]+)*|[a-z][a-z0-9_]*\\.loot\\..+)");
    private final String source;
    private final Consumer<String> warnings;
    private int position;

    private RewardCssParser(String source, Consumer<String> warnings) {
        this.source = Objects.requireNonNull(source);
        this.warnings = Objects.requireNonNull(warnings);
    }

    static List<Rule> parse(String source) {
        return parse(source, message -> Grasscutter.getLogger().warn(message));
    }

    static List<Rule> parse(String source, Consumer<String> warnings) {
        var parser = new RewardCssParser(source, warnings);
        var result = new ArrayList<Rule>();
        parser.trivia();
        while (!parser.end()) {
            List<Selector> selectors = parser.selectors(false);
            parser.trivia();
            parser.expect('{');
            parser.block(selectors, result, false, -1);
            parser.trivia();
        }
        return List.copyOf(result);
    }

    // Flatten nested rules in source order. Each '&[condition]' appends predicates
    // to every parent selector, preserving the existing cascade and specificity.
    private void block(List<Selector> parents, List<Rule> result,
                       boolean nested, int selectorPosition) {
        var declarations = new ArrayList<Declaration>();
        boolean hasOwnDeclarations = false, hasNestedRules = false;
        trivia();
        while (!take('}')) {
            if (end()) throw error("Missing '}'");
            if (peek('&')) {
                hasNestedRules = true;
                if (!declarations.isEmpty()) {
                    result.add(new Rule(parents, List.copyOf(declarations)));
                    declarations.clear();
                }
                int childPosition = position;
                var children = selectors(true);
                var expanded = new ArrayList<Selector>();
                for (Selector parent : parents) {
                    for (Selector child : children) {
                        var predicates = new ArrayList<>(parent.predicates());
                        predicates.addAll(child.predicates());
                        expanded.add(new Selector(parent.type(), predicates));
                    }
                }
                trivia();
                expect('{');
                block(List.copyOf(expanded), result, true, childPosition);
            } else {
                declarations.add(declaration());
                hasOwnDeclarations = true;
            }
            trivia();
        }
        if (!declarations.isEmpty()) {
            result.add(new Rule(parents, List.copyOf(declarations)));
        }
        if (nested && hasNestedRules && !hasOwnDeclarations) {
            warnings.accept("LootCSS: " + location(selectorPosition)
                    + " nested selector has no direct declarations");
        }
    }

    private List<Selector> selectors(boolean nested) {
        var result = new ArrayList<Selector>();
        do {
            trivia();
            String type;
            if (nested) {
                expect('&');
                type = "";
            } else {
                if (peek('&')) throw error("'&' requires a parent selector");
                type = identifier();
            }
            var predicates = new ArrayList<Condition>();
            trivia();
            while (take('[')) {
                trivia();
                predicates.add(condition());
                trivia();
                expect(']');
                trivia();
            }
            if (nested && predicates.isEmpty()) {
                throw error("Nested '&' must append at least one attribute predicate");
            }
            result.add(new Selector(type, predicates));
            if (!take(',')) break;
        } while (true);
        return List.copyOf(result);
    }

    private Condition condition() {
        if (take('!')) {
            trivia();
            String name = identifier();
            checkAttribute(name);
            return new Missing(name);
        }
        String left = atom();
        boolean leftNumber = NUMBER.matcher(left).matches();
        trivia();
        if (peek(']')) {
            if (leftNumber) throw error("Expected an attribute name");
            checkAttribute(left);
            return new Exists(left);
        }
        if (word("in")) {
            if (leftNumber) throw error("Set membership requires an attribute name");
            checkAttribute(left);
            trivia();
            expect('(');
            var values = new ArrayList<Literal>();
            do {
                trivia();
                Literal value = literal();
                checkLiteral(left, value);
                values.add(value);
                trivia();
                if (!take(',')) break;
            } while (true);
            expect(')');
            return new In(left, values);
        }
        String comparison = operator();
        trivia();
        if (leftNumber) {
            if (!comparison.equals("<") && !comparison.equals("<=")) {
                throw error("Range lower bound must use '<' or '<='");
            }
            String name = identifier();
            checkAttribute(name);
            if (RewardCssAttributes.typeOf(name) != RewardCssAttributes.Type.INTEGER) {
                throw error("Numeric range requires an integer attribute: " + name);
            }
            trivia();
            String upperOperator = operator();
            if (!upperOperator.equals("<") && !upperOperator.equals("<=")) {
                throw error("Range upper bound must use '<' or '<='");
            }
            trivia();
            Literal high = literal();
            checkLiteral(name, high);
            Literal low = numeric(left);
            if ((Integer) low.value > (Integer) high.value) {
                throw error("Reversed numeric range");
            }
            return new Between(name, low, comparison.equals("<="),
                    high, upperOperator.equals("<="));
        }
        checkAttribute(left);
        Literal value = literal();
        checkLiteral(left, value);
        if (!comparison.equals("=") && !comparison.equals("!=") && !value.isNumber()) {
            throw error("Numeric comparison requires an integer attribute");
        }
        return new Compare(left, comparison, value);
    }

    private void checkAttribute(String name) {
        try {
            RewardCssAttributes.typeOf(name);
        } catch (IllegalArgumentException failure) {
            throw error(failure.getMessage());
        }
    }

    private void checkLiteral(String name, Literal value) {
        if ((RewardCssAttributes.typeOf(name) == RewardCssAttributes.Type.INTEGER)
                != value.isNumber()) {
            throw error("LootCSS attribute " + name + " requires "
                    + RewardCssAttributes.typeOf(name) + " literal");
        }
    }

    private Literal numeric(String raw) {
        try {
            return new Literal(Integer.valueOf(raw));
        } catch (NumberFormatException failure) {
            throw error("Integer literal is out of range: " + raw);
        }
    }

    private Declaration declaration() {
        trivia();
        int start = position, round = 0, square = 0, angle = 0;
        while (!end()) {
            char c = current();
            if (c == ':' && round == 0 && square == 0 && angle == 0) break;
            if (c == '{' || c == '}' || c == ';') throw error("Expected ':' after property");
            if (source.startsWith("/*", position)) { trivia(); continue; }
            if (c == '(') round++;
            else if (c == ')') round--;
            else if (c == '[') square++;
            else if (c == ']') square--;
            else if (c == '<') angle++;
            else if (c == '>') angle--;
            if (round < 0 || square < 0 || angle < 0) throw error("Invalid resource property");
            position++;
        }
        if (end() || round != 0 || square != 0 || angle != 0) throw error("Invalid resource declaration");
        String property = source.substring(start, position).replaceAll("(?s)/\\*.*?\\*/", "").trim();
        if (!PROPERTY.matcher(property).matches() || property.startsWith("--grant-")
                || property.indexOf('(') >= 0 || property.indexOf(')') >= 0) {
            throw error("Invalid reward property: " + property);
        }
        expect(':');
        trivia();
        var value = new StringBuilder();
        char quote = 0;
        while (!end()) {
            char c = current();
            if (quote != 0) {
                value.append(c);
                position++;
                if (c == '\\') {
                    if (end()) throw error("Unterminated escape sequence");
                    value.append(current());
                    position++;
                } else if (c == quote) quote = 0;
                continue;
            }
            if (source.startsWith("/*", position)) { trivia(); continue; }
            if (c == '"' || c == '\'') { quote = c; value.append(c); position++; continue; }
            if (c == ';') break;
            if (c == '{' || c == '}') throw error("Missing ';' after declaration");
            value.append(c);
            position++;
        }
        if (quote != 0) throw error("Unterminated declaration string");
        expect(';');
        String text = value.toString().trim();
        if (text.isEmpty()) throw error("Empty reward value");
        if (text.matches("(?s).*\\s+!important\\s*") || text.endsWith("!important")) {
            throw error("!important is not supported");
        }
        if (text.length() >= 2
                && ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'")))) {
            text = text.substring(1, text.length() - 1);
        }
        return new Declaration(property, text);
    }

    private Literal literal() {
        trivia();
        if (take('"')) return new Literal(quoted('"'));
        if (take('\'')) return new Literal(quoted('\''));
        String word = atom();
        if (NUMBER.matcher(word).matches()) return numeric(word);
        if (!NAME.matcher(word).matches()) throw error("Invalid literal: " + word);
        return new Literal(word);
    }

    private String quoted(char quote) {
        var value = new StringBuilder();
        while (!end()) {
            char c = source.charAt(position++);
            if (c == quote) return value.toString();
            if (c == '\\') {
                if (end()) throw error("Unterminated escape");
                value.append(source.charAt(position++));
            } else value.append(c);
        }
        throw error("Unterminated string literal");
    }

    private String atom() {
        trivia();
        int start = position;
        while (!end()) {
            char c = current();
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '+' || c == '.') position++;
            else break;
        }
        if (start == position) throw error("Expected literal or attribute name");
        return source.substring(start, position);
    }

    private String identifier() {
        String word = atom();
        if (!NAME.matcher(word).matches()) throw error("Invalid identifier: " + word);
        return word;
    }

    private String operator() {
        if (takeString("!=")) return "!=";
        if (takeString(">=")) return ">=";
        if (takeString("<=")) return "<=";
        for (char c : new char[] {'=', '<', '>'}) if (take(c)) return Character.toString(c);
        throw error("Expected comparison operator");
    }

    private boolean word(String candidate) {
        if (!source.startsWith(candidate, position)) return false;
        int after = position + candidate.length();
        if (after < source.length() && (Character.isLetterOrDigit(source.charAt(after))
                || source.charAt(after) == '_')) return false;
        position = after;
        return true;
    }

    private void trivia() {
        for (;;) {
            while (!end() && Character.isWhitespace(current())) position++;
            if (!source.startsWith("/*", position)) return;
            int close = source.indexOf("*/", position + 2);
            if (close < 0) throw error("Unterminated comment");
            position = close + 2;
        }
    }

    private boolean takeString(String expected) {
        if (!source.startsWith(expected, position)) return false;
        position += expected.length();
        return true;
    }

    private boolean take(char c) {
        if (!peek(c)) return false;
        position++;
        return true;
    }

    private boolean peek(char c) { return !end() && current() == c; }
    private char current() { return source.charAt(position); }
    private boolean end() { return position >= source.length(); }
    private void expect(char c) { if (!take(c)) throw error("Expected '" + c + "'"); }

    private String location(int offset) {
        int line = 1, column = 1;
        for (int i = 0; i < offset; i++) {
            if (source.charAt(i) == '\n') { line++; column = 1; }
            else column++;
        }
        return line + ":" + column;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("Invalid reward CSS: " + location(position) + " " + message);
    }
}
