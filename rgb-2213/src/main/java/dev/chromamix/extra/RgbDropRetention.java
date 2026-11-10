package dev.chromamix.extra;

import dev.chromamix.world.ColorNames;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Keeps exact ChromaMix RGB when blocks drop items. Does not modify recipes or painting. */
@EventBusSubscriber(modid = "chromamix")
public final class RgbDropRetention {
    private RgbDropRetention() {}
    private static final Map<ServerLevel, Set<BlockPos>> PENDING = new WeakHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        BlockState state = event.getState();
        if (!eligible(state)) return;
        Integer rgb = color(level, state, event.getPos());
        if (rgb == null) return;

        for (ItemEntity entity : event.getDrops()) {
            ItemStack original = entity.getItem();
            if (original.isEmpty() || !RgbRules.accepts(original)) continue;
            // Copy all existing item components, including container contents.
            ItemStack colored = RgbRules.withRgb(original, rgb);
            colored.setCount(original.getCount());
            if (!colored.has(DataComponents.CUSTOM_NAME)) {
                colored.set(DataComponents.CUSTOM_NAME, name(original, rgb));
            }
            entity.setItem(colored);
        }
        scheduleCleanup(level, state, event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockState state = event.getState();
        if (state.getBlock() instanceof BedBlock && color(level, state, event.getPos()) != null) {
            scheduleCleanup(level, state, event.getPos());
        }
    }

    @SubscribeEvent
    public static void afterTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Set<BlockPos> positions = PENDING.remove(level);
        if (positions == null) return;
        for (BlockPos pos : positions) {
            if (!eligible(level.getBlockState(pos))) RgbSaved.of(level).set(level, pos, null);
        }
    }

    private static boolean eligible(BlockState state) {
        return RgbRules.accepts(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    private static Integer color(ServerLevel level, BlockState state, BlockPos pos) {
        Integer rgb = RgbSaved.of(level).get(pos);
        if (rgb != null) return rgb;
        BlockPos mate = otherHalf(state, pos);
        return mate == null ? null : RgbSaved.of(level).get(mate);
    }

    private static BlockPos otherHalf(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof BedBlock)) return null;
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                || !state.hasProperty(BlockStateProperties.BED_PART)) return null;
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BedPart half = state.getValue(BlockStateProperties.BED_PART);
        return pos.relative(half == BedPart.FOOT ? facing : facing.getOpposite());
    }

    private static void scheduleCleanup(ServerLevel level, BlockState state, BlockPos pos) {
        Set<BlockPos> positions = PENDING.computeIfAbsent(level, unused -> new HashSet<>());
        positions.add(pos.immutable());
        BlockPos mate = otherHalf(state, pos);
        if (mate != null) positions.add(mate.immutable());
    }

    private static Component name(ItemStack item, int rgb) {
        String base = item.getHoverName().getString().trim()
                .replaceFirst("(?iu)^(?:белая|белое|белые|белый|white)\\s+", "");
        String shade = ColorNames.colorDescription(rgb);
        String id = RgbRules.id(item);
        boolean feminine = id.endsWith("_bed") || id.endsWith("_candle")
                || id.endsWith("_wool") || id.endsWith("_glass_pane")
                || id.endsWith("_terracotta")
                || base.toLowerCase(Locale.ROOT).contains("кровать")
                || base.toLowerCase(Locale.ROOT).contains("шерсть");
        if (feminine) {
            if (shade.endsWith("ый") || shade.endsWith("ой")) {
                shade = shade.substring(0, shade.length() - 2) + "ая";
            } else if (shade.endsWith("ий")) {
                shade = shade.substring(0, shade.length() - 2) + "яя";
            }
        }
        return Component.literal(shade + " " + base).withStyle(ChatFormatting.WHITE);
    }
}
