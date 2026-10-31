package emu.grasscutter.game.player;

import dev.morphia.annotations.*;
import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.PlayerLevelData;
import emu.grasscutter.data.excels.world.WeatherData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.*;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.ArlecchinoBurstBoL;
import emu.grasscutter.game.achievement.Achievements;
import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.game.avatar.*;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.city.CityInfoData;
import emu.grasscutter.game.dailytask.DailyTaskManager;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.expedition.ExpeditionInfo;
import emu.grasscutter.game.friends.*;
import emu.grasscutter.game.gacha.PlayerGachaInfo;
import emu.grasscutter.game.home.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.mail.*;
import emu.grasscutter.game.managers.*;
import emu.grasscutter.game.managers.cooking.*;
import emu.grasscutter.game.managers.deforestation.DeforestationManager;
import emu.grasscutter.game.managers.energy.EnergyManager;
import emu.grasscutter.game.managers.forging.*;
import emu.grasscutter.game.managers.mapmark.*;
import emu.grasscutter.game.managers.stamina.StaminaManager;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.ForcedQuests;
import emu.grasscutter.game.quest.QuestManager;
import emu.grasscutter.game.quest.enums.*;
import emu.grasscutter.game.shop.ShopLimit;
import emu.grasscutter.game.talk.TalkManager;
import emu.grasscutter.game.tower.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.CombatInvokeEntryOuterClass.CombatInvokeEntry;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.MpSettingTypeOuterClass.MpSettingType;
import emu.grasscutter.net.proto.OnlinePlayerInfoOuterClass.OnlinePlayerInfo;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.PlayerLocationInfoOuterClass.PlayerLocationInfo;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.net.proto.SocialDetailOuterClass.SocialDetail;
import emu.grasscutter.plugin.api.PlayerHook;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.data.SceneRegion;
import emu.grasscutter.server.event.player.*;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.net.proto.AbilityScalarTypeOuterClass.AbilityScalarType;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.*;
import emu.grasscutter.utils.helpers.DateHelper;
import emu.grasscutter.utils.objects.FieldFetch;
import it.unimi.dsi.fastutil.ints.*;

import lombok.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

@Entity(value = "players", useDiscriminator = false)
public class Player implements PlayerHook, FieldFetch {
    @Id private int id;
    @Indexed(options = @IndexOptions(unique = true))
    @Getter private String accountId;
    @Setter private transient Account account;
    @Getter @Setter private transient GameSession session;
    @Transient private String sessionKey;

    @Getter private String nickname;
    @Getter private String signature;
    @Getter private int headImage;
    @Getter private int profileFrameId = 100011;
    @Getter private Map<Integer, Set<Integer>> sceneTags;
    @Getter private int nameCardId = 210001;
    @Getter private Position position;
    @Getter @Setter private Position prevPos;
    @Getter @Setter private Position prevPosForHome;
    @Getter @Setter private int prevScene;
    // Written and read only as a pair, unlike prevScene, which entering a home also overwrites.
    @Getter @Setter private Position prevPosForDungeon;
    @Getter @Setter private int prevSceneForDungeon;
    @Getter private Position rotation;
    @Getter private PlayerBirthday birthday;
    @Getter private PlayerCodex codex;
    @Getter @Setter private boolean showAvatars;
    @Getter @Setter private boolean showConstellationNum;
    @Getter @Setter private List<Integer> showAvatarList;
    @Getter @Setter private List<Integer> showNameCardList;
    @Getter private Map<Integer, Integer> properties;
    @Getter @Setter private int currentRealmId;
    @Getter @Setter private transient boolean isInEditMode;
    @Getter @Setter private int widgetId;
    @Getter @Setter private int sceneId;
    @Getter @Setter private int regionId;
    @Getter private int mainCharacterId;
    @Getter @Setter private boolean inGodMode;
    @Getter @Setter private boolean unlimitedStamina;

    @Getter private Set<Integer> nameCardList;
    @Getter private Set<Integer> flyCloakList;
    @Getter private Set<Integer> traceEffectList;
    @Getter private Set<Integer> costumeList;
    @Getter private Set<Integer> weaponSkinList;
    @Getter private Set<Integer> personalLineList;
    @Getter @Setter private Set<Integer> rewardedLevels;
    @Getter @Setter private Set<Integer> homeRewardedLevels;
    @Getter @Setter private Set<Integer> realmList;
    @Getter @Setter private Set<Integer> seenRealmList;
    @Getter private Set<Integer> unlockedForgingBlueprints;
    @Getter private Set<Integer> unlockedCombines;
    @Getter private Set<Integer> unlockedFurniture;
    @Getter private Set<Integer> unlockedFurnitureSuite;
    @Getter private Map<Long, ExpeditionInfo> expeditionInfo;
    @Getter private Map<Integer, Integer> unlockedRecipies;
    @Getter private List<ActiveForgeData> activeForges;
    @Getter private Map<Integer, ActiveCookCompoundData> activeCookCompounds;
    @Getter private Map<Integer, Integer> questGlobalVariables;
    @Getter private Map<Integer, Integer> openStates;
    // TPS ammunition reserve, keyed by TpsAmmunitionExcelConfigData id.
    private Map<Integer, Integer> tpsAmmunition;
    // TPS weapons the TPS traveler wears; it is a trial avatar and is never saved itself.
    private List<Integer> tpsLoadout;
    // Snezhnaya Ice Creatures this player built.
    private List<emu.grasscutter.game.icecreature.IceCreatureRecord> iceCreatures;
    /** The look the Ice Creature build wheel uses per creature type, as the client last chose. */
    private Map<Integer, Integer> iceCreatureTypeGadgets;
    @Getter @Setter private Map<Integer, Set<Integer>> unlockedSceneAreas;
    /** Map area states a quest changed with QUEST_EXEC_CHANGE_MAP_AREA_STATE, by map area id. */
    private Map<Integer, Boolean> mapAreaStates;
    @Getter @Setter private Map<Integer, Set<Integer>> unlockedScenePoints;
    /** Separate first-entry reward receipts; handbook unlocks do not establish discovery. */
    private Map<Integer, Set<Integer>> rewardedDungeonEntries;
    /** First activations whose independent LootCSS reward has not been settled. */
    private Map<Integer, Set<Integer>> pendingTransPointRewards;
    /** Frozen first-activation payouts, keyed by scene.point; an empty map is intentional zero. */
    private Map<String, Map<Integer, Integer>> transPointRewardSnapshots;
    /** Debug/test: points forced locked even if GetScenePointRsp would otherwise unlock-all. */
    @Getter @Setter private Map<Integer, Set<Integer>> forceLockedScenePoints;
    @Getter @Setter private List<Integer> chatEmojiIdList;
    @Getter @Setter private boolean playedFirstLoginCutscene;

    /**
     * Whether this player has yet to see the welcome notice.
     *
     * <p>Defaults to true, so a player whose document predates this field sees the notice once.
     */
    @Getter @Setter private boolean pendingWelcomeNotice = true;

    /**
     * The server build this player last entered the world on, so a restart onto a new build can say
     * so once. Null until they have entered at least once.
     */
    @Getter @Setter private String lastSeenBuildHash;
    /** Whether EntryNotice has run for this login. */
    @Transient @Getter @Setter private boolean entryNoticeChecked;
    @Getter @Setter private int lastBirthdayMailYear;
    private Set<Integer> forcedFinishedQuests;
    /** SotS goddess GroupSuiteNotify debounce (groupId) — not persisted. */
    @Transient @Getter
    private final Set<Integer> goddessSuiteNotified = ConcurrentHashMap.newKeySet();
    /**
     * Spawned statue worktop proxy entity ids by pointId (may be several for thick pillars) —
     * not persisted.
     */
    @Transient @Getter
    private final Map<Integer, java.util.List<Integer>> goddessNpcEntityByPoint =
            new ConcurrentHashMap<>();
    @Transient private long nextGuid = 0;
    @Transient @Getter @Setter private int peerId;
    // volatile rather than guarded by the player lock: Scene and World set these while holding
    // their own locks, and Player.onTick holds the player lock while it reaches into the scene.
    @Transient private volatile World world;
    @Transient @Getter @Setter private HomeWorld curHomeWorld;
    @Transient @Getter @Setter private boolean hasSentInitPacketInHome;
    @Transient private volatile Scene scene;
    @Transient @Getter private int weatherId = 0;
    @Transient @Getter private ClimateType climate = ClimateType.CLIMATE_SUNNY;
    @Transient @Getter private int areaId = 0;
    @Transient @Getter private int areaType = 0;

    @Getter private transient AvatarStorage avatars;
    @Getter private transient Inventory inventory;
    @Getter private transient FriendsList friendsList;
    @Getter private transient MailHandler mailHandler;
    @Getter private transient AbilityManager abilityManager;
    @Getter private transient QuestManager questManager;
    @Getter private transient TowerManager towerManager;
    @Getter private transient SotSManager sotsManager;
    @Getter private transient MapMarksManager mapMarksManager;
    @Getter private transient StaminaManager staminaManager;
    @Getter private transient EnergyManager energyManager;
    @Getter private transient ResinManager resinManager;
    @Getter private transient ForgingManager forgingManager;
    @Getter private transient DeforestationManager deforestationManager;
    @Getter private transient FurnitureManager furnitureManager;
    @Getter private transient BattlePassManager battlePassManager;
    @Getter private transient DailyTaskManager dailyTaskManager;
    @Getter private transient CookingManager cookingManager;
    @Getter private transient CookingCompoundManager cookingCompoundManager;
    @Getter private transient ActivityManager activityManager;
    @Getter private transient PlayerBuffManager buffManager;
    @Getter private transient PlayerProgressManager progressManager;
    @Getter private transient SatiationManager satiationManager;
    @Getter private transient TalkManager talkManager;

