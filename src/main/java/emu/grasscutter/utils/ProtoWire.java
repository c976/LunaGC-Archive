package emu.grasscutter.utils;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Minimal protobuf wire helpers for Artifact Transmuter packets. */
public final class ProtoWire {
    private ProtoWire() {}

    public static void writeVarint(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }

    public static void writeTag(ByteArrayOutputStream out, int fieldNumber, int wireType) {
        writeVarint(out, ((long) fieldNumber << 3) | wireType);
    }

    public static void writeUint32(ByteArrayOutputStream out, int fieldNumber, int value) {
        if (value == 0) {
            return;
        }
        writeTag(out, fieldNumber, 0);
        writeVarint(out, value & 0xFFFFFFFFL);
    }

    public static void writeUint32Force(ByteArrayOutputStream out, int fieldNumber, int value) {
        writeTag(out, fieldNumber, 0);
        writeVarint(out, value & 0xFFFFFFFFL);
    }

    public static void writeUint64(ByteArrayOutputStream out, int fieldNumber, long value) {
        if (value == 0L) {
            return;
        }
        writeTag(out, fieldNumber, 0);
        writeVarint(out, value);
    }

    public static void writeUint64Force(ByteArrayOutputStream out, int fieldNumber, long value) {
        writeTag(out, fieldNumber, 0);
        writeVarint(out, value);
    }

    public static void writeBool(ByteArrayOutputStream out, int fieldNumber, boolean value) {
        if (!value) {
            return;
        }
        writeTag(out, fieldNumber, 0);
        writeVarint(out, 1);
    }

    public static void writeFixed32(ByteArrayOutputStream out, int fieldNumber, int value) {
        writeTag(out, fieldNumber, 5);
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    public static void writeBytes(ByteArrayOutputStream out, int fieldNumber, byte[] payload) {
        writeTag(out, fieldNumber, 2);
        writeVarint(out, payload.length);
        out.write(payload, 0, payload.length);
    }

    public static void writePackedUint32(ByteArrayOutputStream out, int fieldNumber, List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        for (int v : values) {
            writeVarint(packed, v & 0xFFFFFFFFL);
        }
        writeBytes(out, fieldNumber, packed.toByteArray());
    }

    /** Unpacked repeated uint32 (wire type 0 per element). */
    public static void writeRepeatedUint32(ByteArrayOutputStream out, int fieldNumber, List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (int v : values) {
            writeUint32Force(out, fieldNumber, v);
        }
    }

    public static final class Reader {
        private final byte[] data;
        private int pos;

        public Reader(byte[] data) {
            this.data = data == null ? new byte[0] : data;
            this.pos = 0;
        }

        public boolean hasRemaining() {
            return pos < data.length;
        }

        public long readVarint() {
            long result = 0;
            int shift = 0;
            while (pos < data.length) {
                int b = data[pos++] & 0xFF;
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
                shift += 7;
                if (shift > 63) {
                    throw new IllegalArgumentException("varint too long");
                }
            }
            throw new IllegalArgumentException("truncated varint");
        }

        public byte[] readBytes(int len) {
            if (pos + len > data.length) {
                throw new IllegalArgumentException("truncated bytes");
            }
            byte[] out = new byte[len];
            System.arraycopy(data, pos, out, 0, len);
            pos += len;
            return out;
        }

        public int readFixed32() {
            if (pos + 4 > data.length) {
                throw new IllegalArgumentException("truncated fixed32");
            }
            int v = (data[pos] & 0xFF)
                    | ((data[pos + 1] & 0xFF) << 8)
                    | ((data[pos + 2] & 0xFF) << 16)
                    | ((data[pos + 3] & 0xFF) << 24);
            pos += 4;
            return v;
        }
    }

    public static Map<Integer, List<Object>> parse(byte[] payload) {
        Map<Integer, List<Object>> fields = new HashMap<>();
        Reader r = new Reader(payload);
        while (r.hasRemaining()) {
            long tag = r.readVarint();
            int field = (int) (tag >>> 3);
            int wire = (int) (tag & 7);
            Object value;
            switch (wire) {
                case 0 -> value = r.readVarint();
                case 1 -> {
                    // fixed64
                    byte[] b = r.readBytes(8);
                    long v = 0;
                    for (int i = 7; i >= 0; i--) {
                        v = (v << 8) | (b[i] & 0xFFL);
                    }
                    value = v;
                }
                case 2 -> {
                    int len = (int) r.readVarint();
                    value = r.readBytes(len);
                }
                case 5 -> value = r.readFixed32() & 0xFFFFFFFFL;
                default -> throw new IllegalArgumentException("unsupported wire type " + wire);
            }
            if (value == null) {
                continue;
            }
            fields.computeIfAbsent(field, k -> new ArrayList<>()).add(value);
        }
        return fields;
    }

    public static List<Integer> asUint32List(List<Object> values) {
        List<Integer> out = new ArrayList<>();
        if (values == null) {
            return out;
        }
        for (Object v : values) {
            if (v instanceof Long l) {
                out.add(l.intValue());
            } else if (v instanceof Integer i) {
                out.add(i);
            } else if (v instanceof byte[] bytes) {
                Reader r = new Reader(bytes);
                while (r.hasRemaining()) {
                    out.add((int) r.readVarint());
                }
            }
        }
        return out;
    }
}
