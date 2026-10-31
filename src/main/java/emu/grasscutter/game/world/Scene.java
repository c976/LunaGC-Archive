package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.GameDepot;
import emu.grasscutter.data.binout.SceneNpcBornEntry;
import emu.grasscutter.data.binout.routes.Route;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.codex.CodexAnimalData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.excels.world.WorldLevelData;
import emu.grasscutter.data.server.Grid;
import emu.grasscutter.game.ability.ArlecchinoBoLSync;
import emu.grasscutter.game.ability.ArlecchinoBoLUtil;
import emu.grasscutter.game.ability.ArlecchinoBurstBoL;
import emu.grasscutter.game.ability.PartyReviveHelper;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.dungeons.DungeonSettleListener;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.enums.DungeonPassConditionType;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.managers.blossom.BlossomManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamInfo;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.QuestGroupSuite;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.ChangeHpDebtsReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.SceneIndexManager;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.SceneBlock;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.event.entity.EntityCreationEvent;
import emu.grasscutter.server.event.player.PlayerTeleportEvent;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.server.scheduler.ServerTaskScheduler;
import emu.grasscutter.utils.algorithms.KahnsSort;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.*;

import static emu.grasscutter.GameConstants.ENTITY_ID_BIT_SHIFT;

public class Scene {
    @Getter private final World world;
    @Getter private final SceneData sceneData;
    @Getter private final List<Player> players;
    @Getter private final Map<Integer, GameEntity> entities;
    @Getter private final Map<Integer, GameEntity> weaponEntities;
    @Getter private final Set<SpawnDataEntry> spawnedEntities;
    @Getter private final Set<SpawnDataEntry> deadSpawnedEntities;
    @Getter private final Set<SceneBlock> loadedBlocks;
    @Getter private final Set<SceneGroup> loadedGroups;
    @Getter @Setter private transient emu.grasscutter.game.dailytask.DailyTaskGroupLifecycle.State dailyTaskGroups;
    @Getter private final BlossomManager blossomManager;
    private final HashSet<Integer> unlockedForces;
    private final long startWorldTime;
    @Getter @Setter DungeonManager dungeonManager;
    @Getter Int2ObjectMap<Route> sceneRoutes;
    private Set<SpawnDataEntry.GridBlockId> loadedGridBlocks;
    private int loadedBlossomRevision;
    @Getter @Setter private boolean dontDestroyWhenEmpty;
    @Getter private final SceneScriptManager scriptManager;
    @Getter @Setter private WorldChallenge challenge;
    @Getter private List<DungeonSettleListener> dungeonSettleListeners;
    @Getter @Setter private int prevScene;
    @Getter @Setter private int prevScenePoint;
    @Getter @Setter private int killedMonsterCount;
    private Set<SceneNpcBornEntry> npcBornEntrySet;
    @Getter private boolean finishedLoading = false;
    private int lastReportedSceneTime = Integer.MIN_VALUE;
    private String lastReportedFrozenState = null;
    @Getter protected int tickCount = 0;
    @Getter private boolean isPaused = false;

    private final SceneLoadCallbackGate afterLoadedCallbacks = new SceneLoadCallbackGate();
    private final ScenePlayerEntryGate playerEntryCallbacks = new ScenePlayerEntryGate();
    private final List<Runnable> afterHostInitCallbacks = new ArrayList<>();

    @Getter private GameEntity sceneEntity;
    @Getter private final ServerTaskScheduler scheduler;
    /** ICD for Arlecchino Masque NA BoL consume (official ~0.03s). */
    private final Map<Integer, Long> arlecchinoNaBoLReduceMs = new ConcurrentHashMap<>();

    public Scene(World world, SceneData sceneData) {
        this.world = world;
        this.sceneData = sceneData;
        this.players = new CopyOnWriteArrayList<>();
        this.entities = new ConcurrentHashMap<>();
        this.weaponEntities = new ConcurrentHashMap<>();

        this.prevScene = 3;
        this.sceneRoutes = GameData.getSceneRoutes(getId());

        this.startWorldTime = world.getWorldTime();

        this.spawnedEntities = ConcurrentHashMap.newKeySet();
        this.deadSpawnedEntities = ConcurrentHashMap.newKeySet();
        this.loadedBlocks = ConcurrentHashMap.newKeySet();
        this.loadedGroups = ConcurrentHashMap.newKeySet();
        this.loadedGridBlocks = new HashSet<>();
        this.npcBornEntrySet = ConcurrentHashMap.newKeySet();
        this.scriptManager = new SceneScriptManager(this);
        this.blossomManager = getSceneType() == SceneType.SCENE_WORLD ? new BlossomManager(this) : null;
        this.unlockedForces = new HashSet<>();
        this.sceneEntity = new EntityScene(this);
        this.scheduler = new ServerTaskScheduler();
    }

    public int getId() {
        return sceneData.getId();
    }

    public SceneType getSceneType() {
        return getSceneData().getSceneType();
    }

    public int getPlayerCount() {
        return this.getPlayers().size();
    }

    public Player getHost() {
        return this.getWorld().getHost();
    }

    public GameEntity getEntityById(int id) {

        if (id == 0x13800001) return this.sceneEntity;
        else if (id == this.getWorld().getLevelEntityId()) return this.getWorld().getEntity();

        var teamEntityPlayer =
                players.stream().filter(p -> p.getTeamManager().getEntity().getId() == id).findAny();
        if (teamEntityPlayer.isPresent()) return teamEntityPlayer.get().getTeamManager().getEntity();

        var entity = this.entities.get(id);
        if (entity == null) entity = this.weaponEntities.get(id);
        if (entity == null && (id >> ENTITY_ID_BIT_SHIFT) == EntityIdType.AVATAR.getId()) {
            for (var player : getPlayers()) {
                for (var avatar : player.getTeamManager().getActiveTeam()) {
                    if (avatar.getId() == id) return avatar;
                }
            }
        }

        if (entity == null && (id >> ENTITY_ID_BIT_SHIFT) == EntityIdType.WEAPON.getId()) {
            for (var player : this.getPlayers()) {
                for (var avatar : player.getTeamManager().getActiveTeam()) {
                    if (avatar.getWeaponEntityId() == id) return avatar;
                }
            }
        }

        return entity;
    }

    public GameEntity getFirstEntityByConfigId(int configId) {
        return this.entities.values().stream()
                .filter(x -> x.getConfigId() == configId)
                .findFirst()
                .orElse(null);
    }

    public GameEntity getEntityByConfigId(int configId, int groupId) {
        return this.entities.values().stream()
                .filter(x -> x.getConfigId() == configId && x.getGroupId() == groupId)
                .findFirst()
                .orElse(null);
    }

    @Nullable public Route getSceneRouteById(int routeId) {
        return sceneRoutes.get(routeId);
    }

    public void setPaused(boolean paused) {
        if (this.isPaused != paused) {
            this.isPaused = paused;
            this.broadcastPacket(new PacketSceneTimeNotify(this));
        }
    }

    public int getSceneTime() {
        return (int) (this.getWorld().getWorldTime() - this.startWorldTime);
    }

    public int getSceneTimeSeconds() {
        return this.getSceneTime() / 1000;
    }

    public void addDungeonSettleObserver(DungeonSettleListener dungeonSettleListener) {
        if (dungeonSettleListeners == null) {
            dungeonSettleListeners = new ArrayList<>();
        }

        dungeonSettleListeners.add(dungeonSettleListener);
    }

    public void triggerDungeonEvent(DungeonPassConditionType conditionType, int... params) {
        if (this.dungeonManager == null) return;
        this.dungeonManager.triggerEvent(conditionType, params);
    }

    public boolean isInScene(GameEntity entity) {
        return this.entities.containsKey(entity.getId());
    }

