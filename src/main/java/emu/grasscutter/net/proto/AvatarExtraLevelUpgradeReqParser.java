package emu.grasscutter.net.proto;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.WireFormat;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Wire parser for the two 7.0 extra-level request layouts present in the patched server. */
public final class AvatarExtraLevelUpgradeReqParser {
    private AvatarExtraLevelUpgradeReqParser() {}

    public static ParsedExtraLevelUpgradeReq parse(byte[] payload)
            throws InvalidProtocolBufferException {
        return parseAnyStrict(payload);
    }

    public static ParsedExtraLevelUpgradeReq parseStrict(byte[] payload)
            throws InvalidProtocolBufferException {
        Map<Integer, Long> fields = readNumericFields(payload);
        long guid = fields.getOrDefault(14, 0L);
        if (guid <= 0) {
            throw invalid("Missing avatar guid in JDCOOPLLALE payload");
        }
        return new ParsedExtraLevelUpgradeReq(guid, fields.getOrDefault(11, 0L).intValue(), "JDCOOPLLALE");
    }

    public static ParsedExtraLevelUpgradeReq parseKampStrict(byte[] payload)
            throws InvalidProtocolBufferException {
        Map<Integer, Long> fields = readNumericFields(payload);
        long guid = fields.getOrDefault(5, 0L);
        if (guid <= 0) {
            throw invalid("Missing avatar guid in KAMPJKIGBFB payload");
        }
        return new ParsedExtraLevelUpgradeReq(guid, fields.getOrDefault(9, 0L).intValue(), "KAMPJKIGBFB");
    }

    public static ParsedExtraLevelUpgradeReq parseAnyStrict(byte[] payload)
            throws InvalidProtocolBufferException {
        try {
            return parseStrict(payload);
        } catch (InvalidProtocolBufferException ignored) {
            try {
                return parseKampStrict(payload);
            } catch (InvalidProtocolBufferException ignoredAgain) {
                return parseFallback(payload);
            }
        }
    }

    public static ParsedExtraLevelUpgradeReq parseWithFallback(byte[] payload)
            throws InvalidProtocolBufferException {
        return parseAnyStrict(payload);
    }

    public static long parseAvatarGuid(byte[] payload) throws InvalidProtocolBufferException {
        return parse(payload).getAvatarGuid();
    }

    private static ParsedExtraLevelUpgradeReq parseFallback(byte[] payload)
            throws InvalidProtocolBufferException {
        Map<Integer, Long> fields = readNumericFields(payload);
        for (int field : new int[] {1, 5, 7, 11, 12, 13, 14, 15}) {
            long guid = fields.getOrDefault(field, 0L);
            if (guid > 0) {
                int target = fields.containsKey(9) ? fields.get(9).intValue() : fields.getOrDefault(11, 0L).intValue();
                return new ParsedExtraLevelUpgradeReq(guid, target, "fallback");
            }
        }
        throw invalid("Missing avatar guid in extra level payload");
    }

    private static Map<Integer, Long> readNumericFields(byte[] payload)
            throws InvalidProtocolBufferException {
        if (payload == null || payload.length == 0) {
            throw invalid("Empty extra level upgrade payload");
        }
        Map<Integer, Long> fields = new HashMap<>();
        try {
            CodedInputStream input = CodedInputStream.newInstance(payload);
            while (!input.isAtEnd()) {
                int tag = input.readTag();
                if (tag == 0) break;
                int field = WireFormat.getTagFieldNumber(tag);
                int wireType = WireFormat.getTagWireType(tag);
                if (wireType == WireFormat.WIRETYPE_VARINT) {
                    fields.put(field, input.readUInt64());
                } else if (wireType == WireFormat.WIRETYPE_FIXED64) {
                    fields.put(field, input.readFixed64());
                } else if (!input.skipField(tag)) {
                    break;
                }
            }
            return fields;
        } catch (IOException exception) {
            throw invalid("Failed to parse extra level payload");
        }
    }

    private static InvalidProtocolBufferException invalid(String message) {
        return new InvalidProtocolBufferException(message);
    }
}
