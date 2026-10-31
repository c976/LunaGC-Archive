package emu.grasscutter.game.ability;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.game.entity.EntityBaseGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EntityType;
import emu.grasscutter.net.proto.VehicleInteractTypeOuterClass.VehicleInteractType;
import emu.grasscutter.net.proto.VehicleMemberOuterClass.VehicleMember;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.server.packet.send.PacketVehicleInteractRsp;
import java.util.*;

/**
 * Spawns the Natlan Saurian a player rides (龙之魂附).
 *
 * <p>What gets possessed is either a wild Saurian monster or a soul candle (魂烛). The candle is not a
 * separate prop: it is the {@code Natsaurus_*_Vehicle_*} gadget standing in the world un-ridden, whether
 * placed there by a scene group or left behind by the last dismount - which is why the client is never
 * seen creating one, and why a dismount has to leave the vehicle entity in the scene rather than clean it
 * up.
 *
 * <p>Whichever it was, it is consumed: the ridden dragon is a {@code Vehicle} gadget, and
 * {@code vehicle_info} only exists on {@code SceneGadgetInfo}, never on a monster - so the original cannot
 * stay in the scene alongside it. Leaving it there is what made the ride look like a second dragon
 * appearing next to the first.
 *
 * <p>The Saurian's monster row and its vehicle gadget row share only the tribe token in their names -
 * {@code Monster_Natsaurus_Drillhead_Normal_01} against {@code Natsaurus_Drillhead_Vehicle_01} - so the
 * gadget id is resolved from that token rather than from any table, because no table links the two.
 */
public final class NatsaurusVehicleHelper {

    private static final String NATSAURUS = "Natsaurus_";
    private static final String VEHICLE = "_Vehicle";

    /**
     * Soul candle prop gadget id to tribe - the second, rarer shape of attach target.
     *
     * <p>The candles standing around Natlan are the un-ridden {@code Natsaurus_*_Vehicle_*} gadgets
     * themselves and need no table. These six {@code Prop_Dragon_Gadget_01..06} (70801015..20) also carry
     * {@code GadgetAbility_Transfer_Vehicle}, so they are accepted too, but nothing in their gadget rows
     * names a tribe: the pairing is read off the effect each one fires, since
     * {@code Prop_SaurTotem_01} runs {@code Eff_Vehicle_Natsaurus_Hookwalker_SaurTotem}, and so on down the
     * six in {@code ConfigAbility_Scene_V5_0_WhiteBox.json}. NatsaurusVehicleTest re-derives it from that
     * file, so a data change fails the build rather than silently spawning the wrong dragon.
     */
    private static final Map<Integer, String> TOTEM_TRIBES =
            Map.of(
                    70801015, "Hookwalker",
                    70801016, "Drillhead",
                    70801017, "Mosasaurus",
                    70801018, "Shamansaurus",
                    70801019, "Flamingo",
                    70801020, "Bisonsaurus");

    private static volatile Map<String, Integer> tribeToGadgetId;
    private static volatile Set<Integer> vehicleGadgetIds;

    private NatsaurusVehicleHelper() {}

    /** Lowest vehicle gadget id per tribe, so the plain {@code _Vehicle_01} row wins over its variants. */
    private static Map<String, Integer> tribeMap() {
        if (tribeToGadgetId == null) {
            synchronized (NatsaurusVehicleHelper.class) {
                if (tribeToGadgetId == null) {
                    var map = new HashMap<String, Integer>();
                    for (GadgetData gadget : GameData.getGadgetDataMap().values()) {
                        if (gadget.getType() != EntityType.Vehicle) continue;
                        String jsonName = gadget.getJsonName();
                        if (jsonName == null || !jsonName.startsWith(NATSAURUS)) continue;
                        int end = jsonName.indexOf(VEHICLE);
                        if (end <= NATSAURUS.length()) continue;
                        String tribe = jsonName.substring(NATSAURUS.length(), end);
                        map.merge(tribe, gadget.getId(), Math::min);
                    }
                    tribeToGadgetId = map;
                    vehicleGadgetIds = new HashSet<>();
                    for (GadgetData gadget : GameData.getGadgetDataMap().values()) {
                        if (gadget.getType() != EntityType.Vehicle) continue;
                        String jsonName = gadget.getJsonName();
                        if (jsonName != null && jsonName.startsWith(NATSAURUS)) {
                            vehicleGadgetIds.add(gadget.getId());
                        }
                    }
                }
            }
        }
        return tribeToGadgetId;
    }

