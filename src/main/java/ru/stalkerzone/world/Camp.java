package ru.stalkerzone.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import ru.stalkerzone.sim.BuildingType;
import ru.stalkerzone.sim.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Лагеря: площадка, постройки по типам и уровням, повреждения по состоянию.
 * Размер зависит от значимости: 2 главная база фракции, 1 посёлок, 0 аванпост или руины.
 * Каждая постройка симуляции (Штаб, Казарма...) может состоять из нескольких мест в лагере (например, несколько домов).
 */
public final class Camp {
    private Camp() {}

    /** Место постройки в лагере: центр относительно центра лагеря, размеры, сторона входа. */
    public record Site(BuildingType type, int cx, int cz, int w, int d, Direction door, String landmark, int landmarkHeight) {
        public Site(BuildingType type, int cx, int cz, int w, int d, Direction door) {
            this(type, cx, cz, w, d, door, null, 0);
        }
        int x0() { return cx - w / 2; }
        int x1() { return x0() + w - 1; }
        int z0() { return cz - d / 2; }
        int z1() { return z0() + d - 1; }
        int height() {
            if (landmarkHeight > 0) return landmarkHeight;
            return switch (type) {
                case TOWER -> 12;
                case WORKSHOP -> 7;
                default -> 6;
            };
        }
        /** Область перерисовки с запасом на свес крыши. */
        int bx0() { return landmark != null ? x0() - 3 : type == BuildingType.TOWER ? cx - 3 : x0() - 1; }
        int bx1() { return landmark != null ? x1() + 3 : type == BuildingType.TOWER ? cx + 3 : x1() + 1; }
        int bz0() { return landmark != null ? z0() - 3 : type == BuildingType.TOWER ? cz - 3 : z0() - 1; }
        int bz1() { return landmark != null ? z1() + 4 : type == BuildingType.TOWER ? cz + 4 : z1() + 1; }
    }

    public static int radius(int cls) {
        return switch (cls) {
            case 2 -> 34;
            case 1 -> 22;
            default -> 15;
        };
    }

    // ---------------------------------------------------------------- планировка

    public static List<Site> layout(Node n, int cls, int housing) {
        List<Site> out = new ArrayList<>();
        if (cls == 2) {
            out.add(new Site(BuildingType.HQ, 0, -15, 9, 7, Direction.SOUTH));
            out.add(new Site(BuildingType.WORKSHOP, -23, -3, 11, 7, Direction.EAST));
            out.add(new Site(BuildingType.CLINIC, 21, -5, 7, 7, Direction.WEST));
            int houses = Math.max(3, Math.min(9, housing / 9));
            for (int i = 0; i < houses; i++) {
                double a = Math.PI * 2 * i / houses + Math.PI / 2 + 0.35;
                int hx = (int) Math.round(Math.cos(a) * (i % 2 == 0 ? 20 : 26)), hz = (int) Math.round(Math.sin(a) * (i % 2 == 0 ? 20 : 26));
                if (hz < -3) continue; // север занят штабом, мастерской и медпунктом
                out.add(new Site(BuildingType.BARRACKS, hx, hz, 5, 5, hz > 0 ? Direction.NORTH : Direction.SOUTH));
            }
            int tx = 29;
            out.add(new Site(BuildingType.TOWER, tx, tx, 5, 5, Direction.SOUTH));
            out.add(new Site(BuildingType.TOWER, -tx, tx, 5, 5, Direction.SOUTH));
            out.add(new Site(BuildingType.TOWER, tx, -tx, 5, 5, Direction.SOUTH));
            out.add(new Site(BuildingType.TOWER, -tx, -tx, 5, 5, Direction.SOUTH));
        } else if (cls == 1) {
            out.add(new Site(BuildingType.WORKSHOP, 0, -13, 7, 5, Direction.SOUTH));
            for (int i = 0; i < 3; i++) {
                double a = Math.PI * 2 * i / 3 + 0.9;
                out.add(new Site(BuildingType.BARRACKS, (int) Math.round(Math.cos(a) * 12), (int) Math.round(Math.sin(a) * 12) + 4, 5, 5, Direction.SOUTH));
            }
            out.add(new Site(BuildingType.TOWER, 16, -14, 5, 5, Direction.SOUTH));
        } else {
            out.add(new Site(BuildingType.BARRACKS, 0, 7, 7, 5, Direction.NORTH));
            out.add(new Site(BuildingType.TOWER, -9, -3, 5, 5, Direction.SOUTH));
            out.add(new Site(BuildingType.TOWER, 9, -3, 5, 5, Direction.SOUTH));
        }
        Landmarks.override(n, cls, out);
        return out;
    }

