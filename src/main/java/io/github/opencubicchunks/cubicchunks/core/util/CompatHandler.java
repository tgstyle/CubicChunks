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
package io.github.opencubicchunks.cubicchunks.core.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import io.github.opencubicchunks.cubicchunks.api.world.CubeWatchEvent;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorldServer;
import io.github.opencubicchunks.cubicchunks.core.CubicChunks;
import io.github.opencubicchunks.cubicchunks.core.asm.mixin.ICubicWorldInternal;
import io.github.opencubicchunks.cubicchunks.core.asm.mixin.fixes.common.fakeheight.IASMEventHandler;
import io.github.opencubicchunks.cubicchunks.core.asm.mixin.fixes.common.fakeheight.IEventBus;
import io.github.opencubicchunks.cubicchunks.core.worldgen.generator.vanilla.VanillaCompatibilityGenerator;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.eventhandler.ListenerList;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nonnull;

public class CompatHandler {

    private static final Set<String> IWORLDGENERATOR_FAKE_HEIGHT = ImmutableSet.of(
            "ic2",
            "thaumcraft",
            "fossil",
            "realistictorches",
            "iceandfire",
            "tg", // ThaumicGadgets
            "industrialupgrade"
    );

    private static final Set<String> POPULATE_EVENT_PRE_FAKE_HEIGHT = ImmutableSet.of(
            "reccomplex"
    );

    private static final Set<String> DECORATE_EVENT_FAKE_HEIGHT = ImmutableSet.of(
            "reccomplex"
    );

    private static final Set<String> POST_DECORATE_EVENT_FAKE_HEIGHT = ImmutableSet.of(
            "joshxmas"
    );

    private static final Set<String> FAKE_CHUNK_LOAD = ImmutableSet.of(
            "zerocore",
            "reborncore"
    );
    private static Set<String> vanillaCompatPopulationFakeHeight = ImmutableSet.of(
            "biomesoplenty" // temporary workaround: biomesoplenty fires population events directly instead of going through forge event factory
    );

    private static final Map<String, String> packageToModId = getPackageToModId();

    private static IEventListener[] fakeChunkLoadListeners;

