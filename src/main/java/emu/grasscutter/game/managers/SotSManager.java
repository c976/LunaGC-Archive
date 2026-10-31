package emu.grasscutter.game.managers;

import ch.qos.logback.classic.Logger;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.city.CityInfoData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.proto.ChangHpReasonOuterClass.ChangHpReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.event.player.PlayerLevelStatueEvent;
import emu.grasscutter.server.packet.send.*;
import java.util.*;

// Statue of the Seven Manager
public class SotSManager extends BasePlayerManager {

    // NOTE: Spring volume balance *1  = fight prop HP *100

    public static final int GlobalMaximumSpringVolume =
            PlayerProperty.PROP_MAX_SPRING_VOLUME.getMax();
    private final Logger logger = Grasscutter.getLogger();
    private final boolean enablePriorityHealing = false;
    private Timer autoRecoverTimer;

    /**
     * How close the player must be to a statue for the region to count as entered. Deliberately
     * conservative: the real client-side value is not exposed in any config we can read, and
     * {@link #updateStatueProximity()} logs the measured distance so this can be corrected.
     */
    private static final double STATUE_REGION_RADIUS = 15.0;

    /** Beyond the trigger radius but still worth logging, for calibration only. */
    private static final double PROXIMITY_LOG_RADIUS = 60.0;

    private static final long PROXIMITY_PERIOD_MS = 2000;

    /** Shared by every player's proximity timer thread, so it has to be a concurrent map. */
    private static final java.util.Map<Integer, java.util.List<emu.grasscutter.data.binout.ScenePointEntry>>
            STATUE_POINT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private Timer proximityTimer;
    private int nearbyStatuePointId = 0;
    private long lastRefillLogMs = 0L;
    private long lastRefillRunMs = 0L;
    private long lastNearLogMs = 0L;

    private static final long REFILL_PERIOD_MS = 15_000;
    private static final long REFILL_LOG_INTERVAL_MS = 30_000;
    private static final long NEAR_LOG_INTERVAL_MS = 15_000;

    public SotSManager(Player player) {
        super(player);
    }

    public boolean getIsAutoRecoveryEnabled() {
        return player.getProperty(PlayerProperty.PROP_IS_SPRING_AUTO_USE) == 1;
    }

    public void setIsAutoRecoveryEnabled(boolean enabled) {
        player.setProperty(PlayerProperty.PROP_IS_SPRING_AUTO_USE, enabled ? 1 : 0);
        player.save();
    }

    public int getAutoRecoveryPercentage() {
        return player.getProperty(PlayerProperty.PROP_SPRING_AUTO_USE_PERCENT);
    }

    public void setAutoRecoveryPercentage(int percentage) {
        player.setProperty(PlayerProperty.PROP_SPRING_AUTO_USE_PERCENT, percentage);
        player.save();
    }

    public long getLastUsed() {
        return player.getSpringLastUsed();
    }

    public void setLastUsed() {
        player.setSpringLastUsed(System.currentTimeMillis() / 1000);
        player.save();
    }

    public int getMaxVolume() {
        return player.getProperty(PlayerProperty.PROP_MAX_SPRING_VOLUME);
    }

    public void setMaxVolume(int volume) {
        player.setProperty(PlayerProperty.PROP_MAX_SPRING_VOLUME, volume);
        player.save();
    }

    public int getCurrentVolume() {
        return player.getProperty(PlayerProperty.PROP_CUR_SPRING_VOLUME);
    }

    public void setCurrentVolume(int volume) {
        player.setProperty(PlayerProperty.PROP_CUR_SPRING_VOLUME, volume);
        setLastUsed();
        player.save();
    }

    public void handleEnterTransPointRegionNotify() {
        logger.trace("Player entered statue region");
        autoRevive();
        if (autoRecoverTimer == null) {
            autoRecoverTimer = new Timer();
            autoRecoverTimer.schedule(new AutoRecoverTimerTick(), 2500, 15000);
        }
    }

