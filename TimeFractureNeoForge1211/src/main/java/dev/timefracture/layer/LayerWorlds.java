package dev.timefracture.layer;

import dev.timefracture.TimeFractureMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Bounded proof-of-function world layers:
 *
 * Four pre-registered sets of distinct dimensions (overworld/nether/end) use
 * independent VANILLA region storage, at exactly the same XYZ coordinates.
 * No 500000-block teleportation and NO single-tick bulk copies.
 *
 * When a branch is created, it receives a tick-sliced SAME-COORDINATE local
 * snapshot of blocks AND block entities around the source player's anchor.
 * Create machinery is copied via BlockEntity NBT as best-effort, not as a live
 * moved contraption. The rest of the dimension is independently world-generated
 * from the common world seed and is NOT a full fork of every saved chunk.
 *
 * The 4-slot cap is deliberate to avoid holding dozens of extra ServerLevels.
 * An entry is never allowed while its initial snapshot is incomplete.
 */
public final class LayerWorlds {
    public static final int MAX_SLOTS=4;
    private static final int RADIUS=16, HALF_HEIGHT=24;
    private static final int SIDE=2*RADIUS+1;
    private static final int H=2*HALF_HEIGHT+1;
    private static final int TOTAL=SIDE*SIDE*H;
    private static final int BUDGET=128;

    private record ReturnPoint(String dim,double x,double y,double z,float yaw,float pitch) {}
    private static final class Job {
        String id, sourceDimension, kind;
        int x,y,z,offset,failedBlockEntities;
        UUID actor;
        Job(String id,String dim,String kind,int x,int y,int z,int offset,UUID actor){
            this.id=id;sourceDimension=dim;this.kind=kind;
            this.x=x;this.y=y;this.z=z;this.offset=offset;this.actor=actor;
        }
    }

    private final Path file;
    private final LayerCatalog catalog;
    private final Set<String> ready=new HashSet<>();
    private final Deque<Job> jobs=new ArrayDeque<>();
    private final Map<UUID,ReturnPoint> returns=new HashMap<>();

    public LayerWorlds(Path file,LayerCatalog catalog){
        this.file=file;
        this.catalog=catalog;
        read();
    }

    public static String kindOf(String dimension){
        if(dimension.equals("minecraft:the_nether") || dimension.endsWith("_nether"))return "nether";
        if(dimension.equals("minecraft:the_end") || dimension.endsWith("_end"))return "end";
        return "overworld";
    }

    private static int slot(String id){
        if(id==null || !id.matches("T-[0-9]+"))
            throw new IllegalArgumentException("Недопустимый ID временной линии: "+id);
        int value=Integer.parseInt(id.substring(2));
        if(value<1 || value>MAX_SLOTS)
            throw new IllegalArgumentException("Пока доступно "+MAX_SLOTS+" изолированных временных слоёв. "+
                "Этот ID вне предела слотов: "+id);
        return value;
    }

    public static String destination(String id,String kind){
        return "timefracture:layer_"+String.format(Locale.ROOT,"%02d",slot(id))+"_"+kind;
    }