    public void addPlayer(Player player) {
        // Leave the old scene before taking this scene's lock. Holding one scene's lock while
        // waiting for another's deadlocks two players swapping between the same two scenes.
        var previous = player.getScene();
        if (previous != null && previous != this) {
            previous.removePlayer(player);
        }

        synchronized (this) {
            if (getPlayers().contains(player)) {
                return;
            }

            getPlayers().add(player);
            player.setSceneId(this.getId());
            player.setScene(this);

            // Refresh the HUD gate on every scene entry, including teleport and relogin.
            int phlogistonEnabled = player.isInNatlan() ? 1 : 0;
            if (player.getProperty(PlayerProperty.PROP_PHLOGISTON_ENABLE) != phlogistonEnabled) {
                player.setProperty(PlayerProperty.PROP_PHLOGISTON_ENABLE, phlogistonEnabled);
            }
            emu.grasscutter.game.player.SnezhnayaFrigid.syncForSceneEntry(player);

            this.setupPlayerAvatars(player);
        }
        this.playerEntryCallbacks.enter(player.getUid());
    }

    public synchronized void removePlayer(Player player) {

        if (this.getChallenge() != null && this.getChallenge().inProgress()) {
            player.sendPacket(new PacketDungeonChallengeFinishNotify(this.getChallenge()));
        }

        getPlayers().remove(player);
        this.playerEntryCallbacks.leave(player.getUid());
        player.setScene(null);

        this.removePlayerAvatars(player);

        for (EntityBaseGadget gadget : player.getTeamManager().getGadgets()) {
            this.removeEntity(gadget);
        }

        this.getEntities().values().stream()
                .filter(gameEntity -> gameEntity instanceof EntityVehicle)
                .map(gameEntity -> (EntityVehicle) gameEntity)
                .filter(entityVehicle -> entityVehicle.getOwner().equals(player))
                .forEach(entityVehicle -> this.removeEntity(entityVehicle, VisionType.VisionType_VISION_REMOVE));

        if (this.getPlayerCount() <= 0 && !this.dontDestroyWhenEmpty) {
            this.getScriptManager().onDestroy();
            this.getWorld().deregisterScene(this);
        }

        this.saveGroups();
    }

    private void setupPlayerAvatars(Player player) {

        player.getTeamManager().getActiveTeam().clear();

        TeamInfo teamInfo = player.getTeamManager().getCurrentTeamInfo();
        for (int avatarId : teamInfo.getAvatars()) {
            Avatar avatar = player.getAvatars().getAvatarById(avatarId);
            if (avatar == null) {
                if (player.getTeamManager().isUsingTrialTeam()) {
                    avatar = player.getTeamManager().getTrialAvatars().get(avatarId);
                }
                if (avatar == null) continue;
            }
            var entity =
                    EntityCreationEvent.call(
                            EntityAvatar.class,
                            new Class<?>[] {Scene.class, Avatar.class},
                            new Object[] {player.getScene(), avatar});
            if (entity == null) {
                continue;
            }
            player.getTeamManager().getActiveTeam().add(entity);
        }

        if (player.getTeamManager().getCurrentCharacterIndex()
                        >= player.getTeamManager().getActiveTeam().size()
                || player.getTeamManager().getCurrentCharacterIndex() < 0) {
            player
                    .getTeamManager()
                    .setCurrentCharacterIndex(player.getTeamManager().getCurrentCharacterIndex() - 1);
        }
    }

    private synchronized void removePlayerAvatars(Player player) {
        var team = player.getTeamManager().getActiveTeam();

        team.forEach(e -> removeEntity(e, VisionType.VisionType_VISION_REMOVE));
        // The team is rebuilt with new entities on the next scene, so the off-field members' ids
        // are dead too.
        team.forEach(EntityRuntimeStateCleanup::clear);
        team.clear();
    }

