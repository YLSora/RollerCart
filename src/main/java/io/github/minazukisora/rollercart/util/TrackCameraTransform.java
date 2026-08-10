package io.github.minazukisora.rollercart.util;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TrackCameraTransform {
    private static final double MIN_HORIZONTAL_SPEED_SQUARED = 1.0E-12;
    private static final float RADIANS_PER_DEGREE = (float) (Math.PI / 180.0);

    private TrackCameraTransform() {
    }

    public static float horizontalYawOffset(double velocityX, double velocityZ) {
        double horizontalSpeedSquared = velocityX * velocityX + velocityZ * velocityZ;
        if (!Double.isFinite(horizontalSpeedSquared) || horizontalSpeedSquared < MIN_HORIZONTAL_SPEED_SQUARED) {
            throw new IllegalArgumentException("A finite horizontal velocity is required to orient the track camera");
        }

        return (float) Math.toDegrees(Math.atan2(-velocityX, velocityZ));
    }

    public static void apply(Quaternionf trackOrientation, float horizontalYawOffset, Quaternionf cameraRotation) {
        new Quaternionf()
                .rotationY(horizontalYawOffset * RADIANS_PER_DEGREE)
                .mul(cameraRotation, cameraRotation);
        trackOrientation.mul(cameraRotation, cameraRotation);
    }

    /**
     * Converts Camera's YXZ quaternion to Forge's yaw, pitch and roll tuple.
     * The returned vector stores those values in x, y and z respectively.
     */
    public static Vector3f toForgeAngles(Quaternionf cameraRotation, Vector3f destination) {
        cameraRotation.getEulerAnglesYXZ(destination);
        return destination.set(
                (float) -Math.toDegrees(destination.y()),
                (float) Math.toDegrees(destination.x()),
                (float) Math.toDegrees(destination.z()));
    }
}
