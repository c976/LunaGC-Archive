package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarFetterLevelRewardReqOuterClass.AvatarFetterLevelRewardReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;
import java.util.ArrayList;

/**
 * Claims a friendship reward. Besides the level 10 name card, some avatars have an earlier one
 * (Zibai and Linnea at level 1, Aino at level 6), and the client asks for each by its level.
 */
@Opcodes(PacketOpcodes.AvatarFetterLevelRewardReq)
public class HandlerAvatarFetterLevelRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        AvatarFetterLevelRewardReq req = AvatarFetterLevelRewardReq.parseFrom(payload);
        var player = session.getPlayer();
        long avatarGuid = req.getAvatarGuid();
        int fetterLevel = req.getFetterLevel();

        var avatar = player.getAvatars().getAvatarByGuid(avatarGuid);
        int rewardId =
                avatar == null || avatar.getAvatarData() == null
                        ? 0
                        : avatar.getAvatarData().getFetterRewards().get(fetterLevel);
        var reward = GameData.getRewardDataMap().get(rewardId);
        if (avatar == null || reward == null || avatar.getFetterLevel() < fetterLevel) {
            Grasscutter.getLogger()
                    .warn(
                            "[Fetter] uid={} cannot claim level {} reward of avatar guid {}"
                                    + " (reward {}, level {})",
                            player.getUid(),
                            fetterLevel,
                            avatarGuid,
                            rewardId,
                            avatar == null ? -1 : avatar.getFetterLevel());
            session.send(new PacketAvatarFetterLevelRewardRsp(avatarGuid, fetterLevel));
            return;
        }

        if (!avatar.getRewardedFetterLevels().contains(fetterLevel)) {
            var items = new ArrayList<GameItem>();
            for (var param : reward.getAllRewardItems()) {
                if (param.getId() <= 0 || param.getCount() <= 0) continue;
                // Single-copy rewards (Zibai's carriage box, Linnea's widget and codex pages) the
                // player already owns would hit the stack limit and refuse the whole claim.
                var data = GameData.getItemDataMap().get(param.getId());
                if (data != null
                        && data.getStackLimit() == 1
                        && player.getInventory().getItemCountById(param.getId()) > 0) {
                    continue;
                }
                items.add(new GameItem(param.getId(), param.getCount()));
            }
            // All or nothing: a full bag delivers none, so keep the level claimable for a retry.
            var added =
                    items.isEmpty()
                            ? null
                            : player.getInventory().addItems(items, ActionReason.FetterLevelReward);
            if (added != null && !added.allAccepted()) {
                Grasscutter.getLogger()
                        .warn(
                                "[Fetter] uid={} level {} reward {} of avatar {} not admitted: {}",
                                player.getUid(),
                                fetterLevel,
                                rewardId,
                                avatar.getAvatarId(),
                                added.entries());
                session.send(new PacketAvatarFetterLevelRewardRsp(avatarGuid, fetterLevel));
                return;
            }
            avatar.addRewardedFetterLevel(fetterLevel);
            avatar.save();
            if (items.stream().anyMatch(item -> item.getItemId() == avatar.getNameCardId())) {
                player.sendPacket(new PacketUnlockNameCardNotify(avatar.getNameCardId()));
            }
            Grasscutter.getLogger()
                    .debug(
                            "[Fetter] uid={} claimed level {} reward {} of avatar {}",
                            player.getUid(),
                            fetterLevel,
                            rewardId,
                            avatar.getAvatarId());
        }

        session.send(new PacketAvatarFetterDataNotify(avatar));
        session.send(new PacketAvatarFetterLevelRewardRsp(avatarGuid, fetterLevel, rewardId));
    }
}
