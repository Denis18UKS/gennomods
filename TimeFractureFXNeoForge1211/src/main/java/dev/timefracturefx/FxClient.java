package dev.timefracturefx;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Local dev commands. Effects are real world-space meshes, never screen overlays. */
public final class FxClient {
    private static final List<Fx> EFFECTS = new CopyOnWriteArrayList<>();
    record Fx(String type, Vec3 center, long born, long ending, boolean collapse) {}
    static List<Fx> effects() { return EFFECTS; }

    static void initialize() {
        NeoForge.EVENT_BUS.addListener(FxClient::commands);
        NeoForge.EVENT_BUS.addListener(FxClient::tick);
        NeoForge.EVENT_BUS.addListener(FxMeshRenderer::render);
    }

    private static void msg(String s) {
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.displayClientMessage(Component.literal(s), false);
    }

    private static void commands(RegisterClientCommandsEvent e) {
        var root = Commands.literal("tfx");
        var preview = Commands.literal("preview");
        for (String type : new String[]{"portal","rift","window","close","collapse","stop","info","where"}) {
            preview.then(Commands.literal(type).executes(c -> {
                perform(type);
                return 1;
            }));
        }
        e.getDispatcher().register(root.then(preview));
    }

    private static void perform(String action) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        switch (action) {
            case "portal", "rift", "window" -> {
                // One live instance per type limits GPU work and prevents
                // accidental stacking of many sky surfaces.
                EFFECTS.removeIf(existing -> existing.type().equals(action));
                Vec3 origin = mc.player.position();
                double height = switch(action) {
                    case "portal" -> 60.0;
                    case "rift" -> 24.0;
                    default -> 4.0;
                };
                // All anchors live in absolute world coordinates, not relative to camera.
                EFFECTS.add(new Fx(action, origin.add(0, height, 0), now, 0, false));
                msg("§d[TimeFractureFX] §a"+action+" · "+(action.equals("portal")?"небесная воронка без конуса":"3D-разлом")+" · Y+"+(int)height);
            }
            case "close","collapse" -> {
                for (int i=0; i<EFFECTS.size(); i++) {
                    Fx old = EFFECTS.get(i);
                    if (old.ending == 0)
                        EFFECTS.set(i, new Fx(old.type,old.center,old.born,now,action.equals("collapse")));
                }
                msg("§d[TimeFractureFX] §f"+action+" · анимация запущена");
            }
            case "stop" -> {
                EFFECTS.clear();
                msg("§d[TimeFractureFX] §fВсе тестовые эффекты удалены.");
            }
            case "where" -> {
                for(Fx obj:EFFECTS)
                    msg("§d[FX] §f"+obj.type()+" @ "+Math.round(obj.center().x)+
                        " "+Math.round(obj.center().y)+" "+Math.round(obj.center().z));
            }
            case "info" -> msg("§d[TimeFractureFX] §fОбъектов: "+EFFECTS.size()+
                   " · GLSL portal="+(FxShaders.PORTAL!=null)+" rift="+(FxShaders.RIFT!=null));
        }
    }

    private static void tick(ClientTickEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.level==null || mc.player==null) {
            EFFECTS.clear();
            return;
        }
        long now=System.currentTimeMillis();
        EFFECTS.removeIf(f -> f.ending>0 && now-f.ending>(f.collapse?620:2250));
    }
}
