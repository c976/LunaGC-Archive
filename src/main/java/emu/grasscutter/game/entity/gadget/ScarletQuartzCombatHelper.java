package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BuffData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Scene;

/**
 * Dulin's Blood combat effect: ServerBuff notify alone only drives VFX.
 * Ice seals require BloodSmash / HAS_DULINS_BLOOD_BUFF — enforce smash on the server.
 */
public final class ScarletQuartzCombatHelper {
    public static final int BUFF_ID = 500101;
    public static final int BUFF_GROUP_ID = 51;
    public static final String ABILITY_NAME = "Dulins_Blood_Buff";
    public static final String GV_HAS_BUFF = "HAS_DULINS_BLOOD_BUFF";
    public static final String GV_MARK = "HAS_BLOOD_BUFF_MARK_NORMAL";
    public static final String GV_USED = "DULINS_BLOOD_BUFF_USED";

    private ScarletQuartzCombatHelper() {}

    public static boolean playerHasBuff(Player player) {
        if (player == null || player.getBuffManager() == null) {
            return false;
        }
        return player.getBuffManager().hasBuff(BUFF_GROUP_ID);
    }

    public static boolean isDragonspineIceSeal(EntityGadget gadget) {
        if (gadget == null) {
            return false;
        }
        int id = gadget.getGadgetId();
        // Dragonspine ice barrier / seal / solid bulk (official smash targets)
        if (id == 70360112
                || id == 70360113
                || id == 70360118
                || id == 70360120
                || id == 70360124
                || id == 70950017
                || id == 70290022) {
            return true;
        }
        try {
            GadgetData data = gadget.getGadgetData();
            if (data == null || data.getJsonName() == null) {
                return false;
            }
            String json = data.getJsonName();
            if (!json.contains("Ljxs") && !json.contains("SnowMountain") && !json.contains("DragonSpine")) {
                // still allow generic ice seal names
            }
            return json.contains("IceSeal")
                    || json.contains("IceBarrier")
                    || json.contains("IceSolidBulk")
                    || json.contains("AncientBloodTreeIce");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** After ServerBuff is granted: attach ability + GVs so client/server share smash state. */
    public static void onBuffGranted(Player player) {
        if (player == null || player.getTeamManager() == null) {
            return;
        }
        try {
            BuffData buffData = GameData.getBuffDataMap().get(BUFF_ID);
            String abilityName =
                    buffData != null && buffData.getAbilityName() != null && !buffData.getAbilityName().isEmpty()
                            ? buffData.getAbilityName()
                            : ABILITY_NAME;

            for (EntityAvatar avatar : player.getTeamManager().getActiveTeam()) {
                if (avatar == null) {
                    continue;
                }
                avatar.getGlobalAbilityValues().put(GV_HAS_BUFF, 1.0f);
                avatar.getGlobalAbilityValues().put(GV_MARK, 1.0f);
                avatar.getGlobalAbilityValues().put(GV_USED, 0.0f);
                try {
                    player.getAbilityManager().addAbilityToEntity(avatar, abilityName);
                } catch (Throwable t) {
                    Grasscutter.getLogger()
                            .debug("ScarletQuartz addAbility {}: {}", abilityName, t.toString());
                }
            }
            Grasscutter.getLogger()
                    .info("ScarletQuartz combat armed uid={} ability={}", player.getUid(), abilityName);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("ScarletQuartz onBuffGranted failed: {}", t.toString());
        }
    }

    public static void onBuffCleared(Player player) {
        if (player == null || player.getTeamManager() == null) {
            return;
        }
        try {
            for (EntityAvatar avatar : player.getTeamManager().getActiveTeam()) {
                if (avatar == null) {
                    continue;
                }
                avatar.getGlobalAbilityValues().put(GV_HAS_BUFF, 0.0f);
                avatar.getGlobalAbilityValues().put(GV_MARK, 0.0f);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * If attacker holds Dulins Blood and target is a Dragonspine ice seal, shatter it and
     * consume the buff (official: one smash per quartz pickup).
     *
     * @return true if the attack was fully handled (caller should skip normal damage)
     */
    public static boolean trySmashIce(Scene scene, GameEntity attacker, GameEntity target) {
        if (scene == null || target == null || !(target instanceof EntityGadget ice)) {
            return false;
        }
        if (!isDragonspineIceSeal(ice) || ice.isDead()) {
            return false;
        }
        Player player = resolvePlayer(scene, attacker);
        if (player == null || !playerHasBuff(player)) {
            return false;
        }

        try {
            ice.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0.0f);
            ice.checkIfDead();
            if (ice.isDead()) {
                scene.killEntity(ice, attacker != null ? attacker.getId() : 0);
            } else {
                scene.killEntity(ice, attacker != null ? attacker.getId() : 0);
            }

            // Consume buff after successful smash
            player.getBuffManager().removeBuff(BUFF_GROUP_ID);
            onBuffCleared(player);
            for (EntityAvatar avatar : player.getTeamManager().getActiveTeam()) {
                if (avatar != null) {
                    avatar.getGlobalAbilityValues().put(GV_USED, 1.0f);
                }
            }

            Grasscutter.getLogger()
                    .info(
                            "ScarletQuartz smashed ice gadgetId={} cfg={} uid={}",
                            ice.getGadgetId(),
                            ice.getConfigId(),
                            player.getUid());
            return true;
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("ScarletQuartz smash ice failed: {}", t.toString());
            return false;
        }
    }

    private static Player resolvePlayer(Scene scene, GameEntity attacker) {
        try {
            if (attacker instanceof EntityAvatar avatar) {
                return avatar.getPlayer();
            }
            if (attacker instanceof EntityClientGadget cg && cg.getOwner() != null) {
                return cg.getOwner();
            }
            if (scene.getWorld() != null) {
                return scene.getWorld().getHost();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
