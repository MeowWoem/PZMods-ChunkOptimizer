package io.ameno.chunkopt;

import me.zed_0xff.zombie_buddy.Accessor;
import me.zed_0xff.zombie_buddy.Patch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Patch(className = "zombie.iso.IsoChunkMap", methodName = "updateInternal")
public class Patch_IsoChunkMap_updateInternal {

    public static volatile boolean resolved = false;
    public static boolean resolvedOK = false;
    public static boolean clientResolvedOK = false;

    public static Object loadGridSquare;
    public static Method m_queueSize;
    public static Method m_queuePoll;

    public static Field f_chunkLoaded;
    public static Field f_chunkWx;
    public static Field f_chunkWy;
    public static Field f_chunkRequiresHotSave;
    public static Method m_chunkDoLoadGridsquare;
    public static Method m_chunkUpdate;

    public static int chunkGridWidth;
    public static ReentrantLock bSettingChunk;
    public static Field f_mapIgnore;
    public static Field f_mapHotSaveFrequency;
    public static Field f_mapCheckVehiclesFrequency;
    public static Method m_mapSetChunkDirect;
    public static Method m_mapGetChunk;
    public static Method m_mapCalculateZExtents;
    public static Method m_mapCheckVehicles;
    public static Method m_updateLimitCheck;

    public static Field f_worldInstance;
    public static Field f_worldCurrentCell;
    public static Field f_cellChunkMap;

    public static Field f_playerNumPlayers;
    public static Object[] players;
    public static Field f_playerDirtyRecalcGridStackTime;

    public static Field f_gcClient;
    public static Field f_gsServer;

    public static Object worldReuserInstance;
    public static Method m_addReuseChunk;

    public static Object cswInstance;
    public static Field f_toSaveQueue;
    public static Method m_addHotSave;

    public static Method m_vehicleGet;
    public static Field f_vehicleCacheId;
    public static Field f_vehicleManagerInstance;
    public static Method m_sendVehicleRequest;

