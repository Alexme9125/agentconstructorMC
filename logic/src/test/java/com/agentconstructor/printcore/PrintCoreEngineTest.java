package com.agentconstructor.printcore;

import com.agentconstructor.printcore.api.PrintConfig;
import com.agentconstructor.printcore.api.Vec3i;
import com.agentconstructor.printcore.engine.InMemoryBridge;
import com.agentconstructor.printcore.http.PrintHttpServer;
import com.agentconstructor.printcore.job.JobStatus;
import com.agentconstructor.printcore.job.PrintJob;
import com.agentconstructor.printcore.job.PrintPlan;
import com.agentconstructor.printcore.schem.SpongeSchematic;
import com.agentconstructor.printcore.world.InMemoryWorld;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrintCoreEngineTest {
    @Test
    void schematicRoundTripAndRotation() {
        String[] blocks = new String[]{
                "minecraft:stone", "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]",
                "minecraft:air", "minecraft:stone"
        };
        SpongeSchematic schem = SpongeSchematic.create(2, 1, 2, blocks);
        byte[] bytes = schem.toBytes();
        SpongeSchematic read = SpongeSchematic.fromBytes(bytes);
        assertEquals(2, read.width);
        assertEquals("minecraft:stone", read.get(0, 0, 0));
        SpongeSchematic rotated = read.rotateY(90);
        assertEquals(2, rotated.width);
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=straight]", rotated.get(0, 0, 0));
    }

    @Test
    void tickSlicedFillAndRollback() {
        InMemoryWorld world = InMemoryWorld.overworld();
        PrintConfig config = new PrintConfig(
                "127.0.0.1", 0, "token", 128, 50, 1_000_000, 32768, 4096,
                -64, 319, 94, 223, Set.of("minecraft:overworld"), false, false, false
        );
        InMemoryBridge bridge = new InMemoryBridge(config, world);
        JsonObject plan = fillPlan("pad", 0, 94, 0, 9, 96, 9, "minecraft:smooth_stone");
        PrintJob job = bridge.jobs().submit(PrintPlan.fromJson(plan));
        assertTrue(bridge.jobs().dryRun(job.id, world, world).isEmpty());
        bridge.jobs().commit(job.id, world, world);
        int ticks = 0;
        while (job.status == JobStatus.RUNNING && ticks < 100) {
            bridge.jobs().tick(world);
            ticks++;
        }
        assertEquals(JobStatus.COMPLETED, job.status);
        assertTrue(ticks > 1);
        assertEquals("minecraft:smooth_stone", world.getBlock(0, 94, 0));
        assertEquals("minecraft:smooth_stone", world.getBlock(9, 96, 9));
        bridge.jobs().rollback(job.id, world);
        assertEquals("minecraft:air", world.getBlock(0, 94, 0));
        assertEquals(JobStatus.ROLLED_BACK, job.status);
    }

    @Test
    void unknownBlockFailsDryRun() {
        InMemoryWorld world = InMemoryWorld.overworld();
        InMemoryBridge bridge = new InMemoryBridge(PrintConfig.defaults(), world);
        JsonObject plan = fillPlan("bad", 0, 94, 0, 1, 94, 1, "sweetmagic:does_not_exist");
        PrintJob job = bridge.jobs().submit(PrintPlan.fromJson(plan));
        var errors = bridge.jobs().dryRun(job.id, world, world);
        assertTrue(errors.stream().anyMatch(e -> e.contains("unknown block")));
        assertEquals(JobStatus.FAILED, job.status);
    }

    @Test
    void moduleSizeLimit() {
        InMemoryWorld world = InMemoryWorld.overworld();
        InMemoryBridge bridge = new InMemoryBridge(PrintConfig.defaults(), world);
        JsonObject plan = fillPlan("huge", 0, 94, 0, 200, 94, 200, "minecraft:stone");
        PrintJob job = bridge.jobs().submit(PrintPlan.fromJson(plan));
        var errors = bridge.jobs().dryRun(job.id, world, world);
        assertTrue(errors.stream().anyMatch(e -> e.contains("exceeds")));
    }

    @Test
    void httpJobLifecycle() throws Exception {
        InMemoryWorld world = InMemoryWorld.overworld();
        PrintConfig config = new PrintConfig(
                "127.0.0.1", 0, "test-token", 128, 10_000, 1_000_000, 32768, 4096,
                -64, 319, 94, 223, Set.of("minecraft:overworld"), false, false, false
        );
        InMemoryBridge bridge = new InMemoryBridge(config, world);
        PrintHttpServer server = new PrintHttpServer(bridge);
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            String base = "http://127.0.0.1:" + server.port();
            HttpResponse<String> status = client.send(
                    request(base + "/v1/status", "test-token").GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, status.statusCode());
            assertTrue(status.body().contains("serverReady"));

            byte[] schem = stoneCube();
            HttpResponse<String> uploaded = client.send(
                    request(base + "/v1/schematics", "test-token")
                            .header("X-Filename", "stone.schem")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(schem))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(201, uploaded.statusCode());
            JsonObject uploadedJson = JsonParser.parseString(uploaded.body()).getAsJsonObject();
            String schemId = uploadedJson.get("id").getAsString();
            String sha = uploadedJson.get("sha256").getAsString();

            JsonObject plan = new JsonObject();
            plan.addProperty("version", 1);
            plan.addProperty("id", "house");
            plan.addProperty("dimension", "minecraft:overworld");
            JsonArray steps = new JsonArray();
            JsonObject paste = new JsonObject();
            paste.addProperty("type", "paste_schem");
            paste.addProperty("id", "cube");
            paste.addProperty("schematicId", schemId);
            paste.addProperty("sha256", sha);
            paste.addProperty("rotation", 0);
            paste.addProperty("ignoreAir", false);
            JsonObject origin = new JsonObject();
            origin.addProperty("x", 10);
            origin.addProperty("y", 97);
            origin.addProperty("z", 10);
            paste.add("origin", origin);
            steps.add(paste);
            plan.add("steps", steps);

            HttpResponse<String> created = client.send(
                    request(base + "/v1/jobs", "test-token")
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(plan.toString(), StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(201, created.statusCode());
            String jobId = JsonParser.parseString(created.body()).getAsJsonObject().get("id").getAsString();

            HttpResponse<String> dry = client.send(
                    request(base + "/v1/jobs/" + jobId + "/dry-run", "test-token")
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, dry.statusCode());

            HttpResponse<String> commit = client.send(
                    request(base + "/v1/jobs/" + jobId + "/commit", "test-token")
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(202, commit.statusCode());

            PrintJob job = bridge.jobs().get(jobId).orElseThrow();
            int ticks = 0;
            while (job.status == JobStatus.RUNNING && ticks < 20) {
                bridge.jobs().tick(world);
                ticks++;
            }
            assertEquals(JobStatus.COMPLETED, job.status);
            assertEquals("minecraft:stone", world.getBlock(10, 97, 10));
            assertEquals("minecraft:stone", world.getBlock(11, 98, 11));
        } finally {
            server.stop();
        }
    }

    @Test
    void readsPythonCompilerSchematic() throws Exception {
        byte[] data;
        try (var in = PrintCoreEngineTest.class.getResourceAsStream("/house-10.schem")) {
            assertTrue(in != null);
            data = in.readAllBytes();
        }
        SpongeSchematic schem = SpongeSchematic.fromBytes(data);
        assertEquals(10, schem.width);
        assertEquals(10, schem.length);
        assertEquals("minecraft:oak_planks", schem.get(0, 0, 0));
        assertEquals("minecraft:oak_log", schem.get(0, 1, 0));
    }

    @Test
    void rotateVecPreservesVolume() {
        Vec3i local = new Vec3i(0, 1, 2);
        Vec3i rotated = local.rotateY(90, 3, 4);
        assertEquals(2, rotated.x);
        assertEquals(1, rotated.y);
        assertEquals(2, rotated.z);
    }

    private static byte[] stoneCube() {
        String[] blocks = new String[8];
        java.util.Arrays.fill(blocks, "minecraft:stone");
        return SpongeSchematic.create(2, 2, 2, blocks).toBytes();
    }

    private static JsonObject fillPlan(String id, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        JsonObject plan = new JsonObject();
        plan.addProperty("version", 1);
        plan.addProperty("id", id);
        plan.addProperty("dimension", "minecraft:overworld");
        JsonArray steps = new JsonArray();
        JsonObject fill = new JsonObject();
        fill.addProperty("type", "fill");
        fill.addProperty("id", id + "-fill");
        fill.add("from", vec(x1, y1, z1));
        fill.add("to", vec(x2, y2, z2));
        fill.addProperty("block", block);
        steps.add(fill);
        plan.add("steps", steps);
        return plan;
    }

    private static JsonObject vec(int x, int y, int z) {
        JsonObject json = new JsonObject();
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("z", z);
        return json;
    }

    private static HttpRequest.Builder request(String url, String token) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token);
    }
}
