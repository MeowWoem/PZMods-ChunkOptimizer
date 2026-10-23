package io.ameno.chunkopt;

import me.zed_0xff.zombie_buddy.Accessor;
import me.zed_0xff.zombie_buddy.Patch;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ConcurrentLinkedQueue;

@Patch(className = "zombie.iso.WorldStreamer", methodName = "threadLoop")
public class Patch_WorldStreamer_threadLoop {

    public static volatile boolean fieldsResolved = false;
    public static Field f_jobList;
    public static Field f_jobQueue;
    public static Field f_comp;
    public static Field f_pendingRequests1;
    public static Class<?> chunkSaveWorkerClass;
    public static Class<?> isoChunkMapClass;

    public static boolean idleResolvedOK = false;
    public static Object cswInstance;
    public static Field f_cswSaving;
    public static Field f_toSaveQueue;
    public static Method m_cswUpdate;
    public static Field f_gcClient;
    public static Field f_doWorldSyncEnable;
    public static Field f_loadedAsClient;
    public static Method m_playerDbIsAvailable;
    public static Method m_playerDbGetInstance;
    public static Method m_playerDbUpdateWorldStreamer;
    public static Object vehiclesDbInstance;
    public static Method m_vehiclesDbUpdateWorldStreamer;
    public static Method m_isoPlayerGetInstance;

    public static void resolveFields() {
        if (fieldsResolved) return;
        synchronized (Patch_WorldStreamer_threadLoop.class) {
            if (fieldsResolved) return;
            try {
                Class<?> wsClass = Class.forName("zombie.iso.WorldStreamer");
                f_jobList = Accessor.findField(wsClass, "jobList");
                f_jobQueue = Accessor.findField(wsClass, "jobQueue");
                f_comp = Accessor.findField(wsClass, "comp");
                f_pendingRequests1 = Accessor.findField(wsClass, "pendingRequests1");
                if (f_jobList != null) f_jobList.setAccessible(true);
                if (f_jobQueue != null) f_jobQueue.setAccessible(true);
                if (f_comp != null) f_comp.setAccessible(true);
                if (f_pendingRequests1 != null) f_pendingRequests1.setAccessible(true);
                chunkSaveWorkerClass = Class.forName("zombie.iso.ChunkSaveWorker");
                isoChunkMapClass = Class.forName("zombie.iso.IsoChunkMap");
            } catch (Exception e) {
                System.err.println("[ChunkOptimizer] Failed to resolve fields: " + e.getMessage());
            }
            try {
                Class<?> isoChunkClass = Class.forName("zombie.iso.IsoChunk");

                Field f_cswInstance = chunkSaveWorkerClass.getDeclaredField("instance");
                f_cswInstance.setAccessible(true);
                cswInstance = f_cswInstance.get(null);
                f_cswSaving = chunkSaveWorkerClass.getDeclaredField("saving");
                f_cswSaving.setAccessible(true);
                f_toSaveQueue = chunkSaveWorkerClass.getDeclaredField("toSaveQueue");
                f_toSaveQueue.setAccessible(true);
                m_cswUpdate = chunkSaveWorkerClass.getDeclaredMethod("Update", isoChunkClass);
                m_cswUpdate.setAccessible(true);

                f_gcClient = Class.forName("zombie.network.GameClient").getDeclaredField("client");
                f_doWorldSyncEnable = Class.forName("zombie.SystemDisabler").getDeclaredField("doWorldSyncEnable");
                f_loadedAsClient = Class.forName("zombie.GameWindow").getDeclaredField("loadedAsClient");
                f_gcClient.setAccessible(true);
                f_doWorldSyncEnable.setAccessible(true);
                f_loadedAsClient.setAccessible(true);

                Class<?> playerDbClass = Class.forName("zombie.savefile.PlayerDB");
                m_playerDbIsAvailable = playerDbClass.getDeclaredMethod("isAvailable");
                m_playerDbGetInstance = playerDbClass.getDeclaredMethod("getInstance");
                m_playerDbUpdateWorldStreamer = playerDbClass.getDeclaredMethod("updateWorldStreamer");
                m_playerDbIsAvailable.setAccessible(true);
                m_playerDbGetInstance.setAccessible(true);
                m_playerDbUpdateWorldStreamer.setAccessible(true);

                Class<?> vehiclesDbClass = Class.forName("zombie.vehicles.VehiclesDB2");
                Field f_vdbInstance = vehiclesDbClass.getDeclaredField("instance");
                f_vdbInstance.setAccessible(true);
                vehiclesDbInstance = f_vdbInstance.get(null);
                m_vehiclesDbUpdateWorldStreamer = vehiclesDbClass.getDeclaredMethod("updateWorldStreamer");
                m_vehiclesDbUpdateWorldStreamer.setAccessible(true);

                m_isoPlayerGetInstance = Class.forName("zombie.characters.IsoPlayer").getDeclaredMethod("getInstance");
                m_isoPlayerGetInstance.setAccessible(true);

                idleResolvedOK = f_jobList != null && f_jobQueue != null && f_pendingRequests1 != null
                    && cswInstance != null && vehiclesDbInstance != null;
            } catch (Exception e) {
                System.err.println("[ChunkOptimizer] Failed to resolve idle wakeup fields, using vanilla sleep: " + e);
            }
            fieldsResolved = true;
        }
    }

