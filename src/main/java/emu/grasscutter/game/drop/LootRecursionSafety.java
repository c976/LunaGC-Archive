package emu.grasscutter.game.drop;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact expected size of the pool invocation tree. Every invocation, including
 * the root and anonymous nested pools, contributes one node. Item quantities
 * do not contribute nodes unless they execute another pool.
 */
final class LootRecursionSafety {
    static final Fraction MAX_EXPECTED_NODES = Fraction.integer(100);

    private LootRecursionSafety() {}

    static final class Fraction implements Comparable<Fraction> {
        static final Fraction ZERO = integer(0);
        static final Fraction ONE = integer(1);

        private final BigInteger numerator;
        private final BigInteger denominator;

        private Fraction(BigInteger numerator, BigInteger denominator) {
            if (denominator.signum() == 0) throw new ArithmeticException("Division by zero");
            if (denominator.signum() < 0) {
                numerator = numerator.negate();
                denominator = denominator.negate();
            }
            BigInteger gcd = numerator.gcd(denominator);
            this.numerator = numerator.divide(gcd);
            this.denominator = denominator.divide(gcd);
        }

        static Fraction integer(long value) {
            return new Fraction(BigInteger.valueOf(value), BigInteger.ONE);
        }

        static Fraction ratio(BigInteger numerator, BigInteger denominator) {
            return new Fraction(numerator, denominator);
        }

        static Fraction decimal(BigDecimal value) {
            BigInteger unscaled = value.unscaledValue();
            int scale = value.scale();
            return scale < 0
                    ? ratio(unscaled.multiply(BigInteger.TEN.pow(-scale)), BigInteger.ONE)
                    : ratio(unscaled, BigInteger.TEN.pow(scale));
        }

