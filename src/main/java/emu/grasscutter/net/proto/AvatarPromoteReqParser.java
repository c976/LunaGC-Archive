package emu.grasscutter.net.proto;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * Wire parser for the 7.1 ascension request. The client sends {@code uint64 guid = 3}, while the
 * port's AvatarPromoteReq schema, generated from protocol/7.1/protocol.desc, declares guid at field
 * 12. Read the client guid from the parsed unknown fields, with the descriptor guid as a fallback.
 */
public final class AvatarPromoteReqParser {
    private static final int CLIENT_GUID_FIELD = 3;

    private AvatarPromoteReqParser() {}

    /** The avatar guid carried by a valid ascension request, or 0 when absent or malformed. */
    public static long parseGuid(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return 0;
        }
        try {
            var request = AvatarPromoteReqOuterClass.AvatarPromoteReq.parseFrom(payload);
            var clientGuids = request.getUnknownFields().getField(CLIENT_GUID_FIELD).getVarintList();
            long guid = clientGuids.isEmpty() ? 0 : clientGuids.getLast();
            return guid > 0 ? guid : request.getGuid();
        } catch (InvalidProtocolBufferException ignored) {
            return 0;
        }
    }
}
