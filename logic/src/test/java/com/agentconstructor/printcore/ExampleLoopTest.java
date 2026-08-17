package com.agentconstructor.printcore;

import com.agentconstructor.printcore.api.PrintConfig;
import com.agentconstructor.printcore.engine.InMemoryBridge;
import com.agentconstructor.printcore.job.JobStatus;
import com.agentconstructor.printcore.job.PrintJob;
import com.agentconstructor.printcore.job.PrintPlan;
import com.agentconstructor.printcore.job.StoredSchematic;
import com.agentconstructor.printcore.world.InMemoryWorld;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleLoopTest {
    @Test
    void plotPadCompileDryRunCommit() throws Exception {
        Path planPath = repoRoot().resolve("examples/plot-pad/dist/print_plan.json");
        JsonObject json = JsonParser.parseString(Files.readString(planPath)).getAsJsonObject();
        InMemoryWorld world = InMemoryWorld.overworld();
        PrintConfig config = new PrintConfig(
                "127.0.0.1", 0, "token", 128, 10_000, 1_000_000, 32768, 4096,
                -64, 319, 94, 223, Set.of("minecraft:overworld"), false, false, false
        );
        InMemoryBridge bridge = new InMemoryBridge(config, world);
        PrintJob job = bridge.jobs().submit(PrintPlan.fromJson(json));
        assertTrue(bridge.jobs().dryRun(job.id, world, world).isEmpty());
        bridge.jobs().commit(job.id, world, world);
        int ticks = 0;
        while (job.status == JobStatus.RUNNING && ticks < 50) {
            bridge.jobs().tick(world);
            ticks++;
        }
        assertEquals(JobStatus.COMPLETED, job.status);
        assertEquals("minecraft:smooth_stone", world.getBlock(0, 94, 0));
        assertEquals("minecraft:white_stained_glass", world.getBlock(9, 96, 9));
    }

    @Test
    void houseSchemFourRotationsDryRun() throws Exception {
        Path schemPath = repoRoot().resolve("examples/house-10/dist/modules/house-10.schem");
        Path planPath = repoRoot().resolve("examples/house-10/dist/print_plan.json");
        byte[] bytes = Files.readAllBytes(schemPath);
        InMemoryWorld world = InMemoryWorld.overworld();
        InMemoryBridge bridge = new InMemoryBridge(PrintConfig.defaults(), world);
        StoredSchematic stored = bridge.schematics().put("house-10.schem", bytes);
        JsonObject plan = JsonParser.parseString(Files.readString(planPath)).getAsJsonObject();
        for (int rotation : new int[]{0, 90, 180, 270}) {
            JsonObject copy = plan.deepCopy();
            copy.addProperty("id", "house-" + rotation);
            var step = copy.getAsJsonArray("steps").get(0).getAsJsonObject();
            step.addProperty("schematicId", stored.id);
            step.addProperty("sha256", stored.sha256);
            step.addProperty("rotation", rotation);
            PrintJob job = bridge.jobs().submit(PrintPlan.fromJson(copy));
            assertTrue(bridge.jobs().dryRun(job.id, world, world).isEmpty(), job.message);
        }
    }

    @Test
    void cityModule128DryRunCommit() throws Exception {
        Path planPath = repoRoot().resolve("examples/city-module-128/dist/print_plan.json");
        JsonObject json = JsonParser.parseString(Files.readString(planPath)).getAsJsonObject();
        InMemoryWorld world = InMemoryWorld.overworld();
        PrintConfig config = new PrintConfig(
                "127.0.0.1", 0, "token", 128, 50_000, 2_000_000, 32768, 4096,
                -64, 319, 94, 223, Set.of("minecraft:overworld"), false, false, false
        );
        InMemoryBridge bridge = new InMemoryBridge(config, world);
        PrintJob job = bridge.jobs().submit(PrintPlan.fromJson(json));
        assertTrue(bridge.jobs().dryRun(job.id, world, world).isEmpty(), job.message);
        bridge.jobs().commit(job.id, world, world);
        int ticks = 0;
        while (job.status == JobStatus.RUNNING && ticks < 200) {
            bridge.jobs().tick(world);
            ticks++;
        }
        assertEquals(JobStatus.COMPLETED, job.status, job.message);
        assertEquals("minecraft:black_concrete", world.getBlock(0, 96, 64));
        assertEquals("minecraft:white_stained_glass", world.getBlock(8, 96, 8));
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            if (Files.exists(dir.resolve("examples/plot-pad/dist/print_plan.json"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("cannot find repo root from " + Path.of("").toAbsolutePath());
    }
}
