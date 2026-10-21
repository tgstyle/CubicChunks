package io.github.opencubicchunks.cubicchunks.core.asm.mixin.fixes.client.galacticraft;

import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "micdoodle8.mods.galacticraft.core.TransformerHooks", remap = false)
public abstract class MixinTransformerHooks {

    @Dynamic
    @Inject(method = "getRenderPosY(Lnet/minecraft/entity/Entity;D)D", at = @At("HEAD"), cancellable = true, remap = false)
    private static void getCubicRenderPosY(Entity viewEntity, double regular, CallbackInfoReturnable<Double> cir) {
        if (((ICubicWorld) viewEntity.world).isCubicWorld()) {
            cir.setReturnValue(regular + viewEntity.getEyeHeight());
        }
    }
}
