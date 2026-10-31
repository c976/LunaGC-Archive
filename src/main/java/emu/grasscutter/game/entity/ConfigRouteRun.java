package emu.grasscutter.game.entity;

import emu.grasscutter.data.binout.routes.RoutePoint;
import emu.grasscutter.game.entity.gadget.platform.ConfigRoute;
import emu.grasscutter.game.world.Position;

/** One ordinary distance/velocity route run. Liyue's wait-time route handling is not used here. */
final class ConfigRouteRun {
    final ConfigRoute config;
    final int routeId;
    final int startIndex;
    final int endIndex;
    final int leadMillis;
    final RoutePoint[] points;
    final long[] travelMillis;
    private int advertisedStart;
    private boolean armed;
    private boolean cancelled;
    private int nextIndex;

    private ConfigRouteRun(
            ConfigRoute config,
            int startIndex,
            int endIndex,
            int leadMillis,
            RoutePoint[] points,
            long[] travelMillis) {
        this.config = config;
        this.routeId = config.getRouteId();
        this.startIndex = startIndex;
        this.nextIndex = startIndex;
        this.endIndex = endIndex;
        this.leadMillis = leadMillis;
        this.points = points;
        this.travelMillis = travelMillis;
    }

    /**
     * Whether the route times its points by {@code speedLevel} instead of a target velocity: some
     * point from the resume index on has no velocity at all. About a quarter of 7.1 routes do, and
     * they keep the legacy start path, which still sends the start for the client to animate.
     */
    static boolean usesSpeedLevels(EntityGadget gadget, ConfigRoute config) {
        var route = gadget.getScene().getSceneRouteById(config.getRouteId());
        if (route == null || route.getPoints() == null) return false;
        var points = route.getPoints();
        int start = config.getStartIndex() == points.length - 1 ? 0 : config.getStartIndex();
        if (start < 0) return false;
        for (int i = start; i < points.length; i++) {
            if (points[i] != null && points[i].getTargetVelocity() == 0) return true;
        }
        return false;
    }

    /**
     * Validate before resetting an index, dispatching point zero, scheduling, or publishing state.
     */
    static ConfigRouteRun plan(EntityGadget gadget, ConfigRoute config, RoutePoint[] points) {
        int start = config.getStartIndex() == points.length - 1 ? 0 : config.getStartIndex();
        if (start < 0 || start >= points.length) return null;
        // A configured route's timeline begins at its authored source index, not at an
        // unrepresented spawn-to-source approach leg. Initial approach is before that origin.
        Position previous = points[start] == null ? null : points[start].getPos();
        int lead = 300;
        if (start == 0) {
            if (previous == null
                    || gadget.getPosition() == null
                    || !Float.isFinite(points[start].getTargetVelocity())
                    || points[start].getTargetVelocity() <= 0) return null;
            double approach =
                    Math.ceil(
                            previous.computeDistance(gadget.getPosition())
                                    / points[start].getTargetVelocity()
                                    * 1000.0);
            if (!Double.isFinite(approach) || approach < 0 || approach > Integer.MAX_VALUE)
                return null;
            lead = Math.max(300, (int) approach);
        }
        long[] millis = new long[points.length];
        double seconds = 0;
        for (int i = start; i < points.length; i++) {
            RoutePoint point = points[i];
            // Ordinary routes are distance/positive-velocity routes, not zero-velocity wait points.
            if (point == null
                    || point.getPos() == null
                    || previous == null
                    || !Float.isFinite(point.getTargetVelocity())
                    || point.getTargetVelocity() <= 0) return null;
            seconds += point.getPos().computeDistance(previous) / point.getTargetVelocity();
            double duration = Math.ceil(seconds * 1000.0);
            // Signed modular scene-clock comparisons require a run shorter than 2^31ms including
            // lead.
            if (!Double.isFinite(duration) || duration < 0 || duration + lead > Integer.MAX_VALUE)
                return null;
            millis[i] = (long) duration;
            previous = point.getPos();
        }
        int end = points.length - 1;
        for (int i = start + 1; i < points.length; i++) {
            if (points[i].isReachStop()) {
                end = i;
                break;
            }
        }
        return new ConfigRouteRun(config, start, end, lead, points, millis);
    }

    // Run state is only read/written while holding the owning EntityGadget monitor.
    void arm() {
        advertisedStart = config.getStartSceneTime();
        armed = true;
    }

    void cancel() {
        cancelled = true;
    }

    boolean isArmed() {
        return armed;
    }

    boolean isCancelled() {
        return cancelled;
    }

    boolean isDue(int sceneTime, int index) {
        int elapsed = sceneTime - advertisedStart;
        return elapsed >= 0 && (long) elapsed >= travelMillis[index];
    }

    /** Repeating polls retain their original cancellable ID; clock catch-up cannot fire early. */
    static final class Task implements Runnable {
        private final EntityGadget gadget;
        private final ConfigRouteRun run;
        private final int index;
        private int taskId = -1;
        private boolean fired;

        Task(EntityGadget gadget, ConfigRouteRun run, int index) {
            this.gadget = gadget;
            this.run = run;
            this.index = index;
        }

        void setTaskId(int id) {
            // Setup holds the gadget monitor, as do polls, Stop, and route changes.
            taskId = id;
        }

        @Override
        public void run() {
            synchronized (gadget) {
                if (taskId < 0 || fired) return;
                var scheduler = gadget.getScene().getScheduler();
                if (!gadget.isCurrentConfigRouteRun(run) || run.isCancelled()) {
                    scheduler.cancelTask(taskId);
                    return;
                }
                if (!run.isArmed()) return;
                if (!run.config.isStarted()) {
                    scheduler.cancelTask(taskId);
                    return;
                }
                // ConcurrentHashMap scheduler iteration is not route order. Later points wait
                // even when their deadlines are due, so a stop/final event cannot overtake them.
                if (index != run.nextIndex || !run.isDue(gadget.getScene().getSceneTime(), index))
                    return;
                fired = true;
                run.nextIndex++;
                scheduler.cancelTask(taskId);
                gadget.advanceConfigRoutePoint(run, index);
            }
        }
    }
}
