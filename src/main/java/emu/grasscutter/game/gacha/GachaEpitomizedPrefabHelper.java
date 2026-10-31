/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.gacha;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.DataLoader;
import emu.grasscutter.game.player.Player;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;

public class GachaEpitomizedPrefabHelper {
    public static final int LINKED_CHARACTER_SCHEDULE_ID = 5099;
    public static final int LINKED_WEAPON_SCHEDULE_ID = 5098;
    private static final Int2ObjectMap<Skin> SKINS = new Int2ObjectOpenHashMap<Skin>();

    public static int resolveChronicleCharacterWish(Player player) {
        if (player == null || player.getGachaInfo() == null) {
            return 0;
        }
        int wish = player.getGachaInfo().getChronicleBanner().getWishItemId();
        return wish > 0 && wish < 10000 ? wish : 0;
    }

    public static int resolveChronicleWeaponWish(Player player) {
        if (player == null || player.getGachaInfo() == null) {
            return 0;
        }
        int wish = player.getGachaInfo().getChronicleWeaponBanner().getWishItemId();
        if (wish >= 10000) {
            return wish;
        }
        int legacy = player.getGachaInfo().getChronicleBanner().getWishItemId();
        if (legacy >= 10000) {
            player.getGachaInfo().getChronicleWeaponBanner().setWishItemId(legacy);
            player.getGachaInfo().getChronicleBanner().setWishItemId(0);
            return legacy;
        }
        return 0;
    }

    public static synchronized void load() {
        SKINS.clear();
        try {
            List<PrefabEntry> entries = DataLoader.loadTableToList("GachaEpitomizedPrefabMap", PrefabEntry.class);
            if (entries == null || entries.isEmpty()) {
                Grasscutter.getLogger().debug("GachaEpitomizedPrefabMap empty; dedicated epitomized UI uses defaults only.");
                return;
            }
            for (PrefabEntry entry : entries) {
                if (entry.itemId <= 0 || entry.prefabPath == null || entry.prefabPath.isEmpty()) continue;
                Object preview = entry.previewPrefabPath;
                if (preview == null || ((String)preview).isEmpty()) {
                    preview = "UI_Tab_" + entry.prefabPath;
                }
                int[] display = entry.displayItemIds == null ? new int[]{} : entry.displayItemIds;
                SKINS.put(entry.itemId, new Skin(entry.prefabPath, (String)preview, entry.titlePath, display));
            }
            Grasscutter.getLogger().debug("Loaded {} epitomized banner skins.", (Object)SKINS.size());
        }
        catch (Exception ex) {
            Grasscutter.getLogger().warn("Failed to load GachaEpitomizedPrefabMap.json", ex);
        }
    }

    public static Skin resolve(int itemId) {
        return (Skin)SKINS.get(itemId);
    }

    public static int[] resolveWeaponDisplayPair(int wishItemId) {
        if (wishItemId <= 0) {
            return new int[0];
        }
        Skin skin = GachaEpitomizedPrefabHelper.resolve(wishItemId);
        if (skin != null && GachaEpitomizedPrefabHelper.containsItem(skin.getDisplayItemIds(), wishItemId)) {
            return skin.getDisplayItemIds();
        }
        for (Skin candidate : SKINS.values()) {
            int[] displayItemIds = candidate.getDisplayItemIds();
            if (displayItemIds.length < 2 || !GachaEpitomizedPrefabHelper.containsItem(displayItemIds, wishItemId)) continue;
            return displayItemIds;
        }
        if (skin != null && skin.getDisplayItemIds().length > 0) {
            return skin.getDisplayItemIds();
        }
        return new int[]{wishItemId};
    }

    private static boolean containsItem(int[] itemIds, int itemId) {
        if (itemIds == null) {
            return false;
        }
        for (int id : itemIds) {
            if (id != itemId) continue;
            return true;
        }
        return false;
    }

    public static final class PrefabEntry {
        private int itemId;
        private String prefabPath;
        private String previewPrefabPath;
        private String titlePath;
        private int[] displayItemIds;
    }

    public static final class Skin {
        private final String prefabPath;
        private final String previewPrefabPath;
        private final String titlePath;
        private final int[] displayItemIds;

        public Skin(String prefabPath, String previewPrefabPath, String titlePath, int[] displayItemIds) {
            this.prefabPath = prefabPath;
            this.previewPrefabPath = previewPrefabPath;
            this.titlePath = titlePath;
            this.displayItemIds = displayItemIds == null ? new int[]{} : displayItemIds;
        }

        public String getPrefabPath() {
            return this.prefabPath;
        }

        public String getPreviewPrefabPath() {
            return this.previewPrefabPath;
        }

        public String getTitlePath() {
            return this.titlePath;
        }

        public int[] getDisplayItemIds() {
            return this.displayItemIds;
        }
    }
}
