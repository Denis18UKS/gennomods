package dev.timefracturefx;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Client-only FX; the timeline gameplay mod remains separate. */
@Mod(value = "timefracturefx", dist = Dist.CLIENT)
public final class TimeFractureFXMod {
    public TimeFractureFXMod(IEventBus modBus) {
        modBus.addListener(FxShaders::register);
        FxClient.initialize();
    }
}
