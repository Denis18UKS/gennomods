package dev.timefracture.layer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.core.registries.Registries;
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
 * First PLAYABLE, persistent same-coordinate layer implementation for NeoForge 1.21.1.
 *
 * Eight reserved server dimensions are per vanilla dimension (24 total LevelStems).
 * Each dimension has its own region files / block entities / entities. On branch
 * creation an initial *single 16x16 chunk column* at the branch anchor is copied
 * in bounded server-tick batches, INCLUDING block entities, at the EXACT same XYZ.
 *
 * Important limits: outside that copied chunk, terrain uses the same seed but
 * PLAYER BUILDINGS from the parent are not copied. No simulated player echoes.
 * No copying of entities, time/weather or inventories of players. No dynamic
 * infinite slots. A complete branching chunk inheritance system is future work.
 *
 * Never tell players these are complete copies of the entire Minecraft world.
 */
public final class LayerWorlds {
    public static final int MAX_SLOTS=8;
    // Two independent guards: bound block count AND elapsed nanoseconds.
    // Chunk *generation* is staged through futures rather than getBlockState.
    private static final int BLOCKS_PER_TICK=64;
    private static final long NANOSECONDS_PER_TICK=1_000_000L; // ~1ms
    private long lastCopyMicros=0L;
    private long worstCopyMicros=0L;
    private long busyTicks=0L;
    private final Path doneFile;
    private final Set<String> completed=new HashSet<>();
    private final ArrayDeque<CopyJob> pending=new ArrayDeque<>();
    private final Set<String> enqueued=new HashSet<>();
    private final Map<UUID,ReturnPoint> originals=new HashMap<>();
    private final Map<UUID,String> requested=new HashMap<>();
    private long ticks=0;

    private record ReturnPoint(String dimension,double x,double y,double z,float yaw,float pitch) {}

    private static final class CopyJob {
        final String id,dimensionKey;
        final ResourceKey<Level> parent,child;
        final BlockPos position;
        final UUID originPlayer;
        int cursor=0,changed=0,blockEntities=0;
        boolean loadingRequested=false;
        java.util.concurrent.CompletableFuture<?> parentFuture,childFuture;
        long waitTicks=0;
        CopyJob(String id,String dimensionKey,ResourceKey<Level> parent,
                ResourceKey<Level> child,BlockPos position,UUID player) {
            this.id=id;this.dimensionKey=dimensionKey;this.parent=parent;
            this.child=child;this.position=position;this.originPlayer=player;
        }
    }

    public LayerWorlds(Path file){doneFile=file;load();}

    public static String vanillaKind(ResourceLocation dimension){
        String path=dimension.toString();
        if(path.equals("minecraft:overworld") || path.matches("timefracture:layer_[0-9]+_overworld"))return "overworld";
        if(path.equals("minecraft:the_nether") || path.matches("timefracture:layer_[0-9]+_nether"))return "nether";
        if(path.equals("minecraft:the_end") || path.matches("timefracture:layer_[0-9]+_end"))return "end";
        return null;
    }

    public static int slot(String id){
        if(id==null||!id.matches("T-[0-9]+"))return -1;
        try{
            int n=Integer.parseInt(id.substring(2));
            return n>=1&&n<=MAX_SLOTS?n:-1;
        }catch(NumberFormatException ex){return -1;}
    }

