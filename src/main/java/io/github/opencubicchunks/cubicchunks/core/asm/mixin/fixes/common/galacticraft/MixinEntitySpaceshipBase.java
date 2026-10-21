package io.github.opencubicchunks.cubicchunks.core.asm.mixin.fixes.common.galacticraft;

import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "micdoodle8.mods.galacticraft.api.prefab.entity.EntitySpaceshipBase", remap = false)
public abstract class MixinEntitySpaceshipBase extends Entity {

    public MixinEntitySpaceshipBase(World world) {
        super(world);
    }

    @Dynamic
    @ModifyConstant(method = {"onUpdate", "func_70071_h_"}, constant = @Constant(doubleValue = 0.0D, ordinal = 0), remap = false)
    private double getMinShipY(double zero) {
        ICubicWorld cubicWorld = (ICubicWorld) world;
        return cubicWorld.isCubicWorld() ? cubicWorld.getMinHeight() : zero;
    }
}