    /**
     * Distance-driven replacement for {@link #handleEnterTransPointRegionNotify()}.
     *
     * <p>7.1's CmdId for {@code EnterTransPointRegionNotify} is unknown in PacketOpcodes (the value
     * there is a guess carried over from 7.0), so the client's own region notification never reaches
     * this class and the auto-recover timer never starts: standing at a statue heals nothing and the
     * client's 神像的恩泽 panel reads a spring volume of zero. Detecting proximity here gives the
     * same behaviour without depending on that opcode, and it logs the measured distance so the
     * radius below can be calibrated against the real one.
     */
    public void updateStatueProximity() {
        var scene = player.getScene();
        if (scene == null) return;
        var me = player.getPosition();
        if (me == null) return;

        int sceneId = player.getSceneId();
        var statues = statuePointsForScene(sceneId);
        if (statues.isEmpty()) {
            if (nearbyStatuePointId != 0) leaveStatueRegion();
            return;
        }

        int nearestId = -1;
        double nearest = Double.MAX_VALUE;
        for (var entry : statues) {
            var pos = entry.getPointData().getTranPos();
            if (pos == null) pos = entry.getPointData().getPos();
            if (pos == null) continue;
            double d = me.computeDistance(pos);
            if (d < nearest) {
                nearest = d;
                nearestId = entry.getPointData().getId();
            }
        }
        if (nearestId < 0) return;

        if (nearest <= STATUE_REGION_RADIUS) {
            if (nearestId != nearbyStatuePointId) {
                logger.info(
                        "Statue region entered by proximity uid={} scene={} point={} dist={}",
                        player.getUid(), sceneId, nearestId, String.format("%.1f", nearest));
                nearbyStatuePointId = nearestId;
                handleEnterTransPointRegionNotify();
            }
        } else if (nearest <= PROXIMITY_LOG_RADIUS) {
            // Diagnostic only: keeps the real approach distance visible so the radius can be tuned.
            long nowMs = System.currentTimeMillis();
            if (nowMs - lastNearLogMs > NEAR_LOG_INTERVAL_MS) {
                lastNearLogMs = nowMs;
                logger.info(
                        "Near statue uid={} scene={} point={} dist={} (radius={})",
                        player.getUid(), sceneId, nearestId, String.format("%.1f", nearest),
                        String.format("%.1f", STATUE_REGION_RADIUS));
            }
            if (nearbyStatuePointId != 0) leaveStatueRegion();
        } else if (nearbyStatuePointId != 0) {
            leaveStatueRegion();
        }
    }

    private void leaveStatueRegion() {
        nearbyStatuePointId = 0;
        handleExitTransPointRegionNotify();
    }

    /** Statue trans points of one scene, resolved once and cached. */
    private static java.util.List<emu.grasscutter.data.binout.ScenePointEntry>
            statuePointsForScene(int sceneId) {
        return STATUE_POINT_CACHE.computeIfAbsent(
                sceneId,
                id -> {
                    var out = new ArrayList<emu.grasscutter.data.binout.ScenePointEntry>();
                    for (var entry : GameData.getScenePointEntryMap().values()) {
                        if (entry == null || entry.getPointData() == null) continue;
                        if (entry.getSceneId() != id) continue;
                        if (!emu.grasscutter.game.managers.StatueTalkQuests.isStatuePoint(
                                entry.getPointData())) continue;
                        out.add(entry);
                    }
                    return java.util.List.copyOf(out);
                });
    }

    /** Starts the proximity probe. Safe to call more than once. */
    public void startProximityProbe() {
        if (proximityTimer != null) return;
        proximityTimer = new Timer("SotSProximity-" + player.getUid(), true);
        proximityTimer.schedule(new ProximityTick(), 1000, PROXIMITY_PERIOD_MS);
        logger.info("Statue proximity probe started uid={} period={}ms", player.getUid(), PROXIMITY_PERIOD_MS);
    }

    public void stopProximityProbe() {
        if (proximityTimer != null) {
            proximityTimer.cancel();
            proximityTimer = null;
        }
    }

    public void handleExitTransPointRegionNotify() {
        logger.trace("Player left statue region");
        if (autoRecoverTimer != null) {
            autoRecoverTimer.cancel();
            autoRecoverTimer = null;
        }
    }

