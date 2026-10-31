package emu.grasscutter.game.managers.cooking;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.CookRecipeDataOuterClass;
import emu.grasscutter.net.proto.PlayerCookArgsReqOuterClass.PlayerCookArgsReq;
import emu.grasscutter.net.proto.PlayerCookReqOuterClass.PlayerCookReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;
import io.netty.util.internal.ThreadLocalRandom;
import java.util.*;

public class CookingManager extends BasePlayerManager {
    private static final int MANUAL_PERFECT_COOK_QUALITY = 3;
    private static Set<Integer> defaultUnlockedRecipies = new HashSet<>();

    public CookingManager(Player player) {
        super(player);
    }

    public static void initialize() {
        defaultUnlockedRecipies = new HashSet<>();
        for (var recipe : GameData.getCookRecipeDataMap().values()) {
            if (recipe.isDefaultUnlocked()) {
                defaultUnlockedRecipies.add(recipe.getId());
            }
        }
    }

    private static int firstSaneCount(int... candidates) {
        for (int c : candidates) {
            if (c >= 1 && c <= 99) return c;
        }
        return 1;
    }

    public boolean unlockRecipe(int id) {
        if (this.player.getUnlockedRecipies().containsKey(id)) {
            return false;
        }
        this.player.getUnlockedRecipies().put(id, 0);
        this.player.sendPacket(new PacketCookRecipeDataNotify(id));
        return true;
    }

    private double getSpecialtyChance(ItemData cookedItem) {
        return switch (cookedItem.getRankLevel()) {
            case 1 -> 0.25;
            case 2 -> 0.2;
            case 3 -> 0.15;
            default -> 0;
        };
    }

    public void handlePlayerCookReq(PlayerCookReq req) {
        int recipeId = req.getRecipeId();
        int count = firstSaneCount(req.getCookCount());
        int quality = req.getQteQuality();
        if (quality < 0 || quality > 3) {
            quality = 0;
        }
        int avatar = req.getAssistAvatar();
        Grasscutter.getLogger()
                .info(
                        "[Cook] uid={} recipe={} count={} quality={} avatar={}",
                        player.getUid(),
                        recipeId,
                        count,
                        quality,
                        avatar);

        var recipeData = GameData.getCookRecipeDataMap().get(recipeId);
        if (recipeData == null
                || recipeData.getInputVec() == null
                || recipeData.getInputVec().isEmpty()
                || recipeData.getQualityOutputVec() == null
                || recipeData.getQualityOutputVec().isEmpty()) {
            this.player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_FAIL));
            return;
        }

        int proficiency = this.player.getUnlockedRecipies().getOrDefault(recipeId, 0);

        boolean success =
                player.getInventory().payItems(recipeData.getInputVec(), count, ActionReason.Cook);
        if (!success) {
            this.player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_FAIL));
            return;
        }

        var outputs = recipeData.getQualityOutputVec();
        // quality 1/2/3 → index 0/1/2; quality 0 (unset) → best available (usually delicious)
        int qualityIndex;
        if (quality >= 1 && quality <= outputs.size()) {
            qualityIndex = quality - 1;
        } else {
            qualityIndex = Math.min(2, outputs.size() - 1);
        }
        ItemParamData resultParam = outputs.get(qualityIndex);
        if (resultParam.getId() <= 0) {
            // fall back to first valid quality entry
            resultParam =
                    outputs.stream().filter(p -> p.getId() > 0).findFirst().orElse(resultParam);
        }
        ItemData resultItemData = GameData.getItemDataMap().get(resultParam.getId());
        if (resultItemData == null) {
            this.player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_FAIL));
            return;
        }

        int specialtyCount = 0;
        double specialtyChance = this.getSpecialtyChance(resultItemData);
        var bonusData = GameData.getCookBonusDataMap().get(avatar);
        if (bonusData != null && recipeId == bonusData.getRecipeId()) {
            for (int i = 0; i < count; i++) {
                if (ThreadLocalRandom.current().nextDouble() <= specialtyChance) {
                    specialtyCount++;
                }
            }
        }

        List<GameItem> cookResults = new ArrayList<>();
        int normalCount = count - specialtyCount;
        if (normalCount > 0) {
            GameItem cookResultNormal =
                    new GameItem(resultItemData, resultParam.getCount() * normalCount);
            cookResults.add(cookResultNormal);
            this.player.getInventory().addItem(cookResultNormal, ActionReason.Cook);
        }

        if (specialtyCount > 0 && bonusData != null) {
            ItemData specialtyItemData =
                    GameData.getItemDataMap().get(bonusData.getReplacementItemId());
            if (specialtyItemData != null) {
                GameItem cookResultSpecialty =
                        new GameItem(specialtyItemData, resultParam.getCount() * specialtyCount);
                cookResults.add(cookResultSpecialty);
                this.player.getInventory().addItem(cookResultSpecialty, ActionReason.Cook);
            }
        }

        // quality 0 is treated as perfect for proficiency; also accept explicit perfect (3)
        if (quality == 0 || quality == MANUAL_PERFECT_COOK_QUALITY) {
            proficiency = Math.min(proficiency + count, recipeData.getMaxProficiency());
            this.player.getUnlockedRecipies().put(recipeId, proficiency);
        }

        // Client expects a non-zero qte quality in the result UI
        int rspQuality = quality == 0 ? MANUAL_PERFECT_COOK_QUALITY : quality;
        this.player.sendPacket(
                new PacketPlayerCookRsp(cookResults, rspQuality, count, recipeId, proficiency));
    }

    public void handleCookArgsReq(PlayerCookArgsReq req) {
        this.player.sendPacket(new PacketPlayerCookArgsRsp());
    }

    private void addDefaultUnlocked() {
        if (defaultUnlockedRecipies == null || defaultUnlockedRecipies.isEmpty()) {
            initialize();
        }
        var unlockedRecipies = this.player.getUnlockedRecipies();
        var additionalRecipies = new HashSet<>(defaultUnlockedRecipies);
        additionalRecipies.removeAll(unlockedRecipies.keySet());
        for (int id : additionalRecipies) {
            unlockedRecipies.put(id, 0);
        }
    }

    public void sendCookDataNotify() {
        this.addDefaultUnlocked();
        var unlockedRecipes = this.player.getUnlockedRecipies();
        List<CookRecipeDataOuterClass.CookRecipeData> data = new ArrayList<>();
        unlockedRecipes.forEach(
                (recipeId, proficiency) ->
                        data.add(
                                CookRecipeDataOuterClass.CookRecipeData.newBuilder()
                                        .setRecipeId(recipeId)
                                        .setProficiency(proficiency)
                                        .build()));
        this.player.sendPacket(new PacketCookDataNotify(data));
    }
}
