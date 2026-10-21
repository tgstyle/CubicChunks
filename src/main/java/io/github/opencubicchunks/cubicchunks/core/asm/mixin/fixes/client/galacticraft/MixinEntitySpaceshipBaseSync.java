package io.github.opencubicchunks.cubicchunks.core.asm.mixin.fixes.client.galacticraft;

import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = {
        "micdoodle8.mods.galacticraft.api.prefab.entity.EntitySpaceshipBase",
        "micdoodle8.mods.galacticraft.core.entities.EntityLanderBase"
}, remap = false)
public abstract class MixinEntitySpaceshipBaseSync extends Entity {

    public MixinEntitySpaceshipBaseSync(World world) {
        super(world);
    }

    @Dynamic
    @Redirect(method = {"setPositionAndRotationDirect", "func_180426_a"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isBlockLoaded(Lnet/minecraft/util/math/BlockPos;)Z", remap = true),
            remap = false)
    private boolean isTargetCubeLoaded(World world, BlockPos pos, double x, double y, double z, float yaw, float pitch,
            int posRotationIncrements, boolean teleport) {
        return world.isBlockLoaded(((ICubicWorld) world).isCubicWorld() ? new BlockPos(x, y, z) : pos);
    }

    @Dynamic
    @Redirect(method = {"setPositionAndRotationDirect", "func_180426_a"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isBlockLoaded(Lnet/minecraft/util/math/BlockPos;Z)Z", remap = true),
            remap = false)
    private boolean isOldCubeLoaded(World world, BlockPos pos, boolean allowEmpty) {
        if (!((ICubicWorld) world).isCubicWorld()) {
            return world.isBlockLoaded(pos, allowEmpty);
        }
        return world.isBlockLoaded(new BlockPos(pos.getX(), Coords.cubeToMinBlock(chunkCoordY), pos.getZ()), allowEmpty);
    }

    @Dynamic
    @ModifyConstant(method = {"getBrightnessForRender", "func_70070_b"}, constant = @Constant(doubleValue = 255.0D), remap = false)
    private double getBrightnessCeiling(double top) {
        return ((ICubicWorld) world).isCubicWorld() ? Double.MAX_VALUE : top;
    }
}
