package dev.timefracture;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent vanilla Villager-based NPC proxies with real pathfinding and
 * scripted contextual dialogue. NOT neural AI and NOT a real cloned player model.
 *
 * Temporal-echo villagers are different world entities from online players.
 * They are never put in the real online-player list.
 */
public final class TimeEchoes {
    private static final Map<String,Long> cooldown=new HashMap<>();
    private static long ticks=0;
    private TimeEchoes(){}

    public static void create(ServerLevel level,PocketWorld.Pocket pocket,int count){
        Villager echo=EntityType.VILLAGER.create(level);
        if(echo!=null){
            echo.setPos(pocket.x()+6.5,pocket.y()+2,pocket.z()+4.5);
            echo.setCustomName(Component.literal("§dАльтернативный "+pocket.name()));
            echo.setCustomNameVisible(true);
            echo.setPersistenceRequired();
            echo.getPersistentData().putString("tf_echo_subject",pocket.subject().toString());
            echo.getPersistentData().putString("tf_line",pocket.id());
            level.addFreshEntity(echo);
        }
        if(count>0 && count%5==0){
            Villager observer=EntityType.VILLAGER.create(level);
            if(observer!=null){
                observer.setPos(pocket.x()-6.5,pocket.y()+2,pocket.z()-3.5);
                observer.setCustomName(Component.literal("§5Хранитель временного равновесия"));
                observer.setCustomNameVisible(true);
                observer.setPersistenceRequired();
                observer.getPersistentData().putBoolean("tf_keeper",true);
                level.addFreshEntity(observer);
            }
        }
    }

    public static void tick(ServerTickEvent.Post event){
        if(++ticks%40!=0)return;
        MinecraftServer server=event.getServer();
        PocketWorld world=TimeFractureMod.P;
        if(world==null)return;
        for(ServerPlayer player:server.getPlayerList().getPlayers()){
            if(!world.isInPocket(player))continue;
            ServerLevel level=player.serverLevel();
            AABB nearby=player.getBoundingBox().inflate(18);
            for(Villager npc:level.getEntitiesOfClass(Villager.class,nearby,
                  v->v.getPersistentData().contains("tf_echo_subject")
                      ||v.getPersistentData().getBoolean("tf_keeper"))){
                if(npc.getPersistentData().getBoolean("tf_keeper")){
                    npc.getNavigation().moveTo(player,1.05);
                    if(npc.distanceToSqr(player)<36)talk(player,npc,
                        "§5Хранитель: §fКакого хрена ты творишь со временем? "+
                        "Каждый раскол оставляет след. Верни баланс, пока не стало поздно.");
                    continue;
                }
                String identity=npc.getPersistentData().getString("tf_echo_subject");
                if(identity.isBlank())continue;
                UUID represented;
                try {represented=UUID.fromString(identity);}
                catch(IllegalArgumentException ex){continue;}
                if(!represented.equals(player.getUUID()))continue;
                npc.getNavigation().moveTo(player,1.03);
                double d=npc.distanceToSqr(player);
                if(d<64)talk(player,npc,"§dТвоё другое Я: §fЯ знаю, что ты здесь. "+
                    "Нам нельзя встречаться. Уходи!");
                // For safety, collapse is a non-destructive containment event.
                // Never delete actual level.dat or region files.
                if(d<4.0){
                    player.sendSystemMessage(Component.literal(
                        "§4§lВРЕМЕННОЙ ПАРАДОКС! §cДве версии одной личности столкнулись."));
                    TimeFractureMod.L.frozen=true;
                    TimeFractureMod.L.save();
                    try {world.leave(player);}
                    catch(RuntimeException ex){
                        player.sendSystemMessage(Component.literal("§cНе удалось эвакуировать: "+ex.getMessage()));
                    }
                    player.sendSystemMessage(Component.literal(
                        "§eВетвления заморожены. DEV: /timefracture balance restore all"));
                    break;
                }
            }
        }
    }

    private static void talk(ServerPlayer player,Villager npc,String line){
        String key=player.getUUID()+":"+npc.getUUID();
        long now=System.currentTimeMillis();
        if(now-cooldown.getOrDefault(key,0L)<23_000)return;
        cooldown.put(key,now);
        player.sendSystemMessage(Component.literal(line));
    }
}
