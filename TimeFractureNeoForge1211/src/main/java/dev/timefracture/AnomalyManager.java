package dev.timefracture;

import dev.timefracture.layer.LayerWorlds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Opt-in, low-frequency crossing into an EXISTING REAL layer dimension,
 * no displaced pocket or villager proxies.
 * One roll every 5 seconds; approximate average one intrusion per 50 minutes.
 */
public final class AnomalyManager {
    private static final long COOLDOWN_MS=120_000L;
    private final Path path;
    private final Set<UUID> enabled=new HashSet<>();
    private final Map<UUID,Long> recent=new HashMap<>();
    private long ticks=0L;
    public AnomalyManager(Path path){this.path=path;load();}

    public synchronized boolean enabled(UUID id){return enabled.contains(id);}
    public synchronized void set(UUID id,boolean on){
        if(on)enabled.add(id);
        else enabled.remove(id);
        save();
    }
    public synchronized boolean travel(ServerPlayer player,boolean forced){
        if(TimeFractureMod.LAYERS==null || TimeFractureMod.TRAVEL==null)return false;
        if(!forced && TimeFractureMod.L.frozen)return false;
        long now=System.currentTimeMillis();
        if(!forced && now-recent.getOrDefault(player.getUUID(),0L)<COOLDOWN_MS)return false;
        var kind=LayerWorlds.vanillaKind(player.level().dimension().location());
        if(kind==null)return false;
        var choices=TimeFractureMod.LAYERS.list().stream()
            .filter(a->LayerWorlds.slot(a.id())>=1)
            .filter(a->!player.level().dimension().equals(LayerWorlds.destination(a.id(),kind)))
            .toList();
        if(choices.isEmpty())return false;
        var target=choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        // enter() validates the dimension exists and queues a chunk snapshot,
        // otherwise leaves the player untouched.
        String result=TimeFractureMod.TRAVEL.enter(player,target.id(),TimeFractureMod.LAYERS);
        if(result.startsWith("§c")) {
            player.sendSystemMessage(Component.literal("[TimeFracture] "+result));
            return false;
        }
        recent.put(player.getUUID(),now);
        player.sendSystemMessage(Component.literal("§5§lВРЕМЕННАЯ АНОМАЛИЯ! §d"+
            target.id()+" — "+result+" §7Вернуться: /timefracture return"));
        return true;
    }

    public synchronized void tick(ServerTickEvent.Post event){
        if(++ticks%100!=0 || TimeFractureMod.L.frozen || TimeFractureMod.LAYERS==null)return;
        if(TimeFractureMod.LAYERS.size()<1)return;
        long now=System.currentTimeMillis();
        for(ServerPlayer p:event.getServer().getPlayerList().getPlayers()){
            if(!enabled.contains(p.getUUID()))continue;
            if(now-recent.getOrDefault(p.getUUID(),0L)<COOLDOWN_MS)continue;
            if(ThreadLocalRandom.current().nextInt(600)==0)travel(p,false);
        }
        if(ticks%1200==0)recent.entrySet().removeIf(e->now-e.getValue()>COOLDOWN_MS*2);
    }

    private void load(){
        if(!Files.exists(path))return;
        try{
            for(String entry:Files.readAllLines(path)){
                try{enabled.add(UUID.fromString(entry.trim()));}
                catch(IllegalArgumentException ignored){}
            }
        }catch(IOException ex){throw new IllegalStateException("Не удалось загрузить настройки аномалий",ex);}
    }
    private void save(){
        try{
            Files.createDirectories(path.getParent());
            Path temp=path.resolveSibling(path.getFileName()+".tmp");
            Files.write(temp,enabled.stream().map(UUID::toString).sorted().toList());
            try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException ex){throw new IllegalStateException("Не удалось сохранить настройки аномалий",ex);}
    }
}