    @Getter @Setter private transient Position lastCheckedPosition = null;

    @Getter private transient Achievements achievements;
    private PlayerProfile playerProfile;
    @Getter private TeamManager teamManager;
    private TowerData towerData;
    @Getter private PlayerGachaInfo gachaInfo;
    private PlayerCollectionRecords collectionRecordStore;
    @Getter private ArrayList<ShopLimit> shopLimit;

    @Getter private transient GameHome home;

    @Setter private boolean moonCard;
    @Getter @Setter private Date moonCardStartTime;
    @Getter @Setter private int moonCardDuration;
    @Getter @Setter private Set<Date> moonCardGetTimes;

    @Transient @Getter private boolean paused;
    @Transient @Getter @Setter private Future<?> queuedTeleport;
    @Transient @Getter @Setter private int enterSceneToken;
    @Transient @Getter @Setter private SceneLoadState sceneLoadState = SceneLoadState.NONE;
    @Transient private boolean hasSentLoginPackets;
    @Transient private long nextSendPlayerLocTime = 0;
    @Getter private transient final Int2ObjectMap<EnterHomeRequest> enterHomeRequests;

    private transient final Int2ObjectMap<CoopRequest> coopRequests;
    @Getter private transient final Queue<AttackResult> attackResults;
    @Getter private transient final InvokeHandler<CombatInvokeEntry> combatInvokeHandler;
    @Getter private transient final InvokeHandler<AbilityInvokeEntry> abilityInvokeHandler;
    @Getter private transient final InvokeHandler<AbilityInvokeEntry> clientAbilityInitFinishHandler;

    @Getter @Setter private long springLastUsed;
    private HashMap<String, MapMark> mapMarks;
    @Getter @Setter private int nextResinRefresh;
    @Getter @Setter private int resinBuyCount;
    private WeeklyBossClaimState weeklyBossClaimState;

    public synchronized WeeklyBossClaimState getWeeklyBossClaimState() {
        if (weeklyBossClaimState == null) weeklyBossClaimState = new WeeklyBossClaimState();
        return weeklyBossClaimState;
    }

    @Getter @Setter private int lastDailyReset;
    @Getter private transient MpSettingType mpSetting = MpSettingType.MpSettingType_MP_SETTING_ENTER_AFTER_APPLY;
    @Getter private long playerGameTime = 540000;

    @Getter private PlayerProgress playerProgress;
    @Getter private Set<Integer> activeQuestTimers;

    @Getter @Setter private ElementType mainCharacterElement = ElementType.None;

    @Getter @Setter private Map<Integer, CityInfoData> cityInfoData;
    @Getter private float phlogistonValue = 100.0f; // 燃素值

    @Deprecated
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Player() {
        this.inventory = new Inventory(this);
        this.avatars = new AvatarStorage(this);
        this.friendsList = new FriendsList(this);
        this.mailHandler = new MailHandler(this);
        this.towerManager = new TowerManager(this);
        this.abilityManager = new AbilityManager(this);
        this.deforestationManager = new DeforestationManager(this);
        this.questManager = new QuestManager(this);
        this.buffManager = new PlayerBuffManager(this);
        this.position = new Position(GameConstants.START_POSITION);
        this.prevPos = new Position();
        this.prevPosForHome = Position.ZERO;
        this.rotation = new Position(0, 307, 0);
        this.sceneId = 3;
        this.regionId = 1;
        this.properties = new HashMap<>();
        for (PlayerProperty prop : PlayerProperty.values()) {
            if (prop.getId() < 10000) {
                continue;
            }
            this.properties.put(prop.getId(), 0);
        }

        this.gachaInfo = new PlayerGachaInfo();
        this.nameCardList = new HashSet<>();
        this.flyCloakList = new HashSet<>();
        this.traceEffectList = new HashSet<>();
        this.costumeList = new HashSet<>();
        this.weaponSkinList = new HashSet<>();
        this.personalLineList = new HashSet<>();
        this.towerData = new TowerData();
        this.collectionRecordStore = new PlayerCollectionRecords();
        this.unlockedForgingBlueprints = new HashSet<>();
        this.unlockedCombines = new HashSet<>();
        this.forcedFinishedQuests = new HashSet<>();
        this.unlockedFurniture = new HashSet<>();
        this.unlockedFurnitureSuite = new HashSet<>();
        this.activeCookCompounds = new HashMap<>();
        this.activeForges = new ArrayList<>();
        this.unlockedRecipies = new HashMap<>();
        this.questGlobalVariables = new HashMap<>();
        this.openStates = new HashMap<>();
        this.sceneTags = new HashMap<>();
        this.unlockedSceneAreas = new HashMap<>();
        this.unlockedScenePoints = new HashMap<>();
        this.rewardedDungeonEntries = new HashMap<>();
        this.pendingTransPointRewards = new HashMap<>();
        this.transPointRewardSnapshots = new HashMap<>();
        this.forceLockedScenePoints = new HashMap<>();
        this.chatEmojiIdList = new ArrayList<>();
        this.playerProgress = new PlayerProgress();
        this.activeQuestTimers = new HashSet<>();
        this.cityInfoData = new HashMap<>();

        this.attackResults = new LinkedBlockingQueue<>();
        this.coopRequests = new Int2ObjectOpenHashMap<>();
        this.enterHomeRequests = new Int2ObjectOpenHashMap<>();
        this.combatInvokeHandler = new InvokeHandler(PacketCombatInvocationsNotify.class);
        this.abilityInvokeHandler = new InvokeHandler(PacketAbilityInvocationsNotify.class);
        this.clientAbilityInitFinishHandler = new InvokeHandler(PacketClientAbilityInitFinishNotify.class);

        this.birthday = new PlayerBirthday();
        this.rewardedLevels = new HashSet<>();
        this.homeRewardedLevels = new HashSet<>();
        this.seenRealmList = new HashSet<>();
        this.moonCardGetTimes = new HashSet<>();
        this.codex = new PlayerCodex(this);
        this.progressManager = new PlayerProgressManager(this);
        this.shopLimit = new ArrayList<>();
        this.expeditionInfo = new HashMap<>();
        this.mapMarksManager = new MapMarksManager(this);
        this.staminaManager = new StaminaManager(this);
        this.sotsManager = new SotSManager(this);
        this.energyManager = new EnergyManager(this);
        this.resinManager = new ResinManager(this);
        this.forgingManager = new ForgingManager(this);
        this.progressManager = new PlayerProgressManager(this);
        this.furnitureManager = new FurnitureManager(this);
        this.cookingManager = new CookingManager(this);
        this.cookingCompoundManager = new CookingCompoundManager(this);
        this.satiationManager = new SatiationManager(this);
        this.talkManager = new TalkManager(this);
        setPhlogistonValue(100);
    }

    public Player(GameSession session) {
        this();

        this.account = session.getAccount();
        this.accountId = this.getAccount().getId();
        this.session = session;
        this.nickname = "Traveler";
        this.signature = "";
        this.teamManager = new TeamManager(this);
        this.birthday = new PlayerBirthday();
        this.codex = new PlayerCodex(this);

        this.applyProperties();
        this.applyStartingSceneTags();
        this.getFlyCloakList().add(140001);
        for (int __nc = 210001; __nc <= 210100; __nc++) { this.getNameCardList().add(__nc); }
        setPhlogistonValue(100);
        for(int t=0; t < 20; t++){
            this.getTraceEffectList().add(215001+t);
        }
        this.mapMarksManager = new MapMarksManager(this);
        this.staminaManager = new StaminaManager(this);
        this.sotsManager = new SotSManager(this);
        this.energyManager = new EnergyManager(this);
        this.resinManager = new ResinManager(this);
        this.deforestationManager = new DeforestationManager(this);
        this.forgingManager = new ForgingManager(this);
        this.progressManager = new PlayerProgressManager(this);
        this.furnitureManager = new FurnitureManager(this);
        this.cookingManager = new CookingManager(this);
        this.cookingCompoundManager = new CookingCompoundManager(this);
        this.satiationManager = new SatiationManager(this);
    }

    @Override
    public Player getPlayer() {
        return this;
    }
    public void setPhlogistonValue(float value) {
        if (value < 0) value = 0;
        else if (value > 100) value = 100;

        if (value == this.phlogistonValue) return;

        this.phlogistonValue = value;
    }

    public float addPhlogistonValue(float amount) {
        setPhlogistonValue(getPhlogistonValue() + amount);
        return getPhlogistonValue();

    }

