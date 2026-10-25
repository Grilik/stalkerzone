package ru.stalkerzone.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.stalkerzone.sim.Building;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.zonemap.BlockSink;
import ru.stalkerzone.zonemap.Features;
import ru.stalkerzone.zonemap.build.Structures;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ставит объекты готовой карты (zonemap) в живой мир и перестраивает их при смене уровня развития.
 * Состояния блоков приходят строками («minecraft:spruce_stairs[facing=north]») и разбираются BlockStateParser.
 */
public final class ZoneMapPlacer {
    private ZoneMapPlacer() {}

    static {
        ru.stalkerzone.zonemap.build.Vehicles.OBJ = k -> ru.stalkerzone.block.ModBlocks.PROPS3D.containsKey(k);
    }

    private static final Map<String, BlockState> CACHE = new HashMap<>();

    static BlockState state(String s) {
        return CACHE.computeIfAbsent(s, k -> {
            try {
                return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), k, false).blockState();
            } catch (CommandSyntaxException e) {
                return Blocks.AIR.defaultBlockState(); // неизвестный блок (например, мод не загружен) — пропуск
            }
        });
    }

    /**
     * Блок для конкретной позиции: декор мода может подменяться блоками других модов из compat.json
     * (выбор зависит только от позиции, поэтому перестройка не дёргает уже стоящие блоки).
     */
    static BlockState resolve(int x, int y, int z, String s) {
        BlockState st = state(s);
        if (ru.stalkerzone.compat.CompatConfig.DECOR.isEmpty()) return st;
        int br = s.indexOf('[');
        String id = br < 0 ? s : s.substring(0, br);
        if (!ru.stalkerzone.compat.CompatConfig.DECOR.containsKey(id)) return st;
        long seed = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
        return ru.stalkerzone.compat.CompatConfig.decor(id, st, new java.util.Random(seed));
    }

    /** Приёмник блоков поверх живого мира; запоминает позиции для второго прохода (соединение заборов, решёток). */
    static final class LevelSink implements BlockSink {
        final ServerLevel level;
        final List<BlockPos> placed = new ArrayList<>();

        LevelSink(ServerLevel level) { this.level = level; }

        @Override public void set(int x, int y, int z, String s) {
            BlockPos p = new BlockPos(x, y, z);
            level.setBlock(p, resolve(x, y, z, s), Block.UPDATE_CLIENTS);
            placed.add(p);
        }

        @Override public int ground(int x, int z) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        }

        /** Пересчитать формы блоков по соседям: заборы, решётки, стёкла, ступени соединятся как надо. */
        void finish() {
            for (BlockPos p : placed) {
                BlockState st = level.getBlockState(p);
                if (st.isAir()) continue;
                BlockState up = Block.updateFromNeighbourShapes(st, level, p);
                if (up != st) level.setBlock(p, up, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Построить объект в мире на заданном уровне развития. Перед этим очищает площадку объекта над землёй. */
    public static int build(ServerLevel level, Features.Placement p, int lvl, boolean clear) {
        LevelSink sink = new LevelSink(level);
        if (clear && p.padW() > 0) {
            int oy = sink.ground(p.x(), p.z());
            int hw = Math.max(p.padW(), p.padD()) / 2 + 4;
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int x = p.x() - hw; x <= p.x() + hw; x++)
                for (int z = p.z() - hw; z <= p.z() + hw; z++)
                    for (int y = oy; y <= oy + 52; y++) {
                        BlockPos bp = new BlockPos(x, y, z);
                        BlockState old = level.getBlockState(bp);
                        // убираем только прошлую постройку: землю, камень, песок, воду и деревья не трогаем
                        if (old.isAir() || natural(old)) continue;
                        level.setBlock(bp, air, Block.UPDATE_CLIENTS);
                    }
        }
        Structures.build(p, sink, lvl);
        sink.finish();
        return sink.placed.size();
    }

    // ------------------------------------------------------------------ стройка на глазах (как в MineColonies)

    /** Площадка, где бригада поднимает постройку с уровня на уровень: блоки ставятся снизу вверх, по порции в секунду. */
    static final class Site {
        final ServerLevel level;
        final List<Map.Entry<BlockPos, BlockState>> todo;
        final List<BlockPos> placed = new ArrayList<>();
        final String title;
        int next;
        ru.stalkerzone.npc.StalkerEntity builder;

        Site(ServerLevel level, List<Map.Entry<BlockPos, BlockState>> todo, String title) {
            this.level = level;
            this.todo = todo;
            this.title = title;
        }
    }

    static final List<Site> SITES = new ArrayList<>();
    /** Блоков за секунду на одну площадку. */
    static final int PER_SECOND = 40;

    /** Записывающий приёмник: что поставил бы строитель, без изменения мира. */
    static final class Recorder implements BlockSink {
        final ServerLevel level;
        final Map<BlockPos, String> blocks = new LinkedHashMap<>();
        Recorder(ServerLevel level) { this.level = level; }
        @Override public void set(int x, int y, int z, String s) { blocks.put(new BlockPos(x, y, z), s); }
        @Override public int ground(int x, int z) { return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z); }
    }

    /**
     * Начать стройку: на площадке ставится уровень from, затем бригада постепенно достраивает до уровня to
     * (только отличающиеся блоки, снизу вверх). Возвращает число блоков работы.
     */
    public static int construct(ServerLevel level, Features.Placement p, int from, int to, int faction) {
        build(level, p, from, true);
        Recorder rec = new Recorder(level);
        Structures.build(p, rec, to);
        List<Map.Entry<BlockPos, BlockState>> todo = new ArrayList<>();
        for (Map.Entry<BlockPos, String> e : rec.blocks.entrySet()) {
            BlockState want = resolve(e.getKey().getX(), e.getKey().getY(), e.getKey().getZ(), e.getValue());
            if (!level.getBlockState(e.getKey()).equals(want)) todo.add(Map.entry(e.getKey(), want));
        }
        // снизу вверх; на одном уровне сначала разбор мусора (воздух), потом кладка
        todo.sort(Comparator.<Map.Entry<BlockPos, BlockState>>comparingInt(e -> e.getKey().getY())
                .thenComparing(e -> !e.getValue().isAir())
                .thenComparingInt(e -> e.getKey().getX()).thenComparingInt(e -> e.getKey().getZ()));
        Site s = new Site(level, todo, p.kind() + " " + from + "→" + to);
        ru.stalkerzone.npc.StalkerEntity b = ru.stalkerzone.npc.ModEntities.STALKER.get().create(level);
        if (b != null) {
            BlockPos at = ru.stalkerzone.world.SpawnFinder.groundAt(level, p.x() + 3, p.z() + Math.max(p.padD(), 8) / 2 + 2);
            b.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
            b.configure(faction, ru.stalkerzone.npc.StalkerEntity.ROLE_BUILDER, -1, new BlockPos(p.x(), at.getY(), p.z()),
                    net.minecraft.network.chat.Component.literal("Бригадир стройки"));
            level.addFreshEntity(b);
            s.builder = b;
        }
        SITES.add(s);
        return todo.size();
    }

    /** Раз в секунду: бригады кладут следующую порцию блоков. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        for (Iterator<Site> it = SITES.iterator(); it.hasNext(); ) {
            Site s = it.next();
            int end = Math.min(s.todo.size(), s.next + PER_SECOND);
            for (; s.next < end; s.next++) {
                Map.Entry<BlockPos, BlockState> e = s.todo.get(s.next);
                if (!s.level.hasChunkAt(e.getKey())) continue;
                s.level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
                s.placed.add(e.getKey());
            }
            if (s.builder != null && s.builder.isAlive() && s.next < s.todo.size()) {
                BlockPos at = s.todo.get(s.next).getKey();
                s.builder.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 1.0);
            }
            if (s.next >= s.todo.size()) {
                LevelSink fix = new LevelSink(s.level);
                fix.placed.addAll(s.placed);
                fix.finish();
                if (s.builder != null) s.builder.discard();
                it.remove();
            }
        }
    }

    /** Сколько площадок сейчас строится. */
    public static int activeSites() { return SITES.size(); }

    /** Природный блок (рельеф и растительность), который расчистка площадки не должна срывать. */
    static boolean natural(BlockState st) {
        return st.is(net.minecraft.tags.BlockTags.DIRT) || st.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || st.is(net.minecraft.tags.BlockTags.SAND) || st.is(net.minecraft.tags.BlockTags.LOGS)
                || st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(Blocks.GRAVEL) || st.is(Blocks.WATER)
                || st.is(Blocks.CLAY) || st.is(Blocks.SNOW) || st.is(Blocks.GRASS) || st.is(Blocks.TALL_GRASS) || st.is(Blocks.FERN);
    }

    /**
     * Видимый уровень точки по зданиям симуляции (как в MineColonies: что вложено — то и стоит):
     * средний уровень построек, минус один при сильных повреждениях.
     */
    public static int levelForNode(Simulation sim, int node) {
        List<Building> bs = sim.buildingsAt(node);
        if (bs.isEmpty()) return 0;
        double lv = 0, hp = 0;
        for (Building b : bs) { lv += b.level; hp += b.hp; }
        lv /= bs.size(); hp /= bs.size();
        int v = (int) Math.round(lv);
        if (hp < 40) v--;
        return Math.max(0, Math.min(Building.MAX_LEVEL, v));
    }
}