    public static ResourceKey<Level> destination(String id,String kind){
        int n=slot(id);
        if(n<0 || kind==null)return null;
        return ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath(
            "timefracture","layer_"+n+"_"+kind));
    }

    private static ResourceKey<Level> root(String kind){
        return switch(kind){
            case "overworld" -> Level.OVERWORLD;
            case "nether" -> Level.NETHER;
            case "end" -> Level.END;
            default -> null;
        };
    }

    private String copyKey(String id,String kind){return id+":"+kind;}
    private static void say(ServerPlayer p,String s){
        if(p!=null)p.sendSystemMessage(Component.literal(s));
    }
    private void say(MinecraftServer server,UUID user,String s){
        say(server.getPlayerList().getPlayer(user),s);
    }

    public synchronized String status(){
        var job=pending.peekFirst();
        return "снимков: "+completed.size()+", очередь: "+pending.size()+
            (job==null?"":"; "+job.id+"/"+job.dimensionKey+" "+job.cursor+" блоков")+
            "; нагрузка копирования: "+lastCopyMicros+" мкс/тик (максимум "+
            worstCopyMicros+" мкс), активных тиков: "+busyTicks;
    }

    public synchronized void enqueue(ServerPlayer subject,String id,String parent,BlockPos anchor){
        if(slot(id)<0)return;
        String kind=vanillaKind(subject.level().dimension().location());
        if(kind==null)return;
        String key=copyKey(id,kind);
        if(completed.contains(key)||enqueued.contains(key))return;
        ResourceKey<Level> source=parent==null||parent.equals("-")
            ?root(kind):destination(parent,kind);
        ResourceKey<Level> target=destination(id,kind);
        if(source==null||target==null)return;
        pending.add(new CopyJob(id,kind,source,target,anchor,subject.getUUID()));
        enqueued.add(key);
        say(subject,"§b[TimeFracture] Снимок стартового чанка для "+id+
            " поставлен в очередь, исходные координаты сохранены: "+
            anchor.getX()+" "+anchor.getY()+" "+anchor.getZ());
    }

    public synchronized String enter(ServerPlayer player,String id,LayerCatalog catalog){
        var info=catalog.find(id);
        if(info==null)return "§cНеизвестная временная линия "+id;
        if(slot(id)<0)return "§cПока поддерживаются T-1...T-"+MAX_SLOTS+
            ". Для следующих ветвей нужно расширение слотов.";
        String kind=vanillaKind(player.level().dimension().location());
        if(kind==null)return "§cВ этой версии входы доступны для обычного мира, Незера и Энда.";
        ResourceKey<Level> key=destination(id,kind);
        ServerLevel target=player.getServer().getLevel(key);
        if(target==null)return "§cИзмерение "+key.location()+" не загружено. "+
            "Проверь ресурсы мода и сохрани резервную копию мира.";

        String mark=copyKey(id,kind);
        if(!completed.contains(mark)){
            if(!enqueued.contains(mark)){
                ResourceKey<Level> src=info.parent().equals("-")
                    ?root(kind):destination(info.parent(),kind);
                if(src==null||player.getServer().getLevel(src)==null)
                    return "§cРодительский слой пока недоступен.";
                pending.add(new CopyJob(id,kind,src,key,
                    new BlockPos(info.x(),info.y(),info.z()),player.getUUID()));
                enqueued.add(mark);
            }
            requested.put(player.getUUID(),id);
            return "§eСлой "+id+" подготавливается небольшими пакетами без массового копирования за тик. "+
                "Переход произойдёт автоматически после завершения начального снимка.";
        }
        return performEnter(player,id,target);
    }

    private String performEnter(ServerPlayer p,String id,ServerLevel destination){
        var server=p.getServer();
        if(server==null)return "§cСервер недоступен";
        originals.putIfAbsent(p.getUUID(),new ReturnPoint(
            p.level().dimension().location().toString(),
            p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot()));
        // Save the return position on the player so it survives world reloads.
        var tag=p.getPersistentData();
        ReturnPoint home=originals.get(p.getUUID());
        tag.putString("tf_return_dim",home.dimension);
        tag.putDouble("tf_return_x",home.x);
        tag.putDouble("tf_return_y",home.y);
        tag.putDouble("tf_return_z",home.z);
        tag.putFloat("tf_return_yaw",home.yaw);
        tag.putFloat("tf_return_pitch",home.pitch);

        p.teleportTo(destination,p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());
        return "§aВход в "+id+": "+destination.dimension().location()+
            " · те же координаты. §eСкопирован только исходный чанк; дальние постройки могут отсутствовать.";
    }

    public synchronized String leave(ServerPlayer p){
        var home=originals.get(p.getUUID());
        if(home==null){
            var tag=p.getPersistentData();
            if(!tag.contains("tf_return_dim"))return "§cИсходная позиция не сохранена.";
            home=new ReturnPoint(tag.getString("tf_return_dim"),
                tag.getDouble("tf_return_x"),tag.getDouble("tf_return_y"),tag.getDouble("tf_return_z"),
                tag.getFloat("tf_return_yaw"),tag.getFloat("tf_return_pitch"));
        }
        ServerLevel origin;
        try{
            var dim=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(home.dimension));
            origin=p.getServer().getLevel(dim);
        }catch(RuntimeException ex){return "§cПовреждена исходная точка: "+ex.getMessage();}
        if(origin==null)return "§cИсходное измерение недоступно.";
        p.teleportTo(origin,home.x,home.y,home.z,home.yaw,home.pitch);
        originals.remove(p.getUUID());
        var tag=p.getPersistentData();
        tag.remove("tf_return_dim");tag.remove("tf_return_x");tag.remove("tf_return_y");
        tag.remove("tf_return_z");tag.remove("tf_return_yaw");tag.remove("tf_return_pitch");
        return "§aВозвращение в исходную линию на прежние координаты.";
    }

    public synchronized void tick(ServerTickEvent.Post event){
        if(++ticks%1!=0)return;
        var job=pending.peekFirst();
        if(job==null)return;
        MinecraftServer server=event.getServer();
        var parent=server.getLevel(job.parent);
        var child=server.getLevel(job.child);
        if(parent==null||child==null){
            pending.removeFirst();
            enqueued.remove(copyKey(job.id,job.dimensionKey));
            say(server,job.originPlayer,"§cНе удалось подготовить "+job.id+
                ": исходное или целевое измерение не существует.");
            return;
        }

        int chunkX=job.position.getX()>>4,chunkZ=job.position.getZ()>>4;
        ChunkPos chunkPos=new ChunkPos(chunkX,chunkZ);
        // PORTAL tickets keep the exact chunk around only while the copy
        // operates. The tickets are removed afterwards or expire naturally.
        // Futures begin chunk generation without forcing a synchronous
        // getChunk() call in our per-tick copy loop.
        if(!job.loadingRequested){
            job.loadingRequested=true;
            parent.getChunkSource().addRegionTicket(TicketType.PORTAL,chunkPos,2,job.position);
            child.getChunkSource().addRegionTicket(TicketType.PORTAL,chunkPos,2,job.position);
            job.parentFuture=parent.getChunkSource().getChunkFuture(chunkX,chunkZ,ChunkStatus.FULL,true);
            job.childFuture=child.getChunkSource().getChunkFuture(chunkX,chunkZ,ChunkStatus.FULL,true);
            return; // NEVER read blocks in the initial generation-request tick
        }
        job.waitTicks++;
        if(job.waitTicks%120==0){
            parent.getChunkSource().addRegionTicket(TicketType.PORTAL,chunkPos,2,job.position);
            child.getChunkSource().addRegionTicket(TicketType.PORTAL,chunkPos,2,job.position);
        }
        if(!job.parentFuture.isDone()||!job.childFuture.isDone())return;
        if(job.parentFuture.isCompletedExceptionally()||job.childFuture.isCompletedExceptionally()){
            pending.removeFirst();
            enqueued.remove(copyKey(job.id,job.dimensionKey));
            say(server,job.originPlayer,"§cОшибка фоновой подготовки чанка для "+job.id);
            return;
        }
        if(parent.getChunkSource().getChunkNow(chunkX,chunkZ)==null ||
           child.getChunkSource().getChunkNow(chunkX,chunkZ)==null){
            // No accidental synchronous chunk generation in the copy phase.
            if(job.waitTicks%100==0)
                say(server,job.originPlayer,"§eОжидание загруженных чанков "+job.id+
                    ", сервер продолжает отвечать...");
            return;
        }

        final int minY=Math.max(parent.getMinBuildHeight(),child.getMinBuildHeight());
        final int maxY=Math.min(parent.getMaxBuildHeight(),child.getMaxBuildHeight());
        final int volume=(maxY-minY)*256;
        if(volume<=0){
            pending.removeFirst();enqueued.remove(copyKey(job.id,job.dimensionKey));return;
        }
        final int baseX=job.position.getX()&~15;
        final int baseZ=job.position.getZ()&~15;
        long started=System.nanoTime();
        try{
            for(int n=0;n<BLOCKS_PER_TICK&&job.cursor<volume;n++){
                if(n>0 && System.nanoTime()-started>=NANOSECONDS_PER_TICK)break;
                int i=job.cursor++;
                int y=minY+i/256;
                int x=baseX+(i%16);
                int z=baseZ+((i/16)%16);
                BlockPos pos=new BlockPos(x,y,z);
                BlockState original=parent.getBlockState(pos);
                if(!original.equals(child.getBlockState(pos))){
                    child.setBlock(pos,original,2);
                    job.changed++;
                }
                if(original.hasBlockEntity()){
                    BlockEntity source=parent.getBlockEntity(pos);
                    if(source!=null){
                        var tag=source.saveWithFullMetadata(parent.registryAccess());
                        BlockEntity copy=BlockEntity.loadStatic(pos,original,tag,child.registryAccess());
                        if(copy!=null){
                            child.setBlockEntity(copy);
                            copy.setChanged();
                            job.blockEntities++;
                        }
                    }
                }
            }
            if(job.cursor>=volume){
                parent.getChunkSource().removeRegionTicket(TicketType.PORTAL,chunkPos,2,job.position);
                child.getChunkSource().removeRegionTicket(TicketType.PORTAL,chunkPos,2,job.position);
                String mark=copyKey(job.id,job.dimensionKey);
                completed.add(mark);
                persist();
                pending.removeFirst();enqueued.remove(mark);
                say(server,job.originPlayer,"§aСнимок чанка "+job.id+" готов: "+
                    job.changed+" изменённых блоков, "+job.blockEntities+" блок-сущностей.");
                // Rare autonomous echo. NOT a villager proxy; the entity has
                // its own UUID, persistent player model, equipment, goals and
                // one-time saved texture property. Only 20% of new layers get
                // a spawned echo, so encounters are NOT guaranteed.
                ServerPlayer original=server.getPlayerList().getPlayer(job.originPlayer);
                if(original!=null && child.getRandom().nextInt(5)==0){
                    dev.timefracture.EchoPlayerEntity echo=
                        dev.timefracture.TimeFractureMod.ECHO_PLAYER.get().create(child);
                    if(echo!=null){
                        int baseX=job.position.getX()&~15;
                        int baseZ=job.position.getZ()&~15;
                        int sx=baseX+2,sz=baseZ+2;
                        if(Math.abs(sx-job.position.getX())<5) sx=baseX+13;
                        if(Math.abs(sz-job.position.getZ())<5) sz=baseZ+13;
                        int sy=child.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,sx,sz);
                        echo.initializeFrom(original,job.id);
                        echo.setPos(sx+.5,sy,sz+.5);
                        child.addFreshEntity(echo);
                    }
                }

                for(var it=requested.entrySet().iterator();it.hasNext();){
                    var e=it.next();
                    if(!e.getValue().equals(job.id))continue;
                    ServerPlayer traveler=server.getPlayerList().getPlayer(e.getKey());
                    if(traveler!=null && vanillaKind(traveler.level().dimension().location())
                        .equals(job.dimensionKey)){
                        say(traveler,performEnter(traveler,job.id,child));
                    }
                    it.remove();
                }
            }
        }catch(Exception ex){
            parent.getChunkSource().removeRegionTicket(TicketType.PORTAL,chunkPos,2,job.position);
            child.getChunkSource().removeRegionTicket(TicketType.PORTAL,chunkPos,2,job.position);
            pending.removeFirst();
            enqueued.remove(copyKey(job.id,job.dimensionKey));
            say(server,job.originPlayer,"§cОшибка снимка "+job.id+": "+ex.getMessage()+
                ". Частичный снимок не считается завершённым и при следующем входе будет подготовлен заново.");
        }finally {
            long micros=(System.nanoTime()-started)/1000L;
            lastCopyMicros=micros;
            worstCopyMicros=Math.max(worstCopyMicros,micros);
            busyTicks++;
        }
    }

    private void load(){
        if(!Files.exists(doneFile))return;
        try{for(String s:Files.readAllLines(doneFile,StandardCharsets.UTF_8)){
            if(s.matches("T-[0-9]+:(overworld|nether|end)"))completed.add(s);
        }}catch(IOException ex){throw new IllegalStateException("Cannot read layer snapshot index",ex);}
    }
    private void persist(){
        try{
            Files.createDirectories(doneFile.getParent());
            Path tmp=doneFile.resolveSibling(doneFile.getFileName()+".tmp");
            Files.write(tmp,completed.stream().sorted().toList(),StandardCharsets.UTF_8);
            Files.move(tmp,doneFile,StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException ex){throw new IllegalStateException("Cannot persist layer snapshot index",ex);}
    }
}