    public void updatePlayerGameTime(long gameTime) {
        if (this.playerGameTime == gameTime) return;

        this.playerGameTime = gameTime;

        var world = this.getWorld();
        if (world != null && world.getHost() == this) {
            world.changeTime(gameTime);
        }

        this.save();
    }

    public int getUid() {
        return id;
    }

    public void setUid(int id) {
        this.id = id;
    }

    public long getNextGameGuid() {
        long nextId = ++this.nextGuid;
        return ((long) this.getUid() << 32) + nextId;
    }

    public Account getAccount() {
        if (this.account == null)
            this.account = DatabaseHelper.getAccountById(this.accountId);
        return this.account;
    }

    public String getSessionKey() {
        if (this.sessionKey == null) {

            if (this.account == null) {
                this.account = DispatchUtils.getAccountById(this.getAccountId());
            }
            if (this.account == null) return "";

            this.sessionKey = this.getAccount().getSessionKey();
        }

        return this.sessionKey;
    }

    public boolean isOnline() {
        return this.getSession() != null && this.getSession().isActive();
    }

    public GameServer getServer() {
        return this.getSession().getServer();
    }

    public World getWorld() {
        return this.world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public Scene getScene() {
        return scene;
    }

    /** Check location, not only scene ID: Natlan shares Teyvat's scene 3. */
    public boolean isInNatlan() {
        return this.scene != null && PhlogistonRegion.contains(this.scene.getId(), this.position);
    }

    /** Update both client embryos and server ability IDs on a seamless regional crossing. */
    public void refreshPhlogistonRegion() {
        if (this.scene == null || this.session == null || this.teamManager == null) return;
        boolean inNatlan = this.isInNatlan();
        int enabled = inNatlan ? 1 : 0;
        if (this.getProperty(PlayerProperty.PROP_PHLOGISTON_ENABLE) == enabled) return;

        this.setProperty(PlayerProperty.PROP_PHLOGISTON_ENABLE, enabled);
        for (var avatar : this.teamManager.getActiveTeam()) {
            if (avatar == null) continue;
            this.abilityManager.syncPhlogistonAvatarAbility(avatar, inNatlan);
            this.sendPacket(new PacketAbilityChangeNotify(avatar));
        }
        var teamEntity = this.teamManager.getEntity();
        if (teamEntity != null) {
            this.sendPacket(new PacketAbilityChangeNotify(
                    teamEntity.getId(), this.teamManager.getAbilityControlBlock()));
            if (inNatlan) {
                this.sendPacket(new PacketServerGlobalValueChangeNotify(
                        teamEntity.getId(), "SGV_PlayerTeam_Phlogiston", this.getPhlogistonValue()));
            }
        }
    }

    public void setScene(Scene scene) {
        this.scene = scene;
    }

    synchronized public void setClimate(ClimateType climate) {
        this.climate = climate;
        this.session.send(new PacketSceneAreaWeatherNotify(this));
    }

    public synchronized void setWeather(int weather) {
        this.setWeather(weather, ClimateType.CLIMATE_NONE);
    }

    public synchronized void setWeather(int weatherId, ClimateType climate) {

        if (climate == ClimateType.CLIMATE_NONE) {
            WeatherData w = GameData.getWeatherDataMap().get(weatherId);
            if (w != null) {
                climate = w.getDefaultClimate();
            }
        }
        this.weatherId = weatherId;
        this.climate = climate;
        this.session.send(new PacketSceneAreaWeatherNotify(this));
    }

    public void setArea(int areaId, int areaType) {
        this.areaId = areaId;
        this.areaType = areaType;

        var event = new PlayerEnterAreaEvent(this);
        event.call();
    }

    public void setNickname(String nickName) {
        this.nickname = nickName;
        this.updateProfile();
    }

    public void setHeadImage(int picture) {
        this.headImage = picture;
        this.updateProfile();
        this.save();
    }

    public void setProfileFrameId(int frameId) {
        this.profileFrameId = frameId;
        this.updateProfile();
        this.save();
    }

    public void setSignature(String signature) {
        this.signature = signature;
        this.updateProfile();
    }

    public void addRealmList(int realmId) {
        if (this.realmList == null) {
            this.realmList = new HashSet<>();
        } else if (this.realmList.contains(realmId)) {
            return;
        }
        this.realmList.add(realmId);

        if (realmId > 3) {
            this.sendPacket(new PacketHomeModuleUnlockNotify(realmId));
            this.getHome().onClaimReward(this);
        }
    }

    public void addSeenRealmList(int seenId) {
        if (this.seenRealmList == null) {
            this.seenRealmList = new HashSet<>();
        } else if (this.seenRealmList.contains(seenId)) {
            return;
        }
        this.seenRealmList.add(seenId);
    }

    public Optional<GameHome> tryGetHome() {
        if (this.isOnline()) {
            return Optional.ofNullable(this.home);
        }

        if (GameHome.doesHomeExist(this.getUid())) {
            this.home = GameHome.getByUid(this.getUid());
        }

        return Optional.ofNullable(this.home);
    }

    public int getExpeditionLimit() {
        final int CONST_VALUE_EXPEDITION_INIT_LIMIT = 2;
        int expeditionLimit = CONST_VALUE_EXPEDITION_INIT_LIMIT;
        var levelMap = GameData.getPlayerLevelDataMap();
        for (int i = 1; i <= this.getLevel(); i++) {
            var data = levelMap.get(i);
            if (data != null)
                expeditionLimit += data.getExpeditionLimitAdd();
        }
        return expeditionLimit;
    }

    public Set<Integer> getUnlockedSceneAreas(int sceneId) {
        return this.unlockedSceneAreas.computeIfAbsent(sceneId, i -> new CopyOnWriteArraySet<>());
    }

    public Map<Integer, Boolean> getMapAreaStates() {
        if (this.mapAreaStates == null) this.mapAreaStates = new HashMap<>();
        return this.mapAreaStates;
    }

    public Set<Integer> getUnlockedScenePoints(int sceneId) {
        return this.unlockedScenePoints.computeIfAbsent(sceneId, i -> new CopyOnWriteArraySet<>());
    }

    public Map<Integer, Set<Integer>> getPendingTransPointRewards() {
        if (this.pendingTransPointRewards == null) {
            this.pendingTransPointRewards = new HashMap<>();
        }
        return this.pendingTransPointRewards;
    }

    public Map<String, Map<Integer, Integer>> getTransPointRewardSnapshots() {
        if (this.transPointRewardSnapshots == null) this.transPointRewardSnapshots = new HashMap<>();
        return this.transPointRewardSnapshots;
    }

    public Set<Integer> getPendingTransPointRewards(int sceneId) {
        return getPendingTransPointRewards().computeIfAbsent(sceneId, i -> new CopyOnWriteArraySet<>());
    }

    public Set<Integer> getRewardedDungeonEntries(int sceneId) {
        if (this.rewardedDungeonEntries == null) {
            this.rewardedDungeonEntries = new HashMap<>();
        }
        return this.rewardedDungeonEntries.computeIfAbsent(sceneId, i -> new CopyOnWriteArraySet<>());
    }

    public Set<Integer> getForceLockedScenePoints(int sceneId) {
        if (this.forceLockedScenePoints == null) {
            this.forceLockedScenePoints = new HashMap<>();
        }
        return this.forceLockedScenePoints.computeIfAbsent(sceneId, i -> new CopyOnWriteArraySet<>());
    }

    public boolean isScenePointForceLocked(int sceneId, int pointId) {
        if (this.forceLockedScenePoints == null) {
            return false;
        }
        var locked = this.forceLockedScenePoints.get(sceneId);
        return locked != null && locked.contains(pointId);
    }

    public int getLevel() {
        return this.getProperty(PlayerProperty.PROP_PLAYER_LEVEL);
    }

    public boolean setLevel(int level) {
        if (this.getLevel() == level) {
            return true;
        }

        if (this.setProperty(PlayerProperty.PROP_PLAYER_LEVEL, level)) {

            this.updateWorldLevel();
            this.updateProfile();

            this.getProgressManager().tryUnlockOpenStates();
            this.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_PLAYER_LEVEL_UP, level);
            this.getQuestManager().queueEvent(QuestCond.QUEST_COND_PLAYER_LEVEL_EQUAL_GREATER, level);
            // Keep ley-line markers synchronized after any Adventure Rank change,
            // including downward changes and direct jumps across unlock thresholds.
            if (this.scene != null && this.scene.getBlossomManager() != null) {
                this.scene.getBlossomManager().notifyIcon(this);
            }
            if (level >= emu.grasscutter.game.dailytask.DailyTaskManager.UNLOCK_LEVEL
                    && this.getDailyTaskManager() != null) {
                this.getDailyTaskManager().ensureDailyTasksForToday();
            }

            return true;
        }
        return false;
    }

    public int getExp() {
        return this.getProperty(PlayerProperty.PROP_PLAYER_EXP);
    }

    public int getWorldLevel() {
        return this.getProperty(PlayerProperty.PROP_PLAYER_WORLD_LEVEL);
    }

    public boolean setWorldLevel(int level) {
        if (this.setProperty(PlayerProperty.PROP_PLAYER_WORLD_LEVEL, level)) {
            if (this.world.getHost() == this)
                this.world.setWorldLevel(level);
            this.updateProfile();
            return true;
        }
        return false;
    }