    public static void init() {
        Chunk uninitializedChunk;
        try {
            @SuppressWarnings("JavaReflectionMemberAccess")
            Constructor<Chunk> uninitializedConstructor = Chunk.class.getConstructor(CubicChunks.class);
            uninitializedChunk = uninitializedConstructor.newInstance(new Object[]{null});
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        fakeChunkLoadListeners = getFakeEventListeners(
                new ChunkEvent.Load(uninitializedChunk).getListenerList(),
                MinecraftForge.EVENT_BUS, FAKE_CHUNK_LOAD
        );
        if (Loader.isModLoaded("forgemultipartcbe")) {
            try {
                MinecraftForge.EVENT_BUS.register(new MultipartCubeDescriptions());
            } catch (ReflectiveOperationException e) {
                CubicChunks.LOGGER.error("Failed to initialize Forge Multipart cube descriptions", e);
            }
        }
    }

    public static class MultipartCubeDescriptions {

        private final Class<?> container;
        private final MethodHandle tag;
        private final MethodHandle createFromNBT;
        private final MethodHandle notifyTileChange;
        private final MethodHandle getDescPacket;
        private final MethodHandle sendToPlayer;

        MultipartCubeDescriptions() throws ReflectiveOperationException {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            container = Class.forName("codechicken.multipart.handler.MultipartSaveLoad$TileNBTContainer");
            Class<?> tileMultipart = Class.forName("codechicken.multipart.TileMultipart");
            Class<?> packetHandler = Class.forName("codechicken.multipart.handler.MultipartSPH");
            Class<?> packetCustom = Class.forName("codechicken.lib.packet.PacketCustom");
            tag = lookup.findVirtual(container, "tag", MethodType.methodType(NBTTagCompound.class))
                    .asType(MethodType.methodType(NBTTagCompound.class, TileEntity.class));
            createFromNBT = lookup.findStatic(tileMultipart, "createFromNBT", MethodType.methodType(tileMultipart, NBTTagCompound.class))
                    .asType(MethodType.methodType(TileEntity.class, NBTTagCompound.class));
            notifyTileChange = lookup.findVirtual(tileMultipart, "notifyTileChange", MethodType.methodType(void.class))
                    .asType(MethodType.methodType(void.class, TileEntity.class));
            getDescPacket = lookup.findStatic(packetHandler, "getDescPacket", MethodType.methodType(packetCustom, Chunk.class, Iterator.class))
                    .asType(MethodType.methodType(Object.class, Chunk.class, Iterator.class));
            sendToPlayer = lookup.findVirtual(packetCustom, "sendToPlayer", MethodType.methodType(void.class, EntityPlayer.class))
                    .asType(MethodType.methodType(void.class, Object.class, EntityPlayer.class));
        }

        @SubscribeEvent
        public void onCubeWatch(CubeWatchEvent event) {
            ICube cube = event.getCube();
            if (cube == null || cube.getTileEntityMap().isEmpty()) {
                return;
            }
            try {
                for (TileEntity tile : new ArrayList<>(cube.getTileEntityMap().values())) {
                    if (container.isInstance(tile)) {
                        convert(tile);
                    }
                }
                Chunk column = cube.getColumn();
                Iterator<TileEntity> tiles = cube.getTileEntityMap().values().iterator();
                Object packet = (Object) getDescPacket.invokeExact(column, tiles);
                if (packet != null) {
                    sendToPlayer.invokeExact(packet, (EntityPlayer) event.getPlayer());
                }
            } catch (Throwable throwable) {
                Throwables.throwIfUnchecked(throwable);
                throw new RuntimeException(throwable);
            }
        }

        private void convert(TileEntity saved) throws Throwable {
            NBTTagCompound nbt = (NBTTagCompound) tag.invokeExact(saved);
            if (nbt == null) {
                return;
            }
            World world = saved.getWorld();
            BlockPos pos = saved.getPos();
            TileEntity tile = (TileEntity) createFromNBT.invokeExact(nbt);
            if (tile == null) {
                world.setBlockToAir(pos);
                return;
            }
            tile.validate();
            world.setTileEntity(pos, tile);
            notifyTileChange.invokeExact(tile);
        }
    }

    private static Map<String, String> getPackageToModId() {
        return Collections.unmodifiableMap(Loader.instance().getActiveModList().stream()
                .flatMap(mod -> mod.getOwnedPackages().stream().map(pkg -> new AbstractMap.SimpleEntry<>(pkg, mod.getModId())))
                .collect(Collectors
                        .toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue, (a, b) -> a.equals(b) ? a : a + " or " + b)));
    }

    private static String getPackageName(Class<?> clazz) {
        String name = clazz.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0
                ? ""
                : name.substring(0, dot);
    }

