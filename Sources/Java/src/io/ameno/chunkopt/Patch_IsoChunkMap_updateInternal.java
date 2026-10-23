package io.ameno.chunkopt;

import me.zed_0xff.zombie_buddy.Accessor;
import me.zed_0xff.zombie_buddy.Patch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Patch(className = "zombie.iso.IsoChunkMap", methodName = "updateInternal")
public class Patch_IsoChunkMap_updateInternal {

    public static volatile boolean resolved = false;
    public static Field f_loadGridSquare;
    public static Field f_ignore;
    public static Field f_chunkGridWidth;
    public static Field f_playerId;
    public static Field f_loaded;
    public static Field f_vehicles;
    public static Field f_wx;
    public static Field f_wy;
    public static ReentrantLock bSettingChunk;
    public static Object worldReuserInstance;
    public static Class<?> isoPlayerClass;
    public static Class<?> isoWorldClass;
    public static Class<?> gameClientClass;
    public static Class<?> gameServerClass;
    public static Class<?> vehicleCacheClass;
    public static Class<?> vehicleManagerClass;
    public static int chunkGridWidth;
    public static boolean resolvedOK = false;

    @SuppressWarnings("unchecked")
    public static void resolve() {
        if (resolved) return;
        synchronized (Patch_IsoChunkMap_updateInternal.class) {
            if (resolved) return;
            try {
                Class<?> isoChunkClass = Class.forName("zombie.iso.IsoChunk");
                Class<?> isoChunkMapClass = Class.forName("zombie.iso.IsoChunkMap");

                f_loadGridSquare = Accessor.findField(isoChunkClass, "loadGridSquare");
                f_loaded = Accessor.findField(isoChunkClass, "loaded");
                f_wx = Accessor.findField(isoChunkClass, "wx");
                f_wy = Accessor.findField(isoChunkClass, "wy");
                f_vehicles = Accessor.findField(isoChunkClass, "vehicles");

                f_ignore = Accessor.findField(isoChunkMapClass, "ignore");
                f_chunkGridWidth = Accessor.findField(isoChunkMapClass, "chunkGridWidth");
                f_playerId = Accessor.findField(isoChunkMapClass, "playerId");

                Field f_bSettingChunk = Accessor.findField(isoChunkMapClass, "bSettingChunk");
                if (f_bSettingChunk != null) {
                    f_bSettingChunk.setAccessible(true);
                    bSettingChunk = (ReentrantLock) f_bSettingChunk.get(null);
                }

                if (f_loadGridSquare != null) f_loadGridSquare.setAccessible(true);
                if (f_loaded != null) f_loaded.setAccessible(true);
                if (f_wx != null) f_wx.setAccessible(true);
                if (f_wy != null) f_wy.setAccessible(true);
                if (f_vehicles != null) f_vehicles.setAccessible(true);
                if (f_ignore != null) f_ignore.setAccessible(true);
                if (f_chunkGridWidth != null) {
                    f_chunkGridWidth.setAccessible(true);
                    chunkGridWidth = f_chunkGridWidth.getInt(null);
                }
                if (f_playerId != null) f_playerId.setAccessible(true);

                isoPlayerClass = Class.forName("zombie.characters.IsoPlayer");
                isoWorldClass = Class.forName("zombie.iso.IsoWorld");
                gameClientClass = Class.forName("zombie.network.GameClient");
                gameServerClass = Class.forName("zombie.network.GameServer");

                try {
                    vehicleCacheClass = Class.forName("zombie.vehicles.VehicleCache");
                    vehicleManagerClass = Class.forName("zombie.vehicles.VehicleManager");
                } catch (Exception ignored) {}

                try {
                    Class<?> worldReuserClass = Class.forName("zombie.iso.WorldReuserThread");
                    worldReuserInstance = Accessor.tryGet(worldReuserClass, "instance", null);
                } catch (Exception ignored) {}

                resolvedOK = (f_loadGridSquare != null && f_ignore != null && bSettingChunk != null);
            } catch (Exception e) {
                System.err.println("[ChunkOptimizer] updateInternal resolve error: " + e.getMessage());
            }
            resolved = true;
        }
    }

