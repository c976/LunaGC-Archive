package emu.grasscutter.command.commands;

import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.avatar.AvatarExtraLevelHelper;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.server.packet.send.PacketAvatarFetterDataNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketAvatarPropNotify;
import emu.grasscutter.server.packet.send.PacketProudSkillChangeNotify;
import emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify;
import emu.grasscutter.server.packet.send.PacketStoreItemChangeNotify;
import emu.grasscutter.utils.lang.Language;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/** Unified player-owned character operations. The player target is selected by CommandMap. */
@Command(label = "avatar", targetRequirement = Command.TargetRequirement.ONLINE)
public final class AvatarCommand implements CommandHandler {
    private static final int MAX_AVATAR_LEVEL = 90;
    private static final int MAX_CONSTELLATION = 6;
    private static final int MAX_FETTER_LEVEL = 10;
    private static final int MAX_REFINEMENT = 4;
    private static final int DEFAULT_MAX_TALENT = 10;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var root = new CommandLine(new Root(sender));
        root.addSubcommand("list", new ListAvatars(sender, targetPlayer));
        root.addSubcommand("stat", AvatarStatCommand.create(sender, targetPlayer));

        var constellation = new CommandLine(new Help(sender));
        constellation.getCommandSpec().name("constellation");
        constellation.addSubcommand("set", new ConstellationSet(sender, targetPlayer));
        constellation.addSubcommand("reset", new ConstellationReset(sender, targetPlayer));
        root.addSubcommand("constellation", constellation);

        var talent = new CommandLine(new Help(sender));
        talent.getCommandSpec().name("talent");
        talent.addSubcommand("set", new TalentSet(sender, targetPlayer));
        var normal = new CommandLine(new TalentSlot(sender, targetPlayer, Slot.NORMAL));
        var skill = new CommandLine(new TalentSlot(sender, targetPlayer, Slot.SKILL));
        var burst = new CommandLine(new TalentSlot(sender, targetPlayer, Slot.BURST));
        normal.getCommandSpec().name("normal");
        skill.getCommandSpec().name("skill");
        burst.getCommandSpec().name("burst");
        talent.addSubcommand("normal", normal, "n");
        talent.addSubcommand("skill", skill, "e");
        talent.addSubcommand("burst", burst, "q");
        talent.addSubcommand("all", new TalentAll(sender, targetPlayer));
        talent.addSubcommand("list", new TalentList(sender, targetPlayer));
        root.addSubcommand("talent", talent);

        var friendship = new CommandLine(new Help(sender));
        friendship.getCommandSpec().name("friendship");
        friendship.addSubcommand("set", new FriendshipSet(sender, targetPlayer));
        root.addSubcommand("friendship", friendship);

