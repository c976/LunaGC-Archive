package emu.grasscutter.game.ability;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketAllShareCDDataNotify;
import emu.grasscutter.server.packet.send.PacketAvatarSkillInfoNotify;
import emu.grasscutter.utils.FileUtils;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fontaine dive / absorb ShareCD.
 *
 * {@code _cd_end_time} must be scene-time based (ms since scene start), matching
 * {@link emu.grasscutter.game.world.Scene#getSceneTime()}. Wall-clock millis makes the client
 * show remaining ≈ unixTimestamp (e.g. 1787614000.0) on the attack button.
 */
public final class ShareCDHelper {
    private ShareCDHelper() {}

    private static volatile Map<Integer, Float> coolDownSeconds;
    private static volatile Map<Integer, Integer> maxCharges;
    private static final ConcurrentHashMap<Integer, Int2LongMap> PLAYER_ENDS =
            new ConcurrentHashMap<>();

    private static void ensureLoaded() {
        if (coolDownSeconds != null) {
            return;
        }
        synchronized (ShareCDHelper.class) {
            if (coolDownSeconds != null) {
                return;
            }
            Map<Integer, Float> cds = new ConcurrentHashMap<>();
            Map<Integer, Integer> charges = new ConcurrentHashMap<>();
            try {
                Path path = FileUtils.getExcelPath("ShareCDExcelConfigData.json");
                String json = Files.readString(path, StandardCharsets.UTF_8);
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject row = el.getAsJsonObject();
                    if (!row.has("id")) {
                        continue;
                    }
                    int id = row.get("id").getAsInt();
                    int maxCharge = row.has("maxChargeNum") ? row.get("maxChargeNum").getAsInt() : 1;
                    charges.put(id, Math.max(1, maxCharge));
                    float cd = 0f;
                    if (row.has("INAFFABEFEK") && row.get("INAFFABEFEK").isJsonArray()) {
                        JsonArray slots = row.getAsJsonArray("INAFFABEFEK");
                        if (!slots.isEmpty() && slots.get(0).isJsonObject()) {
                            JsonObject slot0 = slots.get(0).getAsJsonObject();
                            if (slot0.has("coolDownTime")) {
                                cd = slot0.get("coolDownTime").getAsFloat();
                            }
                        }
                    }
                    if (cd > 0f) {
                        cds.put(id, cd);
                    }
                }
                Grasscutter.getLogger().info("[ShareCD] loaded {} entries from excel", cds.size());
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("[ShareCD] excel load failed, using dive fallbacks: {}", t.toString());
                cds.put(20046, 1.0f);
                cds.put(20050, 4.0f);
                cds.put(20059, 2.0f);
                cds.put(20060, 1.5f);
                cds.put(20062, 1.0f);
                cds.put(20063, 0.5f);
                charges.put(20046, 1);
                charges.put(20050, 2);
                charges.put(20059, 1);
                charges.put(20060, 1);
                charges.put(20062, 1);
                charges.put(20063, 1);
            }
            coolDownSeconds = cds;
            maxCharges = charges;
        }
    }

    /** Scene-local clock used by ShareCD UI (not wall clock). */
    private static long nowSceneMs(Player player) {
        try {
            if (player.getScene() != null) {
                return player.getScene().getSceneTime() & 0xffffffffL;
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    public static void startShareCd(Player player, int skillId) {
        if (player == null || skillId <= 0) {
            return;
        }
        ensureLoaded();
        Float seconds = coolDownSeconds.get(skillId);
        if (seconds == null || seconds <= 0f) {
            return;
        }
        long endMs = nowSceneMs(player) + Math.round(seconds * 1000.0);
        Int2LongMap ends =
                PLAYER_ENDS.computeIfAbsent(player.getUid(), u -> new Int2LongOpenHashMap());
        ends.put(skillId, endMs);
        try {
            player.sendPacket(new PacketAllShareCDDataNotify(skillId, 0, endMs, false));
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("[ShareCD] notify failed skillId={}: {}", skillId, t.toString());
        }
        syncAvatarSkillCharges(player, skillId);
    }

    /**
     * After AddAvatarSkillInfo: only sync max charges. Do NOT push AllShareCDDataNotify here —
     * a wall-clock / wrong ready end freezes the dive attack button at a huge number.
     */
    public static void addAvatarSkillInfo(Player player, int skillId) {
        if (player == null || skillId <= 0) {
            return;
        }
        ensureLoaded();
        Int2LongMap ends = PLAYER_ENDS.get(player.getUid());
        if (ends != null) {
            ends.remove(skillId);
        }
        syncAvatarSkillCharges(player, skillId);
    }

    public static void removeAvatarSkillInfo(Player player, int skillId) {
        if (player == null || skillId <= 0) {
            return;
        }
        Int2LongMap ends = PLAYER_ENDS.get(player.getUid());
        if (ends != null) {
            ends.remove(skillId);
        }
    }

    private static void syncAvatarSkillCharges(Player player, int skillId) {
        try {
            EntityAvatar entity = player.getTeamManager() != null
                    ? player.getTeamManager().getCurrentAvatarEntity()
                    : null;
            if (entity == null) {
                return;
            }
            Avatar avatar = entity.getAvatar();
            if (avatar == null) {
                return;
            }
            ensureLoaded();
            int max = maxCharges.getOrDefault(skillId, 1);
            AvatarSkillData data = GameData.getAvatarSkillDataMap().get(skillId);
            if (data != null && data.getMaxChargeNum() > 0) {
                max = data.getMaxChargeNum();
            }
            avatar.getSkillExtraChargeMap().put(skillId, max);
            Int2IntMap map = new Int2IntArrayMap();
            map.put(skillId, max);
            player.sendPacket(new PacketAvatarSkillInfoNotify(avatar.getGuid(), map));
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("[ShareCD] skillInfo sync skipped: {}", t.toString());
        }
    }

    public static void clearPlayer(int uid) {
        PLAYER_ENDS.remove(uid);
    }
}
