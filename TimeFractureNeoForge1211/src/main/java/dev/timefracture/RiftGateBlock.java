package dev.timefracture;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Small reachable physical portal for entering another saved layer.
 * The huge sky shader remains entirely in TimeFractureFX.
 */
public final class RiftGateBlock extends Block {
    public RiftGateBlock(Properties properties){super(properties);}

    @Override
    protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){
        if(level.isClientSide)return;
        if(entity instanceof ServerPlayer player && TimeFractureMod.GATES!=null)
            TimeFractureMod.GATES.contact(player,pos);
    }
}
