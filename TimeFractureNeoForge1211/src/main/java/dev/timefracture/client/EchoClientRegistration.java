package dev.timefracture.client;

import dev.timefracture.TimeFractureMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="timefracture",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class EchoClientRegistration {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(TimeFractureMod.ECHO_PLAYER.get(),EchoPlayerRenderer::new);
    }
}
