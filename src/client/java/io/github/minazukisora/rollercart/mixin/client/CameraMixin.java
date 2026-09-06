package io.github.minazukisora.rollercart.mixin.client;

import io.github.minazukisora.rollercart.RollerCartClient;
import io.github.minazukisora.rollercart.entity.TrackFollowerEntity;
import io.github.minazukisora.rollercart.util.TrackCameraTransform;
import net.minecraft.client.render.Camera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(Vec3d pos);
    // @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void moveBy(double x, double y, double z);
    //@Shadow protected abstract double clipToSpace(double desiredCameraDistance);
    @Shadow @Final private Quaternionf rotation;
    @Shadow private Entity focusedEntity;
    // @Shadow @Final private Vector3f horizontalPlane;
    // @Shadow @Final private Vector3f verticalPlane;
    // @Shadow @Final private Vector3f diagonalPlane;

    @Inject(method = "update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 0, target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V"))
    private void CamPos(BlockView area, Entity self, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo info) {
        var vehicle = self.getVehicle();
        if (vehicle != null) {
            var tf = vehicle.getVehicle();
            if (tf instanceof TrackFollowerEntity trackFollower) {
                var pose = trackFollower.getClientPose(tickDelta);
                if (pose == null) return;
                var world = self.getWorld();
                var diff = self.getPos().add(0, self.getStandingEyeHeight(), 0).subtract(trackFollower.getPos());
                var camPos = new Vector3d(diff.getX(), diff.getY(), diff.getZ());
                if (world.isClient()) {
                    var rot = new Quaternionf();
                    trackFollower.getClientOrientation(rot, tickDelta);
                    rot.transform(camPos);

                    var position = pose.translation();
                    this.setPos(new Vec3d(camPos.x() + position.x(), camPos.y() + position.y(), camPos.z() + position.z()));
                }
            }
        }
    }


@Inject(method = "setRotation(FF)V",
    at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 0, target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;", remap = false))
    private void CamRotation(float yaw, float pitch, CallbackInfo info) {
        var self = this.focusedEntity;
        if (self == null) {
            return;
        }
        var vehicle = self.getVehicle();
        var tickDelta = MinecraftClient.getInstance().getTickDelta();
        if (vehicle != null) {
            var tf = vehicle.getVehicle();
            if (tf instanceof TrackFollowerEntity trackFollower) {
                var world = self.getWorld();
                if (world.isClient()) {
                    var rot = new Quaternionf();
                    trackFollower.getClientOrientation(rot, tickDelta);

                    if (RollerCartClient.CFG_ROTATE_CAMERA.get()) {
                        TrackCameraTransform.apply(rot, trackFollower.getCameraYawOffset(), rotation);
                    }
                }
            }
        }
    }
}
