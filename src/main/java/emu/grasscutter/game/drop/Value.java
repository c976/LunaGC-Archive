package emu.grasscutter.game.drop;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * A typed probabilistic expression, independent of any game resource type.
 * Evaluation samples the expression once; composing expressions does not
 * mutate the underlying YAML value or grant rewards.
 */
sealed interface Value<T> permits WeightedChoice, Value.Constant, Value.Mapped, Value.Bound {
    T sample(RandomGenerator random);

    static <T> Value<T> constant(T value) {
        return new Constant<>(Objects.requireNonNull(value));
    }

    static <T> Value<T> choose(List<T> values, List<BigDecimal> weights, String location) {
        return WeightedChoice.of(values, weights, location);
    }

    default <R> Value<R> map(Function<? super T, ? extends R> mapper) {
        return new Mapped<>(this, Objects.requireNonNull(mapper));
    }

    /**
     * Sequence dependent choices. The second expression is constructed only
     * after the first has been sampled, using the same RNG for deterministic order.
     */
    default <R> Value<R> flatMap(Function<? super T, ? extends Value<R>> next) {
        return new Bound<>(this, Objects.requireNonNull(next));
    }

    record Constant<T>(T value) implements Value<T> {
        public Constant { Objects.requireNonNull(value); }
        @Override public T sample(RandomGenerator random) {
            Objects.requireNonNull(random);
            return value;
        }
    }

    record Mapped<T, R>(Value<T> source,
                        Function<? super T, ? extends R> mapper) implements Value<R> {
        public Mapped {
            Objects.requireNonNull(source);
            Objects.requireNonNull(mapper);
        }
        @Override public R sample(RandomGenerator random) {
            return Objects.requireNonNull(mapper.apply(source.sample(random)));
        }
    }

    record Bound<T, R>(Value<T> source,
                       Function<? super T, ? extends Value<R>> next) implements Value<R> {
        public Bound {
            Objects.requireNonNull(source);
            Objects.requireNonNull(next);
        }
        @Override public R sample(RandomGenerator random) {
            return Objects.requireNonNull(next.apply(source.sample(random))).sample(random);
        }
    }
}
