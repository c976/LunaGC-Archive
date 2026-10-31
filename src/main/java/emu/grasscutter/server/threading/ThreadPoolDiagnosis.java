package emu.grasscutter.server.threading;

/**
 * What a pool's numbers say, paired with what to do about it.
 *
 * <p>The two travel together on purpose. Keeping the finding and its advice as separate strings
 * invites a lookup that matches one against the other, and such a lookup fails silently the moment
 * either string is reworded.
 */
public enum ThreadPoolDiagnosis {
    NONE("no bottleneck found", "keep watching"),
    ELEVATED_LOAD("load is rising", "keep watching queue depth, CPU and memory"),
    REJECTED_TASKS("tasks are being rejected", "submit more slowly or raise the pool's capacity"),
    SLOW_TASKS("average task time is too high", "check database and downstream response times"),
    QUEUE_BACKLOG("the queue is backing up", "check how fast tasks arrive, then widen the queue or the pool"),
    THREADS_SATURATED("every thread is busy", "find the hot task and reconsider the maximum thread count"),
    FAILING_TASKS("some tasks are throwing", "read the exception log and fix the failing task"),
    CPU_LIMIT("CPU is near its limit", "find the hot task and submit less often"),
    MEMORY_PRESSURE("memory use is too high", "check caches, object lifetimes and GC pressure");

    private final String description;
    private final String suggestion;

    ThreadPoolDiagnosis(String description, String suggestion) {
        this.description = description;
        this.suggestion = suggestion;
    }

    public String description() {
        return this.description;
    }

    public String suggestion() {
        return this.suggestion;
    }
}
