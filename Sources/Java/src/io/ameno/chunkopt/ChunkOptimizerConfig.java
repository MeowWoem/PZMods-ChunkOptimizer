package io.ameno.chunkopt;

/**
 * Configuration constants for the chunk loading optimizer.
 * Tune these values to adjust the trade-off between loading speed and frame time.
 */
public final class ChunkOptimizerConfig {

    private ChunkOptimizerConfig() {}

    public static boolean DEBUG = true;

    // --- WorldStreamer patches ---
    public static int MAX_CHUNKS_PER_ITERATION = 4;

    public static long BUSY_SLEEP_MS = 5;

    public static long IDLE_NO_PLAYER_SLEEP_MS = 0;

    public static long IDLE_WITH_PLAYER_SLEEP_MS = 50;

    // --- ChunkComparator look-ahead ---
    public static float LOOK_AHEAD_MIN_TILES = 10.0f;

    public static float LOOK_AHEAD_MAX_TILES = 40.0f;

    public static float LOOK_AHEAD_MAX_SPEED_KMH = 100.0f;

    // --- IsoChunkMap.updateInternal budget ---
    public static float FINALIZE_BUDGET_MS = 8.0f;

    public static int FINALIZE_MIN_CHUNKS = 1;

    // --- IsoChunkMap.ProcessChunkPos look-ahead ---
    public static float PROCESS_CHUNK_SPEED_DIVISOR = 3.0f;

    public static float PROCESS_CHUNK_PASSENGER_MAX = 30.0f;
}
