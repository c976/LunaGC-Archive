package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.ability.ArlecchinoBoLUtil;
import emu.grasscutter.game.ability.ArlecchinoBurstBoL;
import emu.grasscutter.game.ability.BurstInvulnHelper;
import emu.grasscutter.game.ability.ClorindeBoLUtil;
import emu.grasscutter.game.ability.EscoffierHealUtil;
import emu.grasscutter.game.ability.HutaoC6Helper;
import emu.grasscutter.game.ability.LaumaC1HealHelper;
import emu.grasscutter.game.ability.ShareCDHelper;
import emu.grasscutter.game.ability.MavuikaSpiritHelper;
import emu.grasscutter.game.ability.PartyReviveHelper;
import emu.grasscutter.game.ability.QiqiEHealHelper;
import emu.grasscutter.game.ability.ShinobuC6Helper;
import emu.grasscutter.game.ability.SkirkCunningBridge;
import emu.grasscutter.game.ability.SkirkCunningHelper;
import emu.grasscutter.game.ability.SkirkInvokeLog;
import emu.grasscutter.game.ability.SymphonistWeaponHelper;
import emu.grasscutter.game.ability.XilonenC6HealHelper;
import emu.grasscutter.game.ability.actions.ActionAvatarSkillStart;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.avatar.SkirkTeamBonusHelper;
import emu.grasscutter.game.avatar.TartagliaTeamBonusHelper;
import emu.grasscutter.game.ability.LohenExtraArtSkillLevelHelper;
import emu.grasscutter.game.battlepass.BattlePassCompatHelper;
import emu.grasscutter.game.dungeons.DomainContinueSpawnHelper;
import emu.grasscutter.game.tower.TowerAbyssFix;

/** Clears transient static state that otherwise outlives a player's session. */
public final class PlayerRuntimeStateCleanup {
    private PlayerRuntimeStateCleanup() {}

    public static void clear(Player player) {
        if (player == null) {
            return;
        }

        int uid = player.getUid();
        run("Arlecchino", () -> ArlecchinoBoLUtil.clearPlayerState(player));
        run("Arlecchino burst", () -> ArlecchinoBurstBoL.clearPlayerState(player));
        run("BurstInvuln", () -> BurstInvulnHelper.clearPlayerState(uid));
        run("Clorinde", () -> ClorindeBoLUtil.clearPlayerState(player));
        run("Escoffier", () -> EscoffierHealUtil.clearPlayerState(player));
        run("Mavuika", () -> MavuikaSpiritHelper.clearPlayerState(uid));
        run("Skirk bridge", () -> SkirkCunningBridge.clearPlayerState(player));
        run("Skirk helper", () -> SkirkCunningHelper.clearPlayerState(player));
        run("Skirk invoke log", () -> SkirkInvokeLog.clearPlayerState(player));
        run("Skirk team bonus", () -> SkirkTeamBonusHelper.clearPlayerState(player));
        run("Tartaglia team bonus", () -> TartagliaTeamBonusHelper.clearPlayerState(player));
        run("Lohen ExtraArt level", () -> LohenExtraArtSkillLevelHelper.clearPlayerState(player));
        run("Symphonist", () -> SymphonistWeaponHelper.clearPlayerState(player));
        run("Xilonen", () -> XilonenC6HealHelper.clearPlayerState(uid));
        run("HutaoC6", () -> HutaoC6Helper.clearPlayerState(player));
        // Qiqi and Barbara C6 revive cooldowns, Kuki Shinobu C6, and the Qiqi E sustained heal.
        run("ShinobuC6", () -> ShinobuC6Helper.clearPlayerState(player));
        run("PartyRevive", () -> PartyReviveHelper.clearPlayerState(player));
        run("QiqiEHeal", () -> QiqiEHealHelper.clearPlayerState(player));
        run("AvatarSkillStart", () -> ActionAvatarSkillStart.clearPlayerState(player));
        run("Avatar fight prop coalesce", () -> Avatar.clearFightPropNotifyState(player));
        run("BattlePass", () -> BattlePassCompatHelper.clearPlayerState(uid));
        run("Domain continue", () -> DomainContinueSpawnHelper.clearPlayerState(uid));
        run("Tower", () -> TowerAbyssFix.clearEntry(player));
        run("LaumaC1", () -> LaumaC1HealHelper.clearPlayerState(player));
        run("ShareCD", () -> ShareCDHelper.clearPlayer(uid));
        run("Dive", () -> DiveAbilityHelper.clearPlayer(uid));
        // The statue auto-heal timer is a thread of its own and keeps the player alive otherwise.
        run("Statue", () -> player.getSotsManager().handleExitTransPointRegionNotify());
        run("StatueProbe", () -> player.getSotsManager().stopProximityProbe());
    }

    private static void run(String name, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Throwable throwable) {
            // A cleanup failure must not skip player persistence or disconnect handling.
            Grasscutter.getLogger().warn("Player runtime cleanup failed for {}", name, throwable);
        }
    }
}
