package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketListProbe;
import emu.grasscutter.server.packet.send.PacketGetAllUnlockNameCardRsp;

/** 7.1 头像/头像框列表。
 * 请求：客户端打开更换界面 -> GetProfilePictureDataReq(27037, len=0)
 * 应答：22477 = MFLHNBCGNJP { repeated uint32 @4 = 头像序号；@15 = 头像框 id；int32 @9 = retcode }
 * 内容照抄实测成功那组：f4 = 1..50，f15 = 210001..210049，retcode 0
 * 注意：客户端刚发请求时界面尚未就绪，立即回包会被丢，因此 立即发 + 800ms 再补发。 */
@Opcodes(PacketOpcodes.GetProfilePictureDataReq)
public class HandlerGetProfilePictureDataReq extends PacketHandler {
    public static final int MIAO_PROFILE_RSP = 22477;
    public static String avatarSeqList() {
        StringBuilder a = new StringBuilder();
        for (emu.grasscutter.game.player.BeyondProfilePictureTable.Entry e : emu.grasscutter.game.player.BeyondProfilePictureTable.all()) { if (a.length() > 0) a.append(','); a.append(e.id); }
        return a.toString();
    }
    public static String frameIdList() {
        StringBuilder b = new StringBuilder();
        for (int i : new int[] {100000, 100011, 100012, 100013, 100014}) { if (b.length() > 0) b.append(','); b.append(i); }
        return b.toString();
    }
    public static void sendProfileList(GameSession session, String tag) {
        var player = session.getPlayer();
        if (player == null) return;
        // [v32] 先把头像/头像框的解锁条件在客户端侧伪造为已满足（任务/道具/角色），再发列表
        try {
            player.getProgressManager().forgeProfilePictureUnlocks();
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("MIAO forgeProfilePicture call fail: {}", t.toString());
        }
        session.send(new PacketListProbe(player, MIAO_PROFILE_RSP, 4, 15, 9, avatarSeqList(), frameIdList()));
        Grasscutter.getLogger()
                .debug(
                        "MIAO nameCardList size={} list={}",
                        player.getNameCardList().size(),
                        new java.util.TreeSet<>(player.getNameCardList()));
        session.send(new PacketGetAllUnlockNameCardRsp(player));
        try { session.send(new emu.grasscutter.server.packet.send.PacketBeyondProfilePictureDataNotify(player)); } catch (Throwable t) { emu.grasscutter.Grasscutter.getLogger().warn("MIAO 6326 fail: {}", t.toString()); }
        Grasscutter.getLogger().debug("MIAO {} -> reply {} uid={}", tag, MIAO_PROFILE_RSP, player.getUid());
    }
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        sendProfileList(session, "GetProfilePictureDataReq(now)");
        // [喵喵 v7] 精简推送：立即 1 次 + 第 2 秒 + 第 4 秒，共 3 次：覆盖用户切到名片页签的任意时刻
        // （实测：界面刚打开的 0~1.6 秒内客户端不收数据，停稳后才收）
        // [v16] cycle 推送已移除（推送只由 5963 触发，共 2 次）
    }
}
