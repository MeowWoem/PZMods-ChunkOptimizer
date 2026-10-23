package io.ameno.chunkopt;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.iso.ChunkSaveWorker", methodName = "WriteQueuedSave")
public class Patch_ChunkSaveWorker_WriteQueuedSave {

    @Patch.OnEnter
    public static void enter() {
        if (!ChunkOptimizerConfig.STATS) return;
        ChunkOptimizerStats.saveStart.get()[0] = System.nanoTime();
    }

    @Patch.OnExit
    public static void exit() {
        if (!ChunkOptimizerConfig.STATS) return;
        long[] start = ChunkOptimizerStats.saveStart.get();
        if (start[0] == 0) return;
        ChunkOptimizerStats.recordSave(System.nanoTime() - start[0]);
        start[0] = 0;
    }
}