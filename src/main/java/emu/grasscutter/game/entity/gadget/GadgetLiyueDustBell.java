package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.LiyueTeapotDungeonSupport;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.server.packet.send.PacketGadgetInteractRsp;

/** The native QuestGadget needs an acknowledgement before MQ1014 can leave the domain. */
public final class GadgetLiyueDustBell extends GadgetContent implements CreditsOwnInteraction {
    private boolean collected;

    public GadgetLiyueDustBell(EntityGadget gadget) { super(gadget); }

    public static boolean matches(EntityGadget gadget) {
        return gadget != null && LiyueTeapotDungeonSupport.isTargetScene(gadget.getScene())
                && gadget.getGroupId() == 201017016 && gadget.getConfigId() == 16002
                && gadget.getGadgetId() == 71700099;
    }

    @Override public boolean onInteract(Player player, GadgetInteractReq request) {
        var gadget = getGadget();
        synchronized (gadget) {
            if (collected) {
                player.sendPacket(new PacketGadgetInteractRsp(gadget,
                        InteractType.InteractType_INTERACT_GATHER, request.getOpType()));
                return false;
            }
            var quest = player.getQuestManager().getQuestById(101405);
            if (!matches(gadget) || !QuestManager.isQuestingActive()
                    || player.getScene() != gadget.getScene()
                    || player.getPosition().computeDistance(gadget.getPosition()) > 6
                    || quest == null || quest.getMainQuestId() != 1014
                    || quest.getState() != QuestState.QUEST_STATE_UNFINISHED) {
                player.sendPacket(new PacketGadgetInteractRsp());
                return false;
            }
            collected = true;
            player.sendPacket(new PacketGadgetInteractRsp(gadget,
                    InteractType.InteractType_INTERACT_GATHER, request.getOpType()));
            // The quest's gainItems and existing native Lua notification own the reward and exit.
            player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_INTERACT_GADGET,
                    gadget.getGadgetId());
            return true;
        }
    }

    @Override public void onBuildProto(SceneGadgetInfo.Builder info) {
        info.setIsEnableInteract(!collected);
    }
}
