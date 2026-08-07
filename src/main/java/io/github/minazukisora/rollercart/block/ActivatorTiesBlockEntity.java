package io.github.minazukisora.rollercart.block;

import io.github.minazukisora.rollercart.RollerCart;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class ActivatorTiesBlockEntity extends TrackTiesBlockEntity {

    public ActivatorTiesBlockEntity(BlockPos pos, BlockState state) {
        super(RollerCart.ACTIVATOR_TIES_BE.get(), pos, state);
    }
}
