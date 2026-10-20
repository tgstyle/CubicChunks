/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015-2021 OpenCubicChunks
 *  Copyright (c) 2015-2021 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package io.github.opencubicchunks.cubicchunks.core.asm.mixin.core.common;

import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.tileentity.TileEntityStructure;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mixin(TileEntityStructure.class)
public abstract class MixinTileEntityStructure {

    @Shadow protected abstract List<TileEntityStructure> getNearbyCornerBlocks(BlockPos from, BlockPos to);

    @Redirect(method = "detectSize", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/tileentity/TileEntityStructure;getNearbyCornerBlocks(Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/util/math/BlockPos;)Ljava/util/List;"))
    private List<TileEntityStructure> detectSize_getNearbyCornerBlocks(TileEntityStructure self, BlockPos from, BlockPos to) {
        World world = self.getWorld();
        if (!((ICubicWorld) world).isCubicWorld()) {
            return getNearbyCornerBlocks(from, to);
        }
        ICubicWorld cubicWorld = (ICubicWorld) world;
        int y = self.getPos().getY();
        int minY = Math.max(y - 80, cubicWorld.getMinHeight());
        int maxY = Math.min(y + 80, cubicWorld.getMaxHeight() - 1);
        return getNearbyCornerBlocks(new BlockPos(from.getX(), minY, from.getZ()), new BlockPos(to.getX(), maxY, to.getZ()));
    }
}
