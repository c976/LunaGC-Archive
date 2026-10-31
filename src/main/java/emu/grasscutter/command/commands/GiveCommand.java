package emu.grasscutter.command.commands;

import static emu.grasscutter.GameConstants.ILLEGAL_ITEMS;
import static emu.grasscutter.GameConstants.ILLEGAL_RELICS;
import static emu.grasscutter.GameConstants.ILLEGAL_WEAPONS;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.GameDepot;
import emu.grasscutter.data.NameIndex;
import emu.grasscutter.data.GenshinResourceIds;
import emu.grasscutter.data.ResourceId;
import emu.grasscutter.data.ResourceRegistry;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.data.excels.reliquary.ReliquaryAffixData;
import emu.grasscutter.data.excels.reliquary.ReliquaryMainPropData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.server.packet.send.PacketAvatarPropNotify;
import emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        label = "give",
        aliases = {"g"},
        permission = "player.give",
        permissionTargeted = "player.give.others",
        threading = true)
public final class GiveCommand implements CommandHandler {
    private static final int DEFAULT_LEVEL = 100;
    private static final int MAX_LEVEL = 100;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "give")
    private final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<id|name|all|weapons|mats|avatars>")
        private String target;

        @Option(names = {"-n", "--amount"}, defaultValue = "1", paramLabel = "<count>")
        private int amount;

        @Option(names = {"-l", "--level"}, paramLabel = "<level>")
        private Integer level;

        @Option(names = {"-r", "--refinement"}, defaultValue = "1", paramLabel = "<1-5>")
        private int refinement;

        @Option(names = {"-c", "--constellation"}, paramLabel = "<0-6>")
        private Integer constellation;

        @Option(names = "--skill-level", paramLabel = "<level>")
        private Integer skillLevel;

        @Option(names = "--main-stat", paramLabel = "<id|stat>")
        private String mainStat;

        @Option(names = "--substat", paramLabel = "<id|stat[_tier][,rolls]>")
        private List<String> substats = new ArrayList<>();

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (amount < 1) {
                CommandOutput.sendTranslatedMessage(sender, "commands.generic.invalid.amount");
                return;
            }
            refinement = Math.max(1, Math.min(5, refinement));
            if (constellation != null && (constellation < 0 || constellation > 6)) {
                CommandOutput.sendTranslatedMessage(sender, "commands.setConst.range_error");
                return;
            }

            GiveItemParameters param = new GiveItemParameters();
            param.amount = amount;
            param.refinement = refinement;
            param.lvl = level == null ? DEFAULT_LEVEL : Math.max(1, Math.min(MAX_LEVEL, level));
            param.lvlGiven = level != null;
            param.constellation = constellation == null ? -1 : constellation;
            param.constellationGiven = constellation != null;
            param.skillLevel = skillLevel == null ? 1 : skillLevel;
            param.skillLevelGiven = skillLevel != null;

            switch (target.toLowerCase(Locale.ROOT)) {
                case "all" -> {
                    param.constellation = constellation == null ? 6 : constellation;
                    giveAll(targetPlayer, param);
                    successAll(sender);
                    return;
                }
                case "weapons" -> {
                    giveAllWeapons(targetPlayer, param);
                    successAll(sender);
                    return;
                }
                case "mats", "materials" -> {
                    giveAllMats(targetPlayer, param);
                    successAll(sender);
                    return;
                }
                case "avatars", "characters" -> {
                    param.constellation = constellation == null ? 6 : constellation;
                    giveAllAvatars(targetPlayer, param);
                    successAll(sender);
                    return;
                }
                default -> {
                    if (!resolveTarget(sender, target, param)) return;
                }
            }

            if (param.recipeId > 0) {
                if (amount != 1) {
                    CommandOutput.sendMessage(sender, "A cooking recipe is unlocked once; --amount must be 1.");
                    return;
                }
                if (!targetPlayer.getCookingManager().unlockRecipe(param.recipeId)) {
                    CommandOutput.sendMessage(sender, "Cooking recipe already unlocked: " + target);
                    return;
                }
                CommandOutput.sendTranslatedMessage(
                        sender, "commands.give.given", 1, target, targetPlayer.getUid());
                return;
            }

            if (param.avatarData != null) {
                if (target.startsWith("genshin.avatar.") && amount != 1) {
                    CommandOutput.sendMessage(sender, "An avatar is granted once; --amount must be 1.");
                    return;
                }
                Avatar owned = targetPlayer.getAvatars().getAvatarById(param.avatarData.getId());
                if (owned != null) updateAvatar(targetPlayer, owned, param);
                else targetPlayer.addAvatar(makeAvatar(param));
                CommandOutput.sendTranslatedMessage(
                        sender,
                        "commands.give.given_avatar",
                        NameIndex.describe(param.id),
                        param.lvl,
                        targetPlayer.getUid());
                return;
            }

            if (param.data == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.generic.invalid.itemId");
                return;
            }

            if (param.data.getItemType() == ItemType.ITEM_RELIQUARY) {
                configureArtifact(sender, param, mainStat, substats);
                if (!param.setPieces.isEmpty()) {
                    giveWholeSet(sender, targetPlayer, param);
                    return;
                }
                targetPlayer.getInventory().addItems(makeArtifacts(param), ActionReason.SubfieldDrop);
                CommandOutput.sendTranslatedMessage(
                        sender,
                        "commands.give.given_level",
                        NameIndex.describe(param.id),
                        param.lvl,
                        param.amount,
                        targetPlayer.getUid());
                return;
            }

            if (param.data.getItemType() == ItemType.ITEM_WEAPON) {
                targetPlayer.getInventory().addItems(makeUnstackableItems(param), ActionReason.SubfieldDrop);
                CommandOutput.sendTranslatedMessage(
                        sender,
                        "commands.give.given_with_level_and_refinement",
                        NameIndex.describe(param.id),
                        param.lvl,
                        param.refinement,
                        param.amount,
                        targetPlayer.getUid());
                return;
            }

            targetPlayer.getInventory().addItem(
                    new GameItem(param.data, param.amount), ActionReason.SubfieldDrop);
            CommandOutput.sendTranslatedMessage(
                    sender,
                    "commands.give.given",
                    param.amount,
                    NameIndex.describe(param.id),
                    targetPlayer.getUid());
        }
    }

    private static boolean resolveTarget(
            Player sender, String input, GiveItemParameters param) {
        if (input.startsWith("genshin.")) {
            try {
                ResourceId resource = ResourceId.parse(input);
                String resourceType = resource.size() > 1 ? resource.segment(1) : "";
                // Artifact-set, avatar and cooking-definition identifiers are separate
                // command domains. A fully specified inventory item always uses the
                // exact same dictionary as LootCSS and loot.yaml.
                boolean artifactSet = "artifact".equals(resourceType) && resource.size() == 3;
                boolean cookingDefinition = "recipe".equals(resourceType)
                        && resource.size() == 3;
                int nativeId = artifactSet || cookingDefinition || "avatar".equals(resourceType)
                        ? GenshinResourceIds.current().resolve(input)
                        : ResourceRegistry.current().resolve(input);
                if (artifactSet) {
                    param.setPieces = NameIndex.bestPiecesForSet(nativeId);
                    param.id = param.setPieces.get(0);
                } else if (cookingDefinition) {
                    param.recipeId = nativeId;
                    return true;
                } else {
                    param.id = nativeId;
                }
            } catch (IllegalArgumentException e) {
                CommandOutput.sendMessage(sender, "Invalid resource: " + e.getMessage());
                return false;
            }
        } else {
            try {
                param.id = Integer.parseInt(input);
            } catch (NumberFormatException ignored) {
                var rest = new ArrayList<String>();
                param.setPieces = NameIndex.resolveRelicSet(input, rest);
                if (!param.setPieces.isEmpty()) param.id = param.setPieces.get(0);
                else param.id = NameIndex.resolveRelic(input, rest);
                if (param.id == 0) param.id = NameIndex.resolve(input, rest);
                if (param.id == 0) {
                    CommandOutput.sendTranslatedMessage(sender, "commands.generic.invalid.itemId");
                    return false;
                }
            }
        }

        param.data = GameData.getItemDataMap().get(param.id);
        if (param.id > 10_000_000 && param.id < 12_000_000) {
            param.avatarData = GameData.getAvatarDataMap().get(param.id);
        } else if (param.id > 1000 && param.id < 1100) {
            param.avatarData = GameData.getAvatarDataMap().get(param.id - 1000 + 10_000_000);
        }

        if (param.data != null && param.data.getItemType() == ItemType.ITEM_RELIQUARY) {
            int requested = param.lvlGiven ? param.lvl : 0;
            param.lvl = Math.max(0, Math.min(20, requested)) + 1;
            if (ILLEGAL_RELICS.contains(param.id)) {
                CommandOutput.sendTranslatedMessage(sender, "commands.give.illegal_relic");
            }
        }
        return true;
    }

    private static void configureArtifact(
            Player sender, GiveItemParameters param, String mainStat, List<String> substats) {
        try {
            if (mainStat != null) {
                try {
                    param.mainPropId = Integer.parseInt(mainStat);
                } catch (NumberFormatException ignored) {
                    param.mainPropId =
                            getArtifactMainProp(
                                    param.data, FightProperty.getPropByShortName(mainStat));
                }
            }
            if (substats != null && !substats.isEmpty()) {
                param.appendPropIdList = new ArrayList<>();
                for (String raw : substats) {
                    String[] rollParts = raw.split(",", 2);
                    int rolls = rollParts.length == 2 ? Math.min(Integer.parseInt(rollParts[1]), 200) : 1;
                    int appendId = getAppendPropId(rollParts[0], param.data);
                    for (int i = 0; i < rolls; i++) param.appendPropIdList.add(appendId);
                }
            }
        } catch (IllegalArgumentException exception) {
            CommandOutput.sendTranslatedMessage(sender, "commands.execution.argument_error");
            throw exception;
        }
    }

    private static Avatar makeAvatar(GiveItemParameters param) {
        int constellation = param.constellation < 0 ? 0 : param.constellation;
        Avatar avatar = new Avatar(param.avatarData);
        avatar.setLevel(param.lvl);
        avatar.setPromoteLevel(Avatar.getMinPromoteLevel(param.lvl));
        avatar.getSkillDepot()
                .getSkillsAndEnergySkill()
                .forEach(id -> avatar.setSkillLevel(id, param.skillLevel));
        avatar.forceConstellationLevel(constellation);
        avatar.recalcStats(true);
        avatar.save();
        return avatar;
    }

    private static void updateAvatar(Player player, Avatar avatar, GiveItemParameters param) {
        if (param.lvlGiven) {
            avatar.setLevel(param.lvl);
            avatar.setPromoteLevel(Avatar.getMinPromoteLevel(param.lvl));
        }
        if (param.skillLevelGiven) {
            avatar.getSkillDepot()
                    .getSkillsAndEnergySkill()
                    .forEach(id -> avatar.setSkillLevel(id, param.skillLevel));
        }
        boolean lowered = false;
        if (param.constellationGiven) {
            lowered = param.constellation < avatar.getCoreProudSkillLevel();
            avatar.forceConstellationLevel(param.constellation);
            avatar.recalcConstellations();
        }
        avatar.recalcStats(true);
        avatar.save();
        player.sendPacket(new PacketAvatarPropNotify(avatar));
        if (lowered) {
            World world = player.getWorld();
            Scene scene = player.getScene();
            Position pos = player.getPosition();
            world.transferPlayerToScene(player, 1, pos);
            world.transferPlayerToScene(player, scene.getId(), pos);
            scene.broadcastPacket(new PacketSceneEntityAppearNotify(player));
        }
    }

    private static void giveAllAvatars(Player player, GiveItemParameters param) {
        int promoteLevel = Avatar.getMinPromoteLevel(param.lvl);
        int constellation = param.constellation < 0 ? 6 : param.constellation;
        for (AvatarData data : GameData.getAvatarDataMap().values()) {
            int id = data.getId();
            if (id < 10000002 || id >= 10000901 || "AVATAR_TEST".equals(data.getUseType())) continue;
            Avatar owned = player.getAvatars().getAvatarById(id);
            if (owned != null) {
                GiveItemParameters copy = param.copy();
                copy.constellation = constellation;
                copy.constellationGiven = true;
                copy.lvlGiven = true;
                copy.skillLevelGiven = true;
                updateAvatar(player, owned, copy);
            } else {
                Avatar avatar = new Avatar(data);
                avatar.setLevel(param.lvl);
                avatar.setPromoteLevel(promoteLevel);
                avatar.getSkillDepot()
                        .getSkillsAndEnergySkill()
                        .forEach(skill -> avatar.setSkillLevel(skill, param.skillLevel));
                avatar.forceConstellationLevel(constellation);
                avatar.recalcStats(true);
                player.addAvatar(avatar, false);
            }
        }
    }

    private static List<GameItem> makeUnstackableItems(GiveItemParameters param) {
        int promoteLevel = GameItem.getMinPromoteLevel(param.lvl);
        int totalExp = 0;
        if (param.data.getItemType() == ItemType.ITEM_WEAPON) {
            int rankLevel = param.data.getRankLevel();
            for (int i = 1; i < param.lvl; i++) totalExp += GameData.getWeaponExpRequired(rankLevel, i);
        }
        List<GameItem> items = new ArrayList<>(param.amount);
        for (int i = 0; i < param.amount; i++) {
            GameItem item = new GameItem(param.data);
            item.setLevel(param.lvl);
            if (item.getItemType() == ItemType.ITEM_WEAPON) {
                item.setPromoteLevel(promoteLevel);
                item.setTotalExp(totalExp);
                item.setRefinement(param.refinement - 1);
            }
            items.add(item);
        }
        return items;
    }

    private static List<GameItem> makeArtifacts(GiveItemParameters param) {
        param.lvl = Math.min(param.lvl, param.data.getMaxLevel());
        int rank = param.data.getRankLevel();
        int totalExp = 0;
        for (int i = 1; i < param.lvl; i++) totalExp += GameData.getRelicExpRequired(rank, i);
        List<GameItem> items = new ArrayList<>(param.amount);
        for (int i = 0; i < param.amount; i++) {
            GameItem item = new GameItem(param.data);
            item.setLevel(param.lvl);
            item.setTotalExp(totalExp);
            int affixCount = param.data.getAppendPropNum() + (param.lvl - 1) / 4;
            if (param.mainPropId > 0) item.setMainPropId(param.mainPropId);
            if (param.appendPropIdList != null) {
                item.getAppendPropIdList().clear();
                item.getAppendPropIdList().addAll(param.appendPropIdList);
            }
            item.addAppendProps(affixCount - item.getAppendPropIdList().size());
            items.add(item);
        }
        return items;
    }

    private static int getArtifactMainProp(ItemData itemData, FightProperty prop) {
        if (prop != FightProperty.FIGHT_PROP_NONE) {
            for (ReliquaryMainPropData data : GameDepot.getRelicMainPropList(itemData.getMainPropDepotId())) {
                if (data.getWeight() > 0 && data.getFightProp() == prop) return data.getId();
            }
        }
        throw new IllegalArgumentException();
    }

    private static List<Integer> getArtifactAffixes(ItemData itemData, FightProperty prop) {
        if (prop == FightProperty.FIGHT_PROP_NONE) throw new IllegalArgumentException();
        List<Integer> affixes = new ArrayList<>();
        for (ReliquaryAffixData data : GameDepot.getRelicAffixList(itemData.getAppendPropDepotId())) {
            if (data.getWeight() > 0 && data.getFightProp() == prop) affixes.add(data.getId());
        }
        return affixes;
    }

    private static int getAppendPropId(String text, ItemData itemData) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            String[] parts = text.split("_");
            int tier = parts.length > 1 ? Integer.parseInt(parts[1]) : 4;
            List<Integer> substats =
                    getArtifactAffixes(itemData, FightProperty.getPropByShortName(parts[0]));
            if (substats.isEmpty()) throw new IllegalArgumentException();
            int index = Math.min(Math.max(0, tier - 1), substats.size() - 1);
            return substats.get(index);
        }
    }

    private static void addItemsChunked(Player player, List<GameItem> items, int packetSize) {
        for (int i = 0; i < items.size(); i += packetSize) {
            player.getInventory().addItems(items.subList(i, Math.min(i + packetSize, items.size())));
        }
    }

    private static void giveAllMats(Player player, GiveItemParameters param) {
        List<GameItem> items = new ArrayList<>();
        for (ItemData data : GameData.getItemDataMap().values()) {
            int id = data.getId();
            if (id < 100_000 || ILLEGAL_ITEMS.contains(id) || data.isEquip()) continue;
            GameItem item = new GameItem(data);
            item.setCount(param.amount);
            items.add(item);
        }
        addItemsChunked(player, items, 100);
    }

    private static void giveAllWeapons(Player player, GiveItemParameters param) {
        int promoteLevel = GameItem.getMinPromoteLevel(param.lvl);
        int quantity = Math.min(param.amount, 5);
        List<GameItem> items = new ArrayList<>();
        for (ItemData data : GameData.getItemDataMap().values()) {
            int id = data.getId();
            if (id < 11100 || id > 16000 || ILLEGAL_WEAPONS.contains(id)) continue;
            if (!data.isEquip() || data.getItemType() != ItemType.ITEM_WEAPON) continue;
            for (int i = 0; i < quantity; i++) {
                GameItem item = new GameItem(data);
                item.setLevel(param.lvl);
                item.setPromoteLevel(promoteLevel);
                item.setRefinement(param.refinement - 1);
                items.add(item);
            }
        }
        addItemsChunked(player, items, 100);
    }

    private static void giveAll(Player player, GiveItemParameters param) {
        giveAllAvatars(player, param);
        giveAllMats(player, param);
        giveAllWeapons(player, param);
    }

    private static void giveWholeSet(Player sender, Player targetPlayer, GiveItemParameters param) {
        param.mainPropId = -1;
        param.appendPropIdList = null;
        int given = 0;
        for (int piece : param.setPieces) {
            ItemData data = GameData.getItemDataMap().get(piece);
            if (data == null) continue;
            param.data = data;
            param.id = piece;
            targetPlayer.getInventory().addItems(makeArtifacts(param), ActionReason.SubfieldDrop);
            given++;
        }
        CommandOutput.sendTranslatedMessage(
                sender, "commands.give.given", given, "artifacts of the set", targetPlayer.getUid());
    }

    private static void successAll(Player sender) {
        CommandOutput.sendTranslatedMessage(sender, "commands.give.giveall_success");
    }

    private static final class GiveItemParameters {
        private int id;
        private int lvl;
        private int amount = 1;
        private int refinement = 1;
        private int constellation = -1;
        private int skillLevel = 1;
        private int mainPropId = -1;
        private List<Integer> appendPropIdList;
        private List<Integer> setPieces = List.of();
        private ItemData data;
        private AvatarData avatarData;
        private int recipeId;
        private boolean lvlGiven;
        private boolean constellationGiven;
        private boolean skillLevelGiven;

        private GiveItemParameters copy() {
            GiveItemParameters copy = new GiveItemParameters();
            copy.id = id;
            copy.lvl = lvl;
            copy.amount = amount;
            copy.refinement = refinement;
            copy.constellation = constellation;
            copy.skillLevel = skillLevel;
            copy.mainPropId = mainPropId;
            copy.appendPropIdList = appendPropIdList;
            copy.setPieces = setPieces;
            copy.data = data;
            copy.avatarData = avatarData;
            copy.lvlGiven = lvlGiven;
            copy.constellationGiven = constellationGiven;
            copy.skillLevelGiven = skillLevelGiven;
            return copy;
        }
    }
}
