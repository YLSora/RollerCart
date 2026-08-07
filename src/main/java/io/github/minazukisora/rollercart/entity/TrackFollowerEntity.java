package io.github.minazukisora.rollercart.entity;

import io.github.minazukisora.rollercart.RollerCart;
import io.github.minazukisora.rollercart.block.ActivatorTiesBlock;
import io.github.minazukisora.rollercart.block.ShuttleTiesBlock;
import io.github.minazukisora.rollercart.block.SwitchTiesBlock;
import io.github.minazukisora.rollercart.block.TrackTiesBlockEntity;
import io.github.minazukisora.rollercart.item.TrackItem;
import io.github.minazukisora.rollercart.util.Pose;
import io.github.minazukisora.rollercart.util.SUtil;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
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

    private BlockPos startTie;
    private BlockPos endTie;
    private double splinePieceProgress = 0; // t
    private double motionScale; // t-distance per block
    private double trackVelocity;
    private boolean reversed = false;

    private final Vector3d serverPosition = new Vector3d();
    private final Vector3d serverVelocity = new Vector3d();
    private int positionInterpSteps;
    private int oriInterpSteps;

    private static final TrackedData<Quaternionf> ORIENTATION = DataTracker.registerData(TrackFollowerEntity.class, TrackedDataHandlerRegistry.QUATERNIONF);
    private static final TrackedData<Boolean> CHAIN_LIFTING = DataTracker.registerData(TrackFollowerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private final Matrix3d basis = new Matrix3d().identity();

    private final Quaternionf lastClientOrientation = new Quaternionf();
    private final Quaternionf clientOrientation = new Quaternionf();

    private boolean hadPassenger = false;

    private boolean firstPositionUpdate = true;
    private boolean firstOriUpdate = true;

    private Vec3d clientMotion = Vec3d.ZERO;

    public TrackFollowerEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    public TrackFollowerEntity(World world, Vec3d startPos, BlockPos startTie, BlockPos endTie, Vec3d velocity) {
        this(RollerCart.TRACK_FOLLOWER.get(), world);

        setStretch(startTie, endTie);
        this.trackVelocity = velocity.multiply(1, 0, 1).length();

        var startE = TrackTiesBlockEntity.of(this.getWorld(), this.startTie);
        if (startE != null) {
            this.setPosition(startPos);
            this.getDataTracker().set(ORIENTATION, startE.pose().basis().getNormalizedRotation(new Quaternionf()));
        }
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

        var startE = TrackTiesBlockEntity.of(this.getWorld(), this.startTie);
        if (startE != null) {
            this.basis.set(startE.pose().basis());
            var endE = TrackTiesBlockEntity.of(this.getWorld(), this.endTie);
            if (endE != null) {
                // Initial approximation of motion scale; from the next tick onward the derivative of the track spline is used
                this.motionScale = 1 / startE.pose().translation().distance(endE.pose().translation());
            } else {
                this.motionScale = 1;
            }
        }

        if (this.splinePieceProgress < 0) {
            this.splinePieceProgress = 0;
        }
    }

    // For more accurate client side position interpolation, we can conveniently use the
    // same cubic hermite spline formula rather than linear interpolation like vanilla,
    // since we have not only the position but also its derivative (velocity)
    protected void interpPos(int step) {
        double t = 1 / (double)step;

        var clientPos = new Vector3d(this.getX(), this.getY(), this.getZ());

        var cv = this.getVelocity();
        var clientVel = new Vector3d(cv.getX(), cv.getY(), cv.getZ());

        var newClientPos = new Vector3d();
        var newClientVel = new Vector3d();
        Pose.cubicHermiteSpline(t, 1, clientPos, clientVel, this.serverPosition, this.serverVelocity,
                newClientPos, newClientVel);

        this.setPosition(newClientPos.x(), newClientPos.y(), newClientPos.z());
        this.setVelocity(newClientVel.x(), newClientVel.y(), newClientVel.z());
    }

    @Override
    public void tick() {
        super.tick();

        var world = this.getWorld();
        if (world.isClient()) {
            this.clientMotion = this.getPos().negate();
            if (this.positionInterpSteps > 0) {
                this.interpPos(this.positionInterpSteps);
                this.positionInterpSteps--;
            } else {
                this.refreshPosition();
                this.setVelocity(this.serverVelocity.x(), this.serverVelocity.y(), this.serverVelocity.z());
            }
            this.clientMotion = this.clientMotion.add(this.getPos());

            this.lastClientOrientation.set(this.clientOrientation);
            if (this.oriInterpSteps > 0) {
                float delta = 1 / (float) oriInterpSteps;
                this.clientOrientation.slerp(this.getDataTracker().get(ORIENTATION), delta);
                this.oriInterpSteps--;
            } else {
                this.clientOrientation.set(this.getDataTracker().get(ORIENTATION));
            }
        } else {
            this.updateServer();
        }
    }

    public void getClientOrientation(Quaternionf q, float tickDelta) {
        this.lastClientOrientation.slerp(this.clientOrientation, tickDelta, q);
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
                this.getDataTracker().set(ORIENTATION, this.basis.getNormalizedRotation(new Quaternionf()));
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
                boolean nearPoweredActivator = poweredActivatorPos != null;

                if (passenger instanceof AbstractMinecartEntity minecart) {
                    if (passenger instanceof HopperMinecartEntity hopper) {
                        hopper.setEnabled(!nearPoweredActivator);
                    } else if (nearPoweredActivator) {
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
                this.dataTracker.set(CHAIN_LIFTING, trackType == TrackItem.Type.CHAIN || (trackType == TrackItem.Type.STATION && powered));
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
        if (this.firstPositionUpdate) {
            this.firstPositionUpdate = false;
            super.updateTrackedPositionAndAngles(x, y, z, yaw, pitch, interpolationSteps, interpolate);
        }

        this.serverPosition.set(x, y, z);
        this.positionInterpSteps = interpolationSteps + 2;
        this.setYaw(yaw);
        this.setPitch(pitch);
    }

    // This method should be called updateTrackedVelocity, its usage is very similar to the above method
    @Override
    public void setVelocityClient(double x, double y, double z) {
        this.serverVelocity.set(x, y, z);
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        positionUpdater.accept(passenger, this.getX(), this.getY(), this.getZ());
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(ORIENTATION, new Quaternionf().identity());
        this.dataTracker.startTracking(CHAIN_LIFTING, false);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);

        if (data.equals(ORIENTATION)) {
            if (this.firstOriUpdate) {
                this.firstOriUpdate = false;
                this.clientOrientation.set(getDataTracker().get(ORIENTATION));
                this.lastClientOrientation.set(this.clientOrientation);
            }
            this.oriInterpSteps = this.getType().getTrackTickInterval() + 2;
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
    }

    public boolean isChainLifting() {
        return this.dataTracker.get(CHAIN_LIFTING);
    }

}
