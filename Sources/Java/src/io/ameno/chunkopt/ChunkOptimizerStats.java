package io.ameno.chunkopt;

import java.util.concurrent.atomic.AtomicLong;

public final class ChunkOptimizerStats {

    private ChunkOptimizerStats() {}

    public static final AtomicLong streamerChunks = new AtomicLong();
    public static final AtomicLong streamerNanos = new AtomicLong();
    public static final AtomicLong streamerMaxNanos = new AtomicLong();
    public static final AtomicLong streamerQueueMax = new AtomicLong();

    public static final AtomicLong saves = new AtomicLong();
    public static final AtomicLong saveNanos = new AtomicLong();
    public static final AtomicLong saveMaxNanos = new AtomicLong();
    public static final AtomicLong saveQueueMax = new AtomicLong();

    public static final AtomicLong finalized = new AtomicLong();
    public static final AtomicLong finalizeNanos = new AtomicLong();
    public static final AtomicLong finalizeMaxNanos = new AtomicLong();
    public static final AtomicLong finalizeQueueMax = new AtomicLong();
    public static final AtomicLong budgetHits = new AtomicLong();
    public static final AtomicLong frameMaxNanos = new AtomicLong();

    public static final AtomicLong wakeups = new AtomicLong();

    public static volatile long lastReportNanos = System.nanoTime();

    public static final ThreadLocal<long[]> doChunkStart = ThreadLocal.withInitial(() -> new long[1]);
    public static final ThreadLocal<long[]> saveStart = ThreadLocal.withInitial(() -> new long[1]);

    public static void max(AtomicLong target, long value) {
        long cur = target.get();
        while (value > cur && !target.compareAndSet(cur, value)) {
            cur = target.get();
        }
    }

    public static void recordStreamerChunk(long nanos) {
        streamerChunks.incrementAndGet();
        streamerNanos.addAndGet(nanos);
        max(streamerMaxNanos, nanos);
    }

    public static void recordSave(long nanos) {
        saves.incrementAndGet();
        saveNanos.addAndGet(nanos);
        max(saveMaxNanos, nanos);
    }

    public static void recordFinalize(long nanos) {
        finalized.incrementAndGet();
        finalizeNanos.addAndGet(nanos);
        max(finalizeMaxNanos, nanos);
    }

    public static void maybeReport() {
        if (!ChunkOptimizerConfig.STATS) return;
        long now = System.nanoTime();
        long elapsed = now - lastReportNanos;
        if (elapsed < ChunkOptimizerConfig.STATS_INTERVAL_MS * 1_000_000L) return;
        lastReportNanos = now;

        long sc = streamerChunks.getAndSet(0);
        long sn = streamerNanos.getAndSet(0);
        long sm = streamerMaxNanos.getAndSet(0);
        long sq = streamerQueueMax.getAndSet(0);
        long vc = saves.getAndSet(0);
        long vn = saveNanos.getAndSet(0);
        long vm = saveMaxNanos.getAndSet(0);
        long vq = saveQueueMax.getAndSet(0);
        long fc = finalized.getAndSet(0);
        long fn = finalizeNanos.getAndSet(0);
        long fm = finalizeMaxNanos.getAndSet(0);
        long fq = finalizeQueueMax.getAndSet(0);
        long bh = budgetHits.getAndSet(0);
        long frm = frameMaxNanos.getAndSet(0);
        long wk = wakeups.getAndSet(0);

        if (sc == 0 && vc == 0 && fc == 0 && sq == 0 && fq == 0) return;

        System.out.println(String.format(
            "[ChunkOptimizer] %.1fs | streamer: %d chunks, moy %.2fms, max %.2fms, file max %d"
            + " | sauvegardes: %d, moy %.2fms, max %.2fms, file max %d"
            + " | principal: %d finalises, moy %.2fms, max %.2fms, file max %d, budget atteint %dx, updateInternal max %.2fms"
            + " | reveils %d",
            elapsed / 1e9,
            sc, avgMs(sn, sc), sm / 1e6, sq,
            vc, avgMs(vn, vc), vm / 1e6, vq,
            fc, avgMs(fn, fc), fm / 1e6, fq, bh, frm / 1e6,
            wk));
    }

    public static double avgMs(long nanos, long count) {
        return count == 0 ? 0.0 : nanos / 1e6 / count;
    }
}