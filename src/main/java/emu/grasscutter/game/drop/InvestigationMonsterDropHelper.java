package emu.grasscutter.game.drop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.world.Scene;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import emu.grasscutter.utils.FileUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Open-world Common/Elite drops from InvestigationMonster reward previews
 * (bounty handbook "chance to obtain"). Official killDropId rows are energy-only, so materials
 * never land without this.
 */
public final class InvestigationMonsterDropHelper {
    // Resolved through FileUtils, so these follow the configured resource folder. Hardcoding
    // "resources" meant every server that names its folder anything else - Resources-main, say -
    // failed to read these and logged a stack trace on each monster kill.
    private static final Path INVEST_FILE =
            FileUtils.getExcelPath("InvestigationMonsterConfigData.json");
    private static final Path PREVIEW_FILE =
            FileUtils.getExcelPath("RewardPreviewExcelConfigData.json");

    /** monsterId -> material entries (expected counts from preview). */
    private static volatile Map<Integer, List<MatRate>> byMonsterId;

    private InvestigationMonsterDropHelper() {}

    private static final class MatRate {
        final int itemId;
        final double expected;

        MatRate(int itemId, double expected) {
            this.itemId = itemId;
            this.expected = expected;
        }
    }

    private static void ensureLoaded() {
        if (byMonsterId != null) {
            return;
        }
        synchronized (InvestigationMonsterDropHelper.class) {
            if (byMonsterId != null) {
                return;
            }
            Map<Integer, List<MatRate>> map = new HashMap<>();
            try {
                Map<Integer, List<MatRate>> previews = loadPreviews();
                JsonArray invest =
                        JsonParser.parseString(Files.readString(INVEST_FILE, StandardCharsets.UTF_8))
                                .getAsJsonArray();
                int linked = 0;
                for (JsonElement el : invest) {
                    JsonObject o = el.getAsJsonObject();
                    String cat = o.has("monsterCategory") ? o.get("monsterCategory").getAsString() : "";
                    if (!"Common".equals(cat) && !"Elite".equals(cat)) {
                        continue;
                    }
                    int previewId = o.has("rewardPreviewId") ? o.get("rewardPreviewId").getAsInt() : 0;
                    List<MatRate> rates = previews.get(previewId);
                    if (rates == null || rates.isEmpty()) {
                        continue;
                    }
                    if (!o.has("monsterIdList")) {
                        continue;
                    }
                    for (JsonElement mid : o.getAsJsonArray("monsterIdList")) {
                        map.put(mid.getAsInt(), rates);
                        linked++;
                    }
                }
                byMonsterId = map;
                Grasscutter.getLogger()
                        .info(
                                "InvestigationMonsterDropHelper loaded monsters={} previewLinks={}",
                                map.size(),
                                linked);
            } catch (Throwable t) {
                byMonsterId = Map.of();
                Grasscutter.getLogger().error("InvestigationMonsterDropHelper load failed", t);
            }
        }
    }

    private static Map<Integer, List<MatRate>> loadPreviews() throws Exception {
        Map<Integer, List<MatRate>> out = new HashMap<>();
        JsonArray arr =
                JsonParser.parseString(Files.readString(PREVIEW_FILE, StandardCharsets.UTF_8))
                        .getAsJsonArray();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            if (!o.has("id")) {
                continue;
            }
            int id = o.get("id").getAsInt();
            if (!o.has("previewItems")) {
                continue;
            }
            List<MatRate> rates = new ArrayList<>();
            for (JsonElement pe : o.getAsJsonArray("previewItems")) {
                JsonObject p = pe.getAsJsonObject();
                int itemId = p.has("id") ? p.get("id").getAsInt() : 0;
                if (itemId <= 0 || itemId == 202) {
                    continue; // mora handled separately
                }
                // Skip artifact placeholders / empty rates
                if (itemId >= 400000 && itemId < 500000) {
                    continue;
                }
                String count = p.has("count") ? p.get("count").getAsString() : "";
                if (count == null || count.isBlank()) {
                    continue;
                }
                try {
                    double expected = Double.parseDouble(count.trim());
                    if (expected > 0) {
                        rates.add(new MatRate(itemId, expected));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (!rates.isEmpty()) {
                out.put(id, List.copyOf(rates));
            }
        }
        return out;
    }

    public static void tryDrop(EntityMonster monster) {
        if (monster == null || monster.getScene() == null || monster.getMonsterData() == null) {
            return;
        }
        // Domains / dungeons keep their own drop tables.
        if (monster.getScene().getDungeonManager() != null) {
            return;
        }
        // TrainingPads / empty-drop marker: killDropId rewritten to 99900001.
        String cn = monster.getClass().getName();
        if (cn != null && cn.startsWith("com.local.trainingpads.")) {
            return;
        }
        if (monster.getMonsterData().getKillDropId() == 99900001) {
            return;
        }
        ensureLoaded();
        List<MatRate> rates = byMonsterId.get(monster.getMonsterData().getId());
        if (rates == null || rates.isEmpty()) {
            return;
        }
        Scene scene = monster.getScene();
        List<Player> players = scene.getPlayers();
        if (players == null || players.isEmpty()) {
            return;
        }
        Player host = scene.getWorld() != null ? scene.getWorld().getHost() : players.get(0);
        if (host == null) {
            host = players.get(0);
        }

        List<GameItem> items = new ArrayList<>();
        int mora = moraForLevel(monster.getLevel());
        if (mora > 0) {
            items.add(new GameItem(202, mora));
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (MatRate rate : rates) {
            int amount = rollExpected(rate.expected, rng);
            if (amount > 0) {
                items.add(new GameItem(rate.itemId, amount));
            }
        }
        if (items.isEmpty()) {
            return;
        }
        try {
            // Ground drops for the host (same as DropSystem fallToGround path).
            for (GameItem item : items) {
                scene.addItemEntity(item.getItemId(), item.getCount(), monster);
            }
            Grasscutter.getLogger()
                    .debug(
                            "InvestigationMonsterDrop monster={} level={} items={}",
                            monster.getMonsterData().getId(),
                            monster.getLevel(),
                            items.size());
        } catch (Throwable t) {
            // Fallback: inventory grant
            try {
                host.getInventory().addItems(items, ActionReason.MonsterDie);
            } catch (Throwable t2) {
                Grasscutter.getLogger()
                        .warn(
                                "InvestigationMonsterDrop failed monster={}: {}",
                                monster.getMonsterData().getId(),
                                t2.toString());
            }
        }
    }

    private static int rollExpected(double expected, ThreadLocalRandom rng) {
        if (expected <= 0) {
            return 0;
        }
        int guaranteed = (int) Math.floor(expected);
        double frac = expected - guaranteed;
        int extra = rng.nextDouble() < frac ? 1 : 0;
        return guaranteed + extra;
    }

    private static int moraForLevel(int level) {
        if (level < 1) {
            level = 1;
        }
        // Rough official open-world band (matches MonsterDrop vanguard-ish scaling).
        if (level < 20) {
            return 14 + ThreadLocalRandom.current().nextInt(8);
        }
        if (level < 40) {
            return 20 + ThreadLocalRandom.current().nextInt(12);
        }
        if (level < 60) {
            return 26 + ThreadLocalRandom.current().nextInt(14);
        }
        if (level < 80) {
            return 30 + ThreadLocalRandom.current().nextInt(16);
        }
        return 32 + ThreadLocalRandom.current().nextInt(18);
    }
}