    private class ProximityTick extends TimerTask {
        @Override
        public void run() {
            try {
                // Volume must accrue whether or not the player is standing at a statue, otherwise
                // spending it all leaves the balance stuck at zero once they walk away. Throttled:
                // each call writes the player document, and the accrual is proportional to elapsed
                // time, so running it every REFILL_PERIOD_MS instead of every tick is equivalent.
                if (System.currentTimeMillis() - lastRefillRunMs >= REFILL_PERIOD_MS) {
                    lastRefillRunMs = System.currentTimeMillis();
                    applyTimeBasedRefill();
                }
                updateStatueProximity();
            } catch (Throwable t) {
                // A probe failure must never take the player or the server down; the region heal is
                // an enhancement, not a requirement.
                logger.trace("Statue proximity probe failed uid={}: {}", player.getUid(), t.toString());
            }
        }
    }

    // autoRevive automatically revives all team members.
    public void autoRevive() {
        player
                .getTeamManager()
                .getActiveTeam()
                .forEach(
                        entity -> {
                            boolean isAlive = entity.isAlive();
                            if (isAlive) {
                                return;
                            }
                            logger.trace("Reviving avatar " + entity.getAvatar().getAvatarData().getName());
                            player.getTeamManager().reviveAvatar(entity.getAvatar());
                            player.getTeamManager().healAvatar(entity.getAvatar(), 30, 0);
                        });
    }

