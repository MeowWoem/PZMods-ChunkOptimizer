package io.ameno.chunkopt;

import me.zed_0xff.zombie_buddy.Accessor;
import me.zed_0xff.zombie_buddy.Patch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.locks.ReentrantLock;

@Patch(className = "zombie.iso.IsoChunkMap", methodName = "ProcessChunkPos")
public class Patch_IsoChunkMap_ProcessChunkPos {

    public static volatile boolean resolved = false;
    public static Field f_worldX;
    public static Field f_worldY;
    public static int chunkGridWidth;
    public static Method m_isoPlayerGetInstance;
    public static ReentrantLock bSettingChunk;
    public static boolean resolvedOK = false;

    public static void resolve() {
        if (resolved) return;
        synchronized (Patch_IsoChunkMap_ProcessChunkPos.class) {
            if (resolved) return;
            try {
                Class<?> mapClass = Class.forName("zombie.iso.IsoChunkMap");
                f_worldX = Accessor.findField(mapClass, "worldX");
                f_worldY = Accessor.findField(mapClass, "worldY");
                Field f_cgw = Accessor.findField(mapClass, "chunkGridWidth");
                Field f_bsc = Accessor.findField(mapClass, "bSettingChunk");

                if (f_worldX != null) f_worldX.setAccessible(true);
                if (f_worldY != null) f_worldY.setAccessible(true);
                if (f_cgw != null) {
                    f_cgw.setAccessible(true);
                    chunkGridWidth = f_cgw.getInt(null);
                }
                if (f_bsc != null) {
                    f_bsc.setAccessible(true);
                    bSettingChunk = (ReentrantLock) f_bsc.get(null);
                }

                m_isoPlayerGetInstance = Class.forName("zombie.characters.IsoPlayer").getDeclaredMethod("getInstance");
                m_isoPlayerGetInstance.setAccessible(true);

                resolvedOK = f_worldX != null && f_worldY != null && bSettingChunk != null && chunkGridWidth > 0;
            } catch (Exception e) {
                System.err.println("[ChunkOptimizer] ProcessChunkPos resolve error: " + e);
            }
            resolved = true;
        }
    }


    @Patch.OnEnter(skipOn = true)
    public static boolean enter(@Patch.This Object self, @Patch.Argument(0) Object chr) {
        resolve();
        if (!resolvedOK) return false;

        try {
            float x1 = ((Number) Accessor.callNoArg(chr, "getX")).floatValue();
            float y1 = ((Number) Accessor.callNoArg(chr, "getY")).floatValue();

            Object localPlayer = m_isoPlayerGetInstance.invoke(null);
            if (localPlayer != null) {
                Object vehicle = Accessor.callNoArg(localPlayer, "getVehicle");
                if (vehicle != null) {
                    float speed = ((Number) Accessor.callNoArg(vehicle, "getCurrentSpeedKmHour")).floatValue();
                    float s = speed / ChunkOptimizerConfig.PROCESS_CHUNK_SPEED_DIVISOR;

                    boolean isDriving = (Boolean) Accessor.callNoArg(localPlayer, "isDriving");
                    if (!isDriving) {
                        s = Math.min(s * 2.0f, ChunkOptimizerConfig.PROCESS_CHUNK_PASSENGER_MAX);
                    }

                    float forwardX = ((Number) Accessor.callNoArg(localPlayer, "getForwardDirectionX")).floatValue();
                    float forwardY = ((Number) Accessor.callNoArg(localPlayer, "getForwardDirectionY")).floatValue();

                    x1 += (float) Math.round(forwardX * s);
                    y1 += (float) Math.round(forwardY * s);
                }
            }

            int x = (int) Math.floor(x1 / 8.0f);
            int y = (int) Math.floor(y1 / 8.0f);

            int currentWorldX = f_worldX.getInt(self);
            int currentWorldY = f_worldY.getInt(self);

            if (x == currentWorldX && y == currentWorldY) {
                return true;
            }

            if (Math.abs(x - currentWorldX) >= chunkGridWidth
                    || Math.abs(y - currentWorldY) >= chunkGridWidth) {
                return false;
            }

            boolean changed = false;
            bSettingChunk.lock();
            try {
                if (x != currentWorldX) {
                    Accessor.callNoArg(self, x < currentWorldX ? "LoadLeft" : "LoadRight");
                    changed = true;
                } else {
                    Accessor.callNoArg(self, y < currentWorldY ? "LoadUp" : "LoadDown");
                    changed = true;
                }
            } finally {
                bSettingChunk.unlock();
                if (changed) {
                    Accessor.callNoArg(self, "calculateZExtentsForChunkMap");
                }
            }

            if (ChunkOptimizerConfig.DEBUG) {
                System.out.println("[ChunkOptimizer] ProcessChunkPos: enhanced look-ahead triggered "
                    + "chunk load (" + currentWorldX + "," + currentWorldY
                    + ") -> (" + x + "," + y + ")");
            }

            return true;

        } catch (Exception e) {
            if (ChunkOptimizerConfig.DEBUG) {
                System.err.println("[ChunkOptimizer] ProcessChunkPos error: " + e);
            }
            return false;
        }
    }
}