package dev.timefracturefx;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * World-space 3D particle geometry. This is NOT yet volumetric GLSL.
 * Windows are visual outlines; they do NOT render actual alternate worlds.
 * Preview commands are local client commands for development.
 */
public final class FxClient {
    private static final List<Effect> EFFECTS = new CopyOnWriteArrayList<>();
    private static long ticks = 0;

    private record Effect(String type, Vec3 center, long born, long closing, boolean collapse) {}

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener(FxClient::register);
        NeoForge.EVENT_BUS.addListener(FxClient::tick);
    }

    private static void message(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal(text), false);
    }

    private static void register(RegisterClientCommandsEvent event) {
        var root = Commands.literal("tfx");
        var preview = Commands.literal("preview");
        for (String action : new String[]{"portal", "rift", "window", "close", "collapse", "stop"}) {
            preview.then(Commands.literal(action).executes(context -> {
                trigger(action);
                return 1;
            }));
        }
        event.getDispatcher().register(root.then(preview));
    }

    private static void trigger(String action) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        long now = System.currentTimeMillis();
        switch (action) {
            case "portal", "rift", "window" -> {
                Vec3 p = player.position();
                double offset = action.equals("portal") ? 42 : action.equals("rift") ? 18 : 3;
                EFFECTS.add(new Effect(action, p.add(0, offset, 0), now, 0, false));
                message("§dTimeFractureFX: §a3D " + action + " создан в мире (локальный предпросмотр).");
            }
            case "close", "collapse" -> {
                for (int i = 0; i < EFFECTS.size(); i++) {
                    Effect old = EFFECTS.get(i);
                    EFFECTS.set(i, new Effect(old.type, old.center, old.born, now, action.equals("collapse")));
                }
                message("§dTimeFractureFX: §f" + action);
            }
            case "stop" -> {
                EFFECTS.clear();
                message("§dTimeFractureFX: §fобъекты предпросмотра убраны.");
            }
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            EFFECTS.clear();
            return;
        }
        ++ticks;
        if (EFFECTS.isEmpty()) return;
        long now = System.currentTimeMillis();
        EFFECTS.removeIf(e -> e.closing > 0 && now - e.closing > (e.collapse ? 600 : 2000));
        for (Effect e : EFFECTS) {
            double scale;
            if (e.closing == 0) scale = Math.min(1, (now - e.born) / 1200.0);
            else scale = Math.max(0, 1 - (now - e.closing) / (e.collapse ? 600.0 : 2000.0));
            if (scale <= 0) continue;
            if (e.type.equals("portal")) portal(e.center, scale);
            else tear(e.center, scale, e.type.equals("window"));
        }
    }

    /** Horizontal nested 3D rings with depth along Y; flyable in-world effect. */
    private static void portal(Vec3 c, double scale) {
        for (int depth = 0; depth < 10; depth++) {
            double r = (13.0 - depth * .82) * scale;
            double y = c.y - depth * 2.0;
            double spin = ticks * (.016 + depth * .0009) * (depth % 2 == 0 ? 1 : -1);
            for (int i = 0; i < 16; i++) {
                double a = (i + (depth % 2) * .5) * Math.PI * 2 / 16 + spin;
                Minecraft.getInstance().level.addParticle(
                    depth % 2 == 0 ? ParticleTypes.PORTAL : ParticleTypes.END_ROD,
                    c.x + r * Math.cos(a), y, c.z + r * Math.sin(a), 0, 0, 0);
            }
        }
    }

    /** Several offset outlines make a three-dimensional world object, not a screen filter. */
    private static void tear(Vec3 c, double scale, boolean window) {
        int points = window ? 15 : 26;
        double rx = (window ? 4.5 : 11) * scale;
        double ry = (window ? 3.7 : 15) * scale;
        for (int layer = 0; layer < 4; layer++) {
            for (int i = 0; i < points; i++) {
                double a = i * Math.PI * 2 / points;
                double jagged = 1 + .12 * Math.sin(i * 4.93 + layer * 2.4);
                Minecraft.getInstance().level.addParticle(
                    layer % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.REVERSE_PORTAL,
                    c.x + Math.cos(a) * rx * jagged,
                    c.y + Math.sin(a) * ry * jagged,
                    c.z + layer * .65, 0, 0, 0);
            }
        }
    }
}
