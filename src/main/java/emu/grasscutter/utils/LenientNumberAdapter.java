package emu.grasscutter.utils;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.function.Function;

/**
 * Reads a number where the data may instead hold a global-value name ("SGV_...") or a formula
 * array, as newer ability and excel dumps do in fields that used to be plain numbers. Such values
 * cannot be resolved at load time, so they read as zero instead of failing the whole file.
 */
public final class LenientNumberAdapter<T extends Number> extends TypeAdapter<T> {
    public static final LenientNumberAdapter<Integer> INT =
            new LenientNumberAdapter<>(Double::intValue, Integer.class, 0);
    public static final LenientNumberAdapter<Long> LONG =
            new LenientNumberAdapter<>(Double::longValue, Long.class, 0L);
    public static final LenientNumberAdapter<Float> FLOAT =
            new LenientNumberAdapter<>(Double::floatValue, Float.class, 0f);
    public static final LenientNumberAdapter<Double> DOUBLE =
            new LenientNumberAdapter<>(d -> d, Double.class, 0d);

    private final Function<Double, T> convert;
    private final T zero;

    private LenientNumberAdapter(Function<Double, T> convert, Class<T> type, T zero) {
        this.convert = convert;
        this.zero = zero;
    }

    @Override
    public T read(JsonReader in) throws IOException {
        var token = in.peek();
        if (token == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        if (token == JsonToken.NUMBER) {
            return this.convert.apply(in.nextDouble());
        }
        if (token == JsonToken.STRING) {
            var text = in.nextString();
            try {
                return this.convert.apply(Double.parseDouble(text));
            } catch (NumberFormatException ignored) {
                return this.zero;
            }
        }
        if (token == JsonToken.BOOLEAN) {
            return this.convert.apply(in.nextBoolean() ? 1d : 0d);
        }
        in.skipValue();
        return this.zero;
    }

    @Override
    public void write(JsonWriter out, T value) throws IOException {
        out.value(value);
    }
}
