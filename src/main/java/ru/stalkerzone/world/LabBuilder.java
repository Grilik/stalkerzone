package ru.stalkerzone.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import ru.stalkerzone.block.ModBlocks;
import ru.stalkerzone.item.ModItems;

import java.util.Random;

/**
 * Строит подземную лабораторию по {@link LabLayout}: бункер-вход с шахтой, комнаты с коридорами,
 * обвалы, разбитый свет, оборудование, терминалы с данными и тайники.
 */
public final class LabBuilder {
    private LabBuilder() {}

    private static BlockState b(Block b) { return b.defaultBlockState(); }

    public static boolean loaded(ServerLevel level, LabLayout lay) {
        for (int x = lay.minX() - 8; x <= lay.maxX() + 8; x += 16)
            for (int z = lay.minZ() - 8; z <= lay.maxZ() + 8; z += 16)
                if (!level.hasChunk(x >> 4, z >> 4)) return false;
        return true;
    }

    public static void build(ServerLevel level, LabLayout lay) {
        Painter pt = new Painter(level, 0, 0, 0);
        Random r = new Random(lay.lab.id * 31337L + 5);
        int tier = lay.lab.tier;
        Block wall = tier >= 4 ? Blocks.LIGHT_GRAY_CONCRETE : tier >= 2 ? Blocks.WHITE_CONCRETE : Blocks.STONE_BRICKS;
        Block stripe = tier >= 4 ? Blocks.GRAY_CONCRETE : Blocks.CYAN_TERRACOTTA;
        // массив породы вокруг комплекса, чтобы пещеры не прорезали стены
        for (LabLayout.Room rm : lay.rooms) {
            pt.fill(rm.x0() - 2, rm.y() - 2, rm.z0() - 2, rm.x0() + LabLayout.ROOM + 1, rm.y() + LabLayout.HEIGHT + 1, rm.z0() + LabLayout.ROOM + 1, b(Blocks.DEEPSLATE));
        }
        for (LabLayout.Room rm : lay.rooms) room(pt, rm, wall, stripe, r);
        corridors(pt, lay, wall, r);
        entrance(pt, lay, r);
        if (lay.stairRoom1 != null) shaft(pt, lay.stairRoom1.cx(), lay.stairRoom1.cz(), lay.y2, lay.y2 + LabLayout.HEIGHT, lay.y1 - 1);
        for (LabLayout.Room rm : lay.rooms) decor(pt, level, lay, rm, r);
        for (int i = 0; i < lay.terminals.size(); i++) terminal(pt, lay.terminals.get(i), lay.terminalRooms.get(i));
        if (lay.lab.code.equals("x16") || lay.lab.code.equals("x10") || lay.lab.code.equals("x2")) psiCore(pt, lay);
    }

    private static void room(Painter pt, LabLayout.Room rm, Block wall, Block stripe, Random r) {
        int x0 = rm.x0(), z0 = rm.z0(), x1 = x0 + LabLayout.ROOM - 1, z1 = z0 + LabLayout.ROOM - 1, y = rm.y(), h = LabLayout.HEIGHT;
        pt.fill(x0 - 1, y - 1, z0 - 1, x1 + 1, y + h, z1 + 1, b(wall));
        pt.fill(x0, y, z0, x1, y + h - 1, z1, b(Blocks.AIR));
        // пол плиткой
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
            pt.set(x, y - 1, z, b(((x + z) & 1) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE));
        // полоса на стенах
        for (int x = x0 - 1; x <= x1 + 1; x++) { pt.set(x, y + 1, z0 - 1, b(stripe)); pt.set(x, y + 1, z1 + 1, b(stripe)); }
        for (int z = z0 - 1; z <= z1 + 1; z++) { pt.set(x0 - 1, y + 1, z, b(stripe)); pt.set(x1 + 1, y + 1, z, b(stripe)); }
        // свет: часть ламп разбита
        for (int lx = x0 + 2; lx <= x1 - 2; lx += 6)
            for (int lz = z0 + 2; lz <= z1 - 2; lz += 6)
                pt.set(lx, y + h, lz, b(r.nextDouble() < 0.55 ? Blocks.SEA_LANTERN : Blocks.REDSTONE_LAMP));
        // разруха: трещины, паутина, лужи
        for (int i = 0; i < 10; i++) {
            int x = x0 - 1 + r.nextInt(LabLayout.ROOM + 2), z = z0 - 1 + r.nextInt(LabLayout.ROOM + 2);
            pt.set(x, y + r.nextInt(h), z, b(Blocks.CRACKED_STONE_BRICKS));
        }
        for (int i = 0; i < 4; i++) pt.set(x0 + r.nextInt(LabLayout.ROOM), y + h - 1, z0 + r.nextInt(LabLayout.ROOM), b(Blocks.COBWEB));
        if (r.nextDouble() < 0.3) pt.set(x0 + 1 + r.nextInt(LabLayout.ROOM - 2), y - 1, z0 + 1 + r.nextInt(LabLayout.ROOM - 2), b(Blocks.WATER));
    }

