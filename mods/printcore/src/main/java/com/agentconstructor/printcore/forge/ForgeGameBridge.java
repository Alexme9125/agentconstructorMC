package com.agentconstructor.printcore.forge;

import com.agentconstructor.printcore.api.PrintConfig;
import com.agentconstructor.printcore.api.ServerStatus;
import com.agentconstructor.printcore.config.PrintCoreConfig;
import com.agentconstructor.printcore.engine.GameBridge;
import com.agentconstructor.printcore.job.JobManager;
import com.agentconstructor.printcore.job.SchematicStore;
import com.agentconstructor.printcore.world.BlockRegistry;
import com.agentconstructor.printcore.world.MinecraftWorldAdapter;
import com.agentconstructor.printcore.world.WorldEditor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ForgeGameBridge implements GameBridge {
    private final MinecraftServer server;
    private final PrintConfig config;
    private final JobManager jobs;

    public ForgeGameBridge(MinecraftServer server) {
        this.server = server;
        this.config = currentConfig();
        this.jobs = new JobManager(this.config, new SchematicStore());
    }

    public static PrintConfig currentConfig() {
        Set<String> dimensions = new HashSet<>(PrintCoreConfig.ALLOWED_DIMENSIONS.get());
        return new PrintConfig(
                PrintCoreConfig.BIND.get(),
                PrintCoreConfig.PORT.get(),
                PrintCoreConfig.TOKEN.get(),
                PrintCoreConfig.MAX_MODULE_SIZE.get(),
                PrintCoreConfig.BLOCKS_PER_TICK.get(),
                PrintCoreConfig.MAX_VOLUME.get(),
                PrintCoreConfig.MAX_READ_VOLUME.get(),
                PrintCoreConfig.MAX_SPARSE.get(),
                -64,
                319,
                94,
                223,
                dimensions,
                PrintCoreConfig.ALLOW_FORCE_OVERWRITE.get(),
                PrintCoreConfig.ALLOW_OUT_OF_BOUNDS.get(),
                false
        );
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
        return new MinecraftWorldAdapter(server.overworld());
    }

    @Override
    public WorldEditor editor(String dimension) {
        ResourceLocation location = ResourceLocation.tryParse(dimension == null ? "minecraft:overworld" : dimension);
        if (location == null) {
            throw new IllegalArgumentException("bad dimension " + dimension);
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
        if (level == null) {
            throw new IllegalArgumentException("unknown dimension " + dimension);
        }
        return new MinecraftWorldAdapter(level);
    }

    @Override
    public ServerStatus status() {
        List<String> dimensions = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            dimensions.add(level.dimension().location().toString());
        }
        return new ServerStatus(
                "0.1.0",
                "1.20.1",
                true,
                20.0,
                0.0,
                jobs.queueSize(),
                dimensions
        );
    }

    @Override
    public <T> T callOnGameThread(Callable<T> task) {
        if (server.isSameThread()) {
            try {
                return task.call();
            } catch (Exception e) {
                throw wrap(e);
            }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                future.complete(task.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw wrap(e);
        }
    }

    private static RuntimeException wrap(Exception e) {
        if (e instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException(e);
    }
}
