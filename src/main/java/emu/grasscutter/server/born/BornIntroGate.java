package emu.grasscutter.server.born;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.quest.QuestChapterBootstrap;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketEnterScenePeerNotify;
import emu.grasscutter.server.packet.send.PacketEnterSceneReadyRsp;
import emu.grasscutter.server.packet.send.PacketPlayerEnterSceneNotify;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates fresh-player handoff from birth data to the first playable quest scene. */
public final class BornIntroGate {
    private enum Mode {
        /** Wait for the 7.1 client-side post-born intro before entering the world. */
        NATIVE_INTRO,
        /** Character selection/native intro was skipped; only wait for scene-ready quest bootstrap. */
        SCENE_READY_ONLY
    }

    // Keep bootstrap state by UID so a replacement session can complete the same first-scene
    // lifecycle if the client reconnects while a cold first login is still finishing.
    private static final Map<Integer, State> FRESH_PLAYER_BOOTSTRAPS = new ConcurrentHashMap<>();

    private static final class State {
        private final Mode mode;
        private final GameSession originSession;
        private boolean sawUnpaused;
        private int completedPauseCycles;
        private boolean cutoverStarted;
        private boolean earlySceneEntrySent;
        private GameSession earlySceneEntrySession;
        private boolean worldLoginComplete;
        private boolean questStarted;
        private boolean sceneReadyDeferred;
        private GameSession deferredSceneReadySession;

        private State(Mode mode, GameSession originSession) {
            this.mode = mode;
            this.originSession = originSession;
        }
    }

    private BornIntroGate() {}

    private static int uidOf(GameSession session) {
        var player = session == null ? null : session.getPlayer();
        return player == null ? 0 : player.getUid();
    }

    private static State stateFor(GameSession session) {
        int uid = uidOf(session);
        return uid > 0 ? FRESH_PLAYER_BOOTSTRAPS.get(uid) : null;
    }

    private static void remove(GameSession session) {
        int uid = uidOf(session);
        if (uid > 0) FRESH_PLAYER_BOOTSTRAPS.remove(uid);
    }

    /** Begins the native 7.1 post-born intro gate after SetPlayerBornData succeeds. */
    public static void armNativeIntro(GameSession session) {
        int uid = uidOf(session);
        if (uid <= 0) return;
        FRESH_PLAYER_BOOTSTRAPS.put(uid, new State(Mode.NATIVE_INTRO, session));
    }

    /** Compatibility alias for the existing native-intro caller. */
    public static void arm(GameSession session) {
        armNativeIntro(session);
    }

    /**
     * Begins automatic/skip-intro birth. World login starts normally, while fresh quests remain
     * gated until the first PostEnterSceneRsp has reached the client.
     */
    public static void armSceneReady(GameSession session) {
        int uid = uidOf(session);
        if (uid <= 0) return;

        State state = new State(Mode.SCENE_READY_ONLY, session);
        state.cutoverStarted = true;
        FRESH_PLAYER_BOOTSTRAPS.put(uid, state);
    }

    /** Marks Player.onLogin complete and resumes an early EnterSceneReady request if necessary. */
    public static void markWorldLoginComplete(GameSession session) {
        State state = stateFor(session);
        if (state == null) return;

        GameSession deferredReadySession = null;
        synchronized (state) {
            state.worldLoginComplete = true;
            if (state.sceneReadyDeferred) {
                deferredReadySession = state.deferredSceneReadySession;
                state.sceneReadyDeferred = false;
                state.deferredSceneReadySession = null;
            }
        }

        if (deferredReadySession != null) resumeSceneReady(deferredReadySession);
    }

    /** True while either fresh-player path owns the first-scene quest bootstrap. */
    public static boolean isFreshPlayerBootstrap(GameSession session) {
        return stateFor(session) != null;
    }

    /** Compatibility alias used by existing call sites such as HomeWorld. */
    public static boolean isAwaiting(GameSession session) {
        return isFreshPlayerBootstrap(session);
    }

    /**
     * Native birth sends scene-entry early at the intro cutover. Suppress only the later duplicate
     * emitted by Player.onLogin on that same connection. Automatic birth never uses this path.
     */
    public static boolean shouldSuppressLoginSceneEntry(GameSession session) {
        State state = stateFor(session);
        if (state == null || state.mode != Mode.NATIVE_INTRO) return false;
        synchronized (state) {
            return state.earlySceneEntrySent && state.earlySceneEntrySession == session;
        }
    }

