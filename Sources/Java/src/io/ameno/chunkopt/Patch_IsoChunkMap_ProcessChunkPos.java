package io.ameno.chunkopt;

import me.zed_0xff.zombie_buddy.Accessor;
import me.zed_0xff.zombie_buddy.Patch;

import java.lang.reflect.Field;

@Patch(className = "zombie.iso.IsoChunkMap", methodName = "ProcessChunkPos")
public class Patch_IsoChunkMap_ProcessChunkPos {

    public static volatile boolean resolved = false;
    public static Field f_worldX;
    public static Field f_worldY;
    public static Field f_playerId;
    public static int chunkGridWidth;
    public static Class<?> isoPlayerClass;
    public static boolean resolvedOK = false;

    public static void resolve() {
        if (resolved) return;
        synchronized (Patch_IsoChunkMap_ProcessChunkPos.class) {
            if (resolved) return;
            try {
                Class<?> mapClass = Class.forName("zombie.iso.IsoChunkMap");
                f_worldX = Accessor.findField(mapClass, "worldX");
                f_worldY = Accessor.findField(mapClass, "worldY");
                f_playerId = Accessor.findField(mapClass, "playerId");
                Field f_cgw = Accessor.findField(mapClass, "chunkGridWidth");

                if (f_worldX != null) f_worldX.setAccessible(true);
                if (f_worldY != null) f_worldY.setAccessible(true);
                if (f_playerId != null) f_playerId.setAccessible(true);
                if (f_cgw != null) {
                    f_cgw.setAccessible(true);
                    chunkGridWidth = f_cgw.getInt(null);
                }

                isoPlayerClass = Class.forName("zombie.characters.IsoPlayer");
                resolvedOK = (f_worldX != null && f_worldY != null);
            } catch (Exception e) {
                System.err.println("[ChunkOptimizer] ProcessChunkPos resolve error: " + e.getMessage());
            }
            resolved = true;
        }
    }


    @Patch.OnEnter(skipOn = true)
    public static boolean enter(@Patch.This Object self, @Patch.Argument(0) Object chr) {
        resolve();
        if (!resolvedOK) return false; // fall through to vanilla

        try {
            float x1 = ((Number) Accessor.callNoArg(chr, "getX")).floatValue();
            float y1 = ((Number) Accessor.callNoArg(chr, "getY")).floatValue();
            int z = (int) Math.floor(((Number) Accessor.callNoArg(chr, "getZ")).floatValue());

            Object localPlayer = Accessor.callNoArg(isoPlayerClass, "getInstance");
            if (localPlayer != null) {
                Object vehicle = Accessor.callNoArg(localPlayer, "getVehicle");
                if (vehicle != null) {
                    float speed = Math.abs(
                        ((Number) Accessor.callNoArg(vehicle, "getCurrentSpeedKmHour")).floatValue());
                    float s = speed / ChunkOptimizerConfig.PROCESS_CHUNK_SPEED_DIVISOR;

                    boolean isDriving = (boolean) Accessor.callNoArg(localPlayer, "isDriving");
                    if (!isDriving) {
                        s = Math.min(s * 2.0f, ChunkOptimizerConfig.PROCESS_CHUNK_PASSENGER_MAX);
                    }

                    float forwardX = ((Number) Accessor.callNoArg(localPlayer,
                        "getForwardDirectionX")).floatValue();
                    float forwardY = ((Number) Accessor.callNoArg(localPlayer,
                        "getForwardDirectionY")).floatValue();

                    x1 += (float) Math.round(forwardX * s);
                    y1 += (float) Math.round(forwardY * s);
                }
            }

            int x = (int) Math.floor(x1 / 8.0f);
            int y = (int) Math.floor(y1 / 8.0f);

            int currentWorldX = f_worldX.getInt(self);
            int currentWorldY = f_worldY.getInt(self);

            if (x == currentWorldX && y == currentWorldY) {
                return true; // No change, skip vanilla
            }

            Class<?> lockClass = Class.forName("java.util.concurrent.locks.ReentrantLock");
            Class<?> mapClass = self.getClass();

            Field f_bsc = Accessor.findField(mapClass, "bSettingChunk");
            if (f_bsc == null) return false;
            f_bsc.setAccessible(true);
            java.util.concurrent.locks.ReentrantLock bSettingChunk =
                (java.util.concurrent.locks.ReentrantLock) f_bsc.get(null);

            bSettingChunk.lock();
            boolean changed = false;
            try {
                if (Math.abs(x - currentWorldX) >= chunkGridWidth
                        || Math.abs(y - currentWorldY) >= chunkGridWidth) {
                    bSettingChunk.unlock();
                    return false;
                } else if (x != currentWorldX) {
                    if (x < currentWorldX) {
                        Accessor.callNoArg(self, "LoadLeft");
                    } else {
                        Accessor.callNoArg(self, "LoadRight");
                    }
                    changed = true;
                } else if (y != currentWorldY) {
                    if (y < currentWorldY) {
                        Accessor.callNoArg(self, "LoadUp");
                    } else {
                        Accessor.callNoArg(self, "LoadDown");
                    }
                    changed = true;
                }
            } finally {
                bSettingChunk.unlock();
                if (changed) {
                    Accessor.callNoArg(self, "calculateZExtentsForChunkMap");
                }
            }

            if (ChunkOptimizerConfig.DEBUG && changed) {
                System.out.println("[ChunkOptimizer] ProcessChunkPos: enhanced look-ahead triggered "
                    + "chunk load (" + currentWorldX + "," + currentWorldY
                    + ") -> (" + x + "," + y + ")");
            }

            return true;

        } catch (Exception e) {
            if (ChunkOptimizerConfig.DEBUG) {
                System.err.println("[ChunkOptimizer] ProcessChunkPos error: " + e.getMessage());
            }
            return false;
        }
    }
}
