package emu.grasscutter.game.train;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AbilityScalarValueEntryOuterClass.AbilityScalarValueEntry;
import emu.grasscutter.net.proto.AbilityStringOuterClass.AbilityString;
import emu.grasscutter.net.proto.BMJNJLKCODGOuterClass.BMJNJLKCODG;
import emu.grasscutter.net.proto.SnezhnayaTrainCallNotifyOuterClass.SnezhnayaTrainCallNotify;
import emu.grasscutter.net.proto.SnezhnayaTrainCallReqOuterClass.SnezhnayaTrainCallReq;
import emu.grasscutter.net.proto.SnezhnayaTrainSetRouteReqOuterClass.SnezhnayaTrainSetRouteReq;
import emu.grasscutter.net.proto.SnezhnayaTrainStateNotifyOuterClass.SnezhnayaTrainState;
import emu.grasscutter.net.proto.SnezhnayaTrainStateNotifyOuterClass.SnezhnayaTrainStateNotify;
import emu.grasscutter.server.packet.send.PacketIceCreature;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.Utils;
import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Snezhnaya player train that Grey Eyes (widget 220133) calls.
 *
 * <p>From an official 7.1 capture: the train is a server-spawned gadget (73094085, no group) whose
 * SceneGadgetInfo field 27 carries its curve state; the client refuses to call when it does not
 * exist ("此处无法召唤列车"). The client owns it and moves it along its own curve data, so the server
 * only answers the call (3893 → 26580), follows the destination (28390) and broadcasts the train
 * position every 2 seconds (23234), taking the position from the client's movement.
 */
public final class SnezhnayaTrainSystem {
    public static final int GREY_EYES_WIDGET = 220133;
    public static final int PLAYER_TRAIN_GADGET = 73094085;
    private static final int SCENE = 3;
    private static final int BROADCAST_MS = 2000;

    /** Where the captured train stood: the park curve of station 1 (Nachalo). */
    private static final int PARK_CURVE = 371200117;
    private static final Position PARK_POS = new Position(7677.914f, 217.74f, 7673.833f);

    /** The train's global values as the capture showed them. */
    private static final Map<String, Float> TRAIN_GLOBALS = new java.util.LinkedHashMap<>();

    static {
        for (int i = 1; i <= 8; i++) TRAIN_GLOBALS.put("SGV_TrainCollection_0" + i, 1f);
        for (int i = 1; i <= 6; i++) TRAIN_GLOBALS.put("SGV_CarriageWallpaper_0" + i, 0f);
        TRAIN_GLOBALS.put("SGV_SnezhnayaTrain_TrainID", 0f);
        TRAIN_GLOBALS.put("SGV_SnezhnayaTrain_PlayerCallingStop", 2f);
    }

    private static final class TrainState {
        int entityId;
        int taskId = -1;
        int curveId;
        int segment;
        int destStation;
        int sourceId;
        List<Integer> stations = List.of();
        List<Integer> reached = List.of();
        SnezhnayaTrainState state = SnezhnayaTrainState.SNEZHNAYA_TRAIN_STATE_NONE;
    }

    private static final Map<Integer, TrainState> TRAINS = new ConcurrentHashMap<>();
    private static volatile Tables tables;

    private SnezhnayaTrainSystem() {}

    /** Gadgets of the Snezhnaya trains (player trains and station trains). */
    private static final java.util.Set<Integer> TRAIN_GADGETS =
            java.util.Set.of(PLAYER_TRAIN_GADGET, 73144055, 73094006, 73144074);

    /** Whether {@code entity} is a train or something one of its abilities created. */
    public static boolean isTrainChain(emu.grasscutter.game.entity.GameEntity entity) {
        while (entity instanceof EntityGadget gadget) {
            if (TRAIN_GADGETS.contains(gadget.getGadgetId())) return true;
            if (gadget.getOwner() == null) return false;
            entity = gadget.getOwner();
        }
        return false;
    }

