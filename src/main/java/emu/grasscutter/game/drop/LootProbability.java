package emu.grasscutter.game.drop;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Joint offspring PGFs, evaluated with downward-rounded nonnegative coefficients.
 * Missing mass is a certified upper bound, never an optimistic tail estimate.
 * No Monte Carlo sampling or runtime reward truncation is involved.
 */
final class LootProbability {
    static final int NODE_BOUND = 100;
    static final int DEPTH_BOUND = 64;
    static final BigDecimal TAIL_LIMIT = new BigDecimal("0.000001");
    private static final MathContext LOWER = new MathContext(40, RoundingMode.DOWN);
    private static final BigDecimal ZERO = BigDecimal.ZERO, ONE = BigDecimal.ONE;

    @FunctionalInterface
    interface Law {
        Poly evaluate(Function<LootRecursionSafety.Graph.Node, Poly> children, int bound);
    }

    static Law empty() { return (children, bound) -> Poly.one(bound); }

    static Law all(List<Law> branches) {
        return (children, bound) -> {
            Poly product = Poly.one(bound);
            for (Law branch : branches) product = product.multiply(branch.evaluate(children, bound));
            return product;
        };
    }

    static Law choice(List<Law> branches, List<BigDecimal> weights) {
        BigDecimal sum = weights.stream().reduce(ZERO, BigDecimal::add);
        return (children, bound) -> {
            Poly result = Poly.zero(bound);
            for (int i = 0; i < branches.size(); i++) {
                if (weights.get(i).signum() == 0) continue;
                result = result.add(branches.get(i).evaluate(children, bound)
                        .scale(weights.get(i).divide(sum, LOWER)));
            }
            return result;
        };
    }

    /** The selected type is fixed for all repetitions; each pool execution is independent. */
    static Law repeat(LootRecursionSafety.Graph.Node child, Object rawCount) {
        String text = rawCount == null ? "1" : rawCount.toString().trim();
        if (text.contains(":") || text.contains(",")) {
            var counts = WeightedChoice.parse(text, "count", Integer::parseInt);
            var branches = counts.candidates().stream()
                    .map(count -> count == 0 ? empty()
                            : (Law) (children, bound) -> children.apply(child).power(count)).toList();
            return choice(branches, counts.weights());
        }
        if (text.contains("~") || text.contains(";")) {
            String[] range = text.split("[~;]", -1);
            int low = Integer.parseInt(range[0]), high = Integer.parseInt(range[1]);
            if (high == 0) return empty();
            return (children, bound) -> {
                Poly p = children.apply(child);
                return p.power(low).multiply(p.powerSum((long) high - low + 1))
                        .scale(ONE.divide(BigDecimal.valueOf((long) high - low + 1), LOWER));
            };
        }
        int count = Integer.parseInt(text);
        if (count == 0) return empty();
        return (children, bound) -> children.apply(child).power(count);
    }

    record Tails(BigDecimal nodes, BigDecimal depth) {}

    static Map<String, Tails> verify(List<List<LootRecursionSafety.Graph.Node>> components,
            Map<String, LootRecursionSafety.Graph.Node> roots) {
        var totals = new IdentityHashMap<LootRecursionSafety.Graph.Node, Poly>();
        // Dependency components precede callers. In a cycle, each iteration adds
        // at least one total node, so 100 iterations recover all coefficients <=100.
        for (var component : components) {
            for (var node : component) totals.put(node, Poly.zero(NODE_BOUND));
            boolean cyclic = component.size() > 1
                    || component.getFirst().children.containsKey(component.getFirst());
            for (int iteration = 0; iteration < (cyclic ? NODE_BOUND : 1); iteration++) {
                var next = new IdentityHashMap<LootRecursionSafety.Graph.Node, Poly>();
                boolean unchanged = true;
                for (var node : component) {
                    Poly result = node.law.evaluate(totals::get, NODE_BOUND).shift();
                    next.put(node, result);
                    unchanged &= result.equals(totals.get(node));
                }
                totals.putAll(next);
                if (unchanged) break;
            }
        }
        var heights = new IdentityHashMap<LootRecursionSafety.Graph.Node, Poly>();
        for (var component : components) for (var node : component) heights.put(node, Poly.zero(0));
        for (int depth = 1; depth <= DEPTH_BOUND; depth++) {
            var next = new IdentityHashMap<LootRecursionSafety.Graph.Node, Poly>();
            for (var component : components) for (var node : component) {
                next.put(node, node.law.evaluate(heights::get, 0));
            }
            heights = next;
        }
        var report = new java.util.LinkedHashMap<String, Tails>();
        for (var root : roots.entrySet()) {
            report.put(root.getKey(), new Tails(totals.get(root.getValue()).tail(), heights.get(root.getValue()).tail()));
            check(root.getKey(), "P(N > " + NODE_BOUND + ")", totals.get(root.getValue()).tail());
            check(root.getKey(), "P(H > " + DEPTH_BOUND + ")", heights.get(root.getValue()).tail());
        }
        return Map.copyOf(report);
    }

