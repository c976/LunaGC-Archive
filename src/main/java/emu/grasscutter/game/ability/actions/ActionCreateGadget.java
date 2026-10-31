package emu.grasscutter.game.ability.actions;

import com.google.protobuf.*;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.IneffaRelayHelper;
import emu.grasscutter.game.ability.VentiSkillObjHelper;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.props.CampTargetType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.AbilityActionCreateGadgetOuterClass.AbilityActionCreateGadget;
import java.util.ArrayList;
import java.util.function.IntPredicate;

@AbilityAction(AbilityModifierAction.Type.CreateGadget)
public class ActionCreateGadget extends AbilityActionHandler {

    /**
     * Whether the client is already running - and populating - the chain this creation hangs off, in
     * which case a copy of ours is a second one the player can see.
     *
     * <p>Only chains rooted at a player's avatar count. A summon hanging off a monster is left alone:
     * the reasoning here is about what the client spawns for its own character, and an enemy's
     * mechanics are not that.
     */
    private static boolean clientOwnsChain(GameEntity entity) {
        if (entity instanceof EntityClientGadget) return true;

        // Owners are only ever set at creation, to an entity that already exists, so walking up
        // cannot come back around.
        while (entity instanceof EntityGadget summon && summon.getOwner() != null) {
            entity = summon.getOwner();
            if (entity instanceof EntityAvatar || entity instanceof EntityClientGadget) return true;
        }
        return false;
    }

    /** Escoffier meks the client spawns via EvtCreateGadgetNotify. */
    private static boolean isEscoffierClientSkillObj(int gadgetId) {
        return gadgetId == 42112001 // cold-storage cooking mek
                || gadgetId == 42112004
                || gadgetId == 42112005; // improvised-cook pot
    }

    /**
     * Chiori Tamoto / turret SkillObjs (client via EvtCreateGadgetNotify). A server EntityGadget
     * shell appears beside her on ability rebuild and survives after she leaves the field.
     */
    private static boolean isChioriClientSkillObj(int gadgetId) {
        return gadgetId == 41094001 // ElementalArt Turret (Tamoto)
                || gadgetId == 41094003 // RockGadget Turret
                || gadgetId == 41094005 // NormalAttack_04
                || gadgetId == 41094007; // Constellation doll
    }

    /** Zhongli stone stele — same client-owned SkillObj shell pattern. */
    private static boolean isZhongliClientSkillObj(int gadgetId) {
        return gadgetId == 41030002;
    }

    private static void purgeAvatarSkillObjShells(Ability ability, IntPredicate ids) {
        var player = ability.getPlayerOwner();
        if (player == null || player.getScene() == null) {
            return;
        }
        var scene = player.getScene();
        for (GameEntity e : new ArrayList<>(scene.getEntities().values())) {
            if (!(e instanceof EntityGadget gadget) || e instanceof EntityClientGadget) {
                continue;
            }
            if (!ids.test(gadget.getGadgetId())) {
                continue;
            }
            var owner = gadget.getOwner();
            if (!(owner instanceof EntityAvatar avatar)
                    || avatar.getAvatar() == null
                    || avatar.getAvatar().getPlayer() != player) {
                continue;
            }
            scene.removeEntity(gadget);
            Grasscutter.getLogger()
                    .debug(
                            "[SkillObjShell] uid={} purged gadget={} entity={}",
                            player.getUid(),
                            gadget.getGadgetId(),
                            gadget.getId());
        }
    }

    /**
     * Magatsu Mitake Narukami combat gadgets (EchoMist clones, BurstAtk vajras, etc.).
     * Positions come from ConfigBornByGlobalValue on the client.
     */
    private static boolean isMitakenarukamiClientSkillObj(int gadgetId) {
        return switch (gadgetId) {
            case 42906104, 42906110, 42906114, 42906116, 42906119, 42906120, 42906123, 42906125,
                    42906126, 42906127 -> true;
            default -> false;
        };
    }

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        // Light-guide effects need a server gadget at the current avatar. The existing
        // per-owner replacement below keeps repeated create invocations to one follower.
        final var entity = action.gadgetID == 70950005 && "CurLocalAvatar".equals(action.target)
                ? resolveTarget(ability, target, action.target)
                : ability.getOwner();
        if (entity == null) return false;
        