        root.addSubcommand("extralevel", new ExtraLevel(sender, targetPlayer));
        root.addSubcommand("max", new Max(sender, targetPlayer));
        return root;
    }

    /** Re-check targets and the original stat permission after nested-command routing. */
    static boolean permitted(Player sender, Player target, String permission) {
        if (target == null || !target.isOnline()
                || target.getSession() == null || !target.getSession().isActive()) {
            CommandOutput.sendMessage(sender, "Avatar operations require an online player.");
            return false;
        }
        if (sender == null) return true;
        var account = sender.getAccount();
        String required = target != sender ? permission + ".others" : permission;
        if (account != null && account.hasPermission(required)) return true;
        CommandOutput.sendTranslatedMessage(sender, "commands.generic.permission_error");
        return false;
    }

    static Avatar selectOwnedAvatar(Player sender, Player target, Integer avatarId) {
        if (avatarId != null) {
            if (avatarId <= 0) {
                CommandOutput.sendMessage(sender, "Avatar ID must be positive.");
                return null;
            }
            Avatar avatar = target.getAvatars().getAvatarById(avatarId);
            if (avatar == null) CommandOutput.sendMessage(sender, "Avatar not owned: " + avatarId);
            return avatar;
        }
        var active = target.getTeamManager().getCurrentAvatarEntity();
        if (active == null) {
            CommandOutput.sendMessage(sender, "No active character; specify --avatar <avatarId>.");
            return null;
        }
        return active.getAvatar();
    }

    @CommandLine.Command(name = "avatar")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            AvatarCommand.this.sendUsageMessage(sender);
        }
    }

    @CommandLine.Command(name = "group")
    private static final class Help implements Runnable {
        private final Player sender;

        @Spec private CommandSpec spec;

        private Help(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            CommandOutput.sendMessage(sender, spec.commandLine().getUsageMessage().stripTrailing());
        }
    }

    /** Select one owned character or the full owned character collection. */
    private static final class Selection {
        @Option(names = "--avatar", paramLabel = "<avatarId>", description = "Owned character ID")
        Integer id;

        @Option(names = "--all", description = "All owned characters")
        boolean all;
    }

    private abstract static class Action implements Runnable {
        protected final Player sender;
        protected final Player target;

        private Action(Player sender, Player target) {
            this.sender = sender;
            this.target = target;
        }

        protected boolean permitted(String permission) {
            return AvatarCommand.permitted(sender, target, permission);
        }

        protected Avatar select(Integer avatarId) {
            return selectOwnedAvatar(sender, target, avatarId);
        }

        protected AvatarSkillDepotData skillDepot(Avatar avatar) {
            AvatarSkillDepotData depot = avatar.getSkillDepot();
            if (depot == null) {
                CommandOutput.sendTranslatedMessage(sender, "commands.talent.invalid_skill_id");
            }
            return depot;
        }

        protected java.util.List<Avatar> selectMany(Selection selection) {
            if (selection != null && selection.all) {
                var all = new ArrayList<Avatar>();
                target.getAvatars().forEach(all::add);
                if (all.isEmpty()) CommandOutput.sendMessage(sender, "No owned characters.");
                return all;
            }
            Avatar selected = select(selection == null ? null : selection.id);
            return selected == null ? java.util.List.of() : java.util.List.of(selected);
        }
    }

    private abstract static class SingleAction extends Action {
        @Option(names = "--avatar", paramLabel = "<avatarId>")
        protected Integer avatarId;

        private SingleAction(Player sender, Player target) {
            super(sender, target);
        }
    }

    private abstract static class MultiAction extends Action {
        @ArgGroup(exclusive = true, multiplicity = "0..1")
        protected Selection selection;

        private MultiAction(Player sender, Player target) {
            super(sender, target);
        }
    }

    @CommandLine.Command(name = "list")
    private static final class ListAvatars extends Action {
        private ListAvatars(Player sender, Player target) {
            super(sender, target);
        }

        @Override
        public void run() {
            if (!permitted("player.give")) return;
            var owned = new ArrayList<Avatar>();
            target.getAvatars().forEach(owned::add);
            owned.sort(Comparator.comparingInt(Avatar::getAvatarId));
            CommandOutput.sendMessage(sender, "Owned avatars (" + owned.size() + "):");
            for (Avatar avatar : owned) {
                CommandOutput.sendMessage(sender,
                        avatar.getAvatarId() + " - " + avatar.getAvatarData().getName()
                                + " (level " + avatar.getLevel() + ")");
            }
        }
    }

    static boolean validConstellationLevel(int level) {
        return level >= 0 && level <= 6;
    }

    @CommandLine.Command(name = "set")
    private static final class ConstellationSet extends MultiAction {
        @Parameters(index = "0", paramLabel = "<0-6>")
        private int level;

        private ConstellationSet(Player sender, Player target) {
            super(sender, target);
        }

        @Override
        public void run() {
            if (!permitted("player.setconstellation")) return;
            if (!validConstellationLevel(level)) {
                CommandOutput.sendTranslatedMessage(sender, "commands.setConst.range_error");
                return;
            }
            List<Avatar> avatars = selectMany(selection);
            if (avatars.isEmpty()) return;
            boolean reload = false;
            for (Avatar avatar : avatars) {
                reload |= level < avatar.getCoreProudSkillLevel();
                avatar.forceConstellationLevel(level);
            }
            if (reload || selection != null && selection.all) reloadScene(target);
            if (selection != null && selection.all) {
                CommandOutput.sendTranslatedMessage(sender, "commands.setConst.successall", level);
            } else {
                CommandOutput.sendTranslatedMessage(sender, "commands.setConst.success",
                        avatars.get(0).getAvatarData().getName(), level);
            }
        }
    }

    @CommandLine.Command(name = "reset")
    private static final class ConstellationReset extends MultiAction {
        private ConstellationReset(Player sender, Player target) {
            super(sender, target);
        }

        @Override
        public void run() {
            if (!permitted("player.resetconstellation")) return;
            List<Avatar> avatars = selectMany(selection);
            if (avatars.isEmpty()) return;
            for (Avatar avatar : avatars) avatar.forceConstellationLevel(-1);
            reloadScene(target);
            if (selection != null && selection.all) {
                CommandOutput.sendTranslatedMessage(sender, "commands.resetConst.reset_all");
            } else {
                CommandOutput.sendTranslatedMessage(sender, "commands.resetConst.success",
                        avatars.get(0).getAvatarData().getName());
            }
        }
    }

    private static void reloadScene(Player player) {
        World world = player.getWorld();
        Scene scene = player.getScene();
        if (world == null || scene == null) return;
        Position pos = new Position(player.getPosition());
        int sceneId = scene.getId();
        world.transferPlayerToScene(player, 1, pos);
        world.transferPlayerToScene(player, sceneId, pos);
        player.getScene().broadcastPacket(new PacketSceneEntityAppearNotify(player));
    }

    private abstract static class TalentAction extends SingleAction {
        private TalentAction(Player sender, Player target) {
            super(sender, target);
        }
    }

    private abstract static class BatchTalentAction extends MultiAction {
        private BatchTalentAction(Player sender, Player target) {
            super(sender, target);
        }
    }

    @CommandLine.Command(name = "set")
    private static final class TalentSet extends TalentAction {
        @Parameters(index = "0", paramLabel = "<talentId>")
        private int skillId;

        @Parameters(index = "1", paramLabel = "<level>")
        private int level;

        private TalentSet(Player sender, Player target) {
            super(sender, target);
        }

        @Override
        public void run() {
            if (!permitted("player.settalent")) return;
            Avatar avatar = select(avatarId);
            if (avatar == null || skillDepot(avatar) == null) return;
            setTalentLevel(sender, avatar, skillId, level);
        }
    }

    private enum Slot { NORMAL, SKILL, BURST }

    @CommandLine.Command(name = "normal")
    private static final class TalentSlot extends BatchTalentAction {
        @Parameters(index = "0", paramLabel = "<level>")
        private int level;
        private final Slot slot;

        private TalentSlot(Player sender, Player target, Slot slot) {
            super(sender, target);
            this.slot = slot;
        }

        @Override
        public void run() {
            if (!permitted("player.settalent")) return;
            List<Avatar> avatars = selectMany(selection);
            for (Avatar avatar : avatars) {
                AvatarSkillDepotData depot = skillDepot(avatar);
                if (depot == null) continue;
                var skills = depot.getSkills();
                if (slot != Slot.BURST && (skills == null || skills.size() < 2)) {
                    CommandOutput.sendTranslatedMessage(sender, "commands.talent.invalid_skill_id");
                    continue;
                }
                int skillId = switch (slot) {
                    case NORMAL -> skills.get(0);
                    case SKILL -> skills.get(1);
                    case BURST -> depot.getEnergySkill();
                };
                setTalentLevel(sender, avatar, skillId, level);
            }
        }
    }

    @CommandLine.Command(name = "all")
    private static final class TalentAll extends BatchTalentAction {
        @Parameters(index = "0", paramLabel = "<level>")
        private int level;

        private TalentAll(Player sender, Player target) {
            super(sender, target);
        }

        @Override
        public void run() {
            if (!permitted("player.settalent")) return;
            if (level < 1 || level > 15) {
                CommandOutput.sendTranslatedMessage(sender, "commands.talent.out_of_range");
                return;
            }
            List<Avatar> avatars = selectMany(selection);
            for (Avatar avatar : avatars) {
                AvatarSkillDepotData depot = skillDepot(avatar);
                if (depot == null) continue;
                depot.getSkillsAndEnergySkill().forEach(id -> setTalentLevel(sender, avatar, id, level));
            }
        }
    }

    @CommandLine.Command(name = "list")
    private static final class TalentList extends TalentAction {
        private TalentList(Player sender, Player target) {
            super(sender, target);
        }

        @Override
        public void run() {
            if (!permitted("player.settalent")) return;
            Avatar avatar = select(avatarId);
            if (avatar == null) return;
            AvatarSkillDepotData depot = skillDepot(avatar);
            if (depot == null) return;
            var skillMap = GameData.getAvatarSkillDataMap();
            depot.getSkillsAndEnergySkill().forEach(id -> {
                var skill = skillMap.get(id);
                if (skill == null) return;
                Object name = Language.getTextMapKey(skill.getNameTextMapHash());
                Object desc = Language.getTextMapKey(skill.getDescTextMapHash());
                if (name == null) name = id;
                if (desc == null) desc = "";
                CommandOutput.sendTranslatedMessage(sender, "commands.talent.id_desc", id, name, desc);
            });
        }
    }

    private static void setTalentLevel(Player sender, Avatar avatar, int skillId, int level) {
        if (avatar.setSkillLevel(skillId, level)) {
            var skill = GameData.getAvatarSkillDataMap().get(skillId);
            Object name = skill != null ? Language.getTextMapKey(skill.getNameTextMapHash()) : null;
            if (name == null) name = skillId;
            CommandOutput.sendTranslatedMessage(sender, "commands.talent.set_id", skillId, name, level);
        } else {
            CommandOutput.sendTranslatedMessage(sender, "commands.talent.out_of_range");
        }
    }

    @CommandLine.Command(name = "set")
    private static final class FriendshipSet extends MultiAction {
        @Parameters(index = "0", paramLabel = "<level>")
        private int level;

        private FriendshipSet(Player sender, Player target) {
            super(sender, target);
        }

        @Override
        public void run() {
            if (!permitted("player.setfetterlevel")) return;
            if (level < 0 || level > 10) {
                CommandOutput.sendMessage(sender, translate(sender, "commands.setFetterLevel.range_error"));
                return;
            }
            List<Avatar> avatars = selectMany(selection);
            for (Avatar avatar : avatars) {
                avatar.setFetterLevel(level);
                var levelData = GameData.getAvatarFetterLevelDataMap().get(level);
                if (level != 10 && levelData != null) {
                    avatar.setFetterExp(levelData.getExp());
                }
                avatar.save();
                target.sendPacket(new PacketAvatarFetterDataNotify(avatar));
            }
            if (!avatars.isEmpty()) {
                CommandOutput.sendMessage(sender,
                        "Set friendship level " + level + " for " + avatars.size() + " character(s).");
            }
        }
    }

    @CommandLine.Command(name = "extralevel")
    private static final class ExtraLevel extends SingleAction {
        private ExtraLevel(Player sender, Player target) {
            super(sender, target);
        }

        @Override
        public void run() {
            if (!permitted("player.give")) return;
            Avatar avatar = select(avatarId);
            if (avatar == null) return;
            int previousLevel = avatar.getLevel();
            if (AvatarExtraLevelHelper.upgradeAvatar(target, avatar)) {
                CommandOutput.sendMessage(sender, "Extra level OK: avatar " + avatar.getAvatarId()
                        + " " + previousLevel + " -> " + avatar.getLevel() + " (cost 104300)");
            } else {
                CommandOutput.sendMessage(sender,
                        "Extra level failed: need promote=6 and level 90 or 95, plus enough 104300. Now level="
                                + avatar.getLevel() + " promote=" + avatar.getPromoteLevel());
            }
        }
    }

    @CommandLine.Command(name = "max")
    private final class Max extends MultiAction {
        private Max(Player sender, Player target) {
            super(sender, target);
        }

        @Override
        public void run() {
            if (!permitted("player.max")) return;
            List<Avatar> avatars = selectMany(selection);
            if (avatars.isEmpty()) return;
            for (Avatar avatar : avatars) maxAvatar(target, avatar);
            healActiveTeam(target);
            if (selection != null && selection.all) {
                CommandOutput.sendMessage(sender, "Maxed out " + avatars.size() + " characters.");
            } else {
                CommandOutput.sendMessage(sender,
                        "Maxed out " + avatars.get(0).getAvatarData().getName() + ".");
            }
        }
    }

    private void maxAvatar(Player player, Avatar avatar) {
        avatar.setLevel(MAX_AVATAR_LEVEL);
        avatar.setPromoteLevel(Avatar.getMinPromoteLevel(MAX_AVATAR_LEVEL));

        var depot = avatar.getSkillDepot();
        if (depot != null) {
            depot.getSkillsAndEnergySkill()
                    .forEach(
                            id -> {
                                var levels = GameData.getAvatarSkillLevels(id);
                                int max =
                                        levels == null || levels.isEmpty()
                                                ? DEFAULT_MAX_TALENT
                                                : levels.intStream().max().orElse(DEFAULT_MAX_TALENT);
                                avatar.setSkillLevel(id, max);
                            });
        }

        avatar.forceConstellationLevel(MAX_CONSTELLATION);
        avatar.recalcConstellations();
        avatar.setFetterLevel(MAX_FETTER_LEVEL);
        maxWeapon(player, avatar);
        avatar.recalcStats(true);
        avatar.save();

        player.sendPacket(new PacketAvatarPropNotify(avatar));
        player.sendPacket(new PacketProudSkillChangeNotify(avatar));
        player.sendPacket(new PacketAvatarFetterDataNotify(avatar));
    }

    private void maxWeapon(Player player, Avatar avatar) {
        GameItem weapon = avatar.getWeapon();
        if (weapon == null || weapon.getItemData() == null) {
            return;
        }

        int promoteId = weapon.getItemData().getWeaponPromoteId();
        int maxPromoteLevel = weapon.getPromoteLevel();
        int maxLevel = weapon.getLevel();
        for (int promote = 0; ; promote++) {
            var data = GameData.getWeaponPromoteData(promoteId, promote);
            if (data == null) {
                break;
            }
            maxPromoteLevel = promote;
            maxLevel = Math.max(maxLevel, data.getUnlockMaxLevel());
        }

        weapon.setPromoteLevel(maxPromoteLevel);
        weapon.setLevel(maxLevel);
        var affixes = weapon.getItemData().getSkillAffix();
        if (affixes != null && affixes.length > 0 && affixes[0] != 0) {
            weapon.setRefinement(MAX_REFINEMENT);
        }

        weapon.save();
        player.sendPacket(new PacketStoreItemChangeNotify(weapon));
    }

    private void healActiveTeam(Player player) {
        player.getTeamManager()
                .getActiveTeam()
                .forEach(
                        entity -> {
                            if (entity.isAlive()) {
                                entity.setFightProperty(
                                        FightProperty.FIGHT_PROP_CUR_HP,
                                        entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
                                entity.getWorld()
                                        .broadcastPacket(
                                                new PacketAvatarFightPropUpdateNotify(
                                                        entity.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
                            } else {
                                entity.reviveToRatio(1f);
                            }
                        });
    }
}
