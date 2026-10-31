package emu.grasscutter.game.entity;

import emu.grasscutter.game.ability.ArlecchinoBoLUtil;
import emu.grasscutter.game.ability.ArlecchinoBurstBoL;
import emu.grasscutter.game.ability.ClorindeBoLUtil;
import emu.grasscutter.game.ability.EscoffierHealUtil;
import emu.grasscutter.game.ability.HutaoC6Helper;
import emu.grasscutter.game.ability.PartyReviveHelper;
import emu.grasscutter.game.ability.QiqiEHealHelper;
import emu.grasscutter.game.ability.ShinobuC6Helper;
import emu.grasscutter.game.ability.SkirkCunningBridge;
import emu.grasscutter.game.ability.SkirkCunningHelper;
import emu.grasscutter.game.ability.SkirkInvokeLog;
import emu.grasscutter.game.ability.actions.ActionAvatarSkillStart;
import emu.grasscutter.game.entity.gadget.GatherInteractHelper;

/** Releases static combat state when an entity leaves a scene. */
public final class EntityRuntimeStateCleanup {
    private EntityRuntimeStateCleanup() {}

    public static void clear(GameEntity entity) {
        if (entity == null) {
            return;
        }
        int entityId = entity.getId();
        ArlecchinoBoLUtil.clearEntityState(entityId);
        ArlecchinoBurstBoL.clearEntityState(entityId);
        ClorindeBoLUtil.clearEntityState(entityId);
        EscoffierHealUtil.clearEntityState(entityId);
        SkirkCunningBridge.clearEntityState(entityId);
        SkirkCunningHelper.clearEntityState(entityId);
        SkirkInvokeLog.clearEntityState(entityId);
        ActionAvatarSkillStart.clearEntityState(entityId);
        EnvironmentalSealHelper.clearEntityState(entityId);
        HutaoC6Helper.clearEntityState(entityId);
        // Per-entity cleanup of the Qiqi skill HoT, the Kuki Shinobu C6 cooldown and similar.
        ShinobuC6Helper.clearEntityState(entityId);
        QiqiEHealHelper.clearEntityState(entityId);
        GatherInteractHelper.clearEntityState(entityId);
    }
}