    /** Whether this gadget id is one of the Saurian mounts - which, parked, is what a soul candle is. */
    public static boolean isNatsaurusVehicleGadget(int gadgetId) {
        tribeMap();
        return vehicleGadgetIds.contains(gadgetId);
    }

    /**
     * Whether this vehicle is a Natlan Saurian mount rather than some other rideable gadget.
     *
     * <p>Everything the soul-attach needs that is not literally about riding an entity - a real HP pool
     * instead of the {@code isInvincible} infinity, releasing the rider when the mount dies, being cleared
     * away on a teleport, taking its own time to disappear - is gated on this. A skiff is the same
     * {@code EntityVehicle} class and its gadget config happens to carry the same {@code isInvincible} flag,
     * so without this gate the Natlan tuning would silently retune boats too.
     */
    public static boolean isNatsaurusMount(EntityVehicle vehicle) {
        return vehicle != null && isNatsaurusVehicleGadget(vehicle.getGadgetId());
    }

    /** The vehicle gadget id for a Saurian monster, or 0 when the name carries no tribe we know. */
    public static int vehicleGadgetId(String monsterName) {
        if (monsterName == null) return 0;
        int start = monsterName.indexOf(NATSAURUS);
        if (start < 0) return 0;
        start += NATSAURUS.length();
        int end = monsterName.indexOf('_', start);
        String tribe = end < 0 ? monsterName.substring(start) : monsterName.substring(start, end);
        return tribeToGadgetIdFor(tribe);
    }

    /** Whether this gadget is one of the six soul candles. */
    public static boolean isSoulCandle(int gadgetId) {
        return TOTEM_TRIBES.containsKey(gadgetId);
    }

    /** The tribe a soul candle stands for, or null when the gadget is not one of the six. */
    public static String soulCandleTribe(int gadgetId) {
        return TOTEM_TRIBES.get(gadgetId);
    }

    private static int tribeToGadgetIdFor(String tribe) {
        return tribeMap().getOrDefault(tribe, 0);
    }

    /**
     * The vehicle gadget id this scan target stands for, or 0 when it is not something that can be ridden.
     *
     * <p>Three shapes count. A wild Saurian monster resolves through its tribe. A soul candle resolves to
     * itself: what stands in the world as a candle is a scene-placed gadget carrying the very same
     * {@code Natsaurus_*_Vehicle_*} id the ride uses - the parked mount, not a separate prop. And a vehicle
     * nobody is on is that same parked mount left behind by a previous dismount.
     */
    public static int vehicleGadgetIdFor(GameEntity target) {
        if (target instanceof EntityMonster monster) {
            return monster.getMonsterData() == null
                    ? 0
                    : vehicleGadgetId(monster.getMonsterData().getMonsterName());
        }
        if (target instanceof EntityVehicle vehicle) {
            // One somebody is already riding is not a mount to be had - and the scan picks the player's own
            // dragon up constantly while they are on it. One that has just run out is waiting out its death
            // animation and would come back as a mount mid-collapse.
            if (!vehicle.isAlive()) return 0;
            return vehicle.getVehicleMembers().isEmpty() ? vehicle.getGadgetId() : 0;
        }
        if (target instanceof EntityBaseGadget gadget) {
            int gadgetId = gadget.getGadgetId();
            if (isNatsaurusVehicleGadget(gadgetId)) return gadgetId;
            String tribe = TOTEM_TRIBES.get(gadgetId);
            return tribe == null ? 0 : tribeToGadgetIdFor(tribe);
        }
        return 0;
    }

