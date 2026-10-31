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
import emu.grasscutter.net.proto.WorktopInfoOuterClass.WorktopInfo;
import emu.grasscutter.scripts.constants.ScriptGadgetState;
import emu.grasscutter.server.packet.send.PacketGadgetInteractRsp;

/** MQ1021's native Meng Dan cage and quest sweet-flower placements only. */
public final class GadgetLiyueJadeMaterials extends GadgetContent implements CreditsOwnInteraction {
    private boolean collected;

    public GadgetLiyueJadeMaterials(EntityGadget gadget) { super(gadget); }

    public static boolean isCage(EntityGadget gadget) {
        return gadget != null && gadget.getScene() != null && gadget.getScene().getId() == 3
                && gadget.getGadgetId() == 70710113 && gadget.getGroupId() == 133107922
                && gadget.getConfigId() == 922011;
    }

    public static boolean matches(EntityGadget gadget) {
        if (isCage(gadget)) return true;
        if (gadget == null || gadget.getScene() == null || gadget.getScene().getId() != 3
                || gadget.getGadgetId() != 70710141) return false;
        return (gadget.getGroupId() == 133107922 && gadget.getConfigId() == 922008)
                || (gadget.getGroupId() == 0 && gadget.getConfigId() == 0
                    && gadget.getPosition().computeDistance(new Position(-223.886f, 186.248f, 936.073f)) <= 1);
    }

    private boolean canUse(Player player, int questId) {
        var gadget = getGadget();
        var main = player.getQuestManager().getMainQuestById(1021);
        var quest = main == null ? null : main.getChildQuestById(questId);
        return matches(gadget) && QuestManager.isQuestingActive()
                && player.getScene() == gadget.getScene()
                && player.getPosition().computeDistance(gadget.getPosition()) <= 6
                && main != null && !main.isFinished()
                && main.getState() == ParentQuestState.PARENT_QUEST_STATE_NONE
                && quest != null && quest.getState() == QuestState.QUEST_STATE_UNFINISHED;
    }

    public boolean openCage(Player player, int option) {
        var gadget = getGadget();
        synchronized (gadget) {
            if (option != 11 || !isCage(gadget) || !canUse(player, 102107)
                    || !gadget.getScene().getScriptManager().isClearedGroupMonsters(133107922)) return false;
            // Native group Lua observes GearStart and emits 10210201; do not bypass its task event.
            gadget.updateState(ScriptGadgetState.GearStart);
            return true;
        }
    }

    @Override public boolean onInteract(Player player, GadgetInteractReq request) {
        var gadget = getGadget();
        synchronized (gadget) {
            if (request.getGadgetId() != gadget.getGadgetId()
                    || (isCage(gadget) ? !openCage(player, 11) : !canUse(player, 102110))) {
                player.sendPacket(new PacketGadgetInteractRsp());
                return false;
            }
            player.sendPacket(new PacketGadgetInteractRsp(gadget,
                    InteractType.InteractType_INTERACT_GATHER, request.getOpType()));
            if (isCage(gadget) || collected) return false;
            collected = true;
            player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_INTERACT_GADGET, gadget.getGadgetId());
            return true;
        }
    }

    @Override public void onBuildProto(SceneGadgetInfo.Builder info) {
        if (isCage(getGadget())) {
            boolean closed = getGadget().getState() != ScriptGadgetState.GearStart;
            info.setIsEnableInteract(closed);
            var options = WorktopInfo.newBuilder();
            if (closed) options.addOptionList(11);
            info.setWorktop(options);
        } else info.setIsEnableInteract(!collected);
    }
}
