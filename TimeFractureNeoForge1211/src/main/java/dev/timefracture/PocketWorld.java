package dev.timefracture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Playable POCKET SNAPSHOTS, not complete copies of the infinite Minecraft world.
 *
 * Each branch has its own independently saved overworld/nether/end block volume,
 * far enough away from other branches that edits do not overwrite their blocks.
 * A child is copied from its parent pocket, so branching is recursive.
 *
 * Current limits: 21 x 17 x 21, no block-entity inventories, no original entities,
 * no cloned biome/weather/time, no independent per-branch simulation when unloaded.
 * This is intentionally labelled as an experimental localized timeline implementation.
 */
public final class PocketWorld {
    public static final int RADIUS=10, HEIGHT=8, MAX=256, PITCH=96;
    public record Pocket(String id,String dimension,int x,int y,int z,UUID subject,String name) {}
    public record ReturnPoint(String dimension,double x,double y,double z,float yaw,float pitch) {}
    private final Path file;
    private final Map<String,Pocket> pockets=new LinkedHashMap<>();
    private final Map<UUID,ReturnPoint> returns=new HashMap<>();
    private int index=0;

    public PocketWorld(Path file){this.file=file;load();}
    public synchronized int count(){return pockets.size();}
    public synchronized Pocket get(String id){return pockets.get(id);}
    public synchronized Collection<Pocket> list(){return List.copyOf(pockets.values());}
    public synchronized boolean isInPocket(ServerPlayer player) {
        for(Pocket p:pockets.values()) {
            if(!player.level().dimension().location().toString().equals(p.dimension))continue;
            if(Math.abs(player.getX()-p.x)<RADIUS+3 && Math.abs(player.getZ()-p.z)<RADIUS+3
              && Math.abs(player.getY()-p.y)<HEIGHT+5) return true;
        }
        return false;
    }

    public synchronized Pocket create(MinecraftServer server,String id,
                                      ServerPlayer sourcePlayer,String parent) {
        if(pockets.containsKey(id))return pockets.get(id);
        if(pockets.size()>=MAX)throw new IllegalStateException("Лимит локальных ветвей: "+MAX);
        if(sourcePlayer==null)throw new IllegalStateException("Реальный игрок не в сети");
        ServerLevel dimension=sourcePlayer.serverLevel();
        BlockPos source=sourcePlayer.blockPosition();
        if(parent!=null && !parent.equals("-")) {
            Pocket prev=pockets.get(parent);
            if(prev==null)throw new IllegalStateException("У родителя "+parent+" отсутствует снимок");
            dimension=level(server,prev.dimension);
            if(dimension==null)throw new IllegalStateException("Недоступно измерение родителя");
            source=new BlockPos(prev.x,prev.y,prev.z);
        }
        // All pockets are spaced apart in world coordinates. Creating a pocket
        // never modifies the original area around a real player.
        int slot=++index;
        int destX=500_000+(slot%64)*PITCH;
        int destZ=500_000+(slot/64)*PITCH;
        int y=Math.min(dimension.getMaxBuildHeight()-HEIGHT-2,
                       Math.max(dimension.getMinBuildHeight()+HEIGHT+2,source.getY()));
        BlockPos center=new BlockPos(destX,y,destZ);

        // Copy safe block states. Block entities deliberately excluded to avoid
        // duplicating inventory NBT and accidental world corruption/exploits.
        // No background threading: all Level access happens on the server thread.
        int copied=0;
        for(int dy=-HEIGHT;dy<=HEIGHT;dy++){
            for(int dx=-RADIUS;dx<=RADIUS;dx++){
                for(int dz=-RADIUS;dz<=RADIUS;dz++){
                    BlockPos src=source.offset(dx,dy,dz);
                    BlockPos dst=center.offset(dx,dy,dz);
                    BlockState state=dimension.getBlockState(src);
                    if(dimension.getBlockEntity(src)!=null)state=Blocks.AIR.defaultBlockState();
                    dimension.setBlock(dst,state,2);
                    copied++;
                }
            }
        }
        Pocket p=new Pocket(id,dimension.dimension().location().toString(),
                            destX,y,destZ,sourcePlayer.getUUID(),
                            sourcePlayer.getGameProfile().getName());
        pockets.put(id,p);
        save();
        sourcePlayer.sendSystemMessage(Component.literal("§d[TimeFracture] §aСоздано локальное ответвление "+id+
             " ("+copied+" блоков). Вход: /timefracture enter "+id));
        return p;
    }