    public static Set<String> getModsForStacktrace(StackTraceElement[] stacktrace) {
        Set<String> mods = new HashSet<>();
        for (StackTraceElement traceElement : stacktrace) {
            try {
                Class<?> cl = Class.forName(traceElement.getClassName());
                if (cl != null) {
                    String modid = packageToModId.get(getPackageName(cl));
                    if (modid != null && !modid.equals("minecraft") && !modid.equals("forge") && !modid.equals("cubicchunks")) {
                        mods.add(modid);
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        return mods;
    }
    
    public static String getModForStacktraceElement(StackTraceElement traceElement) {
        try {
            Class<?> cl = Class.forName(traceElement.getClassName());
            if (cl != null) {
                String modid = packageToModId.get(getPackageName(cl));
                if (modid != null) {
                    return modid;
                }
            }
        } catch (ClassNotFoundException ignored) {
        }
        return "(no mod)";
    }

    public static void beforePopulate(World world, IChunkGenerator vanilla) {
        String modid = packageToModId.get(getPackageName(vanilla.getClass()));
        if (vanillaCompatPopulationFakeHeight.contains(modid)) {
            ((ICubicWorldInternal.Server) world).fakeWorldHeight(256);
        }
    }

    public static void afterPopulate(World world) {
        ((ICubicWorldInternal.Server) world).fakeWorldHeight(0);
    }

    public static void beforeGenerate(World world, IWorldGenerator generator) {
        Class<? extends IWorldGenerator> genClass = generator.getClass();
        String modid = packageToModId.get(getPackageName(genClass));
        if (modid == null) {
            CubicChunks.bigWarning("Found IWorldGenerator %s that doesn't come from any mod! This is most likely a bug.", genClass);
            return;
        }
        if (IWORLDGENERATOR_FAKE_HEIGHT.contains(modid)) {
            ((ICubicWorldInternal.Server) world).fakeWorldHeight(256);
        }
    }

    public static void afterGenerate(World world) {
        ((ICubicWorldInternal.Server) world).fakeWorldHeight(0);
    }

    // this is called from a mixin into ForgeEventFactory
    // it's needed for mods that use events instead of IWorldGenerator
    // performance is not a big issue there, as foring most of those events
    // is a relatively small part of the whole worldgen

    public static boolean postChunkPopulatePreWithFakeWorldHeight(PopulateChunkEvent.Pre event) {
        if (!(MinecraftForge.EVENT_BUS instanceof IEventBus)) {
            MinecraftForge.EVENT_BUS.post(event);
        }
        return postEventPerModFakeHeight(event.getWorld(), event, MinecraftForge.EVENT_BUS, POPULATE_EVENT_PRE_FAKE_HEIGHT);
    }

    public static void carveRiverValleysAhead(World world, int chunkX, int chunkZ) {
        if (StreamsRiverValleys.INSTANCE == null || world.isRemote || world.provider.getDimension() != 0
                || !((ICubicWorld) world).isCubicWorld()
                || !(((ICubicWorldServer) world).getCubeGenerator() instanceof VanillaCompatibilityGenerator)) {
            return;
        }
        StreamsRiverValleys.INSTANCE.carve((WorldServer) world, chunkX, chunkZ);
    }

    public static boolean postBiomeDecorateWithFakeWorldHeight(DecorateBiomeEvent.Decorate event) {
        if (!(MinecraftForge.EVENT_BUS instanceof IEventBus)) {
            MinecraftForge.EVENT_BUS.post(event);
        }
        return postEventPerModFakeHeight(event.getWorld(), event, MinecraftForge.EVENT_BUS, DECORATE_EVENT_FAKE_HEIGHT);
    }

    public static boolean postBiomePostDecorateWithFakeWorldHeight(DecorateBiomeEvent.Post event) {
        if (!(MinecraftForge.EVENT_BUS instanceof IEventBus)) {
            MinecraftForge.EVENT_BUS.post(event);
        }
        return postEventPerModFakeHeight(event.getWorld(), event, MinecraftForge.EVENT_BUS, POST_DECORATE_EVENT_FAKE_HEIGHT);
    }

    private static boolean postEventPerModFakeHeight(World world, Event event, EventBus eventBus, Set<String> modIds) {
        if (!((ICubicWorld) world).isCubicWorld()) {
            return eventBus.post(event);
        }
        return postEvent((ICubicWorldInternal.Server) world, event, eventBus, modIds, w -> w.fakeWorldHeight(256), w -> w.fakeWorldHeight(0));
    }

    public static void onCubeLoad(ChunkEvent.Load load) {
        if (fakeChunkLoadListeners == null || fakeChunkLoadListeners.length == 0) {
            return;
        }
        onChunkLoadImpl(load);
    }

    private static void onChunkLoadImpl(ChunkEvent.Load load) {
        IEventBus bus = (IEventBus) MinecraftForge.EVENT_BUS;
        if (bus.isShutdown()) {
            return;
        }
        int i = -1;
        try {
            for (i = 0; i < fakeChunkLoadListeners.length; i++) {
                IEventListener fakeChunkLoadListener = fakeChunkLoadListeners[i];
                fakeChunkLoadListener.invoke(load);
            }
        } catch (Throwable throwable) {
            bus.getExceptionHandler().handleException(MinecraftForge.EVENT_BUS, load, fakeChunkLoadListeners, i, throwable);
            Throwables.throwIfUnchecked(throwable);
            throw new RuntimeException(throwable);
        }
    }

    private static <T> boolean postEvent(T ctx, Event event, EventBus eventBus, Set<String> modIds, Consumer<T> preEvt, Consumer<T> postEvt) {
        IEventBus forgeEventBus = (IEventBus) eventBus;
        if (forgeEventBus.isShutdown()) {
            return false;
        }
        IEventListener[] listeners = event.getListenerList().getListeners(forgeEventBus.getBusID());
        int index = 0;
        try {
            for (; index < listeners.length; index++) {
                try {
                    IEventListener listener = listeners[index];
                    if (listener instanceof IASMEventHandler) {
                        IASMEventHandler handler = (IASMEventHandler) listener;
                        String modid = handler.getOwner().getModId();
                        if (modIds.contains(modid)) {
                            preEvt.accept(ctx);
                        }
                    }
                    listener.invoke(event);
                } finally {
                    postEvt.accept(ctx);
                }
            }
        } catch (Throwable throwable) {
            forgeEventBus.getExceptionHandler().handleException(eventBus, event, listeners, index, throwable);
            Throwables.throwIfUnchecked(throwable);
            throw new RuntimeException(throwable);
        }
        return event.isCancelable() && event.isCanceled();
    }

    private static <T> IEventListener[] getFakeEventListeners(ListenerList listenerList, EventBus eventBus, Set<String> modIds) {
        if (!(eventBus instanceof IEventBus)) {
            CubicChunks.LOGGER.error("Failed to initialize CompatHandler! No event bus mixin!");
            return null;
        }
        IEventBus forgeEventBus = (IEventBus) eventBus;
        IEventListener[] listeners = listenerList.getListeners(forgeEventBus.getBusID());
        List<IEventListener> newList = new ArrayList<>();
        int index = 0;
        for (; index < listeners.length; index++) {
            IEventListener listener = listeners[index];
            if (listener instanceof IASMEventHandler) {
                IASMEventHandler handler = (IASMEventHandler) listener;
                String modid = handler.getOwner().getModId();
                if (modid.equals("forge")) {
                    // workaround for https://github.com/ZeroNoRyouki/ZeroCore/issues/28
                    String desc = handler.toString();
                    if (desc.startsWith("ASM: ") && desc.contains("@")) {
                        String modClass = desc.split("@")[0].substring("ASM: ".length());
                        try {
                            Class<?> cl = Class.forName(modClass);
                            String newModid = cl.getPackage() == null ? null : getPackageToModId().get(cl.getPackage().getName());
                            if (newModid != null) {
                                modid = newModid;
                            }
                        } catch (ClassNotFoundException t) {
                        }
                    }
                }
                if (modIds.contains(modid)) {
                    newList.add(listener);
                }
            }
        }

        return newList.toArray(new IEventListener[0]);
    }

    private static final class StreamsRiverValleys {

        private static final StreamsRiverValleys INSTANCE = Loader.isModLoaded("streams") ? resolve() : null;

        private final Method surfaceWaterGenerator;
        private final Method invalidWorldTypes;
        private final Method setContains;
        private final Method generate;
        private final Method structures;
        private final Method riverKey;
        private final Method mapGet;
        private final Method optionIsDefined;
        private final Method optionGet;
        private final Method boundingBox;
        private final Field generating;
        private final Class<?> fixedFlowBlock;
        private final Map<World, Set<Long>> carved = new WeakHashMap<>();

        private StreamsRiverValleys(Class<?> rivers) throws ReflectiveOperationException {
            surfaceWaterGenerator = rivers.getMethod("surfaceWaterGenerator");
            invalidWorldTypes = rivers.getMethod("invalidWorldTypes");
            setContains = Class.forName("scala.collection.GenSetLike").getMethod("contains", Object.class);
            generate = rivers.getMethod("generate", WorldServer.class, int.class, int.class, ChunkPrimer.class);
            structures = rivers.getMethod("structures");
            riverKey = rivers.getMethod("riverKey", int.class, int.class);
            mapGet = Class.forName("scala.collection.GenMapLike").getMethod("get", Object.class);
            Class<?> option = Class.forName("scala.Option");
            optionIsDefined = option.getMethod("isDefined");
            optionGet = option.getMethod("get");
            boundingBox = Class.forName("farseek.world.gen.Bounded").getMethod("boundingBox");
            generating = rivers.getSuperclass().getDeclaredField("generating");
            generating.setAccessible(true);
            fixedFlowBlock = Class.forName("streams.block.FixedFlowBlock");
        }

        private static StreamsRiverValleys resolve() {
            try {
                return new StreamsRiverValleys(Class.forName("streams.world.gen.structure.RiverGenerator"));
            } catch (ReflectiveOperationException e) {
                CubicChunks.LOGGER.error("Streams river generator not resolved, river valleys will not be carved ahead of population", e);
                return null;
            }
        }

        void carve(WorldServer world, int chunkX, int chunkZ) {
            Object rivers = call(surfaceWaterGenerator, null);
            if (rivers == null || (boolean) call(setContains, call(invalidWorldTypes, rivers), world.getWorldType())) {
                return;
            }
            Set<Long> done = carved.computeIfAbsent(world, w -> new HashSet<>());
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    carveColumn(world, rivers, done, chunkX + dx, chunkZ + dz);
                }
            }
        }

        private void carveColumn(WorldServer world, Object rivers, Set<Long> done, int x, int z) {
            long key = ChunkPos.asLong(x, z);
            if (done.contains(key)) {
                return;
            }
            Object river = plotted(rivers, x, z);
            if ((river != null && !touches(river, x, z)) || isGenerating(rivers)) {
                return;
            }
            setGenerating(rivers, true);
            try {
                call(generate, rivers, world, x, z, new ColumnPrimer(world.getChunk(x, z), fixedFlowBlock));
            } finally {
                setGenerating(rivers, false);
            }
            river = plotted(rivers, x, z);
            if (river != null && touches(river, x, z)) {
                done.add(key);
            }
        }

        private Object plotted(Object rivers, int x, int z) {
            Object known = call(mapGet, call(structures, rivers), call(riverKey, rivers, x, z));
            return (boolean) call(optionIsDefined, known) ? call(optionGet, known) : null;
        }

        private boolean touches(Object river, int x, int z) {
            if (!(boolean) call(optionIsDefined, river)) {
                return false;
            }
            StructureBoundingBox box = (StructureBoundingBox) call(boundingBox, call(optionGet, river));
            return box.intersectsWith(x << 4, z << 4, (x << 4) + 15, (z << 4) + 15);
        }

        private boolean isGenerating(Object rivers) {
            try {
                return generating.getBoolean(rivers);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        private void setGenerating(Object rivers, boolean value) {
            try {
                generating.setBoolean(rivers, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        private static Object call(Method method, Object target, Object... args) {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwables.throwIfUnchecked(e.getCause());
                throw new RuntimeException(e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class ColumnPrimer extends ChunkPrimer {

        private final Chunk column;
        private final Class<?> keep;

        ColumnPrimer(Chunk column, Class<?> keep) {
            this.column = column;
            this.keep = keep;
        }

        @Override
        @Nonnull
        public IBlockState getBlockState(int x, int y, int z) {
            return column.getBlockState(x, y, z);
        }

        @Override
        public void setBlockState(int x, int y, int z, @Nonnull IBlockState state) {
            if (keep.isInstance(column.getBlockState(x, y, z).getBlock())) {
                return;
            }
            column.getWorld().setBlockState(new BlockPos((column.x << 4) + x, y, (column.z << 4) + z), state, 2 | 16);
        }
    }
}
