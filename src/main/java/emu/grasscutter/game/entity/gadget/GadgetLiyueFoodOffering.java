package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.enums.ParentQuestState;
import emu.grasscutter.game.quest.enums.QuestState;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.GadgetInteractRspOuterClass.GadgetInteractRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.scripts.constants.ScriptGadgetState;

/** MQ1009's three native consume-food/state-change interactions. */
public final class GadgetLiyueFoodOffering extends GadgetContent {
    public GadgetLiyueFoodOffering(EntityGadget gadget) { super(gadget); }

    public static boolean matches(EntityGadget gadget) {
        int offset = gadget.getGroupId() - 133101914;
        var meta = gadget.getMetaGadget();
        return gadget.getScene().getId() == 3 && offset >= 0 && offset < 3
                && gadget.getGadgetId() == 70710120 + offset
                && gadget.getConfigId() == 914001 + offset * 1000
                && meta != null && meta.interact_id == 8 + offset;
    }

    public int submit(Player player) {
        var gadget = getGadget();
        if (!matches(gadget) || !QuestManager.isQuestingActive() || player.getScene() != gadget.getScene())
            return Retcode.RET_SVR_ERROR_VALUE;
        synchronized (gadget) {
            if (gadget.getState() == ScriptGadgetState.GearStart) return 0;
            var main = player.getQuestManager().getMainQuestById(1009);
            int offset = gadget.getGroupId() - 133101914;
            var quest = main != null ? main.getChildQuestById(100907 + offset) : null;
            var entry = main != null ? main.getChildQuestById(100906) : null;
            if (main == null || main.getParentQuestId() != 1009 || main.isFinished()
                    || main.getState() != ParentQuestState.PARENT_QUEST_STATE_NONE
                    || quest == null || quest.getState() != QuestState.QUEST_STATE_UNFINISHED
                    || entry == null || entry.getState() != QuestState.QUEST_STATE_FINISHED
                    || gadget.getState() != ScriptGadgetState.Default
                    || player.getPosition().computeDistance(gadget.getPosition()) > 6)
                return Retcode.RET_SVR_ERROR_VALUE;
            int food = new int[] {108088, 108094, 108091}[offset];
            var inventory = player.getInventory();
            synchronized (inventory) {
                if (inventory.getItemCountById(food) < 1 || !inventory.removeItemById(food, 1))
                    return Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE;
                player.save();
                // Native Lua owns notification/progression; keep the object until its finish action unloads it.
                gadget.updateState(ScriptGadgetState.GearStart);
            }
            return 0;
        }
    }

    @Override public boolean onInteract(Player player, GadgetInteractReq request) {
        int result = submit(player);
        var reply = new BasePacket(PacketOpcodes.GadgetInteractRsp);
        reply.setData(GadgetInteractRsp.newBuilder().setRetcode(result)
                .setGadgetEntityId(getGadget().getId()).setGadgetId(getGadget().getGadgetId())
                .setInteractType(emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType.InteractType_INTERACT_CONSUM)
                .setOpType(request.getOpType()).build());
        player.sendPacket(reply);
        return false;
    }

    @Override public void onBuildProto(SceneGadgetInfo.Builder info) {
        info.setInteractId(getGadget().getMetaGadget().interact_id)
                .setIsEnableInteract(getGadget().getState() == ScriptGadgetState.Default);
    }
}
