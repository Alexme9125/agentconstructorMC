package com.agentconstructor.printcore.job;

import com.agentconstructor.printcore.api.PrintConfig;
import com.agentconstructor.printcore.world.BlockRegistry;
import com.agentconstructor.printcore.world.WorldEditor;
import com.agentconstructor.printcore.world.WorldView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class JobManager {
    private final PrintConfig config;
    private final SchematicStore schematics;
    private final Map<String, PrintJob> jobs = new ConcurrentHashMap<>();
    private final List<String> queue = new ArrayList<>();
    private String runningId;

    public JobManager(PrintConfig config, SchematicStore schematics) {
        this.config = config;
        this.schematics = schematics;
    }

    public SchematicStore schematics() {
        return schematics;
    }

    public PrintJob submit(PrintPlan plan) {
        PrintJob job = PrintJob.create(plan);
        jobs.put(job.id, job);
        return job;
    }

    public Optional<PrintJob> get(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public Collection<PrintJob> all() {
        return jobs.values();
    }

    public List<String> dryRun(String id, WorldView world, BlockRegistry registry) {
        PrintJob job = require(id);
        return job.dryRun(config, world, registry, schematics);
    }

    public PrintJob commit(String id, WorldEditor editor, BlockRegistry registry) {
        PrintJob job = require(id);
        if (job.status != JobStatus.DRY_RUN_OK) {
            List<String> errors = job.dryRun(config, editor, registry, schematics);
            if (!errors.isEmpty()) {
                return job;
            }
        }
        job.beginCommit(editor, schematics);
        queue.remove(id);
        if (runningId == null) {
            runningId = id;
        } else if (!runningId.equals(id)) {
            queue.add(id);
        }
        return job;
    }

    public PrintJob rollback(String id, WorldEditor editor) {
        PrintJob job = require(id);
        job.rollback(editor);
        if (id.equals(runningId)) {
            runningId = null;
        }
        queue.remove(id);
        return job;
    }

    public void tick(WorldEditor editor) {
        tick(dimension -> editor);
    }

    public void tick(java.util.function.Function<String, WorldEditor> editors) {
        if (runningId == null && !queue.isEmpty()) {
            runningId = queue.remove(0);
        }
        if (runningId == null) {
            return;
        }
        PrintJob job = jobs.get(runningId);
        if (job == null || job.status != JobStatus.RUNNING) {
            runningId = null;
            return;
        }
        job.tick(editors.apply(job.plan.dimension), schematics, config.blocksPerTick);
        if (job.status != JobStatus.RUNNING) {
            runningId = null;
        }
    }

    public int queueSize() {
        return queue.size() + (runningId == null ? 0 : 1);
    }

    private PrintJob require(String id) {
        PrintJob job = jobs.get(id);
        if (job == null) {
            throw new IllegalArgumentException("unknown job " + id);
        }
        return job;
    }
}