    public void checkAndHealAvatar(EntityAvatar entity) {
        int maxHP = (int) (entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) * 100);
        int currentHP = (int) (entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) * 100);
        if (currentHP == maxHP) {
            return;
        }
        int targetHP = maxHP * getAutoRecoveryPercentage() / 100;

        if (targetHP > currentHP) {
            int needHP = targetHP - currentHP;
            if (needHP <= 0) {
                return;
            }

            // healAvatar() refuses a character that is not alive, so a knocked-out team member has to
            // be revived before it can be healed.
            boolean healed;
            if (entity.isAlive()) {
                healed = player.getTeamManager().healAvatar(entity.getAvatar(), 0, needHP);
            } else {
                healed = player.getTeamManager().reviveAvatar(entity.getAvatar());
                if (healed) {
                    healed = player.getTeamManager().healAvatar(entity.getAvatar(), 0, needHP);
                }
            }
            if (!healed) {
                logger.info(
                        "Statue heal did not apply uid={} avatar={}",
                        player.getUid(), entity.getAvatar().getAvatarId());
                return;
            }

            // The spring volume budget is deliberately not charged. Genshin's refill wait only makes
            // the statue unusable exactly when it is needed - a player who just died gets revived and
            // then sits at 1 HP because the balance is empty - and recharging it takes ~25 minutes.
            // On a private server that wait buys nothing, so healing is free and the volume stays
            // full for display purposes only.
            logger.info(
                    "Healing avatar {} +{} (volume not charged, stays at {})",
                    entity.getAvatar().getAvatarData().getName(),
                    needHP,
                    getCurrentVolume());
            player
                    .getSession()
                    .send(
                            new PacketEntityFightPropChangeReasonNotify(
                                    entity,
                                    FightProperty.FIGHT_PROP_CUR_HP,
                                    ((float) needHP / 100),
                                    List.of(3),
                                    PropChangeReason.PropChangeReason_PROP_CHANGE_STATUE_RECOVER,
                                    ChangHpReason.ChangHpReason_CHANGE_HP_ADD_STATUE));
            player
                    .getSession()
                    .send(new PacketEntityFightPropUpdateNotify(entity, FightProperty.FIGHT_PROP_CUR_HP));
        }
    }

    public void refillSpringVolume() {
        // Temporary: Max spring volume depends on level of the statues in Mondstadt and Liyue. Override
        // until we have statue level.
        // TODO: remove
        // https://genshin-impact.fandom.com/wiki/Statue_of_The_Seven#:~:text=region%20of%20Inazuma.-,Statue%20Levels,-Upon%20first%20unlocking
        setMaxVolume(GlobalMaximumSpringVolume);
        // Temporary: Auto enable 100% statue recovery until we can adjust statue settings in game
        // TODO: remove
        setAutoRecoveryPercentage(100);
        setIsAutoRecoveryEnabled(true);

        applyTimeBasedRefill();
    }

    /**
     * Accrues spring volume from the time since it was last spent, independent of where the player
     * is. Split out of {@link #refillSpringVolume()} because that method also force-enables 100%
     * auto recovery, which must not be re-applied on a tick the player is nowhere near a statue
     * (it would override their own panel settings). Running this from the always-on proximity tick
     * is what makes the volume recover after being spent; before, it only ran off the auto-recover
     * timer, which stops the moment the player walks away, so the balance stayed at zero forever.
     */
    private void applyTimeBasedRefill() {
        int maxVolume = getMaxVolume();
        int currentVolume = getCurrentVolume();
        if (currentVolume >= maxVolume) return;

        long now = System.currentTimeMillis() / 1000;
        int secondsSinceLastUsed = (int) (now - getLastUsed());
        // 15s = 1% max volume
        int volumeRefilled = secondsSinceLastUsed * maxVolume / 15 / 100;
        if (volumeRefilled <= 0) return;

        int newVolume = Math.min(currentVolume + volumeRefilled, maxVolume);
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastRefillLogMs > REFILL_LOG_INTERVAL_MS) {
            lastRefillLogMs = nowMs;
            logger.info(
                    "Statue spring volume refilled uid={} +{} ({} -> {})",
                    player.getUid(), volumeRefilled, currentVolume, newVolume);
        }
        setCurrentVolume(newVolume);
    }

    private class AutoRecoverTimerTick extends TimerTask {
        // autoRecover checks player setting to see if auto recover is enabled, and refill HP to the
        // predefined level.
        public void run() {
            refillSpringVolume();

            logger.trace(
                    "isAutoRecoveryEnabled: "
                            + getIsAutoRecoveryEnabled()
                            + "\tautoRecoverPercentage: "
                            + getAutoRecoveryPercentage());

            if (getIsAutoRecoveryEnabled()) {
                List<EntityAvatar> activeTeam = player.getTeamManager().getActiveTeam();
                // When the statue does not have enough remaining volume:
                //      Enhanced experience: Enable priority healing
                //                              The current active character will get healed first, then
                // sequential.
                //      Vanilla experience: Disable priority healing
                //                              Sequential healing based on character index.
                int priorityIndex =
                        enablePriorityHealing ? player.getTeamManager().getCurrentCharacterIndex() : -1;
                if (priorityIndex >= 0) {
                    checkAndHealAvatar(activeTeam.get(priorityIndex));
                }
                for (int i = 0; i < activeTeam.size(); i++) {
                    if (i != priorityIndex) {
                        checkAndHealAvatar(activeTeam.get(i));
                    }
                }
            }
        }
    }

    public CityData getCityByAreaId(int areaId) {
        return GameData.getCityDataMap().values().stream()
                .filter(city -> city.getAreaIdVec().contains(areaId))
                .findFirst()
                .orElse(null);
    }

    public CityInfoData getCityInfo(int cityId) {
        if (player.getCityInfoData() == null) player.setCityInfoData(new HashMap<>());
        var cityInfo = player.getCityInfoData().get(cityId);
        if (cityInfo == null) {
            cityInfo = new CityInfoData(cityId);
            player.getCityInfoData().put(cityId, cityInfo);
        }
        return cityInfo;
    }

    public void addCityInfo(CityInfoData cityInfoData) {
        if (player.getCityInfoData() == null) player.setCityInfoData(new HashMap<>());

        player.getCityInfoData().put(cityInfoData.getCityId(), cityInfoData);
    }

    public void levelUpSotS(int areaId, int sceneId, int itemNum) {
        if (itemNum <= 0) return;

        // search city by areaId
        var city = this.getCityByAreaId(areaId);
        if (city == null) return;
        var cityId = city.getCityId();

        var cityInfo = this.getCityInfo(cityId);
        int remainingOffer = itemNum;
        int totalConsumed = 0;
        boolean anyLevelUp = false;

        // "Offer all" used to only process one level per request; loop until max
        // level, out of oculi, or inventory empty.
        while (remainingOffer > 0) {
            var nextStatuePromoteData = GameData.getStatuePromoteData(cityId, cityInfo.getLevel() + 1);
            var nextCityLevelup = GameData.getCityLevelupData(cityId, cityInfo.getLevel() + 1);

            int costItemId;
            int nextLevelCrystal;
            int staminaGainUnits;
            int[] rewardIds;

            if (nextStatuePromoteData != null
                    && nextStatuePromoteData.getCostItems() != null
                    && nextStatuePromoteData.getCostItems().length > 0) {
                costItemId = nextStatuePromoteData.getCostItems()[0].getId();
                nextLevelCrystal = nextStatuePromoteData.getCostItems()[0].getCount();
                staminaGainUnits = nextStatuePromoteData.getStamina();
                rewardIds = nextStatuePromoteData.getRewardIdList();
            } else if (nextCityLevelup != null && nextCityLevelup.getCostItemId() > 0) {
                // Fontaine / Natlan / Nod-Krai (city 7+) use CityLevelupConfigData.
                costItemId = nextCityLevelup.getCostItemId();
                nextLevelCrystal = nextCityLevelup.getCostItemCount();
                staminaGainUnits = nextCityLevelup.getStaminaGain();
                rewardIds =
                        nextCityLevelup.getRewardId() > 0
                                ? new int[] {nextCityLevelup.getRewardId()}
                                : null;
            } else if (nextCityLevelup != null) {
                // Level-1 row often has no consume — treat as free unlock step.
                cityInfo.setLevel(cityInfo.getLevel() + 1);
                anyLevelUp = true;
                continue;
            } else {
                break; // already max level / no table
            }

            int need = Math.max(0, nextLevelCrystal - cityInfo.getNumCrystal());
            int have = player.getInventory().getItemCountById(costItemId);
            int use = Math.min(remainingOffer, Math.min(need, have));

            if (use > 0) {
                player.getInventory().removeItemById(costItemId, use);
                cityInfo.setNumCrystal(cityInfo.getNumCrystal() + use);
                remainingOffer -= use;
                totalConsumed += use;
            }

            if (cityInfo.getNumCrystal() < nextLevelCrystal) {
                break; // not enough for this level
            }

            cityInfo.setNumCrystal(cityInfo.getNumCrystal() - nextLevelCrystal);
            cityInfo.setLevel(cityInfo.getLevel() + 1);
            anyLevelUp = true;

            // update max stamina (clamp to hard cap) and notify client
            int staminaGain = staminaGainUnits * 100;
            if (staminaGain > 0) {
                int curMax = player.getProperty(PlayerProperty.PROP_MAX_STAMINA);
                int hardMax = PlayerProperty.PROP_MAX_STAMINA.getMax();
                int newMax = Math.min(curMax + staminaGain, hardMax);
                if (newMax > curMax) {
                    player.setProperty(PlayerProperty.PROP_MAX_STAMINA, newMax, true);
                }
            }

            // Add items — boosted / evenly redistributed per nation (stamina still from excel).
            var grants =
                    emu.grasscutter.game.managers.StatueOfferRewardHelper.rewardsForLevel(
                            cityId, cityInfo.getLevel());
            if (!grants.isEmpty()) {
                for (var e : grants.int2IntEntrySet()) {
                    if (e.getIntValue() > 0) {
                        player
                                .getInventory()
                                .addItem(e.getIntKey(), e.getIntValue(), ActionReason.CityLevelupReward);
                    }
                }
            } else if (rewardIds != null) {
                // Fallback if helper has no row (should be rare).
                for (var rewardId : rewardIds) {
                    RewardData rewardData = GameData.getRewardDataMap().get(rewardId);
                    if (rewardData == null) continue;
                    player
                            .getInventory()
                            .addItemParamDatas(rewardData.getRewardItemList(), ActionReason.CityLevelupReward);
                }
            }

            // unlock forcescene
            player.sendPacket(new PacketSceneForceUnlockNotify(1, true));
        }

        // handle quest once if anything was offered
        if (totalConsumed >= 1 || anyLevelUp) {
            player.getQuestManager().queueEvent(QuestContent.QUEST_CONTENT_CITY_LEVEL_UP, cityId, areaId);
        }

        // update data
        this.addCityInfo(cityInfo);

        // Packets
        player.sendPacket(
                new PacketLevelupCityRsp(
                        sceneId, cityInfo.getLevel(), cityId, cityInfo.getNumCrystal(), areaId, 0));

        try {
            emu.grasscutter.game.player.InvestigationHandbookHelper.trigger(
                    player,
                    emu.grasscutter.game.props.WatcherTriggerType.TRIGGER_CITY_LEVEL_UP,
                    cityId,
                    1);
        } catch (Throwable ignored) {
        }

        // Call PlayerLevelStatueEvent.
        new PlayerLevelStatueEvent(this.getPlayer(), cityInfo, sceneId, areaId).call();
    }
}
