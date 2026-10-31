package emu.grasscutter.game.inventory;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.proto.ReliquaryOuterClass.Reliquary;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Bridges deobfuscated Reliquary builders ({@code setIsRelicStarred} /
 * {@code addAllPurchasedAppendPropIdList} / {@code addAllDefiniteAppendPropIdList}) and live-jar
 * obfuscated names ({@code setDCCECKHJPKB} / {@code addAllOIGPOFNBKGG}).
 *
 * <p>Live jar Reliquary only exposes field 7 ({@code OIGPOFNBKGG} = purchased). Field 8 (definite
 * / the two guaranteed purple marks) is missing from that generated class, so we append packed field-8 bytes after
 * {@code build()} when the definite setter cannot be resolved.
 */
final class ReliquaryProtoCompat {
    private static final Method SET_STARRED =
            find(Reliquary.Builder.class, boolean.class, "setIsRelicStarred", "setDCCECKHJPKB");
    private static final Method ADD_PURCHASED =
            find(
                    Reliquary.Builder.class,
                    Iterable.class,
                    "addAllPurchasedAppendPropIdList",
                    "addAllOIGPOFNBKGG");
    private static final Method ADD_DEFINITE =
            find(Reliquary.Builder.class, Iterable.class, "addAllDefiniteAppendPropIdList");

    private ReliquaryProtoCompat() {}

    static void applyStarred(Reliquary.Builder builder, boolean starred) {
        invoke(SET_STARRED, builder, starred);
    }

    static void applyPurchased(Reliquary.Builder builder, List<Integer> ids) {
        if (ids == null || ids.isEmpty() || ADD_PURCHASED == null) {
            return;
        }
        invoke(ADD_PURCHASED, builder, ids);
    }

    static void applyDefinite(Reliquary.Builder builder, List<Integer> ids) {
        if (ids == null || ids.isEmpty() || ADD_DEFINITE == null) {
            return;
        }
        invoke(ADD_DEFINITE, builder, ids);
    }

    /**
     * Build Reliquary, ensuring purchased (7) and definite (8) hit the wire even when the live jar
     * proto only has obfuscated field 7.
     */
    static Reliquary finish(
            Reliquary.Builder builder, List<Integer> purchased, List<Integer> definite) {
        applyPurchased(builder, purchased);
        boolean definiteOnBuilder = definite != null && !definite.isEmpty() && ADD_DEFINITE != null;
        if (definiteOnBuilder) {
            applyDefinite(builder, definite);
        }
        Reliquary built = builder.build();
        if (definite == null || definite.isEmpty() || definiteOnBuilder) {
            return built;
        }
        return appendPackedUInt32Field(built, Reliquary._DEFINITE_APPEND_PROP_ID_LIST_FIELD_NUMBER, definite);
    }

    private static Reliquary appendPackedUInt32Field(
            Reliquary base, int fieldNumber, List<Integer> values) {
        try {
            ByteArrayOutputStream packed = new ByteArrayOutputStream();
            CodedOutputStream packedCos = CodedOutputStream.newInstance(packed);
            for (int v : values) {
                packedCos.writeUInt32NoTag(v);
            }
            packedCos.flush();
            byte[] packedBytes = packed.toByteArray();

            ByteArrayOutputStream extra = new ByteArrayOutputStream();
            CodedOutputStream cos = CodedOutputStream.newInstance(extra);
            cos.writeTag(fieldNumber, 2); // WIRETYPE_LENGTH_DELIMITED
            cos.writeUInt32NoTag(packedBytes.length);
            cos.write(packedBytes, 0, packedBytes.length);
            cos.flush();

            byte[] a = base.toByteArray();
            byte[] b = extra.toByteArray();
            byte[] out = new byte[a.length + b.length];
            System.arraycopy(a, 0, out, 0, a.length);
            System.arraycopy(b, 0, out, a.length, b.length);
            return Reliquary.parseFrom(out);
        } catch (Exception e) {
            return base;
        }
    }

    private static Method find(Class<?> type, Class<?> arg, String... names) {
        for (String name : names) {
            try {
                Method m = type.getMethod(name, arg);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static void invoke(Method method, Object target, Object arg) {
        if (method == null) {
            return;
        }
        try {
            method.invoke(target, arg);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
