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
import net.minecraft.item.ItemMap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mixin(ItemMap.class)
public class MixinItemMap {

    @Unique private static final int CUBIC_MAP_SCAN_DEPTH = 64;

    @Unique private int cubicChunks$scanFloor;

    @Redirect(method = "updateMapData",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;getHeightValue(II)I"))
    private int updateMapData_recordScanFloor(Chunk chunk, int x, int z) {
        int h = chunk.getHeightValue(x, z);
        World world = chunk.getWorld();
        if (!((ICubicWorld) world).isCubicWorld()) {
            cubicChunks$scanFloor = 0;
        } else {
            int min = ((ICubicWorld) world).getMinHeight();
            cubicChunks$scanFloor = h <= min ? min : Math.max(min, h - 1 - CUBIC_MAP_SCAN_DEPTH);
        }
        return h;
    }

    @ModifyConstant(method = "updateMapData",
            constant = @Constant(intValue = 1, ordinal = 1),
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;getHeightValue(II)I")))
    private int updateMapData_bedrockTest(int orig) {
        return cubicChunks$scanFloor + 1;
    }

    @ModifyConstant(method = "updateMapData",
            constant = @Constant(intValue = 0, expandZeroConditions = Constant.Condition.GREATER_THAN_ZERO),
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;getHeightValue(II)I"),
                    to = @At(value = "INVOKE", target = "Lcom/google/common/collect/Multiset;add(Ljava/lang/Object;)Z", remap = false)),
            require = 3)
    private int updateMapData_scanFloor(int orig) {
        return cubicChunks$scanFloor;
    }
}
