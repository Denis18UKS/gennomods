package dev.timefracture.compat.jei;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.timefracture.TimeFractureMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Optional JEI integration: a real rotating 3D world-block assembly (NOT a 2D
 * screenshot). Uses vanilla BlockRenderDispatcher for full block model quads.
 * Displays a cyclic layer-by-layer build sequence and RPM requirements.
 */
@JeiPlugin
public final class ChronalStructureJei implements IModPlugin {
    private static final RecipeType<StructureGuide> TYPE = new RecipeType<>(
        ResourceLocation.fromNamespaceAndPath("timefracture","chronal_stabilizer"),StructureGuide.class);
    private StructureCategory category;

    public record StructureGuide() {}

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("timefracture","jei_chronal_structure");
    }
    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        category=new StructureCategory(registration.getJeiHelpers().getGuiHelper());
        registration.addRecipeCategories(category);
    }
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(TYPE,List.of(new StructureGuide()));
    }
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(TimeFractureMod.CHRONAL_CORE.get()),TYPE);
    }

    private static final class StructureCategory implements IRecipeCategory<StructureGuide> {
        private final IDrawable background;
        private final IDrawable icon;

        StructureCategory(IGuiHelper gui){
            background=gui.createBlankDrawable(176,145);
            icon=gui.createDrawableItemStack(new ItemStack(TimeFractureMod.CHRONAL_CORE.get()));
        }
        @Override public RecipeType<StructureGuide> getRecipeType(){return TYPE;}
        @Override public Component getTitle(){return Component.literal("Хронологический стабилизатор · 3D");}
        @Override public IDrawable getBackground(){return background;}
        @Override public IDrawable getIcon(){return icon;}

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, StructureGuide recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT,4,4).addItemStack(new ItemStack(Items.NETHER_STAR));
            builder.addSlot(RecipeIngredientRole.OUTPUT,151,4).addItemStack(new ItemStack(TimeFractureMod.CHRONAL_CORE.get()));
        }

        private static BlockState createBlock(String id,BlockState fallback) {
            Block found=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
            return found==Blocks.AIR ? fallback : found.defaultBlockState();
        }

        private static void part(GuiGraphics graphics, BlockState state,double dx,double dy,double dz){
            var mc=Minecraft.getInstance();
            PoseStack pose=graphics.pose();
            pose.pushPose();
            pose.translate(dx,dy,dz);
            mc.getBlockRenderer().renderSingleBlock(
                state,pose,mc.renderBuffers().bufferSource(),
                LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }

        @Override
        public void draw(StructureGuide recipe,IRecipeSlotsView slots,
                         GuiGraphics graphics,double mouseX,double mouseY) {
            var mc=Minecraft.getInstance();
            long stamp=System.currentTimeMillis();
            int layer=(int)((stamp/3200L)%3L)+1;
            var pose=graphics.pose();
            pose.pushPose();
            pose.translate(88,104,180);
            pose.scale(20f,-20f,20f);
            pose.mulPose(Axis.XP.rotationDegrees(29f));
            pose.mulPose(Axis.YP.rotationDegrees((stamp%26000L)/26000f*360f+20f));

            // Y=-1: vertical Create shaft below the core.
            part(graphics,createBlock("create:shaft",Blocks.IRON_BLOCK.defaultBlockState()),0,-1.4,0);
            if(layer>=2){
                // Y=0: four brass casing neighbors + TimeFracture kinetic core.
                part(graphics,TimeFractureMod.CHRONAL_CORE.get().defaultBlockState(),0,0,0);
                BlockState brass=createBlock("create:brass_casing",Blocks.COPPER_BLOCK.defaultBlockState());
                part(graphics,brass,-1.15,0,0);
                part(graphics,brass,1.15,0,0);
                part(graphics,brass,0,0,-1.15);
                part(graphics,brass,0,0,1.15);
            }
            if(layer>=3){
                // Y=+1: amethyst block directly above the core.
                part(graphics,Blocks.AMETHYST_BLOCK.defaultBlockState(),0,1.4,0);
            }
            mc.renderBuffers().bufferSource().endBatch();
            pose.popPose();
            graphics.drawString(mc.font,"Слой "+layer+"/3 · медленное вращение",5,27,0xFFBFA4FF,false);
            graphics.drawString(mc.font,"1: вал  2: ядро и латунь  3: аметист",5,118,0xFFE6DFF7,false);
            graphics.drawString(mc.font,"Макс. обороты Create (обычно 256 RPM)",5,128,0xFFDFD5F6,false);
            graphics.drawString(mc.font,"Звезда Незера · зарядка 30 секунд",5,138,0xFFD7C8EF,false);
        }
    }
}
