package io.github.minazukisora.rollercart.mixin.client;

import io.github.minazukisora.rollercart.entity.TrackFollowerEntity;
import io.github.minazukisora.rollercart.sound.LiftingTrackFollowerSoundInstance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "playSpawnSound", at = @At("TAIL"))
    public void rollercart$playSpawnSound(Entity entity, CallbackInfo ci) {
        if(entity instanceof TrackFollowerEntity)
            client.getSoundManager().play(new LiftingTrackFollowerSoundInstance((TrackFollowerEntity) entity));
    }

}
