package dev.timefracturefx;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Local dev commands. Effects are real world-space meshes, never screen overlays. */
public final class FxClient {
    private static final List<Fx> EFFECTS = new CopyOnWriteArrayList<>();
    private static ResourceKey<Level> anchorDimension;
    private static int clientTicks;
    private static Vec3 boundGateCenter;
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
        for (String type : new String[]{"portal","rift","window","close","collapse","stop","info","where","perf","auto","low","medium","high","off"}) {
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
                    case "portal" -> 96.0;
                    case "rift" -> 24.0;
                    default -> 4.0;
                };
                // All anchors live in absolute world coordinates, not relative to camera.
                // Anchor never changes with camera position/rotation.
                // Keep FX worlds separate to avoid stale geometry in the
                // next dimension after traveling through a timeline.
                if(anchorDimension!=null && !mc.level.dimension().equals(anchorDimension))
                    EFFECTS.clear();
                anchorDimension=mc.level.dimension();
                EFFECTS.add(new Fx(action, origin.add(0, height, 0), now, 0, false));
                msg("§d[TimeFractureFX] §a"+action+" · мировой якорь XYZ="+
                    Math.round(origin.x)+","+(Math.round(origin.y)+Math.round(height))+","+
                    Math.round(origin.z)+"; не следует за камерой");
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
                anchorDimension=null;
                msg("§d[TimeFractureFX] §fВсе тестовые эффекты удалены.");
            }
            case "where" -> {
                for(Fx obj:EFFECTS)
                    msg("§d[FX] §f"+obj.type()+" @ "+Math.round(obj.center().x)+
                        " "+Math.round(obj.center().y)+" "+Math.round(obj.center().z));
            }
            case "perf" -> msg("§b[TimeFractureFX] "+FxMeshRenderer.perf());
            case "auto","low","medium","high","off" -> {
                int samples=switch(action) {
                    case "auto" -> -1;
                    case "low" -> 1;
                    case "medium" -> 2;
                    case "high" -> 4;
                    default -> 0;
                };
                FxMeshRenderer.setSamples(samples);
                msg("§b[TimeFractureFX] "+FxMeshRenderer.perf());
            }
            case "info" -> msg("§d[TimeFractureFX] §fОбъектов: "+EFFECTS.size()+
                   " · GLSL portal="+(FxShaders.PORTAL!=null)+" rift="+(FxShaders.RIFT!=null));
        }
    }

    private static void tick(ClientTickEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.level==null || mc.player==null) {
            EFFECTS.clear();
            anchorDimension=null;
            boundGateCenter=null;
            return;
        }
        if(anchorDimension!=null && !anchorDimension.equals(mc.level.dimension())){
            EFFECTS.clear();
            anchorDimension=null;
            boundGateCenter=null;
            return;
        }
        // Scan a bounded area once every 2 seconds; NEVER traverse chunks that
        // are not already loaded by the client. A server-created gate now gets
        // an actual localized FX rift, not just a particle substitute.
        if(++clientTicks%40==0)syncNearbyGate(mc);
        long now=System.currentTimeMillis();
        EFFECTS.removeIf(f -> f.ending>0 && now-f.ending>(f.collapse?620:2250));
    }

    private static void syncNearbyGate(Minecraft mc) {
        BlockPos base=mc.player.blockPosition();
        Vec3 found=null;
        for(int dy=-3;dy<=4 && found==null;dy++){
            for(int dx=-6;dx<=6 && found==null;dx++){
                for(int dz=-6;dz<=6;dz++){
                    BlockPos pos=base.offset(dx,dy,dz);
                    if(!mc.level.hasChunkAt(pos))continue;
                    var id=BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock());
                    if(id.toString().equals("timefracture:rift_gate")){
                        found=Vec3.atCenterOf(pos).add(0,.5,0);
                        break;
                    }
                }
            }
        }
        if(found==null){
            EFFECTS.removeIf(f->f.type().equals("gate-window"));
            boundGateCenter=null;
            return;
        }
        if(found.equals(boundGateCenter))return;
        EFFECTS.removeIf(f->f.type().equals("gate-window"));
        EFFECTS.add(new Fx("gate-window",found,System.currentTimeMillis(),0,false));
        boundGateCenter=found;
    }
}