    private static void corridors(Painter pt, LabLayout lay, Block wall, Random r) {
        for (LabLayout.Room a : lay.rooms) {
            LabLayout.Room east = lay.room(a.floor(), a.col() + 1, a.row());
            LabLayout.Room south = lay.room(a.floor(), a.col(), a.row() + 1);
            // остов связности: верхний ряд по горизонтали и все вертикали; прочие проходы иногда обрушены
            if (east != null) corridor(pt, a, east, true, a.row() == 0 || r.nextDouble() > 0.25, wall, r);
            if (south != null) corridor(pt, a, south, false, true, wall, r);
        }
    }

    private static void corridor(Painter pt, LabLayout.Room a, LabLayout.Room b, boolean alongX, boolean open, Block wall, Random r) {
        int y = a.y();
        if (alongX) {
            int xs = a.x0() + LabLayout.ROOM - 1, xe = b.x0(), z = a.cz();
            pt.fill(xs, y - 1, z - 2, xe, y + 3, z + 2, b(wall));
            pt.fill(xs, y, z - 1, xe, y + 2, z + 1, b(Blocks.AIR));
            pt.fill(xs, y - 1, z - 1, xe, y - 1, z + 1, b(Blocks.SMOOTH_STONE));
            if (!open) pt.fill(xs + 2, y, z - 1, xe - 2, y + 2, z + 1, b(r.nextBoolean() ? Blocks.GRAVEL : Blocks.COBBLESTONE));
            else if (r.nextDouble() < 0.4) pt.fill(xs + 2, y, z - 1, xs + 2, y + 2, z + 1, b(Blocks.IRON_BARS));
            if (open && r.nextDouble() < 0.5) pt.set((xs + xe) / 2, y + 2, z, b(Blocks.COBWEB));
        } else {
            int zs = a.z0() + LabLayout.ROOM - 1, ze = b.z0(), x = a.cx();
            pt.fill(x - 2, y - 1, zs, x + 2, y + 3, ze, b(wall));
            pt.fill(x - 1, y, zs, x + 1, y + 2, ze, b(Blocks.AIR));
            pt.fill(x - 1, y - 1, zs, x + 1, y - 1, ze, b(Blocks.SMOOTH_STONE));
            if (open && r.nextDouble() < 0.3) pt.fill(x - 1, y, zs + 2, x + 1, y, zs + 2, b(Blocks.GRAVEL));
        }
        // проёмы в стенах комнат
        if (open) {
            if (alongX) {
                pt.fill(a.x0() + LabLayout.ROOM, a.y(), a.cz() - 1, a.x0() + LabLayout.ROOM, a.y() + 2, a.cz() + 1, b(Blocks.AIR));
                pt.fill(b.x0() - 1, b.y(), b.cz() - 1, b.x0() - 1, b.y() + 2, b.cz() + 1, b(Blocks.AIR));
            } else {
                pt.fill(a.cx() - 1, a.y(), a.z0() + LabLayout.ROOM, a.cx() + 1, a.y() + 2, a.z0() + LabLayout.ROOM, b(Blocks.AIR));
                pt.fill(b.cx() - 1, b.y(), b.z0() - 1, b.cx() + 1, b.y() + 2, b.z0() - 1, b(Blocks.AIR));
            }
        }
    }