    public static List<Site> sitesOf(List<Site> all, BuildingType t) {
        List<Site> out = new ArrayList<>();
        for (Site s : all) if (s.type() == t) out.add(s);
        return out;
    }

    // ---------------------------------------------------------------- палитры

    private static Block accent(int f) {
        return switch (f) {
            case 0 -> Blocks.BROWN_CONCRETE;
            case 1 -> Blocks.RED_CONCRETE;
            case 2 -> Blocks.LIME_CONCRETE;
            case 3 -> Blocks.BLACK_CONCRETE;
            case 4 -> Blocks.WHITE_CONCRETE;
            case 5 -> Blocks.GREEN_CONCRETE;
            case 6 -> Blocks.GRAY_CONCRETE;
            default -> Blocks.LIGHT_GRAY_CONCRETE;
        };
    }

    private static Block wool(int f) {
        return switch (f) {
            case 0 -> Blocks.BROWN_WOOL;
            case 1 -> Blocks.RED_WOOL;
            case 2 -> Blocks.LIME_WOOL;
            case 3 -> Blocks.BLACK_WOOL;
            case 4 -> Blocks.WHITE_WOOL;
            case 5 -> Blocks.GREEN_WOOL;
            case 6 -> Blocks.GRAY_WOOL;
            default -> Blocks.LIGHT_GRAY_WOOL;
        };
    }

    public static Block bannerOf(int f) { return banner(f); }

    public static void bedAt(Painter pt, int x, int z, Direction facing) { bed(pt, x, z, facing); }

    private static Block banner(int f) {
        return switch (f) {
            case 0 -> Blocks.BROWN_BANNER;
            case 1 -> Blocks.RED_BANNER;
            case 2 -> Blocks.LIME_BANNER;
            case 3 -> Blocks.BLACK_BANNER;
            case 4 -> Blocks.WHITE_BANNER;
            case 5 -> Blocks.GREEN_BANNER;
            case 6 -> Blocks.GRAY_BANNER;
            default -> Blocks.LIGHT_GRAY_BANNER;
        };
    }

    /** Стены: уровень 1 дерево, уровень 2 материал фракции, уровень 3 бетон. */
    private static Block wallFor(int level, int f) {
        if (level <= 1) return Blocks.SPRUCE_PLANKS;
        if (level == 2) {
            return switch (f) {
                case 1, 5 -> Blocks.STONE_BRICKS;
                case 3 -> Blocks.COBBLESTONE;
                case 4 -> Blocks.SMOOTH_QUARTZ;
                case 6 -> Blocks.POLISHED_BLACKSTONE_BRICKS;
                default -> Blocks.SPRUCE_PLANKS;
            };
        }
        return switch (f) {
            case 4 -> Blocks.QUARTZ_BLOCK;
            case 6 -> Blocks.POLISHED_BLACKSTONE_BRICKS;
            case 3 -> Blocks.STONE_BRICKS;
            default -> Blocks.LIGHT_GRAY_CONCRETE;
        };
    }

    private static Block roofFor(int level, int f) {
        if (level <= 1) return wool(f);
        if (level == 2) return f == 4 ? Blocks.SMOOTH_QUARTZ : f == 6 ? Blocks.BLACKSTONE : Blocks.RAW_IRON_BLOCK;
        return f == 6 ? Blocks.BLACKSTONE : Blocks.SMOOTH_STONE;
    }

    // ---------------------------------------------------------------- площадка и декор

