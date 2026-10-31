/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.data.GameData
 *  emu.grasscutter.data.common.ItemParamData
 *  emu.grasscutter.data.excels.avatar.AvatarPromoteData
 *  emu.grasscutter.game.avatar.Avatar
 *  emu.grasscutter.game.avatar.AvatarExtraLevelConfig
 *  emu.grasscutter.game.avatar.AvatarExtraLevelOpcodes
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.net.proto.AvatarExtraLevelUpgradeReqParser
 *  emu.grasscutter.net.proto.AvatarInfoOuterClass$AvatarInfo$Builder
 *  emu.grasscutter.net.proto.ParsedExtraLevelUpgradeReq
 *  emu.grasscutter.server.packet.send.PacketAvatarDataNotify
 *  emu.grasscutter.server.packet.send.PacketAvatarExtraLevelUpgradeRsp
 *  emu.grasscutter.server.packet.send.PacketAvatarPromoteRsp
 *  emu.grasscutter.server.packet.send.PacketAvatarPropNotify
 *  emu.grasscutter.server.packet.send.PacketAvatarUpgradeRsp
 *  emu.grasscutter.utils.FileUtils
 *  emu.grasscutter.utils.JsonUtils
 *  it.unimi.dsi.fastutil.ints.Int2FloatArrayMap
 *  it.unimi.dsi.fastutil.ints.Int2FloatMap
 */
