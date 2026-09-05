package io.github.minazukisora.rollercart.mixin.client;

import io.github.minazukisora.rollercart.entity.TrackFollowerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Unique private boolean onTrackFollower = false;
    @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", shift = At.Shift.BEFORE, ordinal = 0, target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void rollercart$rotateEntitiesOnTrackFollower(Entity entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo info) {
        if (entity instanceof TrackFollowerEntity) return;

        Entity vehicle = entity;
        while (vehicle != null) {
            Entity next = vehicle.getVehicle();

            if (next instanceof TrackFollowerEntity trackFollower) {
                var pose = trackFollower.getClientPose(tickDelta);
                if (pose == null) return;
                var rotation = pose.basis().getNormalizedRotation(new Quaternionf());

                matrices.push();
                onTrackFollower = true;

                var dv3d = entity.getPos().subtract(trackFollower.getPos());
                var diff = new Vector3d(dv3d.getX(), dv3d.getY(), dv3d.getZ());
                var position = pose.translation();
                matrices.translate(
                        position.x() - MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()),
                        position.y() - MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()),
                        position.z() - MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()));

                matrices.multiply(rotation);

                matrices.translate(diff.x(), diff.y(), diff.z());
                if (entity instanceof AbstractMinecartEntity) {
                    // Vanilla's 6/16 model lift leaves its bottom 1/16 above the rail.
                    matrices.translate(0, -1.0 / 16.0, 0);
                }

                if (entity instanceof LivingEntity) {
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(trackFollower.getCameraYawOffset()));
                } else if (entity instanceof AbstractMinecartEntity) {
                    // MinecartEntityRenderer applies (180 - yaw), so cancel with +yaw.
                    // The remaining 90 degrees aligns the model's X axis with the track's Z axis.
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw - 90.0F));
                } else {
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotation(-MathHelper.PI / 2 - yaw * MathHelper.RADIANS_PER_DEGREE));
                }

                return;
            }

            vehicle = next;
        }
    }

    @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 0, target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void rollercart$undoTransform(Entity entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo info) {
        if (onTrackFollower) {
            onTrackFollower = false;
            matrices.pop();
        }
    }
}
