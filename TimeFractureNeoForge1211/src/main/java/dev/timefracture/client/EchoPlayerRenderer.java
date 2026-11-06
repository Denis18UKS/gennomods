package dev.timefracture.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.timefracture.EchoPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * A genuine humanoid PLAYER MODEL. The skin is retrieved from the saved
 * GameProfile texture property, NOT from the currently online player,
 * so later skin changes do not propagate to temporal echoes.
 */
public final class EchoPlayerRenderer extends HumanoidMobRenderer<EchoPlayerEntity,PlayerModel<EchoPlayerEntity>> {
    private final Map<String,ResourceLocation> skinCache=new HashMap<>();
    private final Set<String> loading=new HashSet<>();

    public EchoPlayerRenderer(EntityRendererProvider.Context ctx){
        super(ctx,new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER),false),.48f);
        addLayer(new HumanoidArmorLayer<>(this,
            new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            ctx.getModelManager()));
    }
    @Override public ResourceLocation getTextureLocation(EchoPlayerEntity echo){
        String id=echo.subjectId(),name=echo.subjectName();
        UUID uuid;
        try{uuid=UUID.fromString(id);}catch(Exception ex){uuid=echo.getUUID();}
        ResourceLocation fallback=DefaultPlayerSkin.getDefaultSkin(uuid).texture();
        String value=echo.frozenSkinValue();
        if(value.isBlank())return fallback;
        String key=value+"#"+echo.frozenSkinSignature();
        ResourceLocation found=skinCache.get(key);
        if(found!=null)return found;
        if(loading.add(key)){
            GameProfile frozen=new GameProfile(uuid,name.isBlank()?"Echo":name);
            String sig=echo.frozenSkinSignature();
            frozen.getProperties().put("textures",
                sig.isBlank()?new Property("textures",value):new Property("textures",value,sig));
            // Asynchronous texture download; NEVER block the render thread.
            Minecraft.getInstance().getSkinManager().getOrLoad(frozen)
                .thenAccept(skin->Minecraft.getInstance().execute(()->skinCache.put(key,skin.texture())));
        }
        return fallback;
    }
}