package emu.grasscutter.game.avatar;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.avatar.AvatarPromoteData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.avatar.AvatarExtraLevelConfig;
import emu.grasscutter.game.avatar.AvatarExtraLevelOpcodes;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.AvatarExtraLevelUpgradeReqParser;
import emu.grasscutter.net.proto.AvatarInfoOuterClass;
import emu.grasscutter.net.proto.ParsedExtraLevelUpgradeReq;
import emu.grasscutter.server.packet.send.PacketAvatarDataNotify;
import emu.grasscutter.server.packet.send.PacketAvatarExtraLevelUpgradeRsp;
import emu.grasscutter.server.packet.send.PacketAvatarPromoteRsp;
import emu.grasscutter.server.packet.send.PacketAvatarPropNotify;
import emu.grasscutter.server.packet.send.PacketAvatarUpgradeRsp;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2FloatArrayMap;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AvatarExtraLevelHelper {
    private static final int MAX_PROMOTE_LEVEL = 6;
    private static List<AvatarExtraLevelConfig> configs = Collections.emptyList();
    private static boolean configsLoaded;
    private static int lastRequestOpcode;
    private static final Set<Integer> IGNORED_SNIFF_OPCODES;

    private AvatarExtraLevelHelper() {
    }

    public static void ensureConfigsLoaded() {
        if (configsLoaded) {
            return;
        }
        configsLoaded = true;
        Path path = FileUtils.getResourcePath((String)"ExcelBinOutput/AvatarExtraLevelExcelConfigData.json");
        if (!Files.isRegularFile(path, new LinkOption[0])) {
            Grasscutter.getLogger().warn("Missing AvatarExtraLevelExcelConfigData.json at {}", (Object)path);
            configs = Collections.emptyList();
            return;
        }
        try {
            List list = JsonUtils.loadToList((Path)path, AvatarExtraLevelConfig.class);
            configs = list == null ? Collections.emptyList() : list;
            Grasscutter.getLogger().info("Loaded {} avatar extra level upgrade rows", (Object)configs.size());
        }
        catch (Exception exception) {
            Grasscutter.getLogger().error("Failed to load AvatarExtraLevelExcelConfigData.json", (Throwable)exception);
            configs = Collections.emptyList();
        }
    }

    public static void applyAvatarInfoExtraLevel(AvatarInfoOuterClass.AvatarInfo.Builder builder, Avatar avatar) {
        if (builder == null || avatar == null || avatar.getPromoteLevel() < 6) {
            return;
        }
        builder.setJMFFNNBEHGG(AvatarExtraLevelHelper.getExtraLevelTier(avatar));
        builder.setLDHKKNPGIMH(AvatarExtraLevelHelper.getEffectiveMaxLevel(avatar));
    }

    public static int getEffectiveMaxLevel(Avatar avatar) {
        if (avatar == null) {
            return 90;
        }
        if (avatar.getPromoteLevel() < 6) {
            AvatarPromoteData avatarPromoteData = GameData.getAvatarPromoteData((int)avatar.getAvatarData().getAvatarPromoteId(), (int)avatar.getPromoteLevel());
            return avatarPromoteData == null ? 90 : avatarPromoteData.getUnlockMaxLevel();
        }
        int n = avatar.getLevel();
        if (n >= 95) {
            return 100;
        }
        if (n >= 90) {
            return 95;
        }
        AvatarPromoteData avatarPromoteData = GameData.getAvatarPromoteData((int)avatar.getAvatarData().getAvatarPromoteId(), (int)avatar.getPromoteLevel());
        return avatarPromoteData == null ? 90 : avatarPromoteData.getUnlockMaxLevel();
    }

    public static int getExtraLevelTier(Avatar avatar) {
        if (avatar == null) {
            return 0;
        }
        if (avatar.getLevel() >= 100) {
            return 2;
        }
        return avatar.getLevel() >= 95 ? 1 : 0;
    }

    public static boolean tryHandleKnownOpcodePacket(Player player, int n, byte[] byArray) {
        ParsedExtraLevelUpgradeReq parsedExtraLevelUpgradeReq;
        if (n != PacketOpcodes.AvatarPromoteReq) {
            return false;
        }
        if (player == null || byArray == null || byArray.length == 0 || IGNORED_SNIFF_OPCODES.contains(n)) {
            return false;
        }
        try {
            parsedExtraLevelUpgradeReq = AvatarExtraLevelUpgradeReqParser.parseAnyStrict((byte[])byArray);
        }
        catch (Exception exception) {
            return false;
        }
        Avatar avatar = player.getAvatars().getAvatarByGuid(parsedExtraLevelUpgradeReq.getAvatarGuid());
        if (avatar == null) {
            return false;
        }
        int n2 = avatar.getLevel();
        if (n2 != 90 && n2 != 95) {
            return false;
        }
        Grasscutter.getLogger().info("AvatarExtraLevel CONFIRMED opcode={} proto={} avatar={} guid={} level={} promote={} target={} eligible={}", new Object[]{n, parsedExtraLevelUpgradeReq.getProtoKind(), avatar.getAvatarId(), parsedExtraLevelUpgradeReq.getAvatarGuid(), n2, avatar.getPromoteLevel(), parsedExtraLevelUpgradeReq.getTargetLevel(), AvatarExtraLevelHelper.isEligibleForUpgrade(avatar)});
        lastRequestOpcode = n;
        return AvatarExtraLevelHelper.handleExtraLevelRequest(player, n, parsedExtraLevelUpgradeReq, byArray.length);
    }

    public static boolean upgradeAvatar(Player player, Avatar avatar) {
        if (player == null || avatar == null) {
            return false;
        }
        lastRequestOpcode = 0;
        AvatarExtraLevelHelper.ensureConfigsLoaded();
        if (!AvatarExtraLevelHelper.isEligibleForUpgrade(avatar)) {
            return false;
        }
        AvatarExtraLevelConfig avatarExtraLevelConfig = AvatarExtraLevelHelper.findUpgradeRow(avatar.getLevel());
        if (avatarExtraLevelConfig == null) {
            return false;
        }
        return AvatarExtraLevelHelper.upgrade(player, avatar, avatarExtraLevelConfig, lastRequestOpcode);
    }

    public static boolean onPromoteReq(Player player, long l) {
        lastRequestOpcode = PacketOpcodes.AvatarPromoteReq;
        AvatarExtraLevelOpcodes.noteDiscoveredRequest((int)PacketOpcodes.AvatarPromoteReq);
        AvatarExtraLevelHelper.logPromoteRequest(player, l, PacketOpcodes.AvatarPromoteReq);
        return AvatarExtraLevelHelper.upgradeByGuid(player, l);
    }

    public static void logPromoteRequest(Player player, long l, int n) {
        if (player == null) {
            return;
        }
        Avatar avatar = player.getAvatars().getAvatarByGuid(l);
        Grasscutter.getLogger().info("AvatarExtraLevel AvatarPromoteReq opcode={} guid={} avatar={} level={} promote={} eligible={} reason={}", new Object[]{n, l, avatar == null ? 0 : avatar.getAvatarId(), avatar == null ? -1 : avatar.getLevel(), avatar == null ? -1 : avatar.getPromoteLevel(), AvatarExtraLevelHelper.isEligibleForUpgrade(avatar), AvatarExtraLevelHelper.describeEligibility(avatar)});
    }

    public static boolean upgradeByGuid(Player player, long l) {
        Avatar avatar;
        if (lastRequestOpcode <= 0) {
            lastRequestOpcode = PacketOpcodes.AvatarPromoteReq;
        }
        AvatarExtraLevelHelper.ensureConfigsLoaded();
        Avatar avatar2 = avatar = player == null ? null : player.getAvatars().getAvatarByGuid(l);
        if (avatar == null || !AvatarExtraLevelHelper.isEligibleForUpgrade(avatar)) {
            return false;
        }
        AvatarExtraLevelConfig avatarExtraLevelConfig = AvatarExtraLevelHelper.findUpgradeRow(avatar.getLevel());
        if (avatarExtraLevelConfig == null) {
            Grasscutter.getLogger().warn("AvatarExtraLevel upgradeByGuid avatar={} level={} has no config row", (Object)avatar.getAvatarId(), (Object)avatar.getLevel());
            return false;
        }
        return AvatarExtraLevelHelper.upgrade(player, avatar, avatarExtraLevelConfig, lastRequestOpcode);
    }

    public static boolean tryHandleUnregisteredPacket(Player player, int n, byte[] byArray) {
        try {
            ParsedExtraLevelUpgradeReq parsedExtraLevelUpgradeReq;
            if (player == null || byArray == null || byArray.length == 0 || IGNORED_SNIFF_OPCODES.contains(n)) {
                return false;
            }
            if (n == 1211) {
                return false;
            }
            AvatarExtraLevelHelper.ensureConfigsLoaded();
            if (configs.isEmpty()) {
                return false;
            }
            try {
                parsedExtraLevelUpgradeReq = AvatarExtraLevelUpgradeReqParser.parseAnyStrict((byte[])byArray);
            }
            catch (Exception exception) {
                return false;
            }
            Avatar avatar = player.getAvatars().getAvatarByGuid(parsedExtraLevelUpgradeReq.getAvatarGuid());
            if (avatar == null) {
                return false;
            }
            int n2 = avatar.getLevel();
            if (n2 != 90 && n2 != 95) {
                return false;
            }
            Grasscutter.getLogger().info("AvatarExtraLevel CONFIRMED unregistered opcode={} proto={} avatar={} guid={} level={} promote={} target={} eligible={}", new Object[]{n, parsedExtraLevelUpgradeReq.getProtoKind(), avatar.getAvatarId(), parsedExtraLevelUpgradeReq.getAvatarGuid(), n2, avatar.getPromoteLevel(), parsedExtraLevelUpgradeReq.getTargetLevel(), AvatarExtraLevelHelper.isEligibleForUpgrade(avatar)});
            return AvatarExtraLevelHelper.handleExtraLevelRequest(player, n, parsedExtraLevelUpgradeReq, byArray.length);
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().debug("AvatarExtraLevel unregistered opcode={} skipped", (Object)n, (Object)throwable);
            return false;
        }
    }

    public static boolean handleExtraLevelRequest(Player player, int n, ParsedExtraLevelUpgradeReq parsedExtraLevelUpgradeReq, int n2) {
        lastRequestOpcode = n;
        AvatarExtraLevelOpcodes.noteDiscoveredRequest((int)n);
        Avatar avatar = player.getAvatars().getAvatarByGuid(parsedExtraLevelUpgradeReq.getAvatarGuid());
        if (avatar == null || !AvatarExtraLevelHelper.isEligibleForUpgrade(avatar)) {
            return false;
        }
        AvatarExtraLevelConfig avatarExtraLevelConfig = AvatarExtraLevelHelper.findUpgradeRow(avatar.getLevel());
        if (avatarExtraLevelConfig == null) {
            return false;
        }
        Grasscutter.getLogger().info("AvatarExtraLevel request opcode={} proto={} avatar={} level={}->{} target={} payloadLen={}", new Object[]{n, parsedExtraLevelUpgradeReq.getProtoKind(), avatar.getAvatarId(), avatar.getLevel(), avatarExtraLevelConfig.getToLevel(), parsedExtraLevelUpgradeReq.getTargetLevel(), n2});
        return AvatarExtraLevelHelper.upgrade(player, avatar, avatarExtraLevelConfig, n);
    }

    public static void resyncAfterLogin(Player player) {
        if (player == null || player.getAvatars() == null) {
            return;
        }
        AvatarExtraLevelHelper.ensureConfigsLoaded();
        player.sendPacket((BasePacket)new PacketAvatarDataNotify(player));
        Grasscutter.getLogger().info("AvatarExtraLevel login resync uid={} avatars={}", (Object)player.getUid(), (Object)player.getAvatars().getAvatarCount());
    }

    public static String describeEligibilityPublic(Avatar avatar) {
        return AvatarExtraLevelHelper.describeEligibility(avatar);
    }

    private static boolean upgrade(Player player, Avatar avatar, AvatarExtraLevelConfig avatarExtraLevelConfig, int n) {
        if (avatarExtraLevelConfig.getToLevel() <= avatar.getLevel()) {
            return false;
        }
        ItemParamData[] itemParamDataArray = AvatarExtraLevelHelper.normalizeCosts(avatarExtraLevelConfig.getCostItems());
        if (itemParamDataArray.length == 0) {
            Grasscutter.getLogger().warn("Avatar extra level {}->{} has no valid cost items", (Object)avatarExtraLevelConfig.getFromLevel(), (Object)avatarExtraLevelConfig.getToLevel());
            return false;
        }
        if (!player.getInventory().payItems(itemParamDataArray)) {
            player.sendMessage(player, (Object)"Not enough masterless stella (104300) for extra level breakthrough.");
            AvatarExtraLevelHelper.sendFailure(player, avatar, avatarExtraLevelConfig.getFromLevel(), n);
            return true;
        }
        int n2 = avatar.getLevel();
        Int2FloatArrayMap int2FloatArrayMap = new Int2FloatArrayMap((Int2FloatMap)avatar.getFightProperties());
        avatar.setLevel(avatarExtraLevelConfig.getToLevel());
        avatar.setExp(0);
        avatar.recalcStats(true);
        avatar.save();
        player.sendPacket((BasePacket)new PacketAvatarPropNotify(avatar));
        player.sendPacket((BasePacket)new PacketAvatarUpgradeRsp(avatar, n2, (Map)int2FloatArrayMap));
        player.sendPacket((BasePacket)new PacketAvatarDataNotify(player));
        if (n == PacketOpcodes.AvatarPromoteReq) {
            player.sendPacket((BasePacket)new PacketAvatarPromoteRsp(avatar));
        } else if (n > 0) {
            AvatarExtraLevelHelper.sendSuccess(player, avatar, n2, n);
        }
        player.sendMessage(player, (Object)String.format(Locale.US, "Extra level breakthrough: avatar %d %d -> %d", avatar.getAvatarId(), n2, avatar.getLevel()));
        return true;
    }

    private static void sendSuccess(Player player, Avatar avatar, int n, int n2) {
        PacketAvatarExtraLevelUpgradeRsp packetAvatarExtraLevelUpgradeRsp = new PacketAvatarExtraLevelUpgradeRsp(avatar.getGuid(), n, avatar.getLevel());
        packetAvatarExtraLevelUpgradeRsp.setOpcode(AvatarExtraLevelOpcodes.resolveResponseOpcode((int)n2));
        AvatarExtraLevelOpcodes.noteDiscoveredResponse((int)packetAvatarExtraLevelUpgradeRsp.getOpcode());
        player.sendPacket((BasePacket)packetAvatarExtraLevelUpgradeRsp);
    }

    private static void sendFailure(Player player, Avatar avatar, int n, int n2) {
        PacketAvatarExtraLevelUpgradeRsp packetAvatarExtraLevelUpgradeRsp = new PacketAvatarExtraLevelUpgradeRsp(avatar.getGuid(), n, n, 1);
        packetAvatarExtraLevelUpgradeRsp.setOpcode(AvatarExtraLevelOpcodes.resolveResponseOpcode((int)n2));
        if (packetAvatarExtraLevelUpgradeRsp.getOpcode() > 0) {
            player.sendPacket((BasePacket)packetAvatarExtraLevelUpgradeRsp);
        }
    }

    private static ItemParamData[] normalizeCosts(List<ItemParamData> list) {
        if (list == null || list.isEmpty()) {
            return new ItemParamData[0];
        }
        return (ItemParamData[])list.stream().filter(itemParamData -> itemParamData != null && itemParamData.getId() > 0 && itemParamData.getCount() > 0).map(itemParamData -> new ItemParamData(itemParamData.getId(), itemParamData.getCount())).toArray(ItemParamData[]::new);
    }

    private static AvatarExtraLevelConfig findUpgradeRow(int n) {
        for (AvatarExtraLevelConfig avatarExtraLevelConfig : configs) {
            if (avatarExtraLevelConfig.getFromLevel() != n) continue;
            return avatarExtraLevelConfig;
        }
        return null;
    }

    private static String describeEligibility(Avatar avatar) {
        if (avatar == null || avatar.getAvatarData() == null) {
            return "avatar-missing";
        }
        if (avatar.getPromoteLevel() < 6) {
            return "promote<6 (need full ascension)";
        }
        int n = avatar.getLevel();
        if (n != 90 && n != 95) {
            return "level must be 90 or 95, got " + n;
        }
        if (n == 90) {
            AvatarPromoteData avatarPromoteData = GameData.getAvatarPromoteData((int)avatar.getAvatarData().getAvatarPromoteId(), (int)avatar.getPromoteLevel());
            if (avatarPromoteData == null) {
                return "missing promote excel row";
            }
            if (n != avatarPromoteData.getUnlockMaxLevel()) {
                return "level " + n + " != unlockMaxLevel " + avatarPromoteData.getUnlockMaxLevel();
            }
        }
        if (n == 95 && AvatarExtraLevelHelper.getExtraLevelTier(avatar) < 1) {
            return "tier must be >=1 for 95->100, got " + AvatarExtraLevelHelper.getExtraLevelTier(avatar);
        }
        return AvatarExtraLevelHelper.findUpgradeRow(n) == null ? "no AvatarExtraLevelExcel row for level " + n : "ok";
    }

    private static boolean isEligibleForUpgrade(Avatar avatar) {
        return "ok".equals(AvatarExtraLevelHelper.describeEligibility(avatar));
    }

    static {
        IGNORED_SNIFF_OPCODES = new HashSet<Integer>(Arrays.asList(2151, 5080, 8274, 20808, 21080, 21498, 26395, 26986, 28659, 29324));
    }
}

