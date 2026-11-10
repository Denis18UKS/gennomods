package dev.chromamix.extra.mixin;
import dev.chromamix.extra.RgbRules;
import dev.chromamix.extra.client.RgbTintedBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Color RGB white-derived objects in inventories and when held without replacing their item models. */
@Mixin(ItemRenderer.class)
public abstract class RgbItemRendererMixin {
 @ModifyVariable(
  method="render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
  at=@At("HEAD"),argsOnly=true,ordinal=0,require=0)
 private MultiBufferSource chromamix$rgbItemBuffer(MultiBufferSource source,
      ItemStack stack, ItemDisplayContext transform, boolean left, PoseStack matrices,
      MultiBufferSource originalSource, int light, int overlay, BakedModel model) {
   if (!RgbRules.accepts(stack)) return source;
   Integer rgb=RgbRules.rgb(stack);
   return rgb == null ? source : new RgbTintedBuffer(source, rgb);
 }
}
