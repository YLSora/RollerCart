package io.github.minazukisora.rollercart.block;

import io.github.minazukisora.rollercart.RollerCart;
import io.github.minazukisora.rollercart.entity.TrackFollowerEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.List;

public class DetectorTiesBlockEntity extends TrackTiesBlockEntity {
    private static final int ACTIVE_TICKS = 20;
    private static final double MAX_CART_SPEED = 1.28;

    private int activeTicks = 0;
    private int redstoneSignal = 0;

    public DetectorTiesBlockEntity(BlockPos pos, BlockState state) {
        super(RollerCart.DETECTOR_TIES_BE.get(), pos, state);
    }

    @Override
    public void markHasCart() {
        if (world == null || world.isClient()) return;

        TrackFollowerEntity cart = findCart();
        if (cart != null) {
            markHasCart(cart);
        }
    }

    @Override
    public void markHasCart(TrackFollowerEntity cart) {
        if (world == null || world.isClient() || cart == null) return;
        if (!cart.getBlockPos().equals(pos)) return;
        if (!cart.getStartTie().equals(pos) && !cart.getEndTie().equals(pos)) return;

        this.activeTicks = ACTIVE_TICKS;
        double speed = Math.min(cart.getTrackVelocity(), MAX_CART_SPEED);
        int newSignal = 1 + MathHelper.floor(14.0 * (speed / MAX_CART_SPEED));
        setSignal(newSignal, world, pos, world.getBlockState(pos));
        markDirty();
    }

    private TrackFollowerEntity findCart() {
        if (world == null) return null;

        Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        List<TrackFollowerEntity> list = world.getEntitiesByClass(TrackFollowerEntity.class, box, e -> true);
        for (TrackFollowerEntity cart : list) {
            if (cart.getBlockPos().equals(pos) && (cart.getStartTie().equals(pos) || cart.getEndTie().equals(pos))) {
                return cart;
            }
        }
        return null;
    }

    private void setSignal(int newSignal, World world, BlockPos pos, BlockState state) {
        if (newSignal != this.redstoneSignal) {
            this.redstoneSignal = newSignal;
            markDirty();
            sync();
            world.updateNeighbors(pos, state.getBlock());
            world.updateComparators(pos, state.getBlock());
        }
        if (!state.get(DetectorTiesBlock.POWERED)) {
            world.setBlockState(pos, state.with(DetectorTiesBlock.POWERED, true), Block.NOTIFY_ALL);
        }
    }

    private void tick(World world, BlockPos pos, BlockState state) {
        if (world.isClient()) return;

        if (activeTicks > 0) {
            activeTicks--;
            if (activeTicks == 0) {
                this.redstoneSignal = 0;
                markDirty();
                sync();
                if (state.get(DetectorTiesBlock.POWERED)) {
                    world.setBlockState(pos, state.with(DetectorTiesBlock.POWERED, false), Block.NOTIFY_ALL);
                }
                world.updateNeighbors(pos, state.getBlock());
                world.updateComparators(pos, state.getBlock());
            }
        }
    }

    @Override
    public int power() {
        if (redstoneSignal > 0) return redstoneSignal;
        return getCachedState().get(DetectorTiesBlock.POWERED) ? 1 : 0;
    }

    @Override
    public void updatePower() {
        // Detector ties output their own redstone signal, not received power.
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.redstoneSignal = nbt.getInt("redstone_signal");
        this.activeTicks = nbt.getInt("active_ticks");
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("redstone_signal", this.redstoneSignal);
        nbt.putInt("active_ticks", this.activeTicks);
    }

    public static void staticTick(World world, BlockPos pos, BlockState state, BlockEntity be) {
        if (be instanceof DetectorTiesBlockEntity a) a.tick(world, pos, state);
    }

    public int getRedstoneSignal() {
        return redstoneSignal;
    }
}
