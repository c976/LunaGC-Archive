/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.data.DataLoader
 *  emu.grasscutter.data.GameData
 *  emu.grasscutter.data.common.ItemParamData
 *  emu.grasscutter.data.excels.InvestigationMonsterData
 *  emu.grasscutter.data.excels.RewardPreviewData
 */
package emu.grasscutter.game.drop;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.DataLoader;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.InvestigationMonsterData;
import emu.grasscutter.data.excels.RewardPreviewData;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class BossChestDropTagResolver {
    private static final Map<Integer, String> DROP_TAG_BY_GROUP = new HashMap<Integer, String>();
    private static final Map<Integer, String> DROP_TAG_BY_INVESTIGATION = new HashMap<Integer, String>();
    private static boolean loaded;

    private BossChestDropTagResolver() {
    }

    public static String resolve(int groupId, int monsterId) {
        BossChestDropTagResolver.ensureLoaded();
        String tag = DROP_TAG_BY_GROUP.get(groupId);
        if (tag != null) {
            return tag;
        }
        InvestigationMonsterData investigation = BossChestDropTagResolver.findInvestigation(groupId, monsterId);
        if (investigation != null && (tag = DROP_TAG_BY_INVESTIGATION.get(investigation.getId())) != null) {
            return tag;
        }
        return null;
    }

    public static int resolveBossMaterialId(int monsterId) {
        InvestigationMonsterData investigation = BossChestDropTagResolver.findInvestigation(0, monsterId);
        if (investigation == null) {
            return 0;
        }
        RewardPreviewData preview = BossChestDropTagResolver.resolveRewardPreview(investigation.getRewardPreviewId());
        if (preview == null || preview.getPreviewItems() == null) {
            return 0;
        }
        for (ItemParamData param : preview.getPreviewItems()) {
            if (param.getId() <= 100000) continue;
            return param.getId();
        }
        return 0;
    }

    private static RewardPreviewData resolveRewardPreview(int previewId) {
        RewardPreviewData preview = (RewardPreviewData)GameData.getRewardPreviewDataMap().get(previewId);
        if (preview != null) {
            return preview;
        }
        for (int offset = 1; offset <= 4; ++offset) {
            preview = (RewardPreviewData)GameData.getRewardPreviewDataMap().get(previewId - offset);
            if (preview == null) continue;
            return preview;
        }
        return null;
    }

    private static InvestigationMonsterData findInvestigation(int groupId, int monsterId) {
        for (InvestigationMonsterData investigation : GameData.getInvestigationMonsterDataMap().values()) {
            if (monsterId > 0 && investigation.getMonsterIdList() != null && investigation.getMonsterIdList().contains(monsterId)) {
                return investigation;
            }
            if (groupId <= 0 || investigation.getGroupIdList() == null || !investigation.getGroupIdList().contains(groupId)) continue;
            return investigation;
        }
        return null;
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            TagConfig config = (TagConfig)DataLoader.loadClass((String)"boss-drop-tags.json", TagConfig.class);
            if (config.byGroupId != null) {
                config.byGroupId.forEach((key, value) -> DROP_TAG_BY_GROUP.put(Integer.parseInt(key), (String)value));
            }
            if (config.byInvestigationId != null) {
                config.byInvestigationId.forEach((key, value) -> DROP_TAG_BY_INVESTIGATION.put(Integer.parseInt(key), (String)value));
            }
        }
        catch (IOException e) {
            Grasscutter.getLogger().warn("boss-drop-tags.json not found; using built-in overrides only.");
        }
        DROP_TAG_BY_GROUP.putIfAbsent(133003543, "\u6025\u51bb\u6811");
        DROP_TAG_BY_GROUP.putIfAbsent(133515097, "\u6df1\u9083\u9b79\u8bed\u4e4b\u4e3b");
        DROP_TAG_BY_GROUP.putIfAbsent(133705121, "\u94c1\u7532\u7194\u706b\u5e1d\u7687");
        DROP_TAG_BY_GROUP.putIfAbsent(133711054, "\u971c\u591c\u7075\u55e3");
        DROP_TAG_BY_INVESTIGATION.putIfAbsent(8, "\u6025\u51bb\u6811");
        DROP_TAG_BY_INVESTIGATION.putIfAbsent(79, "\u6df1\u9083\u9b79\u8bed\u4e4b\u4e3b");
        DROP_TAG_BY_INVESTIGATION.putIfAbsent(98, "\u94c1\u7532\u7194\u706b\u5e1d\u7687");
        DROP_TAG_BY_INVESTIGATION.putIfAbsent(97, "\u971c\u591c\u7075\u55e3");
    }

    private static final class TagConfig {
        private Map<String, String> byGroupId;
        private Map<String, String> byInvestigationId;

        private TagConfig() {
        }

        public Map<String, String> getByGroupId() {
            return this.byGroupId;
        }

        public Map<String, String> getByInvestigationId() {
            return this.byInvestigationId;
        }
    }
}

