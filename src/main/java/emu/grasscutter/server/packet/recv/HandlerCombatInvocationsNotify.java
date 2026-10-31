package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dps.DPSAttackContext;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ForwardTypeOuterClass.ForwardType;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.CombatInvocationsNotifyOuterClass.CombatInvocationsNotify;
import emu.grasscutter.net.proto.CombatInvokeEntryOuterClass.CombatInvokeEntry;
import emu.grasscutter.net.proto.EntityMoveInfoOuterClass.EntityMoveInfo;
import emu.grasscutter.net.proto.EvtAnimatorParameterInfoOuterClass.EvtAnimatorParameterInfo;
import emu.grasscutter.net.proto.EvtBeingHitInfoOuterClass.EvtBeingHitInfo;
import emu.grasscutter.net.proto.MotionInfoOuterClass.MotionInfo;
import emu.grasscutter.net.proto.MotionStateOuterClass.MotionState;
import emu.grasscutter.net.proto.PlayerDieTypeOuterClass;
import emu.grasscutter.server.event.entity.EntityMoveEvent;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;

@Opcodes(PacketOpcodes.CombatInvocationsNotify)
public class HandlerCombatInvocationsNotify extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        CombatInvocationsNotify notif = CombatInvocationsNotify.parseFrom(payload);
        for (CombatInvokeEntry entry : notif.getInvokeListList()) {

            switch (entry.getArgumentType()) {
                case CombatTypeArgument_COMBAT_EVT_BEING_HIT -> {
                    EvtBeingHitInfo hitInfo = EvtBeingHitInfo.parseFrom(entry.getCombatData());
                    AttackResult attackResult = hitInfo.getAttackResult();
                    Player player = session.getPlayer();

                    if (attackResult.getAttackerId()
                                    != player.getTeamManager().getCurrentAvatarEntity().getId()
                            && player.getAbilityManager().isAbilityInvulnerable()) break;

                    {
                        int defenseId = attackResult.getDefenseId();
                        float computedDamage = attackResult.getDamage();
                        boolean changed = false;

                        float hexRatio = 0f, normalPct = 0f;
                        EntityClientGadget attackerGadget = null;
                        if (computedDamage == 0.0f) {
                            var attackerEntity = player.getScene().getEntityById(attackResult.getAttackerId());
                            // Hexenzirkel infused arrows may report OriginOwner (avatar) as attacker.
                            // Fall back to scanning recent Venti Hexenzirkel arrow gadgets.
                            EntityClientGadget gadget = null;
                            if (attackerEntity instanceof EntityClientGadget g) {
                                gadget = g;
                            } else if (attackerEntity instanceof EntityAvatar) {
                                gadget = findVentiHexenzirkelArrowGadget(player, attackResult.getAttackerId());
                            }
                            if (gadget != null) {
                                for (var ab : gadget.getInstancedAbilities()) {
                                    if (ab == null) continue;
                                    var sp = ab.getAbilitySpecials();
                                    if (hexRatio == 0f) hexRatio = sp.getFloat("Hexenzirkel_NormalAttack_Ratio");
                                    if (normalPct == 0f) {
                                        normalPct = pickNormalAttackPct(sp.keySet(), k -> sp.getFloat(k), attackResult.getAnimEventId());
                                    }
                                    if (hexRatio > 0f && normalPct > 0f) break;
                                }
                                if (hexRatio == 0f || normalPct == 0f) {
                                    var vars = player.getAbilityManager().computeGadgetVarOverrides(gadget);
                                    // Persist resolved proud-skill specials onto the gadget so later hits
                                    // do not keep reading config defaults of 0.
                                    for (var ab : gadget.getInstancedAbilities()) {
                                        if (ab == null) continue;
                                        vars.forEach(ab.getAbilitySpecials()::put);
                                    }
                                    if (hexRatio == 0f) hexRatio = vars.getOrDefault("Hexenzirkel_NormalAttack_Ratio", 0f);
                                    if (normalPct == 0f) {
                                        normalPct = pickNormalAttackPct(vars.keySet(), vars::get, attackResult.getAnimEventId());
                                    }
                                }
                                if (hexRatio > 0f && normalPct > 0f) {
                                    attackerGadget = gadget;
                                }
                            }
                        }

                        GameEntity nearestMonster = null;
                        if ((defenseId == 0 || attackerGadget != null)
                                && (computedDamage > 0f || attackerGadget != null)) {
                            var avatarPos = player.getTeamManager().getCurrentAvatarEntity().getPosition();
                            float nearestDist2 = Float.MAX_VALUE;
                            for (var e : player.getScene().getEntities().values()) {
                                if (!(e instanceof EntityMonster)) continue;
                                if (e.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) <= 0f) continue;
                                var p = e.getPosition();
                                float dx = avatarPos.getX() - p.getX();
                                float dz = avatarPos.getZ() - p.getZ();
                                float dist2 = dx * dx + dz * dz;
                                if (dist2 < nearestDist2) {
                                    nearestDist2 = dist2;
                                    nearestMonster = e;
                                }
                            }
                            if (nearestMonster != null) {
                                defenseId = nearestMonster.getId();
                                changed = true;
                            }
                        }

                        if (attackerGadget != null) {
                            var avatarEntity = player.getTeamManager().getCurrentAvatarEntity();
                            float atk = avatarEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK);
                            float anemoDmgBonus = avatarEntity.getFightProperty(FightProperty.FIGHT_PROP_WIND_ADD_HURT);
                            float critRate = avatarEntity.getFightProperty(FightProperty.FIGHT_PROP_CRITICAL);
                            float critDmg = avatarEntity.getFightProperty(FightProperty.FIGHT_PROP_CRITICAL_HURT);

                            computedDamage = atk * hexRatio * normalPct * (1f + anemoDmgBonus);

                            if (Math.random() < critRate) {
                                computedDamage *= (1f + critDmg);
                            }

                            changed = true;
                        }

                        if (changed) {
                            var resultBuilder = attackResult.toBuilder()
                                .setDefenseId(defenseId)
                                .setDamage(computedDamage);
                            // Infused NA often arrives with elementType=0 when ratio was 0; wind floaters
                            // need Anemo (7) or the client may skip the number even after we rewrite damage.
                            if (attackerGadget != null && attackResult.getElementType() == 0) {
                                resultBuilder.setElementType(7);
                            }
                            AttackResult newResult = resultBuilder.build();
                            hitInfo = hitInfo.toBuilder().setAttackResult(newResult).build();
                            entry = entry.toBuilder()
                                .setCombatData(hitInfo.toByteString())
                                .setForwardType(ForwardType.ForwardType_FORWARD_TO_ALL)
                                .build();
                            attackResult = newResult;
                            // Local client already resolved the hit as 0 damage (no floater). Combat
                            // invoke echo alone often won't redraw numbers; EvtBeingHitNotify does.
                            if (attackerGadget != null && computedDamage > 0f) {
                                try {
                                    player.getScene()
                                            .broadcastPacket(
                                                    new emu.grasscutter.server.packet.send.PacketEvtBeingHitNotify(
                                                            hitInfo));
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    }

                    player.getAttackResults().add(attackResult);
                    player.getEnergyManager().handleAttackHit(hitInfo);
                    try {
                        GameEntity atkEnt = player.getScene().getEntityById(attackResult.getAttackerId());
                        GameEntity defEnt = player.getScene().getEntityById(attackResult.getDefenseId());
                        emu.grasscutter.game.achievement.AchievementTriggerHelper.onAttackResultFromAttacker(
                                atkEnt, attackResult, defEnt);
                    } catch (Throwable ignored) {
                    }
                }
                case CombatTypeArgument_ENTITY_MOVE -> {

                    EntityMoveInfo moveInfo = EntityMoveInfo.parseFrom(entry.getCombatData());
                    GameEntity entity = session.getPlayer().getScene().getEntityById(moveInfo.getEntityId());
                    if (entity != null
                            && session.getPlayer().getSceneLoadState() != Player.SceneLoadState.LOADING) {

                        MotionInfo motionInfo = moveInfo.getMotionInfo();
                        MotionState motionState = motionInfo.getState();

                        EntityMoveEvent event =
                                new EntityMoveEvent(
                                        entity,
                                        new Position(motionInfo.getPos()),
                                        new Position(motionInfo.getRot()),
                                        motionState);
                        event.call();

                        entity.move(event.getPosition(), event.getRotation());
                        entity.setLastMoveSceneTimeMs(moveInfo.getSceneTime());
                        entity.setLastMoveReliableSeq(moveInfo.getReliableSeq());
                        entity.setMotionState(motionState);

                        try {
                            emu.grasscutter.game.player.DiveAbilityHelper.onMotionChange(
                                    session.getPlayer(), entity, motionState);
                        } catch (Throwable ignored) {
                            // Dive attach/detach must not break movement.
                        }

                        session
                                .getPlayer()
                                .getStaminaManager()
                                .handleCombatInvocationsNotify(session, moveInfo, entity);

                        if (motionState == MotionState.MotionState_MOTION_LAND_SPEED) {
                            session.setLandingSpeed(motionInfo.getSpeed().getY());
                            session.setLandingTimeMillis(System.currentTimeMillis());
                        }
                        if (motionState == MotionState.MotionState_MOTION_FALL_ON_GROUND
                                && session.getLandingTimeMillis() != 0) {
                            handleFallOnGround(session, entity);
                            session.setLandingTimeMillis(0);
                        }

                        if (motionState == MotionState.MotionState_MOTION_NOTIFY
                                || motionState == MotionState.MotionState_MOTION_FIGHT) {
                            continue;
                        }
                    }
                }
                case CombatTypeArgument_COMBAT_ANIMATOR_PARAMETER_CHANGED -> {
                    EvtAnimatorParameterInfo paramInfo =
                            EvtAnimatorParameterInfo.parseFrom(entry.getCombatData());
                    if (paramInfo.getIsServerCache()) {
                        paramInfo = paramInfo.toBuilder().setIsServerCache(false).build();
                        entry = entry.toBuilder().setCombatData(paramInfo.toByteString()).build();
                    }
                }
                default -> {
                    Grasscutter.getLogger().debug("UnhandledCombatType: type={} typeVal={} dataSize={}",
                        entry.getArgumentType(), entry.getArgumentTypeValue(), entry.getCombatData().size());
                }
            }

            session.getPlayer().getCombatInvokeHandler().addEntry(entry.getForwardType(), entry);
        }
        // Flush even when this notify arrives outside UnionCmd (needed for MP motion/skill sync).
        session.getPlayer().getCombatInvokeHandler().update(session.getPlayer());

        // A standalone notify has no UnionCmdNotify after it to apply its hits. Inside a batch they
        // wait for the end of it, after the ability invokes (shields, modifiers) sent alongside.
        if (!HandlerUnionCmdNotify.isHandlingBatch()) applyQueuedAttacks(session.getPlayer());
    }

    static void applyQueuedAttacks(emu.grasscutter.game.player.Player player) {
        var attacks = player.getAttackResults();
        for (var attack = attacks.poll(); attack != null; attack = attacks.poll()) {
            // Lets the DPS dummy tell reaction damage from elemental damage inside damage().
            DPSAttackContext.set(attack);
            try {
                player.getScene().handleAttack(attack);
            } finally {
                DPSAttackContext.clear();
            }
        }
    }

    private void handleFallOnGround(GameSession session, GameEntity entity) {
        if (session.getPlayer().isInGodMode()) {
            return;
        }

        int maxDelay = 200;
        long actualDelay = System.currentTimeMillis() - session.getLandingTimeMillis();
        Grasscutter.getLogger()
                .trace(
                        "MOTION_FALL_ON_GROUND received after "
                                + actualDelay
                                + "/"
                                + maxDelay
                                + "ms."
                                + (actualDelay > maxDelay ? " Discard" : ""));
        if (actualDelay > maxDelay) {
            return;
        }
        float currentHP = entity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        float maxHP = entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        float cachedLandingSpeed = session.getLandingSpeed();
        float damageFactor = 0;
        if (cachedLandingSpeed < -23.5) {
            damageFactor = 0.33f;
        }
        if (cachedLandingSpeed < -25) {
            damageFactor = 0.5f;
        }
        if (cachedLandingSpeed < -26.5) {
            damageFactor = 0.66f;
        }
        if (cachedLandingSpeed < -28) {
            damageFactor = 1f;
        }
        float damage = maxHP * damageFactor;
        float newHP = currentHP - damage;
        if (entity instanceof EntityAvatar avatarEntity) {
            try {
                newHP = emu.grasscutter.game.ability.HutaoC6Helper.filterDirectHpLoss(
                        avatarEntity, currentHP, Math.max(newHP, 0f));
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("[HutaoC6] fall-damage filter failed", t);
            }
            try {
                newHP = emu.grasscutter.game.ability.ShinobuC6Helper.filterDirectHpLoss(
                        avatarEntity, currentHP, Math.max(newHP, 0f));
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("[ShinobuC6] fall-damage filter failed", t);
            }
        }
        if (newHP < 0) {
            newHP = 0;
        }
        if (damageFactor > 0) {
            Grasscutter.getLogger()
                    .debug(
                            currentHP
                                    + "/"
                                    + maxHP
                                    + "\tLandingSpeed: "
                                    + cachedLandingSpeed
                                    + "\tDamageFactor: "
                                    + damageFactor
                                    + "\tDamage: "
                                    + damage
                                    + "\tNewHP: "
                                    + newHP);
        } else {
            Grasscutter.getLogger().trace(currentHP + "/" + maxHP + "\tLandingSpeed: 0\tNo damage");
        }
        entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, newHP);
        entity
                .getWorld()
                .broadcastPacket(
                        new PacketEntityFightPropUpdateNotify(entity, FightProperty.FIGHT_PROP_CUR_HP));
        if (newHP == 0) {
            session
                    .getPlayer()
                    .getStaminaManager()
                    .killAvatar(session, entity, PlayerDieTypeOuterClass.PlayerDieType.PlayerDieType_PLAYER_DIE_FALL);
        }
    }

    /**
     * When Hexenzirkel infused NA reports the avatar as attacker, locate a matching client arrow
     * gadget owned by that avatar so we can recover talent-scaled damage.
     */
    private static EntityClientGadget findVentiHexenzirkelArrowGadget(Player player, int avatarEntityId) {
        EntityClientGadget best = null;
        for (var e : player.getScene().getEntities().values()) {
            if (!(e instanceof EntityClientGadget gadget)) continue;
            if (gadget.getOriginalOwnerEntityId() != avatarEntityId
                    && gadget.getOwnerEntityId() != avatarEntityId) {
                continue;
            }
            String json = gadget.getGadgetData() != null ? gadget.getGadgetData().getJsonName() : null;
            if (json == null) continue;
            if (!json.startsWith("Venti_HexenzirkelArrow_Burst_")
                    && !json.startsWith("Venti_Constellation01_Arrow")) {
                continue;
            }
            best = gadget; // arrows are short-lived; last matching is fine
        }
        return best;
    }

    private static float pickNormalAttackPct(
            Iterable<String> keys, java.util.function.Function<String, Float> getter, String animEventId) {
        String preferred = null;
        if (animEventId != null && !animEventId.isEmpty()) {
            // e.g. Hit_NAttack_01 → NormalAttack_01_Damage_Percentage
            for (int i = 1; i <= 6; i++) {
                String token = String.format("%02d", i);
                if (animEventId.contains(token) || animEventId.contains("_" + i)) {
                    preferred = "NormalAttack_" + token + "_Damage_Percentage";
                    break;
                }
            }
        }
        if (preferred != null) {
            for (String k : keys) {
                if (preferred.equals(k)) {
                    Float v = getter.apply(k);
                    if (v != null && v > 0f) return v;
                }
            }
        }
        for (String k : keys) {
            if (k.startsWith("NormalAttack_") && k.endsWith("_Damage_Percentage")) {
                Float v = getter.apply(k);
                if (v != null && v > 0f) return v;
            }
        }
        return 0f;
    }
}
