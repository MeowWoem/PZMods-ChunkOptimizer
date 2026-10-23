package io.ameno.chunkopt;

import me.zed_0xff.zombie_buddy.Accessor;
import me.zed_0xff.zombie_buddy.Patch;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Patches WorldStreamer.threadLoop() to process multiple chunks per iteration
 * instead of just one.
 *
 * Vanilla: processes exactly 1 chunk from jobList per call to threadLoop().
 * Patched: pre-processes up to (MAX_CHUNKS_PER_ITERATION - 1) extra chunks
 *          before vanilla handles its single chunk.
 *
 * This runs on the World Streamer thread, same as vanilla.
 */
@Patch(className = "zombie.iso.WorldStreamer", methodName = "threadLoop")
public class Patch_WorldStreamer_threadLoop {

    // Cached reflection fields for performance (resolved once)
    private static volatile boolean fieldsResolved = false;
    private static Field f_jobList;
    private static Field f_jobQueue;
    private static Field f_comp;
    private static Class<?> chunkSaveWorkerClass;
    private static Class<?> isoChunkMapClass;

    private static void resolveFields() {
        if (fieldsResolved) return;
        synchronized (Patch_WorldStreamer_threadLoop.class) {
            if (fieldsResolved) return;
            try {
                Class<?> wsClass = Class.forName("zombie.iso.WorldStreamer");
                f_jobList = Accessor.findField(wsClass, "jobList");
                f_jobQueue = Accessor.findField(wsClass, "jobQueue");
                f_comp = Accessor.findField(wsClass, "comp");
                if (f_jobList != null) f_jobList.setAccessible(true);
                if (f_jobQueue != null) f_jobQueue.setAccessible(true);
                if (f_comp != null) f_comp.setAccessible(true);
                chunkSaveWorkerClass = Class.forName("zombie.iso.ChunkSaveWorker");
                isoChunkMapClass = Class.forName("zombie.iso.IsoChunkMap");
            } catch (Exception e) {
                System.err.println("[ChunkOptimizer] Failed to resolve fields: " + e.getMessage());
            }
            fieldsResolved = true;
        }
    }

    @SuppressWarnings("unchecked")
    @Patch.OnEnter
    public static void enter(@Patch.This Object self) {
        if (ChunkOptimizerConfig.MAX_CHUNKS_PER_ITERATION <= 1) return;

        resolveFields();
        if (f_jobList == null || f_jobQueue == null) return;

        try {
            Stack<Object> jobList = (Stack<Object>) f_jobList.get(self);
            ConcurrentLinkedQueue<Object> jobQueue = (ConcurrentLinkedQueue<Object>) f_jobQueue.get(self);

            if (jobList == null || jobQueue == null) return;

            // Drain jobQueue into jobList (same as vanilla does first)
            Object chunk = jobQueue.poll();
            while (chunk != null) {
                if (!jobList.contains(chunk)) {
                    jobList.add(chunk);
                }
                chunk = jobQueue.poll();
            }

            // Nothing to do
            if (jobList.size() <= 1) return;

            // Clean up dead-ref chunks (same as vanilla)
            for (int i = jobList.size() - 1; i >= 0; --i) {
                Object c = jobList.get(i);
                Object refs = Accessor.tryGet(c, "refs", null);
                if (refs != null && ((java.util.Collection<?>) refs).isEmpty()) {
                    jobList.remove(i);
                    Accessor.callNoArg(c, "resetForStore");
                    Object chunkStore = Accessor.tryGet(isoChunkMapClass, "chunkStore", null);
                    if (chunkStore != null) {
                        ((ConcurrentLinkedQueue<Object>) chunkStore).add(c);
                    }
                }
            }

            if (jobList.size() <= 1) return;

            // Sort by distance to player (closest last = highest priority)
            Object comp = f_comp.get(null); // static field
            if (comp != null) {
                try {
                    Accessor.callNoArg(comp, "init");
                    Collections.sort((List<Object>) jobList, (Comparator<Object>) comp);
                } catch (Exception ignored) {}
            }

            // Get ChunkSaveWorker.instance for save coordination
            Object csw_instance = Accessor.tryGet(chunkSaveWorkerClass, "instance", null);

            // Process up to (MAX - 1) extra chunks
            // Leave at least 1 for vanilla to process (its own sort + DoChunk flow)
            int extra = Math.min(jobList.size() - 1, ChunkOptimizerConfig.MAX_CHUNKS_PER_ITERATION - 1);

            for (int i = 0; i < extra; i++) {
                if (jobList.isEmpty()) break;
                Object extraChunk = jobList.remove(jobList.size() - 1);

                // Double-check refs
                Object refs = Accessor.tryGet(extraChunk, "refs", null);
                if (refs == null || ((java.util.Collection<?>) refs).isEmpty()) {
                    Accessor.callNoArg(extraChunk, "resetForStore");
                    Object chunkStore = Accessor.tryGet(isoChunkMapClass, "chunkStore", null);
                    if (chunkStore != null) {
                        ((ConcurrentLinkedQueue<Object>) chunkStore).add(extraChunk);
                    }
                    continue;
                }

                // Coordinate with ChunkSaveWorker (same as vanilla does before DoChunk)
                if (csw_instance != null) {
                    try {
                        Accessor.callByName(csw_instance, "Update", extraChunk);
                    } catch (Exception ignored) {}
                }

                // Load the chunk (runs on world streamer thread = correct context)
                try {
                    Accessor.callByName(self, "DoChunk", extraChunk, null);
                } catch (Exception e) {
                    if (ChunkOptimizerConfig.DEBUG) {
                        System.err.println("[ChunkOptimizer] Error in extra DoChunk: " + e.getMessage());
                    }
                }
            }

            if (ChunkOptimizerConfig.DEBUG && extra > 0) {
                System.out.println("[ChunkOptimizer] Processed " + extra
                    + " extra chunk(s), " + jobList.size() + " remaining");
            }

        } catch (Exception e) {
            if (ChunkOptimizerConfig.DEBUG) {
                System.err.println("[ChunkOptimizer] threadLoop patch error: " + e.getMessage());
            }
        }
    }
}
