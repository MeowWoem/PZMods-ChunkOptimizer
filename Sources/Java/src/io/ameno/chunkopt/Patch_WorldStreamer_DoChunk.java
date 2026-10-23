package io.ameno.chunkopt;

import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.iso.WorldStreamer", methodName = "DoChunk")
public class Patch_WorldStreamer_DoChunk {

    @Patch.OnEnter
    public static void enter() {
        if (!ChunkOptimizerConfig.STATS) return;
        ChunkOptimizerStats.doChunkStart.get()[0] = System.nanoTime();
    }

    @Patch.OnExit
    public static void exit() {
        if (!ChunkOptimizerConfig.STATS) return;
        long[] start = ChunkOptimizerStats.doChunkStart.get();
        if (start[0] == 0) return;
        ChunkOptimizerStats.recordStreamerChunk(System.nanoTime() - start[0]);
        start[0] = 0;
    }
}