package emu.grasscutter.game.tower;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigLevelEntity;
import emu.grasscutter.data.binout.config.fields.ConfigAbilityData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketAbilityChangeNotify;
import emu.grasscutter.server.packet.send.PacketSyncTeamEntityNotify;
import emu.grasscutter.utils.Utils;
import java.util.*;

/**
 * Resolves Spiral Abyss schedule / floor {@code levelConfigId} rows into ConfigLevelEntity ability
 * names, and pushes them onto avatar / team ability control blocks.
 */
public final class TowerLevelEntityHelper {
    private TowerLevelEntityHelper() {}

    /** Collect monthly blessing + floor-level entity config names for the current tower run. */
    public static List<String> resolveActiveConfigNames(Player player) {
        var names = new ArrayList<String>();
        if (player == null || player.getTowerManager() == null) return names;

        var schedule = player.getServer().getTowerSystem().getCurrentTowerScheduleData();
        if (schedule != null && schedule.getMonthlyLevelConfigId() > 0) {
            appendConfigNames(names, schedule.getMonthlyLevelConfigId());
        }

        int floorId = player.getTowerManager().getCurrentFloorId();
        var floor = GameData.getTowerFloorDataMap().get(floorId);
        if (floor != null && floor.getFloorLevelConfigId() > 0) {
            appendConfigNames(names, floor.getFloorLevelConfigId());
        }
        return names;
    }

    private static void appendConfigNames(List<String> out, int levelConfigId) {
        var list = GameData.getDungeonLevelEntityConfigMap().get(levelConfigId);
        if (list == null || list.isEmpty()) return;
        for (String name : list) {
            if (name != null && !name.isBlank() && !out.contains(name)) {
                out.add(name);
            }
        }
    }

    public static List<ConfigLevelEntity> resolveConfigs(Collection<String> names) {
        var configs = new ArrayList<ConfigLevelEntity>();
        if (names == null) return configs;
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            // Empty charge placeholders are intentional no-ops.
            if ("Level_Spiral_Abyss_Charge_Empty".equals(name)) continue;
            var config = GameData.getConfigLevelEntityDataMap().get(name);
            if (config != null) {
                configs.add(config);
            }
        }
        return configs;
    }

    public static void collectAvatarAbilityHashes(Collection<String> configNames, Collection<Integer> out) {
        for (ConfigLevelEntity config : resolveConfigs(configNames)) {
            appendAbilityHashes(config.getAvatarAbilities(), out);
        }
    }

    public static void collectTeamAbilityNames(Collection<String> configNames, Collection<String> out) {
        for (ConfigLevelEntity config : resolveConfigs(configNames)) {
            if (config.getTeamAbilities() == null) continue;
            for (ConfigAbilityData ability : config.getTeamAbilities()) {
                if (ability != null
                        && ability.getAbilityName() != null
                        && !ability.getAbilityName().isEmpty()
                        && !out.contains(ability.getAbilityName())) {
                    out.add(ability.getAbilityName());
                }
            }
        }
    }

    public static void collectMonsterAbilityNames(Collection<String> configNames, Collection<String> out) {
        for (ConfigLevelEntity config : resolveConfigs(configNames)) {
            if (config.getMonsterAbilities() == null) continue;
            for (ConfigAbilityData ability : config.getMonsterAbilities()) {
                if (ability != null
                        && ability.abilityName != null
                        && !ability.abilityName.isEmpty()
                        && !out.contains(ability.abilityName)) {
                    out.add(ability.abilityName);
                }
            }
        }
    }

    private static void appendAbilityHashes(
            List<ConfigAbilityData> abilities, Collection<Integer> out) {
        if (abilities == null) return;
        for (ConfigAbilityData ability : abilities) {
            if (ability == null || ability.getAbilityName() == null || ability.getAbilityName().isEmpty()) {
                continue;
            }
            int hash = Utils.abilityHash(ability.getAbilityName());
            if (!out.contains(hash)) {
                out.add(hash);
            }
        }
    }

    /** Re-send avatar + team ability blocks so the client attaches moon / floor LevelEntity buffs. */
    public static void notifyClient(Player player) {
        if (player == null || player.getTeamManager() == null) return;
        try {
            for (EntityAvatar entity : player.getTeamManager().getActiveTeam()) {
                if (entity == null) continue;
                player.sendPacket(new PacketAbilityChangeNotify(entity));
            }
            player.sendPacket(new PacketSyncTeamEntityNotify(player));
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("Tower ability notify failed uid={}: {}", player.getUid(), t.toString());
        }
    }
}
