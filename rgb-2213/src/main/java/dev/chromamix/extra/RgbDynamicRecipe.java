package dev.chromamix.extra;

import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/** Builds an independent recipe for each basin, carrying its exact pigment RGB. */
public final class RgbDynamicRecipe {
  private RgbDynamicRecipe() {}

  public static Recipe<?> fromBasin(BasinBlockEntity basin) {
    if (basin.getLevel() == null || basin.getLevel().isClientSide) return null;
    IItemHandler handler = basin.getLevel().getCapability(
        Capabilities.ItemHandler.BLOCK, basin.getBlockPos(), null);
    if (handler == null) return null;

    ItemStack pigment = ItemStack.EMPTY;
    ItemStack white = ItemStack.EMPTY;
    int pigmentKinds = 0, whiteKinds = 0;
    for (int i = 0; i < handler.getSlots(); i++) {
      ItemStack input = handler.getStackInSlot(i);
      if (input.isEmpty()) continue;
      if (RgbRules.pigment(input)) { pigment = input; pigmentKinds++; }
      else if (RgbRules.accepts(input)) { white = input; whiteKinds++; }
    }
    if (pigmentKinds != 1 || whiteKinds != 1) return null;
    Integer color = RgbRules.rgb(pigment);
    if (color == null) return null;

    ItemStack output = RgbRules.withRgb(white, color);
    if (!output.has(DataComponents.CUSTOM_NAME)) {
      String shade = dev.chromamix.world.ColorNames.colorDescription(color);
      output.set(DataComponents.CUSTOM_NAME,
          Component.literal(shade + " | " + white.getHoverName().getString()));
    }
    ResourceLocation inputId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(white.getItem());
    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
        "chromamix", "create_universal_rgb_" + inputId.getNamespace() + "_" + inputId.getPath());
    MixingRecipe recipe = new StandardProcessingRecipe.Builder<>(MixingRecipe::new, id)
        .require(white.getItem())
        .require(pigment.getItem())
        .output(output)
        .build();
    return BasinRecipe.match(basin, recipe) ? recipe : null;
  }
}