    @SuppressWarnings("unchecked")
    @Patch.OnEnter(skipOn = true)
    public static boolean enter(@Patch.This Object self) {
        resolve();
        if (!resolvedOK) return false;

        try {
            
            Object loadQueue = f_loadGridSquare.get(null);

            int queueSize = ((Number) Accessor.callNoArg(loadQueue, "size")).intValue();
            if (queueSize == 0) {
                doPostLoop(self, false);
                return true;
            }

            int count = 1 + queueSize * 3 / chunkGridWidth;
            boolean bChanged = false;
            int processed = 0;
            long startNanos = System.nanoTime();

            Object isoWorld = Accessor.tryGet(isoWorldClass, "instance", null);
            Object currentCell = (isoWorld != null) ?
                Accessor.callNoArg(isoWorld, "getCell") : null;
            Object[] chunkMaps = null;
            int numPlayers = 0;
            if (currentCell != null) {
                chunkMaps = Accessor.tryGet(currentCell, "chunkMap", null);
                numPlayers = (int) Accessor.tryGet(isoPlayerClass, "numPlayers", 1);
            }

            boolean isClient = (boolean) Accessor.tryGet(gameClientClass, "client", false);

            while (count > 0) {
                if (processed >= ChunkOptimizerConfig.FINALIZE_MIN_CHUNKS) {
                    float elapsedMs = (System.nanoTime() - startNanos) / 1_000_000f;
                    if (elapsedMs >= ChunkOptimizerConfig.FINALIZE_BUDGET_MS) {
                        if (ChunkOptimizerConfig.DEBUG) {
                            System.out.println("[ChunkOptimizer] Budget hit: "
                                + String.format("%.1f", elapsedMs) + "ms, "
                                + processed + "/" + (processed + count)
                                + " chunks finalized, " + Accessor.callNoArg(loadQueue, "size") + " deferred");
                        }
                        break;
                    }
                }

                Object chunk = Accessor.callNoArg(loadQueue, "poll");
                if (chunk != null) {
                    boolean loaded = false;

                    if (chunkMaps != null) {
                        for (int n = 0; n < numPlayers; ++n) {
                            Object cm = chunkMaps[n];
                            if (cm == null) continue;
                            boolean ignore = (boolean) f_ignore.get(cm);
                            if (ignore) continue;
                            try {
                                Object result = Accessor.callByName(cm, "setChunkDirect", chunk, false);
                                if (result instanceof Boolean && (Boolean) result) {
                                    loaded = true;
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    if (!loaded) {
                        if (worldReuserInstance != null) {
                            try {
                                Accessor.callByName(worldReuserInstance, "addReuseChunk", chunk);
                            } catch (Exception ignored) {}
                        }
                        --count;
                        continue;
                    }

                    f_loaded.set(chunk, true);
                    bSettingChunk.lock();
                    try {
                        Accessor.callNoArg(chunk, "doLoadGridsquare");
                        bChanged = true;

                        if (isClient && vehicleCacheClass != null) {
                            try {
                                int cwx = f_wx.getInt(chunk);
                                int cwy = f_wy.getInt(chunk);
                                Object vehicles = Accessor.callByName(vehicleCacheClass,
                                    "vehicleGet", cwx, cwy);
                                if (vehicles instanceof List) {
                                    List<?> vList = (List<?>) vehicles;
                                    Object vmInstance = Accessor.tryGet(vehicleManagerClass,
                                        "instance", null);
                                    if (vmInstance != null) {
                                        for (Object vc : vList) {
                                            short vid = (short) Accessor.tryGet(vc, "id", (short) 0);
                                            Accessor.callByName(vmInstance,
                                                "sendVehicleRequest", vid, (short) 1);
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    } finally {
                        bSettingChunk.unlock();
                    }

                    try {
                        Object[] players = Accessor.tryGet(isoPlayerClass, "players", null);
                        if (players != null) {
                            for (int i = 0; i < numPlayers; ++i) {
                                if (players[i] != null) {
                                    Accessor.trySet(players[i],
                                        "dirtyRecalcGridStackTime", 20.0f);
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    processed++;
                }
                --count;
            }

            doPostLoop(self, bChanged);
            return true; // skip vanilla

        } catch (Exception e) {
            if (ChunkOptimizerConfig.DEBUG) {
                System.err.println("[ChunkOptimizer] updateInternal error: " + e.getMessage());
                e.printStackTrace();
            }
            return false;
        }
    }

    public static void doPostLoop(Object self, boolean bChanged) {
        try {
            if (bChanged) {
                Accessor.callNoArg(self, "calculateZExtentsForChunkMap");
            }

            boolean isClient = (boolean) Accessor.tryGet(gameClientClass, "client", false);
            boolean isServer = (boolean) Accessor.tryGet(gameServerClass, "server", false);
            boolean hotSave = !isClient && !isServer;

            if (hotSave) {
                Object freq = Accessor.tryGet(self, "hotSaveFrequency", null);
                if (freq != null) {
                    Object check = Accessor.callNoArg(freq, "Check");
                    hotSave = (check instanceof Boolean) && (Boolean) check;
                }
            }

            for (int y = 0; y < chunkGridWidth; ++y) {
                for (int x = 0; x < chunkGridWidth; ++x) {
                    Object chunk = Accessor.callByName(self, "getChunk", x, y);
                    if (chunk == null) continue;
                    Accessor.callNoArg(chunk, "update");

                    if (hotSave) {
                        boolean requiresHotSave = (boolean) Accessor.tryGet(chunk,
                            "requiresHotSave", false);
                        if (requiresHotSave) {
                            Class<?> cswClass = Class.forName("zombie.iso.ChunkSaveWorker");
                            Object cswInstance = Accessor.tryGet(cswClass, "instance", null);
                            if (cswInstance != null) {
                                Object toSaveQueue = Accessor.tryGet(cswInstance, "toSaveQueue", null);
                                if (toSaveQueue instanceof java.util.Collection
                                        && ((java.util.Collection<?>) toSaveQueue).size() < 10) {
                                    Accessor.callByName(cswInstance, "AddHotSave", chunk);
                                    Accessor.trySet(chunk, "requiresHotSave", false);
                                }
                            }
                        }
                    }
                }
            }

            boolean isClient2 = (boolean) Accessor.tryGet(gameClientClass, "client", false);
            if (isClient2) {
                Object checkFreq = Accessor.tryGet(self, "checkVehiclesFrequency", null);
                if (checkFreq != null) {
                    Object check = Accessor.callNoArg(checkFreq, "Check");
                    if (check instanceof Boolean && (Boolean) check) {
                        Accessor.callNoArg(self, "checkVehicles");
                    }
                }
            }
        } catch (Exception e) {
            if (ChunkOptimizerConfig.DEBUG) {
                System.err.println("[ChunkOptimizer] doPostLoop error: " + e.getMessage());
            }
        }
    }
}