    /** Players carrying Grey Eyes get their train in Teyvat, parked at Nachalo. */
    public static void onEnterSceneDone(Player player) {
        Scene scene = player.getScene();
        if (scene == null || scene.getId() != SCENE) return;
        if (player.getInventory().getItemCountById(GREY_EYES_WIDGET) <= 0) return;

        var train = TRAINS.computeIfAbsent(player.getUid(), uid -> new TrainState());
        synchronized (train) {
            if (scene.getEntityById(train.entityId) instanceof EntityGadget existing
                    && existing.getGadgetId() == PLAYER_TRAIN_GADGET) {
                return;
            }
            var gadget = new EntityGadget(scene, PLAYER_TRAIN_GADGET, PARK_POS.clone(), new Position());
            TRAIN_GLOBALS.forEach((name, value) -> gadget.getExtraGlobalValues().put(
                    Utils.abilityHash(name),
                    AbilityScalarValueEntry.newBuilder()
                            .setKey(AbilityString.newBuilder().setHash(Utils.abilityHash(name)))
                            .setFloatValue(value)
                            .build()));
            // Parked: on the park curve but not started, so the client neither runs it along the
            // curve nor animates it as moving. A call (3893) hands it the client's curve state.
            gadget.setCurveMoveInfo(BMJNJLKCODG.newBuilder()
                    .setStartTime(scene.getSceneTime())
                    .setEEFABNDBKCO(PARK_CURVE)
                    .setHMHCGLCNBMK(1)
                    .setLGIDIOIEGCI(1)
                    .build());
            scene.addEntity(gadget);
            train.entityId = gadget.getId();
            train.curveId = PARK_CURVE;
            train.segment = 1;
            Grasscutter.getLogger().debug("[Train] uid={} train entity {} parked at {}",
                    player.getUid(), gadget.getId(), PARK_POS);
        }
    }

    /** cmd 3893: Grey Eyes called the train to the curve the player stands by. */
    public static void onCall(Player player, SnezhnayaTrainCallReq req) {
        var info = req.getCurveInfo();
        var train = TRAINS.get(player.getUid());
        int station = tables().stationOf(info.getEEFABNDBKCO());
        Grasscutter.getLogger().debug(
                "[Train] uid={} call: curve {} segment {} progress {} speed {} routes {} -> station {}",
                player.getUid(), info.getEEFABNDBKCO(), info.getHMHCGLCNBMK(), info.getDEEKKONIOPE(),
                info.getSpeed(), info.getBCOFNPNHJCIList().size(), station);
        if (train != null) {
            synchronized (train) {
                train.curveId = info.getEEFABNDBKCO();
                train.segment = info.getHMHCGLCNBMK();
                train.state = SnezhnayaTrainState.SNEZHNAYA_TRAIN_STATE_NONE;
                train.destStation = 0;
                train.stations = List.of();
                train.reached = List.of();
                if (player.getScene() != null
                        && player.getScene().getEntityById(train.entityId) instanceof EntityGadget gadget) {
                    gadget.setCurveMoveInfo(info);
                }
                startBroadcast(player, train);
            }
        }
        player.sendPacket(new PacketIceCreature(
                PacketOpcodes.SnezhnayaTrainCallNotify,
                SnezhnayaTrainCallNotify.newBuilder().setStationId(station).build()));
    }

    /** cmd 28390: the player picked a destination; the train starts running. */
    public static void onSetRoute(Player player, SnezhnayaTrainSetRouteReq req) {
        var train = TRAINS.get(player.getUid());
        var route = new ArrayList<Integer>();
        req.getRouteListList().forEach(r -> route.addAll(r.getPointListList()));
        Grasscutter.getLogger().debug("[Train] uid={} route to station {} via {} stations {}",
                player.getUid(), req.getDestStationId(), route, req.getStationListList());
        if (train == null) return;
        synchronized (train) {
            int calledAt = tables().stationOf(train.curveId);
            train.curveId = tables().nextCurve(train.curveId, route);
            train.destStation = req.getDestStationId();
            train.sourceId = req.getSourceId();
            train.stations = List.copyOf(req.getStationListList());
            var reached = new ArrayList<Integer>();
            for (int s : train.stations) {
                reached.add(s);
                if (s == calledAt) break;
            }
            train.reached = reached;
            train.state = SnezhnayaTrainState.SNEZHNAYA_TRAIN_STATE_RUNNING;
            startBroadcast(player, train);
            sendState(player, train);
        }
    }

