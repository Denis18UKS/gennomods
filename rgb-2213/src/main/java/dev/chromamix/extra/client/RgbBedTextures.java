package dev.chromamix.extra.client;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Produces original pillow/frame + RGB blanket, with no bundled Minecraft texture. */
public final class RgbBedTextures {
 private static final ResourceLocation ORIGINAL=ResourceLocation.withDefaultNamespace("textures/entity/bed/white.png");
 private static final Map<Integer,ResourceLocation> CACHE=new LinkedHashMap<>(32,0.75f,true);
 private static final int LIMIT=128;
 private static Object lastResources;
 private RgbBedTextures(){}
 public static synchronized ResourceLocation get(int color){
  color &= 0xFFFFFF;
  Minecraft mc=Minecraft.getInstance();
  Object manager=mc.getResourceManager();
  if(lastResources!=manager){clear();lastResources=manager;}
  ResourceLocation hit=CACHE.get(color);
  if(hit!=null) return hit;
  ResourceLocation location=ResourceLocation.fromNamespaceAndPath("chromamix",String.format("dynamic/bed_rgb_%06x",color));
  try(InputStream input=mc.getResourceManager().getResourceOrThrow(ORIGINAL).open();
      NativeImage nativeImage=NativeImage.read(input)){
    if(nativeImage.getWidth()<32 || nativeImage.getHeight()<32)return null;
    tintFabricPixels(nativeImage,color);
    NativeImage texture=new NativeImage(nativeImage.getWidth(),nativeImage.getHeight(),true);
    texture.copyFrom(nativeImage);
    mc.getTextureManager().register(location,new DynamicTexture(texture));
    CACHE.put(color,location);
    if(CACHE.size()>LIMIT){
      Integer oldest=CACHE.keySet().iterator().next();
      ResourceLocation id=CACHE.remove(oldest);
      mc.getTextureManager().release(id);
    }
    return location;
  }catch(Exception ex){return null;}
 }
 private static void tintFabricPixels(NativeImage image,int color){
   int tr=color>>>16&255,tg=color>>>8&255,tb=color&255;
   for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++){
      int atlasX=x*64/image.getWidth(),atlasY=y*64/image.getHeight();
      if(atlasX>=29 || atlasY<15 || atlasY>=44)continue;
      int abgr=image.getPixelRGBA(x,y),a=abgr>>>24;
      int r=abgr&255,g=abgr>>>8&255,b=abgr>>>16&255;
      if(a==0 || Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))>18)continue;
      image.setPixelRGBA(x,y,(a<<24)|((b*tb/255)<<16)|((g*tg/255)<<8)|(r*tr/255));
   }
 }
 public static synchronized void clear(){
   Minecraft mc=Minecraft.getInstance();
   for(ResourceLocation id:CACHE.values())mc.getTextureManager().release(id);
   CACHE.clear();
 }
}
