package dev.chromamix.extra.mixin;
import com.simibubi.create.content.equipment.toolbox.ToolboxBlock;
import dev.chromamix.extra.RgbRules;
import dev.chromamix.extra.RgbSaved;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Create toolbox extracts its item manually; keep RGB in the output while retaining its inventory components. */
@Mixin(value=ToolboxBlock.class, remap=false)
public abstract class RgbToolboxPickupMixin {
 @Inject(method="getCloneItemStack(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;",
         at=@At("RETURN"),remap=false,require=0,cancellable=true)
 private void chromamix$toolboxPickup(LevelReader level, BlockPos pos, BlockState state,
                                       CallbackInfoReturnable<ItemStack> cir) {
   if (!(level instanceof ServerLevel server)) return;
   Integer rgb=RgbSaved.of(server).get(pos);
   ItemStack stack=cir.getReturnValue();
   if(rgb!=null && stack!=null && !stack.isEmpty() && RgbRules.accepts(stack))
     cir.setReturnValue(RgbRules.withRgb(stack,rgb));
 }
}
