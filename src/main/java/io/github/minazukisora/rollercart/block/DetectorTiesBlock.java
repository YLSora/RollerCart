package io.github.minazukisora.rollercart.block;

import io.github.minazukisora.rollercart.RollerCart;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class DetectorTiesBlock extends TrackTiesBlock {

    public static final BooleanProperty POWERED = BooleanProperty.of("powered");

    public DetectorTiesBlock(AbstractBlock.Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(POWERED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(POWERED);
    }

    @Override
    public boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        if (!state.get(POWERED)) return 0;
        if (world.getBlockEntity(pos) instanceof DetectorTiesBlockEntity be) {
            return Math.max(be.getRedstoneSignal(), 1);
        }
        return 1;
    }

    @Override
    public int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return getWeakRedstonePower(state, world, pos, direction);
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        if (!state.get(POWERED)) return 0;
        if (world.getBlockEntity(pos) instanceof DetectorTiesBlockEntity be) {
            return Math.max(be.getRedstoneSignal(), 1);
        }
        return 1;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DetectorTiesBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (type == RollerCart.DETECTOR_TIES_BE.get()) {
            return DetectorTiesBlockEntity::staticTick;
        }
        return null;
    }
}
