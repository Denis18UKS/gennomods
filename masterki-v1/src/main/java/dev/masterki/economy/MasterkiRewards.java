package dev.masterki.economy;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server-only earned currency. Unique achievements/locations are paid once.
 * Hunting has a strict daily cap, to avoid passive mob farms printing money.
 */
@EventBusSubscriber(modid="masterki")
public final class MasterkiRewards {
    private static final long DAY_MS=86_400_000L;
    private MasterkiRewards() {}

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
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
        if(!(event.getSource().getEntity() instanceof ServerPlayer player))return;
        if(!(event.getEntity() instanceof Enemy))return;
        if(event.getEntity()==player)return;
        var data=Masterki.Saved.of(player.serverLevel());
        long today=System.currentTimeMillis()/DAY_MS;
        // At most 12 paid kills for each player per real-world day.
        if(!data.recordHunt(player.getUUID(),today))return;
        String id=BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).getPath();
        long reward=id.contains("wither")||id.contains("ender_dragon")||id.contains("warden")?450:25;
        grant(player,reward,"Охота: "+event.getEntity().getName().getString());
    }

    @SubscribeEvent
    public static void onExplore(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
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
