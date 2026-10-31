package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.AbilityActionSummonOuterClass.AbilityActionSummon;
import emu.grasscutter.server.packet.send.PacketMonsterSummonTagNotify;
import emu.grasscutter.utils.*;

@AbilityAction(AbilityModifierAction.Type.Summon)
public class ActionSummon extends AbilityActionHandler {
    @Override
    public synchronized boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        AbilityActionSummon summonPosRot = null;
        try {
            // In game version 4.0, summoned entity's
            // position and rotation are packed in EPKDEHOJFLI.
            // This is packet AbilityActionSummon and has two fields:
            //  4: Vector pos
            //  13: Vector rot
            summonPosRot = AbilityActionSummon.parseFrom(abilityData);
        } catch (InvalidProtocolBufferException e) {
            Grasscutter.getLogger()
                    .error("Failed to parse abilityData: {}", Utils.bytesToHex(abilityData.toByteArray()));
            return false;
        }

        var pos = resolveSummonPosition(target, action, summonPosRot.getPos());
        var rot = new Position(summonPosRot.getRot());
        var monsterId = action.monsterID;

        var scene = target.getScene();

        var monsterData = GameData.getMonsterDataMap().get(monsterId);
        if (monsterData == null) {
            Grasscutter.getLogger().error("Failed to find monster by ID {}", monsterId);
            return false;
        }

        if (target instanceof EntityMonster ownerEntity) {
            var level = scene.getLevelForMonster(0, ownerEntity.getLevel());
            var entity = new EntityMonster(scene, monsterData, pos, rot, level);
            ownerEntity.getSummonTagMap().put(action.summonTag, entity);
            entity.setSummonedTag(action.summonTag);
            entity.setOwnerEntityId(target.getId());
            scene.addEntity(entity);
            scene.getPlayers().get(0).sendPacket(new PacketMonsterSummonTagNotify(ownerEntity));

            Grasscutter.getLogger()
                    .trace(
                            "Spawned entityId {} monsterId {} pos {} rot {}, target { {} }, action { {} }",
                            entity.getId(),
                            monsterId,
                            pos,
                            rot,
                            target,
                            action);

            return true;
        } else {
            return false;
        }
    }

    /**
     * The position to place this summon at.
     *
     * <p>A summon born on a global position key takes the server's copy of that key, which
     * {@link ActionSetGlobalPos} wrote. The vector packed in the notify is not usable there: proto3
     * leaves zero-valued fields out of the wire, and the client has nothing to report for a position
     * the server never told it about, so it parses back as the world origin - thousands of units from
     * the caster, where the entity falls through the terrain and {@code die_y} culls it.
     *
     * <p>Born types that are not keyed on a global position keep the client's value, which is
     * correct for them.
     */
    private static Position resolveSummonPosition(
            GameEntity caster,
            AbilityModifierAction action,
            emu.grasscutter.net.proto.VectorOuterClass.Vector clientPos) {
        var born = action.born;
        if (born != null && "ConfigBornByGlobalValue".equals(String.valueOf(born.get("$type")))) {
            Object key = born.get("positionKey");
            if (key != null && caster != null) {
                Position stored = caster.getGlobalAbilityPositions().get(String.valueOf(key));
                if (stored != null) return new Position(stored);
            }
        }
        return new Position(clientPos);
    }
}