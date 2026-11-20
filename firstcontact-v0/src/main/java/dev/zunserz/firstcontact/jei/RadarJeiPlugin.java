package dev.zunserz.firstcontact.jei;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.zunserz.firstcontact.FirstContact;
import dev.zunserz.firstcontact.RadarBlueprint;
import dev.zunserz.firstcontact.RadarIntegration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/**
 * Create: Radars assembly example, not a counterfeit Create radar.
 * The schematic uses real radar-bearing, receiver, dish and plate blocks.
 * Interactive JEI controls: drag to orbit, scroll zoom, click layer/auto buttons.
 */
@JeiPlugin
public final class RadarJeiPlugin implements IModPlugin {
    public record RadarSchematic() {}
    private static final RecipeType<RadarSchematic> TYPE =
        RecipeType.create(FirstContact.ID, "radars_dish_3d", RadarSchematic.class);
    private RadarCategory category;
    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(FirstContact.ID, "jei_real_radar");
    }
    @Override public void registerCategories(IRecipeCategoryRegistration reg) {
        category = new RadarCategory(reg.getJeiHelpers().getGuiHelper());
        reg.addRecipeCategories(category);
    }
    @Override public void registerRecipes(IRecipeRegistration reg) {
        reg.addRecipes(TYPE, List.of(new RadarSchematic()));
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration reg) {
        reg.addRecipeCatalyst(new ItemStack(FirstContact.RADIO_ITEM.get()), TYPE);
        if (BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(RadarIntegration.BEARING)))
            reg.addRecipeCatalyst(item(RadarIntegration.BEARING), TYPE);
    }

    // Actual build plan shared by JEI, server-side checklist and material counts.
    private static final List<RadarBlueprint.Part> MODEL = RadarBlueprint.parts();

    private static final class RadarCategory implements IRecipeCategory<RadarSchematic> {
        private final IDrawable icon;
        private float yaw=30,pitch=24,zoom=1.0f;
        private int layer=-1;
        private boolean autorotate=true;
        private final Map<String,Integer> count = RadarBlueprint.materials();
        RadarCategory(IGuiHelper helper) {
            icon=helper.createDrawableItemStack(item(RadarIntegration.BEARING));

        }
        @Override public RecipeType<RadarSchematic> getRecipeType(){return TYPE;}
        @Override public ResourceLocation getRegistryName(RadarSchematic recipe) {
            return ResourceLocation.fromNamespaceAndPath(FirstContact.ID, "radar_multiblock_blueprint");
        }
        @Override public Component getTitle(){return Component.literal("Реальный радар Create: Radars (3D)");}
        @Override public IDrawable getIcon(){return icon;}
        @Override public int getWidth(){return 220;}
        @Override public int getHeight(){return 238;}
        @Override public boolean needsRecipeBorder(){return false;}

        @Override public void setRecipe(IRecipeLayoutBuilder builder,RadarSchematic schematic,
                                        IFocusGroup group) {
            builder.addSlot(RecipeIngredientRole.INPUT, 12, 208).addItemStack(item(RadarIntegration.BEARING));
            builder.addSlot(RecipeIngredientRole.INPUT, 54, 208).addItemStack(item(RadarIntegration.RECEIVER));
            builder.addSlot(RecipeIngredientRole.INPUT, 96, 208).addItemStack(item(RadarIntegration.PLATE));
            builder.addSlot(RecipeIngredientRole.INPUT, 138, 208).addItemStack(item(RadarIntegration.DISH));
            builder.addSlot(RecipeIngredientRole.INPUT, 180, 208).addItemStack(item("create:brass_casing"));
        }

        @Override public void createRecipeExtras(IRecipeExtrasBuilder extras,
                                                 RadarSchematic recipe,IFocusGroup group) {
            extras.addInputHandler(new IJeiInputHandler() {
                @Override public ScreenRectangle getArea(){return new ScreenRectangle(0,0,220,194);}
                @Override public boolean handleInput(double mx,double my,IJeiUserInput input) {
                    if(input.getKey().getType()!=InputConstants.Type.MOUSE
                      ||input.getKey().getValue()!=0 ||my<164 || my>184) return false;
                    if(!input.isSimulate()) {
                        if(mx<56) {layer=layer>=RadarBlueprint.maxHeight()?-1:layer+1;}
                        else if(mx<110) {layer=-1;}
                        else if(mx<170) {autorotate=!autorotate;}
                        else {yaw+=45;}
                    }
                    return true;
                }
                @Override public boolean handleMouseScrolled(double mx,double my,double dx,double dy) {
                    zoom=Math.clamp(zoom+(float)dy*.13f,.45f,1.95f);
                    return true;
                }
                @Override public boolean handleMouseDragged(double mx,double my,
                      InputConstants.Key mouseKey,double dx,double dy) {
                    if(mouseKey.getType()!=InputConstants.Type.MOUSE
                      ||mouseKey.getValue()!=0 ||my>159) return false;
                    yaw+=(float)dx*1.4f;
                    pitch=Math.clamp(pitch+(float)dy*.9f,-60f,65f);
                    autorotate=false;
                    return true;
                }
            });
        }

        @Override public void draw(RadarSchematic schematic,
                     mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                     GuiGraphics g,double mouseX,double mouseY) {
            g.fill(0,0,220,162,0xFF172226);
            g.renderOutline(0,0,220,162,0xFFB69A5E);
            g.drawString(Minecraft.getInstance().font,"CREATE: RADARS  /  3D  Ø13",7,6,0xFFEDCEA0,false);
            String desc="Схема: " + MODEL.size() + " блоков / проверяется сервером";
            g.drawString(Minecraft.getInstance().font,
                Minecraft.getInstance().font.plainSubstrByWidth(desc,207),
                7,17,0xFF9BBDB0,false);
            if(!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(RadarIntegration.BEARING))) {
                drawButtons(g);
                g.drawCenteredString(Minecraft.getInstance().font,
                   "Установите Create: Radars",110,74,0xFFFF8C75);
                return;
            }
            PoseStack pose=g.pose();
            pose.pushPose();
            pose.translate(110,111,270);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(
                yaw+(autorotate?(float)((System.currentTimeMillis()/75L)%360):0)));
            pose.scale(7.3f*zoom,-7.3f*zoom,7.3f*zoom);
            pose.translate(-.5,-3.0,-.5);
            var mc=Minecraft.getInstance();
            var buffers=mc.renderBuffers().bufferSource();
            for(RadarBlueprint.Part part:MODEL) {
                if(layer>=0 && part.y()>layer)continue;
                ResourceLocation id=ResourceLocation.parse(part.block());
                if(!BuiltInRegistries.BLOCK.containsKey(id))continue;
                Block block=BuiltInRegistries.BLOCK.get(id);
                BlockState state=block.defaultBlockState();
                pose.pushPose();
                pose.translate(part.x(),part.y(),part.z());
                mc.getBlockRenderer().renderSingleBlock(state,pose,buffers,0xF000F0,
                    OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            buffers.endBatch();
            pose.popPose();
            // Draw UI on top of the 3D model, not behind its depth buffer.
            drawButtons(g);
            g.drawString(mc.font,"ЛКМ+двигать: вращение   •   колесо: масштаб",5,188,0xFF9DA8A1,false);
            g.drawString(mc.font,"МАТЕРИАЛЫ (количество под иконкой)",5,198,0xFFD1B47C,false);
            String[] materials={
                RadarIntegration.BEARING,RadarIntegration.RECEIVER,
                RadarIntegration.PLATE,RadarIntegration.DISH,"create:brass_casing"
            };
            int[] positions={20,62,104,146,188};
            for(int i=0;i<materials.length;i++)
                g.drawCenteredString(mc.font,"x"+count.getOrDefault(materials[i],0),
                    positions[i],228,0xFFF1D59F);
        }
        private void drawButtons(GuiGraphics g){
            String[] captions={"СЛОЙ "+(layer<0?"ВСЕ":layer),"ВСЕ СЛОИ","АВТО "+(autorotate?"ON":"OFF"),"↻ 45°"};
            int[] starts={4,57,111,171};int[] ends={55,109,169,215};
            for(int i=0;i<captions.length;i++){
                g.fill(starts[i],164,ends[i],184,0xFF493529);
                g.renderOutline(starts[i],164,ends[i]-starts[i],20,0xFFD0A769);
                g.drawCenteredString(Minecraft.getInstance().font,captions[i],
                      (starts[i]+ends[i])/2,170,0xFFE9D6A7);
            }
            // Hint is drawn after the 3D mesh, see draw().

        }
        @Override public void getTooltip(ITooltipBuilder tooltip,RadarSchematic schematic,
                     mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                     double mx,double my) {
            if(my>158)return;
            tooltip.add(Component.literal("Точная схема: /firstcontact radar check"));
            tooltip.add(Component.literal("Radar Bearing: "+count.getOrDefault(RadarIntegration.BEARING,0)
                     +" / Receiver: "+count.getOrDefault(RadarIntegration.RECEIVER,0)));
            tooltip.add(Component.literal("Dish: "+count.getOrDefault(RadarIntegration.DISH,0)
                     +" / Plate: "+count.getOrDefault(RadarIntegration.PLATE,0)));
            tooltip.add(Component.literal("Полная схема: " + MODEL.size() + " блоков / высота 6"));
            tooltip.add(Component.literal("Нижний слой: 0. Кнопка СЛОЙ показывает до указанной высоты."));
            tooltip.add(Component.literal("Соедини всю тарелку Super Glue из Create."));
            tooltip.add(Component.literal("Отдельный чеклист по слоям: ЛКМ на СЛОЙ."));
            tooltip.add(Component.literal("Привяжи: /firstcontact radar link"));
        }
    }
    private static ItemStack item(String id) {
        ResourceLocation key=ResourceLocation.parse(id);
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }
}
