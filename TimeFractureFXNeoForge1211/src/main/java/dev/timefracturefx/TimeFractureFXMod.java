package dev.timefracturefx;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.DistExecutor;

@Mod("timefracturefx")
public final class TimeFractureFXMod {
    public TimeFractureFXMod() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> FxClient::initialize);
    }
}