        Fraction add(Fraction other) {
            return ratio(numerator.multiply(other.denominator).add(
                    other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }

        Fraction subtract(Fraction other) {
            return ratio(numerator.multiply(other.denominator).subtract(
                    other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }

        Fraction multiply(Fraction other) {
            return ratio(numerator.multiply(other.numerator),
                    denominator.multiply(other.denominator));
        }

        Fraction divide(Fraction other) {
            return ratio(numerator.multiply(other.denominator),
                    denominator.multiply(other.numerator));
        }

        boolean isZero() {
            return numerator.signum() == 0;
        }

        int signum() {
            return numerator.signum();
        }

        @Override
        public int compareTo(Fraction other) {
            return numerator.multiply(other.denominator).compareTo(
                    other.numerator.multiply(denominator));
        }

        @Override
        public String toString() {
            if (denominator.equals(BigInteger.ONE)) return numerator.toString();
            return new BigDecimal(numerator).divide(
                    new BigDecimal(denominator), MathContext.DECIMAL64).toPlainString();
        }
    }

    /** Reads the distribution that LootCount.parse already validated. */
    static Fraction expectedCount(Object raw) {
        if (raw == null) return Fraction.ONE;
        String text = raw.toString().trim();
        if (text.contains(":") || text.contains(",")) {
            WeightedChoice<Integer> options = WeightedChoice.parse(
                    text, "count", Integer::parseInt);
            Fraction sum = Fraction.ZERO;
            Fraction weightSum = Fraction.ZERO;
            for (int i = 0; i < options.candidates().size(); i++) {
                Fraction weight = Fraction.decimal(options.weights().get(i));
                weightSum = weightSum.add(weight);
                sum = sum.add(Fraction.integer(options.candidates().get(i)).multiply(weight));
            }
            return sum.divide(weightSum);
        }
        if (text.contains("~") || text.contains(";")) {
            String[] bounds = text.split("[~;]");
            long low = Integer.parseInt(bounds[0]);
            long high = Integer.parseInt(bounds[1]);
            return Fraction.ratio(BigInteger.valueOf(low).add(BigInteger.valueOf(high)),
                    BigInteger.TWO);
        }
        return Fraction.integer(Integer.parseInt(text));
    }

    static final class Graph {
        static final class Node {
            final String label;
            LootProbability.Law law = LootProbability.empty();
            final Map<Node, Fraction> children = new LinkedHashMap<>();

            Node(String label) {
                this.label = label;
            }
        }

        private final Map<Object, Node> nodes = new IdentityHashMap<>();
        private final Map<String, Node> roots = new LinkedHashMap<>();

        Node node(Object identity, String label) {
            return nodes.computeIfAbsent(identity, ignored -> new Node(label));
        }

        void root(String name, Object identity) {
            roots.put(name, node(identity, name));
        }

        void edge(Node from, Node to, Fraction expectedChildren) {
            if (!expectedChildren.isZero()) {
                from.children.merge(to, expectedChildren, Fraction::add);
            }
        }

        Map<String, YamlLootTables.SafetyReport> verify() {
            var components = new ArrayList<List<Node>>();
            var indices = new IdentityHashMap<Node, Integer>();
            var lowlinks = new IdentityHashMap<Node, Integer>();
            var stack = new ArrayList<Node>();
            var onStack = new IdentityHashMap<Node, Boolean>();
            int[] nextIndex = {0};

            for (Node node : nodes.values()) {
                if (!indices.containsKey(node)) {
                    visit(node, indices, lowlinks, stack, onStack, nextIndex, components);
                }
            }

            var totals = new IdentityHashMap<Node, Fraction>();
            // Tarjan emits dependency components before their callers.
            for (List<Node> component : components) {
                var positions = new IdentityHashMap<Node, Integer>();
                for (int i = 0; i < component.size(); i++) positions.put(component.get(i), i);
                int count = component.size();
                Fraction[][] coefficients = new Fraction[count][count];
                Fraction[] right = new Fraction[count];
                for (int i = 0; i < count; i++) {
                    Node node = component.get(i);
                    for (int j = 0; j < count; j++) {
                        coefficients[i][j] = i == j ? Fraction.ONE : Fraction.ZERO;
                    }
                    right[i] = Fraction.ONE;
                    for (var edge : node.children.entrySet()) {
                        Integer column = positions.get(edge.getKey());
                        if (column != null) {
                            coefficients[i][column] =
                                    coefficients[i][column].subtract(edge.getValue());
                        } else {
                            Fraction resolved = totals.get(edge.getKey());
                            if (resolved == null) {
                                throw new IllegalStateException(
                                        "Unresolved loot dependency: " + edge.getKey().label);
                            }
                            right[i] = right[i].add(edge.getValue().multiply(resolved));
                        }
                    }
                }

                Fraction[] solution = solve(coefficients, right);
                if (solution == null) {
                    throw new IllegalArgumentException(
                            "Unsafe loot recursion (unbounded expected nodes): "
                                    + component.getFirst().label);
                }
                for (int i = 0; i < count; i++) {
                    if (solution[i].signum() <= 0) {
                        throw new IllegalArgumentException(
                                "Unsafe loot recursion (unbounded expected nodes): "
                                        + component.get(i).label);
                    }
                    totals.put(component.get(i), solution[i]);
                }
            }

            for (var root : roots.entrySet()) {
                Fraction expected = totals.get(root.getValue());
                if (expected.compareTo(MAX_EXPECTED_NODES) > 0) {
                    throw new IllegalArgumentException("Loot pool " + root.getKey()
                            + " has expected " + expected + " nodes; maximum is "
                            + MAX_EXPECTED_NODES);
                }
            }
            var tails = LootProbability.verify(components, roots);
            var report = new LinkedHashMap<String, YamlLootTables.SafetyReport>();
            roots.forEach((name, node) -> report.put(name, new YamlLootTables.SafetyReport(
                    totals.get(node).toString(), tails.get(name).nodes(), tails.get(name).depth())));
            return Map.copyOf(report);
        }

        private static void visit(Node node, Map<Node, Integer> index,
                Map<Node, Integer> low, List<Node> stack,
                Map<Node, Boolean> onStack, int[] next,
                List<List<Node>> components) {
            int id = next[0]++;
            index.put(node, id);
            low.put(node, id);
            stack.add(node);
            onStack.put(node, true);
            for (Node child : node.children.keySet()) {
                if (!index.containsKey(child)) {
                    visit(child, index, low, stack, onStack, next, components);
                    low.put(node, Math.min(low.get(node), low.get(child)));
                } else if (onStack.containsKey(child)) {
                    low.put(node, Math.min(low.get(node), index.get(child)));
                }
            }
            if (low.get(node).equals(index.get(node))) {
                var group = new ArrayList<Node>();
                Node member;
                do {
                    member = stack.removeLast();
                    onStack.remove(member);
                    group.add(member);
                } while (member != node);
                components.add(group);
            }
        }

        /**
         * Solve (I - M)T = b exactly. A critical component is singular;
         * a supercritical component has no strictly positive solution.
         */
        private static Fraction[] solve(Fraction[][] matrix, Fraction[] values) {
            int size = values.length;
            for (int column = 0; column < size; column++) {
                int pivot = column;
                while (pivot < size && matrix[pivot][column].isZero()) pivot++;
                if (pivot == size) return null;
                if (pivot != column) {
                    Fraction[] old = matrix[pivot];
                    matrix[pivot] = matrix[column];
                    matrix[column] = old;
                    Fraction rhs = values[pivot];
                    values[pivot] = values[column];
                    values[column] = rhs;
                }
                Fraction diagonal = matrix[column][column];
                for (int j = column; j < size; j++) {
                    matrix[column][j] = matrix[column][j].divide(diagonal);
                }
                values[column] = values[column].divide(diagonal);
                for (int row = 0; row < size; row++) {
                    if (row == column || matrix[row][column].isZero()) continue;
                    Fraction multiple = matrix[row][column];
                    for (int j = column; j < size; j++) {
                        matrix[row][j] = matrix[row][j].subtract(
                                multiple.multiply(matrix[column][j]));
                    }
                    values[row] = values[row].subtract(multiple.multiply(values[column]));
                }
            }
            return values;
        }
    }
}
