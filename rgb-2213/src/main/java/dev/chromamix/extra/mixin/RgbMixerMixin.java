package dev.chromamix.extra.mixin;

import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import dev.chromamix.extra.RgbDynamicRecipe;
import net.minecraft.world.item.crafting.Recipe;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MechanicalMixerBlockEntity.class, remap = false)
public abstract class RgbMixerMixin {
  @Inject(method = "getMatchingRecipes()Ljava/util/List;", at = @At("RETURN"),
          remap = false, cancellable = true, require = 1)
  private void chromamix$dynamicRecipes(CallbackInfoReturnable<List<Recipe<?>>> cir) {
    MechanicalMixerBlockEntity mixer = (MechanicalMixerBlockEntity)(Object)this;
    var level = mixer.getLevel();
    if (level == null || level.isClientSide) return;
    var found = level.getBlockEntity(mixer.getBlockPos().below(2));
    if (!(found instanceof BasinBlockEntity basin)) return;
    Recipe<?> additional = RgbDynamicRecipe.fromBasin(basin);
    if (additional == null) return;
    List<Recipe<?>> combined = new ArrayList<>(cir.getReturnValue());
    combined.add(0, additional);
    cir.setReturnValue(combined);
  }
}