        // Kachina's client owns Turbo Twirly, including riding, energy and expiry.
        // A second server top can linger or interrupt riding.
        if (action.gadgetID == 41100001
                && entity instanceof EntityAvatar avatar
                && avatar.getAvatar() != null
                && avatar.getAvatar().getAvatarId() == 10000100) {
            purgeAvatarSkillObjShells(ability, id -> id == 41100001);
            return true;
        }
        // The client owns these chains and spawns its own copies, so ours are only ever duplicates -
        // and duplicates the owner can see, since addEntity below broadcasts to everyone while a
        // client-made gadget is deliberately not echoed back to its own client.
        //
        // Two shapes of it. A gadget the client created outright, which is Odette's shadows. And a
        // summon spawning the next hop, which is Furina: her skill puts out an invisible Salon
        // Solitaire controller, and that controller's own ability creates the singers. Those pile up
        // rather than merely double, because every cast builds a fresh controller and the cleanup
        // below only recognises summons belonging to the one controller it is standing on - so the
        // previous cast's singers match nothing, and neither does the KillGadget that should retire
        // them when the skill ends.
        if (clientOwnsChain(entity)) {
            return true;
        }

        // The Snezhnaya trains' doors, carriages and triggers come from their own abilities, and the
        // client builds them locally (an official capture has no EvtCreateGadgetNotify for them).
        // Server copies only stack extra door interactions on the train.
        if (emu.grasscutter.game.train.SnezhnayaTrainSystem.isTrainChain(entity)) {
            return true;
        }

        // Escoffier skill objs are client-owned (EvtCreateGadget). A server EntityGadget copy is an
        // empty shell the caster can see (no charge UI / SkillObj abilities), including cook pot
        // 42112005 and cold-storage mek 42112001.
        if (isEscoffierClientSkillObj(action.gadgetID)) {
            return true;
        }

        // Chiori Tamoto / Zhongli stele: client-owned SkillObjs. Server shells auto-spawn on ability
        // rebuild and linger after the avatar leaves (no KillSelf on the empty EntityGadget).
        if (isChioriClientSkillObj(action.gadgetID) || isZhongliClientSkillObj(action.gadgetID)) {
            purgeAvatarSkillObjShells(
                    ability, id -> isChioriClientSkillObj(id) || isZhongliClientSkillObj(id));
            return true;
        }

        // Raiden Mitakenarukami echoes / vajras: client places them via ConfigBornByGlobalValue
        // (not implemented server-side). A server copy stacks at the boss origin and looks like
        // several skills firing at once; KillSelf+otherTargets then also mis-cleans the wrong set.
        if (isMitakenarukamiClientSkillObj(action.gadgetID)) {
            return true;
        }

        // Hypostasis skill objs (InitialPos / RushPos / emitters / defence / reborn): client owns
        // CreateGadget. A server EntityGadget has no SkillObj abilities and renders as Default_Item
        // — the weird floating “device” that hangs in the arena for the whole fight.
        if (emu.grasscutter.game.world.EffigyCombatHelper.isEffigyClientOwnedGadget(action.gadgetID)) {
            return true;
        }

        // Ineffa Volti-tower / assists / burst bullets: same client-owned SkillObj pattern. A server
        // shell stacks with EvtCreateGadget copies, survives past duration (no onRemoved KillSelf),
        // and shows up as leftover "hats" once the real summon is gone.
        if (IneffaRelayHelper.isIneffaClientSkillObj(action.gadgetID)) {
            var player = ability.getPlayerOwner();
            if (player != null) {
                IneffaRelayHelper.purgeServerShells(player, false);
            }
            return true;
        }