    @SuppressWarnings("unchecked")
    @Patch.OnEnter(skipOn = true)
    public static boolean enter(@Patch.This Object self) {
        ChunkOptimizerWakeup.beginIteration();
        resolveFields();
        if (f_jobList == null || f_jobQueue == null) return false;

        if (ChunkOptimizerConfig.STATS) {
            sampleQueues(self);
        }
        processExtraChunks(self);
        return handleIdle(self);
    }

    public static void sampleQueues(Object self) {
        try {
            int pending = ((Collection<?>) f_jobList.get(self)).size()
                + ((Collection<?>) f_jobQueue.get(self)).size();
            ChunkOptimizerStats.max(ChunkOptimizerStats.streamerQueueMax, pending);
            if (cswInstance != null && f_toSaveQueue != null) {
                ChunkOptimizerStats.max(ChunkOptimizerStats.saveQueueMax,
                    ((Collection<?>) f_toSaveQueue.get(cswInstance)).size());
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    public static void processExtraChunks(Object self) {
        if (ChunkOptimizerConfig.MAX_CHUNKS_PER_ITERATION <= 1) return;

        try {
            Stack<Object> jobList = (Stack<Object>) f_jobList.get(self);
            ConcurrentLinkedQueue<Object> jobQueue = (ConcurrentLinkedQueue<Object>) f_jobQueue.get(self);

            if (jobList == null || jobQueue == null) return;

            Object chunk = jobQueue.poll();
            while (chunk != null) {
                if (!jobList.contains(chunk)) {
                    jobList.add(chunk);
                }
                chunk = jobQueue.poll();
            }

            if (jobList.size() <= 1) return;

            for (int i = jobList.size() - 1; i >= 0; --i) {
                Object c = jobList.get(i);
                Object refs = Accessor.tryGet(c, "refs", null);
                if (refs != null && ((Collection<?>) refs).isEmpty()) {
                    jobList.remove(i);
                    returnToChunkStore(c);
                }
            }

            if (jobList.size() <= 1) return;

            Object comp = f_comp.get(null);
            if (comp != null) {
                try {
                    Accessor.callNoArg(comp, "init");
                    Collections.sort((List<Object>) jobList, (Comparator<Object>) comp);
                } catch (Exception ignored) {}
            }

            Object csw_instance = Accessor.tryGet(chunkSaveWorkerClass, "instance", null);

            int extra = Math.min(jobList.size() - 1, ChunkOptimizerConfig.MAX_CHUNKS_PER_ITERATION - 1);

            for (int i = 0; i < extra; i++) {
                if (jobList.isEmpty()) break;
                Object extraChunk = jobList.remove(jobList.size() - 1);

                Object refs = Accessor.tryGet(extraChunk, "refs", null);
                if (refs == null || ((Collection<?>) refs).isEmpty()) {
                    returnToChunkStore(extraChunk);
                    continue;
                }

                if (csw_instance != null) {
                    try {
                        Accessor.callByName(csw_instance, "Update", extraChunk);
                    } catch (Exception ignored) {}
                }

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

    public static void returnToChunkStore(Object chunk) throws ReflectiveOperationException {
        Accessor.callNoArg(chunk, "resetForStore");
        Object chunkStore = Accessor.tryGet(isoChunkMapClass, "chunkStore", null);
        if (chunkStore != null) {
            Accessor.callByName(chunkStore, "add", chunk);
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean handleIdle(Object self) {
        if (!idleResolvedOK) return false;

        boolean started = false;
        try {
            boolean isClient = f_gcClient.getBoolean(null);
            if (isClient && !f_doWorldSyncEnable.getBoolean(null)) return false;

            if (!((Collection<?>) f_jobQueue.get(self)).isEmpty()) return false;
            if (!((Collection<?>) f_jobList.get(self)).isEmpty()) return false;

            started = true;
            m_cswUpdate.invoke(cswInstance, (Object) null);
            if (f_cswSaving.getBoolean(cswInstance)) return true;

            if (!((Collection<?>) f_pendingRequests1.get(self)).isEmpty()) {
                ChunkOptimizerWakeup.waitForWork(20);
                return true;
            }

            ChunkOptimizerWakeup.waitForWork(ChunkOptimizerConfig.IDLE_SLEEP_MS);

            if (!isClient && !f_loadedAsClient.getBoolean(null)
                    && (Boolean) m_playerDbIsAvailable.invoke(null)) {
                Object playerDb = m_playerDbGetInstance.invoke(null);
                m_playerDbUpdateWorldStreamer.invoke(playerDb);
            }
            m_vehiclesDbUpdateWorldStreamer.invoke(vehiclesDbInstance);

            if (m_isoPlayerGetInstance.invoke(null) != null) {
                ChunkOptimizerWakeup.waitForWork(ChunkOptimizerConfig.IDLE_WITH_PLAYER_SLEEP_MS);
            } else {
                ChunkOptimizerWakeup.waitForWork(ChunkOptimizerConfig.IDLE_NO_PLAYER_SLEEP_MS);
            }
            return true;

        } catch (Throwable t) {
            Throwable cause = (t instanceof InvocationTargetException && t.getCause() != null) ? t.getCause() : t;
            System.err.println("[ChunkOptimizer] idle wakeup error: " + cause);
            cause.printStackTrace();
            return started;
        }
    }
}