    private static void startBroadcast(Player player, TrainState train) {
        Scene scene = player.getScene();
        if (scene == null || train.taskId >= 0) return;
        int ticks = Math.max(1, BROADCAST_MS / Math.max(1, Configuration.GAME_INFO.tickRateMs));
        train.taskId = scene.getScheduler().scheduleRepeatingTask(() -> {
            synchronized (train) {
                if (!player.isOnline() || player.getScene() != scene
                        || !(scene.getEntityById(train.entityId) instanceof EntityGadget)) {
                    scene.getScheduler().cancelTask(train.taskId);
                    train.taskId = -1;
                    return;
                }
                sendState(player, train);
            }
        }, ticks);
    }

    private static void sendState(Player player, TrainState train) {
        var scene = player.getScene();
        if (scene == null || !(scene.getEntityById(train.entityId) instanceof EntityGadget gadget)) return;
        var notify = SnezhnayaTrainStateNotify.newBuilder()
                .setPos(gadget.getPosition().toProto())
                .setCurveId(train.curveId)
                .setSegment(train.segment)
                .setState(train.state);
        if (train.state == SnezhnayaTrainState.SNEZHNAYA_TRAIN_STATE_RUNNING) {
            notify.setDestStationId(train.destStation)
                    .setSourceId(train.sourceId)
                    .addAllStationList(train.stations)
                    .addAllReachedStationList(train.reached);
        }
        player.sendPacket(new PacketIceCreature(PacketOpcodes.SnezhnayaTrainStateNotify, notify.build()));
    }

    static Tables tables() {
        var t = tables;
        if (t == null) {
            synchronized (SnezhnayaTrainSystem.class) {
                if (tables == null) tables = Tables.load();
                t = tables;
            }
        }
        return t;
    }

    /** SnezhnayaTrainStationMapConfigData and SnezhnayaTrackToCurveConfigData, field names obfuscated. */
    static final class Tables {
        /** Curve id → station id (HKHLDFJLLGA lists a station's curves). */
        final Map<Integer, Integer> stationByCurve = new HashMap<>();
        /** Curve id → the curve it leads into (CHIDBFEHEGB group → HGGFDDEOEBD). */
        final Map<Integer, Integer> forwardByCurve = new HashMap<>();

        int stationOf(int curve) {
            return this.stationByCurve.getOrDefault(curve, 0);
        }

        /** The curve after {@code current} on {@code route}: its successor in the list, else its forward link. */
        int nextCurve(int current, List<Integer> route) {
            int i = route.indexOf(current);
            if (i >= 0 && i + 1 < route.size()) return route.get(i + 1);
            var forward = this.forwardByCurve.get(current);
            if (forward != null && route.contains(forward)) return forward;
            return route.isEmpty() ? current : route.get(0);
        }

        static Tables load() {
            var t = new Tables();
            try {
                for (JsonObject row : rows("SnezhnayaTrainStationMapConfigData.json")) {
                    int station = row.has("id") ? row.get("id").getAsInt() : 0;
                    if (station == 0 || !row.has("HKHLDFJLLGA")) continue;
                    for (JsonElement c : row.getAsJsonArray("HKHLDFJLLGA")) t.stationByCurve.put(c.getAsInt(), station);
                }
                for (JsonObject row : rows("SnezhnayaTrackToCurveConfigData.json")) {
                    if (!row.has("CHIDBFEHEGB") || !row.has("HGGFDDEOEBD")) continue;
                    int forward = row.get("HGGFDDEOEBD").getAsInt();
                    for (String c : row.get("CHIDBFEHEGB").getAsString().split(",")) {
                        if (!c.isBlank()) t.forwardByCurve.put(Integer.parseInt(c.trim()), forward);
                    }
                }
            } catch (Exception e) {
                Grasscutter.getLogger().warn("Failed to read the Snezhnaya train tables", e);
            }
            return t;
        }

        private static List<JsonObject> rows(String file) throws Exception {
            var out = new ArrayList<JsonObject>();
            var path = FileUtils.getExcelPath(file);
            if (!Files.isRegularFile(path)) return out;
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root instanceof JsonArray array) {
                    for (JsonElement e : array) if (e.isJsonObject()) out.add(e.getAsJsonObject());
                }
            }
            return out;
        }
    }
}
