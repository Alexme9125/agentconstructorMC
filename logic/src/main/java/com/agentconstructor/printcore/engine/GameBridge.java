package com.agentconstructor.printcore.engine;

import com.agentconstructor.printcore.api.PrintConfig;
import com.agentconstructor.printcore.api.ServerStatus;
import com.agentconstructor.printcore.job.JobManager;
import com.agentconstructor.printcore.job.SchematicStore;
import com.agentconstructor.printcore.world.BlockRegistry;
import com.agentconstructor.printcore.world.WorldEditor;

import java.util.concurrent.Callable;

/**
 * Glue between the HTTP/job engine and a game (or in-memory) backend.
 * Forge implements this by hopping onto the server thread.
 */
public interface GameBridge {
    PrintConfig config();

    JobManager jobs();

    SchematicStore schematics();

    BlockRegistry registry();

    WorldEditor editor(String dimension);

    ServerStatus status();

    <T> T callOnGameThread(Callable<T> task);

    default void runOnGameThread(Runnable task) {
        callOnGameThread(() -> {
            task.run();
            return null;
        });
    }
}
