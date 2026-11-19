package dev.zunserz.firstcontact;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Optional physical-ship adapter for Create: Aeronautics / Sable.
 * Requires a real, assembled Sable sublevel under / around the player.
 * Does not silently accept nearby decorative blocks or a plain static hull.
 */
public final class AeronauticsIntegration {
    private AeronauticsIntegration() {}

    public record ShipCheck(boolean installed, boolean aboard, String description) {}

    public static boolean installed() {
        ModList mods = ModList.get();
        return mods.isLoaded("aeronautics_bundled")
            || mods.isLoaded("create_aeronautics")
            || mods.isLoaded("aeronautics");
    }

    public static ShipCheck check(ServerPlayer player) {
        if (!installed()) return new ShipCheck(false, false, "Create: Aeronautics is not loaded");
        if (!ModList.get().isLoaded("sable"))
            return new ShipCheck(true, false, "Sable not loaded; cannot verify physical ship");
        try {
            Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable");
            Field helperField = sable.getField("HELPER");
            Object helper = helperField.get(null);
            if (helper == null) return new ShipCheck(true, false, "Sable helper unavailable");
            // Works with Sable versions exposing getContaining(Entity).
            Object sublevel = invoke(helper, "getContaining", player);
            // Fallback for Sable versions exposing getContaining(Level, Vec3).
            if (sublevel == null) {
                Vec3[] points = {
                    player.position(), player.getEyePosition(),
                    player.position().add(0, -1.0, 0)
                };
                for (Vec3 point : points) {
                    sublevel = invoke(helper, "getContaining", player.serverLevel(), point);
                    if (sublevel != null) break;
                }
            }
            if (sublevel == null) {
                return new ShipCheck(true, false,
                    "Stand on / inside the assembled Aeronautics ship and retry");
            }
            String className = sublevel.getClass().getName();
            boolean valid = className.contains("sable") &&
                (className.endsWith("ServerSubLevel") || className.endsWith("SubLevel"));
            return new ShipCheck(true, valid,
                valid ? "Physical Aeronautics/Sable sublevel verified: " + className
                      : "Found unexpected vessel type: " + className);
        } catch (ReflectiveOperationException | LinkageError e) {
            return new ShipCheck(true, false, "Sable adapter unavailable: " + e.getClass().getSimpleName());
        }
    }

    private static Object invoke(Object target, String name, Object... args) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            boolean matches = true;
            Class<?>[] types = method.getParameterTypes();
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null && !types[i].isAssignableFrom(args[i].getClass())) {
                    matches = false; break;
                }
            }
            if (!matches) continue;
            try { return method.invoke(target, args); }
            catch (ReflectiveOperationException | IllegalArgumentException ignored) { }
        }
        return null;
    }
}
