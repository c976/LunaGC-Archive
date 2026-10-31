package emu.grasscutter.scripts;

import emu.grasscutter.game.quest.LiyueCloudDungeonSupport;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import java.util.HashMap;
import java.util.Map;

/** Scene-owned one-shot timers; refreshing/unloading a group invalidates queued old callbacks too. */
public final class LiyueCloudDungeonTimers {
    private record Key(int group, String source) {}
    private static final class Ticket { int task = -1; }
    private final SceneScriptManager manager;
    private final Map<Key, Ticket> active = new HashMap<>();
    private final Map<ScriptArgs, Ticket> queued = new java.util.IdentityHashMap<>();

    public LiyueCloudDungeonTimers(SceneScriptManager manager) { this.manager = manager; }

    public synchronized int create(int groupId, String source, double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0 || source == null) return 1;
        var group = manager.getGroupById(groupId);
        if (group == null || group.triggers == null || group.triggers.values().stream().noneMatch(t ->
                t.getEvent() == EventType.EVENT_TIMER_EVENT && source.equals(t.getSource()))) return 1;
        cancel(groupId, source);
        var key = new Key(groupId, source);
        var ticket = new Ticket();
        active.put(key, ticket);
        ticket.task = manager.getScene().getScheduler().scheduleDelayedTask(() -> fire(key, ticket),
                LiyueCloudDungeonSupport.ticksForSeconds(seconds));
        return 0;
    }

    private void fire(Key key, Ticket ticket) {
        ScriptArgs args;
        synchronized (this) {
            if (active.get(key) != ticket || manager.isDestroyed()) return;
            ticket.task = -1;
            args = new ScriptArgs(key.group(), EventType.EVENT_TIMER_EVENT).setEventSource(key.source());
            queued.put(args, ticket);
        }
        // Outside this lock: callEvent can take the script manager's lock, and the manager's destroy
        // holds that lock while clearing these timers.
        manager.callEvent(args);
    }

    public synchronized boolean consume(ScriptArgs args) {
        var ticket = queued.remove(args);
        var key = new Key(args.getGroupId(), args.getEventSource());
        if (ticket == null || active.get(key) != ticket || manager.isDestroyed()) return false;
        active.remove(key); // Before native Lua runs, so a same-name replacement cannot be cancelled afterwards.
        return true;
    }

    public synchronized int cancel(int group, String source) {
        var ticket = active.remove(new Key(group, source));
        if (ticket != null) {
            if (ticket.task >= 0) manager.getScene().getScheduler().cancelTask(ticket.task);
            queued.values().removeIf(v -> v == ticket);
        }
        return 0;
    }

    public synchronized void cancelGroup(int group) {
        for (var key : java.util.List.copyOf(active.keySet()))
            if (key.group() == group || (group == 201052009 && key.group() >= 201052010
                    && key.group() <= 201052012)) cancel(key.group(), key.source());
    }

    public synchronized void clear() {
        for (var key : java.util.List.copyOf(active.keySet())) cancel(key.group(), key.source());
        queued.clear();
    }
}
