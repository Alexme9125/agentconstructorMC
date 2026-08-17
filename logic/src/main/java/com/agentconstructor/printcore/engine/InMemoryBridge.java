package com.agentconstructor.printcore.engine;

import com.agentconstructor.printcore.api.PrintConfig;
import com.agentconstructor.printcore.api.ServerStatus;
import com.agentconstructor.printcore.job.JobManager;
import com.agentconstructor.printcore.job.SchematicStore;
import com.agentconstructor.printcore.world.BlockRegistry;
import com.agentconstructor.printcore.world.InMemoryWorld;
import com.agentconstructor.printcore.world.WorldEditor;

import java.util.List;
import java.util.concurrent.Callable;

public final class InMemoryBridge implements GameBridge {
    private final PrintConfig config;
    private final InMemoryWorld world;
    private final JobManager jobs;

    public InMemoryBridge(PrintConfig config, InMemoryWorld world) {
        this.config = config;
        this.world = world;
        this.jobs = new JobManager(config, new SchematicStore());
    }

    public InMemoryWorld world() {
        return world;
    }

    @Override
    public PrintConfig config() {
        return config;
    }

    @Override
    public JobManager jobs() {
        return jobs;
    }

    @Override
    public SchematicStore schematics() {
        return jobs.schematics();
    }

    @Override
    public BlockRegistry registry() {
        return world;
    }

    @Override
    public WorldEditor editor(String dimension) {
        if (dimension != null && !dimension.equals(world.dimension())) {
            throw new IllegalArgumentException("unknown dimension " + dimension);
        }
        return world;
    }

    @Override
    public ServerStatus status() {
        return new ServerStatus(
                "0.1.0",
                "1.20.1-inmemory",
                true,
                20.0,
                0.0,
                jobs.queueSize(),
                List.of(world.dimension())
        );
    }

    @Override
    public <T> T callOnGameThread(Callable<T> task) {
        try {
            return task.call();
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e);
        }
    }
}
