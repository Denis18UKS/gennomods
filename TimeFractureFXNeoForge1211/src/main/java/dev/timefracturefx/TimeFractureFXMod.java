package dev.timefracturefx;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** Client-side NeoForge 1.21.1 FX alpha. */
@Mod(value = "timefracturefx", dist = Dist.CLIENT)
public final class TimeFractureFXMod {
    public TimeFractureFXMod() {
        FxClient.initialize();
    }
}