    /**
     * The native 7.1 second intro emits two false->true PlayerSetPauseReq cycles after born data.
     * The second cycle is the protocol-visible world-entry boundary. Automatic birth never feeds
     * this state machine.
     */
    public static void notePause(GameSession session, boolean paused) {
        if (session == null) return;

        State state = stateFor(session);
        if (state == null || state.mode != Mode.NATIVE_INTRO) return;

        boolean enterWorld = false;
        synchronized (state) {
            if (state.originSession != session || state.cutoverStarted) return;

            if (!paused) {
                state.sawUnpaused = true;
                return;
            }
            if (!state.sawUnpaused) return;

            state.sawUnpaused = false;
            if (++state.completedPauseCycles >= 2) {
                state.cutoverStarted = true;
                enterWorld = true;
            }
        }

        if (enterWorld) enterWorldAfterNativeIntro(session);
    }

    private static void enterWorldAfterNativeIntro(GameSession session) {
        var player = session.getPlayer();
        if (player == null) {
            remove(session);
            return;
        }

        State state = stateFor(session);
        if (state == null || state.mode != Mode.NATIVE_INTRO) return;

        try {
            session.send(new PacketPlayerEnterSceneNotify(player));
            synchronized (state) {
                state.earlySceneEntrySent = true;
                state.earlySceneEntrySession = session;
            }

            // Cold first login can be slow. Keep packet handling alive while the world is built.
            Grasscutter.getThreadPool().submit(() -> completeNativeWorldLogin(session, player));
        } catch (Throwable t) {
            synchronized (state) {
                state.cutoverStarted = false;
            }
            Grasscutter.getLogger()
                    .error(
                            "Failed to schedule world login after native fresh-player intro for uid {}.",
                            player.getUid(),
                            t);
        }
    }

    private static void completeNativeWorldLogin(
            GameSession session, emu.grasscutter.game.player.Player player) {
        State state = stateFor(session);
        if (state == null || state.mode != Mode.NATIVE_INTRO) return;

        try {
            synchronized (player) {
                player.onLogin();
            }
            markWorldLoginComplete(session);
        } catch (Throwable t) {
            synchronized (state) {
                state.cutoverStarted = false;
            }
            Grasscutter.getLogger()
                    .error(
                            "Failed to enter the world after native fresh-player intro for uid {}.",
                            player.getUid(),
                            t);
        }
    }

    /** Returns true when EnterSceneReadyReq is retained until Player.onLogin completes. */
    public static boolean deferSceneReadyUntilLoginComplete(GameSession session) {
        State state = stateFor(session);
        if (state == null) return false;

        synchronized (state) {
            if (state.worldLoginComplete) return false;
            state.sceneReadyDeferred = true;
            state.deferredSceneReadySession = session;
        }
        return true;
    }

    private static void resumeSceneReady(GameSession session) {
        if (session == null) return;
        var player = session.getPlayer();
        if (player == null || player.getWorld() == null) return;

        session.send(new PacketEnterScenePeerNotify(player));
        session.send(new PacketEnterSceneReadyRsp(player));
    }

    /** Starts the fresh-player quest lifecycle exactly once after PostEnterSceneRsp. */
    public static void finishOnSceneReady(GameSession session) {
        if (session == null) return;

        State state = stateFor(session);
        if (state == null) return;
        synchronized (state) {
            if (!state.worldLoginComplete || state.questStarted) return;
            state.questStarted = true;
        }

        var player = session.getPlayer();
        if (player == null) {
            remove(session);
            return;
        }

        try {
            // Player.onLogin already sent the full new-player quest snapshot. onPlayerBorn creates
            // the visible prologue through normal incremental updates; then restore any hidden
            // chapter controller linked to the active main-quest series from ChapterData.
            player.getQuestManager().onPlayerBorn();
            QuestChapterBootstrap.startForActiveMainQuests(player.getQuestManager());
            remove(session);
        } catch (Throwable t) {
            synchronized (state) {
                state.questStarted = false;
            }
            Grasscutter.getLogger()
                    .error(
                            "Failed to start fresh-player quests after first scene entry for uid {}.",
                            player.getUid(),
                            t);
        }
    }
}