    public void spawnPlayer(Player player) {
        var teamManager = player.getTeamManager();
        if (this.isInScene(teamManager.getCurrentAvatarEntity())) {
            return;
        }

        if (teamManager.getCurrentAvatarEntity().getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)
                <= 0f) {
            teamManager.getCurrentAvatarEntity().setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1f);
        }

        this.addEntity(teamManager.getCurrentAvatarEntity());

        teamManager.getActiveTeam().stream()
                .map(EntityAvatar::getAvatar)
                .forEach(Avatar::sendSkillExtraChargeMap);
    }

    private void addEntityDirectly(GameEntity entity) {
        getEntities().put(entity.getId(), entity);
        if (blossomManager != null) blossomManager.onSceneEntityCreated(entity);
        entity.onCreate();
    }

    public synchronized void addEntity(GameEntity entity) {
        this.addEntityDirectly(entity);
        this.broadcastPacket(new PacketSceneEntityAppearNotify(entity));
    }

    public synchronized void addEntityToSingleClient(Player player, GameEntity entity) {
        this.addEntityDirectly(entity);
        player.sendPacket(new PacketSceneEntityAppearNotify(entity));
    }

    /** Wildlife meat that official scatters as one ground pickup per unit. */
    private static boolean isSplitGroundDropItem(int itemId) {
        return itemId == 100061 // raw meat
                || itemId == 100064 // fowl
                || itemId == 100086 // chunk of raw meat
                || itemId == 100087 // bird egg / small fowl
                || itemId == 100094 // chilled meat
                || itemId == 100097; // mysterious meat
    }

    public void addDropEntity(GameItem item, GameEntity bornForm, Player player, boolean share) {

        ItemData itemData = GameData.getItemDataMap().get(item.getItemId());
        if (itemData == null) return;
        int count = Math.max(item.getCount(), 1);
        // Equip + wildlife meat: one EntityItem per unit. Other materials stay stacked.
        boolean split = itemData.isEquip() || isSplitGroundDropItem(item.getItemId());
        if (split) {
            float range = 1.5f + (.05f * count);
            for (int j = 0; j < count; j++) {
                Position pos = bornForm.getPosition().nearby2d(range).addY(0.5f);
                addEntity(new EntityItem(this, player, itemData, pos, 1, share));
            }
        } else {
            addEntity(
                    new EntityItem(
                            this,
                            player,
                            itemData,
                            bornForm.getPosition().clone().addY(0.5f),
                            count,
                            share));
        }
    }

    public void addEntities(Collection<? extends GameEntity> entities) {
        addEntities(entities, VisionType.VisionType_VISION_BORN);
    }

    public void updateEntity(GameEntity entity) {
        this.broadcastPacket(new PacketSceneEntityUpdateNotify(entity));
    }

    public void updateEntity(GameEntity entity, VisionType type) {
        this.broadcastPacket(new PacketSceneEntityUpdateNotify(Arrays.asList(entity), type));
    }

    private static <T> List<List<T>> chopped(List<T> list, final int L) {
        List<List<T>> parts = new ArrayList<List<T>>();
        final int N = list.size();
        for (int i = 0; i < N; i += L) {
            parts.add(new ArrayList<T>(list.subList(i, Math.min(N, i + L))));
        }
        return parts;
    }

    public synchronized void addEntities(
            Collection<? extends GameEntity> entities, VisionType visionType) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        for (var entity : entities) {
            this.addEntityDirectly(entity);
        }

        for (var l : chopped(new ArrayList<>(entities), 100)) {
            this.broadcastPacket(new PacketSceneEntityAppearNotify(l, visionType));
        }
    }

    private GameEntity removeEntityDirectly(GameEntity entity) {
        var removed = getEntities().remove(entity.getId());
        if (removed != null) {
            removed.onRemoved();
        }
        return removed;
    }

    public void removeEntity(GameEntity entity) {
        this.removeEntity(entity, VisionType.VisionType_VISION_DIE);
    }

    public synchronized void removeEntity(GameEntity entity, VisionType visionType) {
        GameEntity removed = this.removeEntityDirectly(entity);
        if (removed != null) {
            EntityRuntimeStateCleanup.clear(removed);
            this.broadcastPacket(new PacketSceneEntityDisappearNotify(removed, visionType));
        }
    }

    public void removeEntities(List<GameEntity> entity, VisionType visionType) {
        var toRemove =
                entity.stream()
                        .filter(Objects::nonNull)
                        .map(this::removeEntityDirectly)
                        .filter(Objects::nonNull)
                        .toList();
        toRemove.forEach(EntityRuntimeStateCleanup::clear);
        if (!toRemove.isEmpty()) {
            this.broadcastPacket(new PacketSceneEntityDisappearNotify(toRemove, visionType));
        }
    }

    public synchronized void replaceEntity(EntityAvatar oldEntity, EntityAvatar newEntity) {
        this.removeEntityDirectly(oldEntity);
        this.addEntityDirectly(newEntity);
        this.broadcastPacket(
                new PacketSceneEntityDisappearNotify(oldEntity, VisionType.VisionType_VISION_REPLACE));
        this.broadcastPacket(
                new PacketSceneEntityAppearNotify(
                        newEntity, VisionType.VisionType_VISION_REPLACE, oldEntity.getId()));
    }

    public void showOtherEntities(Player player) {
        GameEntity currentEntity = player.getTeamManager().getCurrentAvatarEntity();
        List<GameEntity> entities =
                this.getEntities().values().stream()
                        .filter(entity -> entity != currentEntity)
                        .filter(
                                gameEntity ->
                                        !(gameEntity instanceof Rebornable rebornable) || !rebornable.isInCD())
                        .toList();

        player.sendPacket(new PacketSceneEntityAppearNotify(entities, VisionType.VisionType_VISION_MEET));
    }

    public void handleAttack(AttackResult result) {
        // Remap OSREL defenseId + clamp ore/breakable damage (multi-hit HP).
        try {
            result = emu.grasscutter.game.entity.gadget.OreMiningHelper.fixAttackResult(this, result);
        } catch (Throwable ignored) {
        }
        GameEntity target = getEntityById(result.getDefenseId());
        ElementType attackType = ElementType.getTypeByValue(result.getElementType());

        if (target == null) {
            // Ordinary: the client keeps swinging at something the server already removed. One
            // line per hit, and nobody acts on any of them.
            Grasscutter.getLogger().debug("handleAttack: target not found defenseId={} attackerId={} damage={}", result.getDefenseId(), result.getAttackerId(), result.getDamage());
            Grasscutter.getLogger().debug("handleAttack unknownFields (defense_id = the monster entityId): {}", result.getUnknownFields().toString().replaceAll("\\s+", " "));
            return;
        }
        if (target instanceof EntityAvatar) {
            if (((EntityAvatar) target).getPlayer().isInGodMode()) {
                return;
            }
        }

        // World chests are interact-only; AoE / auto-attacks must not break them
        // (otherwise explore helpers treat them as gone and respawn, giving double F prompts).
        // Exception: bramble/frozen/rock seals must still accept the matching element.
        if (target instanceof EntityGadget chestGadget) {
            try {
                boolean isChest =
                        chestGadget.getContent() instanceof emu.grasscutter.game.entity.gadget.GadgetChest
                                || (chestGadget.getGadgetData() != null
                                        && chestGadget.getGadgetData().getType() == EntityType.Chest);
                if (isChest) {
                    if (emu.grasscutter.game.entity.EnvironmentalSealHelper.tryProcessAttack(
                            chestGadget, result.getDamage(), attackType)) {
                        return;
                    }
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        GameEntity attacker = getEntityById(result.getAttackerId());
        // Dulin's Blood ice break: while the buff is held, hitting solid ice or an ice seal shatters it
        // immediately and consumes the buff.
        try {
            if (emu.grasscutter.game.entity.gadget.ScarletQuartzCombatHelper.trySmashIce(
                    this, attacker, target)) {
                return;
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("ScarletQuartz trySmashIce: {}", t.toString());
        }
        if (attacker instanceof EntityClientGadget && target instanceof EntityAvatar) {
            if (target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS) > 0f) return;
            float curHp = target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            float capped = Math.min(result.getDamage(), curHp - 1f);
            if (capped > 0) target.damage(capped, result.getAttackerId(), attackType);
            return;
        }

        if (target instanceof EntityAvatar avatar) {
            if (avatar.getPlayer()
            .getAbilityManager()
            .isAbilityInvulnerable()) {
                return;
            }
        }

        target.damage(result.getDamage(), result.getAttackerId(), attackType);

        if (!(target instanceof EntityAvatar)) {
            EntityAvatar arlecAttacker = resolveArlecchinoAttacker(attacker);
            if (arlecAttacker != null) {
                synchronized (arlecAttacker) {
                    // Q slash often reports ElementalBurst_Gadget as attackerId - still settle BoL clear.
                    if (ArlecchinoBurstBoL.isPending(arlecAttacker.getId())
                            || (attacker instanceof EntityAvatar)) {
                        ArlecchinoBurstBoL.onAttack(arlecAttacker, result);
                    }
                    // Masque of the Red Death: only avatar Normal Attacks consume BoL, not gadget / CA / plunge / E / Q.
                    if (attacker instanceof EntityAvatar) {
                        reduceArlecchinoBoLOnNormalAttack(arlecAttacker, result);
                    }
                }
            }
        }

        // Clorinde: BoL is only paid down by Impale the Night healing. Normal attacks and Swift Hunt never
        // consume it; the mistaken Arlecchino-style on-hit drain has been removed.
    }

    /**
     * Arlecchino Q damage is frequently attributed to {@code Arlecchino_ElementalBurst_Gadget}
     * instead of the avatar entity. Map that (and nested gadgets) back to the casting avatar.
     */
    private EntityAvatar resolveArlecchinoAttacker(GameEntity attacker) {
        if (attacker instanceof EntityAvatar avatar
                && avatar.getAvatar() != null
                && avatar.getAvatar().getAvatarId() == 10000096) {
            return avatar;
        }
        if (!(attacker instanceof EntityClientGadget gadget)) {
            return null;
        }
        int ownerId = gadget.getOriginalOwnerEntityId();
        if (ownerId <= 0) {
            ownerId = gadget.getOwnerEntityId();
        }
        if (ownerId > 0) {
            GameEntity owner = getEntityById(ownerId);
            if (owner instanceof EntityClientGadget nested) {
                ownerId =
                        nested.getOriginalOwnerEntityId() > 0
                                ? nested.getOriginalOwnerEntityId()
                                : nested.getOwnerEntityId();
                owner = ownerId > 0 ? getEntityById(ownerId) : null;
            }
            if (owner instanceof EntityAvatar avatar
                    && avatar.getAvatar() != null
                    && avatar.getAvatar().getAvatarId() == 10000096) {
                return avatar;
            }
        }
        Player player = gadget.getOwner();
        if (player == null || player.getTeamManager() == null) {
            return null;
        }
        for (EntityAvatar avatar : player.getTeamManager().getActiveTeam()) {
            if (avatar != null
                    && avatar.getAvatar() != null
                    && avatar.getAvatar().getAvatarId() == 10000096
                    && ArlecchinoBurstBoL.isPending(avatar.getId())) {
                return avatar;
            }
        }
        return null;
    }

    /**
     * Masque of the Red Death: BoL ≥ 30% MaxHP. Official FireAttack_ReduceHPDebts only runs on
     * NormalAttack_01..06 tags - 7.5% of current BoL, ICD ~0.03s. Charged/plunge/skill must not
     * consume.
     */
    private void reduceArlecchinoBoLOnNormalAttack(EntityAvatar arlecchino, AttackResult result) {
        float maxHp = arlecchino.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float curDebt = arlecchino.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (maxHp <= 0f || curDebt < 0.30f * maxHp) {
            return;
        }
        if (!isArlecchinoRedDeathNormalHit(result)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = arlecchinoNaBoLReduceMs.get(arlecchino.getId());
        if (last != null && now - last < 30L) {
            return;
        }
        arlecchinoNaBoLReduceMs.put(arlecchino.getId(), now);

        if (ArlecchinoBurstBoL.isPending(arlecchino.getId())) {
            return;
        }
        float reduction = curDebt * 0.075f;
        float newDebt = Math.max(0f, curDebt - reduction);
        float change = newDebt - curDebt;
        if (change == 0f) {
            return;
        }
        var debtsReason =
                newDebt <= 0f
                        ? ChangeHpDebtsReason._ChangeHpDebtsReason
                                ._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY_FINISH
                        : ChangeHpDebtsReason._ChangeHpDebtsReason
                                ._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY;
        ArlecchinoBoLSync.pushBoL(arlecchino, newDebt, change, debtsReason);
    }

    private static boolean isArlecchinoRedDeathNormalHit(AttackResult result) {
        if (result == null) {
            return false;
        }
        // 7.0 often sends hashed anim ids with an empty string - use the util's hash table.
        if (ArlecchinoBoLUtil.isRedDeathNormalAttackHit(result)) {
            return true;
        }
        String anim = result.getAnimEventId();
        if (anim == null || anim.isEmpty()) {
            return false;
        }
        String s = anim.toLowerCase(Locale.ROOT);
        if (s.contains("extraattack")
                || s.contains("charged")
                || s.contains("plunge")
                || s.contains("burst")
                || s.contains("elementalart")
                || s.contains("elemental_art")
                || s.contains("elementalburst")) {
            return false;
        }
        return s.contains("normalattack")
                || s.contains("normal_attack")
                || s.contains("fireattack")
                || (s.contains("atk") && s.contains("plus"));
    }

    public void killEntity(GameEntity target) {
        killEntity(target, 0);
    }

    public void killEntity(GameEntity target, int attackerId) {
        GameEntity attacker = null;

        if (attackerId > 0) {
            attacker = getEntityById(attackerId);
        }

        if (attacker != null) {

            if (attacker instanceof EntityClientGadget gadgetAttacker) {
                var clientGadgetOwner = getEntityById(gadgetAttacker.getOwnerEntityId());
                if (clientGadgetOwner instanceof EntityAvatar) {
                    ((EntityClientGadget) attacker)
                            .getOwner()
                            .getCodex()
                            .checkAnimal(target, CodexAnimalData.CountType.CODEX_COUNT_TYPE_KILL);
                }
            } else if (attacker instanceof EntityAvatar avatarAttacker) {
                avatarAttacker
                        .getPlayer()
                        .getCodex()
                        .checkAnimal(target, CodexAnimalData.CountType.CODEX_COUNT_TYPE_KILL);
            }
        }

        this.broadcastPacket(new PacketLifeStateChangeNotify(attackerId, target, LifeState.LIFE_DEAD));

        // Barbara C6: send LIFE_DEAD first so the character goes down, then immediately revive to full HP,
        // keeping her on-field without a switch.
        if (target instanceof EntityAvatar deadAvatar) {
            try {
                if (PartyReviveHelper.tryBarbaraC6AfterDeath(deadAvatar)) {
                    if (this.getEntityById(deadAvatar.getId()) == null) {
                        this.addEntity(deadAvatar);
                    }
                    return;
                }
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("[BarbaraC6] killEntity revive failed", t);
            }
        }

        var world = this.getWorld();
        if (target instanceof EntityMonster monster && this.getSceneType() != SceneType.SCENE_DUNGEON) {
            boolean handled = world.getServer().getDropSystem().handleMonsterDrop(monster);
            if (!handled) {
                if (monster.getMetaMonster() != null) {
                    Grasscutter.getLogger()
                            .debug(
                                    "Can not solve monster drop: drop_id = {}, drop_tag = {}. Falling back to legacy drop system.",
                                    monster.getMetaMonster().drop_id,
                                    monster.getMetaMonster().drop_tag);
                }
                world.getServer().getDropSystemLegacy().callDrop(monster);
            }
        }

        if (target instanceof EntityGadget gadget) {
            if (gadget.getMetaGadget() != null) {
                world
                        .getServer()
                        .getDropSystem()
                        .handleChestDrop(
                                gadget.getMetaGadget().drop_id, gadget.getMetaGadget().drop_count, gadget);
            }
        }

        // Weekly bosses (e.g. Dvalin): VISION_DIE leaves a stuck corpse; remove immediately.
        if (target instanceof EntityMonster boss
                && emu.grasscutter.game.dungeons.WeeklyBossModelCleanup.shouldForceRemoveModel(
                        this, boss)) {
            emu.grasscutter.game.dungeons.WeeklyBossModelCleanup.removeBossAndOwnedGadgets(this, boss);
        } else if (target instanceof EntityVehicle vehicle
                && emu.grasscutter.game.ability.NatsaurusVehicleHelper.isNatsaurusMount(vehicle)) {
            scheduleVehicleRemoval(vehicle);
        } else {
            this.removeEntity(target);
        }

        if (target instanceof EntityClientGadget cg && cg.getOwner() != null) {
            cg.getOwner().getTeamManager().getGadgets().remove(cg);
        }

        target.onDeath(attackerId);
        this.triggerDungeonEvent(
                DungeonPassConditionType.DUNGEON_COND_KILL_MONSTER_COUNT, ++killedMonsterCount);
    }

    /**
     * Kills a mount the way its own model knows how: collapse first, then take the corpse away.
     *
     * <p>Each vision type on its own gets one half wrong. {@code VISION_REMOVE} alone drops the mount at
     * once, so the client never plays the death animation it has; {@code VISION_DIE} alone gives the
     * collapse but leaves the corpse, and a Saurian mount gadget with nobody on it *is* a soul candle, so
     * what the player sees is the dragon they rode turning into a candle they cannot interact with.
     * Holding the candle back is worse than either: the mount is still there while the rider is told to
     * dismount, and the client plays its "possession ended" transformation instead of the death.
     *
     * <p>So this removes with {@code VISION_DIE}, which the client animates, and then - after the die
     * block's own {@code dieEndTime}, 1.5s for the Natlan Saurians - broadcasts the removal itself. The
     * entity is already out of the scene by then, so there is nothing left to remove and only the notify
     * needs sending.
     */
    private void scheduleVehicleRemoval(EntityVehicle vehicle) {
        var combat = vehicle.getConfigGadget() == null ? null : vehicle.getConfigGadget().getCombat();
        var die = combat == null ? null : combat.getDie();
        double seconds = die == null || !die.isHasAnimatorDie() ? 0d : die.getDieEndTime();

        this.removeEntity(vehicle, VisionType.VisionType_VISION_DIE);

        if (seconds <= 0d) return;

        int tickRateMs = Math.max(1, emu.grasscutter.config.Configuration.GAME_INFO.tickRateMs);
        int ticks = (int) Math.ceil(seconds * 1000d / tickRateMs);
        this.scheduler.scheduleDelayedTask(
            () -> this.broadcastPacket(
                    new PacketSceneEntityDisappearNotify(vehicle, VisionType.VisionType_VISION_REMOVE)),
            ticks);
    }

    public void onTick() {

        if (this.getSceneType() == SceneType.SCENE_HOME_WORLD
                || this.getSceneType() == SceneType.SCENE_HOME_ROOM) {
            this.finishLoading();
            return;
        }

        if (!isPaused) {
            this.getScheduler().runTasks();
        }

        if (this.getScriptManager().isInit()) {

            this.checkGroups();
        } else {

            this.checkSpawns();
        }

        this.scriptManager.checkRegions();
        this.scriptManager.getSealBattleManager().onTick();

        if (challenge != null) {
            challenge.onCheckTimeOut();
        }

        var sceneTime = getSceneTimeSeconds();

        var entities = Map.copyOf(this.getEntities());
        entities.forEach(
                (eid, e) -> {
                    if (!e.isAlive()) {
                        this.getEntities().remove(eid);
                    } else e.onTick(sceneTime);
                });

        if (getSceneType() == SceneType.SCENE_WORLD) {
            blossomManager.onTick();
        }

        // Remote Player may not carry this hook; settle delayed Q BoL clear from the scene tick.
        for (Player player : this.getPlayers()) {
            ArlecchinoBurstBoL.onTick(player);
        }

        if (!getPlayers().isEmpty()) {
            var towerManager = getPlayers().get(0).getTowerManager();
            if (towerManager != null && towerManager.isInProgress()) {
                towerManager.onTick();
            }
        }

        this.checkNpcGroup();

        this.finishLoading();
        this.checkPlayerRespawn();
        if (this.tickCount % 50 == 0) this.reportFrozenState();
        if (this.tickCount++ % 10 == 0) this.broadcastPacket(new PacketSceneTimeNotify(this));
    }

    /**
     * Reports the handful of flags that stop the client's world dead while its menus keep working:
     * a paused world or scene, locked game time, a scene time that is not advancing, or a player
     * stuck short of {@link Player.SceneLoadState#LOADED}. Any of these looks identical in game -
     * the avatar will not walk and everything else stands still - and none of them logs anything on
     * its own, so say so here rather than leaving it to be guessed at.
     */
    private void reportFrozenState() {
        var world = this.getWorld();
        if (world == null) return;

        var sceneTime = this.getSceneTime();
        var stalled = sceneTime == this.lastReportedSceneTime;
        this.lastReportedSceneTime = sceneTime;

        var notLoaded =
                this.players.stream()
                        .anyMatch(p -> p.getSceneLoadState() != Player.SceneLoadState.LOADED);
        var frozen = this.isPaused || world.isPaused() || world.isTimeLocked() || stalled || notLoaded;

        var line =
                "Scene {} tick: sceneTime={} ({}), scenePaused={}, worldPaused={}, timeLocked={},"
                    + " entities={}, players={}";
        Object[] args = {
            this.getId(),
            sceneTime,
            stalled ? "not advancing" : "advancing",
            this.isPaused,
            world.isPaused(),
            world.isTimeLocked(),
            this.getEntities().size(),
            this.players.stream().map(p -> p.getUid() + ":" + p.getSceneLoadState()).toList()
        };

        // Only worth an operator's attention when something is wrong, and only when it is
        // news: a world that stays frozen says so once, not every ten seconds forever.
        var state = frozen + "/" + this.isPaused + "/" + world.isPaused() + "/" + world.isTimeLocked()
                + "/" + stalled + "/" + notLoaded;
        var changed = !state.equals(this.lastReportedFrozenState);
        this.lastReportedFrozenState = state;

        if (frozen && changed) {
            Grasscutter.getLogger().warn(line, args);
        } else {
            Grasscutter.getLogger().debug(line, args);
        }
    }

    protected void checkPlayerRespawn() {
        if (this.getScriptManager().getConfig() == null) return;
        var diePos = this.getScriptManager().getConfig().die_y;

        this.players.forEach(
                player -> {
                    if (this.getScriptManager().getConfig() == null) return;

                    if (diePos >= player.getPosition().getY()) {

                        this.respawnPlayer(player);
                    }
                });

        this.getEntities()
                .forEach(
                        (id, entity) -> {
                            if (diePos >= entity.getPosition().getY()) {
                                this.killEntity(entity);
                            }
                        });
    }

    public Position getDefaultLocation(Player player) {
        var config = getScriptManager().getConfig();
        val defaultPosition = config != null ? config.resolveBornPos() : null;
        return defaultPosition != null ? defaultPosition : player.getPosition();
    }

    public Position getDefaultRotation(Player player) {
        var config = this.getScriptManager().getConfig();
        var defaultRotation = config != null ? config.resolveBornRot() : null;
        return defaultRotation != null ? defaultRotation : player.getRotation();
    }

    private Position getRespawnLocation(Player player) {

        var lastCheckpointPos = dungeonManager != null ? dungeonManager.getRespawnLocation() : null;
        return lastCheckpointPos != null ? lastCheckpointPos : getDefaultLocation(player);
    }

    private Position getRespawnRotation(Player player) {
        var lastCheckpointRot =
                this.dungeonManager != null ? this.dungeonManager.getRespawnRotation() : null;
        return lastCheckpointRot != null ? lastCheckpointRot : this.getDefaultRotation(player);
    }

    public boolean respawnPlayer(Player player) {

        player.getTeamManager().applyVoidDamage();

        var targetPos = getRespawnLocation(player);
        var targetRot = getRespawnRotation(player);
        var teleportProps =
                TeleportProperties.builder()
                        .sceneId(getId())
                        .teleportTo(targetPos)
                        .teleportRot(targetRot)
                        .teleportType(PlayerTeleportEvent.TeleportType.INTERNAL)
                        .enterType(EnterTypeOuterClass.EnterType.EnterType_ENTER_GOTO)
                        .enterReason(
                                dungeonManager != null ? EnterReason.DungeonReviveOnWaypoint : EnterReason.Revival);

        return this.getWorld().transferPlayerToScene(player, teleportProps.build());
    }

    public void finishLoading() {
        if (this.finishedLoading) return;

        this.finishedLoading = true;
        this.afterLoadedCallbacks.complete();
    }

    public void runWhenFinished(Runnable runnable) {
        this.afterLoadedCallbacks.whenComplete(runnable);
    }

    /** Register a one-shot callback for when the specified player enters this scene. */
    public void runWhenPlayerEnters(Player player, Runnable runnable) {
        if (player == null || runnable == null) return;
        this.playerEntryCallbacks.whenPresent(player.getUid(), runnable);
    }

    public void playerSceneInitialized(Player player) {

        if (!player.equals(this.getHost())) return;

        this.afterHostInitCallbacks.forEach(Runnable::run);
        this.afterHostInitCallbacks.clear();
    }

    public void runWhenHostInitialized(Runnable runnable) {
        if (this.isFinishedLoading()) {
            runnable.run();
            return;
        }

        this.afterHostInitCallbacks.add(runnable);
    }

    public int getEntityLevel(int baseLevel, int worldLevelOverride) {
        int level = worldLevelOverride > 0 ? worldLevelOverride + baseLevel - 22 : baseLevel;
        level = Math.min(level, 100);
        level = level <= 0 ? 1 : level;

        return level;
    }

    public int getLevelForMonster(int configId, int defaultLevel) {
        if (getDungeonManager() != null) {
            return getDungeonManager().getLevelForMonster(configId);
        } else if (getWorld().getWorldLevel() > 0) {
            var worldLevelData = GameData.getWorldLevelDataMap().get(getWorld().getWorldLevel());

            if (worldLevelData != null) {
                return worldLevelData.getMonsterLevel();
            }
        }
        return defaultLevel;
    }

    public void checkNpcGroup() {
        Set<SceneNpcBornEntry> npcBornEntries = ConcurrentHashMap.newKeySet();
        for (Player player : this.getPlayers()) {
            npcBornEntries.addAll(loadNpcForPlayer(player));
        }

        this.npcBornEntrySet = npcBornEntries;
    }

    public void checkSpawns() {
        Set<SpawnDataEntry.GridBlockId> loadedGridBlocks = new HashSet<>();
        for (Player player : this.getPlayers()) {
            Collections.addAll(
                    loadedGridBlocks,
                    SpawnDataEntry.GridBlockId.getAdjacentGridBlockIds(
                            player.getSceneId(), player.getPosition()));
        }

        Set<SpawnDataEntry.GridBlockId> leavingBlocks = new HashSet<>(this.loadedGridBlocks);
        leavingBlocks.removeAll(loadedGridBlocks);
        if (!leavingBlocks.isEmpty()) {
            this.getDeadSpawnedEntities().removeIf(entry -> leavingBlocks.contains(entry.getBlockId()));
        }
        int blossomRevision = getSceneType() == SceneType.SCENE_WORLD
                ? blossomManager.getActiveSetRevision() : 0;
        if (this.loadedGridBlocks.equals(loadedGridBlocks)
                && loadedBlossomRevision == blossomRevision) {
            return;
        }
        this.loadedGridBlocks = loadedGridBlocks;
        this.loadedBlossomRevision = blossomRevision;
        var spawnLists = GameDepot.getSpawnLists();
        Set<SpawnDataEntry> visible = new HashSet<>();
        for (var block : loadedGridBlocks) {
            var spawns = spawnLists.get(block);
            if (spawns != null) {
                visible.addAll(spawns);
            }
        }

        if (getSceneType() == SceneType.SCENE_WORLD) {
            visible.removeIf(entry -> !blossomManager.isActiveSpawn(entry));
        }

        WorldLevelData worldLevelData = GameData.getWorldLevelDataMap().get(getWorld().getWorldLevel());
        int worldLevelOverride = 0;

        if (worldLevelData != null) {
            worldLevelOverride = worldLevelData.getMonsterLevel();
        }

        List<GameEntity> toAdd = new ArrayList<>();
        List<GameEntity> toRemove = new ArrayList<>();
        var spawnedEntities = this.getSpawnedEntities();
        for (SpawnDataEntry entry : visible) {

            if (!spawnedEntities.contains(entry) && !this.getDeadSpawnedEntities().contains(entry)) {
                // The dead set is per scene instance and forgets a collected oculus on relog.
                if (entry.getGatherItemId() > 0
                        && entry.getGroup() != null
                        && OpenWorldSpawnHelper.isPermanentGather(entry.getGatherItemId())
                        && OpenWorldSpawnHelper.isEntityRespawnBlocked(
                                this, entry.getGroup().getGroupId(), entry.getConfigId())) {
                    Grasscutter.getLogger()
                            .debug(
                                    "[Spawn] skip collected oculus item={} group={} cfg={}",
                                    entry.getGatherItemId(),
                                    entry.getGroup().getGroupId(),
                                    entry.getConfigId());
                    this.getDeadSpawnedEntities().add(entry);
                    continue;
                }

                GameEntity entity = null;

                if (entry.getMonsterId() > 0) {
                    MonsterData data = GameData.getMonsterDataMap().get(entry.getMonsterId());
                    if (data == null) continue;

                    int level = this.getEntityLevel(entry.getLevel(), worldLevelOverride);

                    EntityMonster monster =
                            new EntityMonster(this, data, entry.getPos(), entry.getRot(), level);
                    monster.setGroupId(entry.getGroup().getGroupId());
                    monster.setPoseId(entry.getPoseId());
                    monster.setConfigId(entry.getConfigId());
                    monster.setSpawnEntry(entry);

                    entity = monster;
                } else if (entry.getGadgetId() > 0) {
                    EntityGadget gadget =
                            new EntityGadget(this, entry.getGadgetId(), entry.getPos(), entry.getRot());
                    gadget.setGroupId(entry.getGroup().getGroupId());
                    gadget.setConfigId(entry.getConfigId());
                    gadget.setSpawnEntry(entry);
                    int state = entry.getGadgetState();
                    if (state > 0) {
                        gadget.setState(state);
                    }
                    gadget.buildContent();

                    gadget.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, Float.POSITIVE_INFINITY);
                    gadget.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, Float.POSITIVE_INFINITY);
                    gadget.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, Float.POSITIVE_INFINITY);

                    entity = gadget;
                    if (getSceneType() == SceneType.SCENE_WORLD) {
                        blossomManager.initBlossom(gadget);
                    }
                }

                if (entity == null) continue;

                toAdd.add(entity);
                spawnedEntities.add(entry);
            }
        }

        for (GameEntity entity : this.getEntities().values()) {
            var spawnEntry = entity.getSpawnEntry();
            if (spawnEntry != null
                    && !(entity instanceof EntityWeapon)
                    && !visible.contains(spawnEntry)
                    && !(getSceneType() == SceneType.SCENE_WORLD
                            && blossomManager.isNativeCampSpawn(spawnEntry))) {
                toRemove.add(entity);
                spawnedEntities.remove(spawnEntry);
            }
        }

        if (toAdd.size() > 0) {
            toAdd.forEach(this::addEntityDirectly);
            this.broadcastPacket(new PacketSceneEntityAppearNotify(toAdd, VisionType.VisionType_VISION_BORN));
        }

        if (toRemove.size() > 0) {
            toRemove.forEach(this::removeEntityDirectly);
            this.broadcastPacket(
                    new PacketSceneEntityDisappearNotify(toRemove, VisionType.VisionType_VISION_REMOVE));
            if (getSceneType() == SceneType.SCENE_WORLD) {
                blossomManager.recycleGadgetEntity(toRemove);
            }
        }
    }

    public List<SceneBlock> getPlayerActiveBlocks(Player player) {

        return SceneIndexManager.queryNeighbors(
                getScriptManager().getBlocksIndex(),
                player.getPosition().toXZDoubleArray(),
                Grasscutter.getConfig().server.game.loadEntitiesForPlayerRange);
    }

    public Set<Integer> getPlayerActiveGroups(Player player) {

        Position playerPosition = player.getPosition();
        Set<Integer> activeGroups = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            Grid grid = getScriptManager().getGroupGrids().get(i);

            activeGroups.addAll(grid.getNearbyGroups(i, playerPosition));
        }

        return activeGroups;
    }

    public boolean loadBlock(SceneBlock block) {
        if (this.loadedBlocks.contains(block)) return false;

        this.onLoadBlock(block, this.players);
        this.loadedBlocks.add(block);
        return true;
    }

    public void checkGroups() {
        Set<Integer> visible =
                this.players.stream()
                        .map(this::getPlayerActiveGroups)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toSet());

        for (var group : this.loadedGroups) {
            if (!visible.contains(group.id) && !group.dynamic_load && !group.dontUnload)
                unloadGroup(scriptManager.getBlocks().get(group.block_id), group.id);
        }

        var toLoad =
                visible.stream()
                        .filter(g -> this.loadedGroups.stream().noneMatch(gr -> gr.id == g))
                        .map(
                                g -> {
                                    for (var b : scriptManager.getBlocks().values()) {
                                        loadBlock(b);
                                        if (b.groups == null) continue;
                                        SceneGroup group = b.groups.getOrDefault(g, null);
                                        if (group != null && !group.dynamic_load
                                                && !isReplacedByLoadedDynamicGroup(group)) return group;
                                    }

                                    return null;
                                })
                        .filter(Objects::nonNull)
                        .toList();

        this.onLoadGroup(toLoad);
        if (!toLoad.isEmpty()) this.onRegisterGroups();
        try {
            emu.grasscutter.game.world.InvestigationSpawnHelper.ensureNearby(this);
        } catch (Throwable __t) {
            emu.grasscutter.Grasscutter.getLogger().error("InvestigationSpawnHelper nearby check failed", __t);
        }
        try {
            emu.grasscutter.game.world.NodKraiExploreSpawnHelper.ensureNearby(this);
        } catch (Throwable __t) {
            emu.grasscutter.Grasscutter.getLogger().error("NodKraiExploreSpawnHelper nearby check failed", __t);
        }
        try {
            emu.grasscutter.game.world.SnezhnayaExploreSpawnHelper.ensureNearby(this);
        } catch (Throwable __t) {
            emu.grasscutter.Grasscutter.getLogger().error("SnezhnayaExploreSpawnHelper nearby check failed", __t);
        }
        try {
            emu.grasscutter.game.activity.leylinechallenge.LeyLineChallengeGroupLoader.ensureNearby(this);
        } catch (Throwable __t) {
            emu.grasscutter.Grasscutter.getLogger()
                    .error("LeyLineChallengeGroupLoader nearby check failed", __t);
        }
        try {
            emu.grasscutter.game.world.OfferingSpawnHelper.ensureNearby(this);
        } catch (Throwable __t) {
            emu.grasscutter.Grasscutter.getLogger()
                    .error("OfferingSpawnHelper nearby check failed", __t);
        }
        if (getSceneType() == SceneType.SCENE_WORLD) {
            blossomManager.ensureScriptedSpawns();
        }
        emu.grasscutter.game.dailytask.DailyTaskGroupLifecycle.tick(this);
    }

    /** Authored replacement edges are scene-local policy only while their dynamic group is loaded. */
    private boolean isReplacedByLoadedDynamicGroup(SceneGroup group) {
        if (group.is_replaceable == null) return false;
        for (var replacement : this.loadedGroups) {
            if (!replacement.dynamic_load || replacement.is_replaceable == null) continue;
            var data = GameData.getGroupReplacements().get(replacement.id);
            if (data == null || data.replace_groups == null || !data.replace_groups.contains(group.id)) continue;
            if ((group.is_replaceable.value
                            && group.is_replaceable.version <= replacement.is_replaceable.version)
                    || group.is_replaceable.new_bin_only) return true;
        }
        return false;
    }

    public void onLoadBlock(SceneBlock block, List<Player> players) {
        this.getScriptManager().loadBlockFromScript(block);
        scriptManager.getLoadedGroupSetPerBlock().put(block.id, new HashSet<>());

        Grasscutter.getLogger().trace("Scene {} block {} loaded.", this.getId(), block.id);
    }

    public int loadDynamicGroup(int group_id) {
        SceneGroup group = getScriptManager().getGroupById(group_id);
        if (group == null) return -1;

        this.onLoadGroup(new ArrayList<>(List.of(group)));

        if (GameData.getGroupReplacements().containsKey(group_id)) onRegisterGroups();

        if (group.init_config == null) return -1;
        return group.init_config.suite;
    }

    public boolean unregisterDynamicGroup(int groupId) {
        var group = getScriptManager().getGroupById(groupId);
        if (group == null) return false;

        var block = getScriptManager().getBlocks().get(group.block_id);
        this.unloadGroup(block, groupId);
        return true;
    }

    public void onRegisterGroups() {
        var sceneGroups = this.loadedGroups;
        var sceneGroupMap =
                sceneGroups.stream().collect(Collectors.toMap(item -> item.id, item -> item));
        var sceneGroupsIds = sceneGroups.stream().map(group -> group.id).toList();
        var dynamicGroups =
                sceneGroups.stream().filter(group -> group.dynamic_load).map(group -> group.id).toList();

        var nodes = new ArrayList<KahnsSort.Node>();
        var groupList = new ArrayList<Integer>();
        GameData.getGroupReplacements().values().stream()
                .filter(replacement -> dynamicGroups.contains(replacement.id))
                .forEach(
                        replacement -> {
                            Grasscutter.getLogger().debug("Graph ordering replacement {}", replacement);
                            replacement.replace_groups.forEach(
                                    group -> {
                                        nodes.add(new KahnsSort.Node(replacement.id, group));
                                        if (!groupList.contains(group)) groupList.add(group);
                                    });

                            if (!groupList.contains(replacement.id)) groupList.add(replacement.id);
                        });

        KahnsSort.Graph graph = new KahnsSort.Graph(nodes, groupList);
        List<Integer> dynamicGroupsOrdered = KahnsSort.doSort(graph);

        dynamicGroupsOrdered.forEach(
                group -> {
                    if (GameData.getGroupReplacements().containsKey((int) group)) {
                        var data = GameData.getGroupReplacements().get((int) group);
                        // An earlier replacement in the sorted order may already have unloaded this one.
                        var sceneGroupReplacement =
                                this.loadedGroups.stream().filter(g -> g.id == group).findFirst().orElse(null);
                        if (sceneGroupReplacement != null && sceneGroupReplacement.is_replaceable != null) {
                            var it = data.replace_groups.iterator();
                            while (it.hasNext()) {
                                var replace_group = it.next();
                                if (!sceneGroupsIds.contains(replace_group)) continue;

                                SceneGroup sceneGroup = sceneGroupMap.get(replace_group);
                                if (sceneGroup != null
                                        && sceneGroup.is_replaceable != null
                                        && ((sceneGroup.is_replaceable.value
                                                        && sceneGroup.is_replaceable.version
                                                                <= sceneGroupReplacement.is_replaceable.version)
                                                || sceneGroup.is_replaceable.new_bin_only)) {
                                    this.unloadGroup(
                                            scriptManager.getBlocks().get(sceneGroup.block_id), replace_group);
                                    Grasscutter.getLogger().debug("Graph ordering: unloaded {}", replace_group);
                                }
                            }
                        }
                    }
                });
    }

    public void loadTriggerFromGroup(SceneGroup group, String triggerName) {

        this.getScriptManager()
                .registerTrigger(
                        group.triggers.values().stream()
                                .filter(p -> p.getName().contains(triggerName))
                                .toList());
        group.regions.values().stream()
                .filter(q -> q.config_id == Integer.parseInt(triggerName.substring(13)))
                .map(region -> new EntityRegion(this, region))
                .forEach(getScriptManager()::registerRegion);
    }

    public void onLoadGroup(List<SceneGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }

        for (var group : groups) {
            if (this.loadedGroups.contains(group)) continue;

            this.getScriptManager().loadGroupFromScript(group);
            if (!this.scriptManager.getLoadedGroupSetPerBlock().containsKey(group.block_id))
                this.onLoadBlock(scriptManager.getBlocks().get(group.block_id), players);
            this.scriptManager.getLoadedGroupSetPerBlock().get(group.block_id).add(group);
        }

        var entities = new ArrayList<GameEntity>();
        for (var group : groups) {
            if (this.loadedGroups.contains(group)) continue;

            if (group.init_config == null) {
                continue;
            }

            var groupInstance = this.getScriptManager().getGroupInstanceById(group.id);
            var cachedInstance = this.getScriptManager().getCachedGroupInstanceById(group.id);
            if (cachedInstance != null) {
                cachedInstance.setLuaGroup(group);
                groupInstance = cachedInstance;
            }

            emu.grasscutter.game.dailytask.DailyTaskGroupLifecycle.load(
                    this.getScriptManager(), groupInstance,
                    emu.grasscutter.game.quest.LiyueSceneGroupRecovery.selectSuite(this, groupInstance));

            this.loadedGroups.add(group);
        }

        this.scriptManager.meetEntities(entities);
        groups.forEach(g -> emu.grasscutter.game.dailytask.DailyTaskGroupLifecycle.groupLoad(
                scriptManager, new ScriptArgs(g.id, EventType.EVENT_GROUP_LOAD, g.id)));

        Grasscutter.getLogger().trace("Scene {} loaded {} group(s)", this.getId(), groups.size());
    }

    public void unloadGroup(SceneBlock block, int group_id) {
        List<GameEntity> toRemove =
                this.getEntities().values().stream()
                        .filter(
                                e ->
                                        e != null
                                                && e.getGroupId() == group_id
                                                && (block == null || e.getBlockId() == block.id))
                        .toList();

        if (toRemove.size() > 0) {
            toRemove.forEach(this::removeEntityDirectly);
            this.broadcastPacket(
                    new PacketSceneEntityDisappearNotify(toRemove, VisionType.VisionType_VISION_REMOVE));
            if (getSceneType() == SceneType.SCENE_WORLD) {
                blossomManager.recycleGadgetEntity(toRemove);
            }
        }

        var group =
                block == null || block.groups == null
                        ? this.scriptManager.getGroupById(group_id)
                        : block.groups.get(group_id);
        if (group == null) {
            // The group's script never loaded, so there is nothing to tear down. The entities are
            // already gone above; tell the client the group is unloaded and stop.
            this.broadcastPacket(new PacketGroupUnloadNotify(List.of(group_id)));
            return;
        }

        if (group.triggers != null) {
            group.triggers.values().forEach(getScriptManager()::deregisterTrigger);
        }
        if (group.regions != null) {
            group.regions.values().forEach(getScriptManager()::deregisterRegion);
        }
        if (challenge != null && group.id == challenge.getGroup().id) {
            challenge.fail();
        }

        // A null block means the group's block is gone; only the per-block bookkeeping is skipped.
        if (block != null) {
            var blockGroups = scriptManager.getLoadedGroupSetPerBlock().get(block.id);
            if (blockGroups != null) {
                blockGroups.remove(group);
            }
        }
        this.loadedGroups.remove(group);

        if (block != null && scriptManager.getLoadedGroupSetPerBlock().get(block.id) != null
                && this.scriptManager.getLoadedGroupSetPerBlock().get(block.id).isEmpty()) {
            this.scriptManager.getLoadedGroupSetPerBlock().remove(block.id);
            Grasscutter.getLogger().trace("Scene {} block {} is unloaded.", this.getId(), block.id);
        }

        this.broadcastPacket(new PacketGroupUnloadNotify(List.of(group_id)));
        this.scriptManager.unregisterGroup(group);
    }

    public void onPlayerCreateGadget(EntityClientGadget gadget) {
        var owner = gadget.getOwner();

        this.addEntityDirectly(gadget);
        owner.getTeamManager().getGadgets().add(gadget);

        for (var player : this.getPlayers()) {
            if (player != owner) {
                player.getSession().send(new PacketSceneEntityAppearNotify(gadget));
            }
        }
    }

    public void onPlayerDestroyGadget(int entityId) {
        GameEntity entity = getEntities().get(entityId);

        if (!(entity instanceof EntityClientGadget gadget)) {
            for (var player : this.getPlayers()) {
                player.getTeamManager().getGadgets().removeIf(g -> g.getId() == entityId);
            }
            return;
        }

        this.removeEntityDirectly(gadget);

        var owner = gadget.getOwner();
        owner.getTeamManager().getGadgets().remove(gadget);

        this.broadcastPacket(
                new PacketSceneEntityDisappearNotify(gadget, VisionType.VisionType_VISION_DIE));
    }

    public void broadcastPacket(BasePacket packet) {

        for (Player player : this.getPlayers()) {
            player.getSession().send(packet);
        }
    }

    public void broadcastPacketToOthers(Player excludedPlayer, BasePacket packet) {

        if (this.getPlayerCount() == 1 && this.getPlayers().get(0) == excludedPlayer) {
            return;
        }

        for (Player player : this.getPlayers()) {
            if (player == excludedPlayer) {
                continue;
            }

            player.getSession().send(packet);
        }
    }

    public void addItemEntity(int itemId, int amount, GameEntity bornForm) {
        addEntities(prepareItemEntities(itemId, amount, bornForm));
    }

    /** Construct and validate every pickup before any entity is added to the scene. */
    public List<EntityItem> prepareItemEntities(int itemId, int amount, GameEntity bornFrom) {
        ItemData itemData = GameData.getItemDataMap().get(itemId);
        if (itemData == null || itemData.getItemType() == null || amount <= 0
                || bornFrom == null || bornFrom.getScene() != this) {
            throw new IllegalArgumentException("Invalid ground reward: " + itemId + " x" + amount);
        }
        var items = new ArrayList<EntityItem>();
        boolean split = itemData.isEquip() || isSplitGroundDropItem(itemId);
        if (split) {
            float range = 1.5f + (.05f * amount);
            for (int i = 0; i < amount; i++) {
                Position pos = bornFrom.getPosition().nearby2d(range).addZ(.9f);
                items.add(new EntityItem(this, null, itemData, pos, 1));
            }
        } else {
            items.add(new EntityItem(this, null, itemData,
                    bornFrom.getPosition().clone().addZ(.9f), amount));
        }
        // Force serialization before admission, so malformed pickup metadata fails before payouts.
        items.forEach(EntityItem::toProto);
        return List.copyOf(items);
    }

    public void loadNpcForPlayerEnter(Player player) {
        this.npcBornEntrySet.addAll(loadNpcForPlayer(player));
    }

    private List<SceneNpcBornEntry> loadNpcForPlayer(Player player) {
        var pos = player.getPosition();
        var data = GameData.getSceneNpcBornData().get(getId());
        if (data == null) {
            return List.of();
        }

        var npcList =
                SceneIndexManager.queryNeighbors(
                        data.getIndex(),
                        pos.toDoubleArray(),
                        Grasscutter.getConfig().server.game.loadEntitiesForPlayerRange);

        var sceneNpcBornCanidates =
                npcList.stream().filter(i -> !this.npcBornEntrySet.contains(i)).toList();

        List<SceneNpcBornEntry> sceneNpcBornEntries = new ArrayList<>();
        sceneNpcBornCanidates.forEach(
                i -> {
                    var groupInstance = scriptManager.getGroupInstanceById(i.getGroupId());
                    if (groupInstance != null) {
                        if (i.getSuiteIdList() != null
                                && !i.getSuiteIdList()
                                        .contains(groupInstance.getActiveSuiteId())) {
                            return;
                        }
                    } else {
                        // Server is missing many late-region Lua groups (Fontaine+/Natlan SotS).
                        // Client still has them locally - GroupSuiteNotify alone loads the NPC.
                        // Skipping here left goddess statues with no F tip after unlock.
                        if (i.getSuiteIdList() == null || i.getSuiteIdList().isEmpty()) {
                            return;
                        }
                    }
                    sceneNpcBornEntries.add(i);
                });

        if (sceneNpcBornEntries.size() > 0) {
            this.broadcastPacket(new PacketGroupSuiteNotify(sceneNpcBornEntries));
            Grasscutter.getLogger().trace("Loaded Npc Group Suite {}", sceneNpcBornEntries);
        }

        return npcList.stream()
                .filter(i -> this.npcBornEntrySet.contains(i) || sceneNpcBornEntries.contains(i))
                .toList();
    }

    public void loadGroupForQuest(List<QuestGroupSuite> sceneGroupSuite) {
        if (!scriptManager.isInit()) {
            return;
        }

        sceneGroupSuite.forEach(
                i -> {
                    var group = scriptManager.getGroupById(i.getGroup());
                    if (group == null) return;

                    var groupInstance = scriptManager.getGroupInstanceById(i.getGroup());
                    var suite = group.getSuiteByIndex(i.getSuite());
                    if (suite == null || groupInstance == null) {
                        return;
                    }

                    scriptManager.refreshGroup(groupInstance, i.getSuite(), false);
                });
    }

    public void unlockForce(int force) {
        this.unlockedForces.add(force);
        this.broadcastPacket(new PacketSceneForceUnlockNotify(force, true));
    }

    public void lockForce(int force) {
        this.unlockedForces.remove(force);
        this.broadcastPacket(new PacketSceneForceLockNotify(force));
    }

    public void selectWorktopOptionWith(SelectWorktopOptionReqOuterClass.SelectWorktopOptionReq req) {
        GameEntity entity = getEntityById(req.getGadgetEntityId());
        if (entity == null) {
            return;
        }

        if (entity instanceof EntityGadget gadget) {
            if (gadget.getContent() instanceof GadgetWorktop worktop) {
                boolean shouldDelete = worktop.onSelectWorktopOption(req);
                if (shouldDelete) {
                    entity.getScene().removeEntity(entity, VisionType.VisionType_VISION_REMOVE);
                }
            }
        }
    }

    public void saveGroups() {
        emu.grasscutter.game.dailytask.DailyTaskGroupLifecycle.captureAll(this);
        this.getScriptManager().getCachedGroupInstances().values().forEach(SceneGroupInstance::save);
    }
}
