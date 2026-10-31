/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.EscoffierHealUtil;
import emu.grasscutter.game.ability.EscoffierSkillCookHelper;
import emu.grasscutter.game.ability.NyxHelper;
import emu.grasscutter.game.ability.PartyReviveHelper;
import emu.grasscutter.game.ability.QiqiEHealHelper;
import emu.grasscutter.game.ability.SkirkCunningBridge;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;

public final class NyxSkillHook {
    private static final int SKIRK_AVATAR_ID = 10000114;
    private static final int SKIRK_E_SKILL_ID = 11142;
    private static final int SKIRK_HOVER_SKILL_ID = 11147;
    private static final int SKIRK_Q_SKILL_ID = 11145;

    private NyxSkillHook() {
    }

    public static void handle(AbilityManager abilityManager, Player player, int n, int n2) {
        try {
            if (player == null || abilityManager == null || abilityManager.getPlayer() == null) {
                return;
            }
            if (player.getUid() != abilityManager.getPlayer().getUid()) {
                return;
            }
            EntityAvatar entityAvatar = player.getTeamManager().getCurrentAvatarEntity();
            if (entityAvatar == null || entityAvatar.getId() != n2 || entityAvatar.getAvatar() == null) {
                return;
            }
            int n3 = entityAvatar.getAvatar().getAvatarId();
            if (n3 == 10000112 && n == 11125) {
                EscoffierHealUtil.onBurstSkill(player, entityAvatar);
            }
            if (n3 == EscoffierSkillCookHelper.ESCOFFIER_AVATAR_ID
                    && n == EscoffierSkillCookHelper.HOLD_COOK_SKILL_ID) {
                EscoffierSkillCookHelper.onHoldCookSkill(player);
            }
            if (n3 == QiqiEHealHelper.QIQI_AVATAR_ID) {
                // Qiqi E: supply the Herald of Frost periodic heal. Q: C6 revives a downed teammate.
                QiqiEHealHelper.onSkillStart(player, entityAvatar, n);
                if (n == PartyReviveHelper.QIQI_BURST_SKILL
                        || (entityAvatar.getAvatar().getSkillDepot() != null
                                && n == entityAvatar.getAvatar().getSkillDepot().getEnergySkill())) {
                    PartyReviveHelper.onQiqiBurst(player, entityAvatar);
                }
            }
            if (!NyxHelper.isSkirkEntity(entityAvatar)) {
                return;
            }
            if (n3 == 10000114 && n == 11145) {
                SkirkCunningBridge.onBurstSkill(player, entityAvatar);
            } else if (n3 == 10000114 && (n == 11142 || n == 11147)) {
                SkirkCunningBridge.onESkillUiSync(player, entityAvatar, n);
            }
            SkirkCunningBridge.tickModeDrain(player, entityAvatar);
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("NyxSkillHook failed: {}", (Object)throwable.toString());
        }
    }
}
