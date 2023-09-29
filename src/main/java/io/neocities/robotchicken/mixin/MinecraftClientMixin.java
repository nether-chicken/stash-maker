package io.neocities.robotchicken.mixin;

import io.neocities.robotchicken.modules.*;
import net.minecraft.client.*;
import net.minecraft.util.hit.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = MinecraftClient.class, priority = 1001)
public abstract class MinecraftClientMixin {

    @Redirect(method = "doItemUse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;crosshairTarget:Lnet/minecraft/util/hit/HitResult;", ordinal = 1))
    private HitResult doItemUseMinecraftClientCrosshairTargetProxy(MinecraftClient client) {
        if (AutoEatModule.INSTANCE.eating) {
            return new HitResult(client.player.getPos()) {
                @Override
                public Type getType() {
                    return Type.MISS;
                }
            };
        } else {
            return client.crosshairTarget;
        }
    }
}
