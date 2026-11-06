package dev.timefracture;

import dev.timefracture.layer.LayerWorlds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

/**
 * A physical, server-authoritative rift gate. The portal's block position is
 * stored in WORLD coordinates. It never follows a camera or a client.
 *
 * Gate states persist to disk and expire, without force-loading distant chunks.
 * This supports actual entry into existing TimeFracture layer dimensions,
 * unlike client-only FX previews. It is deliberately NOT a full-world clone.
 */
public final class RiftGates {
    private static final int MAX_GATES=32;
    private static final long LIFETIME_MS=5L*60_000L;
    private static final long COOLDOWN_MS=8_000L;
    private final Map<GateKey,Gate> active=new LinkedHashMap<>();
    private final Map<UUID,Long> cooldown=new HashMap<>();
    private final Path file;
    private long ticks=0;

    private record GateKey(String dimension,long position) {}
    private record Gate(GateKey key,String destination,UUID owner,long expires) {}

    public RiftGates(Path file){this.file=file;read();}

    public synchronized String open(ServerPlayer user,String destination){
        if(TimeFractureMod.LAYERS==null || TimeFractureMod.LAYERS.find(destination)==null)
            return "§cВременная линия не существует: "+destination;
        if(LayerWorlds.slot(destination)<0)return "§cНет игрового измерения для "+destination;
        if(active.size()>=MAX_GATES)return "§cДостигнут безопасный лимит открытых проходов: "+MAX_GATES;
        if(!user.isCreative() && !holdsArtifact(user))
            return "§cДля открытия разлома держи артефакт сломанных часов в руке.";
        ServerLevel level=user.serverLevel();
        BlockPos pos=user.blockPosition().relative(user.getDirection(),3).above();
        if(!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir())
            return "§cВпереди недостаточно места: нужно 2 свободных блока.";
        if(!level.setBlock(pos,TimeFractureMod.RIFT_GATE.get().defaultBlockState(),3))
            return "§cНе удалось разместить разлом.";
        // The portal occupies only air; this never overwrites a chest, machine or block.
        GateKey key=new GateKey(level.dimension().location().toString(),pos.asLong());
        active.put(key,new Gate(key,destination,user.getUUID(),System.currentTimeMillis()+LIFETIME_MS));
        save();
        return "§dПространственный проход открыт в §f"+destination+
            " §dна координатах "+pos.toShortString()+". Срок: 5 минут. Пройди сквозь него.";
    }

    private boolean holdsArtifact(ServerPlayer user){
        return itemId(user.getMainHandItem()).equals("timefracture:time_artifact")
            || itemId(user.getOffhandItem()).equals("timefracture:time_artifact");
    }
    private static String itemId(net.minecraft.world.item.ItemStack item){
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
    }
    public synchronized String status(){
        return "Активных физических проходов: "+active.size()+"/"+MAX_GATES+
            ". Используй /timefracture gate open <T-ID> с артефактом.";
    }

    public synchronized void contact(ServerPlayer p,BlockPos pos){
        MinecraftServer server=p.getServer();
        if(server==null || TimeFractureMod.TRAVEL==null || TimeFractureMod.LAYERS==null)return;
        long now=System.currentTimeMillis();
        GateKey key=new GateKey(p.serverLevel().dimension().location().toString(),pos.asLong());
        Gate gate=active.get(key);
        if(gate==null || gate.expires<now)return;
        if(now-cooldown.getOrDefault(p.getUUID(),0L)<COOLDOWN_MS)return;
        cooldown.put(p.getUUID(),now);
        String info=TimeFractureMod.TRAVEL.enter(p,gate.destination,TimeFractureMod.LAYERS);
        p.sendSystemMessage(Component.literal("§5[Разлом] "+info));
    }

    public synchronized void tick(ServerTickEvent.Post e){
        if(++ticks%20!=0)return;
        MinecraftServer server=e.getServer();
        long now=System.currentTimeMillis();
        boolean changed=false;
        for(var iterator=active.entrySet().iterator();iterator.hasNext();){
            Gate gate=iterator.next().getValue();
            if(gate.expires>now)continue;
            ServerLevel level;
            try{
                ResourceKey<Level> id=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(gate.key.dimension));
                level=server.getLevel(id);
            }catch(RuntimeException ex){iterator.remove();changed=true;continue;}
            if(level==null){iterator.remove();changed=true;continue;}
            BlockPos pos=BlockPos.of(gate.key.position);
            // Only close a gate if its chunk is ALREADY loaded. Never load
            // chunks from this expiry housekeeping path (FPS/TPS protection).
            if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)continue;
            if(level.getBlockState(pos).is(TimeFractureMod.RIFT_GATE.get()))
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            iterator.remove();changed=true;
        }
        if(changed)save();
        // Bound memory in long-lived servers.
        if(ticks%1200==0) cooldown.entrySet().removeIf(x->now-x.getValue()>COOLDOWN_MS*2);
    }

    private void read(){
        if(!Files.exists(file))return;
        try{
            for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){
                String[] a=line.split("\t");
                if(a.length!=5)continue;
                var k=new GateKey(a[0],Long.parseLong(a[1]));
                active.put(k,new Gate(k,a[2],UUID.fromString(a[3]),Long.parseLong(a[4])));
            }
        }catch(Exception ex){throw new IllegalStateException("Некорректное сохранение порталов: "+file,ex);}
    }
    private void save(){
        try{
            Files.createDirectories(file.getParent());
            List<String> lines=new ArrayList<>();
            for(var gate:active.values())
                lines.add(String.join("\t",gate.key.dimension,Long.toString(gate.key.position),
                    gate.destination,gate.owner.toString(),Long.toString(gate.expires)));
            Path tmp=file.resolveSibling(file.getFileName()+".tmp");
            Files.write(tmp,lines,StandardCharsets.UTF_8);
            try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException ex){throw new IllegalStateException("Не удалось сохранить проходы",ex);}
    }
}
