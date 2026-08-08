package io.github.minazukisora.rollercart.block.entity;

import io.github.minazukisora.rollercart.RollerCart;
import io.github.minazukisora.rollercart.RollerCartClient;
import io.github.minazukisora.rollercart.block.TrackTiesBlock;
import io.github.minazukisora.rollercart.block.TrackTiesBlockEntity;
import io.github.minazukisora.rollercart.item.TrackItem;
import io.github.minazukisora.rollercart.util.Pose;
import io.github.minazukisora.rollercart.util.SUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class TrackTiesBlockEntityRenderer implements BlockEntityRenderer<TrackTiesBlockEntity> {

    // Required for Connector on NeoForge to render this BE properly
    public static final Box INFINITE_BOX = new Box(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

    public static final int WHITE = 0xFFFFFFFF;
    public static final Identifier STATION_TRACK_TEXTURE = RollerCart.id("textures/station_track.png");
    private static final Identifier BRAKE_TRACK_TEXTURE = RollerCart.id("textures/brake_track.png");
    private static final Identifier BRAKE_TRACK_UNPOWERED_TEXTURE = RollerCart.id("textures/brake_track_unpowered.png");
    private static final Identifier TRACK_ATLAS_TEXTURE = RollerCart.id("textures/splinecart_track.png");
    private static final Identifier TRACK_OVERLAY_TEXTURE = RollerCart.id("textures/track_overlay.png");
    public static final Identifier TRACK_TEXTURE = RollerCart.id("textures/track.png");
    public static final Identifier POSE_TEXTURE_DEBUG = RollerCart.id("textures/debug.png");

    public TrackTiesBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(TrackTiesBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if(entity.getCachedState().isOf(RollerCart.INVISIBLE_TIES.get())) {
            MinecraftClient c = MinecraftClient.getInstance();
            if(c != null && c.player != null && c.player.getStackInHand(Hand.MAIN_HAND).isOf(RollerCart.INVISIBLE_TIES.get().asItem())) {
                BlockRenderManager brm = c.getBlockRenderManager();
                BlockState toRender = entity.getCachedState();
                BakedModel model = brm.getModel(toRender);
                int color = c.getBlockColors().getColor(toRender, entity.getWorld(), entity.getPos(), 0);
                float r = (float) (color >> 16 & 0xFF) / 255.0F;
                float g = (float) (color >> 8 & 0xFF) / 255.0F;
                float b = (float) (color & 0xFF) / 255.0F;
                brm.getModelRenderer().render(
                        matrices.peek(),
                        vertexConsumers.getBuffer(RenderLayers.getEntityBlockLayer(toRender, false)),
                        toRender,
                        model,
                        r, g, b,
                        light,
                        overlay
                );
            }
        }

        var start = entity.pose();
        var pos = entity.getPos();

        if (MinecraftClient.getInstance().options.debugEnabled) {
            matrices.push();

            matrices.translate(0.5, 0.5, 0.5);
            var buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(POSE_TEXTURE_DEBUG));
            renderDebug(start, matrices.peek(), buffer);

            matrices.pop();
        }

        var trackType = entity.getTrackType();
        var buffer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(getTexture(entity)));
        var nextE = entity.next();
        if (nextE != null) {
            var end = nextE.pose();
            var world = entity.getWorld();

            matrices.push();

            matrices.translate(-pos.getX(), -pos.getY(), -pos.getZ());

            int segs = RollerCartClient.CFG_TRACK_RESOLUTION.get() * Math.max((int) start.translation().distance(end.translation()), 2);
            float u0 = getU0(trackType);
            float u1 = getU1(trackType);
            renderTrack(world, matrices.peek(), buffer, start, end, segs, u0, u1, 0, WHITE, overlay);

            if (trackType == TrackItem.Type.MAGNETIC) {
                Vector3f color = SUtil.REDSTONE_COLOR_LUT[Math.max(entity.power(), nextE.power())];
                int packedColor = 0xFF000000 | (int) (color.x() * 255) << 16 | (int) (color.y() * 255) << 8 | (int) (color.z() * 255);
                renderTrack(world, matrices.peek(), vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TRACK_OVERLAY_TEXTURE)),
                        start, end, segs, u0, u1, 0, packedColor, overlay);
            } else if (trackType == TrackItem.Type.CHAIN) {
                float speed = entity.power() > 0 ? 0.15f : 0.05f;
                float vOffset = ((world.getTime() + tickDelta) * speed) % 1.0f;
                renderTrack(world, matrices.peek(), vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TRACK_OVERLAY_TEXTURE)),
                        start, end, segs, u0, u1, vOffset, WHITE, overlay);
            } else if (trackType == TrackItem.Type.STATION) {
                float vOffset = entity.power() > 0 ? ((world.getTime() + tickDelta) * 0.15f) % 1.0f : 0.0f;
                renderTrack(world, matrices.peek(), vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TRACK_OVERLAY_TEXTURE)),
                        start, end, segs, 0.5f, 0.75f, vOffset, WHITE, overlay);
            }

            matrices.pop();
        }

        var prevE = entity.prev();
        if ((prevE == null) ^ (nextE == null)) {
            float z0 = -0.5f;
            float z1 = 0;
            float v0 = 0.125f;
            float v1 = 0.5f;
            float u0 = getU0(trackType);
            float u1 = getU1(trackType);

            if (nextE == null) {
                z0 = 0;
                z1 = 0.5f;
                v0 = 0.5f;
                v1 = 0;
            }

            matrices.push();

            matrices.translate(0.5, 0.5, 0.5);

            var entry = matrices.peek();
            var posMat = entry.getPositionMatrix();
            var nmlMat = entry.getNormalMatrix();
            Vector3f point = new Vector3f();
            Vector3d norm = new Vector3d(0, 1, 0).mul(start.basis());

            matrices.translate(0, -0.4375, 0);

            // strange fix, but works
            switch(entity.getCachedState().get(TrackTiesBlock.FACING)) {
                case NORTH -> matrices.translate(0, 0.4375, 0.4375);
                case SOUTH -> matrices.translate(0, 0.4375, -0.4375);
                case WEST -> matrices.translate(0.4375, 0.4375, 0);
                case EAST -> matrices.translate(-0.4375, 0.4375, 0);
                case DOWN -> matrices.translate(0, 0.875, 0);
            }

            point.set(0.5f, 0, z0).mul(start.basis());
            buffer.vertex(posMat, point.x(), point.y(), point.z()).color(WHITE).texture(u1, v0).overlay(overlay).light(light).normal(nmlMat, (float) norm.x(), (float) norm.y(), (float) norm.z()).next();
            point.set(-0.5f, 0, z0).mul(start.basis());
            buffer.vertex(posMat, point.x(), point.y(), point.z()).color(WHITE).texture(u0, v0).overlay(overlay).light(light).normal(nmlMat, (float) norm.x(), (float) norm.y(), (float) norm.z()).next();

            point.set(-0.5f, 0, z1).mul(start.basis());
            buffer.vertex(posMat, point.x(), point.y(), point.z()).color(WHITE).texture(u0, v1).overlay(overlay).light(light).normal(nmlMat, (float) norm.x(), (float) norm.y(), (float) norm.z()).next();
            point.set(0.5f, 0, z1).mul(start.basis());
            buffer.vertex(posMat, point.x(), point.y(), point.z()).color(WHITE).texture(u1, v1).overlay(overlay).light(light).normal(nmlMat, (float) norm.x(), (float) norm.y(), (float) norm.z()).next();

            matrices.pop();
        }
    }

    protected Identifier getTexture(TrackTiesBlockEntity be) {
        return switch(be.getTrackType()) {
            case STANDARD -> TRACK_TEXTURE;
            case CHAIN, MAGNETIC -> TRACK_ATLAS_TEXTURE;
            case STATION -> be.power() > 0 ? BRAKE_TRACK_TEXTURE : BRAKE_TRACK_UNPOWERED_TEXTURE;
            case BRAKE -> be.power() > 0 ? BRAKE_TRACK_TEXTURE : BRAKE_TRACK_UNPOWERED_TEXTURE;
        };
    }

    private float getU0(TrackItem.Type type) {
        return switch(type) {
            case CHAIN -> 0.25f;
            case MAGNETIC -> 0.5f;
            default -> 0;
        };
    }

    private float getU1(TrackItem.Type type) {
        return switch(type) {
            case CHAIN -> 0.5f;
            case MAGNETIC -> 0.75f;
            default -> 1;
        };
    }

    @Override
    public boolean rendersOutsideBoundingBox(TrackTiesBlockEntity blockEntity) {
        return true;
    }

    // Required for Connector on NeoForge to render this BE properly
    @SuppressWarnings("unused")
    public Box getRenderBoundingBox(TrackTiesBlockEntity be) {
        return INFINITE_BOX;
    }

    @Override
    public int getRenderDistance() {
        return RollerCartClient.CFG_TRACK_RENDER_DISTANCE.get() * 16;
    }

    @Override
    public boolean isInRenderDistance(TrackTiesBlockEntity blockEntity, Vec3d pos) {
        BlockPos blockPos = blockEntity.getPos();
        double dx = pos.getX() - (blockPos.getX() + 0.5);
        double dz = pos.getZ() - (blockPos.getZ() + 0.5);
        double dist = this.getRenderDistance();
        return dx * dx + dz * dz <= dist * dist;
    }

    private void renderDebug(Pose pose, MatrixStack.Entry entry, VertexConsumer buffer) {
        var posMat = entry.getPositionMatrix();
        var nmlMat = entry.getNormalMatrix();
        Vector3f point = new Vector3f();
        Vector3d norm = new Vector3d(0, 1, 0).mul(pose.basis());

        point.set(1, 0, 1).mul(pose.basis());
        buffer.vertex(posMat, point.x(), point.y(), point.z()).color(WHITE).texture(0, 0)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(nmlMat, (float) norm.x(), (float) norm.y(), (float) norm.z()).next();
        point.set(0, 0, 1).mul(pose.basis());
        buffer.vertex(posMat, point.x(), point.y(), point.z()).color(WHITE).texture(1, 0)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(nmlMat, (float) norm.x(), (float) norm.y(), (float) norm.z()).next();
        point.set(0, 0, 0).mul(pose.basis());
        buffer.vertex(posMat, point.x(), point.y(), point.z()).color(WHITE).texture(1, 1)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(nmlMat, (float) norm.x(), (float) norm.y(), (float) norm.z()).next();
        point.set(1, 0, 0).mul(pose.basis());
        buffer.vertex(posMat, point.x(), point.y(), point.z()).color(WHITE).texture(0, 1)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(nmlMat, (float) norm.x(), (float) norm.y(), (float) norm.z()).next();
    }

    private void renderTrack(World world, MatrixStack.Entry entry, VertexConsumer buffer, Pose start, Pose end,
                             int segs, float u0, float u1, float vOffset, int color, int overlay) {
        var origin = new Vector3d(start.translation());
        var basis = new Matrix3d(start.basis());
        var grad = new Vector3d(0, 0, 1).mul(start.basis());
        double[] totalDist = {0};
        for (int i = 0; i < segs; i++) {
            renderPart(world, entry, buffer, start, end, (double) i / segs, (double) (i + 1) / segs,
                    totalDist, origin, basis, grad, u0, u1, vOffset, color, overlay);
        }
    }

    private void renderPart(World world, MatrixStack.Entry entry, VertexConsumer buffer, Pose start, Pose end,
                            double t0, double t1, double[] blockProgress, Vector3d origin0, Matrix3d basis0, Vector3d grad0,
                            float u0, float u1, float vOffset, int color, int overlay) {
        start.interpolate(end, t0, origin0, basis0, grad0);
        var norm0 = new Vector3d(0, 1, 0).mul(basis0);

        var origin1 = new Vector3d(origin0);
        var basis1 = new Matrix3d(basis0);
        var grad1 = new Vector3d(grad0);
        start.interpolate(end, t1, origin1, basis1, grad1);
        var norm1 = new Vector3d(0, 1, 0).mul(basis1);

        float v0 = (float) blockProgress[0];
        while (v0 > 1) v0 -= 1;
        float v1 = v0 + (float) (grad0.length() * (t1 - t0));

        blockProgress[0] = v1;

        v1 = 1 - v1 + vOffset;
        v0 = 1 - v0 + vOffset;

        var pos0 = new BlockPos(MathHelper.floor(origin0.x()), MathHelper.floor(origin0.y()), MathHelper.floor(origin0.z()));
        var pos1 = new BlockPos(MathHelper.floor(origin1.x()), MathHelper.floor(origin1.y()), MathHelper.floor(origin1.z()));

        int light0 = WorldRenderer.getLightmapCoordinates(world, pos0);
        int light1 = WorldRenderer.getLightmapCoordinates(world, pos1);

        var point = new Vector3f();
        var posMat = entry.getPositionMatrix();
        var nmlMat = entry.getNormalMatrix();

        point.set(0.5, 0, 0).mul(basis0).add((float) origin0.x(), (float) origin0.y(), (float) origin0.z());
        buffer.vertex(posMat, point.x(), point.y(), point.z()).color(color).texture(u0, v0).overlay(overlay).light(light0).normal(nmlMat, (float) norm0.x(), (float) norm0.y(), (float) norm0.z()).next();
        point.set(-0.5, 0, 0).mul(basis0).add((float) origin0.x(), (float) origin0.y(), (float) origin0.z());
        buffer.vertex(posMat, point.x(), point.y(), point.z()).color(color).texture(u1, v0).overlay(overlay).light(light0).normal(nmlMat, (float) norm0.x(), (float) norm0.y(), (float) norm0.z()).next();

        point.set(-0.5, 0, 0).mul(basis1).add((float) origin1.x(), (float) origin1.y(), (float) origin1.z());
        buffer.vertex(posMat, point.x(), point.y(), point.z()).color(color).texture(u1, v1).overlay(overlay).light(light1).normal(nmlMat, (float) norm1.x(), (float) norm1.y(), (float) norm1.z()).next();
        point.set(0.5, 0, 0).mul(basis1).add((float) origin1.x(), (float) origin1.y(), (float) origin1.z());
        buffer.vertex(posMat, point.x(), point.y(), point.z()).color(color).texture(u0, v1).overlay(overlay).light(light1).normal(nmlMat, (float) norm1.x(), (float) norm1.y(), (float) norm1.z()).next();
    }
}
