package emu.grasscutter.server.threading;

/** One pool's state at one instant, as a status readout sees it. */
public record ThreadPoolSnapshot(
        String name,
        ThreadPoolType type,
        long createdAtMillis,
        String lifecycleState,
        int corePoolSize,
        int maximumPoolSize,
        int currentPoolSize,
        int activeCount,
        int queueSize,
        /** -1 when the queue is unbounded. */
        int queueCapacity,
        long completedTaskCount,
        long submittedTaskCount,
        long failedTaskCount,
        long rejectedTaskCount,
        long averageExecutionMillis,
        long maxExecutionMillis,
        ThreadPoolHealth health,
        ThreadPoolDiagnosis diagnosis) {

    public String diagnosisText() {
        return this.diagnosis.description();
    }

    public String suggestion() {
        return this.diagnosis.suggestion();
    }
}
