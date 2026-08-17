package com.agentconstructor.printcore.forge;

import com.agentconstructor.printcore.http.PrintHttpServer;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

public final class ServerHooks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ForgeGameBridge bridge;
    private static PrintHttpServer http;

    private ServerHooks() {
    }

    public static ForgeGameBridge bridge() {
        return bridge;
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        bridge = new ForgeGameBridge(server);
        http = new PrintHttpServer(bridge);
        try {
            http.start();
            LOGGER.info("PrintCore HTTP listening on {}:{}", bridge.config().bind, http.port());
        } catch (Exception e) {
            LOGGER.error("Failed to start PrintCore HTTP server", e);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        if (http != null) {
            http.stop();
            http = null;
        }
        bridge = null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || bridge == null) {
            return;
        }
        try {
            bridge.jobs().tick(bridge::editor);
        } catch (RuntimeException e) {
            LOGGER.warn("PrintCore tick failed: {}", e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("printcore")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(ctx -> {
                            if (bridge == null) {
                                ctx.getSource().sendFailure(Component.literal("PrintCore is not running"));
                                return 0;
                            }
                            var status = bridge.status();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "PrintCore ready tps=" + String.format("%.1f", status.tps)
                                            + " mspt=" + String.format("%.1f", status.mspt)
                                            + " queue=" + status.jobQueue
                                            + " http=" + bridge.config().bind + ":" + bridge.config().port
                            ), false);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("validate")
                                .then(Commands.argument("jobId", StringArgumentType.word()).executes(ctx -> {
                                    if (bridge == null) {
                                        ctx.getSource().sendFailure(Component.literal("PrintCore is not running"));
                                        return 0;
                                    }
                                    String id = StringArgumentType.getString(ctx, "jobId");
                                    var job = bridge.jobs().get(id).orElse(null);
                                    if (job == null) {
                                        ctx.getSource().sendFailure(Component.literal("unknown job"));
                                        return 0;
                                    }
                                    var errors = bridge.jobs().dryRun(id, bridge.editor(job.plan.dimension), bridge.registry());
                                    if (errors.isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("dry-run ok " + id), true);
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    ctx.getSource().sendFailure(Component.literal(String.join("; ", errors)));
                                    return 0;
                                })))
                        .then(Commands.literal("print")
                                .then(Commands.argument("jobId", StringArgumentType.word()).executes(ctx -> {
                                    if (bridge == null) {
                                        ctx.getSource().sendFailure(Component.literal("PrintCore is not running"));
                                        return 0;
                                    }
                                    String id = StringArgumentType.getString(ctx, "jobId");
                                    var job = bridge.jobs().get(id).orElse(null);
                                    if (job == null) {
                                        ctx.getSource().sendFailure(Component.literal("unknown job"));
                                        return 0;
                                    }
                                    bridge.jobs().commit(id, bridge.editor(job.plan.dimension), bridge.registry());
                                    ctx.getSource().sendSuccess(() -> Component.literal("committed " + id + " status=" + job.status), true);
                                    return Command.SINGLE_SUCCESS;
                                })))
                        .then(Commands.literal("rollback")
                                .then(Commands.argument("jobId", StringArgumentType.word()).executes(ctx -> {
                                    if (bridge == null) {
                                        ctx.getSource().sendFailure(Component.literal("PrintCore is not running"));
                                        return 0;
                                    }
                                    String id = StringArgumentType.getString(ctx, "jobId");
                                    var job = bridge.jobs().get(id).orElse(null);
                                    if (job == null) {
                                        ctx.getSource().sendFailure(Component.literal("unknown job"));
                                        return 0;
                                    }
                                    bridge.jobs().rollback(id, bridge.editor(job.plan.dimension));
                                    ctx.getSource().sendSuccess(() -> Component.literal("rolled back " + id), true);
                                    return Command.SINGLE_SUCCESS;
                                })))
        );
    }
}