    public int getForgePoints() {
        return this.getProperty(PlayerProperty.PROP_PLAYER_FORGE_POINT);
    }

    public boolean setForgePoints(int value) {
        if (value == this.getForgePoints()) {
            return true;
        }

        return this.setProperty(PlayerProperty.PROP_PLAYER_FORGE_POINT, value);
    }

    private void applyProperties() {
        var withQuesting = GAME_OPTIONS.questing.enabled;

        this.setOrFetch(PlayerProperty.PROP_PLAYER_LEVEL, 1);
        this.setOrFetch(PlayerProperty.PROP_IS_SPRING_AUTO_USE, 1);
        this.setOrFetch(PlayerProperty.PROP_SPRING_AUTO_USE_PERCENT, 50);
        // The statue's spring volume is only ever written by SotSManager.refillSpringVolume(),
        // which runs off the auto-recover timer that starts on entering a statue region. Without
        // these the client's 神像的恩泽 panel reads 0/0 and offers nothing to heal, and
        // checkAndHealAvatar() can never heal because the balance stays at zero.
        // Statue healing no longer charges the spring volume (see SotSManager), so the balance is
        // display-only. Set it outright rather than with setOrFetch: setOrFetch keeps a previously
        // saved non-zero value, which left a half-drained balance creeping back up over minutes
        // instead of reading full. MAX first, because CUR's sanity check clamps against it.
        this.setProperty(PlayerProperty.PROP_MAX_SPRING_VOLUME,
            PlayerProperty.PROP_MAX_SPRING_VOLUME.getMax(), false);
        this.setProperty(PlayerProperty.PROP_CUR_SPRING_VOLUME,
            PlayerProperty.PROP_MAX_SPRING_VOLUME.getMax(), false);
        this.setOrFetch(PlayerProperty.PROP_IS_FLYABLE,
            withQuesting ? 0 : 1);
        this.setOrFetch(PlayerProperty.PROP_PLAYER_CAN_DIVE,
                withQuesting ? 0 : 1);
        this.setOrFetch(PlayerProperty.PROP_IS_TRANSFERABLE, 1);
        this.setOrFetch(PlayerProperty.PROP_MAX_STAMINA,
            withQuesting ? 10000 : 24000);
        this.setOrFetch(PlayerProperty.PROP_DIVE_MAX_STAMINA,
                withQuesting ? 10000 : 0);
        this.setOrFetch(PlayerProperty.PROP_PLAYER_RESIN, 200);
        this.setOrFetch(PlayerProperty.PROP_PHLOGISTON_MAX_VALUE, 10000);
        // The active scene enables phlogiston when the player actually enters Natlan.
        this.setProperty(PlayerProperty.PROP_PHLOGISTON_ENABLE, 0, false);

        this.setProperty(PlayerProperty.PROP_CUR_PERSIST_STAMINA,
            this.getProperty(PlayerProperty.PROP_MAX_STAMINA));
        this.setProperty(PlayerProperty.PROP_DIVE_CUR_STAMINA,
                this.getProperty(PlayerProperty.PROP_DIVE_MAX_STAMINA));
        this.setProperty(PlayerProperty.PROP_CUR_PHLOGISTON,
            this.getProperty(PlayerProperty.PROP_PHLOGISTON_MAX_VALUE));
    }

    private void applyStartingSceneTags() {
        GameData.getSceneTagDataMap().values().stream()
                .filter(sceneTag -> sceneTag.isDefaultValid())
                .forEach(sceneTag -> {
                    if (this.getSceneTags().get(sceneTag.getSceneId()) == null) {
                        this.getSceneTags().put(sceneTag.getSceneId(), new HashSet<>());
                    }
                    this.getSceneTags().get(sceneTag.getSceneId()).add(sceneTag.getId());
                });
    }

    private void setOrFetch(PlayerProperty property, int defaultValue) {
        var exists = this.properties.containsKey(property.getId());
        if (exists) exists = this.getProperty(property) != 0;
        this.setProperty(property, exists ? this.getProperty(property)
            : defaultValue, false);
    }

    public int getPrimogems() {
        return this.getProperty(PlayerProperty.PROP_PLAYER_HCOIN);
    }

    public boolean setPrimogems(int primogem) {
        return this.setProperty(PlayerProperty.PROP_PLAYER_HCOIN, primogem);
    }

    public int getMora() {
        return this.getProperty(PlayerProperty.PROP_PLAYER_SCOIN);
    }

    public boolean setMora(int mora) {
        return this.setProperty(PlayerProperty.PROP_PLAYER_SCOIN, mora);
    }

    public int getCrystals() {
        return this.getProperty(PlayerProperty.PROP_PLAYER_MCOIN);
    }

    public boolean setCrystals(int crystals) {
        return this.setProperty(PlayerProperty.PROP_PLAYER_MCOIN, crystals);
    }

    public int getHomeCoin() {
        return this.getProperty(PlayerProperty.PROP_PLAYER_HOME_COIN);
    }

    public boolean setHomeCoin(int coin) {
        return this.setProperty(PlayerProperty.PROP_PLAYER_HOME_COIN, coin);
    }

    private int getExpRequired(int level) {
        PlayerLevelData levelData = GameData.getPlayerLevelDataMap().get(level);
        return levelData != null ? levelData.getExp() : 0;
    }

    private float getExpModifier() {
        return GAME_OPTIONS.rates.adventureExp;
    }

    public void earnExp(int exp) {
        addExpDirectly((int) (exp * getExpModifier()));
    }

    public void addExpDirectly(int gain) {
        int level = getLevel();
        int exp = getExp();
        int reqExp = getExpRequired(level);

        exp += gain;

        while (exp >= reqExp && reqExp > 0) {
            exp -= reqExp;
            level += 1;
            reqExp = getExpRequired(level);

            this.setLevel(level);
        }

        this.setProperty(PlayerProperty.PROP_PLAYER_EXP, exp);
    }

    private void updateWorldLevel() {
        int currentWorldLevel = this.getWorldLevel();
        int currentLevel = this.getLevel();

        int newWorldLevel =
            (currentLevel >= 58) ? 9 :
                (currentLevel >= 55) ? 8 :
                    (currentLevel >= 50) ? 7 :
                        (currentLevel >= 45) ? 6 :
                            (currentLevel >= 40) ? 5 :
                                (currentLevel >= 35) ? 4 :
                                    (currentLevel >= 30) ? 3 :
                                        (currentLevel >= 25) ? 2 :
                                            (currentLevel >= 20) ? 1 :
                                                0;

        if (newWorldLevel != currentWorldLevel) {
            this.setWorldLevel(newWorldLevel);
        }
    }

    private void updateProfile() {
        getProfile().syncWithCharacter(this);
    }

    public boolean isFirstLoginEnterScene() {
        return !this.hasSentLoginPackets;
    }

    public TowerData getTowerData() {
        if (towerData == null) {

            towerData = new TowerData();
        }
        return towerData;
    }

