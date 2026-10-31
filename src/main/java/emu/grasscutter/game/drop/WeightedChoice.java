package emu.grasscutter.game.drop;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * A single weighted-expression evaluator for entry fields and reward pools.
 * Every candidate has an explicit relative weight, with no implicit outcome.
 * A scalar compiles to a constant expression.
 */
final class WeightedChoice<T> implements Value<T> {
    private final List<T> candidates;
    private final List<BigDecimal> weights;
    private final double[] boundaries;
    private final boolean constant;

    private WeightedChoice(List<T> candidates, List<BigDecimal> weights,
            double[] boundaries, boolean constant) {
        this.candidates = List.copyOf(candidates);
        this.weights = List.copyOf(weights);
        this.boundaries = boundaries;
        this.constant = constant;
    }

    static <T> WeightedChoice<T> compile(
            Object raw, String location, Function<Object, T> resolve) {
        Objects.requireNonNull(resolve, "resolve");
        if (raw == null) {
            throw new IllegalArgumentException(location + ": missing value");
        }
        if (raw instanceof String text && hasTopLevelDelimiter(text)) {
            return parse(text, location, name -> resolve.apply(name));
        }
        return constant(Objects.requireNonNull(resolve.apply(raw),
                location + ": invalid value " + raw));
    }

    private static <T> WeightedChoice<T> constant(T candidate) {
        return new WeightedChoice<>(List.of(candidate), List.of(BigDecimal.ONE),
                new double[] {1.0}, true);
    }

    static <T> WeightedChoice<T> parse(
            String expression, String location, Function<String, T> resolve) {
        Objects.requireNonNull(resolve, "resolve");
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException(location + ": empty weighted choice");
        }
        var candidates = new ArrayList<T>();
        var weights = new ArrayList<BigDecimal>();
        for (String segment : splitTopLevel(expression, ',')) {
            int separator = topLevelIndex(segment, ':');
            if (separator < 1 || topLevelIndex(segment.substring(separator + 1), ':') >= 0) {
                throw new IllegalArgumentException(
                        location + ": expected candidate:weight: " + segment);
            }
            String name = segment.substring(0, separator).trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException(location + ": empty weighted candidate");
            }
            var weight = weight(segment.substring(separator + 1), location);
            if (weight.signum() == 0) continue;
            candidates.add(Objects.requireNonNull(resolve.apply(name),
                    location + ": invalid candidate " + name));
            weights.add(weight);
        }
        return of(candidates, weights, location);
    }


    /**
     * Count separators only outside template arguments,
     * brackets and quoted strings. A call such as chest_artifact<3,1,set>
     * is a single candidate, even though its argument list contains commas.
     */
    private static int topLevelIndex(String source, char delimiter) {
        int angle = 0, round = 0, square = 0, curly = 0;
        char quote = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') { quote = c; continue; }
            if (c == delimiter && angle == 0 && round == 0 && square == 0 && curly == 0) {
                return i;
            }
            switch (c) {
                case '<' -> angle++;
                case '>' -> angle--;
                case '(' -> round++;
                case ')' -> round--;
                case '[' -> square++;
                case ']' -> square--;
                case '{' -> curly++;
                case '}' -> curly--;
                default -> {}
            }
            if (angle < 0 || round < 0 || square < 0 || curly < 0) {
                throw new IllegalArgumentException("Unbalanced weighted expression: " + source);
            }
        }
        if (quote != 0 || angle != 0 || round != 0 || square != 0 || curly != 0) {
            throw new IllegalArgumentException("Unbalanced weighted expression: " + source);
        }
        return -1;
    }

    private static boolean hasTopLevelDelimiter(String text) {
        return topLevelIndex(text, ':') >= 0 || topLevelIndex(text, ',') >= 0;
    }

    private static List<String> splitTopLevel(String expression, char delimiter) {
        var result = new ArrayList<String>();
        String remaining = expression;
        for (;;) {
            int index = topLevelIndex(remaining, delimiter);
            if (index < 0) {
                result.add(remaining);
                return result;
            }
            result.add(remaining.substring(0, index));
            remaining = remaining.substring(index + 1);
        }
    }

    static BigDecimal weight(Object raw, String location) {
        if (!(raw instanceof Number || raw instanceof String)
                || !raw.toString().trim().matches("[0-9]+(?:\\.[0-9]+)?")) {
            throw new IllegalArgumentException(location + ": weight must be nonnegative decimal");
        }
        return new BigDecimal(raw.toString().trim());
    }

    /** A weighted pool is the same expression, with entries as its candidates. */
    static <T> WeightedChoice<T> of(
            List<T> candidates, List<BigDecimal> weights, String location) {
        if (candidates.size() != weights.size()) {
            throw new IllegalArgumentException(location + ": mismatched weighted candidates");
        }
        var selected = new ArrayList<T>();
        var selectedWeights = new ArrayList<BigDecimal>();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < candidates.size(); i++) {
            BigDecimal weight = weights.get(i);
            if (weight.signum() < 0) {
                throw new IllegalArgumentException(location + ": negative weight");
            }
            if (weight.signum() == 0) continue;
            selected.add(Objects.requireNonNull(candidates.get(i)));
            selectedWeights.add(weight);
            total = total.add(weight);
        }
        if (total.signum() <= 0) {
            throw new IllegalArgumentException(location + ": no positive weights");
        }
        double[] cumulative = new double[selectedWeights.size()];
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < selectedWeights.size(); i++) {
            sum = sum.add(selectedWeights.get(i));
            cumulative[i] = sum.divide(total, MathContext.DECIMAL128).doubleValue();
        }
        cumulative[cumulative.length - 1] = 1.0;
        return new WeightedChoice<>(selected, selectedWeights, cumulative, false);
    }

    @Override
    public T sample(RandomGenerator random) {
        if (constant) return candidates.getFirst();
        double ticket = random.nextDouble();
        for (int i = 0; i < boundaries.length - 1; i++) {
            if (ticket < boundaries[i]) return candidates.get(i);
        }
        return candidates.getLast();
    }

    List<T> candidates() {
        return candidates;
    }

    List<BigDecimal> weights() {
        return weights;
    }
}