    private static void check(String root, String metric, BigDecimal upper) {
        if (upper.compareTo(TAIL_LIMIT) > 0) {
            throw new IllegalArgumentException("Loot pool " + root + " " + metric
                    + " upper bound " + upper.toEngineeringString() + " exceeds " + TAIL_LIMIT);
        }
    }

    /** Truncated nonnegative PGF. The bound is an analysis window, not a runtime cap. */
    static final class Poly {
        final int bound;
        final BigDecimal[] coefficients;

        Poly(int bound, BigDecimal[] coefficients) {
            this.bound = bound;
            int length = coefficients.length;
            while (length > 1 && coefficients[length - 1].signum() == 0) length--;
            this.coefficients = Arrays.copyOf(coefficients, length);
        }

        static Poly zero(int bound) { return new Poly(bound, new BigDecimal[] {ZERO}); }
        static Poly one(int bound) { return new Poly(bound, new BigDecimal[] {ONE}); }

        Poly add(Poly other) {
            var result = new BigDecimal[Math.max(coefficients.length, other.coefficients.length)];
            Arrays.fill(result, ZERO);
            for (int i = 0; i < result.length; i++) {
                if (i < coefficients.length) result[i] = coefficients[i];
                if (i < other.coefficients.length) result[i] = result[i].add(other.coefficients[i], LOWER);
            }
            return new Poly(bound, result);
        }

        Poly scale(BigDecimal factor) {
            var result = new BigDecimal[coefficients.length];
            for (int i = 0; i < result.length; i++) result[i] = coefficients[i].multiply(factor, LOWER);
            return new Poly(bound, result);
        }

        Poly multiply(Poly other) {
            var result = new BigDecimal[Math.min(bound + 1, coefficients.length + other.coefficients.length - 1)];
            Arrays.fill(result, ZERO);
            for (int i = 0; i < coefficients.length; i++) {
                if (coefficients[i].signum() == 0) continue;
                for (int j = 0; j < other.coefficients.length && i + j < result.length; j++) {
                    if (other.coefficients[j].signum() == 0) continue;
                    result[i + j] = result[i + j].add(coefficients[i].multiply(other.coefficients[j], LOWER), LOWER);
                }
            }
            return new Poly(bound, result);
        }

        Poly power(long count) {
            Poly result = one(bound), base = this;
            while (count > 0) {
                if ((count & 1) != 0) result = result.multiply(base);
                count >>= 1;
                if (count > 0) base = base.multiply(base);
            }
            return result;
        }

        /** 1+p+...+p^(count-1), logarithmic in count, including very wide ranges. */
        Poly powerSum(long count) {
            if (count == 0) return zero(bound);
            if (count == 1) return one(bound);
            long half = count / 2;
            Poly pair = powerSum(half).multiply(one(bound).add(power(half)));
            return (count & 1) == 0 ? pair : pair.add(power(count - 1));
        }

        Poly shift() {
            var result = new BigDecimal[Math.min(bound + 1, coefficients.length + 1)];
            result[0] = ZERO;
            for (int i = 1; i < result.length; i++) result[i] = coefficients[i - 1];
            return new Poly(bound, result);
        }

        BigDecimal tail() {
            BigDecimal mass = ZERO;
            for (BigDecimal coefficient : coefficients) mass = mass.add(coefficient, LOWER);
            return ONE.subtract(mass);
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Poly p) || coefficients.length != p.coefficients.length) return false;
            for (int i = 0; i < coefficients.length; i++) {
                if (coefficients[i].compareTo(p.coefficients[i]) != 0) return false;
            }
            return true;
        }

        @Override public int hashCode() {
            return Arrays.hashCode(Arrays.stream(coefficients).map(BigDecimal::stripTrailingZeros).toArray());
        }
    }
}
