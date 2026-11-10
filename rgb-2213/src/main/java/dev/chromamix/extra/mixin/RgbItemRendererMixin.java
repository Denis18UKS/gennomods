package dev.chromamix.extra.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.chromamix.extra.RgbRules;
import dev.chromamix.extra.client.RgbItemRenderContext;
import dev.chromamix.extra.client.RgbTintedBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** White bed item texture is recolored only by RgbBedMixin; no full tint buffer. */
@Mixin(ItemRenderer.class)
public abstract class RgbItemRendererMixin {
 private static final String METHOD="render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V";
 @Inject(method=METHOD,at=@At("HEAD"),require=0)
 private void chromamix$begin(ItemStack stack,ItemDisplayContext mode,boolean left,PoseStack pose,MultiBufferSource source,int light,int overlay,BakedModel model,CallbackInfo ci){
   RgbItemRenderContext.set(RgbRules.accepts(stack)?RgbRules.rgb(stack):null);
 }
 @Inject(method=METHOD,at=@At("RETURN"),require=0)
 private void chromamix$end(ItemStack stack,ItemDisplayContext mode,boolean left,PoseStack pose,MultiBufferSource source,int light,int overlay,BakedModel model,CallbackInfo ci){
   RgbItemRenderContext.clear();
 }
 @ModifyVariable(method=METHOD,at=@At("HEAD"),argsOnly=true,ordinal=0,require=0)
 private MultiBufferSource chromamix$buffer(MultiBufferSource source,ItemStack stack,ItemDisplayContext mode,boolean left,PoseStack pose,MultiBufferSource originalSource,int light,int overlay,BakedModel model){
   if(!RgbRules.accepts(stack))return source;
   Integer rgb=RgbRules.rgb(stack);
   if(rgb==null || "minecraft:white_bed".equals(RgbRules.id(stack)))return source;
   return new RgbTintedBuffer(source,rgb);
 }
}