    public synchronized void enter(ServerPlayer visitor,String id){
        Pocket p=pockets.get(id);
        if(p==null)throw new IllegalArgumentException("У линии "+id+" нет доступного локального снимка");
        MinecraftServer server=visitor.getServer();
        if(server==null)throw new IllegalStateException("Сервер недоступен");
        ServerLevel destination=level(server,p.dimension);
        if(destination==null)throw new IllegalStateException("Измерение линии недоступно");
        returns.putIfAbsent(visitor.getUUID(),
            new ReturnPoint(visitor.level().dimension().location().toString(),visitor.getX(),
                            visitor.getY(),visitor.getZ(),visitor.getYRot(),visitor.getXRot()));
        save();
        visitor.teleportTo(destination,p.x+.5,p.y+.2,p.z+.5,visitor.getYRot(),visitor.getXRot());
        visitor.sendSystemMessage(Component.literal("§b[TimeFracture] Линия "+id+
            ". Это отдельная сохранённая область мира, а не бесконечное измерение."));
    }

    public synchronized void leave(ServerPlayer visitor){
        ReturnPoint original=returns.get(visitor.getUUID());
        if(original==null)throw new IllegalArgumentException("Исходная позиция не сохранена");
        MinecraftServer server=visitor.getServer();
        ServerLevel destination=server==null?null:level(server,original.dimension);
        if(destination==null)throw new IllegalStateException("Исходное измерение сейчас недоступно");
        visitor.teleportTo(destination,original.x,original.y,original.z,
                           original.yaw,original.pitch);
        returns.remove(visitor.getUUID());
        save();
        visitor.sendSystemMessage(Component.literal("§a[TimeFracture] Возвращение в исходную реальность."));
    }

    public synchronized String closest(ServerPlayer p){
        for(Pocket cell:pockets.values()){
            if(!p.level().dimension().location().toString().equals(cell.dimension))continue;
            if(Math.abs(p.getX()-cell.x)<RADIUS+3&&Math.abs(p.getZ()-cell.z)<RADIUS+3
                &&Math.abs(p.getY()-cell.y)<HEIGHT+5)return cell.id;
        }
        return "-";
    }

    private static ServerLevel level(MinecraftServer server,String id){
        try{
            ResourceKey<Level> key=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(id));
            return server.getLevel(key);
        }catch(RuntimeException ex){return null;}
    }
    private static String enc(String s){
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
    private static String dec(String s){
        return new String(Base64.getUrlDecoder().decode(s),StandardCharsets.UTF_8);
    }
    private synchronized void save(){
        try{
            Files.createDirectories(file.getParent());
            List<String> lines=new ArrayList<>();
            lines.add("H\t"+index);
            for(Pocket p:pockets.values()){
                lines.add("P\t"+p.id+"\t"+p.dimension+"\t"+p.x+"\t"+p.y+"\t"+p.z+
                          "\t"+p.subject+"\t"+enc(p.name));
            }
            for(var entry:returns.entrySet()){
                ReturnPoint r=entry.getValue();
                lines.add("R\t"+entry.getKey()+"\t"+r.dimension+"\t"+r.x+"\t"+r.y+
                          "\t"+r.z+"\t"+r.yaw+"\t"+r.pitch);
            }
            Path tmp=file.resolveSibling(file.getFileName().toString()+".tmp");
            Files.write(tmp,lines,StandardCharsets.UTF_8);
            Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException ex){
            throw new IllegalStateException("Не удалось сохранить снимки временных линий",ex);
        }
    }
    private void load(){
        if(!Files.exists(file))return;
        try{
            for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){
                String[] a=line.split("\t");
                switch(a[0]){
                    case "H"->index=Integer.parseInt(a[1]);
                    case "P"->{Pocket p=new Pocket(a[1],a[2],Integer.parseInt(a[3]),
                        Integer.parseInt(a[4]),Integer.parseInt(a[5]),UUID.fromString(a[6]),dec(a[7]));
                        pockets.put(p.id,p);}
                    case "R"->returns.put(UUID.fromString(a[1]),new ReturnPoint(a[2],
                        Double.parseDouble(a[3]),Double.parseDouble(a[4]),Double.parseDouble(a[5]),
                        Float.parseFloat(a[6]),Float.parseFloat(a[7])));
                }
            }
        }catch(Exception ex){throw new IllegalStateException("Повреждены данные временных линий: "+file,ex);}
    }
}
