package dev.timefracture;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random cross-line intrusions.
 * Default OFF per player: opt-in avoids involuntary live-server teleportation.
 */
public final class AnomalyManager {
    private final Path path;
    private final Set<UUID> enabled=new HashSet<>();
    private long ticks;
    public AnomalyManager(Path path) {this.path=path;load();}

    public synchronized boolean enabled(UUID id){return enabled.contains(id);}
    public synchronized void set(UUID id,boolean on){
        if(on)enabled.add(id);
        else enabled.remove(id);
        save();
    }

    public synchronized boolean travel(ServerPlayer p,boolean forced) {
        PocketWorld world=TimeFractureMod.P;
        if(world==null||world.count()<1)return false;
        if(!forced && TimeFractureMod.L.frozen)return false;
        String current=world.closest(p);
        var choices=world.list().stream().filter(x->!x.id().equals(current)).toList();
        if(choices.isEmpty())return false;
        var chosen=choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        try{
            world.enter(p,chosen.id());
            p.sendSystemMessage(Component.literal("§5§lВРЕМЕННАЯ АНОМАЛИЯ! §dТебя затянуло в чужую линию "+chosen.id()+
                ". Опасайся своего двойника. /timefracture return"));
            return true;
        }catch(RuntimeException ex){
            p.sendSystemMessage(Component.literal("§cОшибка перехода: "+ex.getMessage()));
            return false;
        }
    }

    public void tick(ServerTickEvent.Post e){
        // Chance roll once per 5 seconds per participating online player.
        if(++ticks%100!=0)return;
        if(TimeFractureMod.L.frozen||TimeFractureMod.P==null
            ||TimeFractureMod.P.count()<3)return;
        for(ServerPlayer p:e.getServer().getPlayerList().getPlayers()){
            if(!enabled(p.getUUID()))continue;
            // Approx. one crossing every 50 minutes in active play with 3+ pockets.
            if(ThreadLocalRandom.current().nextInt(600)==0)travel(p,false);
        }
    }

    private void load(){
        if(!Files.exists(path))return;
        try{
            for(String s:Files.readAllLines(path)){
                try{enabled.add(UUID.fromString(s.trim()));}
                catch(IllegalArgumentException ignored){}
            }
        }catch(IOException ex){throw new IllegalStateException("Не удалось прочитать настройки аномалий",ex);}
    }
    private void save(){
        try{
            Files.createDirectories(path.getParent());
            var ordered=enabled.stream().map(UUID::toString).sorted().toList();
            Path tmp=path.resolveSibling(path.getFileName().toString()+".tmp");
            Files.write(tmp,ordered);
            Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException ex){throw new IllegalStateException("Не удалось записать настройки аномалий",ex);}
    }
}
