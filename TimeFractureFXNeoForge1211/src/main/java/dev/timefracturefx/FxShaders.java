package dev.timefracturefx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import java.io.IOException;

/** Loads actual world-surface GLSL programs, not post-process overlays. */
public final class FxShaders {
    public static ShaderInstance PORTAL;
    public static ShaderInstance RIFT;
    private FxShaders() {}

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("timefracturefx","timefracture_portal"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> PORTAL = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("timefracturefx","timefracture_rift"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> RIFT = shader);
        } catch (IOException ex) {
            throw new IllegalStateException("TimeFractureFX failed to load world-space shaders", ex);
        }
    }
}
