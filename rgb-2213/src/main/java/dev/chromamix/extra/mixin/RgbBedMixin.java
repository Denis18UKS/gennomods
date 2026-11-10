package dev.chromamix.extra.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.chromamix.extra.client.RgbBedTextures;
import dev.chromamix.extra.client.RgbClientCache;
import dev.chromamix.extra.client.RgbItemRenderContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BedRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tint only blanket pixels; keep the pillow and wooden bed frame unchanged. */
@Mixin(BedRenderer.class)
public abstract class RgbBedMixin {
 @Unique private Integer chromamix$rgb;
 @Inject(method="render(Lnet/minecraft/world/level/block/entity/BedBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",at=@At("HEAD"),require=0)
 private void chromamix$begin(BedBlockEntity bed,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay,CallbackInfo ci){
   chromamix$rgb=bed.getLevel()==null?RgbItemRenderContext.get():RgbClientCache.get(bed.getBlockPos());
 }
 @Inject(method="render(Lnet/minecraft/world/level/block/entity/BedBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",at=@At("RETURN"),require=0)
 private void chromamix$end(BedBlockEntity bed,float partial,PoseStack pose,MultiBufferSource buffer,int light,int overlay,CallbackInfo ci){
   chromamix$rgb=null;
 }
 @Redirect(method="renderPiece",require=0,
   at=@At(value="INVOKE",target="Lnet/minecraft/client/resources/model/Material;buffer(Lnet/minecraft/client/renderer/MultiBufferSource;Ljava/util/function/Function;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
 private VertexConsumer chromamix$bedMaterial(Material original,MultiBufferSource source,Function<ResourceLocation,RenderType> factory){
    if(chromamix$rgb!=null){
      ResourceLocation dynamic=RgbBedTextures.get(chromamix$rgb);
      if(dynamic!=null)return source.getBuffer(RenderType.entitySolid(dynamic));
    }
    return original.buffer(source,factory);
 }
}
