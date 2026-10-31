package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.utils.ProtoWire;
import java.io.ByteArrayOutputStream;

/**
 * 7.0 {@code PCPIJOHICAK} (CmdId 3674) — alternate loading fade with only template + duration.
 * Unlike {@code ShowLoadingScreenNotify} (21177), this may not roll the LoadingTips tip pool.
 *
 * <pre>
 *   float  duration             = 7;
 *   uint32 _template_loading_id = 3;
 * </pre>
 */
public class PacketLoadingFadeNotify extends BasePacket {

    public static final int OPCODE = 3674;

    public PacketLoadingFadeNotify(int templateLoadingId, float durationSeconds) {
        super(OPCODE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // field 7 = float duration (fixed32)
        ProtoWire.writeFixed32(out, 7, Float.floatToIntBits(durationSeconds));
        if (templateLoadingId > 0) {
            ProtoWire.writeUint32Force(out, 3, templateLoadingId);
        }
        setData(out.toByteArray());
    }
}
