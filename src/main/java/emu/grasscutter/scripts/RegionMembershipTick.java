package emu.grasscutter.scripts;

import static emu.grasscutter.GameConstants.ENTITY_ID_BIT_SHIFT;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityRegion;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Map;

/** Region membership updates without stream pipelines or quadratic ID lookups. */
public final class RegionMembershipTick {
    private RegionMembershipTick() {}

    public static void check(SceneScriptManager manager, Map<Integer, EntityRegion> regions) {
        if (regions.isEmpty()) {
            return;
        }

        for (var region : regions.values()) {
            var entities = new ArrayList<GameEntity>();
            // An earlier region event can spawn or remove entities for this region.
            for (var entity : manager.getScene().getEntities().values()) {
                if (region.getMetaRegion().contains(entity.getPosition())) {
                    entities.add(entity);
                }
            }

            var entityIds = new IntOpenHashSet(entities.size());
            var orderedIds = new int[entities.size()];
            for (int i = 0; i < entities.size(); i++) {
                var entityId = entities.get(i).getId();
                orderedIds[i] = entityId;
                entityIds.add(entityId);
            }

            var enterEntities = new ArrayList<Integer>();
            for (var entityId : orderedIds) {
                if (!region.getEntities().contains(entityId)) {
                    enterEntities.add(entityId);
                }
            }

            var leaveEntities = new ArrayList<Integer>();
            for (var entityId : region.getEntities()) {
                if (!entityIds.contains(entityId.intValue())) {
                    leaveEntities.add(entityId);
                }
            }

            for (var entity : entities) {
                region.addEntity(entity);
            }

            for (var targetId : enterEntities) {
                if (EntityIdType.toEntityType(targetId >> ENTITY_ID_BIT_SHIFT).getValue() == 19) {
                    continue;
                }
                Grasscutter.getLogger()
                        .trace("Call EVENT_ENTER_REGION_{}", region.getMetaRegion().config_id);
                manager.callEvent(
                        new ScriptArgs(region.getGroupId(), EventType.EVENT_ENTER_REGION, region.getConfigId())
                                .setEventSource(EntityIdType.toEntityType(targetId >> ENTITY_ID_BIT_SHIFT).getValue())
                                .setSourceEntityId(region.getId())
                                .setTargetEntityId(targetId));
            }

            // Event callbacks can move or remove an entity; retain the live cleanup check.
            for (var entityId : region.getEntities()) {
                var entity = manager.getScene().getEntityById(entityId);
                if (entity == null || !region.getMetaRegion().contains(entity.getPosition())) {
                    region.removeEntity(entityId);
                }
            }

            for (var targetId : leaveEntities) {
                if (EntityIdType.toEntityType(targetId >> ENTITY_ID_BIT_SHIFT).getValue() == 19) {
                    continue;
                }
                manager.callEvent(
                        new ScriptArgs(region.getGroupId(), EventType.EVENT_LEAVE_REGION, region.getConfigId())
                                .setEventSource(EntityIdType.toEntityType(targetId >> ENTITY_ID_BIT_SHIFT).getValue())
                                .setSourceEntityId(region.getId())
                                .setTargetEntityId(targetId));
            }
        }
    }
}
