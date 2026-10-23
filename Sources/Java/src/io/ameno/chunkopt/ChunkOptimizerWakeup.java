package io.ameno.chunkopt;

import java.util.concurrent.locks.LockSupport;

public final class ChunkOptimizerWakeup {

    public static volatile Thread streamerThread;
    public static volatile boolean workPending = false;

    private ChunkOptimizerWakeup() {}

    public static void beginIteration() {
        streamerThread = Thread.currentThread();
        workPending = false;
    }

    public static void signal() {
        workPending = true;
        Thread t = streamerThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    public static void waitForWork(long timeoutMs) {
        if (timeoutMs <= 0) return;
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (!workPending) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            LockSupport.parkNanos(remaining);
            if (Thread.interrupted()) break;
        }
    }
}