        // Venti Stormeye / WindBlade / WindField: client-owned. Server shells land under the
        // caster (Q duplicate stormeye, or E field while Hexenzirkel-infused NA fires).
        if (VentiSkillObjHelper.isVentiClientSkillObj(action.gadgetID)) {
            VentiSkillObjHelper.purgeServerShells(ability.getPlayerOwner());
            return true;
        }

        // Fontaine dive ripple (Avatar_DiveSkill_Octopus_OnGround CreateGadget). Never spawn on
        // land ability rebuilds — underwater combat uses client/local dive state instead.
        if (action.gadgetID == 40034001) {
            var player = ability.getPlayerOwner();
            if (player == null
                    || !emu.grasscutter.game.player.DiveAbilityHelper.isCurrentlyDiving(player)) {
                return true;
            }
        }

        AbilityActionCreateGadget createGadget;
        try {
            createGadget = AbilityActionCreateGadget.parseFrom(abilityData);
        } catch (InvalidProtocolBufferException e) {
            return false;
        }

        // The payload only carries a position when the action came from a real create-gadget
        // invocation. Reached from a modifier being added it carries something else entirely, and
        // taking its absent pos would strand the summon at the world origin - so fall back to
        // whoever is summoning it.
        var pos =
                createGadget.hasPos() ? new Position(createGadget.getPos()) : entity.getPosition().clone();
        var rot =
                createGadget.hasRot() ? new Position(createGadget.getRot()) : entity.getRotation().clone();

        var owner = action.ownerIsTarget ? target : entity;
        // Defer ability init until owner is set: Furina's OrderController onAdded immediately
        // CreateGadgets the Salon singers, and clientOwnsChain only recognises an avatar root once
        // getOwner() is non-null. Constructing with init-first left owner null and spawned a second
        // set of singers the caster can see (usually looking like an extra Usher near her).
        var entityCreated =
                new EntityGadget(
                        entity.getScene(),
                        action.gadgetID,
                        pos,
                        rot,
                        null,
                        action.campID,
                        CampTargetType.getTypeByName(action.campTargetType).getValue(),
                        false);
        entityCreated.setOwner(owner);
        // Ability initialization may immediately create a nested gadget and then delete it.
        // Register this parent before those callbacks so a completed ley-line can retire the
        // entire summon chain even if one intermediate link vanishes before the next tick.
        var blossomManager = entity.getScene().getBlossomManager();
        if (blossomManager != null) blossomManager.onSceneEntityCreated(entityCreated);
        entityCreated.initAbilities();

        // Nothing on this side runs the summon's own KillSelf, so without this every charged attack
        // would leave another copy standing in the scene. One summon of a given kind per owner —
        // EXCEPT Effigy rebirth prisms: CreateRebornPart1/2/3 all use gadget 42004010, so this
        // cleanup would delete prism #1 when spawning #2/#3 and leave an empty arena.
        if (!emu.grasscutter.game.world.EffigyCombatHelper.isEffigyRebornPrismGadget(action.gadgetID)) {
            entity.getScene().getEntities().values().stream()
                    .filter(e -> e instanceof EntityGadget g
                            && g.getGadgetId() == action.gadgetID
                            && g.getOwner() == owner)
                    .toList()
                    .forEach(stale -> entity.getScene().removeEntity(stale));
        }

        if (owner instanceof EntityGadget ownerGadget) {
            ownerGadget.getChildren().add(entityCreated);
        }

        entity.getScene().addEntity(entityCreated);

        Grasscutter.getLogger()
                .trace(
                        "Gadget {} created at pos {} rot {}",
                        action.gadgetID,
                        entityCreated.getPosition(),
                        entityCreated.getRotation());

        return true;
    }
}
