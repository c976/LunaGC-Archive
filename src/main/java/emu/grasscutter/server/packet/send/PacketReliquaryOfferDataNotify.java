package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.systems.ArtifactTransmuterSystem;
import emu.grasscutter.game.systems.ArtifactTransmuterSystem.PlayerState;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.utils.ProtoWire;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * FLIJGBNHOBC (26781). Field numbers follow the sniffed all.proto; the 2/5 numbering in the fix pack's
 * README is wrong:
 *
 *   OPLHCEOLCFB = 2   // extracted count this cycle (or the produced delta); the client computes
 *                     // remaining = ConstMax - this value
 *   ACDENEEFIEJ = 8   // schedule id = 700 (required)
 *   CMBJDHDHLCC = 12  // extraction progress
 *   PECGJPBDOAI = 13  // list of already defined sets, PDENDJIGPKH
 *   ILJNAEPANNC = 15  // cycle end, unix time
 *
 * Nested PDENDJIGPKH: FAODDMOJFNN=9 setId, NOLPABPANIH=11 count
 *
 * <p>Private-server notes:
 * - The client ConstValue extraction cap defaults to 1, so sending field2 as the extracted count locks
 *   the UI immediately. field2 is therefore not sent by default.
 * - The cap on how often a set may be defined lives in ConstValue PURCHASE_RELIQUARY_PARAM (default 2),
 *   and field13 decrements the remaining count.
 */
public class PacketReliquaryOfferDataNotify extends BasePacket {

    public PacketReliquaryOfferDataNotify(PlayerState state) {
        this(state, false);
    }

    /**
     * @param announceExtracted when true, send this cycle's extracted count; the client needs
     *     ConstMax &gt;= 10 for this not to lock up
     */
    public PacketReliquaryOfferDataNotify(PlayerState state, boolean announceExtracted) {
        super(PacketOpcodes.ReliquaryOfferDataNotify);
        // 7.1 field numbers, carried over from 7.0 (2/8/12/13{9,11}/15) by the structural match; the
        // four uint32 fields share a type, so which is which follows the match, not a capture.
        if (state == null) {
            state = new PlayerState();
        }
        ArtifactTransmuterSystem.ensureCycle(state);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        if (announceExtracted && state.extractedThisCycle > 0) {
            ProtoWire.writeUint32Force(out, 5, state.extractedThisCycle);
        }

        ProtoWire.writeUint32Force(out, 7, ArtifactTransmuterSystem.SCHEDULE_ID);

        if (state.progress > 0) {
            ProtoWire.writeUint32Force(out, 11, state.progress);
        }

        // Sending the defined-set list makes the client decrement "remaining definable" per ConstValue,
        // which defaults to 2 per set.
        // To relax that on a private server the list is not synced by default, so the UI does not stick at
        // 0/2; the server still keeps its own tally.
        boolean syncDefinedSuites = false;
        if (syncDefinedSuites && state.definedSuites != null && !state.definedSuites.isEmpty()) {
            for (Map.Entry<Integer, Integer> e : state.definedSuites.entrySet()) {
                ByteArrayOutputStream nest = new ByteArrayOutputStream();
                ProtoWire.writeUint32Force(nest, 13, e.getKey());
                ProtoWire.writeUint32Force(nest, 12, e.getValue());
                ProtoWire.writeBytes(out, 13, nest.toByteArray());
            }
        }

        ProtoWire.writeUint32Force(out, 8, ArtifactTransmuterSystem.cycleEndTime(state));
        this.setData(out.toByteArray());
    }
}
