package emu.grasscutter.scripts;

import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.ScriptArgs;
import java.util.*;
import lombok.*;

@Getter
@RequiredArgsConstructor
public final class SceneTimeAxis {
    private final Timer timer = new Timer();

    private final SceneScriptManager handle;
    private final int groupId;

    private final String identifier;
    /** Delay from ScriptLib.InitTimeAxis — official lua units are seconds. */
    private final int delay;
    private final boolean loop;

    /** Schedules the task to run. */
    public void start() {
        // java.util.Timer expects milliseconds; lua InitTimeAxis passes seconds.
        long delayMs = Math.max(0, (long) this.delay) * 1000L;
        if (this.loop) {
            this.timer.scheduleAtFixedRate(new Task(), delayMs, delayMs);
        } else {
            this.timer.schedule(new Task(), delayMs);
        }
    }

    /** Terminates a repeating task. */
    public void stop() {
        this.timer.cancel();
    }

    final class Task extends TimerTask {
        @Override
        public void run() {
            // Invoke script event.
            SceneTimeAxis.this.handle.callEvent(
                    new ScriptArgs(SceneTimeAxis.this.groupId, EventType.EVENT_TIME_AXIS_PASS)
                            .setEventSource(SceneTimeAxis.this.identifier));
        }
    }
}
