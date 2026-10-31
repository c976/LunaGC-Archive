package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.server.packet.send.PacketGadgetInteractRsp;

/** MQ1015's material pickup and native quest-created Guizhong machine actors. */
public final class GadgetLiyueGuizhong extends GadgetContent implements CreditsOwnInteraction {
    private boolean used;

    public GadgetLiyueGuizhong(EntityGadget gadget) { super(gadget); }

    public static boolean matches(EntityGadget gadget) {
        if (gadget == null || gadget.getScene() == null || gadget.getScene().getId() != 3) return false;
        if (gadget.getGadgetId() == 71700102)
            return gadget.getGroupId() == 133107079 && gadget.getConfigId() == 79004;
        return (gadget.getGadgetId() == 71700100 || gadget.getGadgetId() == 71700107)
                && gadget.getGroupId() == 0 && gadget.getConfigId() == 0
                && gadget.getPosition().computeDistance(new Position(-378.988f, 290.558f, 674.031f)) <= 1;
    }

    @Override public boolean onInteract(Player player, GadgetInteractReq request) {
        var gadget = getGadget();
        synchronized (gadget) {
            int questId = switch (gadget.getGadgetId()) {
                case 71700100 -> 101508;
                case 71700102 -> 101504;
                case 71700107 -> 101505;
                default -> 0;
            };
            var main = player.getQuestManager().getMainQuestById(1015);
            var quest = main == null ? null : main.getChildQuestById(questId);
            if (!matches(gadget) || !QuestManager.isQuestingActive()
                    || player.getScene() != gadget.getScene()
                    || player.getPosition().computeDistance(gadget.getPosition()) > 6
                    || request.getGadgetId() != gadget.getGadgetId()
                    || main == null || main.isFinished()
                    || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE
                    || quest == null || quest.getState() != QuestState.QUEST_STATE_UNFINISHED
                    || (questId == 101505 && (player.getInventory().getItemCountById(100502) < 1
                        || player.getInventory().getItemCountById(100523) < 1))) {
                player.sendPacket(new PacketGadgetInteractRsp());
                return false;
            }
            player.sendPacket(new PacketGadgetInteractRsp(gadget,
                    InteractType.InteractType_INTERACT_GATHER, request.getOpType()));
            if (used) return false;
            used = true;
            // Native quest rewards, item consumption and suite transitions own the outcome.
            player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_INTERACT_GADGET,
                    gadget.getGadgetId());
            return true;
        }
    }

    @Override public void onBuildProto(SceneGadgetInfo.Builder info) {
        info.setIsEnableInteract(!used);
    }
}
