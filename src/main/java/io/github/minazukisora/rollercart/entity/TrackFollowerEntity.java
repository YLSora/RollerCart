package io.github.minazukisora.rollercart.entity;

import io.github.minazukisora.rollercart.RollerCart;
import io.github.minazukisora.rollercart.block.ActivatorTiesBlock;
import io.github.minazukisora.rollercart.block.ShuttleTiesBlock;
import io.github.minazukisora.rollercart.block.SwitchTiesBlock;
import io.github.minazukisora.rollercart.block.TrackTiesBlockEntity;
import io.github.minazukisora.rollercart.item.TrackItem;
import io.github.minazukisora.rollercart.util.SUtil;
import io.github.minazukisora.rollercart.util.Pose;
import io.github.minazukisora.rollercart.util.TrackCameraTransform;
import io.github.minazukisora.rollercart.util.TrackSnapUtil;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.CommandBlockMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;

import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public class TrackFollowerEntity extends Entity {
    private static final double COMFORTABLE_SPEED = 0.37;
    private static final double MAX_SPEED = 1.28;
    private static final double MAX_ENERGY = 2.45;
    private static final double FRICTION = 0.986;
    private static final double MAGNETIC_FRICTION = 0.997;
    private static final double MAGNETIC_SPEED_FACTOR = 1.6;
    private static final double MAGNETIC_ACCEL = 0.07;
    private static final double CHAIN_FRICTION = 0.997;
    private static final double CHAIN_DRIVE_SPEED = 0.36;
    private static final double GRAVITY = 0.02;

    private @Nullable BlockPos startTie;
    private @Nullable BlockPos endTie;
    private double splinePieceProgress = 0; // t
    private double motionScale; // t-distance per block
    private double trackVelocity;
    private boolean reversed = false;

    private int trackSegment;
    private double clientProgress;
    private double lastClientProgress;
    private double targetProgress;
    private int progressInterpSteps;
    private boolean hasClientTrack;

    private static final TrackedData<NbtCompound> TRACK_STATE = DataTracker.registerData(TrackFollowerEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final TrackedData<Float> CAMERA_YAW_OFFSET = DataTracker.registerData(TrackFollowerEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private final Matrix3d basis = new Matrix3d().identity();

    private boolean hadPassenger = false;

    private Vec3d clientMotion = Vec3d.ZERO;

    public TrackFollowerEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    public TrackFollowerEntity(World world) {
        this(RollerCart.TRACK_FOLLOWER.get(), world);
    }

    public static @Nullable TrackFollowerEntity create(World world, Vec3d startPos, BlockPos tie, Vec3d velocity) {

        var tieE = TrackTiesBlockEntity.of(world, tie);
        double trackVelocity, progress;
        BlockPos start, end;
        
        if (tieE != null) {
            var tieDir = new Vector3d(0, 0, 1).mul(tieE.pose().basis()).normalize();
            var velDir = new Vector3d(velocity.getX(), velocity.getY(), velocity.getZ()).normalize();

            if (tieDir.dot(velDir) >= 0) { // Heading in positive direction
                trackVelocity = velocity.length();
                start = tie;
                end = tieE.nextPos();
                progress = 0;
            } else {
                trackVelocity = -velocity.length();
                start = tieE.prevPos();
                end = tie;
                progress = 1;
            }
            
        } else {
            return null;
        }

        var startE = TrackTiesBlockEntity.of(world, start);
        if (startE != null) {
            var follower = new TrackFollowerEntity(world);
            follower.trackVelocity = trackVelocity;
            follower.splinePieceProgress = progress;
            follower.setStretch(start, end);
            follower.setPosition(startPos);
            follower.syncTrackState();
            follower.getDataTracker().set(CAMERA_YAW_OFFSET,
                    TrackCameraTransform.horizontalYawOffset(velocity.getX(), velocity.getZ()));

            return follower;
        } 

        return null;
    }

    public BlockPos getStartTie() {
        return this.startTie;
    }

    public BlockPos getEndTie() {
        return this.endTie;
    }

    public void setStretch(BlockPos start, BlockPos end) {
        this.startTie = start;
        this.endTie = end;
    }

    private void syncTrackState() {
        if (this.startTie == null || this.endTie == null) return;
        var state = new NbtCompound();
        state.putLong("start", this.startTie.asLong());
        state.putLong("end", this.endTie.asLong());
        state.putInt("segment", this.trackSegment);
        state.putDouble("progress", this.trackSegment + this.splinePieceProgress);
        this.dataTracker.set(TRACK_STATE, state);
    }

    /** Samples the actual connected track, including frames that cross a tie. */
    public @Nullable Pose getClientPose(float tickDelta) {
        if (!this.hasClientTrack) return null;
        double progress = this.lastClientProgress
                + (this.clientProgress - this.lastClientProgress) * tickDelta - this.trackSegment;
        var start = TrackTiesBlockEntity.of(getWorld(), this.startTie);
        var end = TrackTiesBlockEntity.of(getWorld(), this.endTie);
        while (progress < 0 && start != null) {
            end = start;
            start = start.prev();
            progress++;
        }
        while (progress > 1 && end != null) {
            start = end;
            end = end.next();
            progress--;
        }
        // Track chunks can arrive after the entity's spawn/data packets.
        if (start == null || end == null) return null;
        var position = new Vector3d();
        var orientation = new Matrix3d();
        start.pose().interpolate(end.pose(), progress, position, orientation, new Vector3d());
        return new Pose(position, orientation);
    }

    @Override
    public void tick() {
        super.tick();

        var world = this.getWorld();
        if (world.isClient()) {
            var previousPosition = this.getPos();
            this.lastClientProgress = this.clientProgress;
            if (this.progressInterpSteps > 0) {
                this.clientProgress += (this.targetProgress - this.clientProgress) / this.progressInterpSteps;
                this.progressInterpSteps--;
            }
            var pose = getClientPose(1);
            if (pose != null) {
                var position = pose.translation();
                this.setPosition(position.x(), position.y(), position.z());
                this.basis.set(pose.basis());
            }
            this.clientMotion = this.getPos().subtract(previousPosition);
            this.setVelocity(this.clientMotion);
        } else {
            this.updateServer();
        }
    }

    public void getClientOrientation(Quaternionf q, float tickDelta) {
        var pose = getClientPose(tickDelta);
        (pose == null ? this.basis : pose.basis()).getNormalizedRotation(q);
    }

    public float getCameraYawOffset() {
        return this.getDataTracker().get(CAMERA_YAW_OFFSET);
    }

    public Vec3d getClientMotion() {
        return this.clientMotion;
    }

    public double getTrackVelocity() {
        return this.trackVelocity;
    }

    public Matrix3dc getServerBasis() {
        return this.basis;
    }

    public void destroy() {
        this.remove(RemovalReason.KILLED);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    protected void updateServer() {
        for (var passenger : this.getPassengerList()) {
            passenger.fallDistance = 0;
        }

        var passenger = this.getFirstPassenger();
        if (passenger != null) {
            if (!hadPassenger) {
                hadPassenger = true;
            } else {
                var world = this.getWorld();
                var startE = TrackTiesBlockEntity.of(world, this.startTie);
                var endE = TrackTiesBlockEntity.of(world, this.endTie);
                if (startE == null || endE == null) {
                    this.destroy();
                    return;
                }

                startE.markHasCart(this);
                endE.markHasCart(this);

                double velocity = Math.min(this.trackVelocity, MAX_SPEED);
                this.splinePieceProgress += velocity * this.motionScale * (this.reversed ? -1 : 1);
                if (this.splinePieceProgress > 1) {
                    if(endE.getCachedState().isOf(RollerCart.SHUTTLE_TIES.get()) && endE.getCachedState().get(ShuttleTiesBlock.RUNNING)) {
                        this.reversed = !reversed;
                        return;
                    } else {
                        this.splinePieceProgress -= 1;
                        var nextE = endE.next();
                        if (nextE == null) {
                            fullDismount(passenger, endE.getCachedState(), this.endTie, false);
                            return;
                        } else {
                            if (endE.getCachedState().isOf(RollerCart.SWITCH_TIES.get())) {
                                boolean switched = endE.getCachedState().get(SwitchTiesBlock.SWITCHED);
                                if (switched) {
                                    fullDismount(passenger, endE.getCachedState(), this.endTie, true);
                                    return;
                                }
                            }
                            this.setStretch(this.endTie, nextE.getPos());
                            this.trackSegment++;
                            startE = endE;
                            endE = nextE;
                        }
                    }
                } else if(this.splinePieceProgress < 0) {
                    if(startE.getCachedState().isOf(RollerCart.SHUTTLE_TIES.get()) && startE.getCachedState().get(ShuttleTiesBlock.RUNNING)) {
                        this.reversed = !reversed;
                        return;
                    } else {
                        this.splinePieceProgress += 1;
                        var nextE = startE.prev();
                        if (nextE == null) {
                            fullDismount(passenger, startE.getCachedState(), this.startTie, false);
                            return;
                        } else {
                            this.setStretch(nextE.getPos(), this.startTie);
                            this.trackSegment--;
                            endE = startE;
                            startE = nextE;
                        }
                    }
                }

                var pos = new Vector3d();
                var grad = new Vector3d(); // Change in position per change in spline progress
                startE.pose().interpolate(endE.pose(), this.splinePieceProgress, pos, this.basis, grad);

                TrackItem.Type trackType = startE.getTrackType();

                this.setPosition(pos.x(), pos.y(), pos.z());
                this.syncTrackState();
                this.motionScale = 1 / grad.length();

                double dt = this.trackVelocity * this.motionScale * (this.reversed ? -1 : 1); // Change in spline progress per tick
                grad.mul(dt); // Change in position per tick (velocity)
                this.setVelocity(grad.x(), grad.y(), grad.z());

                BlockPos poweredActivatorPos = null;
                for (var tie : new TrackTiesBlockEntity[]{startE, endE}) {
                    var state = tie.getCachedState();
                    if (state.isOf(RollerCart.ACTIVATOR_TIES.get()) && state.get(ActivatorTiesBlock.POWERED)) {
                        var t = tie.pose().translation();
                        if (pos.distanceSquared(t.x(), t.y(), t.z()) < 0.25) {
                            poweredActivatorPos = tie.getPos();
                            break;
                        }
                    }
                }
                if (passenger instanceof AbstractMinecartEntity) {
                    if (passenger instanceof HopperMinecartEntity hopper) {
                        hopper.setEnabled(poweredActivatorPos == null);
                    } else if (poweredActivatorPos != null) {
                        if (passenger instanceof TntMinecartEntity tnt && tnt.getFuseTicks() < 0) {
                            tnt.prime();
                        } else if (passenger instanceof MinecartEntity mc && mc.hasPassengers()) {
                            mc.removeAllPassengers();
                        } else if (passenger instanceof CommandBlockMinecartEntity cmd) {
                            cmd.onActivatorRail(poweredActivatorPos.getX(), poweredActivatorPos.getY(), poweredActivatorPos.getZ(), true);
                        }
                    }
                }

                boolean powered = startE.power() > 0;
                switch(trackType) {
                    case CHAIN -> {
                        double target = powered ? CHAIN_DRIVE_SPEED : 0.05;
                        this.trackVelocity = Math.max(this.trackVelocity * CHAIN_FRICTION, target);
                        this.reversed = false;
                    }
                    case STATION -> {
                        if(powered) {
                            this.trackVelocity = Math.max(this.trackVelocity * CHAIN_FRICTION, CHAIN_DRIVE_SPEED);
                            this.reversed = false;
                        } else {
                            this.trackVelocity *= 0.9;
                        }
                    }
                    case BRAKE -> {
                        if(powered) {
                            this.applyStandardPhysics();
                        } else {
                            this.trackVelocity *= 0.9;
                        }
                    }
                    case MAGNETIC -> {
                        var grade = new Vector3d(0, 1, 0).mul(this.basis).mul(1, 0, 1).length();
                        int power = Math.max(startE.power(), endE.power());
                        double speed = (power / 15.0) * MAGNETIC_SPEED_FACTOR;
                        this.trackVelocity *= MAGNETIC_FRICTION;
                        this.trackVelocity += (speed - this.trackVelocity) * MAGNETIC_ACCEL * (1 - grade);
                        this.trackVelocity += this.trackGravity();
                    }
                    default -> {
                        this.applyStandardPhysics();
                    }
                }
            }
        } else {
            if (this.hadPassenger) {
                this.destroy();
            }
        }
    }

    private double trackGravity() {
        var forward = new Vector3d(0, 0, 1).mul(this.basis);
        return -forward.y() / forward.length() * GRAVITY * (this.reversed ? -1 : 1);
    }

    private void applyStandardPhysics() {
        if (this.trackVelocity > COMFORTABLE_SPEED) {
            double diff = this.trackVelocity - COMFORTABLE_SPEED;
            diff *= FRICTION;
            this.trackVelocity = COMFORTABLE_SPEED + diff;
        }
        this.trackVelocity += this.trackGravity();
    }

    private void fullDismount(Entity passenger, BlockState ties, BlockPos tiesPos, boolean trackSwitch) {
        passenger.stopRiding();
        if(trackSwitch) {
            var tieE = TrackTiesBlockEntity.of(getWorld(), tiesPos);
            Vec3d push = Vec3d.ZERO;
            if (tieE != null) {
                var pose = tieE.pose();
                var forward = new Vector3d(0, 0, 1);
                forward.mul(pose.basis());
                var up = new Vector3d(0, 1, 0);
                up.mul(pose.basis());
                var right = new Vector3d();
                forward.cross(up, right);
                right.normalize();
                push = new Vec3d(right.x(), right.y(), right.z());
            }
            passenger.setVelocity(push.x, push.y, push.z);
            Vec3d startPos = passenger.getPos().add(push);
            passenger.setPos(startPos.x, startPos.y, startPos.z);
        } else {
            Vector3d newVel = new Vector3d(0, 0, this.trackVelocity).mul(this.basis).mul(this.reversed ? -1 : 1);
            passenger.setVelocity(newVel.x(), newVel.y(), newVel.z());
            boolean reverse = this.reversed ^ this.trackVelocity < 0;
            BlockPos snapTo = TrackSnapUtil.snapToTrackOnExit(getWorld(), tiesPos, ties, reverse);
            if(snapTo != null) passenger.setPos(snapTo.getX() + 0.5, snapTo.getY(), snapTo.getZ() + 0.5);
        }
        this.destroy();
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
        // TRACK_STATE owns client motion; vanilla XYZ packets must not pull it off the spline.
    }

    // This method should be called updateTrackedVelocity, its usage is very similar to the above method
    @Override
    public void setVelocityClient(double x, double y, double z) {
        // Client velocity is derived from consecutive samples of the track.
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        positionUpdater.accept(passenger, this.getX(), this.getY(), this.getZ());
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(TRACK_STATE, new NbtCompound());
        this.dataTracker.startTracking(CAMERA_YAW_OFFSET, 0.0F);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);

        if (getWorld().isClient() && data.equals(TRACK_STATE)) {
            var state = this.dataTracker.get(TRACK_STATE);
            if (state.isEmpty()) return;
            this.startTie = BlockPos.fromLong(state.getLong("start"));
            this.endTie = BlockPos.fromLong(state.getLong("end"));
            this.trackSegment = state.getInt("segment");
            this.targetProgress = state.getDouble("progress");
            if (!this.hasClientTrack) {
                this.clientProgress = this.lastClientProgress = this.targetProgress;
                this.hasClientTrack = true;
            }
            this.progressInterpSteps = this.getType().getTrackTickInterval() + 2;
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("start")) {
            this.startTie = SUtil.getBlockPos(nbt, "start");
        } else this.startTie = null;
        if (nbt.contains("end")) {
            this.endTie = SUtil.getBlockPos(nbt, "end");
        } else this.endTie = null;
        this.trackVelocity = nbt.getDouble("track_velocity");
        this.motionScale = nbt.getDouble("motion_scale");
        this.splinePieceProgress = nbt.getDouble("spline_piece_progress");
        this.reversed = nbt.getBoolean("reversed");
        this.syncTrackState();
        this.getDataTracker().set(CAMERA_YAW_OFFSET, nbt.getFloat("camera_yaw_offset"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (this.startTie != null) {
            SUtil.putBlockPos(nbt, this.startTie, "start");
        }
        if (this.endTie != null) {
            SUtil.putBlockPos(nbt, this.endTie, "end");
        }
        nbt.putDouble("track_velocity", this.trackVelocity);
        nbt.putDouble("motion_scale", this.motionScale);
        nbt.putDouble("spline_piece_progress", this.splinePieceProgress);
        nbt.putBoolean("reversed", this.reversed);
        nbt.putFloat("camera_yaw_offset", this.getCameraYawOffset());
    }

}
