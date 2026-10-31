package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.systems.ReliquaryDustSystem.PendingCandidate;
import emu.grasscutter.game.systems.ReliquaryDustSystem.PlayerDustState;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.utils.ProtoWire;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * FKBHBPBNOJJ opcode 25355 — shared Offer companion / Dust notify.
 *
 * <p>Pending reshape: {@code PJIOAPGJDBI} (chosen + new append + guid) plus {@code
 * cur_progress} so the reshape page keeps Dust state.
 */
public class PacketReliquaryOfferCompanionNotify extends BasePacket {

    public PacketReliquaryOfferCompanionNotify() {
        this(null, false);
    }

    public PacketReliquaryOfferCompanionNotify(PlayerDustState dustState) {
        this(dustState, false);
    }

    /**
     * @param clearCandidate wipe the client's sticky reshape candidate instead of describing one.
     *     The client merges this notify into what it already holds, so an empty body leaves the old
     *     candidate in place - the clear has to be an explicit {@code guid = 0} that overwrites it.
     *     Chosen lines and the new-append list are deliberately left out: repeated fields append on
     *     merge, so writing them empty would be a no-op while writing them at all risks duplicates.
     */
    public PacketReliquaryOfferCompanionNotify(PlayerDustState dustState, boolean clearCandidate) {
        super(PacketOpcodes.ReliquaryOfferCompanionNotify);
        this.setData(clearCandidate ? buildClear(dustState) : build(dustState));
    }

    private static byte[] build(PlayerDustState dustState) {
        if (dustState == null || (dustState.progress <= 0 && dustState.pending == null)) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PendingCandidate pending = dustState.pending;
        if (pending != null) {
            ByteArrayOutputStream nest = new ByteArrayOutputStream();
            List<Integer> chosen = pending.chosenAffixIds;
            if (chosen == null || chosen.isEmpty()) {
                chosen = pending.chosenFightPropIds;
            }
            // 7.1 numbers (7.0: chosen 2, append 3, guid 9, entry 10, cur_progress 7).
            // chosen lines (UI highlight); append_prop_id_list (right panel).
            ProtoWire.writePackedUint32(nest, 10, chosen);
            ProtoWire.writePackedUint32(nest, 3, pending.newAppend);
            ProtoWire.writeUint64Force(nest, 2, pending.guid);
            ProtoWire.writeBytes(out, 3, nest.toByteArray());
            // Keep cur_progress so reshape page treats this as Dust state, not empty Offer ping.
            if (dustState.progress > 0) {
                ProtoWire.writeUint32Force(out, 12, dustState.progress);
            } else {
                ProtoWire.writeUint32Force(out, 12, 1);
            }
        } else if (dustState.progress > 0) {
            ProtoWire.writeUint32Force(out, 12, dustState.progress);
        }
        return out.toByteArray();
    }

    private static byte[] buildClear(PlayerDustState dustState) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream nest = new ByteArrayOutputStream();
        ProtoWire.writeUint64Force(nest, 2, 0L);
        ProtoWire.writeBytes(out, 3, nest.toByteArray());
        // Same cur_progress rule as build(): the page must still read this as Dust state.
        int progress = dustState != null && dustState.progress > 0 ? dustState.progress : 1;
        ProtoWire.writeUint32Force(out, 12, progress);
        return out.toByteArray();
    }
}
