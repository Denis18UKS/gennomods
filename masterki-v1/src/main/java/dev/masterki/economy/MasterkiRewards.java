package dev.masterki.economy;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Distinct daily species caps and earned rewards, based on the killer's actual
 * Apotheosis World Tier. 05:00 Ufa reset is shared with existing daily tasks.
 */
@EventBusSubscriber(modid="masterki")
public final class MasterkiRewards {
    public record HuntRule(String key,long baseReward,int dailyCap,String name) {}
    private MasterkiRewards(){}

    public static HuntRule rule(String key){
        return switch(key){
            case "minecraft:zombie" -> new HuntRule(key,15,12,"Зомби");
            case "minecraft:husk","minecraft:drowned" -> new HuntRule(key,18,12,"Зомби");
            case "minecraft:skeleton" -> new HuntRule(key,20,10,"Скелет");
            case "minecraft:stray","minecraft:bogged" -> new HuntRule(key,25,10,"Скелет");
            case "minecraft:spider" -> new HuntRule(key,15,12,"Паук");
            case "minecraft:cave_spider" -> new HuntRule(key,22,10,"Пещерный паук");
            case "minecraft:creeper" -> new HuntRule(key,40,5,"Крипер");
            case "minecraft:charged_creeper" -> new HuntRule(key,80,5,"Заряженный крипер");
            case "minecraft:enderman" -> new HuntRule(key,45,6,"Эндермен");
            case "minecraft:witch" -> new HuntRule(key,50,5,"Ведьма");
            case "minecraft:blaze" -> new HuntRule(key,30,8,"Ифрит");
            case "minecraft:ghast" -> new HuntRule(key,45,6,"Гаст");
            case "minecraft:pillager" -> new HuntRule(key,20,10,"Разбойник");
            case "minecraft:vindicator" -> new HuntRule(key,35,8,"Поборник");
            case "minecraft:evoker" -> new HuntRule(key,65,4,"Вызыватель");
            case "minecraft:ravager" -> new HuntRule(key,80,4,"Разоритель");
            case "minecraft:guardian" -> new HuntRule(key,28,8,"Страж");
            case "minecraft:elder_guardian" -> new HuntRule(key,140,2,"Древний страж");
            case "minecraft:piglin_brute" -> new HuntRule(key,50,5,"Брутальный пиглин");
            case "minecraft:wither_skeleton" -> new HuntRule(key,40,6,"Скелет-иссушитель");
            case "minecraft:breeze" -> new HuntRule(key,40,6,"Бриз");
            case "minecraft:warden" -> new HuntRule(key,650,1,"Варден");
            case "minecraft:wither" -> new HuntRule(key,450,2,"Визер");
            case "minecraft:ender_dragon" -> new HuntRule(key,550,1,"Эндер-дракон");
            default -> new HuntRule(key,20,8,key);
        };
    }

    private static String key(net.minecraft.world.entity.LivingEntity victim){
        if(victim instanceof Creeper creeper && creeper.isPowered())
            return "minecraft:charged_creeper";
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player)||player.getAbilities().instabuild)return;
        ResourceLocation advancement=event.getAdvancement().id();
        String path=advancement.getPath();
        if(path.contains("recipes/")||path.endsWith("/root")||path.startsWith("root")||
            path.startsWith("unlock_")||advancement.getNamespace().equals("masterki"))return;
        var data=Masterki.Saved.of(player.serverLevel());
        String marker="advancement:"+advancement;
        if(!data.firstDiscovery(player.getUUID(),marker))return;
        long base=path.contains("dragon")||path.contains("wither")||path.contains("netherite")?350:100;
        grant(player,MasterkiDifficulty.adjusted(base,player),"Достижение: "+advancement);
    }

    @SubscribeEvent
    public static void onHostileDeath(LivingDeathEvent event){
        if(!(event.getSource().getEntity() instanceof ServerPlayer player)
            ||player.getAbilities().instabuild)return;
        if(event.getEntity()==player)return;
        var type=event.getEntity().getType();
        boolean boss=type==EntityType.WITHER||type==EntityType.WARDEN||type==EntityType.ENDER_DRAGON;
        if(!boss && !(event.getEntity() instanceof Enemy))return;
        String species=key(event.getEntity());
        HuntRule rule=rule(species);
        var data=Masterki.Saved.of(player.serverLevel());
        long day=MasterkiSchedule.cycleDay(System.currentTimeMillis());
        long reward=MasterkiDifficulty.adjusted(rule.baseReward(),player);
        if(data.balance(player.getUUID())>Long.MAX_VALUE-reward)return;
        if(!data.recordSpeciesHunt(player.getUUID(),day,species,rule.dailyCap()))return;
        int count=data.speciesHuntCount(player.getUUID(),day,species);
        grant(player,reward,(boss?"Босс: ":"Охота: ")+
            event.getEntity().getName().getString()+" ("+count+"/"+rule.dailyCap()+
            ", "+MasterkiDifficulty.tier(player)+")");
        if(count==rule.dailyCap())
            Masterki.tell(player,"Лимит наград за "+rule.name()+": "+count+"/"+rule.dailyCap()+
                ". Сброс в 05:00 по расписанию.",ChatFormatting.YELLOW);
    }

    @SubscribeEvent
    public static void onExplore(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer player)||player.getAbilities().instabuild)return;
        if(player.tickCount%1200!=0)return;
        var data=Masterki.Saved.of(player.serverLevel());
        String dimension="dimension:"+player.serverLevel().dimension().location();
        if(data.firstDiscovery(player.getUUID(),dimension))
            grant(player,MasterkiDifficulty.adjusted(200,player),
                "Открыто измерение: "+player.serverLevel().dimension().location());
        player.serverLevel().getBiome(player.blockPosition()).unwrapKey().ifPresent(biome->{
            if(data.firstDiscovery(player.getUUID(),"biome:"+biome.location()))
                grant(player,MasterkiDifficulty.adjusted(50,player),"Новый биом: "+biome.location());
        });
    }

    private static void grant(ServerPlayer player,long reward,String reason){
        var data=Masterki.Saved.of(player.serverLevel());
        long money=data.balance(player.getUUID());
        if(money>Long.MAX_VALUE-reward)return;
        data.setBalance(player.getUUID(),money+reward);
        data.log(player.getUUID(),reason+" (+"+reward+" М)");
        Masterki.tell(player,"+"+reward+" М · "+reason,ChatFormatting.GOLD);
        Masterki.sync(player,false,"");
    }
}
