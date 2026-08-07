package io.github.minazukisora.rollercart.mixin.client;

import io.github.minazukisora.rollercart.RollerCartClient;
import io.github.minazukisora.rollercart.entity.TrackFollowerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(Vec3d pos);
    @Shadow protected abstract void moveBy(double x, double y, double z);
    @Shadow protected abstract double clipToSpace(double desiredCameraDistance);
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f horizontalPlane;
    @Shadow @Final private Vector3f verticalPlane;
    @Shadow @Final private Vector3f diagonalPlane;

    @Inject(method = "update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V", at = @At("RETURN"))
    private void rollercart$updateTrackCamera(BlockView area, Entity self, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo info) {
        Entity cart = self.getVehicle();
        if (cart == null || !(cart.getVehicle() instanceof TrackFollowerEntity trackFollower)) return;

        Quaternionf trackRotation = new Quaternionf();
        trackFollower.getClientOrientation(trackRotation, tickDelta);
        Vec3d eye = self.getCameraPosVec(tickDelta);
        org.joml.Vector3d relativeEye = new org.joml.Vector3d(
                eye.getX() - trackFollower.getLerpedPos(tickDelta).getX(),
                eye.getY() - trackFollower.getLerpedPos(tickDelta).getY(),
                eye.getZ() - trackFollower.getLerpedPos(tickDelta).getZ());
        trackRotation.transform(relativeEye);
        Vec3d transformedEye = new Vec3d(relativeEye.x, relativeEye.y, relativeEye.z).add(trackFollower.getLerpedPos(tickDelta));

        if (!RollerCartClient.CFG_ROTATE_CAMERA.get()) {
            Vec3d offset = ((Camera)(Object)this).getPos().subtract(eye);
            this.setPos(transformedEye.add(offset));
            return;
        }

        // Replicates the upstream Splinecart camera orientation exactly:
        //   rotation = trackRot * Ry(90 + vehicleYaw) * (vanilla rotation)
        // where vanilla `rotation` = Ry(-playerYaw) * Rx(playerPitch) was set by
        // Camera.setRotation during update. Using the player's absolute look angles
        // (not accumulated deltas) keeps mouse direction/sensitivity matching vanilla,
        // and trackRot carries the cart's bank so the camera rolls with the track.
        new Quaternionf().rotationY((float) Math.toRadians(90 + cart.getYaw(tickDelta))).mul(this.rotation, this.rotation);
        trackRotation.mul(this.rotation, this.rotation);
        this.horizontalPlane.set(0, 0, 1).rotate(this.rotation);
        this.verticalPlane.set(0, 1, 0).rotate(this.rotation);
        this.diagonalPlane.set(1, 0, 0).rotate(this.rotation);
        this.setPos(transformedEye);
        if (thirdPerson) this.moveBy(-this.clipToSpace(4), 0, 0);
    }
}
