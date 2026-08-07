package io.github.minazukisora.rollercart.block;

import io.github.minazukisora.rollercart.RollerCart;
import io.github.minazukisora.rollercart.entity.TrackFollowerEntity;
import io.github.minazukisora.rollercart.item.TrackItem;
import io.github.minazukisora.rollercart.util.Pose;
import io.github.minazukisora.rollercart.util.SUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

public class TrackTiesBlockEntity extends BlockEntity {
    private BlockPos next;
    private BlockPos prev;
    private Pose pose;
    private TrackItem.Type trackType;
    private int power = -1;
    private int hasCartTicks = 0;

    public TrackTiesBlockEntity(BlockPos pos, BlockState state) {
        this(RollerCart.TRACK_TIES_BE.get(), pos, state);
    }

    protected TrackTiesBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        updatePose(pos, state);
        this.trackType = TrackItem.Type.STANDARD;
    }

    public void updatePose(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof TrackTiesBlock ties) {
            this.pose = ties.getPose(state, pos);
        } else {
            this.pose = new Pose(new Vector3d(), new Matrix3d().identity());
        }
    }

    private void tick(World world, BlockPos pos, BlockState state) {
        if(hasCartTicks > 0) {
            hasCartTicks--;
            if(hasCartTicks == 0) world.updateComparators(pos, state.getBlock());
        }
    }

    @Override
    public void setCachedState(BlockState state) {
        super.setCachedState(state);
        updatePose(this.getPos(), this.getCachedState());
    }

    public static @Nullable TrackTiesBlockEntity of(World world, @Nullable BlockPos pos) {
        if (pos != null && world.getBlockEntity(pos) instanceof TrackTiesBlockEntity e) {
            return e;
        }

        return null;
    }

    private void dropTrack(TrackItem.Type trackType) {
        var world = getWorld();
        var pos = Vec3d.ofCenter(getPos());
        var item = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(switch(trackType) {
            case STANDARD -> RollerCart.TRACK.get();
            case CHAIN -> RollerCart.CHAIN_TRACK.get();
            case STATION -> RollerCart.STATION_TRACK.get();
            case BRAKE -> RollerCart.BRAKE_TRACK.get();
            case MAGNETIC -> RollerCart.MAGNETIC_TRACK.get();
        }));

        world.spawnEntity(item);
    }

    public void setNext(@Nullable BlockPos pos, @Nullable TrackItem.Type trackType) {
        if (this.next != null && (pos == null || !this.next.equals(pos))) {
            var oldNextE = next();
            if (oldNextE != null) {
                oldNextE.prev = null;
                oldNextE.sync();
                oldNextE.markDirty();
            }
            this.dropTrack(this.trackType);
        }
        if (pos == null) {
            this.next = null;
            this.trackType = TrackItem.Type.STANDARD;
        } else {
            this.next = pos;
            var nextE = next();
            if (nextE != null) {
                nextE.prev = getPos();
                nextE.sync();
                nextE.markDirty();
            }
            this.trackType = trackType == null ? TrackItem.Type.STANDARD : trackType;
        }

        sync();
        markDirty();
    }

    public @Nullable TrackTiesBlockEntity next() {
        return of(this.getWorld(), this.next);
    }

    public @Nullable TrackTiesBlockEntity prev() {
        return of(this.getWorld(), this.prev);
    }

    public Pose pose() {
        return this.pose;
    }

    public void onDestroy() {
        if (this.prev != null) {
            TrackTiesBlockEntity prevE = prev();
            this.dropTrack(prevE != null ? prevE.getTrackType() : TrackItem.Type.STANDARD);
        }
        if (this.next != null) {
            this.dropTrack(trackType);
        }

        var prevE = prev();
        if (prevE != null) {
            prevE.next = null;
            prevE.trackType = TrackItem.Type.STANDARD;
            prevE.sync();
            prevE.markDirty();
        }
        var nextE = next();
        if (nextE != null) {
            nextE.prev = null;
            if (nextE.next == null) {
                nextE.trackType = TrackItem.Type.STANDARD;
            }
            nextE.sync();
            nextE.markDirty();
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);

        this.prev = SUtil.getBlockPos(nbt, "prev");
        this.next = SUtil.getBlockPos(nbt, "next");
        if(nbt.contains("track_type", NbtElement.STRING_TYPE)) {
            try {
                this.trackType = TrackItem.Type.valueOf(nbt.getString("track_type"));
            } catch(IllegalArgumentException ex) {
                this.trackType = TrackItem.Type.STANDARD;
            }
        } else this.trackType = TrackItem.Type.STANDARD;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);

        SUtil.putBlockPos(nbt, this.prev, "prev");
        SUtil.putBlockPos(nbt, this.next, "next");
        nbt.putString("track_type", trackType.name());
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        var nbt = super.toInitialChunkDataNbt();
        writeNbt(nbt);
        return nbt;
    }

    public void sync() {
        getWorld().updateListeners(getPos(), getCachedState(), getCachedState(), 3);
    }

    public TrackItem.Type getTrackType() {
        return trackType;
    }

    public void updatePower() {
        int oldPower = this.power;
        this.power = getWorld().getReceivedRedstonePower(getPos());
        if (oldPower != this.power) {
            markDirty();
            sync();
        }
    }

    public int power() {
        updatePower();
        return this.power;
    }

    public static void staticTick(World world, BlockPos pos, BlockState state, BlockEntity be) {
        if(be instanceof TrackTiesBlockEntity ttBE) ttBE.tick(world, pos, state);
    }

    public boolean hasCart() {
        return hasCartTicks > 0;
    }

    public void markHasCart() {
        boolean didNotHaveCart = !hasCart();
        hasCartTicks = 2;
        if(didNotHaveCart && world != null) {
            world.updateComparators(pos, getCachedState().getBlock());
        }
    }

    public void markHasCart(TrackFollowerEntity cart) {
        markHasCart();
    }

}