package dev.zunserz.firstcontact;

import java.lang.reflect.Method;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Reads the actual Create: Radars multiblock controller. No dummy bearings or
 * static decorative dish can pass the integration check.
 *
 * Reflection avoids hard linking to binary-incompatible Create: Radars releases;
 * public names are verified against the 1.21.1 radar-bearing implementation.
 */
public final class RadarIntegration {
    private RadarIntegration() {}
    public static final String BEARING = "create_radar:radar_bearing";
    public static final String DISH = "create_radar:radar_dish_block";
    public static final String PLATE = "create_radar:radar_plate_block";
    public static final String RECEIVER = "create_radar:radar_receiver_block";

    public record Status(boolean present, boolean assembled, boolean powered,
                         int dishPanels, float range, int tracks, String detail) {
        public boolean ready() { return present && assembled && powered && dishPanels >= 8; }
    }

    public static Status probe(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return new Status(false, false, false, 0, 0, 0, "Chunk not loaded");
        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        if (!id.equals(BEARING)) return new Status(false, false, false, 0, 0, 0, "Radar bearing absent");
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !be.getClass().getName().contains("RadarBearingBlockEntity"))
            return new Status(true, false, false, 0, 0, 0, "Radar controller BE missing");
        try {
            boolean running = (boolean) be.getClass().getMethod("isRunning").invoke(be);
            float speed = ((Number) be.getClass().getMethod("getSpeed").invoke(be)).floatValue();
            int dishes = ((Number) be.getClass().getMethod("getDishCount").invoke(be)).intValue();
            float range = ((Number) be.getClass().getMethod("getRange").invoke(be)).floatValue();
            int count = 0;
            try {
                Object result = be.getClass().getMethod("getTracks").invoke(be);
                if (result instanceof Collection<?> tracks) count = tracks.size();
            } catch (ReflectiveOperationException ignored) { }
            boolean powered = running && Math.abs(speed) > 0.001f;
            return new Status(true, running, powered, dishes, range, count,
                !running ? "Contraption not assembled; receiver required"
                    : dishes < 8 ? "Too few physical radar panels (min 8)"
                    : !powered ? "Kinetic power is zero" : "Scanning");
        } catch (ReflectiveOperationException | ClassCastException ex) {
            return new Status(true, false, false, 0, 0, 0,
                "Unsupported Radars API: " + ex.getClass().getSimpleName());
        }
    }

    /** Manual linking only; automatic scanning never force-loads chunks. */
    public static BlockPos findNearby(ServerLevel level, BlockPos origin) {
        BlockPos nearest = null;
        double best = 33 * 33;
        for (BlockPos p : BlockPos.betweenClosed(
                origin.offset(-32, -12, -32), origin.offset(32, 12, 32))) {
            if (!level.hasChunkAt(p)) continue;
            if (!BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock())
                    .toString().equals(BEARING)) continue;
            double dist = p.distSqr(origin);
            if (dist < best) { best = dist; nearest = p.immutable(); }
        }
        return nearest;
    }
}
