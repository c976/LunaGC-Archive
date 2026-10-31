/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.born;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.born.BornDataConfig;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class BornDataHelper {
    private BornDataHelper() {
    }

    public static void ensureMainCharacter(Player player) {
        if (player.getMainCharacterId() != 0 && player.getAvatars().getAvatarCount() > 0) {
            if (player.getNickname() == null || player.getNickname().isBlank()) {
                String string = BornDataConfig.getNickname(player.getAccount() != null ? player.getAccount().getUsername() : "Traveler");
                player.setNickname(string);
                player.save();
            }
            return;
        }
        if (player.getAvatars().getAvatarCount() == 0) {
            BornDataHelper.autoCreateMainCharacter(player);
            return;
        }
        BornDataHelper.repairMainCharacterFromExistingAvatars(player);
    }

    public static void autoCreateMainCharacter(Player player) {
        int n;
        int n2;
        if (player.getAvatars().getAvatarCount() != 0) {
            return;
        }
        if (BornDataConfig.isRandomGender()) {
            n2 = ThreadLocalRandom.current().nextBoolean() ? 10000005 : 10000007;
        } else {
            n2 = BornDataConfig.getAvatarId();
            if (n2 != 10000005 && n2 != 10000007) {
                n2 = 10000007;
            }
        }
        int n3 = n = n2 == 10000005 ? 504 : 704;
        if (!GameData.getAvatarDataMap().containsKey(n2)) {
            throw new IllegalStateException("No avatar data for id " + n2 + ". Check ExcelBinOutput.");
        }
        String string = BornDataConfig.getNickname(player.getAccount() != null ? player.getAccount().getUsername() : "Traveler");
        player.setNickname(string);
        Avatar avatar = new Avatar(n2);
        AvatarSkillDepotData avatarSkillDepotData = (AvatarSkillDepotData)GameData.getAvatarSkillDepotDataMap().get(n);
        if (avatarSkillDepotData != null) {
            avatar.setSkillDepotData(avatarSkillDepotData);
        }
        avatar.recalcStats(true);
        player.addAvatar(avatar, false);
        player.setMainCharacterId(n2);
        player.setHeadImage(n2);
        List<Integer> list = player.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars();
        if (!list.contains(n2)) {
            list.add(n2);
        }
        player.save();
        Grasscutter.getLogger().info("Auto-born created traveler uid={} avatarId={} nickname={}", player.getUid(), n2, player.getNickname());
    }

    private static void repairMainCharacterFromExistingAvatars(Player player) {
        int n = BornDataHelper.resolveMainAvatarId(player);
        if (n == 0) {
            Grasscutter.getLogger().warn("Broken player uid={} has avatars but no main character; recreating traveler", (Object)player.getUid());
            for (Avatar avatar : player.getAvatars()) {
                if (avatar == null) continue;
                n = avatar.getAvatarId();
                break;
            }
            if (n == 0) {
                BornDataHelper.autoCreateMainCharacter(player);
                return;
            }
        }
        if (player.getNickname() == null || player.getNickname().isBlank()) {
            player.setNickname(BornDataConfig.getNickname(player.getAccount() != null ? player.getAccount().getUsername() : "Traveler"));
        }
        player.setMainCharacterId(n);
        player.setHeadImage(n);
        List<Integer> list = player.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars();
        if (!list.contains(n)) {
            list.add(n);
        }
        player.save();
    }

    private static int resolveMainAvatarId(Player player) {
        if (player.getAvatars().getAvatarById(10000005) != null) {
            return 10000005;
        }
        if (player.getAvatars().getAvatarById(10000007) != null) {
            return 10000007;
        }
        for (Avatar avatar : player.getAvatars()) {
            if (avatar == null) continue;
            return avatar.getAvatarId();
        }
        return 0;
    }
}