    /**
     * Вертикальная шахта 3×3 с лестницей: стены от yWalls до yTop, лестница от пола комнаты yLadder до yTop
     * (в комнате лестница держится на колонне).
     */
    private static void shaft(Painter pt, int cx, int cz, int yLadder, int yWalls, int yTop) {
        pt.fill(cx - 2, yWalls, cz - 2, cx + 2, yTop, cz + 2, b(Blocks.POLISHED_DEEPSLATE));
        pt.fill(cx - 1, yWalls, cz - 1, cx + 1, yTop, cz + 1, b(Blocks.AIR));
        pt.fill(cx, yLadder, cz - 2, cx, yWalls - 1, cz - 2, b(Blocks.POLISHED_DEEPSLATE));
        BlockState ladder = b(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = yLadder; y <= yTop; y++) pt.set(cx, y, cz - 1, ladder);
    }

    /** Бункер на поверхности: ржавые бетонные стены, предупредительные полосы, шахта вниз. */
    private static void entrance(Painter pt, LabLayout lay, Random r) {
        int cx = lay.ex, cz = lay.ez, y = lay.surfaceY;
        pt.fill(cx - 4, y - 1, cz - 4, cx + 4, y - 1, cz + 4, b(Blocks.GRAY_CONCRETE));
        shaft(pt, cx, cz, lay.y1, lay.y1 + LabLayout.HEIGHT, y - 1);
        for (int x = cx - 4; x <= cx + 4; x++) for (int z = cz - 4; z <= cz + 4; z++) {
            boolean edge = Math.abs(x - cx) == 4 || Math.abs(z - cz) == 4;
            if (!edge) {
                pt.fill(x, y, z, x, y + 3, z, b(Blocks.AIR));
                if ((Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2) && !(x == cx && z == cz - 2)) pt.set(x, y, z, b(Blocks.IRON_BARS));
                continue;
            }
            pt.fill(x, y, z, x, y + 3, z, b(r.nextDouble() < 0.2 ? Blocks.CRACKED_STONE_BRICKS : r.nextDouble() < 0.5 ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE));
            pt.set(x, y + 1, z, b(((x + z) & 1) == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE));
        }
        pt.fill(cx - 5, y + 4, cz - 5, cx + 5, y + 4, cz + 5, b(Blocks.SMOOTH_STONE_SLAB));
        // вход с юга, решётки на окнах
        pt.fill(cx - 1, y, cz + 4, cx + 1, y + 2, cz + 4, b(Blocks.AIR));
        pt.set(cx - 4, y + 2, cz, b(Blocks.IRON_BARS));
        pt.set(cx + 4, y + 2, cz, b(Blocks.IRON_BARS));
        // ограждение с колючкой и мусор вокруг
        for (int i = -7; i <= 7; i += 2) {
            pt.set(cx + i, y, cz - 7, b(Blocks.IRON_BARS));
            pt.set(cx - 7, y, cz + i, b(Blocks.IRON_BARS));
            pt.set(cx + 7, y, cz + i, b(Blocks.IRON_BARS));
        }
        pt.set(cx + 6, y, cz + 6, b(ModBlocks.RUSTY_BARREL.get()));
        pt.set(cx - 6, y, cz + 5, b(ModBlocks.RUSTY_BARREL.get()));
        pt.set(cx - 6, y + 1, cz + 5, b(ModBlocks.RUSTY_BARREL.get()));
        pt.set(cx + 2, y + 5, cz - 2, b(Blocks.LIGHTNING_ROD));
        // над шахтой лестница продолжается, чтобы выбраться наверх
        BlockState ladder = b(Blocks.LADDER).setValue(LadderBlock.FACING, Direction.SOUTH);
        pt.set(cx, y, cz - 2, b(Blocks.POLISHED_DEEPSLATE));
        pt.set(cx, y, cz - 1, ladder);
    }

