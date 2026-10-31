package emu.grasscutter.game.reward;

import emu.grasscutter.data.ResourceRegistry;
import emu.grasscutter.game.drop.YamlLootTables;
import emu.grasscutter.game.drop.ArtifactResolver;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.random.RandomGenerator;

/** Loaded after the game item resources; CSS owns chest and scene-point rewards. */
public final class RewardConfiguration {
    private static volatile RewardConfiguration active;

    private final YamlLootTables loot;
    private final CssRewards css;

    RewardConfiguration(YamlLootTables loot, CssRewards css) {
        this.loot = loot;
        this.css = css;
    }

    private static final Path REWARD_DIRECTORY = Path.of("rewards");

    /**
     * Compile every reward source before publishing the new configuration.
     * A failed /reload rewards leaves the previous compiled rules and registry active.
     */
    public static synchronized void load() {
        try {
            initializeExternalDirectory(REWARD_DIRECTORY);
            var artifacts = ArtifactResolver.fromGameResources();
            var registry = ResourceRegistry.fromDefinitions(
                    Files.readString(REWARD_DIRECTORY.resolve("registry.tsv"), StandardCharsets.UTF_8),
                    artifacts);
            var loot = YamlLootTables.parse(
                    Files.readString(REWARD_DIRECTORY.resolve("loot.yaml"), StandardCharsets.UTF_8),
                    registry::resolve, artifacts);
            var css = CssRewards.parse(
                    readLootCssDirectory(REWARD_DIRECTORY.resolve("loot.yaml").toUri().toURL()),
                    registry, loot);
            var compiled = new RewardConfiguration(loot, css);
            ResourceRegistry.install(registry);
            active = compiled;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "Cannot load reward configuration from " + REWARD_DIRECTORY.toAbsolutePath(), e);
        }
    }

    /** Copy packaged defaults once. Subsequent loads never overwrite operator edits. */
    static void initializeExternalDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            if (!Files.isDirectory(directory)) {
                throw new IOException("Reward path is not a directory: " + directory);
            }
            return;
        }
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("loot.yaml"), readBundled("loot.yaml"),
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("registry.tsv"), readBundled("registry.tsv"),
                StandardCharsets.UTF_8);
        URL marker = RewardConfiguration.class.getResource("/rewards/loot.yaml");
        if (marker == null) throw new IOException("Missing packaged rewards/loot.yaml");
        for (var entry : readLootCssFiles(marker).entrySet()) {
            Files.writeString(directory.resolve(entry.getKey()), entry.getValue(),
                    StandardCharsets.UTF_8);
        }
    }

    static String readBundled(String filename) throws IOException {
        try (InputStream stream = RewardConfiguration.class.getResourceAsStream("/rewards/" + filename)) {
            if (stream == null) throw new IOException("Missing bundled reward resource " + filename);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Discover every direct .lcss LootCSS file in bundled rewards, in filename order. */
    static String readBundledLootCss() throws IOException {
        URL marker = RewardConfiguration.class.getResource("/rewards/loot.yaml");
        if (marker == null) throw new IOException("Missing bundled rewards directory");
        return readLootCssDirectory(marker);
    }

    /**
     * The loot.yaml URL identifies the resource directory in both an expanded
     * classpath and a packaged JAR; no stylesheet filenames are hardcoded.
     */
    static String readLootCssDirectory(URL marker) throws IOException {
        return String.join("\n", readLootCssFiles(marker).values());
    }

    private static Map<String, String> readLootCssFiles(URL marker) throws IOException {
        var stylesheets = new TreeMap<String, String>();
        if ("file".equals(marker.getProtocol())) {
            Path directory;
            try {
                directory = Path.of(marker.toURI()).getParent();
            } catch (URISyntaxException e) {
                throw new IOException("Invalid reward resource directory: " + marker, e);
            }
            try (var files = Files.list(directory)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".lcss"))
                        .toList()) {
                    stylesheets.put(file.getFileName().toString(),
                            Files.readString(file, StandardCharsets.UTF_8));
                }
            }
        } else if ("jar".equals(marker.getProtocol())) {
            var connection = (JarURLConnection) marker.openConnection();
            connection.setUseCaches(false);
            String entry = connection.getEntryName();
            if (entry == null || !entry.contains("/")) {
                throw new IOException("Invalid reward resource location: " + marker);
            }
            String prefix = entry.substring(0, entry.lastIndexOf('/') + 1);
            try (JarFile archive = connection.getJarFile()) {
                Enumeration<java.util.jar.JarEntry> entries = archive.entries();
                while (entries.hasMoreElements()) {
                    var candidate = entries.nextElement();
                    String name = candidate.getName();
                    if (candidate.isDirectory() || !name.startsWith(prefix)
                            || !name.endsWith(".lcss")
                            || name.substring(prefix.length()).contains("/")) continue;
                    try (InputStream stream = archive.getInputStream(candidate)) {
                        stylesheets.put(name.substring(prefix.length()),
                                new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        } else {
            throw new IOException("Unsupported rewards resource URL: " + marker);
        }
        if (stylesheets.isEmpty()) {
            throw new IOException("No LootCSS files found in bundled rewards directory");
        }
        return stylesheets;
    }

    public static RewardConfiguration current() {
        var value = active;
        if (value == null) throw new IllegalStateException("Reward configuration is not loaded");
        return value;
    }

    public Map<Integer, Integer> roll(
            String targetType, Map<String, ?> properties, RandomGenerator random) {
        return roll(targetType, properties, random, RewardContext.NONE);
    }

    public Map<Integer, Integer> roll(
            String targetType, Map<String, ?> properties, RandomGenerator random,
            RewardContext context) {
        var selected = css.resolve(targetType, properties);
        requireChestLootKeys(targetType, selected, properties);
        if ("blossom".equals(targetType)) {
            requireBlossomRewardKeys(selected, properties);
        }
        return RewardRoller.resolveKeys(RewardRoller.roll(selected, loot, random),
                loot, context);
    }

    static void requireChestLootKeys(
            String targetType, CssRewards.KeySelection selected, Map<String, ?> properties) {
        if ("chest".equals(targetType)
                && selected.loot().values().stream().noneMatch(count -> count > 0)
                && selected.rangedLoot().isEmpty()) {
            throw new IllegalStateException(
                    "No chest loot table matched (missing or invalid type/grade/level): " + properties);
        }
    }

    static void requireBlossomRewardKeys(
            CssRewards.KeySelection selected, Map<String, ?> properties) {
        Object type = properties.get("type");
        boolean experience = selected.items().getOrDefault("genshin.experience.adventure", 0) > 0;
        boolean companionship = selected.items().getOrDefault("genshin.experience.companionship", 0) > 0;
        boolean wealth = selected.items().getOrDefault("genshin.currency.mora", 0) > 0;
        boolean books = java.util.Set.of(
                "genshin.avatar_up.wanderers_advice",
                "genshin.avatar_up.adventurers_experience",
                "genshin.avatar_up.heros_wit").stream()
                .anyMatch(key -> selected.items().getOrDefault(key, 0) > 0
                        || selected.rangedItems().containsKey(key));
        boolean usesLoot = !selected.loot().isEmpty() || !selected.rangedLoot().isEmpty();
        if (!experience || !companionship || usesLoot
                || !("wealth".equals(type) && wealth && !books
                    || "revelation".equals(type) && books && !wealth)) {
            throw new IllegalStateException("No complete blossom reward configured for " + properties);
        }
    }

    static void requireChestLoot(
            String targetType, CssRewards.Selection selected, Map<String, ?> properties) {
        if ("chest".equals(targetType)
                && selected.loot().values().stream().noneMatch(count -> count > 0)
                && selected.rangedLoot().isEmpty()) {
            throw new IllegalStateException(
                    "No chest loot table matched (missing or invalid type/grade/level): " + properties);
        }
    }

    /** Refuse unconfigured or partially configured ley-line rewards. */
    static void requireBlossomReward(
            CssRewards.Selection selected, Map<String, ?> properties) {
        Object type = properties.get("type");
        boolean experience = selected.items().getOrDefault(102, 0) > 0;
        boolean companionship = selected.items().getOrDefault(105, 0) > 0;
        boolean wealth = selected.items().getOrDefault(202, 0) > 0;
        boolean books = java.util.Set.of(104001, 104002, 104003).stream()
                .anyMatch(id -> selected.items().getOrDefault(id, 0) > 0
                        || selected.rangedItems().containsKey(id));
        boolean usesLoot = !selected.loot().isEmpty() || !selected.rangedLoot().isEmpty();
        if (!experience || !companionship || usesLoot
                || !("wealth".equals(type) && wealth && !books
                    || "revelation".equals(type) && books && !wealth)) {
            throw new IllegalStateException("No complete blossom reward configured for " + properties);
        }
    }

    /** Each paid reward set is rolled independently, including Luna I triple claims. */
    public Map<Integer, Integer> rollBlossom(
            String type, int worldLevel, int rolls, RandomGenerator random) {
        if (!("wealth".equals(type) || "revelation".equals(type))
                || worldLevel < 0 || worldLevel > 9 || rolls < 1 || rolls > 3) {
            throw new IllegalArgumentException(
                    "Unknown blossom type/world level: " + type + "/" + worldLevel);
        }
        var attrs = Map.of("type", (Object) type, "WR", (Object) worldLevel);
        var total = new java.util.TreeMap<Integer, Integer>();
        for (int i = 0; i < rolls; i++) {
            roll("blossom", attrs, random).forEach(
                    (id, count) -> total.merge(id, count, Math::addExact));
        }
        return Map.copyOf(total);
    }

    // The original 7.1 ChestDrop source uses Chinese drop tags.
    // LootCSS rules use the English semantic tags shared with loot.yaml.
    // Keep the native input unchanged for DropSystem's native-table fallback.
    private static final Map<String, String> CSS_BOSS_TAGS = Map.ofEntries(
            Map.entry("北风狼", "andrius"),
            Map.entry("若陀龙王水冰", "azhdaha_hydro_cryo"),
            Map.entry("若陀龙王水电", "azhdaha_hydro_electro"),
            Map.entry("若陀龙王火冰", "azhdaha_pyro_cryo"),
            Map.entry("若陀龙王火电", "azhdaha_pyro_electro"),
            Map.entry("雷电将军", "raiden_shogun"),
            Map.entry("「正机之神」", "shouki_no_kami"),
            Map.entry("女士", "signora"),
            Map.entry("风魔龙", "stormterror"),
            Map.entry("公子", "tartaglia"),
            Map.entry("兆载永劫龙兽", "aeonblight_drake"),
            Map.entry("半永恒统辖矩阵", "algorithm_semi_intransient_matrix"),
            Map.entry("无相之风", "anemo_hypostasis"),
            Map.entry("深海龙蜥组合", "bathysmal_vishap_herd"),
            Map.entry("嵌合翼骏狮", "chimera_sky_roamer"),
            Map.entry("无相之冰", "cryo_hypostasis"),
            Map.entry("急冻树", "cryo_regisvine"),
            Map.entry("无相之草", "dendro_hypostasis"),
            Map.entry("无相之雷", "electro_hypostasis"),
            Map.entry("掣电树", "electro_regisvine"),
            Map.entry("铁甲熔火帝皇", "emperor_of_fire_and_iron"),
            Map.entry("实验性场力发生装置", "experimental_field_generator"),
            Map.entry("霜夜灵嗣", "frostnight_scion"),
            Map.entry("无相之岩", "geo_hypostasis"),
            Map.entry("贪食匿叶龙山王", "gluttonous_yumkasaur_mountain_king"),
            Map.entry("黄金王兽", "golden_wolflord"),
            Map.entry("金焰绒翼龙暴君", "goldflame_qucusaure_tyrant"),
            Map.entry("灵觉隐修的迷者", "holawaqa_ngoubou"),
            Map.entry("无相之水", "hydro_hypostasis"),
            Map.entry("水形幻人", "hydro_tulpa"),
            Map.entry("冰风组曲", "icewind_suite"),
            Map.entry("翠翎恐蕈", "jadeplume_terrorshroom"),
            Map.entry("重拳出击鸭", "knuckle_duck"),
            Map.entry("熔岩辉龙像", "lava_dragon_statue"),
            Map.entry("魔像督军", "legatus_golem"),
            Map.entry("蕴光月幻蝶", "lightbearing_lunar_moth"),
            Map.entry("深邃魹语之主", "lord_of_the_deep_dreadwing"),
            Map.entry("魔偶剑鬼", "maguu_kenki"),
            Map.entry("精密发条机关", "precision_clockwork"),
            Map.entry("千年珍珠骏麟", "millennial_pearl_seahorse"),
            Map.entry("纯水精灵", "oceanid"),
            Map.entry("恒常机关阵列", "perpetual_mechanical_array"),
            Map.entry("原初监视者", "primal_watcher"),
            Map.entry("古岩龙蜥冰", "primo_geovishap_cryo"),
            Map.entry("古岩龙蜥电", "primo_geovishap_electro"),
            Map.entry("古岩龙蜥水", "primo_geovishap_hydro"),
            Map.entry("古岩龙蜥火", "primo_geovishap_pyro"),
            Map.entry("无相之火", "pyro_hypostasis"),
            Map.entry("爆炎树", "pyro_regisvine"),
            Map.entry("蕴光异兽", "radiant_beast"),
            Map.entry("遗迹巨蛇", "ruin_serpent"),
            Map.entry("秘源机兵·构型械", "secret_source_automaton_configuration_device"),
            Map.entry("秘源机兵·统御械", "secret_source_automaton_overseer"),
            Map.entry("风蚀沙虫", "setekh_wenut"),
            Map.entry("隐山猊兽", "solitary_suanni"),
            Map.entry("深邃摹结株", "tenebrous_papilla"),
            Map.entry("雷音权现", "thunder_manifestation"),
            Map.entry("不灭衍生造物", "undying_replicant"));

    static String cssBossTag(String nativeTag) {
        return CSS_BOSS_TAGS.getOrDefault(nativeTag, nativeTag);
    }

    /** Roll configured world Boss materials and shared rewards from CSS.
     * Unknown Boss identities and uncovered levels retain their native path.
     */
    public Map<Integer, Integer> rollWorldBoss(
            String tag, int nativeBossLevel, RandomGenerator random) {
        var attributes = Map.of(
                "type", "world",
                "tag", cssBossTag(tag),
                "level", nativeBossLevel);
        var selection = css.resolve("boss", attributes);
        if (selection.loot().isEmpty() && selection.rangedLoot().isEmpty()
                && selection.rangedItems().isEmpty()) {
            return null;
        }
        return RewardRoller.resolveKeys(RewardRoller.roll(selection, loot, random),
                loot, RewardContext.NONE);
    }

    /** Qualified table IDs can be executed without CSS selection. */
    public Map<Integer, Integer> rollLoot(String resourceId, RandomGenerator random) {
        return RewardRoller.resolveKeys(loot.roll(resourceId, random), loot, RewardContext.NONE);
    }
}
