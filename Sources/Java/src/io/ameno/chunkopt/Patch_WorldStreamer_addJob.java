package io.ameno.chunkopt;

import me.zed_0xff.zombie_buddy.Accessor;
import me.zed_0xff.zombie_buddy.Patch;


@Patch(className = "zombie.iso.WorldStreamer", methodName = "addJob")
public class Patch_WorldStreamer_addJob {

    @Patch.OnExit
    public static void exit(@Patch.This Object self) {
        try {
            Thread wsThread = Accessor.tryGet(self, "worldStreamer", null);
            if (wsThread != null && wsThread.isAlive()) {
                wsThread.interrupt();
            }
        } catch (Exception ignored) {
            
        }
    }
}
