package dev.timefracture.create;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;

import dev.timefracture.TimeFractureMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Chronal Convergence Core: a genuine Create kinetic consumer connected via
 * a vertical shaft from BELOW. It cannot run from chat commands or fake RPM.
 */
public class ChronalCoreBlock extends KineticBlock implements IBE<ChronalCoreBlockEntity> {
    public ChronalCoreBlock(Properties properties) { super(properties); }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos,
                                   BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public Class<ChronalCoreBlockEntity> getBlockEntityClass() {
        return ChronalCoreBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ChronalCoreBlockEntity> getBlockEntityType() {
        return TimeFractureMod.CHRONAL_CORE_BE.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
                                              Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.NETHER_STAR)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ChronalCoreBlockEntity be) {
            if (be.arm(player)) {
                if (!player.isCreative()) stack.shrink(1);
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ChronalCoreBlockEntity be)
            player.sendSystemMessage(Component.literal(be.status()));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