    public void onEnterRegion(SceneRegion region) {
        var enterRegionName = "ENTER_REGION_" + region.config_id;
        this.getQuestManager().forEachActiveQuest(quest -> {
            if (quest.getTriggerData() != null &&
                quest.getTriggers().containsKey(enterRegionName) &&
                region.getGroupId() == quest.getTriggerData().get(enterRegionName).getGroupId()) {

                if (!Boolean.TRUE.equals(quest.getTriggers().put(enterRegionName, true))) {
                    this.getSession().send(new PacketServerCondMeetQuestListUpdateNotify());
                    this.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_TRIGGER_FIRE,
                        quest.getTriggerData().get(enterRegionName).getId(), 0);
                }
            }
        });

    }

    public void onLeaveRegion(SceneRegion region) {
        var leaveRegionName = "LEAVE_REGION_" + region.config_id;
        this.getQuestManager().forEachActiveQuest(quest -> {
            if (quest.getTriggers().containsKey(leaveRegionName) &&
                region.getGroupId() == quest.getTriggerData().get(leaveRegionName).getGroupId()) {

                if (!Boolean.TRUE.equals(quest.getTriggers().put(leaveRegionName, true))) {
                    this.getSession().send(new PacketServerCondMeetQuestListUpdateNotify());
                    this.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_TRIGGER_FIRE,
                        quest.getTriggerData().get(leaveRegionName).getId(), 0);
                }
            }
        });
    }

    public PlayerProfile getProfile() {
        if (this.playerProfile == null) {
            this.playerProfile = new PlayerProfile(this);
        }
        return playerProfile;
    }

    public boolean setProperty(PlayerProperty prop, boolean value) {
        return setPropertyWithSanityCheck(prop, value ? 1 : 0, true);
    }

    public boolean setProperty(PlayerProperty prop, int value) {
        return setPropertyWithSanityCheck(prop, value, true);
    }

    public boolean setProperty(PlayerProperty prop, int value, boolean sendPacket) {
        return setPropertyWithSanityCheck(prop, value, sendPacket);
    }

    public int getProperty(PlayerProperty prop) {
        return getProperties().get(prop.getId());
    }

    public synchronized Int2ObjectMap<CoopRequest> getCoopRequests() {
        return coopRequests;
    }

    public void setNameCardId(int nameCardId) {
        this.nameCardId = nameCardId;
        this.updateProfile();
    }

    public void setMainCharacterId(int mainCharacterId) {
        if (this.mainCharacterId != 0) {
            return;
        }
        this.mainCharacterId = mainCharacterId;
    }

    public int getClientTime() {
        return session.getClientTime();
    }

    public long getLastPingTime() {
        return session.getLastPingTime();
    }

    public void setPaused(boolean newPauseState) {
        var oldPauseState = this.paused;
        this.paused = newPauseState;

        if (newPauseState && !oldPauseState) {
            this.onPause();
        } else if (oldPauseState && !newPauseState) {
            this.onUnpause();
        }
    }

    public boolean isInMultiplayer() {
        return this.getWorld() != null && this.getWorld().isMultiplayer();
    }

    public boolean inMoonCard() {
        return moonCard;
    }

    public void addMoonCardDays(int days) {
        this.moonCardDuration += days;
    }

    public int getMoonCardRemainDays() {
        Calendar remainCalendar = Calendar.getInstance();
        remainCalendar.setTime(moonCardStartTime);
        remainCalendar.add(Calendar.DATE, moonCardDuration);
        Date theLastDay = remainCalendar.getTime();
        Date now = DateHelper.onlyYearMonthDay(new Date());
        return (int) ((theLastDay.getTime() - now.getTime()) / (24 * 60 * 60 * 1000));
    }

    public boolean rechargeMoonCard() {
        if (this.moonCardDuration > 150) return false;
        inventory.addItem(new GameItem(203, 300));
        if (!moonCard) {
            moonCard = true;
            Date now = new Date();
            moonCardStartTime = DateHelper.onlyYearMonthDay(now);
            moonCardDuration = 30;
        } else {
            moonCardDuration += 30;
        }
        moonCardGetTimes.add(moonCardStartTime);
        return true;
    }

    public void getTodayMoonCard() {
        if (!moonCard) {
            return;
        }
        Date now = DateHelper.onlyYearMonthDay(new Date());
        if (moonCardGetTimes.contains(now)) {
            return;
        }
        Date stopTime = new Date();
        Calendar stopCalendar = Calendar.getInstance();
        stopCalendar.setTime(stopTime);
        stopCalendar.add(Calendar.DATE, moonCardDuration);
        stopTime = stopCalendar.getTime();
        if (now.after(stopTime)) {
            moonCard = false;
            return;
        }
        moonCardGetTimes.add(now);
        addMoonCardDays(1);
        GameItem item = new GameItem(201, 90);
        getInventory().addItem(item, ActionReason.BlessingRedeemReward);
        session.send(new PacketCardProductRewardNotify(getMoonCardRemainDays()));
    }

    public void addExpeditionInfo(long avatarGuid, int expId, int hourTime, int startTime) {
        ExpeditionInfo exp = new ExpeditionInfo();
        exp.setExpId(expId);
        exp.setHourTime(hourTime);
        exp.setState(1);
        exp.setStartTime(startTime);
        expeditionInfo.put(avatarGuid, exp);
    }

    public void removeExpeditionInfo(long avatarGuid) {
        expeditionInfo.remove(avatarGuid);
    }

    public ExpeditionInfo getExpeditionInfo(long avatarGuid) {
        return expeditionInfo.get(avatarGuid);
    }

    public ShopLimit getGoodsLimit(int goodsId) {
        Optional<ShopLimit> shopLimit = this.shopLimit.stream().filter(x -> x.getShopGoodId() == goodsId).findFirst();
        if (shopLimit.isEmpty())
            return null;
        return shopLimit.get();
    }

    public void addShopLimit(int goodsId, int boughtCount, int nextRefreshTime) {
        ShopLimit target = getGoodsLimit(goodsId);
        if (target != null) {
            target.setHasBought(target.getHasBought() + boughtCount);
            target.setHasBoughtInPeriod(target.getHasBoughtInPeriod() + boughtCount);
            target.setNextRefreshTime(nextRefreshTime);
        } else {
            ShopLimit sl = new ShopLimit();
            sl.setShopGoodId(goodsId);
            sl.setHasBought(boughtCount);
            sl.setHasBoughtInPeriod(boughtCount);
            sl.setNextRefreshTime(nextRefreshTime);
            getShopLimit().add(sl);
        }
        this.save();
    }

    public boolean hasSentLoginPackets() {
        return hasSentLoginPackets;
    }

    public void addAvatar(Avatar avatar, boolean addToCurrentTeam) {
        boolean result = getAvatars().addAvatar(avatar);

        if (result) {

            getAvatars().addStartingWeapon(avatar);

            if (hasSentLoginPackets()) {

                avatar.recalcStats();

                sendPacket(new PacketAvatarAddNotify(avatar, addToCurrentTeam && this.getTeamManager().canAddAvatarToCurrentTeam()));
                if (addToCurrentTeam) {

                    this.getTeamManager().addAvatarToCurrentTeam(avatar);
                }
            }

            new PlayerObtainAvatarEvent(this.getPlayer(), avatar).call();
        } else {

        }
    }

    public void addAvatar(Avatar avatar) {
        addAvatar(avatar, true);
    }

    public void addAvatar(int avatarId) {

        addAvatar(new Avatar(avatarId), true);
    }

    public void addFlycloak(int flycloakId) {
        this.getFlyCloakList().add(flycloakId);
        this.sendPacket(new PacketAvatarGainFlycloakNotify(flycloakId));
    }

    public void addCostume(int costumeId) {
        if (this.getCostumeList().contains(costumeId)) {
            return;
        }
        this.getCostumeList().add(costumeId);
        this.sendPacket(new PacketAvatarGainCostumeNotify(costumeId));
    }

    /** 0 means "no skin" (unequip) and must count as valid, otherwise the client's
     * unequip/resend request gets a failure retcode and shows "server error". */
    public boolean hasWeaponSkin(int weaponSkinId) {
        return weaponSkinId <= 0 || this.getWeaponSkinList().contains(weaponSkinId);
    }

    /** Unlocks one or more weapon skins (from ITEM_USE_ADD_WEAPON_SKIN materials). */
    public void addWeaponSkins(List<Integer> skinIds) {
        boolean changed = false;
        for (int id : skinIds) {
            if (id != 0 && this.getWeaponSkinList().add(id)) {
                changed = true;
            }
        }
        if (changed) {
            this.save();
            this.sendPacket(new PacketAvatarWeaponSkinDataNotify(this));
        }
    }

    public void addTraceEffect(int traceEffectId) {
        this.getTraceEffectList().add(traceEffectId);
        this.sendPacket(new PacketAvatarGainTraceEffectNotify(traceEffectId));
        this.save();
    }

    public int getCostumeFrom(int avatarId) {
        var avatars = this.getAvatars();
        avatars.loadFromDatabase();
        var avatar = avatars.getAvatarById(avatarId);
        return avatar == null ? 0 : avatar.getCostume();
    }

    public void addPersonalLine(int personalLineId) {
        this.getPersonalLineList().add(personalLineId);
        session.getPlayer().getQuestManager().queueEvent(QuestCond.QUEST_COND_PERSONAL_LINE_UNLOCK, personalLineId);
    }

    public void addNameCard(int nameCardId) {
        this.getNameCardList().add(nameCardId);
        this.sendPacket(new PacketUnlockNameCardNotify(nameCardId));
    }

    public void setNameCard(int nameCardId) {
        if (!this.getNameCardList().contains(nameCardId)) {
            return;
        }

        this.setNameCardId(nameCardId);

        this.sendPacket(new PacketSetNameCardRsp(nameCardId));
    }

    public void dropMessage(Object message) {
        this.getServer().getChatSystem().sendPrivateMessageFromServer(getUid(), message.toString());
    }

    public void sendMessage(Player sender, Object message) {
        this.getServer().getChatSystem().sendPrivateMessage(sender, this.getUid(), message.toString());
    }

    public List<Mail> getAllMail() {
        return this.getMailHandler().getMail();
    }

    public void sendMail(Mail message) {
        this.getMailHandler().sendMail(message);
    }

    public boolean deleteMail(int mailId) {
        return this.getMailHandler().deleteMail(mailId);
    }

    public Mail getMail(int index) {
        return this.getMailHandler().getMailById(index);
    }

    public int getMailId(Mail message) {
        return this.getMailHandler().getMailIndex(message);
    }

    public boolean replaceMailByIndex(int index, Mail message) {
        return this.getMailHandler().replaceMailByIndex(index, message);
    }

    public void interactWith(int gadgetEntityId, GadgetInteractReq interactReq) {
        GameEntity target = getScene().getEntityById(gadgetEntityId);

        if (target == null) {
            return;
        }

        target.onInteract(this, interactReq);
        // EntityGadget, EntityItem and EntityMonster credit the quest themselves once the interaction
        // goes through; a client-created gadget has no interaction logic of its own and still counts.
        if (target instanceof emu.grasscutter.game.entity.EntityClientGadget clientGadget) {
            onGadgetInteracted(clientGadget.getGadgetId());
        }
    }

    /** Quest credit for a gadget interaction that went through. */
    public void onGadgetInteracted(int gadgetId) {
        this.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_INTERACT_GADGET, gadgetId);
    }

    public void onPause() {
        getStaminaManager().stopSustainedStaminaHandler();
    }

    public void onUnpause() {
        getStaminaManager().startSustainedStaminaHandler();
    }

    public void sendPacket(BasePacket packet) {
        this.getSession().send(packet);
    }

    public OnlinePlayerInfo getOnlinePlayerInfo() {
        OnlinePlayerInfo.Builder onlineInfo = OnlinePlayerInfo.newBuilder()
            .setUid(this.getUid())
            .setNickname(this.getNickname())
            .setPlayerLevel(this.getLevel())
            .setMpSettingType(this.getMpSetting())
            .setNameCardId(this.getNameCardId() == 0 || this.getNameCardId() == 210001 ? 210003 : this.getNameCardId())
            .setSignature(this.getSignature())
            .setProfilePicture(ProfilePictureHelper.toProto(this.getHeadImage()).toBuilder().setProfileFrameId(this.getProfileFrameId()).build());

        if (this.getWorld() != null) {
            onlineInfo.setCurPlayerNumInWorld(getWorld().getPlayerCount());
        } else {
            onlineInfo.setCurPlayerNumInWorld(1);
        }

        return onlineInfo.build();
    }

    public void setBirthday(int d, int m) {
        this.birthday = new PlayerBirthday(d, m);
        this.updateProfile();
    }

    public boolean hasBirthday() {
        return this.birthday.getDay() > 0;
    }

    public SocialDetail.Builder getSocialDetail() {
        List<SocialShowAvatarInfoOuterClass.SocialShowAvatarInfo> socialShowAvatarInfoList = new ArrayList<>();
        boolean showConstellation = this.isShowConstellationNum();
        if (this.isOnline()) {
            if (this.getShowAvatarList() != null) {
                for (int avatarId : this.getShowAvatarList()) {
                    socialShowAvatarInfoList.add(
                        socialShowAvatarInfoList.size(),
                        this.toSocialShowAvatarInfo(
                            avatarId, getAvatars().getAvatarById(avatarId), showConstellation));
                }
            }
        } else {
            Player offlinePlayer = DatabaseHelper.getPlayerByUid(id);
            List<Integer> showAvatarList = offlinePlayer.getShowAvatarList();
            showConstellation = offlinePlayer.isShowConstellationNum();
            AvatarStorage avatars = offlinePlayer.getAvatars();
            avatars.loadFromDatabase();
            if (showAvatarList != null) {
                for (int avatarId : showAvatarList) {
                    socialShowAvatarInfoList.add(
                        socialShowAvatarInfoList.size(),
                        this.toSocialShowAvatarInfo(
                            avatarId, avatars.getAvatarById(avatarId), showConstellation));
                }
            }
        }

        return SocialDetail.newBuilder()
            .setUid(this.getUid())
            .setProfilePicture(ProfilePictureHelper.toProto(this.getHeadImage()).toBuilder().setProfileFrameId(this.getProfileFrameId()).build())
            .setNickname(this.getNickname())
            .setSignature(this.getSignature())
            .setLevel(this.getLevel())
            .setBirthday(this.getBirthday().getFilledProtoWhenNotEmpty())
            .setWorldLevel(this.getWorldLevel())
            .setNameCardId(this.getNameCardId() == 0 || this.getNameCardId() == 210001 ? 210003 : this.getNameCardId())
            .setIsShowAvatar(this.isShowAvatars())
            .setIsShowConstellationNum(showConstellation)
            .addAllShowAvatarInfoList(socialShowAvatarInfoList)
            .addAllShowNameCardIdList(this.getShowNameCardInfoList())
            .setFinishAchievementNum(this.getFinishedAchievementNum())
            .setFriendEnterHomeOptionValue(this.getHome() == null ? 0 : this.getHome().getEnterHomeOption());
    }

    private SocialShowAvatarInfoOuterClass.SocialShowAvatarInfo toSocialShowAvatarInfo(
            int avatarId, Avatar avatar, boolean showConstellation) {
        SocialShowAvatarInfoOuterClass.SocialShowAvatarInfo.Builder builder =
                SocialShowAvatarInfoOuterClass.SocialShowAvatarInfo.newBuilder()
                        .setAvatarId(avatarId)
                        .setLevel(avatar.getLevel())
                        .setCostumeId(avatar.getCostume());
        if (showConstellation) {
            builder.setTalentLevel(avatar.getCoreProudSkillLevel());
        }
        return builder.build();
    }

    public int getFinishedAchievementNum() {
        return Achievements.getByPlayer(this).getFinishedAchievementNum();
    }

    public List<ShowAvatarInfoOuterClass.ShowAvatarInfo> getShowAvatarInfoList() {
        List<ShowAvatarInfoOuterClass.ShowAvatarInfo> showAvatarInfoList = new ArrayList<>();

        Player player;
        boolean shouldRecalc;
        if (this.isOnline()) {
            player = this;
            shouldRecalc = false;
        } else {
            player = DatabaseHelper.getPlayerByUid(id);
            player.getAvatars().loadFromDatabase();
            player.getInventory().loadFromDatabase();
            shouldRecalc = true;
        }

        List<Integer> showAvatarList = player.getShowAvatarList();
        AvatarStorage avatars = player.getAvatars();
        if (showAvatarList != null) {
            for (int avatarId : showAvatarList) {
                Avatar avatar = avatars.getAvatarById(avatarId);
                if (shouldRecalc) {
                    avatar.recalcStats();
                }
                showAvatarInfoList.add(avatar.toShowAvatarInfoProto());
            }
        }
        return showAvatarInfoList;
    }

    public List<Integer> getShowNameCardInfoList() {
        List<Integer> info = this.getShowNameCardList();
        return info == null ? new ArrayList<>() : info;
    }

    public PlayerWorldLocationInfoOuterClass.PlayerWorldLocationInfo getWorldPlayerLocationInfo() {
        return PlayerWorldLocationInfoOuterClass.PlayerWorldLocationInfo.newBuilder()
            .setSceneId(emu.grasscutter.game.dungeons.DomainDungeonHelper.notifySceneId(this.getSceneId()))
            .setPlayerLoc(this.getPlayerLocationInfo())
            .build();
    }

    public PlayerLocationInfo getPlayerLocationInfo() {
        return PlayerLocationInfo.newBuilder()
            .setUid(this.getUid())
            .setPos(this.getPosition().toProto())
            .setRot(this.getRotation().toProto())
            .build();
    }

    public void loadBattlePassManager() {
        if (this.battlePassManager != null) return;
        this.battlePassManager = DatabaseHelper.loadBattlePass(this);
        this.battlePassManager.getMissions().values().removeIf(mission -> mission.getData() == null);
    }

    public void loadDailyTaskManager() {
        if (this.dailyTaskManager != null) return;
        this.dailyTaskManager = DatabaseHelper.loadDailyTaskManager(this);
    }

    /** Lazily created: saves written before the field existed carry no list at all. */
    public Set<Integer> getForcedFinishedQuests() {
        if (this.forcedFinishedQuests == null) {
            this.forcedFinishedQuests = new HashSet<>();
        }
        return this.forcedFinishedQuests;
    }

    public PlayerCollectionRecords getCollectionRecordStore() {
        if (this.collectionRecordStore == null) {
            this.collectionRecordStore = new PlayerCollectionRecords();
        }
        return collectionRecordStore;
    }

    public Map<String, MapMark> getMapMarks() {
        if (this.mapMarks == null) {
            this.mapMarks = new HashMap<String, MapMark>();
        }
        return mapMarks;
    }

    private boolean expireCoopRequest(CoopRequest req) {
        if (!req.isExpired()) return false;
        req.getRequester().sendPacket(new PacketPlayerApplyEnterMpResultNotify(
            this,
            false,
            ReasonOuterClass.Reason.Reason_SYSTEM_JUDGE));
        return true;
    }

    private boolean expireEnterHomeRequest(EnterHomeRequest req) {
        return this.expireEnterHomeRequest(req, false);
    }

    private boolean expireEnterHomeRequest(EnterHomeRequest req, boolean force) {
        if (!req.isExpired() && !force) return false;
        req.getRequester().sendPacket(new PacketPlayerApplyEnterHomeResultNotify(
            this.getUid(),
            this.getNickname(),
            false,
            PlayerApplyEnterHomeResultNotifyOuterClass.PlayerApplyEnterHomeResultNotify.Reason.Reason_SYSTEM_JUDGE));
        req.getRequester().sendPacket(new PacketTryEnterHomeRsp());
        return true;
    }

    public synchronized void onTick() {

        long pingAge = System.currentTimeMillis() - this.getLastPingTime();
        if (pingAge > 60000) {
            this.getSession().close();
            return;
        }

        this.getCoopRequests().values().removeIf(this::expireCoopRequest);

        this.getEnterHomeRequests().values().removeIf(this::expireEnterHomeRequest);

        this.getBuffManager().onTick();

        // Settles an Arlecchino burst whose damage frame never arrived - a cast that hit
        // nothing sends no AttackResult, so nothing else would ever clear the debt.
        ArlecchinoBurstBoL.onTick(this);

        // Drop SkillSucc burst energy pending if ability never confirmed (e.g. dash-recovery false Q).
        try {
            this.getEnergyManager().onTick();
        } catch (Throwable ignored) {
            // Energy pending expiry must not break the player tick.
        }

        // Read the world once: a duplicate login can clear it from another thread mid-tick.
        var world = this.getWorld();
        if (world != null) {

            this.sendPacket(new PacketWorldPlayerRTTNotify(world));

            long time = System.currentTimeMillis();
            if (world.isMultiplayer() && this.getScene() != null && time > nextSendPlayerLocTime) {
                this.sendPacket(new PacketWorldPlayerLocationNotify(world));
                this.sendPacket(new PacketScenePlayerLocationNotify(this.getScene()));
                this.resetSendPlayerLocTime();
            }
        }

        this.doDailyReset();

        var timeNow = Utils.getCurrentSeconds();
        var needNotify = false;
        for (ExpeditionInfo e : expeditionInfo.values()) {
            if (e.getState() == 1) {
                if (timeNow - e.getStartTime() >= e.getHourTime() * 60 * 60) {
                    e.setState(2);
                    needNotify = true;
                }
            }
        }
        if (needNotify) {
            this.save();
            this.sendPacket(new PacketAvatarExpeditionDataNotify(this.getExpeditionInfo()));
        }

        this.getForgingManager().sendPlayerForgingUpdate();

        this.getResinManager().rechargeResin();

        this.getSatiationManager().reduceSatiation();

        this.getHome().updateHourlyResources(this);

        this.getQuestManager().onTick();
    }

    private synchronized void doDailyReset() {

        int currentTime = Utils.getCurrentSeconds();

        var currentDate = GameDay.date(currentTime);
        var lastResetDate = GameDay.date(this.getLastDailyReset());

        if (!currentDate.isAfter(lastResetDate)) {
            return;
        }

        this.setForgePoints(300_000);

        this.getBattlePassManager().resetDailyMissions();

        if (GameDay.crossedWeeklyReset(this.getLastDailyReset(), currentTime)) {
            this.getBattlePassManager().resetWeeklyMissions();
        }
        this.getBattlePassManager().triggerMission(WatcherTriggerType.TRIGGER_LOGIN);

        if (this.dailyTaskManager != null) {
            this.dailyTaskManager.resetDailyTasksForNewDay();
        }

        this.setResinBuyCount(0);

        BirthdayMailSystem.checkAndSend(this, currentDate);

        this.setLastDailyReset(currentTime);
    }

    public void resetSendPlayerLocTime() {
        this.nextSendPlayerLocTime = System.currentTimeMillis() + 5000;
    }

    @PostLoad
    private void onLoad() {
        this.getCodex().setPlayer(this);
        this.getProgressManager().setPlayer(this);
        this.getTeamManager().setPlayer(this);
    }

    public List<Integer> getTpsLoadout() {
        if (this.tpsLoadout == null) {
            this.tpsLoadout = new ArrayList<>();
        }
        return this.tpsLoadout;
    }

    public List<emu.grasscutter.game.icecreature.IceCreatureRecord> getIceCreatures() {
        if (this.iceCreatures == null) {
            this.iceCreatures = new ArrayList<>();
        }
        return this.iceCreatures;
    }

    public Map<Integer, Integer> getIceCreatureTypeGadgets() {
        if (this.iceCreatureTypeGadgets == null) {
            this.iceCreatureTypeGadgets = new HashMap<>();
        }
        return this.iceCreatureTypeGadgets;
    }

    public Map<Integer, Integer> getTpsAmmunition() {
        if (this.tpsAmmunition == null) {
            this.tpsAmmunition = new HashMap<>();
        }
        return this.tpsAmmunition;
    }

    public void save() {
        DatabaseHelper.savePlayer(this);
    }

    public void loadFromDatabase() {

        if (this.getTeamManager() == null) {
            this.teamManager = new TeamManager(this);
        }
        if (this.getCodex() == null) {
            this.codex = new PlayerCodex(this);
        }
        if (this.getProfile().getUid() == 0) {
            this.getProfile().syncWithCharacter(this);
        }

        var runner = Grasscutter.getThreadPool();
        var achievementsLoad = runner.submit(() -> this.achievements = Achievements.getByPlayer(this));

        var avatarsLoad = runner.submit(this.getAvatars()::loadFromDatabase);
        var inventoryLoad = runner.submit(this.getInventory()::loadFromDatabase);

        runner.submit(this.getFriendsList()::loadFromDatabase);
        runner.submit(this.getMailHandler()::loadFromDatabase);
        var questsLoad = runner.submit(this.getQuestManager()::loadFromDatabase);

        var battlePassLoad = runner.submit(this::loadBattlePassManager);
        var dailyTasksLoad = runner.submit(this::loadDailyTaskManager);

        // With asyncLogin off this runs on the one thread that handles every player's packets.
        // Waiting on the loaded flags forever meant a loader that threw - its exception swallowed
        // by the pool - or a pool full of stuck tasks froze the whole server. Fail this one login
        // instead.
        awaitLoginLoads(avatarsLoad, inventoryLoad, achievementsLoad, questsLoad, battlePassLoad, dailyTasksLoad);

        this.getPlayerProgress().setPlayer(this);
    }

    private static final long LOAD_TIMEOUT_SECONDS = 30;

    void awaitLoginLoads(Future<?> avatarsLoad, Future<?> inventoryLoad, Future<?> achievementsLoad,
            Future<?> questsLoad, Future<?> battlePassLoad, Future<?> dailyTasksLoad) {
        awaitLoad("avatars", avatarsLoad);
        awaitLoad("inventory", inventoryLoad);
        awaitLoad("achievements", achievementsLoad);
        awaitLoad("quests", questsLoad);
        awaitLoad("battle pass", battlePassLoad);
        awaitLoad("daily tasks", dailyTasksLoad);
    }

    private void awaitLoad(String what, java.util.concurrent.Future<?> load) {
        try {
            load.get(LOAD_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException(
                    "Loading " + what + " for uid " + this.getUid() + " failed.", e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            load.cancel(true);
            throw new IllegalStateException(
                    "Loading " + what + " for uid " + this.getUid() + " took over "
                            + LOAD_TIMEOUT_SECONDS + "s; the load pool may be stuck.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted loading " + what + " for uid " + this.getUid());
        }
    }

    public void onLogin() {
        // A trial avatar saved into a team (e.g. logging out inside a trial dungeon) has no avatar
        // to build an entity from.
        this.getTeamManager().removeUnownedAvatarsFromTeams();

        if (this.getSceneTags().isEmpty() || this.getSceneTags() == null) {
            this.applyStartingSceneTags();
        }

        // A TPS dungeon is not saved: logging back into one finds no dungeon running, an empty
        // scene and a client that never finishes loading. Start in Teyvat instead.
        var savedScene = GameData.getSceneDataMap().get(this.getSceneId());
        if (emu.grasscutter.game.tps.TpsAvatarSystem.isTpsScene(savedScene)) {
            this.setSceneId(3);
            this.position.set(ScriptLoader.getSceneMeta(3).config.born_pos);
        }

        if (GameHome.HOME_SCENE_IDS.contains(this.getSceneId())) {
            this.setSceneId(this.prevScene <= 0 ? 3 : this.prevScene);
            var pos = this.getPrevPosForHome();
            if (pos.equals(Position.ZERO)) {
                pos = ScriptLoader.getSceneMeta(this.getSceneId()).config.born_pos;
            }
            this.position.set(pos);
        }

        // The same holds for every other dungeon, and mid-quest it strands the step: the scene
        // comes back with no running dungeon, so the subquest never gets its rewind. Arriving in
        // the world is what fires ENTER_MY_WORLD and lets the quest's own fail/rollback execs
        // restart the segment, so send the player back to where they walked in from.
        savedScene = GameData.getSceneDataMap().get(this.getSceneId());
        if (savedScene != null && savedScene.getSceneType() == SceneType.SCENE_DUNGEON) {
            var originScene = this.prevSceneForDungeon > 0 ? this.prevSceneForDungeon : 3;
            var originPos = this.getPrevPosForDungeon();
            this.setSceneId(originScene);
            this.position.set(
                    originPos != null && !originPos.equals(Position.ZERO)
                            ? originPos
                            : ScriptLoader.getSceneMeta(originScene).config.born_pos);
        }

        World world = new World(this);
        world.addPlayer(this);

        this.setProperty(PlayerProperty.PROP_PLAYER_MP_SETTING_TYPE, this.getMpSetting().getNumber(), false);
        this.setProperty(PlayerProperty.PROP_IS_MP_MODE_AVAILABLE, 1, false);

        this.doDailyReset();

        getQuestManager().onLogin();

        session.send(new PacketPlayerDataNotify(this));
        session.send(new PacketStoreWeightLimitNotify());
        session.send(new PacketPlayerStoreNotify(this));
        session.send(new PacketAvatarDataNotify(this));
        session.send(new PacketCraftingAvatarDataNotify());
        session.send(new PacketAvatarWeaponSkinDataNotify(this));
        // 登录时下发头像/头像框数据：否则重登后个人主页的头像框会显示成"?"，要重新应用一次才恢复。
        // (HandlerMiao21517 修复头像框时发的也是这一组：25989+6326+4162。)
        session.send(new PacketBeyondProfilePictureDataNotify(this));
        session.send(new PacketBeyondPlayerDetailRsp(this));
        session.send(new PacketGetPlayerSocialDetailRsp(this.getSocialDetail(), 0));

        this.getProgressManager().onPlayerLogin();
        try {
            emu.grasscutter.game.player.DomainHandbookHelper.onPlayerLogin(this);
        } catch (Throwable ignored) {
        }

        session.send(new PacketFinishedParentQuestNotify(this));
        session.send(new PacketBattlePassAllDataNotify(this));
        session.send(new PacketQuestListNotify(this));
        session.send(new PacketQuestGlobalVarNotify(this));
        // Tips met before they were tracked, so the codex matches this account's progress.
        try {
            PushTipsManager.of(this)
                    .backfill(
                            subQuestId -> {
                                var quest = this.getQuestManager().getQuestById(subQuestId);
                                return quest != null && quest.getState() == QuestState.QUEST_STATE_FINISHED;
                            },
                            monsterId -> this.getCodex().getUnlockedAnimal().containsKey(monsterId));
        } catch (RuntimeException e) {
            Grasscutter.getLogger().warn("[PushTips] uid={} backfill failed", this.getUid(), e);
        }
        session.send(new PacketCodexDataFullNotify(this));
        session.send(new PacketPushTipsAllDataNotify(PushTipsManager.of(this).states()));
        session.send(new PacketAllWidgetDataNotify(this));

        this.achievements.onLogin(this);

        session.send(new PacketWidgetGadgetAllDataNotify());
        // Re-attach follower pet ability group after widget slot is restored
        WidgetPetHelper.syncEquippedWidget(this);
        this.getServer().getCombineSystem().onPlayerLogin(this);
        session.send(new PacketCombineDataNotify(this.unlockedCombines));
        session.send(new PacketGetChatEmojiCollectionRsp(this.getChatEmojiIdList()));
        this.forgingManager.sendForgeDataNotify();
        this.resinManager.onPlayerLogin();
        this.cookingManager.sendCookDataNotify();
        this.cookingCompoundManager.onPlayerLogin();
        this.teamManager.onPlayerLogin();

        // Idempotent, and not merely a null check: the load is submitted to the pool and nothing
        // waits on it, so skipping here would silently cost the player a day of commissions.
        this.loadDailyTaskManager();
        this.dailyTaskManager.onPlayerLogin();

        // The client is told nothing about these on its own - they exist only as a forged notify,
        // so a relog has to re-assert them or the quests come back unfinished.
        if (this.forcedFinishedQuests != null && !this.forcedFinishedQuests.isEmpty()) {
            ForcedQuests.notify(this, this.forcedFinishedQuests);
        }

        getTodayMoonCard();

        this.getBattlePassManager().triggerMission(WatcherTriggerType.TRIGGER_LOGIN);

        this.furnitureManager.onLogin();

        var homeWorld = this.getServer().getHomeWorldOrCreate(this);
        homeWorld.setHost(this);
        this.home = homeWorld.getHome();
        this.setCurHomeWorld(homeWorld);
        home.onOwnerLogin(this);

        this.activityManager = new ActivityManager(this);
        getQuestManager().onActivitiesReady();

        session.send(new PacketPlayerEnterSceneNotify(this));
        session.send(new PacketPlayerLevelRewardUpdateNotify(rewardedLevels));

        this.hasSentLoginPackets = true;

        session.setState(SessionState.ACTIVE);

        PlayerJoinEvent event = new PlayerJoinEvent(this);
        event.call();
        if (event.isCanceled()) {
            session.close();
            return;
        }

        getServer().registerPlayer(this);
    }

    public void onLogout() {
        try {

            this.getServer().getChatSystem().clearHistoryOnLogout(this);

            getStaminaManager().stopSustainedStaminaHandler();

            // Static per-player combat state (and the repeating tasks some of it drives) would
            // otherwise keep this player and their world reachable after they leave.
            PlayerRuntimeStateCleanup.clear(this);

            // Leaving the dungeon (trial team, TPS traveler) must not keep the player in the world:
            // a world left behind keeps ticking and sending to the closed session forever.
            try {
                this.getServer().getDungeonSystem().exitDungeon(this);
            } catch (Throwable e) {
                Grasscutter.getLogger().warn("Player (UID {}) could not leave the dungeon on logout", getUid(), e);
            }

            if (this.getWorld() != null) {
                this.getWorld().removePlayer(this);
            }

            this.getProfile().syncWithCharacter(this);

            this.getCoopRequests().clear();
            this.getEnterHomeRequests().values().forEach(req -> this.expireEnterHomeRequest(req, true));
            this.getEnterHomeRequests().clear();

            PlayerQuitEvent event = new PlayerQuitEvent(this);
            event.call();
        } catch (Throwable e) {
            Grasscutter.getLogger().warn("Player (UID {}) save failure", getUid(), e);
        } finally {
            Utils.runAll(
                    "Player (UID " + getUid() + ") logout save",
                    this::save,
                    () -> this.getTeamManager().saveAvatars(),
                    () -> this.getFriendsList().save());
            removeFromServer();
        }
    }

    public void removeFromServer() {

        getServer().getPlayers().values().removeIf(player1 -> player1 == this);
    }

    public void unfreezeUnlockedScenePoints(int sceneId) {

        GameData.getScenePointEntryMap().values().stream()
                .filter(scenePointEntry ->

                        "DungeonEntry".equals(scenePointEntry.getPointData().getType())

                        && scenePointEntry.getPointData().isGroupLimit())
                .forEach(scenePointEntry -> {

                        val pointId = scenePointEntry.getPointData().getId();
                        if (unlockedScenePoints.get(sceneId).contains(pointId)) {
                            this.sendPacket(new PacketUnfreezeGroupLimitNotify(pointId, sceneId));
                        }
                });
    }

    public void unfreezeUnlockedScenePoints() {
        unlockedScenePoints.keySet().forEach(sceneId -> unfreezeUnlockedScenePoints(sceneId));
    }

    public int getLegendaryKey() {
        return this.getProperty(PlayerProperty.PROP_PLAYER_LEGENDARY_KEY);
    }

    public synchronized void addLegendaryKey(int count) {
        this.setProperty(PlayerProperty.PROP_PLAYER_LEGENDARY_KEY, getLegendaryKey() + count);
    }

    public synchronized void useLegendaryKey(int count) {
        this.setProperty(PlayerProperty.PROP_PLAYER_LEGENDARY_KEY, getLegendaryKey() - count);
    }

    public int getPropertyMin(PlayerProperty prop) {
        if (prop.isDynamicRange()) {
            return 0;
        } else {
            return prop.getMin();
        }
    }

    public int getPropertyMax(PlayerProperty prop) {
        if (prop.isDynamicRange()) {
            return switch (prop) {
                case PROP_CUR_SPRING_VOLUME -> getProperty(PlayerProperty.PROP_MAX_SPRING_VOLUME);
                case PROP_CUR_PERSIST_STAMINA -> getProperty(PlayerProperty.PROP_MAX_STAMINA);
                default -> 0;
            };
        } else {
            return prop.getMax();
        }
    }

    private boolean setPropertyWithSanityCheck(PlayerProperty prop, int value, boolean sendPacket) {
        var currentValue = this.properties.getOrDefault(prop.getId(), 0);

        var event = new PlayerPropertyChangeEvent(this, prop, currentValue, value);
        if (!event.call()) return false;

        prop = event.getProperty();
        value = event.getNewValue();

        var min = this.getPropertyMin(prop);
        var max = this.getPropertyMax(prop);
        if (min <= value && value <= max) {
            this.properties.put(prop.getId(), value);
            if (sendPacket) {

                switch (prop) {
                    case PROP_PLAYER_EXP -> this.sendPacket(new PacketPlayerPropChangeReasonNotify(this, prop, currentValue, value,
                        PropChangeReason.PropChangeReason_PROP_CHANGE_PLAYER_ADD_EXP));
                    case PROP_PLAYER_LEVEL -> this.sendPacket(new PacketPlayerPropChangeReasonNotify(this, prop, currentValue, value,
                        PropChangeReason.PropChangeReason_PROP_CHANGE_LEVELUP));
                    case PROP_MAX_STAMINA -> this.sendPacket(new PacketPlayerPropChangeReasonNotify(this, prop, currentValue, value,
                        PropChangeReason.PropChangeReason_PROP_CHANGE_CITY_LEVELUP));

                }

                this.sendPacket(new PacketPlayerPropNotify(this, prop));
                this.sendPacket(new PacketPlayerPropChangeNotify(this, prop, value - currentValue));
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Player otherPlayer &&
            this.id == otherPlayer.getUid();
    }

    @Override
    public String toString() {
        return "Player UID: %s; Nickname: %s; Account: %s"
                .formatted(this.id, this.nickname, this.account);
    }

    public enum SceneLoadState {
        NONE(0), LOADING(1), INIT(2), LOADED(3);

        @Getter
        private final int value;

        SceneLoadState(int value) {
            this.value = value;
        }
    }
}
