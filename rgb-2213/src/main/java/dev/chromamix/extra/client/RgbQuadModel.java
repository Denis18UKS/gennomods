package dev.chromamix.extra.client;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Recolors white textile quads only, leaving wood and metallic geometry unchanged. */
public final class RgbQuadModel extends BakedModelWrapper<BakedModel> {
 private static final ModelProperty<Integer> RGB=new ModelProperty<>();
 private final Map<BakedQuad,Map<Integer,BakedQuad>> cache=new IdentityHashMap<>();
 public RgbQuadModel(BakedModel model){super(model);}
 @Override public ModelData getModelData(BlockAndTintGetter world,BlockPos pos,BlockState state,ModelData input){
   ModelData parent=originalModel.getModelData(world,pos,state,input);
   Integer color=RgbClientCache.get(pos);
   return color==null?parent:parent.derive().with(RGB,color).build();
 }
 @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType renderType){
   List<BakedQuad> original=originalModel.getQuads(state,side,random,data,renderType);
   Integer rgb=data.get(RGB);
   if(rgb==null||original.isEmpty())return original;
   ArrayList<BakedQuad> out=new ArrayList<>(original.size());
   for(BakedQuad quad:original)out.add(isPaintableSurface(quad)?tint(quad,rgb):quad);
   return out;
 }
 private static boolean isPaintableSurface(BakedQuad quad){
   if(quad.getSprite()==null)return false;
   String path=quad.getSprite().contents().name().getPath();
   if(path.contains("wood")||path.contains("plank")||path.contains("metal")||path.contains("copper")||path.contains("brass")||path.contains("iron")||path.contains("shaft")||path.contains("frame")||path.contains("base"))return false;
   return path.contains("white")||path.contains("wool")||path.contains("fabric")||path.contains("cloth")||path.contains("seat")||path.contains("sail")||path.contains("canvas");
 }
 private BakedQuad tint(BakedQuad q,int rgb){
   synchronized(cache){
     Map<Integer,BakedQuad> per=cache.get(q);
     if(per!=null){BakedQuad cached=per.get(rgb);if(cached!=null)return cached;}
     if(cache.size()>4096)cache.clear();
     if(per==null){per=new HashMap<>();cache.put(q,per);}
     int[] data=q.getVertices().clone();
     int step=data.length/4;
     if(step>=8){int r=rgb>>>16&255,g=rgb>>>8&255,b=rgb&255;
       for(int i=0;i<4;i++){
        int index=i*step+3,pixel=data[index];
        int a=pixel&0xFF000000,pr=(pixel&255)*r/255,pg=((pixel>>>8)&255)*g/255,pb=((pixel>>>16)&255)*b/255;
        data[index]=a|pr|(pg<<8)|(pb<<16);
       }
     }
     BakedQuad result=new BakedQuad(data,q.getTintIndex(),q.getDirection(),q.getSprite(),q.isShade(),q.hasAmbientOcclusion());
     if(per.size()<128)per.put(rgb,result);
     return result;
   }
 }
}