    private static ServerLevel getLevel(MinecraftServer server,String id){
        try{
            ResourceKey<Level> key=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(id));
            return server.getLevel(key);
        }catch(RuntimeException ex){return null;}
    }

    public synchronized boolean isReady(String id){return ready.contains(id);}
    public synchronized int pendingCount(){return jobs.size();}
    public synchronized int readyCount(){return ready.size();}
    public synchronized String status(String id){
        if(ready.contains(id))return "§aСлой готов к входу";
        for(Job job:jobs)if(job.id.equals(id))
            return "§eКопирование начальной области: "+(100L*job.offset/TOTAL)+
                "% · "+job.offset+"/"+TOTAL+" блоков · размер "+SIDE+"×"+H+"×"+SIDE;
        return "§7Слой ещё не подготовлен. Используй /timefracture enter "+id;
    }

    public synchronized void queue(ServerPlayer player,String id,String parent){
        if(catalog.find(id)==null)throw new IllegalArgumentException("Линия не зарегистрирована: "+id);
        if(ready.contains(id))return;
        for(Job old:jobs)if(old.id.equals(id))return;
        String kind=kindOf(player.level().dimension().location().toString());
        String sourceDimension=player.level().dimension().location().toString();
        if(parent!=null && !parent.equals("-")){
            if(!ready.contains(parent))
                throw new IllegalStateException("Сначала дождись готовности родительской линии "+parent);
            sourceDimension=destination(parent,kind);
        }
        if(getLevel(player.getServer(),destination(id,kind))==null)
            throw new IllegalStateException("Нет измерения "+destination(id,kind)+
                ". Перезапусти мир с установленной актуальной версией мода.");
        var layer=catalog.find(id);
        Job job=new Job(id,sourceDimension,kind,layer.x(),layer.y(),layer.z(),0,player.getUUID());
        jobs.addLast(job);
        persist();
    }

    public synchronized void enter(ServerPlayer player,String id) {
        var layer=catalog.find(id);
        if(layer==null)throw new IllegalArgumentException("Неизвестная линия "+id);
        slot(id);
        if(!ready.contains(id)){
            queue(player,id,layer.parent());
            player.sendSystemMessage(Component.literal("§e[TimeFracture] "+status(id)+
                ". Вход разрешится после синхронизации. Повтори /timefracture enter "+id));
            return;
        }

        MinecraftServer server=player.getServer();
        if(server==null)throw new IllegalStateException("Сервер недоступен");
        String dimension=destination(id,kindOf(player.level().dimension().location().toString()));
        ServerLevel target=getLevel(server,dimension);
        if(target==null)throw new IllegalStateException("Нет измерения "+dimension);
        String previous=player.level().dimension().location().toString();
        if(!returns.containsKey(player.getUUID())){
            returns.put(player.getUUID(),new ReturnPoint(previous,
                player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot()));
            persist();
        }
        // Distinct registered dimensions mean separate server chunk sets,
        // while Minecraft coordinates remain completely unchanged.
        double x=player.getX(),y=player.getY(),z=player.getZ();
        player.teleportTo(target,x,y,z,player.getYRot(),player.getXRot());
        player.sendSystemMessage(Component.literal("§d[TimeFracture] §fВход в "+id+
            ". Координаты сохранены, измерение: "+dimension+
            ". §7Наследование мира пока ограничено начальной областью."));
    }

    public synchronized void leave(ServerPlayer player){
        ReturnPoint point=returns.get(player.getUUID());
        if(point==null)throw new IllegalArgumentException("Сохранённой точки возврата нет");
        MinecraftServer server=player.getServer();
        ServerLevel target=server==null?null:getLevel(server,point.dim());
        if(target==null)throw new IllegalStateException("Измерение возврата отсутствует: "+point.dim());
        player.teleportTo(target,point.x(),point.y(),point.z(),point.yaw(),point.pitch());
        returns.remove(player.getUUID());
        persist();
        player.sendSystemMessage(Component.literal("§a[TimeFracture] Возвращение в исходную временную линию."));
    }

    public synchronized void tick(ServerTickEvent.Post event){
        if(jobs.isEmpty())return;
        Job job=jobs.peekFirst();
        if(job==null)return;
        MinecraftServer server=event.getServer();
        ServerLevel from=getLevel(server,job.sourceDimension);
        ServerLevel to=getLevel(server,destination(job.id,job.kind));
        if(from==null || to==null)return;
        for(int i=0;i<BUDGET && job.offset<TOTAL;i++){
            int at=job.offset++;
            int dx=(at%SIDE)-RADIUS;
            int dz=((at/SIDE)%SIDE)-RADIUS;
            int dy=(at/(SIDE*SIDE))-HALF_HEIGHT;
            BlockPos p=new BlockPos(job.x+dx,job.y+dy,job.z+dz);
            if(p.getY()<from.getMinBuildHeight() || p.getY()>=from.getMaxBuildHeight() ||
               p.getY()<to.getMinBuildHeight() || p.getY()>=to.getMaxBuildHeight())continue;
            BlockState original=from.getBlockState(p);
            // Replacing the block state only if changed avoids needless neighbor
            // and chunk updates. Flag 2 notifies clients, not neighbors.
            if(!to.getBlockState(p).equals(original))to.setBlock(p,original,2);
            BlockEntity sourceBE=from.getBlockEntity(p);
            BlockEntity targetBE=to.getBlockEntity(p);
            if(sourceBE!=null && targetBE!=null &&
               sourceBE.getType()==targetBE.getType()){
                try{
                    CompoundTag tag=sourceBE.saveWithFullMetadata(from.registryAccess());
                    targetBE.loadWithComponents(tag,to.registryAccess());
                    targetBE.setChanged();
                }catch(RuntimeException ex){
                    job.failedBlockEntities++;
                }
            }
        }
        if(job.offset>=TOTAL){
            ready.add(job.id);
            jobs.removeFirst();
            ServerPlayer actor=server.getPlayerList().getPlayer(job.actor);
            if(actor!=null)actor.sendSystemMessage(Component.literal(
                "§a[TimeFracture] Слой "+job.id+" подготовлен: "+
                TOTAL+" блоков. Ошибок копирования block-entity: "+job.failedBlockEntities+
                ". Теперь можно /timefracture enter "+job.id));
            persist();
        }else if(job.offset%2048< BUDGET)persist();
    }

    private synchronized void persist(){
        try{
            Files.createDirectories(file.getParent());
            List<String> lines=new ArrayList<>();
            lines.add("LAYER_WORLDS_V1");
            for(String id:ready)lines.add("Y\t"+id);
            for(Job j:jobs)lines.add(String.join("\t","J",j.id,j.sourceDimension,j.kind,
                Integer.toString(j.x),Integer.toString(j.y),Integer.toString(j.z),
                Integer.toString(j.offset),j.actor.toString()));
            for(var entry:returns.entrySet()){
                ReturnPoint p=entry.getValue();
                lines.add(String.join("\t","R",entry.getKey().toString(),p.dim(),
                    Double.toString(p.x()),Double.toString(p.y()),Double.toString(p.z()),
                    Float.toString(p.yaw()),Float.toString(p.pitch())));
            }
            Path tmp=file.resolveSibling(file.getFileName()+".tmp");
            Files.write(tmp,lines,StandardCharsets.UTF_8);
            Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException ex){throw new IllegalStateException("Не удалось сохранить реестр мировых слоёв",ex);}
    }
    private synchronized void read(){
        if(!Files.exists(file))return;
        try{
            List<String> lines=Files.readAllLines(file,StandardCharsets.UTF_8);
            if(lines.isEmpty() || !lines.getFirst().equals("LAYER_WORLDS_V1"))
                throw new IOException("Неизвестная версия списка слоёв");
            for(int i=1;i<lines.size();i++){
                String[] a=lines.get(i).split("\t",-1);
                switch(a[0]){
                    case "Y" -> ready.add(a[1]);
                    case "J" -> jobs.addLast(new Job(a[1],a[2],a[3],
                        Integer.parseInt(a[4]),Integer.parseInt(a[5]),
                        Integer.parseInt(a[6]),Integer.parseInt(a[7]),UUID.fromString(a[8])));
                    case "R" -> returns.put(UUID.fromString(a[1]),new ReturnPoint(a[2],
                        Double.parseDouble(a[3]),Double.parseDouble(a[4]),Double.parseDouble(a[5]),
                        Float.parseFloat(a[6]),Float.parseFloat(a[7])));
                }
            }
        }catch(Exception ex){throw new IllegalStateException(
            "Данные слоёв повреждены, мир не менялся: "+file,ex);}
    }
}
