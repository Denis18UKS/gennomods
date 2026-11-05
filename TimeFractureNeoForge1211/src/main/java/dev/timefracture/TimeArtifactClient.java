package dev.timefracture;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A broken clock tries to tick, catches, and falls silent.
 * Subtle vanilla acoustic samples (no hidden external audio files).
 * Only runs on the client and only when the artifact is held.
 */
public final class TimeArtifactClient {
    private static boolean registered;
    private static int phase;

    private TimeArtifactClient(){}

    public static void open(){
        if(!registered){
            registered=true;
            NeoForge.EVENT_BUS.addListener(TimeArtifactClient::tick);
        }
        Minecraft.getInstance().setScreen(new TimeArtifactScreen());
    }

    private static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null){phase=0;return;}
        var player=mc.player;
        boolean held = isArtifact(player.getMainHandItem()) || isArtifact(player.getOffhandItem());
        if(!held){phase=0;return;}
        int p=phase++ % 210;
        if(p!=2 && p!=13 && p!=27 && p!=34)return;
        // A pair of ticks, a mechanical catch, and an irregular failed restart.
        float volume=p==34?.07f:.13f;
        float pitch=p==2?.80f:p==13?.71f:p==27?.53f:.62f;
        mc.level.playLocalSound(player.getX(),player.getY()+1,player.getZ(),
            p==27 ? SoundEvents.LEVER_CLICK : SoundEvents.STONE_BUTTON_CLICK_ON,
            SoundSource.PLAYERS,volume,pitch,false);
    }

    private static boolean isArtifact(net.minecraft.world.item.ItemStack stack){
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem())
            .toString().equals("timefracture:time_artifact");
    }
}
