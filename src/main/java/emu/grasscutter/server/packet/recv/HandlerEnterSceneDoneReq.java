package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EnterSceneDoneReqOuterClass.EnterSceneDoneReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.ChangeHpDebtsReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.EnterSceneDoneReq)
public class HandlerEnterSceneDoneReq extends PacketHandler {
    /** 本次运行内已经给客户端同步过解锁通知的点（防止重复弹提示）。 */
    private static final java.util.Set<String> MIAO_UNLOCK_SYNCED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();


    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        EnterSceneDoneReq req = EnterSceneDoneReq.parseFrom(payload);

        var player = session.getPlayer();

        // Finished loading
        player.setSceneLoadState(SceneLoadState.LOADED);
        // 7.0 移植：进场景完成时校正世界归属
        try {
            session.getServer().getMultiplayerSystem().reconcileMultiplayerWorld(player);
        } catch (Throwable t) {
        }

        // Suppress LUA SetMonsterBattleByGroup ForceAlert while nearby groups bootstrap
        // under the player's feet (ENTER_REGION false-fires, giving a hilichurl horn on login/reload).
        try {
            emu.grasscutter.game.world.WorldBossSpawnHelper.markPlayerTeleportGrace(player);
        } catch (Throwable ignored) {
        }

        // Done

        session.send(new PacketPlayerTimeNotify(player)); // Probably not the right place

        // Spawn player in world
        player.getScene().spawnPlayer(player);

        // Spawn other entites already in world
        player.getScene().showOtherEntities(player);

        // Snezhnaya Ice Creatures the player built here stand again.
        emu.grasscutter.game.icecreature.IceCreatureSystem.onEnterSceneDone(player);
        // The Snezhnaya player train Grey Eyes calls; the client refuses to call without it.
        emu.grasscutter.game.train.SnezhnayaTrainSystem.onEnterSceneDone(player);

        // Locations
        session.send(new PacketWorldPlayerLocationNotify(player.getWorld()));
        session.send(new PacketScenePlayerLocationNotify(player.getScene()));
        session.send(new PacketWorldPlayerRTTNotify(player.getWorld()));
        var avatarEntity = player.getTeamManager().getCurrentAvatarEntity();
        float currentHpDebts = avatarEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (currentHpDebts > 0.0f) {
            avatarEntity.getWorld().broadcastPacket(new PacketEntityFightPropChangeReasonNotify(avatarEntity, FightProperty.FIGHT_PROP_CUR_HP_DEBTS, currentHpDebts, PropChangeReasonOuterClass.PropChangeReason.PropChangeReason_PROP_CHANGE_NONE, ChangeHpDebtsReason._ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_NONE));
        }
        // spawn NPC
        player.getScene().loadNpcForPlayerEnter(player);

        // notify client to load the npc for quest
        var questGroupSuites = player.getQuestManager().getSceneGroupSuite(player.getSceneId());

        player.getScene().loadGroupForQuest(questGroupSuites);
        Grasscutter.getLogger()
                .trace("Loaded Scene {} Quest(s) Groupsuite(s): {}", player.getSceneId(), questGroupSuites);
        session.send(new PacketGroupSuiteNotify(questGroupSuites));

        // Commission monsters live in dynamic groups exactly like the quest ones above, and nothing
        // else brings them in: generation only loads them when the player already happens to be
        // standing in Teyvat with a live scene, which is never true at login. Without this the
        // commission is issued and tracked but its monsters never spawn.
        var dailyTaskManager = player.getDailyTaskManager();
        if (dailyTaskManager != null) {
            dailyTaskManager.loadActiveGroups(player.getScene());
        }

        // Reset timer for sending player locations
        player.resetSendPlayerLocTime();

        try {
            emu.grasscutter.game.player.DomainHandbookHelper.onEnterScene(player, player.getSceneId());
        } catch (Throwable ignored) {
        }

        // Artifact Transmuter: re-send Offer after entering the scene. On a slow cold start the delayed
        // StoreNotify task from onLogin may not have fired yet.
        try {
            emu.grasscutter.game.systems.ArtifactTransmuterSystem.sendLoginNotifyOnce(player);
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("ArtifactTransmuter login Offer (EnterSceneDone) failed uid={}: {}", player.getUid(), t.toString());
        }

        // ===== 进场景时主动刷新神像代理（不依赖客户端 EnterTransPoint 通知）=====
        try {
            int scId = player.getSceneId();
            var me = player.getPosition();
            java.util.List<Object[]> cands = new java.util.ArrayList<>();
            int statueCnt = 0;
            for (var e : emu.grasscutter.data.GameData.getScenePointEntryMap().values()) {
                if (e == null || e.getPointData() == null) continue;
                if (e.getSceneId() != scId) continue;
                if (!emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(e.getPointData()))
                    continue;
                statueCnt++;
                var pd = e.getPointData();
                var pos = pd.getTranPos() != null ? pd.getTranPos() : pd.getPos();
                double d = 999999.0;
                if (me != null && pos != null) {
                    try {
                        d = me.computeDistance(pos);
                    } catch (Throwable ignored) {
                    }
                }
                cands.add(new Object[] {pd.getId(), d});
            }
            cands.sort(
                    (x, y) -> Double.compare((Double) x[1], (Double) y[1]));
            int limit = Math.min(1, cands.size());
            StringBuilder done = new StringBuilder();
            for (int i = 0; i < limit; i++) {
                int pid = (Integer) cands.get(i)[0];
                try {
                    if (player.getGoddessNpcEntityByPoint().containsKey(pid)) continue;
                    // Do not change activation state. Refresh only interactions for activated statues.
                    player.getProgressManager().refreshStatueGoddessInteractions(scId, pid);
                    done.append(pid).append(' ');
                } catch (Throwable t) {
                }
            }
        } catch (Throwable t) {
        }
        // Statue auto-heal needs to know the player is standing at a statue, and 7.1's
        // EnterTransPointRegionNotify CmdId is unknown, so the client's own region notification
        // cannot be relied on. Drive it from position instead.
        try {
            player.getSotsManager().startProximityProbe();
        } catch (Throwable t) {
        }
        // TPS ammunition reserves, which the client expects before the Rsp.
        emu.grasscutter.game.tps.TpsWeaponSystem.sendSceneAmmunition(player);

        // Rsp
        session.send(new PacketEnterSceneDoneRsp(player));
        if (player.getSceneId() == 3) {
            player.getScene().getBlossomManager().notifyIcon(player);
        }
        emu.grasscutter.game.quest.AmberFlightExamSupport.onSceneLoaded(player);
        emu.grasscutter.game.quest.MondstadtStoryQuestSupport.onSceneLoaded(player);
    }
}
