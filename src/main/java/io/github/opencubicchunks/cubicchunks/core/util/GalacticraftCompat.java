package io.github.opencubicchunks.cubicchunks.core.util;

import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.core.world.cube.BlankCube;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class GalacticraftCompat {

    private static final int SEAL_WINDOW = 256;
    private static final int SUN_RUN = 5;
    @Nullable private static World sealWorld;
    private static int sealFloor;

    private GalacticraftCompat() {
        throw new Error();
    }

    public static int sealFloor(int headY) {
        return headY - SEAL_WINDOW / 2;
    }

    public static void openSealWindow(World world, int floor) {
        sealWorld = world;
        sealFloor = floor;
    }

    public static void closeSealWindow() {
        sealWorld = null;
    }

    @Nullable
    public static IBlockState loadedBlockState(World world, int x, int y, int z) {
        if (outsideSealSearch(world, y)) {
            return null;
        }
        ICube cube = ((ICubicWorld) world).getCubeCache().getLoadedCube(Coords.blockToCube(x), Coords.blockToCube(y), Coords.blockToCube(z));
        if (cube == null || cube instanceof BlankCube) {
            return Blocks.BEDROCK.getDefaultState();
        }
        return cube.getBlockState(x, y, z);
    }

    @Nullable
    public static IBlockState blockState(World world, int x, int y, int z) {
        if (outsideSealSearch(world, y)) {
            return null;
        }
        return world.getBlockState(new BlockPos(x, y, z));
    }

    public static int lightValue(IBlockState state, World world, BlockPos pos) {
        if (Sunlight.BRIGHT_AIR == null || state.getBlock() != Sunlight.BRIGHT_AIR || !((ICubicWorld) world).isCubicWorld()) {
            return state.getLightValue(world, pos);
        }
        int y = pos.getY();
        int z = pos.getZ();
        int run = 0;
        while (run < SUN_RUN) {
            int x = pos.getX() - 1 - run;
            ICube cube = ((ICubicWorld) world).getCubeCache().getLoadedCube(Coords.blockToCube(x), Coords.blockToCube(y), Coords.blockToCube(z));
            if (cube != null && !(cube.getBlockState(x, y, z).getBlock() instanceof BlockAir)) {
                break;
            }
            run++;
        }
        return Math.min(4 + run * 2, 14);
    }

    private static boolean outsideSealSearch(World world, int y) {
        ICubicWorld cubicWorld = (ICubicWorld) world;
        if (y < cubicWorld.getMinHeight() || y >= cubicWorld.getMaxHeight()) {
            return true;
        }
        return world == sealWorld && (y < sealFloor || y >= sealFloor + SEAL_WINDOW);
    }

    public static boolean blocksGenerator(World world, IWorldGenerator generator) {
        if (Planets.CONFIG == null || Planets.PLANET == null || Planets.STATION == null) {
            return false;
        }
        if (Planets.STATION.isInstance(world.provider)) {
            return true;
        }
        if (!Planets.PLANET.isInstance(world.provider) || Planets.flag("enableOtherModsFeatures")) {
            return false;
        }
        if (Planets.RDPL_PLANET != null && Planets.RDPL_PLANET.isInstance(world.provider)
            && (Planets.RDPL_WORLDGEN != null && Planets.RDPL_WORLDGEN.isInstance(generator)
            || Planets.RDPL_SWAPS != null && Planets.RDPL_SWAPS.isInstance(generator))) {
            return false;
        }
        for (Class<?> allowed : Planets.whitelist()) {
            if (allowed.isInstance(generator)) {
                return false;
            }
        }
        return true;
    }

    private static final class Sunlight {

        @Nullable private static final Block BRIGHT_AIR = Block.getBlockFromName("galacticraftcore:bright_air");
    }

    private static final class Planets {

        @Nullable private static final Class<?> CONFIG = find("micdoodle8.mods.galacticraft.core.util.ConfigManagerCore");
        @Nullable private static final Class<?> PLANET = CONFIG == null ? null : find("micdoodle8.mods.galacticraft.api.world.IGalacticraftWorldProvider");
        @Nullable private static final Class<?> STATION = CONFIG == null ? null : find("micdoodle8.mods.galacticraft.core.dimension.WorldProviderSpaceStation");
        @Nullable private static final Class<?> RDPL_PLANET = find("mctmods.resourcedatapackloader.util.compat.GcWorldProvider");
        @Nullable private static final Class<?> RDPL_WORLDGEN = find("mctmods.resourcedatapackloader.content.worldgen.ContentWorldgen");
        @Nullable private static final Class<?> RDPL_SWAPS = find("mctmods.resourcedatapackloader.content.ContentHardness$Swaps");
        @Nullable private static List<Class<?>> whitelist;

        @Nullable
        private static Class<?> find(String name) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        private static boolean flag(String name) {
            try {
                return Objects.requireNonNull(CONFIG).getField(name).getBoolean(null);
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }

        private static List<Class<?>> whitelist() {
            if (whitelist == null) {
                List<String> names = new ArrayList<>();
                if (flag("whitelistCoFHCoreGen")) {
                    names.add("cofh.cofhworld.init.WorldHandler");
                }
                names.add("bloodasp.galacticgreg.GT_Worldgenerator_Space");
                names.add("com.rwtema.denseores.WorldGenOres");
                names.add("appeng.worldgen.MeteoriteWorldGen");
                if (flag("enableThaumCraftNodes")) {
                    names.add("thaumcraft.common.lib.world.ThaumcraftWorldGenerator");
                }
                List<Class<?>> found = new ArrayList<>();
                for (String name : names) {
                    Class<?> each = find(name);
                    if (each != null) {
                        found.add(each);
                    }
                }
                whitelist = found;
            }
            return whitelist;
        }
    }
}
