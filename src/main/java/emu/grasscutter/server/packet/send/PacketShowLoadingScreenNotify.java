package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ShowLoadingScreenNotify._ShowLoadingScreenNotify;

/**
 * {@code _ShowLoadingScreenNotify}. Used for the Spiral Abyss mid-half black screen while the
 * official tip UI ({@code UI_TOWER_INSTAGE_LOADING_TIP}) is driven by MidLevel.
 */
public class PacketShowLoadingScreenNotify extends BasePacket {
    public PacketShowLoadingScreenNotify(float durationSeconds) {
        this(0, durationSeconds);
    }

    public PacketShowLoadingScreenNotify(int templateLoadingId, float durationSeconds) {
        super(PacketOpcodes.ShowLoadingScreenNotify);
        this.setData(
                _ShowLoadingScreenNotify.newBuilder()
                        .setTemplateLoadingId(Math.max(templateLoadingId, 0))
                        .setDuration(durationSeconds)
                        .build());
    }
}
