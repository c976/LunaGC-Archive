package emu.grasscutter.game.player;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.net.proto.ProfilePictureOuterClass;
import emu.grasscutter.net.proto.SocialDetailOuterClass.SocialDetail;
import java.io.ByteArrayOutputStream;

/**
 * 7.1 头像 / 头像框。
 *
 * 7.1 真表里，资料页头像数据不再走 SocialDetail.profile_picture(25)，
 * 而是 SocialDetail 字段 6 = IIBOEFCLJEA { uint32 picId = 1; uint32 frameId = 2; }
 * （同结构也出现在 CmdId 29394 / 27497 上）。
 *
 * 工程生成的 proto 里字段 6 仍是 birthday，故这里用 UnknownFieldSet
 * 直接按 wire 格式写入字段 6，绕过生成代码。
 */
public final class BeyondProfilePictureHelper {

    /** SocialDetail 里承载头像/头像框的字段号（7.1） */
    public static final int FIELD_NUMBER = 6;

    private BeyondProfilePictureHelper() {}

    public static SocialDetail.Builder apply(SocialDetail.Builder sb, int headImage, int frameId) {
        if (sb == null) {
            return null;
        }
        int picId;
        try {
            picId = ProfilePictureHelper
                    .fillFromHeadImage(ProfilePictureOuterClass.ProfilePicture.newBuilder(), headImage)
                    .getProfilePictureId();
        } catch (Throwable t) {
            picId = 1;
        }
        if (picId <= 0) {
            picId = 1;
        }
        if (frameId <= 0) {
            frameId = 100000;
        }
        byte[] inner = encode(picId, frameId);
        UnknownFieldSet.Field field = UnknownFieldSet.Field.newBuilder()
                .addLengthDelimited(ByteString.copyFrom(inner))
                .build();
        return sb.setUnknownFields(UnknownFieldSet.newBuilder().addField(FIELD_NUMBER, field).build());
    }

    private static byte[] encode(int picId, int frameId) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarintField(out, 1, picId);
        writeVarintField(out, 2, frameId);
        return out.toByteArray();
    }

    private static void writeVarintField(ByteArrayOutputStream out, int fieldNumber, int value) {
        writeVarint(out, ((long) fieldNumber) << 3);
        writeVarint(out, value);
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        while (true) {
            if ((value & ~0x7FL) == 0L) {
                out.write((int) value);
                return;
            }
            out.write((int) ((value & 0x7FL) | 0x80L));
            value >>>= 7;
        }
    }
}
