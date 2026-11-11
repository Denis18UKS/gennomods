package dev.masterki.economy.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.masterki.economy.MasterkiNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid="masterki",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class MasterkiKeys {
    private MasterkiKeys(){}
    public static final KeyMapping OPEN=new KeyMapping(
        "key.masterki.open_shop",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_M,"key.categories.masterki");
    @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){
        event.register(OPEN);
    }
    @EventBusSubscriber(modid="masterki",value=Dist.CLIENT)
    public static final class Tick {
        private Tick(){}
        @SubscribeEvent public static void tick(ClientTickEvent.Post event){
            while (OPEN.consumeClick()) {
                Minecraft mc=Minecraft.getInstance();
                if(mc.player!=null && mc.screen==null)
                    PacketDistributor.sendToServer(new MasterkiNetwork.Action("open","",1));
            }
        }
    }
}
