package io.github.opencubicchunks.cubicchunks.core.asm.mixin.fixes.common.galacticraft;

import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.core.util.GalacticraftCompat;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "micdoodle8.mods.galacticraft.core.fluid.ThreadFindSeal", remap = false)
public abstract class MixinThreadFindSeal {

    @Unique private int cubicchunks$sealFloor;
    @Unique private boolean cubicchunks$windowOpen;

    @Dynamic
    @Shadow
    private static void checkedClear() {
        throw new AssertionError();
    }

    @Dynamic
    @Redirect(method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;ILjava/util/List;)V",
            at = @At(value = "INVOKE", target = "Lmicdoodle8/mods/galacticraft/core/fluid/ThreadFindSeal;checkedClear()V"), remap = false)
    private void openSealWindow(World world, BlockPos head, int checkCount, List<?> sealers) {
        checkedClear();
        if (((ICubicWorld) world).isCubicWorld()) {
            cubicchunks$sealFloor = GalacticraftCompat.sealFloor(head.getY());
            cubicchunks$windowOpen = true;
            GalacticraftCompat.openSealWindow(world, cubicchunks$sealFloor);
        }
    }

    @Dynamic
    @Inject(method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;ILjava/util/List;)V", at = @At("RETURN"), remap = false)
    private void closeSealWindow(World world, BlockPos head, int checkCount, List<?> sealers, CallbackInfo ci) {
        if (cubicchunks$windowOpen) {
            cubicchunks$windowOpen = false;
            GalacticraftCompat.closeSealWindow();
        }
    }

    @Dynamic
    @ModifyArg(method = "checkedAdd",
            at = @At(value = "INVOKE", target = "Lmicdoodle8/mods/galacticraft/core/fluid/ThreadFindSeal$intBucket;add(I)V"), remap = false)
    private int rebaseAddedKey(int key) {
        return key - cubicchunks$sealFloor;
    }

    @Dynamic
    @ModifyArg(method = {
            "checkedContains(Lmicdoodle8/mods/galacticraft/api/vector/BlockVec3;)Z",
            "checkedContains(Lmicdoodle8/mods/galacticraft/api/vector/BlockVec3;I)Z"
    }, at = @At(value = "INVOKE", target = "Lmicdoodle8/mods/galacticraft/core/fluid/ThreadFindSeal$intBucket;contains(I)Z"), remap = false)
    private int rebaseCheckedKey(int key) {
        return key - cubicchunks$sealFloor;
    }

    @Dynamic
    @ModifyArg(method = "checkedContainsTrace",
            at = @At(value = "INVOKE", target = "Lmicdoodle8/mods/galacticraft/core/fluid/ThreadFindSeal$intBucket;getMSB4shifted(I)I"), remap = false)
    private int rebaseTracedKey(int key) {
        return key - cubicchunks$sealFloor;
    }

    @Dynamic
    @ModifyConstant(method = "checkedContains(Lmicdoodle8/mods/galacticraft/api/vector/BlockVec3;I)Z",
            constant = @Constant(intValue = 0, expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0), remap = false)
    private int sealFloor(int zero) {
        return cubicchunks$sealFloor;
    }

    @Dynamic
    @ModifyConstant(method = "checkedContains(Lmicdoodle8/mods/galacticraft/api/vector/BlockVec3;I)Z",
            constant = @Constant(intValue = 255), remap = false)
    private int sealCeiling(int top) {
        return cubicchunks$sealFloor + top;
    }

    @Dynamic
    @ModifyArg(method = "checkedAll",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/BlockPos;<init>(III)V"), index = 1, remap = false)
    private int unpackCheckedY(int y) {
        return y + cubicchunks$sealFloor;
    }
}