    public static void buildGround(ServerLevel level, Node n, int cls, int factionId, BlockPos ground, boolean trader) {
        Random r = new Random(n.id * 7727L + 13);
        int R = radius(cls);
        Painter pt = new Painter(level, ground.getX(), ground.getY(), ground.getZ());
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                if (dx * dx + dz * dz > R * R) continue;
                pt.fill(dx, -5, dz, dx, -2, dz, Blocks.DIRT.defaultBlockState());
                int roll = r.nextInt(10);
                pt.set(dx, -1, dz, (roll == 0 ? Blocks.COARSE_DIRT : roll == 1 ? Blocks.GRAVEL : Blocks.GRASS_BLOCK).defaultBlockState());
                pt.fill(dx, 0, dz, dx, 13, dz, air);
            }
        }
        // костёр и бочки в стороне от центра
        pt.set(5, -1, 4, Blocks.COBBLESTONE.defaultBlockState());
        pt.set(5, 0, 4, Blocks.CAMPFIRE.defaultBlockState());
        pt.set(8, 0, 3, Blocks.HAY_BLOCK.defaultBlockState());
        // прилавок торговца в центре: сам торговец стоит за ним в точке (0, 0)
        if (trader) {
            for (int x = -2; x <= 2; x++) pt.set(x, 0, 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
            for (int sx : new int[] {-2, 2}) for (int sz : new int[] {-1, 1}) pt.fill(sx, 0, sz, sx, 2, sz, Blocks.SPRUCE_FENCE.defaultBlockState());
            for (int x = -2; x <= 2; x++) for (int z = -1; z <= 1; z++) pt.set(x, 3, z, wool(factionId).defaultBlockState());
            pt.set(-2, 0, -1, Blocks.BARREL.defaultBlockState());
            pt.set(2, 0, -1, Blocks.BARREL.defaultBlockState());
        }
        if (cls == 2 || cls == 0) posts(pt, R - 2, cls == 2 ? 3 : 2);
        if (cls == 0) {
            for (int x = -8; x <= 8; x++) {
                if (Math.abs(x) <= 2) continue;
                pt.set(x, 0, -10, Blocks.COBBLESTONE.defaultBlockState());
                pt.set(x, 1, -10, Blocks.COBBLESTONE_WALL.defaultBlockState());
            }
        }
        // 3D-обстановка: бочки, баллоны, фонари у въездов, мешки у укреплённых баз, техника
        boolean fortified = factionId == ru.stalkerzone.sim.ZoneScenario.MILITARY || factionId == ru.stalkerzone.sim.ZoneScenario.DUTY
                || factionId == ru.stalkerzone.sim.ZoneScenario.MONOLITH;
        Dressing.camp(pt, n, cls, R, r, fortified, factionId);
        // обломки стен: руины и просто для атмосферы вокруг
        int walls = n.type == ru.stalkerzone.sim.NodeType.RUINS ? 9 : 3;
        for (int i = 0; i < walls; i++) {
            int wx = r.nextInt(2 * R - 8) - R + 4, wz = r.nextInt(2 * R - 8) - R + 4;
            if (wx * wx + wz * wz > (R - 3) * (R - 3) || (Math.abs(wx) < 6 && Math.abs(wz) < 6)) continue;
            boolean alongX = r.nextBoolean();
            int len = 3 + r.nextInt(5);
            for (int k = 0; k < len; k++) {
                int h = 1 + r.nextInt(3);
                Block b = r.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE : r.nextBoolean() ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS;
                pt.fill(alongX ? wx + k : wx, 0, alongX ? wz : wz + k, alongX ? wx + k : wx, h - 1, alongX ? wz : wz + k, b.defaultBlockState());
            }
        }
    }

    private static void posts(Painter pt, int radius, int gap) {
        int steps = (int) (2 * Math.PI * radius);
        BlockState post = Blocks.COBBLESTONE_WALL.defaultBlockState();
        for (int i = 0; i < steps; i += 2) {
            double a = 2 * Math.PI * i / steps;
            int x = (int) Math.round(Math.cos(a) * radius), z = (int) Math.round(Math.sin(a) * radius);
            if (Math.abs(x) <= gap || Math.abs(z) <= gap) continue;
            pt.fill(x, 0, z, x, 1, z, post);
        }
    }

    // ---------------------------------------------------------------- постройки

    /** Перерисовать все места постройки данного типа: очистить, нарисовать целую и применить повреждения. */
    public static void renderBuilding(ServerLevel level, Node n, int cls, int housing, int factionId, BlockPos ground,
                                      BuildingType type, int lvl, int cond, boolean repairing) {
        Painter pt = new Painter(level, ground.getX(), ground.getY(), ground.getZ());
        for (Site s : sitesOf(layout(n, cls, housing), type)) {
            clear(pt, s);
            paint(pt, s, Math.max(1, lvl), factionId);
            int c = lvl == 0 ? 3 : cond;
            // карточка 041: на узлах с постройками уровня (existingBuildings) повреждения и щебень не наносим вовсе
            if (c > 0 && !n.existingBuildings) damage(pt, s, c, n.id * 31L + s.cx() * 17L + s.cz() * 13L + c);
            if (repairing && c > 0) scaffolding(pt, s);
        }
    }

    /** Леса вокруг ремонтируемой постройки: стойки по углам и вдоль длинных стен. */
    private static void scaffolding(Painter pt, Site s) {
        BlockState sc = Blocks.SCAFFOLDING.defaultBlockState();
        int top = Math.min(s.height(), 8);
        int x0 = s.x0() - 1, x1 = s.x1() + 1, z0 = s.z0() - 1, z1 = s.z1() + 1;
        for (int x = x0; x <= x1; x += 3) {
            if (pt.get(x, 0, z0).isAir()) pt.fill(x, 0, z0, x, top, z0, sc);
            if (pt.get(x, 0, z1).isAir()) pt.fill(x, 0, z1, x, top, z1, sc);
        }
        for (int z = z0; z <= z1; z += 3) {
            if (pt.get(x0, 0, z).isAir()) pt.fill(x0, 0, z, x0, top, z, sc);
            if (pt.get(x1, 0, z).isAir()) pt.fill(x1, 0, z, x1, top, z, sc);
        }
    }

    public static boolean allLoaded(ServerLevel level, Node n, int cls, int housing, BlockPos ground, BuildingType type) {
        for (Site s : sitesOf(layout(n, cls, housing), type)) {
            for (int x = s.bx0(); x <= s.bx1(); x += 8)
                for (int z = s.bz0(); z <= s.bz1(); z += 8)
                    if (!level.hasChunk((ground.getX() + x) >> 4, (ground.getZ() + z) >> 4)) return false;
        }
        return true;
    }

    private static void clear(Painter pt, Site s) {
        BlockState air = Blocks.AIR.defaultBlockState();
        pt.fill(s.bx0(), 0, s.bz0(), s.bx1(), s.height() + 1, s.bz1(), air);
        pt.fill(s.bx0(), -1, s.bz0(), s.bx1(), -1, s.bz1(), Blocks.GRASS_BLOCK.defaultBlockState());
    }

    private static void paint(Painter pt, Site s, int level, int f) {
        if (s.landmark() != null) {
            Landmarks.paint(pt, s, level, f);
            return;
        }
        switch (s.type()) {
            case TOWER -> tower(pt, s, level, f);
            case WORKSHOP -> { shell(pt, s, level, f, 4); workshopInterior(pt, s); }
            case CLINIC -> { shell(pt, s, level, f, 3); clinicInterior(pt, s, f); }
            case HQ -> { shell(pt, s, level, f, 4); hqInterior(pt, s, level, f); }
            default -> { shell(pt, s, level, f, 3); barracksInterior(pt, s); }
        }
    }

    /** Коробка здания: пол, стены, цветная полоса, окна, проём, крыша, мешки с песком и лампы по уровню. */
    private static void shell(Painter pt, Site s, int level, int f, int h) {
        int x0 = s.x0(), x1 = s.x1(), z0 = s.z0(), z1 = s.z1();
        BlockState wall = wallFor(level, f).defaultBlockState();
        BlockState roof = roofFor(level, f).defaultBlockState();
        BlockState floor = (level >= 3 ? Blocks.POLISHED_ANDESITE : Blocks.SPRUCE_PLANKS).defaultBlockState();
        pt.fill(x0, -1, z0, x1, -1, z1, floor);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (!edge) continue;
                pt.fill(x, 0, z, x, h - 1, z, wall);
                if (level >= 2) pt.set(x, h - 1, z, accent(f).defaultBlockState());
            }
        }
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) pt.set(x, h, z, roof);
        int xm = (x0 + x1) / 2, zm = (z0 + z1) / 2;
        int px = s.door() == Direction.EAST ? x1 : s.door() == Direction.WEST ? x0 : xm;
        int pz = s.door() == Direction.SOUTH ? z1 : s.door() == Direction.NORTH ? z0 : zm;
        int doorW = s.type() == BuildingType.WORKSHOP ? 1 : 0;
        for (int k = -doorW; k <= doorW; k++) {
            int ax = s.door() == Direction.EAST || s.door() == Direction.WEST ? px : px + k;
            int az = s.door() == Direction.EAST || s.door() == Direction.WEST ? pz + k : pz;
            pt.fill(ax, 0, az, ax, 1, az, Blocks.AIR.defaultBlockState());
        }
        BlockState glass = (level >= 2 ? Blocks.GLASS : Blocks.AIR).defaultBlockState();
        if (level >= 2) {
            pt.set(x0, 1, zm, glass);
            pt.set(x1, 1, zm, glass);
            pt.set(xm, 1, z0 == pz ? z1 : z0, glass);
        }
        pt.set(x0 + 1, 0, z0 + 1, Blocks.TORCH.defaultBlockState());
        // уровень 2 и выше: мешки с песком у входа и лампа
        if (level >= 2) {
            BlockState sand = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            int ox = s.door().getStepX(), oz = s.door().getStepZ();
            int bx = px + ox * 2, bz = pz + oz * 2;
            int sx = oz != 0 ? 2 : 0, sz = ox != 0 ? 2 : 0;
            pt.fill(bx - sx, 0, bz - sz, bx - sx, 0, bz - sz, sand);
            pt.fill(bx + sx, 0, bz + sz, bx + sx, 1, bz + sz, sand);
            pt.set(xm, h - 1, zm, Blocks.LANTERN.defaultBlockState());
        }
        // уровень 3: прожекторы на углах крыши и антенна
        if (level >= 3) {
            for (int x : new int[] {x0 - 1, x1 + 1}) for (int z : new int[] {z0 - 1, z1 + 1}) pt.set(x, h + 1, z, Blocks.GLOWSTONE.defaultBlockState());
            pt.fill(xm, h + 1, zm, xm, h + 3, zm, Blocks.LIGHTNING_ROD.defaultBlockState());
        }
    }

    private static void bed(Painter pt, int x, int z, Direction facing) {
        BlockState foot = Blocks.BROWN_BED.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT);
        BlockState head = foot.setValue(BedBlock.PART, BedPart.HEAD);
        pt.set(x, 0, z, foot);
        pt.set(x + facing.getStepX(), 0, z + facing.getStepZ(), head);
    }

    private static void barracksInterior(Painter pt, Site s) {
        bed(pt, s.x0() + 1, s.z0() + 1, Direction.SOUTH);
        if (s.w() >= 7) bed(pt, s.x1() - 1, s.z0() + 1, Direction.SOUTH);
        pt.set(s.x1() - 1, 0, s.z1() - 1, Blocks.BARREL.defaultBlockState());
    }

    private static void workshopInterior(Painter pt, Site s) {
        int x0 = s.x0(), z0 = s.z0();
        pt.set(x0 + 1, 0, z0 + 1, Blocks.ANVIL.defaultBlockState());
        pt.set(x0 + 3, 0, z0 + 1, Blocks.BLAST_FURNACE.defaultBlockState());
        pt.set(x0 + 5, 0, z0 + 1, Blocks.SMITHING_TABLE.defaultBlockState());
        pt.set(x0 + 1, 0, z0 + 3, Blocks.GRINDSTONE.defaultBlockState());
        pt.set(Math.min(s.x1() - 1, x0 + 6), 0, z0 + 3, Blocks.CRAFTING_TABLE.defaultBlockState());
    }

    private static void clinicInterior(Painter pt, Site s, int f) {
        bed(pt, s.x0() + 1, s.z0() + 1, Direction.SOUTH);
        bed(pt, s.x1() - 1, s.z0() + 1, Direction.SOUTH);
        pt.set(s.cx(), 0, s.z0() + 1, Blocks.BARREL.defaultBlockState());
        // красный крест на стене над входом
        int cx = s.cx();
        int zWall = s.door() == Direction.SOUTH ? s.z1() : s.z0();
        int xWall = s.door() == Direction.EAST ? s.x1() : s.x0();
        if (s.door() == Direction.EAST || s.door() == Direction.WEST) {
            int zc = s.cz();
            pt.set(xWall, 2, zc - 2, Blocks.RED_CONCRETE.defaultBlockState());
            pt.set(xWall, 2, zc + 2, Blocks.RED_CONCRETE.defaultBlockState());
        } else {
            pt.set(cx - 2, 2, zWall, Blocks.RED_CONCRETE.defaultBlockState());
            pt.set(cx + 2, 2, zWall, Blocks.RED_CONCRETE.defaultBlockState());
        }
    }

    private static void hqInterior(Painter pt, Site s, int level, int f) {
        pt.set(s.cx(), 0, s.z0() + 1, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        pt.set(s.x0() + 1, 0, s.z0() + 1, Blocks.LECTERN.defaultBlockState());
        pt.set(s.x1() - 1, 0, s.z0() + 1, Blocks.BARREL.defaultBlockState());
        if (level >= 2) {
            int px = s.cx(), pz = s.z1() + 2;
            pt.fill(px - 3, 0, pz, px - 3, 4, pz, Blocks.COBBLESTONE_WALL.defaultBlockState());
            pt.set(px - 3, 5, pz, banner(f).defaultBlockState());
        }
    }

    /** Вышка: столбы, площадка, перила, лестница; со второго уровня ещё крыша и мешки, с третьего прожектор. */
    private static void tower(Painter pt, Site s, int level, int f) {
        int cx = s.cx(), cz = s.cz();
        BlockState leg = (level >= 3 ? Blocks.STONE_BRICKS : level == 2 ? Blocks.SPRUCE_LOG : Blocks.SPRUCE_LOG).defaultBlockState();
        BlockState deck = (level >= 3 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.SPRUCE_PLANKS).defaultBlockState();
        for (int dx : new int[] {-2, 2}) for (int dz : new int[] {-2, 2}) pt.fill(cx + dx, -1, cz + dz, cx + dx, 7, cz + dz, leg);
        pt.fill(cx - 2, 7, cz - 2, cx + 2, 7, cz + 2, deck);
        BlockState rail = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState sand = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean perimeter = Math.abs(x) == 2 || Math.abs(z) == 2;
                boolean exit = x >= 1 && z >= 1;
                if (perimeter && !exit) pt.set(cx + x, 8, cz + z, level >= 2 && (z == -2 || x == -2) ? sand : rail);
            }
        }
        if (level >= 2) {
            for (int dx : new int[] {-2, 2}) for (int dz : new int[] {-2, 2}) pt.fill(cx + dx, 8, cz + dz, cx + dx, 10, cz + dz, leg);
            pt.fill(cx - 3, 11, cz - 3, cx + 3, 11, cz + 3, roofFor(level, f).defaultBlockState());
            pt.set(cx, 10, cz, Blocks.LANTERN.defaultBlockState());
        } else {
            pt.set(cx, 8, cz, Blocks.TORCH.defaultBlockState());
        }
        if (level >= 3) {
            pt.set(cx - 2, 10, cz - 2, Blocks.GLOWSTONE.defaultBlockState());
            pt.set(cx + 2, 10, cz - 2, Blocks.GLOWSTONE.defaultBlockState());
            pt.fill(cx, 12, cz, cx, 14, cz, Blocks.LIGHTNING_ROD.defaultBlockState());
        }
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = 0; y <= 8; y++) pt.set(cx + 2, y, cz + 3, ladder);
    }

    // ---------------------------------------------------------------- повреждения

    /** Состояния: 1 потрескавшаяся, 2 пробитая, 3 обрушенная. Блоки выбиваются, на их месте щебень, где-то тлеют угли. */
    private static void damage(Painter pt, Site s, int cond, long seed) {
        Random r = new Random(seed);
        BlockState air = Blocks.AIR.defaultBlockState();
        int h = s.height();
        double removeWall = cond == 1 ? 0.05 : cond == 2 ? 0.28 : 0.82;
        double removeRoof = cond == 1 ? 0.04 : cond == 2 ? 0.45 : 0.9;
        for (int x = s.bx0(); x <= s.bx1(); x++) {
            for (int z = s.bz0(); z <= s.bz1(); z++) {
                for (int y = 0; y <= h + 4; y++) {
                    BlockState st = pt.get(x, y, z);
                    if (st.isAir()) continue;
                    Block b = st.getBlock();
                    boolean top = y >= h - 1;
                    if (b == Blocks.GLASS) {
                        if (r.nextDouble() < (cond == 1 ? 0.5 : 0.95)) pt.set(x, y, z, air);
                        continue;
                    }
                    if (cond == 1 && (b == Blocks.STONE_BRICKS) && r.nextDouble() < 0.4) {
                        pt.set(x, y, z, (r.nextBoolean() ? Blocks.CRACKED_STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS).defaultBlockState());
                        continue;
                    }
                    if (cond == 1 && b == Blocks.SPRUCE_PLANKS && r.nextDouble() < 0.12) {
                        pt.set(x, y, z, Blocks.OAK_PLANKS.defaultBlockState());
                        continue;
                    }
                    if (cond >= 2 && b == Blocks.LIGHT_GRAY_CONCRETE && r.nextDouble() < 0.3) {
                        pt.set(x, y, z, Blocks.ANDESITE.defaultBlockState());
                        continue;
                    }
                    // каркас вышки и ограда рушатся реже, чем стены
                    double p = top ? removeRoof : removeWall;
                    if (y == 0) p *= 0.6;
                    if (r.nextDouble() < p && !(b instanceof BedBlock)) pt.set(x, y, z, air);
                }
            }
        }
        // щебень на полу и на земле вокруг: только гравий и грубая земля — одиночные булыжники
        // (COBBLESTONE/MOSSY_COBBLESTONE) и уголь в плейтесте выглядели как метки точек (карточка 041)
        double rubble = cond == 1 ? 0.04 : cond == 2 ? 0.18 : 0.42;
        Block[] junk = {Blocks.GRAVEL, Blocks.COARSE_DIRT};
        for (int x = s.bx0(); x <= s.bx1(); x++) {
            for (int z = s.bz0(); z <= s.bz1(); z++) {
                if (pt.get(x, 0, z).isAir() && !pt.get(x, -1, z).isAir() && r.nextDouble() < rubble) {
                    pt.set(x, 0, z, junk[r.nextInt(junk.length)].defaultBlockState());
                }
            }
        }
        if (cond >= 2) {
            // тлеющие очаги: адский камень с огнём не гаснет и не расползается
            int spots = cond == 2 ? 2 : 4;
            for (int i = 0; i < spots; i++) {
                int x = s.x0() + r.nextInt(Math.max(1, s.w())), z = s.z0() + r.nextInt(Math.max(1, s.d()));
                pt.set(x, -1, z, Blocks.NETHERRACK.defaultBlockState());
                pt.set(x, 0, z, Blocks.FIRE.defaultBlockState());
            }
        }
        if (cond == 3) {
            // торчащая арматура
            for (int i = 0; i < 4; i++) {
                int x = s.x0() + r.nextInt(Math.max(1, s.w())), z = s.z0() + r.nextInt(Math.max(1, s.d()));
                pt.fill(x, 0, z, x, 1 + r.nextInt(3), z, Blocks.IRON_BARS.defaultBlockState());
            }
        }
        if (cond == 1) {
            for (int i = 0; i < 4; i++) {
                int x = s.x0() + 1 + r.nextInt(Math.max(1, s.w() - 2)), z = s.z0() + 1 + r.nextInt(Math.max(1, s.d() - 2));
                if (pt.get(x, h - 1, z).isAir()) pt.set(x, h - 1, z, Blocks.COBWEB.defaultBlockState());
            }
        }
    }

    // ---------------------------------------------------------------- декор на постройках уровня (карточка 009)

    /**
     * Ставит декор на существующих постройках уровня: костёр, флаг фракции, лежанки, ящики.
     * Всё ставится на поверхность: ищем первый твёрдый блок вниз от точки (до 6 блоков), при пустоте
     * под объектом подставляем опору из материала покрытия. Внутрь стен ничего не ставим, одиночных
     * блоков-меток не остаётся (карточка 027). Вызывается при первом подходе игрока и при смене
     * владельца (меняется цвет флага).
     */
    public static void placeLevelDecor(ServerLevel level, Node n, com.google.gson.JsonObject pts, int worldCX, int worldCZ) {
        // координаты точек — уже в блоках мира, поэтому рисуем без смещения
        Painter pt = new Painter(level, 0, 0, 0);

        // Флаг фракции: столб на 3 блока и баннер сверху
        if (n.owner >= 0) {
            int[] flag = LevelPoints.readVec(pts, "flag");
            if (flag != null && flag.length >= 3) {
                BlockPos base = surface(level, pt, flag[0], flag[1], flag[2]);
                if (base != null) {
                    pt.fill(base.getX(), base.getY(), base.getZ(), base.getX(), base.getY() + 2, base.getZ(),
                            Blocks.OAK_FENCE.defaultBlockState());
                    pt.set(base.getX(), base.getY() + 3, base.getZ(), bannerOf(n.owner).defaultBlockState());
                }
            }
        }

        // Костёр
        int[] campfire = LevelPoints.readVec(pts, "campfire");
        if (campfire != null && campfire.length >= 3) {
            BlockPos base = surface(level, pt, campfire[0], campfire[1], campfire[2]);
            if (base != null) pt.set(base.getX(), base.getY(), base.getZ(),
                    Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, true));
        }

        // Лежанки (сеном)
        for (int[] bed : LevelPoints.readVecs(pts, "beds")) {
            if (bed.length < 3) continue;
            BlockPos base = surface(level, pt, bed[0], bed[1], bed[2]);
            if (base != null) pt.set(base.getX(), base.getY(), base.getZ(), Blocks.HAY_BLOCK.defaultBlockState());
        }

        // Ящики и бочки вокруг костра
        if (campfire != null && campfire.length >= 3) {
            Random r = new Random(n.id * 7727L + 13);
            int fx = campfire[0], fz = campfire[2];
            for (int i = 0; i < 3; i++) {
                int bx = fx + r.nextInt(5) - 2, bz = fz + r.nextInt(5) - 2;
                if (Math.abs(bx - fx) < 2 && Math.abs(bz - fz) < 2) continue;
                BlockPos base = surface(level, pt, bx, campfire[1], bz);
                if (base != null) pt.set(base.getX(), base.getY(), base.getZ(),
                        r.nextBoolean() ? Blocks.BARREL.defaultBlockState() : Blocks.CHEST.defaultBlockState());
            }
        }
    }

    /**
     * Место объекта на поверхности: ищем первый твёрдый блок вниз от точки (до 6 блоков), при пустоте
     * под объектом ставим опору из материала покрытия. Если точка внутри стены/рельефа — поднимаем до
     * свободного места, но не выше 8 блоков. null, если свободного места не нашлось (внутрь не ставим).
     */
    private static BlockPos surface(ServerLevel level, Painter pt, int x, int y, int z) {
        int top = y;
        for (int k = 0; k < 8 && solid(pt, x, top, z); k++) top++;
        if (solid(pt, x, top, z)) return null;
        int base = Integer.MIN_VALUE;
        for (int dy = 1; dy <= 6; dy++) if (solid(pt, x, top - dy, z)) { base = top - dy; break; }
        if (base == Integer.MIN_VALUE) {
            // Y точек уровня абсолютна (tools/xray/kordon_points.py: y = xr_y + yoff), но если под точкой
            // нет земли — берём реальную высоту рельефа, а не строим висящую колонку
            int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            return solid(pt, x, ground, z) ? null : new BlockPos(x, ground, z);
        }
        BlockState cover = pt.get(x, base, z);
        if (cover.isAir() || cover.canBeReplaced() || !cover.getFluidState().isEmpty()) cover = Blocks.DIRT.defaultBlockState();
        // опора из материала покрытия, если под объектом пусто
        for (int yy = base + 1; yy < top; yy++) pt.set(x, yy, z, cover);
        return new BlockPos(x, top, z);
    }

    /** Твёрдый ли блок: не воздух, не заменяемый и не жидкость. */
    private static boolean solid(Painter pt, int x, int y, int z) {
        BlockState s = pt.get(x, y, z);
        return !s.isAir() && !s.canBeReplaced() && s.getFluidState().isEmpty();
    }
}
