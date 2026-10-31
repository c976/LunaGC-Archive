package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;

/**
 * The "is a Saurian in range" scan behind Natlan's soul-attach (龙之魂附).
 *
 * <p>The interaction key is a hold, not a tap: the client scans while the key is down and reports the
 * result as an {@code ABILITY_MIXIN_CHECK_SCAN_ENTITY} invoke whose payload carries the entity it picked.
 * An empty payload means the hold ended without a target. That is the whole signal - the client never
 * sends anything else for this interaction, so dropping the invoke loses the press entirely.
 *
 * <p>The scanned entity is remembered on the {@link emu.grasscutter.game.ability.AbilityManager} because
 * {@link TryEnterVehicleMixin} runs later, off a modifier attach, and the Saurian is not named anywhere in
 * that invoke.
 */
@AbilityMixin(value = AbilityMixinData.Type.CheckSubTagScanEntityMixin)
public class CheckSubTagScanEntityMixin extends AbilityMixinHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityMixinData abilityMixinData, ByteString byteString, GameEntity target) {
        if (ability == null || abilityMixinData == null) return false;

        GameEntity owner = target != null ? target : ability.getOwner();
        var manager = ability.getManager();
        if (manager == null) return false;

        int scannedId = firstScannedEntityId(byteString);
        GameEntity scanned =
                scannedId == 0 || owner == null || owner.getScene() == null
                        ? null
                        : owner.getScene().getEntityById(scannedId);

        manager.setScanTarget(scanned);

        AbilityModifierAction[] actions =
                scanned != null ? abilityMixinData.onSelectStart : abilityMixinData.onSelectEnd;

        if (actions == null || owner == null) return true;

        GameEntity actionTarget = scanned != null ? scanned : owner;
        for (AbilityModifierAction action : actions) {
            if (action == null) continue;
            manager.executeActionNow(ability, action, byteString, actionTarget);
        }
        return true;
    }

    /**
     * The scanned entity id out of the invoke payload, or 0 when the client sent an empty one.
     *
     * <p>The payload is {@code field 2 = packed repeated entity ids}; a capture reads
     * {@code 12 05 c3 81 80 02 00}, which is entity 4194499 followed by a zero the client pads the list
     * with. Parsed by hand because 7.1 ships no named message for this invoke.
     */
    public static int firstScannedEntityId(ByteString byteString) {
        if (byteString == null || byteString.isEmpty()) return 0;
        return new VarintReader(byteString.toByteArray()).firstPackedEntityId();
    }

    /** Minimal protobuf reader: only what this one payload needs. */
    private static final class VarintReader {
        private final byte[] bytes;
        private int at;
        private boolean truncated;

        VarintReader(byte[] bytes) {
            this.bytes = bytes;
        }

        int firstPackedEntityId() {
            while (at < bytes.length && !truncated) {
                long tag = varint();
                if (truncated) return 0;

                int fieldNumber = (int) (tag >>> 3);
                int wireType = (int) (tag & 7);

                if (wireType == 0) {
                    long value = varint();
                    if (!truncated && fieldNumber == 2 && value != 0) return (int) value;
                } else if (wireType == 2) {
                    int length = (int) varint();
                    if (truncated || length < 0 || at + length > bytes.length) return 0;
                    if (fieldNumber == 2) {
                        int end = at + length;
                        while (at < end) {
                            long value = varint();
                            if (truncated) return 0;
                            if (value != 0) return (int) value;
                        }
                    } else {
                        at += length;
                    }
                } else {
                    return 0;
                }
            }
            return 0;
        }

        private long varint() {
            long result = 0;
            int shift = 0;
            while (at < bytes.length) {
                byte b = bytes[at++];
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return result;
                shift += 7;
                if (shift > 63) break;
            }
            truncated = true;
            return 0;
        }
    }
}
