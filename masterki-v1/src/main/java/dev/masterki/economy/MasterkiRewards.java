package dev.masterki.economy;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server-only earned currency. Unique achievements/locations are paid once.
 * Hunting has separate daily caps: 12 ordinary hostile mobs and 2 vanilla bosses.
 */
@EventBusSubscriber(modid="masterki")
public final class MasterkiRewards {
    private static final long DAY_MS=86_400_000L;
    private MasterkiRewards() {}

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||player.getAbilities().instabuild)return;
        ResourceLocation advancement=event.getAdvancement().id();
        String path=advancement.getPath();
        if(path.contains("recipes/")||path.endsWith("/root")||path.startsWith("root")||
           path.startsWith("unlock_")||advancement.getNamespace().equals("masterki"))return;
        var data=Masterki.Saved.of(player.serverLevel());
        String achievement="advancement:"+advancement;
        if(!data.firstDiscovery(player.getUUID(),achievement))return;
        long reward=path.contains("dragon")||path.contains("wither")||path.contains("netherite")?350:100;
        grant(player,reward,"Достижение: "+advancement);
    }

    @SubscribeEvent
    public static void onHostileDeath(LivingDeathEvent event) {
        if(!(event.getSource().getEntity() instanceof ServerPlayer player)||player.getAbilities().instabuild)return;
        if(event.getEntity()==player)return;
        // Dragon is NOT an Enemy in Minecraft 1.21.1, so check boss types first.
        // Counting each category separately prevents 12 zombies from blocking a boss reward.
        var type=event.getEntity().getType();
        boolean boss=type==EntityType.WITHER || type==EntityType.WARDEN || type==EntityType.ENDER_DRAGON;
        if(!boss && !(event.getEntity() instanceof Enemy))return;
        var data=Masterki.Saved.of(player.serverLevel());
        long today=System.currentTimeMillis()/DAY_MS;
        long reward=boss?450L:25L;
        if(data.balance(player.getUUID())>Long.MAX_VALUE-reward)return;
        if(boss){
            if(!data.recordBossHunt(player.getUUID(),today))return;
            int count=data.bossHuntCount(player.getUUID(),today);
            grant(player,reward,"Охота на босса: "+event.getEntity().getName().getString()+" ("+count+"/2)");
            if(count==2)Masterki.tell(player,"Дневной лимит наград за боссов: 2/2. Сброс в 00:00 UTC.",ChatFormatting.YELLOW);
        }else{
            if(!data.recordHunt(player.getUUID(),today))return;
            int count=data.huntCount(player.getUUID(),today);
            grant(player,reward,"Охота: "+event.getEntity().getName().getString()+" ("+count+"/12)");
            if(count==12)Masterki.tell(player,"Дневной лимит обычной охоты: 12/12. Боссы оплачиваются отдельно. Сброс в 00:00 UTC.",ChatFormatting.YELLOW);
        }
    }

    @SubscribeEvent
    public static void onExplore(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||player.getAbilities().instabuild)return;
        if(player.tickCount%1200!=0)return; // Once every 60 seconds, never every game tick.
        String dimension="dimension:"+player.serverLevel().dimension().location();
        var data=Masterki.Saved.of(player.serverLevel());
        if(data.firstDiscovery(player.getUUID(),dimension))
            grant(player,200,"Открыто измерение: "+player.serverLevel().dimension().location());
        player.serverLevel().getBiome(player.blockPosition()).unwrapKey().ifPresent(key->{
            if(data.firstDiscovery(player.getUUID(),"biome:"+key.location()))
                grant(player,50,"Новый биом: "+key.location());
        });
    }

    private static void grant(ServerPlayer player,long reward,String reason) {
        var data=Masterki.Saved.of(player.serverLevel());
        long money=data.balance(player.getUUID());
        if(money>Long.MAX_VALUE-reward)return;
        data.setBalance(player.getUUID(),money+reward);
        data.log(player.getUUID(),reason+" (+"+reward+" М)");
        Masterki.tell(player,"+"+reward+" М · "+reason,ChatFormatting.GOLD);
        Masterki.sync(player,false,"");
    }
}
