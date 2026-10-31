package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTakePlayerLevelRewardRsp;
import java.io.IOException;
import java.util.*;

@Opcodes(PacketOpcodes.TakePlayerLevelRewardReq)
public class HandlerTakePlayerLevelRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int level;
        try {
            level = readLevel(payload);
        } catch (IOException exception) {
            session.send(new PacketTakePlayerLevelRewardRsp(0, 0));
            return;
        }
        Player pl = session.getPlayer();
        if (pl == null) {
            session.send(new PacketTakePlayerLevelRewardRsp(level, 0));
            return;
        }
        synchronized (pl) {
            var levelData = GameData.getPlayerLevelDataMap().get(level);
            if (level <= 0
                    || level > pl.getLevel()
                    || levelData == null
                    || levelData.getRewardId() <= 0) {
                session.send(new PacketTakePlayerLevelRewardRsp(level, 0));
                return;
            }
            int rewardId = levelData.getRewardId();
            Set<Integer> rewardedLevels = pl.getRewardedLevels();
            if (rewardedLevels != null && rewardedLevels.contains(level)) {
                session.send(new PacketTakePlayerLevelRewardRsp(level, rewardId));
                return;
            }
            var rewardData = GameData.getRewardDataMap().get(rewardId);
            if (rewardData == null
                    || rewardData.getRewardItemList() == null
                    || rewardData.getRewardItemList().isEmpty()) {
                session.send(new PacketTakePlayerLevelRewardRsp(level, 0));
                return;
            }
            List<ItemParamData> rewardItems = rewardData.getRewardItemList();
            pl.getInventory().addItemParamDatas(rewardItems, ActionReason.PlayerUpgradeReward);
            if (rewardedLevels == null) rewardedLevels = new HashSet<>();
            rewardedLevels.add(level);
            pl.setRewardedLevels(rewardedLevels);
            pl.save();
            session.send(new PacketTakePlayerLevelRewardRsp(level, rewardId));
        }
    }

    /** Validate the complete 7.1 field-15 request before entering the reward boundary. */
    private static int readLevel(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0) return 0;
        CodedInputStream input = CodedInputStream.newInstance(payload);
        int level = 0;
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if ((tag >>> 3) == 15) {
                if ((tag & 7) != 0) throw new IOException("Invalid rank wire type");
                level = input.readUInt32();
            } else if (!input.skipField(tag)) {
                throw new IOException("Unexpected end-group in rank request");
            }
        }
        return level;
    }
}