    public static Method findMethod(Class<?> cls, String name, Class<?>... params) throws NoSuchMethodException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(cls.getName() + "." + name);
    }

    public static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        Field f = Accessor.findField(cls, name);
        if (f == null) throw new NoSuchFieldException(cls.getName() + "." + name);
        f.setAccessible(true);
        return f;
    }

    public static void resolve() {
        if (resolved) return;
        synchronized (Patch_IsoChunkMap_updateInternal.class) {
            if (resolved) return;
            try {
                Class<?> chunkClass = Class.forName("zombie.iso.IsoChunk");
                Class<?> mapClass = Class.forName("zombie.iso.IsoChunkMap");
                Class<?> queueClass = Class.forName("zombie.util.CappedConcurrentQueue");
                Class<?> worldClass = Class.forName("zombie.iso.IsoWorld");
                Class<?> cellClass = Class.forName("zombie.iso.IsoCell");
                Class<?> playerClass = Class.forName("zombie.characters.IsoPlayer");
                Class<?> updateLimitClass = Class.forName("zombie.core.utils.UpdateLimit");
                Class<?> reuserClass = Class.forName("zombie.iso.WorldReuserThread");
                Class<?> cswClass = Class.forName("zombie.iso.ChunkSaveWorker");

                loadGridSquare = findField(chunkClass, "loadGridSquare").get(null);
                m_queueSize = findMethod(queueClass, "size");
                m_queuePoll = findMethod(queueClass, "poll");

                f_chunkLoaded = findField(chunkClass, "loaded");
                f_chunkWx = findField(chunkClass, "wx");
                f_chunkWy = findField(chunkClass, "wy");
                f_chunkRequiresHotSave = findField(chunkClass, "requiresHotSave");
                m_chunkDoLoadGridsquare = findMethod(chunkClass, "doLoadGridsquare");
                m_chunkUpdate = findMethod(chunkClass, "update");

                chunkGridWidth = findField(mapClass, "chunkGridWidth").getInt(null);
                bSettingChunk = (ReentrantLock) findField(mapClass, "bSettingChunk").get(null);
                f_mapIgnore = findField(mapClass, "ignore");
                f_mapHotSaveFrequency = findField(mapClass, "hotSaveFrequency");
                f_mapCheckVehiclesFrequency = findField(mapClass, "checkVehiclesFrequency");
                m_mapSetChunkDirect = findMethod(mapClass, "setChunkDirect", chunkClass, boolean.class);
                m_mapGetChunk = findMethod(mapClass, "getChunk", int.class, int.class);
                m_mapCalculateZExtents = findMethod(mapClass, "calculateZExtentsForChunkMap");
                m_mapCheckVehicles = findMethod(mapClass, "checkVehicles");
                m_updateLimitCheck = findMethod(updateLimitClass, "Check");

                f_worldInstance = findField(worldClass, "instance");
                f_worldCurrentCell = findField(worldClass, "currentCell");
                f_cellChunkMap = findField(cellClass, "chunkMap");

                f_playerNumPlayers = findField(playerClass, "numPlayers");
                players = (Object[]) findField(playerClass, "players").get(null);
                f_playerDirtyRecalcGridStackTime = findField(playerClass, "dirtyRecalcGridStackTime");

                f_gcClient = findField(Class.forName("zombie.network.GameClient"), "client");
                f_gsServer = findField(Class.forName("zombie.network.GameServer"), "server");

                worldReuserInstance = findField(reuserClass, "instance").get(null);
                m_addReuseChunk = findMethod(reuserClass, "addReuseChunk", chunkClass);

                cswInstance = findField(cswClass, "instance").get(null);
                f_toSaveQueue = findField(cswClass, "toSaveQueue");
                m_addHotSave = findMethod(cswClass, "AddHotSave", chunkClass);

                resolvedOK = loadGridSquare != null && bSettingChunk != null && players != null
                    && worldReuserInstance != null && cswInstance != null && chunkGridWidth > 0;
            } catch (Exception e) {
                System.err.println("[ChunkOptimizer] updateInternal resolve error, using vanilla: " + e);
            }

            try {
                Class<?> vehicleCacheClass = Class.forName("zombie.vehicles.VehicleCache");
                Class<?> vehicleManagerClass = Class.forName("zombie.vehicles.VehicleManager");
                m_vehicleGet = findMethod(vehicleCacheClass, "vehicleGet", int.class, int.class);
                f_vehicleCacheId = findField(vehicleCacheClass, "id");
                f_vehicleManagerInstance = findField(vehicleManagerClass, "instance");
                m_sendVehicleRequest = findMethod(vehicleManagerClass, "sendVehicleRequest", short.class, short.class);
                clientResolvedOK = true;
            } catch (Exception e) {
                System.err.println("[ChunkOptimizer] updateInternal client resolve error: " + e);
            }
            resolved = true;
        }
    }

    @Patch.OnEnter(skipOn = true)
    public static boolean enter(@Patch.This Object self) {
        resolve();
        if (!resolvedOK) return false;

        boolean isClient;
        try {
            isClient = f_gcClient.getBoolean(null);
            if (isClient && !clientResolvedOK) return false;
        } catch (Exception e) {
            return false;
        }

        long frameStart = System.nanoTime();
        try {
            int queueSize = (Integer) m_queueSize.invoke(loadGridSquare);
            if (ChunkOptimizerConfig.STATS) {
                ChunkOptimizerStats.max(ChunkOptimizerStats.finalizeQueueMax, queueSize);
            }

            boolean bChanged = false;

            if (queueSize > 0) {
                int count = 1 + queueSize * 3 / chunkGridWidth;
                int processed = 0;

                Object isoWorld = f_worldInstance.get(null);
                Object currentCell = isoWorld != null ? f_worldCurrentCell.get(isoWorld) : null;
                Object[] chunkMaps = currentCell != null ? (Object[]) f_cellChunkMap.get(currentCell) : null;
                int numPlayers = f_playerNumPlayers.getInt(null);

                while (count > 0) {
                    if (processed >= ChunkOptimizerConfig.FINALIZE_MIN_CHUNKS) {
                        float elapsedMs = (System.nanoTime() - frameStart) / 1_000_000f;
                        if (elapsedMs >= ChunkOptimizerConfig.FINALIZE_BUDGET_MS) {
                            if (ChunkOptimizerConfig.STATS) {
                                ChunkOptimizerStats.budgetHits.incrementAndGet();
                            }
                            if (ChunkOptimizerConfig.DEBUG) {
                                System.out.println("[ChunkOptimizer] Budget hit: "
                                    + String.format("%.1f", elapsedMs) + "ms, "
                                    + processed + "/" + (processed + count)
                                    + " chunks finalized, " + m_queueSize.invoke(loadGridSquare) + " deferred");
                            }
                            break;
                        }
                    }

                    Object chunk = m_queuePoll.invoke(loadGridSquare);
                    if (chunk != null) {
                        boolean loaded = false;

                        if (chunkMaps != null) {
                            for (int n = 0; n < numPlayers; ++n) {
                                Object cm = chunkMaps[n];
                                if (cm == null) continue;
                                if (f_mapIgnore.getBoolean(cm)) continue;
                                if ((Boolean) m_mapSetChunkDirect.invoke(cm, chunk, false)) {
                                    loaded = true;
                                }
                            }
                        }

                        if (!loaded) {
                            m_addReuseChunk.invoke(worldReuserInstance, chunk);
                            --count;
                            continue;
                        }

                        f_chunkLoaded.setBoolean(chunk, true);
                        long chunkStart = System.nanoTime();
                        bSettingChunk.lock();
                        try {
                            m_chunkDoLoadGridsquare.invoke(chunk);
                            bChanged = true;

                            if (isClient) {
                                Object vehicles = m_vehicleGet.invoke(null,
                                    f_chunkWx.getInt(chunk), f_chunkWy.getInt(chunk));
                                if (vehicles instanceof List) {
                                    Object vm = f_vehicleManagerInstance.get(null);
                                    for (Object vc : (List<?>) vehicles) {
                                        m_sendVehicleRequest.invoke(vm, f_vehicleCacheId.getShort(vc), (short) 1);
                                    }
                                }
                            }
                        } finally {
                            bSettingChunk.unlock();
                        }
                        if (ChunkOptimizerConfig.STATS) {
                            ChunkOptimizerStats.recordFinalize(System.nanoTime() - chunkStart);
                        }

                        for (int i = 0; i < numPlayers; ++i) {
                            Object p = players[i];
                            if (p != null) {
                                f_playerDirtyRecalcGridStackTime.setFloat(p, 20.0f);
                            }
                        }

                        processed++;
                    }
                    --count;
                }
            }

            doPostLoop(self, bChanged, isClient);

            if (ChunkOptimizerConfig.STATS) {
                ChunkOptimizerStats.max(ChunkOptimizerStats.frameMaxNanos, System.nanoTime() - frameStart);
                ChunkOptimizerStats.maybeReport();
            }
            return true;

        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            System.err.println("[ChunkOptimizer] updateInternal error: " + cause);
            if (ChunkOptimizerConfig.DEBUG) {
                cause.printStackTrace();
            }
            return true;
        }
    }

    public static void doPostLoop(Object self, boolean bChanged, boolean isClient) throws Exception {
        if (bChanged) {
            m_mapCalculateZExtents.invoke(self);
        }

        boolean hotSave = !isClient && !f_gsServer.getBoolean(null)
            && (Boolean) m_updateLimitCheck.invoke(f_mapHotSaveFrequency.get(self));

        for (int y = 0; y < chunkGridWidth; ++y) {
            for (int x = 0; x < chunkGridWidth; ++x) {
                Object chunk = m_mapGetChunk.invoke(self, x, y);
                if (chunk == null) continue;
                m_chunkUpdate.invoke(chunk);

                if (hotSave && f_chunkRequiresHotSave.getBoolean(chunk)
                        && ((Collection<?>) f_toSaveQueue.get(cswInstance)).size() < 10) {
                    m_addHotSave.invoke(cswInstance, chunk);
                    f_chunkRequiresHotSave.setBoolean(chunk, false);
                }
            }
        }

        if (isClient && (Boolean) m_updateLimitCheck.invoke(f_mapCheckVehiclesFrequency.get(self))) {
            m_mapCheckVehicles.invoke(self);
        }
    }
}