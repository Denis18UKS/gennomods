package dev.zunserz.firstcontact.jei;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.zunserz.firstcontact.FirstContact;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
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

/** Live 3D preview in JEI, built from the exact same voxel blueprint as server validation. */
@JeiPlugin
public final class RadarJeiPlugin implements IModPlugin {
    public record RadarSchematic() {}
    private static final RecipeType<RadarSchematic> TYPE =
        RecipeType.create(FirstContact.ID, "radar_schematic", RadarSchematic.class);
    private RadarCategory category;
    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(FirstContact.ID, "jei_radar");
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
    }

    private static final class RadarCategory implements IRecipeCategory<RadarSchematic> {
        private final IDrawable icon;
        RadarCategory(IGuiHelper helper) {
            icon = helper.createDrawableItemStack(item("create:mechanical_bearing"));
        }
        @Override public RecipeType<RadarSchematic> getRecipeType() { return TYPE; }
        @Override public Component getTitle() { return Component.literal("Радиолокационная тарелка Create"); }
        @Override public IDrawable getIcon() { return icon; }
        @Override public int getWidth() { return 180; }
        @Override public int getHeight() { return 166; }

        @Override public void setRecipe(IRecipeLayoutBuilder b, RadarSchematic schematic,
                                        IFocusGroup focus) {
            b.addSlot(RecipeIngredientRole.INPUT, 10, 140).addItemStack(item("create:mechanical_bearing"));
            b.addSlot(RecipeIngredientRole.INPUT, 39, 140).addItemStack(item("create:brass_casing"));
            b.addSlot(RecipeIngredientRole.INPUT, 68, 140).addItemStack(item("minecraft:cut_copper"));
            b.addSlot(RecipeIngredientRole.INPUT, 97, 140).addItemStack(item("minecraft:lightning_rod"));
        }

        @Override public void draw(RadarSchematic schematic,
                                   mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                                   GuiGraphics g, double mouseX, double mouseY) {
            g.fill(0, 0, 180, 136, 0xFF172128);
            g.renderOutline(0, 0, 180, 136, 0xFFC5A35B);
            g.drawString(Minecraft.getInstance().font, "3D / CREATE RADAR / Ø13", 7, 6, 0xFFFFD28D, false);
            g.drawString(Minecraft.getInstance().font, "Автовращение • масштаб: 5", 7, 118, 0xFFB9C2BE, false);
            g.drawString(Minecraft.getInstance().font, "Крепить к Mechanical Bearing", 7, 128, 0xFFB9C2BE, false);

            // Render actual baked block models, not flat item icons or a 2D drawing.
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(90, 95, 240);
            pose.mulPose(Axis.XP.rotationDegrees(27));
            pose.mulPose(Axis.YP.rotationDegrees(
                (float)((System.currentTimeMillis() / 90L) % 360L)));
            pose.scale(4.4f, -4.4f, 4.4f);
            pose.translate(-0.5, -2.2, -0.5);
            var mc = Minecraft.getInstance();
            var buffers = mc.renderBuffers().bufferSource();
            for (FirstContact.Blueprint.Part part : FirstContact.Blueprint.parts()) {
                ResourceLocation id = ResourceLocation.tryParse(part.id());
                if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) continue;
                Block block = BuiltInRegistries.BLOCK.get(id);
                BlockState state = block.defaultBlockState();
                pose.pushPose();
                pose.translate(part.x(), part.y(), part.z());
                mc.getBlockRenderer().renderSingleBlock(state, pose, buffers, 0xF000F0,
                        OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            buffers.endBatch();
            pose.popPose();
        }

        @Override public List<Component> getTooltipStrings(RadarSchematic schematic,
                                                             mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,
                                                             double mouseX, double mouseY) {
            if (mouseY > 116) return List.of();
            Map<String, Integer> needed = new HashMap<>();
            for (FirstContact.Blueprint.Part p : FirstContact.Blueprint.parts())
                needed.merge(p.id(), 1, Integer::sum);
            return List.of(
                Component.literal("Радар Create • 3D-схема"),
                Component.literal("Построй вручную по слоям; центр — Mechanical Bearing."),
                Component.literal("Латунь: " + needed.getOrDefault("create:brass_casing",0)
                    + " • Медь: " + needed.getOrDefault("minecraft:cut_copper",0)),
                Component.literal("Затем: /firstcontact radar calibrate"),
                Component.literal("Подавай вращение на Mechanical Bearing."));
        }

        private static ItemStack item(String id) {
            ResourceLocation key = ResourceLocation.parse(id);
            return new ItemStack(BuiltInRegistries.ITEM.get(key));
        }
    }
}
