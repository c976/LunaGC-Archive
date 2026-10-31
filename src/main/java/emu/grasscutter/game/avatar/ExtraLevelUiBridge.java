/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.avatar;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.avatar.AvatarExtraLevelHelper;
import emu.grasscutter.game.avatar.AvatarExtraLevelOpcodes;
import emu.grasscutter.game.avatar.AvatarGuidCodec;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.AvatarExtraLevelUpgradeReqParser;
import emu.grasscutter.net.proto.AvatarPromoteReqParser;
import emu.grasscutter.net.proto.ParsedExtraLevelUpgradeReq;
import java.util.Set;

public final class ExtraLevelUiBridge {
    private static final int MAX_PAYLOAD = 256;
    private static final int UNION_CMD_NOTIFY = PacketOpcodes.UnionCmdNotify;
    private static final int PROMOTE_REQ = PacketOpcodes.AvatarPromoteReq;
    private static final Set<Integer> IGNORE = Set.of(Integer.valueOf(1211), Integer.valueOf(119), Integer.valueOf(24997), Integer.valueOf(27433), Integer.valueOf(28092), Integer.valueOf(23961), Integer.valueOf(1185), Integer.valueOf(7886), Integer.valueOf(4646));

    private ExtraLevelUiBridge() {
    }

    public static boolean tryHandle(Player player, int n, byte[] byArray) {
        if (player == null || byArray == null || byArray.length == 0) {
            return false;
        }
        if (IGNORE.contains(n) || n == UNION_CMD_NOTIFY) {
            return false;
        }
        return ExtraLevelUiBridge.tryHandleOne(player, n, byArray);
    }

    private static boolean tryHandleOne(Player player, int n, byte[] byArray) {
        // Promote opcodes use their own wire layout, including after opcode discovery.
        if (n != PROMOTE_REQ && n != 6091 && AvatarExtraLevelOpcodes.isKnownRequestOpcode(n)) {
            try {
                Avatar avatar;
                ParsedExtraLevelUpgradeReq parsedExtraLevelUpgradeReq = AvatarExtraLevelUpgradeReqParser.parseAnyStrict(byArray);
                long l = AvatarGuidCodec.resolve(player, parsedExtraLevelUpgradeReq.getAvatarGuid());
                if (l != parsedExtraLevelUpgradeReq.getAvatarGuid()) {
                    parsedExtraLevelUpgradeReq = new ParsedExtraLevelUpgradeReq(l, parsedExtraLevelUpgradeReq.getTargetLevel(), parsedExtraLevelUpgradeReq.getProtoKind());
                }
                if ((avatar = player.getAvatars().getAvatarByGuid(l)) != null && ExtraLevelUiBridge.isExtraLevelCandidate(avatar)) {
                    Grasscutter.getLogger().info("ExtraLevel UI opcode={} proto={} avatar={} level={} target={}", n, parsedExtraLevelUpgradeReq.getProtoKind(), avatar.getAvatarId(), avatar.getLevel(), parsedExtraLevelUpgradeReq.getTargetLevel());
                    return AvatarExtraLevelHelper.handleExtraLevelRequest(player, n, parsedExtraLevelUpgradeReq, byArray.length);
                }
            }
            catch (Throwable throwable) {
                Grasscutter.getLogger().debug("ExtraLevel known opcode parse skip {}", (Object)n, (Object)throwable);
            }
            return false;
        }
        if (n == PROMOTE_REQ || n == 6091) {
            if (byArray.length > 256) {
                return false;
            }
            try {
                long l = AvatarGuidCodec.resolve(player, AvatarPromoteReqParser.parseGuid(byArray));
                Avatar avatar = player.getAvatars().getAvatarByGuid(l);
                if (avatar != null && ExtraLevelUiBridge.isExtraLevelCandidate(avatar)) {
                    Grasscutter.getLogger().info("ExtraLevel PromoteReq opcode={} avatar={} level={}", n, avatar.getAvatarId(), avatar.getLevel());
                    return AvatarExtraLevelHelper.onPromoteReq(player, l);
                }
            }
            catch (Throwable throwable) {
                Grasscutter.getLogger().warn("ExtraLevel PromoteReq fail opcode={}", (Object)n, (Object)throwable);
            }
        }
        return false;
    }

    private static boolean isExtraLevelCandidate(Avatar avatar) {
        if (avatar == null || avatar.getPromoteLevel() < 6) {
            return false;
        }
        int n = avatar.getLevel();
        return n == 90 || n == 95;
    }
}
