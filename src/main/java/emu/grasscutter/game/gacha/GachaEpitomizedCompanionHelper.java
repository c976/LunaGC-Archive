/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.data.GameData
 *  emu.grasscutter.data.excels.ItemData
 *  emu.grasscutter.game.gacha.GachaBanner
 *  emu.grasscutter.game.inventory.ItemType
 *  emu.grasscutter.game.inventory.MaterialType
 *  emu.grasscutter.utils.FileUtils
 *  emu.grasscutter.utils.JsonUtils
 *  it.unimi.dsi.fastutil.ints.Int2ObjectMap
 *  it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
 *  it.unimi.dsi.fastutil.ints.IntArrayList
 *  it.unimi.dsi.fastutil.ints.IntArrays
 */
package emu.grasscutter.game.gacha;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.gacha.GachaBanner;
import emu.grasscutter.game.gacha.GachaEpitomizedPrefabHelper;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GachaEpitomizedCompanionHelper {
    private static final int[] EMPTY = new int[0];
    private static int[] allFourStarChars = EMPTY;
    private static int[] allFourStarWeapons = EMPTY;
    private static final Int2ObjectMap<int[]> characterCompanions = new Int2ObjectOpenHashMap();

    public static synchronized void load() {
        GachaEpitomizedCompanionHelper.rebuildGlobalPools();
        GachaEpitomizedCompanionHelper.loadCharacterCompanions();
    }

    public static int[] getAllFourStarCharacters() {
        GachaEpitomizedCompanionHelper.ensurePoolsBuilt();
        return allFourStarChars;
    }

    public static int[] getAllFourStarWeapons() {
        GachaEpitomizedCompanionHelper.ensurePoolsBuilt();
        return allFourStarWeapons;
    }

    private static void loadCharacterCompanions() {
        characterCompanions.clear();
        try {
            Path path = FileUtils.getDataPathTsjJsonTsv((String)"GachaEpitomizedCompanions");
            if (path == null || !Files.exists(path, new LinkOption[0])) {
                return;
            }
            CompanionsFile file = (CompanionsFile)JsonUtils.loadToClass((Path)path, CompanionsFile.class);
            if (file == null || file.characterCompanions == null) {
                return;
            }
            for (CharacterCompanionEntry entry : file.characterCompanions) {
                if (entry.itemId <= 0 || entry.rateUp4 == null || entry.rateUp4.length <= 0) continue;
                characterCompanions.put(entry.itemId, entry.rateUp4);
            }
            Grasscutter.getLogger().debug("Loaded {} character 4-star companion mappings.", (Object)characterCompanions.size());
        }
        catch (Exception ex) {
            Grasscutter.getLogger().warn("Failed to load GachaEpitomizedCompanions.json", (Throwable)ex);
        }
    }

    private static void rebuildGlobalPools() {
        if (GameData.getItemDataMap() == null || GameData.getItemDataMap().isEmpty()) {
            Grasscutter.getLogger().warn("GameData not ready; epitomized 4-star pools will rebuild on first use.");
            allFourStarChars = EMPTY;
            allFourStarWeapons = EMPTY;
            return;
        }
        IntArrayList chars = new IntArrayList();
        IntArrayList weapons = new IntArrayList();
        for (ItemData item : GameData.getItemDataMap().values()) {
            int id;
            if (item == null || item.getRankLevel() != 4 || (id = item.getId()) == 20001) continue;
            if (item.getMaterialType() == MaterialType.MATERIAL_AVATAR && id < 5000 && id != 1005 && id != 1007) {
                chars.add(id);
                continue;
            }
            if (item.getItemType() != ItemType.ITEM_WEAPON || id <= 10000) continue;
            weapons.add(id);
        }
        IntArrays.quickSort((int[])chars.elements(), (int)0, (int)chars.size());
        IntArrays.quickSort((int[])weapons.elements(), (int)0, (int)weapons.size());
        allFourStarChars = chars.toIntArray();
        allFourStarWeapons = weapons.toIntArray();
        Grasscutter.getLogger().debug("Epitomized 4-star pools: {} characters, {} weapons.", (Object)allFourStarChars.length, (Object)allFourStarWeapons.length);
    }

    private static void ensurePoolsBuilt() {
        if (allFourStarChars.length == 0 || allFourStarWeapons.length == 0) {
            GachaEpitomizedCompanionHelper.rebuildGlobalPools();
        }
    }

    public static int[] resolveCharacterRateUp4(int wishItemId, int[] bannerFallback) {
        if (wishItemId > 0) {
            int[] mapped = (int[])characterCompanions.get(wishItemId);
            if (mapped != null && mapped.length > 0) {
                return mapped;
            }
            GachaEpitomizedPrefabHelper.Skin skin = GachaEpitomizedPrefabHelper.resolve(wishItemId);
            if (skin != null && skin.getDisplayItemIds().length > 0) {
                return skin.getDisplayItemIds();
            }
        }
        if (bannerFallback != null && bannerFallback.length > 0) {
            return bannerFallback;
        }
        return EMPTY;
    }

    public static int[] resolveRateUp4(GachaBanner banner, int wishItemId) {
        if (banner.getScheduleId() == 5099 && wishItemId > 0) {
            return GachaEpitomizedCompanionHelper.resolveCharacterRateUp4(wishItemId, banner.getRateUpItems4());
        }
        if (banner.getScheduleId() == 5098) {
            GachaEpitomizedCompanionHelper.ensurePoolsBuilt();
            return allFourStarWeapons.length > 0 ? allFourStarWeapons : banner.getRateUpItems4();
        }
        if (!banner.hasEpitomized()) {
            if (banner.getRateUpItems4().length > 0) {
                return banner.getRateUpItems4();
            }
            if (banner.isWeaponFocusedPool()) {
                GachaEpitomizedCompanionHelper.ensurePoolsBuilt();
                return allFourStarWeapons;
            }
            return banner.getRateUpItems4();
        }
        GachaEpitomizedCompanionHelper.ensurePoolsBuilt();
        if (banner.isCharacterEpitomizedPool()) {
            if (banner.getRateUpItems4().length > 0) {
                return banner.getRateUpItems4();
            }
            return allFourStarChars.length > 0 ? allFourStarChars : EMPTY;
        }
        if (banner.isWeaponEpitomizedPool()) {
            return allFourStarWeapons.length > 0 ? allFourStarWeapons : banner.getRateUpItems4();
        }
        return banner.getRateUpItems4();
    }

    public static int[] pickDisplaySample(int[] pool, int count) {
        if (pool == null || pool.length == 0 || count <= 0) {
            return EMPTY;
        }
        if (pool.length <= count) {
            return pool;
        }
        int[] shuffled = (int[])pool.clone();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = shuffled.length - 1; i > 0; --i) {
            int j = random.nextInt(i + 1);
            int tmp = shuffled[i];
            shuffled[i] = shuffled[j];
            shuffled[j] = tmp;
        }
        int[] sample = new int[count];
        System.arraycopy(shuffled, 0, sample, 0, count);
        IntArrays.quickSort((int[])sample);
        return sample;
    }

    private static final class CompanionsFile {
        private List<CharacterCompanionEntry> characterCompanions;

        private CompanionsFile() {
        }
    }

    private static final class CharacterCompanionEntry {
        private int itemId;
        private int[] rateUp4;

        private CharacterCompanionEntry() {
        }
    }
}

