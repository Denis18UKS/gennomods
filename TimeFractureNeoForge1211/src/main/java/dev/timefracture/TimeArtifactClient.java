package dev.timefracture;
import net.minecraft.client.Minecraft;
/** Client-only screen entrypoint. Called only by Item#use on the physical client. */
public final class TimeArtifactClient {
    public static void open(){
        Minecraft.getInstance().setScreen(new TimeArtifactScreen());
    }
}
