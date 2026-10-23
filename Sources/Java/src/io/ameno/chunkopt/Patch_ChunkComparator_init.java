package io.ameno.chunkopt;

import me.zed_0xff.zombie_buddy.Accessor;
import me.zed_0xff.zombie_buddy.Patch;

import java.lang.reflect.Field;


@Patch(className = "zombie.iso.WorldStreamer$ChunkComparator", methodName = "init")
public class Patch_ChunkComparator_init {

    @Patch.OnExit
    public static void exit(@Patch.This Object self) {
        try {
            Class<?> compClass = self.getClass();
            Field posField = Accessor.findField(compClass, "pos");
            if (posField == null) return;
            posField.setAccessible(true);
            Object[] pos = (Object[]) posField.get(self);
            if (pos == null) return;

            Class<?> playerClass = Class.forName("zombie.characters.IsoPlayer");
            Field playersField = Accessor.findField(playerClass, "players");
            if (playersField == null) return;
            playersField.setAccessible(true);
            Object[] players = (Object[]) playersField.get(null);

            for (int playerIndex = 0; playerIndex < 4 && playerIndex < pos.length; ++playerIndex) {
                Object vec = pos[playerIndex];
                if (vec == null) continue;

                if (playerIndex >= players.length || players[playerIndex] == null) continue;
                Object player = players[playerIndex];

                Object vehicle = null;
                try {
                    vehicle = Accessor.callNoArg(player, "getVehicle");
                } catch (Exception ignored) {}

                if (vehicle == null) continue;

                float speed = 0;
                try {
                    Object speedObj = Accessor.callNoArg(vehicle, "getCurrentSpeedKmHour");
                    speed = Math.abs(((Number) speedObj).floatValue());
                } catch (Exception ignored) {
                    continue;
                }

                if (speed < 5.0f) continue; // Not moving fast enough to matter

                float t = Math.min(speed / ChunkOptimizerConfig.LOOK_AHEAD_MAX_SPEED_KMH, 1.0f);
                float lookAhead = ChunkOptimizerConfig.LOOK_AHEAD_MIN_TILES
                    + t * (ChunkOptimizerConfig.LOOK_AHEAD_MAX_TILES - ChunkOptimizerConfig.LOOK_AHEAD_MIN_TILES);

                float playerX, playerY, lastX, lastY;
                try {
                    playerX = ((Number) Accessor.callNoArg(player, "getX")).floatValue();
                    playerY = ((Number) Accessor.callNoArg(player, "getY")).floatValue();
                    lastX = ((Number) Accessor.callNoArg(player, "getLastX")).floatValue();
                    lastY = ((Number) Accessor.callNoArg(player, "getLastY")).floatValue();
                } catch (Exception ignored) {
                    continue;
                }

                if (playerX != lastX || playerY != lastY) {
                    float dx = playerX - lastX;
                    float dy = playerY - lastY;
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len > 0.001f) {
                        dx /= len;
                        dy /= len;
                    }
                    Accessor.trySet(vec, "x", playerX + dx * lookAhead);
                    Accessor.trySet(vec, "y", playerY + dy * lookAhead);
                } else {
                    // Stationary - vanilla just uses current pos, no change needed
                }
            }
        } catch (Exception e) {
            if (ChunkOptimizerConfig.DEBUG) {
                System.err.println("[ChunkOptimizer] ChunkComparator.init patch error: " + e.getMessage());
            }
        }
    }
}
