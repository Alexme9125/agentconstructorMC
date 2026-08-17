package com.agentconstructor.printcore.job;

import com.agentconstructor.printcore.api.PrintConfig;
import com.agentconstructor.printcore.api.Volume;
import com.agentconstructor.printcore.schem.SpongeSchematic;
import com.agentconstructor.printcore.world.BlockRegistry;
import com.agentconstructor.printcore.world.InMemoryWorld;
import com.agentconstructor.printcore.world.WorldEditor;
import com.agentconstructor.printcore.world.WorldView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PrintJob {
    public final String id;
    public final PrintPlan plan;
    public JobStatus status;
    public String message;
    public int stepIndex;
    public long blocksApplied;
    public VolumeSnapshot snapshot;
    public Volume bounds;
    private long cursor;

    public PrintJob(String id, PrintPlan plan) {
        this.id = id;
        this.plan = plan;
        this.status = JobStatus.SUBMITTED;
        this.message = "";
        this.stepIndex = 0;
        this.blocksApplied = 0;
    }

    public static PrintJob create(PrintPlan plan) {
        return new PrintJob(UUID.randomUUID().toString(), plan);
    }

    public List<String> dryRun(PrintConfig config, WorldView world, BlockRegistry registry, SchematicResolver schematics) {
        List<String> errors = new ArrayList<>(plan.validate(config, registry, schematics));
        Volume resolved = null;
        try {
            resolved = plan.resolvedBounds(schematics);
            bounds = resolved;
        } catch (RuntimeException e) {
            errors.add(e.getMessage());
        }
        if (resolved != null && world.hasEntityOccupying(resolved)) {
            errors.add("entities occupy target volume " + resolved);
        }
        if (errors.isEmpty()) {
            status = JobStatus.DRY_RUN_OK;
            message = "dry-run ok; volume=" + resolved.blockCount();
        } else {
            status = JobStatus.FAILED;
            message = String.join("; ", errors);
        }
        return errors;
    }

    public void beginCommit(WorldEditor editor, SchematicResolver schematics) {
        if (status != JobStatus.DRY_RUN_OK && status != JobStatus.SUBMITTED) {
            throw new IllegalStateException("job " + id + " cannot commit from " + status);
        }
        bounds = plan.resolvedBounds(schematics);
        editor.preload(bounds);
        snapshot = editor.snapshot(bounds);
        status = JobStatus.RUNNING;
        message = "printing";
        stepIndex = 0;
        blocksApplied = 0;
        cursor = 0;
    }

    /**
     * Apply up to {@code budget} block writes. Returns remaining budget.
     */
    public int tick(WorldEditor editor, SchematicResolver schematics, int budget) {
        if (status != JobStatus.RUNNING) {
            return budget;
        }
        try {
            while (budget > 0 && stepIndex < plan.steps.size()) {
                PrintPlan.Step step = plan.steps.get(stepIndex);
                budget = applyStep(editor, schematics, step, budget);
                if (cursor < 0) {
                    stepIndex++;
                    cursor = 0;
                }
            }
            if (stepIndex >= plan.steps.size()) {
                status = JobStatus.COMPLETED;
                message = "printed " + blocksApplied + " blocks";
            }
        } catch (RuntimeException e) {
            failAndRestore(editor, e.getMessage());
        }
        return budget;
    }

    public void rollback(WorldEditor editor) {
        if (snapshot == null) {
            throw new IllegalStateException("no snapshot to restore");
        }
        editor.restore(snapshot);
        status = JobStatus.ROLLED_BACK;
        message = "rolled back";
    }

    public void cancel(WorldEditor editor) {
        if (status == JobStatus.RUNNING && snapshot != null) {
            editor.restore(snapshot);
        }
        status = JobStatus.CANCELLED;
        message = "cancelled";
    }

    private void failAndRestore(WorldEditor editor, String error) {
        if (snapshot != null) {
            editor.restore(snapshot);
        }
        status = JobStatus.FAILED;
        message = error;
    }

    private int applyStep(WorldEditor editor, SchematicResolver schematics, PrintPlan.Step step, int budget) {
        if (step instanceof PrintPlan.FillStep fill) {
            return applyFill(editor, fill, budget);
        }
        if (step instanceof PrintPlan.TerrainStep terrain) {
            return applyTerrain(editor, terrain, budget);
        }
        if (step instanceof PrintPlan.SparseStep sparse) {
            return applySparse(editor, sparse, budget);
        }
        if (step instanceof PrintPlan.PasteStep paste) {
            return applyPaste(editor, schematics.require(paste), paste, budget);
        }
        throw new IllegalStateException("unknown step " + step.type());
    }

    private int applyFill(WorldEditor editor, PrintPlan.FillStep fill, int budget) {
        Volume volume = fill.volume();
        long total = volume.blockCount();
        while (cursor < total && budget > 0) {
            int[] pos = decode(volume, cursor);
            editor.setBlock(pos[0], pos[1], pos[2], fill.block);
            blocksApplied++;
            budget--;
            cursor++;
        }
        if (cursor >= total) {
            cursor = -1;
        }
        return budget;
    }

    private int applyTerrain(WorldEditor editor, PrintPlan.TerrainStep terrain, int budget) {
        int width = terrain.maxX - terrain.minX + 1;
        int length = terrain.maxZ - terrain.minZ + 1;
        int layerCount = terrain.layers.size();
        long total = (long) width * length * layerCount;
        while (cursor < total && budget > 0) {
            long layerIndex = cursor % layerCount;
            long column = cursor / layerCount;
            int x = terrain.minX + (int) (column % width);
            int z = terrain.minZ + (int) (column / width);
            PrintPlan.TerrainLayer layer = terrain.layers.get((int) layerIndex);
            editor.setBlock(x, layer.y, z, layer.block);
            blocksApplied++;
            budget--;
            cursor++;
        }
        if (cursor >= total) {
            cursor = -1;
        }
        return budget;
    }

    private int applySparse(WorldEditor editor, PrintPlan.SparseStep sparse, int budget) {
        while (cursor < sparse.blocks.size() && budget > 0) {
            PrintPlan.SparseBlock block = sparse.blocks.get((int) cursor);
            editor.setBlock(block.x, block.y, block.z, block.block);
            blocksApplied++;
            budget--;
            cursor++;
        }
        if (cursor >= sparse.blocks.size()) {
            cursor = -1;
        }
        return budget;
    }

    private int applyPaste(WorldEditor editor, SpongeSchematic schematic, PrintPlan.PasteStep paste, int budget) {
        SpongeSchematic rotated = schematic.rotateY(paste.rotation);
        long total = (long) rotated.width * rotated.height * rotated.length;
        while (cursor < total && budget > 0) {
            int x = (int) (cursor % rotated.width);
            long rest = cursor / rotated.width;
            int z = (int) (rest % rotated.length);
            int y = (int) (rest / rotated.length);
            String block = rotated.get(x, y, z);
            if (!(paste.ignoreAir && InMemoryWorld.isAir(block))) {
                editor.setBlock(paste.origin.x + x, paste.origin.y + y, paste.origin.z + z, block);
                blocksApplied++;
                budget--;
            }
            cursor++;
        }
        if (cursor >= total) {
            cursor = -1;
        }
        return budget;
    }

    private static int[] decode(Volume volume, long index) {
        int x = volume.minX + (int) (index % volume.sizeX());
        long rest = index / volume.sizeX();
        int z = volume.minZ + (int) (rest % volume.sizeZ());
        int y = volume.minY + (int) (rest / volume.sizeZ());
        return new int[]{x, y, z};
    }
}