    private static void decor(Painter pt, ServerLevel level, LabLayout lay, LabLayout.Room rm, Random r) {
        int x0 = rm.x0(), z0 = rm.z0(), y = rm.y();
        if (rm == lay.entryRoom || rm == lay.stairRoom1 || rm == lay.stairRoom2) return;
        switch (rm.kind()) {
            case "tanks", "lab" -> {
                // v1.13: 3D-капсулы с образцами, пульт, стол с колбами, шкафы ЭВМ
                net.minecraft.world.level.block.state.BlockState tank = Dressing.prop("prop_lab_tank", net.minecraft.core.Direction.NORTH);
                if (tank != null) {
                    for (int i = 0; i < 3; i++) pt.set(x0 + 2 + i * 3, y, z0 + LabLayout.ROOM - 3, r.nextDouble() < 0.25 ? b(Blocks.IRON_BLOCK) : tank);
                    put3d(pt, x0 + 2, y, z0 + 4, rm.kind().equals("lab") ? "prop_lab_table" : "prop_lab_console", net.minecraft.core.Direction.EAST, Blocks.BREWING_STAND);
                    put3d(pt, x0 + 8, y, z0 + 4, "prop_server_rack", net.minecraft.core.Direction.WEST, Blocks.SMITHING_TABLE);
                    put3d(pt, x0 + 8, y, z0 + 7, "prop_lab_console", net.minecraft.core.Direction.WEST, Blocks.CAULDRON);
                    break;
                }
                // колбы: стеклянные столбы, в части из них вода
                for (int i = 0; i < 3; i++) {
                    int x = x0 + 2 + i * 3, z = z0 + LabLayout.ROOM - 3;
                    pt.set(x, y, z, b(Blocks.IRON_BLOCK));
                    pt.fill(x, y + 1, z, x, y + 3, z, b(r.nextDouble() < 0.4 ? Blocks.GLASS : Blocks.LIGHT_BLUE_STAINED_GLASS));
                    pt.set(x, y + 4, z, b(Blocks.IRON_BLOCK));
                }
                pt.set(x0 + 2, y, z0 + 4, b(Blocks.BREWING_STAND));
                pt.set(x0 + 3, y, z0 + 4, b(Blocks.CAULDRON));
                pt.set(x0 + 8, y, z0 + 4, b(Blocks.SMITHING_TABLE));
            }
            case "office" -> {
                for (int x = x0 + 1; x <= x0 + 9; x += 4) pt.fill(x, y, z0 + 1, x, y + 2, z0 + 1, b(Blocks.BOOKSHELF));
                put3d(pt, x0 + 3, y, z0 + 4, "prop_soviet_table", net.minecraft.core.Direction.EAST, null);
                put3d(pt, x0 + 8, y, z0 + 5, "prop_tv", net.minecraft.core.Direction.WEST, null);
                put3d(pt, x0 + 7, y, z0 + 8, "prop_locker", net.minecraft.core.Direction.NORTH, null);
                pt.set(x0 + 5, y, z0 + 5, b(Blocks.LECTERN));
                pt.set(x0 + 3, y, z0 + 6, b(Blocks.OAK_STAIRS));
            }
            case "generator" -> {
                put3d(pt, x0 + 2, y, z0 + 8, "prop_generator", net.minecraft.core.Direction.EAST, null);
                put3d(pt, x0 + 8, y, z0 + 8, "prop_el_cabinet", net.minecraft.core.Direction.WEST, null);
                pt.fill(x0 + 3, y, z0 + 3, x0 + 7, y + 2, z0 + 6, b(Blocks.IRON_BLOCK));
                pt.fill(x0 + 4, y, z0 + 4, x0 + 6, y + 1, z0 + 5, b(Blocks.BLAST_FURNACE));
                for (int x = x0; x <= x0 + 10; x++) pt.set(x, y + 4, z0 + 1, b(Blocks.CHAIN));
            }
            case "dorm" -> {
                if (Dressing.prop("prop_army_bed", net.minecraft.core.Direction.EAST) != null) {
                    for (int z = z0 + 2; z <= z0 + 8; z += 3) put3d(pt, x0 + 2, y, z, "prop_army_bed", net.minecraft.core.Direction.EAST, null);
                    for (int z = z0 + 2; z <= z0 + 8; z += 3) put3d(pt, x0 + 8, y, z, "prop_locker", net.minecraft.core.Direction.WEST, null);
                }
                for (int x = x0 + 1; x <= x0 + 9; x += 2) if (Dressing.prop("prop_army_bed", net.minecraft.core.Direction.EAST) == null) pt.set(x, y, z0 + 1, b(Blocks.WHITE_WOOL));
                pt.set(x0 + 5, y, z0 + 8, b(Blocks.BARREL));
            }
            default -> {
                // склад: стеллажи и тайники
                for (int x = x0 + 1; x <= x0 + 9; x += 2) pt.fill(x, y, z0 + 8, x, y + 1, z0 + 9, b(Blocks.SPRUCE_PLANKS));
                for (int i = 0; i < 2 + r.nextInt(2); i++) {
                    BlockPos p = new BlockPos(x0 + 2 + r.nextInt(7), y, z0 + 3 + r.nextInt(3));
                    pt.set(p.getX(), p.getY(), p.getZ(), b(Blocks.BARREL));
                    loot(level, p, r, lay.lab.tier);
                }
            }
        }
        // обломки и гильзы
        for (int i = 0; i < 3; i++) pt.set(x0 + r.nextInt(LabLayout.ROOM), y, z0 + 2 + r.nextInt(LabLayout.ROOM - 4), b(r.nextBoolean() ? Blocks.GRAVEL : Blocks.COBBLESTONE_SLAB));
    }

