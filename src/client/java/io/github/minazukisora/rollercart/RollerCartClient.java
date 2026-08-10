package io.github.minazukisora.rollercart;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.minazukisora.rollercart.block.TrackTiesBlockEntity;
import io.github.minazukisora.rollercart.block.entity.TrackTiesBlockEntityRenderer;
import io.github.minazukisora.rollercart.component.OriginComponent;
import io.github.minazukisora.rollercart.config.Config;
import io.github.minazukisora.rollercart.config.ConfigOption;
import io.github.minazukisora.rollercart.entity.TrackFollowerEntity;
import io.github.minazukisora.rollercart.util.TrackCameraTransform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.joml.Vector3f;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = RollerCart.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RollerCartClient {
    public static final Config CONFIG = new Config("rollercart_client",
            () -> FMLPaths.CONFIGDIR.get().resolve("rollercart").resolve("rollercart_client.properties"));
    public static final ConfigOption.BooleanOption CFG_ROTATE_CAMERA = CONFIG.optBool("rotate_camera", true);

    public static final ConfigOption.IntOption CFG_TRACK_RESOLUTION = CONFIG.optInt("track_resolution", 3, 1, 16);
    public static final ConfigOption.IntOption CFG_TRACK_RENDER_DISTANCE = CONFIG.optInt("track_render_distance", 8, 4, 32);

    static {
        try {
            CONFIG.load();
        } catch (IOException e) {
            RollerCart.LOGGER.error("Error loading client config on mod init", e);
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(RollerCart.TRACK_TIES_BE.get(), TrackTiesBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(RollerCart.DETECTOR_TIES_BE.get(), TrackTiesBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(RollerCart.ACTIVATOR_TIES_BE.get(), TrackTiesBlockEntityRenderer::new);
        event.registerEntityRenderer(RollerCart.TRACK_FOLLOWER.get(), EmptyEntityRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.render.RenderLayers.setRenderLayer(RollerCart.TRACK_TIES.get(), RenderLayer.getCutout());
            net.minecraft.client.render.RenderLayers.setRenderLayer(RollerCart.SWITCH_TIES.get(), RenderLayer.getCutout());
            net.minecraft.client.render.RenderLayers.setRenderLayer(RollerCart.INVISIBLE_TIES.get(), RenderLayer.getCutout());
            net.minecraft.client.render.RenderLayers.setRenderLayer(RollerCart.SHUTTLE_TIES.get(), RenderLayer.getCutout());
            net.minecraft.client.render.RenderLayers.setRenderLayer(RollerCart.DETECTOR_TIES.get(), RenderLayer.getCutout());
            net.minecraft.client.render.RenderLayers.setRenderLayer(RollerCart.ACTIVATOR_TIES.get(), RenderLayer.getCutout());
        });
    }

    @Mod.EventBusSubscriber(modid = RollerCart.MOD_ID, value = Dist.CLIENT)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void registerClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(LiteralArgumentBuilder.<ServerCommandSource>literal("rollercartc")
                    .then(CONFIG.command(LiteralArgumentBuilder.literal("config"),
                            (source, text) -> source.sendFeedback(() -> text, false))));
        }

        @SubscribeEvent
        public static void computeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            if (!CFG_ROTATE_CAMERA.get()) return;
            Entity entity = MinecraftClient.getInstance().getCameraEntity();
            if (entity == null) return;
            Entity cart = entity.getVehicle();
            if (cart == null || !(cart.getVehicle() instanceof TrackFollowerEntity)) {
                return;
            }

            Vector3f angles = TrackCameraTransform.toForgeAngles(event.getCamera().getRotation(), new Vector3f());
            event.setYaw(angles.x());
            event.setPitch(angles.y());
            event.setRoll(angles.z());
        }

        @SubscribeEvent
        public static void onHudRender(RenderGuiEvent.Post event) {
            var client = MinecraftClient.getInstance();
            var world = client.world;

            if (world == null || client.player == null) return;

            var origin = OriginComponent.get(client.player.getMainHandStack());
            if (origin == null) {
                origin = OriginComponent.get(client.player.getOffHandStack());
            }

            if (origin != null && client.crosshairTarget instanceof BlockHitResult hit) {
                var pos = hit.getBlockPos();
                if (world.getBlockState(pos).isAir()) return;

                var hint = Text.translatable("hud.rollercart.cancel").formatted(Formatting.RED);

                if (!pos.equals(origin.pos()) && world.getBlockEntity(pos) instanceof TrackTiesBlockEntity ties && ties.prev() == null) {
                    hint = Text.translatable("hud.rollercart.create_track").formatted(Formatting.GREEN);
                }

                var text = Text.translatable("hud.rollercart.right_click", client.options.useKey.getBoundKeyLocalizedText(), hint);
                DrawContext drawContext = event.getGuiGraphics();
                int w = drawContext.getScaledWindowWidth();
                int h = drawContext.getScaledWindowHeight();

                drawContext.drawCenteredTextWithShadow(client.textRenderer, text, w / 2, (h / 2) + 20, 0xFFFFFFFF);
            }
        }
    }
}
