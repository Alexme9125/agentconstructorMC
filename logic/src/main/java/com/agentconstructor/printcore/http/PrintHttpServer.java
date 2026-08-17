package com.agentconstructor.printcore.http;

import com.agentconstructor.printcore.api.PrintConfig;
import com.agentconstructor.printcore.api.ServerStatus;
import com.agentconstructor.printcore.api.Volume;
import com.agentconstructor.printcore.engine.GameBridge;
import com.agentconstructor.printcore.job.JobStatus;
import com.agentconstructor.printcore.job.PrintJob;
import com.agentconstructor.printcore.job.PrintPlan;
import com.agentconstructor.printcore.job.StoredSchematic;
import com.agentconstructor.printcore.world.WorldEditor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class PrintHttpServer {
    public static final String VERSION = "0.1.0";

    private final GameBridge bridge;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private HttpServer server;

    public PrintHttpServer(GameBridge bridge) {
        this.bridge = bridge;
    }

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        PrintConfig config = bridge.config();
        server = HttpServer.create(new InetSocketAddress(config.bind, config.port), 0);
        server.createContext("/v1/status", this::status);
        server.createContext("/v1/registry/blocks", this::registry);
        server.createContext("/v1/world/blocks", this::blocks);
        server.createContext("/v1/world/heightmap", this::heightmap);
        server.createContext("/v1/schematics", this::schematics);
        server.createContext("/v1/jobs", this::jobs);
        server.setExecutor(null);
        server.start();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public int port() {
        return server == null ? bridge.config().port : server.getAddress().getPort();
    }

    private void status(HttpExchange exchange) throws IOException {
        if (!auth(exchange) || !allow(exchange, "GET")) {
            return;
        }
        ServerStatus status = bridge.callOnGameThread(bridge::status);
        JsonObject json = new JsonObject();
        json.addProperty("modVersion", status.modVersion);
        json.addProperty("gameVersion", status.gameVersion);
        json.addProperty("serverReady", status.serverReady);
        json.addProperty("tps", status.tps);
        json.addProperty("mspt", status.mspt);
        json.addProperty("jobQueue", status.jobQueue);
        json.addProperty("api", VERSION);
        json.add("dimensions", gson.toJsonTree(status.dimensions));
        json.addProperty("maxModuleSize", bridge.config().maxModuleSize);
        json.addProperty("blocksPerTick", bridge.config().blocksPerTick);
        send(exchange, 200, json);
    }

    private void registry(HttpExchange exchange) throws IOException {
        if (!auth(exchange) || !allow(exchange, "GET")) {
            return;
        }
        String prefix = query(exchange, "prefix", "");
        List<String> blocks = bridge.callOnGameThread(() -> bridge.registry().list(prefix));
        JsonObject json = new JsonObject();
        json.add("blocks", gson.toJsonTree(blocks));
        send(exchange, 200, json);
    }

    private void blocks(HttpExchange exchange) throws IOException {
        if (!auth(exchange) || !allow(exchange, "GET")) {
            return;
        }
        try {
            JsonObject json = bridge.callOnGameThread(() -> {
                int x1 = integer(exchange, "x1");
                int y1 = integer(exchange, "y1");
                int z1 = integer(exchange, "z1");
                int x2 = integer(exchange, "x2");
                int y2 = integer(exchange, "y2");
                int z2 = integer(exchange, "z2");
                String dimension = query(exchange, "dimension", "minecraft:overworld");
                Volume volume = new Volume(x1, y1, z1, x2, y2, z2);
                if (volume.blockCount() > bridge.config().maxReadVolume) {
                    throw new IllegalArgumentException("read volume exceeds maxReadVolume");
                }
                WorldEditor editor = bridge.editor(dimension);
                JsonArray array = new JsonArray();
                for (int y = volume.minY; y <= volume.maxY; y++) {
                    for (int z = volume.minZ; z <= volume.maxZ; z++) {
                        for (int x = volume.minX; x <= volume.maxX; x++) {
                            JsonObject block = new JsonObject();
                            block.addProperty("x", x);
                            block.addProperty("y", y);
                            block.addProperty("z", z);
                            block.addProperty("block", editor.getBlock(x, y, z));
                            array.add(block);
                        }
                    }
                }
                JsonObject out = new JsonObject();
                out.addProperty("count", array.size());
                out.add("blocks", array);
                return out;
            });
            send(exchange, 200, json);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private void heightmap(HttpExchange exchange) throws IOException {
        if (!auth(exchange) || !allow(exchange, "GET")) {
            return;
        }
        try {
            JsonObject json = bridge.callOnGameThread(() -> {
                int x1 = integer(exchange, "x1");
                int z1 = integer(exchange, "z1");
                int x2 = integer(exchange, "x2");
                int z2 = integer(exchange, "z2");
                String type = query(exchange, "type", "MOTION_BLOCKING_NO_LEAVES");
                String dimension = query(exchange, "dimension", "minecraft:overworld");
                long area = (long) (Math.abs(x2 - x1) + 1) * (Math.abs(z2 - z1) + 1);
                if (area > bridge.config().maxReadVolume) {
                    throw new IllegalArgumentException("heightmap area exceeds maxReadVolume");
                }
                WorldEditor editor = bridge.editor(dimension);
                JsonArray rows = new JsonArray();
                int minX = Math.min(x1, x2);
                int maxX = Math.max(x1, x2);
                int minZ = Math.min(z1, z2);
                int maxZ = Math.max(z1, z2);
                for (int z = minZ; z <= maxZ; z++) {
                    JsonArray row = new JsonArray();
                    for (int x = minX; x <= maxX; x++) {
                        row.add(editor.getHeight(type, x, z));
                    }
                    rows.add(row);
                }
                JsonObject out = new JsonObject();
                out.addProperty("type", type);
                out.addProperty("minX", minX);
                out.addProperty("minZ", minZ);
                out.add("heights", rows);
                return out;
            });
            send(exchange, 200, json);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private void schematics(HttpExchange exchange) throws IOException {
        if (!auth(exchange)) {
            return;
        }
        if ("GET".equals(exchange.getRequestMethod())) {
            JsonArray array = new JsonArray();
            for (StoredSchematic schematic : bridge.schematics().list()) {
                JsonObject json = new JsonObject();
                json.addProperty("id", schematic.id);
                json.addProperty("filename", schematic.filename);
                json.addProperty("sha256", schematic.sha256);
                json.addProperty("width", schematic.schematic.width);
                json.addProperty("height", schematic.schematic.height);
                json.addProperty("length", schematic.schematic.length);
                array.add(json);
            }
            JsonObject out = new JsonObject();
            out.add("schematics", array);
            send(exchange, 200, out);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method not allowed");
            return;
        }
        String filename = header(exchange, "X-Filename", "upload.schem");
        byte[] body = exchange.getRequestBody().readAllBytes();
        if (body.length == 0) {
            sendError(exchange, 400, "empty schematic");
            return;
        }
        try {
            StoredSchematic stored = bridge.schematics().put(filename, body);
            JsonObject json = new JsonObject();
            json.addProperty("id", stored.id);
            json.addProperty("filename", stored.filename);
            json.addProperty("sha256", stored.sha256);
            json.addProperty("width", stored.schematic.width);
            json.addProperty("height", stored.schematic.height);
            json.addProperty("length", stored.schematic.length);
            send(exchange, 201, json);
        } catch (RuntimeException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private void jobs(HttpExchange exchange) throws IOException {
        if (!auth(exchange)) {
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String rest = path.substring("/v1/jobs".length());
        if (rest.startsWith("/")) {
            rest = rest.substring(1);
        }
        try {
            if (rest.isEmpty()) {
                handleJobCollection(exchange);
                return;
            }
            String[] parts = rest.split("/");
            String id = parts[0];
            if (parts.length == 1 && "GET".equals(exchange.getRequestMethod())) {
                send(exchange, 200, jobJson(requireJob(id)));
                return;
            }
            if (parts.length == 2 && "dry-run".equals(parts[1]) && "POST".equals(exchange.getRequestMethod())) {
                PrintJob job = bridge.callOnGameThread(() -> {
                    PrintJob current = requireJob(id);
                    WorldEditor editor = bridge.editor(current.plan.dimension);
                    bridge.jobs().dryRun(id, editor, bridge.registry());
                    return requireJob(id);
                });
                send(exchange, job.status == JobStatus.FAILED ? 400 : 200, jobJson(job));
                return;
            }
            if (parts.length == 2 && "commit".equals(parts[1]) && "POST".equals(exchange.getRequestMethod())) {
                PrintJob job = bridge.callOnGameThread(() -> {
                    PrintJob current = requireJob(id);
                    WorldEditor editor = bridge.editor(current.plan.dimension);
                    return bridge.jobs().commit(id, editor, bridge.registry());
                });
                send(exchange, job.status == JobStatus.FAILED ? 400 : 202, jobJson(job));
                return;
            }
            if (parts.length == 2 && "rollback".equals(parts[1]) && "POST".equals(exchange.getRequestMethod())) {
                PrintJob job = bridge.callOnGameThread(() -> {
                    PrintJob current = requireJob(id);
                    WorldEditor editor = bridge.editor(current.plan.dimension);
                    return bridge.jobs().rollback(id, editor);
                });
                send(exchange, 200, jobJson(job));
                return;
            }
            sendError(exchange, 404, "unknown job route");
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private void handleJobCollection(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            JsonArray array = new JsonArray();
            for (PrintJob job : bridge.jobs().all()) {
                array.add(jobJson(job));
            }
            JsonObject json = new JsonObject();
            json.add("jobs", array);
            send(exchange, 200, json);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "method not allowed");
            return;
        }
        JsonObject body = readJson(exchange);
        PrintPlan plan = PrintPlan.fromJson(body);
        PrintJob job = bridge.jobs().submit(plan);
        send(exchange, 201, jobJson(job));
    }

    private PrintJob requireJob(String id) {
        return bridge.jobs().get(id).orElseThrow(() -> new IllegalArgumentException("unknown job " + id));
    }

    private JsonObject jobJson(PrintJob job) {
        JsonObject json = new JsonObject();
        json.addProperty("id", job.id);
        json.addProperty("planId", job.plan.id);
        json.addProperty("dimension", job.plan.dimension);
        json.addProperty("status", job.status.name());
        json.addProperty("message", job.message);
        json.addProperty("stepIndex", job.stepIndex);
        json.addProperty("blocksApplied", job.blocksApplied);
        if (job.bounds != null) {
            json.addProperty("volume", job.bounds.blockCount());
        }
        return json;
    }

    private boolean auth(HttpExchange exchange) throws IOException {
        String token = bridge.config().token;
        if (token.isBlank()) {
            return true;
        }
        String header = header(exchange, "Authorization", "");
        String alt = header(exchange, "X-PrintCore-Token", "");
        String provided = header.startsWith("Bearer ") ? header.substring("Bearer ".length()) : alt;
        if (!token.equals(provided)) {
            sendError(exchange, 401, "unauthorized");
            return false;
        }
        return true;
    }

    private boolean allow(HttpExchange exchange, String method) throws IOException {
        if (method.equals(exchange.getRequestMethod())) {
            return true;
        }
        sendError(exchange, 405, "method not allowed");
        return false;
    }

    private JsonObject readJson(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return gson.fromJson(text, JsonObject.class);
        }
    }

    private void send(HttpExchange exchange, int code, JsonObject json) throws IOException {
        byte[] bytes = gson.toJson(json).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("error", message);
        send(exchange, code, json);
    }

    private static String query(HttpExchange exchange, String key, String fallback) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) {
            return fallback;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) {
                return java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return fallback;
    }

    private static int integer(HttpExchange exchange, String key) {
        String value = query(exchange, key, null);
        if (value == null) {
            throw new IllegalArgumentException("missing query parameter " + key);
        }
        return Integer.parseInt(value);
    }

    private static String header(HttpExchange exchange, String name, String fallback) {
        List<String> values = exchange.getRequestHeaders().get(name);
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values.get(0);
    }
}
