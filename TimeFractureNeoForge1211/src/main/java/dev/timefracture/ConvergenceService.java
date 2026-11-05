package dev.timefracture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A conservative, server-tick-sliced convergence of ALL currently active
 * POCKET snapshots. This does NOT merge infinite timelines, block entities,
 * inventories, NPC decisions or Create contraption NBT.
 *
 * The result is always a NEW saved pocket per dimension; existing snapshots
 * are retained, only marked archived after all scans finish successfully.
 * Incompatible non-air blocks keep the base branch value and are counted
 * (reported as unresolved conflicts). No destructive world rollback.
 */
public final class ConvergenceService {
    private ConvergenceService(){}
    private static Job job;
    private static final int WORK_PER_TICK = 24;
    private static final int SIZE = 2*PocketWorld.RADIUS+1;
    private static final int VERTICAL = 2*PocketWorld.HEIGHT+1;
    private static final int VOLUME = SIZE*SIZE*VERTICAL;

    private record Layer(String dim,List<PocketWorld.Pocket> inputs,PocketWorld.Pocket result) {}
    private static final class Job {
        final UUID actor;
        final BlockPos core;
        final List<Layer> layers;
        final List<String> outputs;
        int layerIndex;
        int offset;
        long added;
        long conflicts;
        Job(UUID actor,BlockPos core,List<Layer> layers,List<String> outputs){
            this.actor=actor;this.core=core;this.layers=layers;this.outputs=outputs;
        }
    }

    public static synchronized boolean running(){return job!=null;}

    public static synchronized void start(ServerLevel level,BlockPos core,UUID owner) {
        if(job!=null) {
            inform(level,owner,"§cОбъединение уже выполняется.");
            return;
        }
        PocketWorld worlds=TimeFractureMod.P;
        if(worlds==null || TimeFractureMod.L.frozen) {
            inform(level,owner,"§cСистема временных ветвей недоступна или заморожена.");
            return;
        }
        ServerPlayer initiator=level.getServer().getPlayerList().getPlayer(owner);
        if(initiator==null) {
            inform(level,owner,"§cИнициатор вышел с сервера.");
            return;
        }
        var inputs=worlds.list();
        if(inputs.size()<2) {
            inform(level,owner,"§cНужно как минимум две активные временные ветви.");
            return;
        }
        Map<String,List<PocketWorld.Pocket>> groups=inputs.stream()
            .collect(Collectors.groupingBy(PocketWorld.Pocket::dimension,
                LinkedHashMap::new,Collectors.toList()));
        List<Layer> layers=new ArrayList<>();
        List<String> outputs=new ArrayList<>();

        try {
            for(var entry:groups.entrySet()){
                // New history origin, not a forced same-person ancestry.
                var ledger=TimeFractureMod.L.add(initiator.getUUID(),
                    initiator.getGameProfile().getName(),"-","convergence:"+inputs.size());
                var result=worlds.create(level.getServer(),ledger.id(),initiator,entry.getValue().getFirst().id());
                worlds.markPending(result.id());
                outputs.add(result.id());
                layers.add(new Layer(entry.getKey(),List.copyOf(entry.getValue()),result));
            }
            job=new Job(owner,core,List.copyOf(layers),List.copyOf(outputs));
            inform(level,owner,"§bНачато объединение "+inputs.size()+" ветвей в "+
                layers.size()+" измерениях. Результаты будут сохранены отдельно; исходные линии не удаляются.");
        }catch(RuntimeException ex){
            // Created drafts stay tagged as pending, so a crash cannot silently
            // allow entry into a partially merged area.
            inform(level,owner,"§cНе удалось начать схлопывание: "+ex.getMessage());
        }
    }

    private static void inform(ServerLevel level,UUID owner,String msg){
        if(level.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer p)
            p.sendSystemMessage(Component.literal(msg));
    }

    public static synchronized void tick(ServerTickEvent.Post e){
        Job active=job;
        if(active==null)return;
        PocketWorld worlds=TimeFractureMod.P;
        if(worlds==null || TimeFractureMod.L.frozen) return;
        ServerPlayer player=e.getServer().getPlayerList().getPlayer(active.actor);
        // Pause when player is offline; no unsafe background work or async chunk loading.
        if(player==null)return;
        for(int work=0;work<WORK_PER_TICK;work++) {
            if(active.layerIndex>=active.layers.size()){
                worlds.archiveAllExcept(active.outputs);
                player.sendSystemMessage(Component.literal(
                    "§d§lСХЛОПЫВАНИЕ ЗАВЕРШЕНО. §fНовые линии: "+
                    String.join(", ",active.outputs)+" · добавлено блоков: "+active.added+
                    " · неразрешённых конфликтов: "+active.conflicts+
                    ". Исходные сохранены в архиве. /timefracture enter <ID>"));
                job=null;
                return;
            }
            Layer layer=active.layers.get(active.layerIndex);
            ServerLevel dimension=e.getServer().getLevel(ResourceKey.create(
                Registries.DIMENSION,ResourceLocation.parse(layer.dim)));
            if(dimension==null){
                player.sendSystemMessage(Component.literal("§cОбъединение приостановлено: измерение "+layer.dim+" не загружено."));
                job=null;
                return;
            }

            int i=active.offset++;
            int dx=(i%SIZE)-PocketWorld.RADIUS;
            int dz=((i/SIZE)%SIZE)-PocketWorld.RADIUS;
            int dy=(i/(SIZE*SIZE))-PocketWorld.HEIGHT;
            BlockPos dest=new BlockPos(layer.result.x()+dx,layer.result.y()+dy,layer.result.z()+dz);
            BlockState current=dimension.getBlockState(dest);
            boolean conflicted=false;

            for(PocketWorld.Pocket p:layer.inputs) {
                BlockPos from=new BlockPos(p.x()+dx,p.y()+dy,p.z()+dz);
                BlockState next=dimension.getBlockState(from);
                // Never manufacture empty inventories or duplicate machine NBT.
                if(next.isAir()||next.hasBlockEntity())continue;
                if(current.isAir() && !current.hasBlockEntity()){
                    dimension.setBlock(dest,next,2);
                    current=next;
                    active.added++;
                }else if(!current.equals(next))conflicted=true;
            }
            if(conflicted)active.conflicts++;

            if(active.offset>=VOLUME){
                active.layerIndex++;
                active.offset=0;
                player.sendSystemMessage(Component.literal(
                    "§bСлой измерения "+layer.dim+" объединён. "+
                    active.layerIndex+"/"+active.layers.size()+
                    " · конфликтов: "+active.conflicts));
            }
        }
    }

    public static synchronized String status(){
        if(job==null)return "Нет активного объединения.";
        return "Объединение "+job.layerIndex+"/"+job.layers.size()+
            " измерений; "+job.offset+"/"+VOLUME+" блоков; конфликты "+job.conflicts;
    }
}
