package dev.chromamix.extra.mixin;
import dev.chromamix.extra.client.RgbClientCache;
import dev.chromamix.extra.client.RgbTintedBuffer;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BannerRenderer.class)
public abstract class RgbBannerMixin {
 @ModifyVariable(method="render(Lnet/minecraft/world/level/block/entity/BannerBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",at=@At("HEAD"),ordinal=0,argsOnly=true,require=0)
 private MultiBufferSource chromamix$bannerTint(MultiBufferSource source, BannerBlockEntity banner,float partial,PoseStack pose,MultiBufferSource unused,int light,int overlay){
   Integer rgb=RgbClientCache.get(banner.getBlockPos());
   return rgb==null?source:new RgbTintedBuffer(source,rgb);
 }
}
