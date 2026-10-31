package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** PlayerNicknameNotify for the 7.1 protocol. */
public class PacketPlayerNicknameNotify extends BasePacket {
    private static final int NICKNAME_FIELD_NUMBER = 12;

    public PacketPlayerNicknameNotify(String nickname) {
        super(PacketOpcodes.PlayerNicknameNotify);
        this.setData(encodeNickname(nickname));
    }

    private static byte[] encodeNickname(String nickname) {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = CodedOutputStream.newInstance(bytes);
            output.writeString(NICKNAME_FIELD_NUMBER, nickname);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode PlayerNicknameNotify", e);
        }
    }
}