    /**
     * Puts the player on the Saurian {@code source} stands for, consuming {@code source} in the process.
     *
     * <p>The vehicle spawns at the source's position: riding a soul candle is supposed to bring the dragon
     * up where the candle was, not where some other dragon happens to be standing.
     *
     * @return the vehicle entity, or null when {@code source} is not something that can be ridden
     */
    public static EntityVehicle board(Player player, GameEntity source) {
        if (player == null || player.getScene() == null || source == null) return null;

        int gadgetId = vehicleGadgetIdFor(source);
        if (gadgetId == 0) return null;

        var scene = player.getScene();
        var pos = source.getPosition().clone();
        var rot = source.getRotation().clone();

        eject(player);

        // Ahead of creating anything, and not left to eject(): a soul candle is the mount gadget itself,
        // and a mount taken out through the scene's death path now waits out its own death animation
        // before leaving. The candle the player just rode off is not dying - it is being taken up, and
        // officially it goes at once. This is also what makes a boarded Saurian vanish instead of
        // dissolving. VISION_REPLACE was tried first and the client ignored it for a monster, keeping the
        // original on screen and still running its AI.
        scene.removeEntity(source, VisionType.VisionType_VISION_REMOVE);

        var vehicle = new EntityVehicle(scene, player, gadgetId, 0, pos, rot);
        scene.addEntity(vehicle);

        player.getStaminaManager()
                .handleVehicleInteractReq(
                        player.getSession(),
                        vehicle.getId(),
                        VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_IN);

        player.sendPacket(
                new PacketVehicleInteractRsp(
                        player, vehicle.getId(), VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_IN));

        return vehicle;
    }

    /**
     * The player's own living Saurian mount behind this entity, or null if it is not one.
     *
     * <p>Living is part of the gate rather than a nicety: a mount that has just died is held in the scene
     * for the length of its own death animation, and dismounting anybody from it again would restart the
     * client's possession-ended transformation.
     */
    private static EntityVehicle livingMountOf(Player player, GameEntity entity) {
        if (!(entity instanceof EntityVehicle vehicle)) return null;
        if (!player.equals(vehicle.getOwner())) return null;
        if (!isNatsaurusMount(vehicle)) return null;
        return vehicle.isAlive() ? vehicle : null;
    }

    /**
     * Takes the player off whatever Saurian they are riding, if any.
     *
     * <p>The mount stays. A Saurian vehicle with nobody on it <i>is</i> the soul candle, so leaving it in
     * the scene is what "the dragon I just rode off becomes a candle" means - and it holds when the player
     * abandons one mount for another, not only when they dismount outright. Killing it here instead made
     * switching rides look like the previous dragon died.
     */
    public static void eject(Player player) {
        if (player == null || player.getScene() == null) return;

        for (var entity : new ArrayList<>(player.getScene().getEntities().values())) {
            var vehicle = livingMountOf(player, entity);
            if (vehicle == null) continue;

            for (var member : new ArrayList<>(vehicle.getVehicleMembers())) {
                dismount(player, vehicle, member);
            }
        }
    }

    /**
     * Takes the player off their Saurian and removes the mount, for when the ride is ended by being moved
     * somewhere else rather than by dismounting.
     *
     * <p>{@link #eject} deliberately leaves the mount in the scene, because a Saurian with nobody on it is
     * the soul candle the player can ride again. That is wrong here: the scene is about to be re-sent to
     * the client after the transfer, the candle comes back with it, and a client that is still in vehicle
     * control re-parents its camera onto it and plays it as a killed dragon - the input then goes to an
     * entity nowhere near the character and nothing recovers it. Leaving one behind only makes sense where
     * the player can still walk up to it.
     */
    public static void takeAway(Player player) {
        if (player == null || player.getScene() == null) return;

        for (var entity : new ArrayList<>(player.getScene().getEntities().values())) {
            var vehicle = livingMountOf(player, entity);
            if (vehicle == null) continue;

            for (var member : new ArrayList<>(vehicle.getVehicleMembers())) {
                dismount(player, vehicle, member);
            }
            player.getScene().removeEntity(vehicle, VisionType.VisionType_VISION_REMOVE);
        }
    }

    /**
     * Tells one rider they are off. Split out of {@link #eject} so the vehicle's own death can use it, and
     * only after that death has been announced - see {@code EntityVehicle.onDeath} for what happens when
     * this arrives while the mount is still on screen.
     */
    public static void dismount(Player player, EntityVehicle vehicle, VehicleMember member) {
        player.sendPacket(
                new PacketVehicleInteractRsp(
                        vehicle, member, VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_OUT));
        vehicle.getVehicleMembers().remove(member);
        player.getStaminaManager()
                .handleVehicleInteractReq(
                        player.getSession(),
                        vehicle.getId(),
                        VehicleInteractType.VehicleInteractType_VEHICLE_INTERACT_OUT);
    }
}
