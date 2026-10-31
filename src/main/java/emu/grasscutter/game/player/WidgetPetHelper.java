package emu.grasscutter.game.player;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.AvatarConfig;
import emu.grasscutter.data.ResourceLoader.AvatarConfigAbility;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.entity.EntityTeam;
import emu.grasscutter.net.proto.AbilityControlBlockOuterClass.AbilityControlBlock;
import emu.grasscutter.net.proto.AbilityEmbryoOuterClass.AbilityEmbryo;
import emu.grasscutter.server.packet.send.PacketAbilityChangeNotify;
import emu.grasscutter.server.packet.send.PacketWidgetUseAttachAbilityGroupChangeNotify;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.Utils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Follower gadgets (Endora, mini seelies and similar) are not world entities. Equipping them attaches a
 * team ability group; the client renders the pet via SendEffectTrigger on that ability.
 */
public final class WidgetPetHelper {
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);
    /** materialId -> abilityGroupName */
    private static Map<Integer, String> widgetAbilityGroupMap = Map.of();
    /** abilityGroupName -> ability names (e.g. SceneObj_Toys_Endora) */
    private static Map<String, List<String>> abilityGroupAbilities = Map.of();

    private WidgetPetHelper() {}

    public static boolean hasAbilityGroup(int materialId) {
        ensureLoaded();
        return widgetAbilityGroupMap.containsKey(materialId);
    }

    public static void onWidgetSlotChange(Player player, int previousMaterialId, int currentMaterialId) {
        ensureLoaded();
        Grasscutter.getLogger()
                .debug(
                        "WidgetPetHelper slot change uid={} prev={} curr={}",
                        player.getUid(),
                        previousMaterialId,
                        currentMaterialId);
        if (previousMaterialId != 0 && previousMaterialId != currentMaterialId) {
            setAttach(player, previousMaterialId, false);
        }
        if (currentMaterialId != 0) {
            setAttach(player, currentMaterialId, true);
        } else if (previousMaterialId != 0) {
            setAttach(player, previousMaterialId, false);
        }
    }

    /** Re-apply currently equipped follower pet after login / scene enter. */
    public static void syncEquippedWidget(Player player) {
        ensureLoaded();
        int materialId = player.getWidgetId();
        if (materialId == 0 || !widgetAbilityGroupMap.containsKey(materialId)) {
            return;
        }
        Grasscutter.getLogger()
                .debug("WidgetPetHelper sync equipped uid={} material={}", player.getUid(), materialId);
        setAttach(player, materialId, true);
    }

    /**
     * Add follower-pet embryos before PlayerEnterSceneInfoNotify so the enter-scene
     * AbilityControlBlock already contains them.
     */
    public static void preloadEquippedWidget(Player player) {
        ensureLoaded();
        int materialId = player.getWidgetId();
        if (materialId == 0 || !widgetAbilityGroupMap.containsKey(materialId)) {
            return;
        }
        String groupName = widgetAbilityGroupMap.get(materialId);
        List<String> abilities = resolveAbilities(groupName);
        var embryos = player.getTeamManager().getTeamAbilityEmbryos();
        EntityTeam team = player.getTeamManager().getEntity();
        for (String ability : abilities) {
            embryos.add(ability);
            if (team != null) {
                AbilityData data = GameData.getAbilityData(ability);
                if (data != null) {
                    boolean already =
                            team.getInstancedAbilities().stream()
                                    .anyMatch(
                                            a ->
                                                    a != null
                                                            && a.getData() != null
                                                            && ability.equals(a.getData().abilityName));
                    if (!already) {
                        player.getAbilityManager().addAbilityToEntity(team, data);
                    }
                }
            }
        }
        Grasscutter.getLogger()
                .debug(
                        "WidgetPetHelper preload uid={} material={} abilities={}",
                        player.getUid(),
                        materialId,
                        abilities);
    }

    private static List<String> resolveAbilities(String groupName) {
        List<String> abilities =
                new ArrayList<>(abilityGroupAbilities.getOrDefault(groupName, Collections.emptyList()));
        if (abilities.isEmpty() && groupName != null && groupName.startsWith("DynamicAbility_")) {
            String guessed = "SceneObj_" + groupName.substring("DynamicAbility_".length());
            if (GameData.getAbilityData(guessed) != null) {
                abilities.add(guessed);
            }
            if (abilities.isEmpty() && groupName.contains("TreasureSeekingSeelie")) {
                String alt = "SceneObj_Toys_SeekerWindCrystal_TreasureSeekingSeelie_Fontaine";
                if (GameData.getAbilityData(alt) != null) {
                    abilities.add(alt);
                }
            }
        }
        if (abilities.isEmpty() && groupName != null && GameData.getAbilityData(groupName) != null) {
            abilities.add(groupName);
        }
        return abilities;
    }

    private static void setAttach(Player player, int materialId, boolean isAttach) {
        String groupName = widgetAbilityGroupMap.get(materialId);
        if (groupName == null || groupName.isEmpty()) {
            Grasscutter.getLogger()
                    .debug("WidgetPetHelper skip material={} (no abilityGroup)", materialId);
            return;
        }

        List<String> abilities = resolveAbilities(groupName);
        if (abilities.isEmpty()) {
            Grasscutter.getLogger()
                    .warn(
                            "WidgetPetHelper no abilities for material={} group={} (notify still sent)",
                            materialId,
                            groupName);
        }
        var embryos = player.getTeamManager().getTeamAbilityEmbryos();
        EntityTeam team = player.getTeamManager().getEntity();

        if (isAttach) {
            for (String ability : abilities) {
                embryos.add(ability);
                if (team != null) {
                    AbilityData data = GameData.getAbilityData(ability);
                    if (data != null) {
                        boolean already =
                                team.getInstancedAbilities().stream()
                                        .anyMatch(
                                                a ->
                                                        a != null
                                                                && a.getData() != null
                                                                && ability.equals(a.getData().abilityName));
                        if (!already) {
                            player.getAbilityManager().addAbilityToEntity(team, data);
                        }
                    }
                }
            }
        } else {
            for (String ability : abilities) {
                embryos.remove(ability);
                if (team != null) {
                    team.getInstancedAbilities()
                            .removeIf(
                                    a ->
                                            a != null
                                                    && a.getData() != null
                                                    && ability.equals(a.getData().abilityName));
                }
            }
        }

        // 1) Client-side attach via widget config lookup
        player.sendPacket(new PacketWidgetUseAttachAbilityGroupChangeNotify(materialId, isAttach));

        // 2) Authoritative team AbilityControlBlock refresh (client effect needs the embryo)
        if (team != null) {
            AbilityControlBlock block = buildTeamAbilityControlBlock(player, embryos);
            player.sendPacket(new PacketAbilityChangeNotify(team.getId(), block));
        }

        Grasscutter.getLogger()
                .debug(
                        "WidgetPetHelper {} material={} group={} abilities={} teamEntity={}",
                        isAttach ? "ATTACH" : "DETACH",
                        materialId,
                        groupName,
                        abilities,
                        team != null ? team.getId() : 0);
    }

    private static AbilityControlBlock buildTeamAbilityControlBlock(
            Player player, java.util.Set<String> extraEmbryos) {
        // Start from the server's normal team block, then append widget pet embryos.
        AbilityControlBlock base = player.getTeamManager().getAbilityControlBlock();
        AbilityControlBlock.Builder builder = base.toBuilder();
        int nextId = base.getAbilityEmbryoListCount();
        for (String skill : extraEmbryos) {
            if (skill == null || skill.isEmpty()) {
                continue;
            }
            boolean exists =
                    base.getAbilityEmbryoListList().stream()
                            .anyMatch(e -> e.getAbilityNameHash() == Utils.abilityHash(skill));
            if (exists) {
                continue;
            }
            builder.addAbilityEmbryoList(
                    AbilityEmbryo.newBuilder()
                            .setAbilityId(++nextId)
                            .setAbilityNameHash(Utils.abilityHash(skill))
                            .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                            .build());
        }
        return builder.build();
    }

    private static void ensureLoaded() {
        if (LOADED.get()) {
            return;
        }
        synchronized (WidgetPetHelper.class) {
            if (LOADED.get()) {
                return;
            }
            Map<Integer, String> map = new HashMap<>();
            Map<String, List<String>> groups = new HashMap<>();
            try {
                Path path = FileUtils.getResourcePath("BinOutput/WidgetNew/ConfigWidgetNew.json");
                if (Files.isRegularFile(path)) {
                    ConfigWidgetNewRoot root = JsonUtils.loadToClass(path, ConfigWidgetNewRoot.class);
                    if (root != null && root.widgetConfigMap != null) {
                        for (var entry : root.widgetConfigMap.entrySet()) {
                            if (entry.getValue() == null || entry.getValue().abilityGroupName == null) {
                                continue;
                            }
                            try {
                                map.put(Integer.parseInt(entry.getKey()), entry.getValue().abilityGroupName);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Grasscutter.getLogger().error("Failed to load ConfigWidgetNew for follower pets", e);
            }

            // Known follower-pet material -> group (ids from ConfigWidgetNew)
            map.putIfAbsent(220014, "DynamicAbility_Toys_Seelie03");
            map.putIfAbsent(220015, "DynamicAbility_Toys_Seelie");
            map.putIfAbsent(220016, "DynamicAbility_Toys_Seelie02");
            map.putIfAbsent(220023, "DynamicAbility_Toys_Endora");
            map.putIfAbsent(220038, "DynamicAbility_Toys_Seelie_Purple");
            map.putIfAbsent(220041, "DynamicAbility_Toys_LeiLing");
            map.putIfAbsent(220045, "DynamicAbility_Toys_ShikiShogun");
            map.putIfAbsent(220061, "DynamicAbility_Toys_SeekerSeelieV3");
            map.putIfAbsent(220062, "DynamicAbility_Toys_Seelie_Green");
            map.putIfAbsent(220072, "DynamicAbility_Toys_WeatherWizard");
            map.putIfAbsent(220074, "DynamicAbility_Toys_PaperCraftCrane");
            map.putIfAbsent(220076, "DynamicAbility_Toys_Sorush");
            map.putIfAbsent(220079, "DynamicAbility_Toys_Sorush_Hat");
            map.putIfAbsent(220084, "DynamicAbility_Toys_Octopus");
            map.putIfAbsent(220095, "DynamicAbility_Toys_TreasureSeekingSeelie_Fontaine");
            map.putIfAbsent(220096, "DynamicAbility_Toys_MiniSeelie_Fontaine");
            map.putIfAbsent(220105, "DynamicAbility_Toys_Salamander");
            map.putIfAbsent(220115, "DynamicAbility_Toys_MachinePora");
            map.putIfAbsent(220120, "DynamicAbility_Toys_Felicette");
            map.putIfAbsent(220137, "DynamicAbility_Toys_FrostConductor");

            try {
                Path dir = FileUtils.getResourcePath("BinOutput/AbilityGroup");
                if (Files.isDirectory(dir)) {
                    try (Stream<Path> paths = Files.list(dir)) {
                        paths.filter(Files::isRegularFile)
                                .filter(p -> p.toString().endsWith(".json"))
                                .forEach(
                                        p -> {
                                            try {
                                                Map<String, AvatarConfig> loaded =
                                                        JsonUtils.loadToMap(p, String.class, AvatarConfig.class);
                                                if (loaded == null) {
                                                    return;
                                                }
                                                for (var e : loaded.entrySet()) {
                                                    if (e.getValue() == null || e.getValue().abilities == null) {
                                                        continue;
                                                    }
                                                    List<String> names = new ArrayList<>();
                                                    for (AvatarConfigAbility a : e.getValue().abilities) {
                                                        if (a != null
                                                                && a.abilityName != null
                                                                && !a.abilityName.isEmpty()) {
                                                            names.add(a.abilityName);
                                                        }
                                                    }
                                                    if (!names.isEmpty()) {
                                                        groups.put(e.getKey(), List.copyOf(names));
                                                    }
                                                }
                                            } catch (Exception ex) {
                                                Grasscutter.getLogger()
                                                        .debug("Skip ability group {}: {}", p.getFileName(), ex.toString());
                                            }
                                        });
                    }
                }
            } catch (Exception e) {
                Grasscutter.getLogger().error("Failed to load AbilityGroup for follower pets", e);
            }

            // Hard fallbacks for every FlyAttach follower pet
            putGroup(groups, "DynamicAbility_Toys_Seelie", "SceneObj_Toys_SeelieCreater");
            putGroup(groups, "DynamicAbility_Toys_Seelie02", "SceneObj_Toys_SeelieCreater_02");
            putGroup(groups, "DynamicAbility_Toys_Seelie03", "SceneObj_Toys_SeelieCreater_03");
            putGroup(groups, "DynamicAbility_Toys_Seelie_Purple", "SceneObj_Toys_SeelieCreater_04");
            putGroup(groups, "DynamicAbility_Toys_Seelie_Green", "SceneObj_Toys_SeelieCreater_05");
            putGroup(groups, "DynamicAbility_Toys_Endora", "SceneObj_Toys_Endora");
            putGroup(groups, "DynamicAbility_Toys_LeiLing", "SceneObj_Toys_LeiLing");
            putGroup(groups, "DynamicAbility_Toys_ShikiShogun", "SceneObj_Toys_ShikiShogun");
            putGroup(
                    groups,
                    "DynamicAbility_Toys_SeekerSeelieV3",
                    "SceneObj_Toys_SeekerWindCrystal_SeekerSeelieV3");
            putGroup(groups, "DynamicAbility_Toys_WeatherWizard", "SceneObj_Toys_WeatherWizard");
            putGroup(groups, "DynamicAbility_Toys_PaperCraftCrane", "SceneObj_Toys_PaperCraftCrane");
            putGroup(groups, "DynamicAbility_Toys_Sorush", "SceneObj_Toys_Sorush");
            putGroup(groups, "DynamicAbility_Toys_Sorush_Hat", "SceneObj_Toys_Sorush_Hat");
            putGroup(groups, "DynamicAbility_Toys_Octopus", "SceneObj_Toys_Octopus");
            putGroup(
                    groups,
                    "DynamicAbility_Toys_TreasureSeekingSeelie_Fontaine",
                    "SceneObj_Toys_SeekerWindCrystal_TreasureSeekingSeelie_Fontaine");
            putGroup(groups, "DynamicAbility_Toys_MiniSeelie_Fontaine", "SceneObj_Toys_MiniSeelie_Fontaine");
            putGroup(groups, "DynamicAbility_Toys_Salamander", "SceneObj_Toys_Salamander");
            putGroup(groups, "DynamicAbility_Toys_MachinePora", "SceneObj_Toys_MachinePora");
            putGroup(groups, "DynamicAbility_Toys_Felicette", "SceneObj_Toys_Felicette");
            putGroup(groups, "DynamicAbility_Toys_FrostConductor", "SceneObj_Toys_FrostConductor");

            widgetAbilityGroupMap = Collections.unmodifiableMap(map);
            abilityGroupAbilities = Collections.unmodifiableMap(groups);
            LOADED.set(true);
            Grasscutter.getLogger()
                    .debug(
                            "WidgetPetHelper loaded {} widget maps, {} ability groups",
                            map.size(),
                            groups.size());
        }
    }

    private static void putGroup(Map<String, List<String>> groups, String groupName, String ability) {
        groups.putIfAbsent(groupName, List.of(ability));
    }

    public static class ConfigWidgetNewRoot {
        public Map<String, WidgetConfigEntry> widgetConfigMap;
    }

    public static class WidgetConfigEntry {
        public String abilityGroupName;

        @SerializedName("isTeam")
        public boolean isTeam;
    }
}