    /** 3D-пропс, если модель есть; иначе запасной блок (или ничего). */
    private static void put3d(Painter pt, int x, int y, int z, String name, net.minecraft.core.Direction f, Block fallback) {
        net.minecraft.world.level.block.state.BlockState s = Dressing.prop(name, f);
        if (s != null) pt.set(x, y, z, s);
        else if (fallback != null) pt.set(x, y, z, b(fallback));
    }

    private static void loot(ServerLevel level, BlockPos p, Random r, int tier) {
        if (!(level.getBlockEntity(p) instanceof BarrelBlockEntity be)) return;
        int slot = 0;
        be.setItem(slot++, new ItemStack(ModItems.MEDS.get(), 1 + r.nextInt(2 + tier)));
        be.setItem(slot++, new ItemStack(ModItems.AMMO.get(), 10 + r.nextInt(30)));
        if (r.nextDouble() < 0.5) be.setItem(slot++, new ItemStack(ModItems.PARTS.get(), 2 + r.nextInt(6)));
        if (r.nextDouble() < 0.15 * tier) be.setItem(slot++, new ItemStack(ModItems.ARTIFACTS[r.nextInt(ModItems.ARTIFACTS.length)].get()));
        if (r.nextDouble() < 0.2) be.setItem(slot, new ItemStack(ModItems.FLASH_DRIVE.get()));
    }

    /** Терминал: стол, монитор (сам терминал), свет. */
    private static void terminal(Painter pt, BlockPos p, LabLayout.Room rm) {
        pt.fill(p.getX() - 1, p.getY(), p.getZ(), p.getX() + 1, p.getY(), p.getZ(), b(Blocks.POLISHED_DIORITE));
        pt.set(p.getX(), p.getY(), p.getZ(), ModBlocks.LAB_TERMINAL.get().defaultBlockState());
        pt.set(p.getX() - 1, p.getY() + 1, p.getZ(), b(Blocks.IRON_BARS));
        pt.set(p.getX() + 1, p.getY() + 1, p.getZ(), b(Blocks.IRON_BARS));
        pt.set(p.getX(), rm.y() + LabLayout.HEIGHT, p.getZ() + 1, b(Blocks.SEA_LANTERN));
    }

    /** Пси-установка в дальней комнате (X-16, X-10, X-2). */
    private static void psiCore(Painter pt, LabLayout lay) {
        LabLayout.Room rm = lay.terminalRooms.isEmpty() ? lay.rooms.get(lay.rooms.size() - 1) : lay.terminalRooms.get(0);
        int cx = rm.cx(), cz = rm.cz() + 1, y = rm.y();
        pt.fill(cx - 1, y, cz - 1, cx + 1, y, cz + 1, b(Blocks.CRYING_OBSIDIAN));
        pt.fill(cx, y + 1, cz, cx, y + 3, cz, b(Blocks.END_ROD));
        pt.set(cx - 1, y + 1, cz, b(Blocks.AMETHYST_CLUSTER));
        pt.set(cx + 1, y + 1, cz, b(Blocks.AMETHYST_CLUSTER));
        pt.set(cx, y + 4, cz, b(Blocks.BEACON));
    }
}
