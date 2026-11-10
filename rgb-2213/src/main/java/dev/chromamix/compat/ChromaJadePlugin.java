package dev.chromamix.compat;

import dev.chromamix.extra.RgbRules;
import dev.chromamix.extra.client.RgbClientCache;
import dev.chromamix.world.ChromaticBlockEntity;
import dev.chromamix.world.ColorNames;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

/** Jade client-only presentation. Does not modify block state, recipes or color storage. */
@WailaPlugin
public final class ChromaJadePlugin implements IWailaPlugin {
    @Override public void register(IWailaCommonRegistration registry) {}

    @Override public void registerClient(IWailaClientRegistration registry) {
        // In v2.2.15 this was ChromaticBlock.class, so it ignored white-base vanilla/Create blocks.
        // The provider itself filters by whether a block actually carries a ChromaMix RGB value.
        registry.registerBlockComponent(ColorProvider.INSTANCE, Block.class);
        registry.registerBlockIcon(ColorProvider.INSTANCE, Block.class);
    }

    public enum ColorProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override public ResourceLocation getUid() {
            // Preserve the old id, so Jade's existing user config continues to work.
            return ResourceLocation.fromNamespaceAndPath("chromamix", "rgb_jade_color");
        }

        @Override public int getDefaultPriority() { return 2000; }

        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            Integer rgb = colorAt(accessor);
            if (rgb == null) return;

            String name = nameOf(accessor.getBlock(), rgb);
            // Force white explicitly rather than inheriting the gray formatting of some modded names.
            tooltip.replace(JadeIds.CORE_OBJECT_NAME,
                Component.literal(name).withStyle(ChatFormatting.WHITE));
        }

        @Override public IElement getIcon(BlockAccessor accessor, IPluginConfig config, IElement currentIcon) {
            Integer rgb = colorAt(accessor);
            if (rgb == null) return currentIcon;
            ItemStack base = new ItemStack(accessor.getBlock().asItem());
            if (base.isEmpty()) return currentIcon;

            // The existing ChromaMix item renderer reads this exact RGB custom data for
            // wool, beds, shulker boxes and other supported item models.
            ItemStack colored = RgbRules.withRgb(base, rgb);
            return IElementHelper.get().item(colored);
        }

        private static Integer colorAt(BlockAccessor accessor) {
            if (accessor == null) return null;
            BlockEntity entity = accessor.getBlockEntity();
            if (entity instanceof ChromaticBlockEntity rgbBlock) {
                return rgbBlock.rgb() & 0xFFFFFF;
            }
            BlockPos pos = accessor.getPosition();
            return pos == null ? null : RgbClientCache.get(pos);
        }

        public static String nameOf(Block block, int rgb) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            String path = id.getPath();

            if (id.getNamespace().equals("chromamix") && path.startsWith("rgb_")) {
                return ColorNames.blockName(rgb, block);
            }
            String original = block.getName().getString();
            String type = stripWhitePrefix(original);
            String shade = ColorNames.colorDescription(rgb);
            shade = adjustForType(shade, path, type);
            return shade + " " + type;
        }

        private static String stripWhitePrefix(String original) {
            String trimmed = original.trim();
            String noWhite = trimmed.replaceFirst("(?iu)^(?:белая|белый|белое|белые|white)\\s+", "");
            if (noWhite.equals(trimmed)) {
                // Don't append a second "white" in languages where the base block is
                // formatted with a non-leading name such as "Shulker Box (White)".
                noWhite = noWhite.replaceFirst("(?iu)\\s*\\(white\\)$", "")
                                 .replaceFirst("(?iu)\\s*\\(белый\\)$", "");
            }
            return noWhite;
        }

        private static String adjustForType(String shade, String registryPath, String type) {
            String simple = type.toLowerCase(Locale.ROOT);
            boolean feminine = registryPath.contains("bed") ||
                    registryPath.contains("carpet") ||
                    registryPath.contains("candle") ||
                    registryPath.contains("terracotta") ||
                    registryPath.contains("wool") ||
                    registryPath.contains("table_cloth") ||
                    registryPath.endsWith("glass_pane") ||
                    simple.contains("кровать") ||
                    simple.contains("шерсть") ||
                    simple.contains("скатерт") ||
                    simple.contains("панель") ||
                    simple.contains("свеч") ||
                    simple.contains("керамик");
            boolean neuter = registryPath.contains("seat") ||
                    registryPath.contains("glass") ||
                    registryPath.contains("banner") ||
                    simple.contains("сидень") ||
                    simple.contains("стекл") ||
                    simple.contains("знам");
            if (feminine) return adjectiveEnding(shade, "ая", "яя");
            if (neuter) return adjectiveEnding(shade, "ое", "ее");
            return shade;
        }

        private static String adjectiveEnding(String word, String hard, String soft) {
            if (word.endsWith("ый") || word.endsWith("ой")) return word.substring(0, word.length()-2) + hard;
            if (word.endsWith("ий")) return word.substring(0, word.length()-2) + soft;
            return word;
        }
    }
}
