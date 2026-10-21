package io.github.opencubicchunks.cubicchunks.core.asm.mixin.fixes.common.galacticraft;

import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.core.util.GalacticraftCompat;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "micdoodle8.mods.galacticraft.api.vector.BlockVec3", remap = false)
public abstract class MixinBlockVec3 {

    @Dynamic @Shadow(remap = false) public int x;
    @Dynamic @Shadow(remap = false) public int y;
    @Dynamic @Shadow(remap = false) public int z;

    @Dynamic
    @Inject(method = "getBlockStateSafe_noChunkLoad(Lnet/minecraft/world/World;)Lnet/minecraft/block/state/IBlockState;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void getLoadedCubicBlockState(World world, CallbackInfoReturnable<IBlockState> cir) {
        if (((ICubicWorld) world).isCubicWorld()) {
            cir.setReturnValue(GalacticraftCompat.loadedBlockState(world, x, y, z));
        }
    }

    @Dynamic
    @Inject(method = "getBlockState(Lnet/minecraft/world/World;)Lnet/minecraft/block/state/IBlockState;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void getCubicBlockState(World world, CallbackInfoReturnable<IBlockState> cir) {
        if (((ICubicWorld) world).isCubicWorld()) {
            cir.setReturnValue(GalacticraftCompat.blockState(world, x, y, z));
        }
    }
}
