package emu.grasscutter.game.ability;

import emu.grasscutter.net.proto.AvatarEscoffierSkillCookDataNotify;
import emu.grasscutter.net.proto.AvatarEscoffierSkillCookRsp;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.packet.BasePacket;

/**
 * Escoffier's improvised cooking packets, built from the generated 7.1 classes.
 *
 * <p>The path known to work: the client draws the charge bar and sends SkillCookReq, with <strong>no</strong>
 * DataNotify from the server.
 * Getting a DataNotify field wrong puts the client into CannotCreateFood and the charge ring disappears.
 *
 * <p>The unlock/allow-cooking notify sends only remain@1 and max@2, never a refresh field; remain is 0 at
 * the cap.
 */
final class EscoffierSkillCookProto {
    private EscoffierSkillCookProto() {}

    /** Successful dish response. */
    static BasePacket buildCookRsp(int itemId, int count) {
        var rsp =
                AvatarEscoffierSkillCookRsp._AvatarEscoffierSkillCookRsp.newBuilder()
                        .addItemList(ItemParam.newBuilder().setItemId(itemId).setCount(count));
        return simplePacket(EscoffierSkillCookOpcodes.COOK_RSP, rsp.build().toByteArray());
    }

    /** Failed dish response. */
    static BasePacket buildCookRspError(int retcode) {
        var rsp =
                AvatarEscoffierSkillCookRsp._AvatarEscoffierSkillCookRsp.newBuilder().setRetcode(retcode);
        return simplePacket(EscoffierSkillCookOpcodes.COOK_RSP, rsp.build().toByteArray());
    }

    /**
     * Minimal quota packet: only the weekly finished count. The refresh time is deliberately left
     * out - a wrong one poisons CanCook and the charge UI.
     */
    static BasePacket buildCookDataNotify(int usedCount, int maxWeekly, long nextResetEpochSec) {
        var notify =
                AvatarEscoffierSkillCookDataNotify._AvatarEscoffierSkillCookDataNotify.newBuilder()
                        .setFinishedWeeklyCookNum(Math.max(0, Math.min(usedCount, maxWeekly)));
        return simplePacket(EscoffierSkillCookOpcodes.COOK_DATA_NOTIFY, notify.build().toByteArray());
    }

    private static BasePacket simplePacket(int opcode, byte[] data) {
        BasePacket packet = new BasePacket(opcode);
        packet.setData(data);
        return packet;
    }



}
