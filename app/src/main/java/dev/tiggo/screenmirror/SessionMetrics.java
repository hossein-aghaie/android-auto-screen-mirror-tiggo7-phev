package dev.tiggo.screenmirror;

/** Monotonic send counters; not a measurement of car-display refresh or latency. */
final class SessionMetrics {
    private boolean active;
    private long start;
    private long end;
    private long sampleTime;
    private long sampleFrames;
    private double fps;

    void start(long now) { active = true; start = now; end = now; sampleTime = now; sampleFrames = 0; fps = 0; }
    void stop(long now) { if (active) end = Math.max(start, now); active = false; fps = 0; }
    long durationMs(long now) { return Math.max(0, (active ? now : end) - start); }
    double sample(long now, long sentFrames) {
        if (!active) return 0;
        long elapsed = now - sampleTime;
        if (sentFrames < sampleFrames || elapsed < 0) { sampleTime = now; sampleFrames = sentFrames; fps = 0; }
        else if (elapsed >= 800) {
            fps = Math.max(0, (sentFrames - sampleFrames) * 1000.0 / elapsed);
            sampleTime = now;
            sampleFrames = sentFrames;
        }
        return fps;
    }
    static String clock(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }
    static boolean canForwardInput(boolean active, boolean surface, boolean enabled,
            boolean sessionIsWhole, boolean dimensionsMatch, boolean locked, boolean placement, long ageMs) {
        return active && surface && enabled && sessionIsWhole && dimensionsMatch && !locked && placement && ageMs >= 0 && ageMs < 2500;
    }
}
