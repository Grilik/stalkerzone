package ru.stalkerzone.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.registries.RegistryObject;
import ru.stalkerzone.block.ModBlocks;
import ru.stalkerzone.block.ObjPropBlock;
import ru.stalkerzone.block.VehicleBlock;
import ru.stalkerzone.sim.Node;

import java.util.List;
import java.util.Random;

/**
 * Обстановка из 3D-моделей (OBJ): бочки, баллоны, покрышки, катушки, мусорные баки, электрошкафы,
 * скамейки у костра, фонари у въездов, мешки и бетонные блоки у укреплённых баз, брошенная техника.
 * Ставится один раз вместе с площадкой лагеря и при генерации чанков вне лагерей.
 */
public final class Dressing {
    private Dressing() {}

    private static final Direction[] DIRS = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
    static final String[] CARS = {"veh_zhiguli", "veh_moskvich", "veh_zaporozhets", "veh_niva", "veh_uaz"};
    static final String[] BIG = {"veh_raf", "veh_paz", "veh_zil", "veh_kamaz", "veh_laz", "veh_zil_fire", "veh_tractor", "veh_uaz452", "veh_zil131"};

    /** Состояние 3D-пропса по имени (prop_barrel, veh_uaz…) или null, если такого нет. */
    public static BlockState prop(String name, Direction f) {
        RegistryObject<Block> ro = ModBlocks.PROPS3D.get(name);
        if (ro == null || !ro.isPresent()) return null;
        BlockState s = ro.get().defaultBlockState();
        return s.hasProperty(ObjPropBlock.FACING) ? s.setValue(ObjPropBlock.FACING, f) : s;
    }

    public static BlockState vehicle(String name, Direction f, Random r, int damage) {
        BlockState s = prop(name, f);
        if (s == null || !(s.getBlock() instanceof VehicleBlock)) return s;
        VehicleBlock.Paint[] ps = VehicleBlock.Paint.values();
        // у машин с «фирменным» цветом (скорая, пожарная, автобусы) краска своя, у легковушек любая
        if (name.equals("veh_zhiguli") || name.equals("veh_moskvich") || name.equals("veh_zaporozhets") || name.equals("veh_niva"))
            s = s.setValue(VehicleBlock.PAINT, ps[r.nextInt(ps.length)]);
        return s.setValue(VehicleBlock.DAMAGE, Math.max(0, Math.min(2, damage)));
    }

    static Direction dir(Random r) { return DIRS[r.nextInt(4)]; }

    // ---------------------------------------------------------------- лагерь

    /** Свободна ли клетка лагеря: не на месте построек (с запасом) и не у торговца в центре. */
    private static boolean free(List<Camp.Site> sites, int x, int z, int margin) {
        if (Math.abs(x) <= 4 && Math.abs(z) <= 4) return false;
        if (Math.abs(x - 5) <= 2 && Math.abs(z - 4) <= 2) return false; // костёр
        for (Camp.Site s : sites)
            if (x >= s.bx0() - margin && x <= s.bx1() + margin && z >= s.bz0() - margin && z <= s.bz1() + margin) return false;
        return true;
    }

    private static boolean freeBox(List<Camp.Site> sites, int x, int z, int hx, int hz, int R) {
        for (int dx = -hx; dx <= hx; dx++) for (int dz = -hz; dz <= hz; dz++) {
            if ((x + dx) * (x + dx) + (z + dz) * (z + dz) > (R - 1) * (R - 1)) return false;
            if (!free(sites, x + dx, z + dz, 1)) return false;
        }
        return true;
    }

    private static void put(Painter pt, int x, int z, BlockState s) { if (s != null) pt.set(x, 0, z, s); }

    /**
     * Обставить площадку лагеря. cls: 0 малая точка, 1 посёлок, 2 главная база.
     * fortified — укреплённая (военные, Долг, Монолит): мешки, бетонные блоки, прожекторные фонари у въезда.
     */
    public static void camp(Painter pt, Node n, int cls, int R, Random r, boolean fortified) {
        camp(pt, n, cls, R, r, fortified, -1);
    }

    /** Случайное место на кольце лагеря (доля радиуса от a до b), свободное на ±hx×±hz клеток; null — не нашлось. */
    private static int[] spot(List<Camp.Site> sites, int R, Random r, int hx, int hz, double a, double b) {
        for (int t = 0; t < 40; t++) {
            double ang = r.nextDouble() * Math.PI * 2, d = R * (a + r.nextDouble() * (b - a));
            int x = (int) Math.round(Math.cos(ang) * d), z = (int) Math.round(Math.sin(ang) * d);
            if (freeBox(sites, x, z, hx, hz, R)) return new int[] {x, z};
        }
        return null;
    }

    private static void ring(Painter pt, List<Camp.Site> sites, int R, Random r, String name, int count, int hx, int hz, double a, double b) {
        for (int i = 0; i < count; i++) {
            int[] at = spot(sites, R, r, hx, hz, a, b);
            if (at == null) return;
            // повернуть «лицом» к центру лагеря
            Direction f = Math.abs(at[0]) > Math.abs(at[1]) ? (at[0] > 0 ? Direction.WEST : Direction.EAST) : (at[1] > 0 ? Direction.NORTH : Direction.SOUTH);
            put(pt, at[0], at[1], prop(name, f));
        }
    }

    /**
     * v1.13: у каждой группировки своя обстановка. Одиночки и бандиты — палатки, бревна у костра, поддоны;
     * армия — палатки УСБ, грибки часовых, шлагбаум, «ежи» и спираль колючки, прожекторы, знак «Стой!»;
     * Долг и Монолит — прожекторы, генератор, ящики с патронами; учёные — генератор и знаки радиации.
     */
    public static void camp(Painter pt, Node n, int cls, int R, Random r, boolean fortified, int factionId) {
        List<Camp.Site> sites = Camp.layout(n, cls, n.housing);
        boolean army = factionId == ru.stalkerzone.sim.ZoneScenario.MILITARY;
        boolean sci = factionId == ru.stalkerzone.sim.ZoneScenario.SCIENTISTS;
        // у костра: у укреплённых баз скамейки, у вольных сталкеров брёвна
        put(pt, 5, 6, prop(fortified ? "prop_bench" : "prop_log_seat", Direction.NORTH));
        put(pt, 5, 2, prop(fortified ? "prop_bench" : "prop_log_seat", Direction.SOUTH));
        put(pt, 7, 5, prop(r.nextBoolean() ? "prop_barrel" : "prop_barrel_blue", dir(r)));
        put(pt, 3, 6, prop("prop_stove", Direction.SOUTH));

        // въезды: фонари-«кобры», у укреплённых баз — блоки «Нью-Джерси» змейкой и мешки
        // ворота на четырёх сторонах (там разрыв в столбах ограды)
        for (Direction g : DIRS) {
            int ux = g.getStepX(), uz = g.getStepZ();      // наружу
            int vx = -uz, vz = ux;                          // вдоль ограды
            int bx = ux * (R - 2), bz = uz * (R - 2);
            Direction along = vx != 0 ? Direction.EAST : Direction.SOUTH;
            for (int side : new int[] {-4, 4}) {
                int lx = bx + vx * side, lz = bz + vz * side;
                if (free(sites, lx, lz, 0)) put(pt, lx, lz, prop("prop_street_lamp", g));
            }
            if (fortified && cls >= 1) {
                // змейка: два блока со смещением поперёк проезда, по бокам мешки
                int jx = bx - ux * 3, jz = bz - uz * 3;
                if (free(sites, jx + vx * 2, jz + vz * 2, 0)) put(pt, jx + vx * 2, jz + vz * 2, prop("prop_jersey", along));
                if (free(sites, jx - vx * 2 - ux * 3, jz - vz * 2 - uz * 3, 0)) put(pt, jx - vx * 2 - ux * 3, jz - vz * 2 - uz * 3, prop("prop_jersey", along));
                for (int side : new int[] {-6, 6}) {
                    int sx = bx + vx * side, sz = bz + vz * side;
                    if (free(sites, sx, sz, 0)) put(pt, sx, sz, prop(cls == 2 ? "prop_sandbags_high" : "prop_sandbags", along));
                }
            }
        }

        // по кругу: хозяйство лагеря
        String[] junk = {"prop_barrel", "prop_barrel_green", "prop_barrel_lying", "prop_gas_cylinder", "prop_gas_rack", "prop_tire_stack",
                "prop_tire", "prop_cable_spool", "prop_trash_bin", "prop_el_cabinet", "prop_rubble", "prop_fbs", "prop_barrel_rad"};
        int n0 = cls == 2 ? 26 : cls == 1 ? 16 : 9;
        for (int i = 0, tries = 0; i < n0 && tries < n0 * 12; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = R * (0.45 + r.nextDouble() * 0.5);
            int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
            if (!freeBox(sites, x, z, 1, 1, R)) continue;
            String k = junk[r.nextInt(junk.length)];
            if (k.equals("prop_barrel_rad") && !(n.type == ru.stalkerzone.sim.NodeType.RUINS || r.nextInt(4) == 0)) k = "prop_barrel";
            put(pt, x, z, prop(k, dir(r)));
            // бочки кучкуются
            if (k.startsWith("prop_barrel") && r.nextBoolean() && free(sites, x + 1, z, 0)) put(pt, x + 1, z, prop("prop_barrel", dir(r)));
            i++;
        }
        // жильё и хозяйство по группировкам
        if (army) {
            ring(pt, sites, R, r, "prop_army_tent", cls == 2 ? 3 : cls == 1 ? 2 : 1, 3, 2, 0.45, 0.75);
            ring(pt, sites, R, r, "prop_ammo_crates", 2 + cls, 1, 1, 0.3, 0.8);
            ring(pt, sites, R, r, "prop_generator", 1, 2, 1, 0.4, 0.8);
            ring(pt, sites, R, r, "prop_radio", 1, 1, 1, 0.3, 0.6);
        } else if (fortified) {
            ring(pt, sites, R, r, "prop_ammo_crates", 1 + cls, 1, 1, 0.3, 0.8);
            ring(pt, sites, R, r, "prop_generator", 1, 2, 1, 0.4, 0.8);
            ring(pt, sites, R, r, "prop_pallet_sacks", 1 + cls, 1, 1, 0.3, 0.8);
        } else if (sci) {
            ring(pt, sites, R, r, "prop_generator", 2, 2, 1, 0.4, 0.8);
            ring(pt, sites, R, r, "prop_sign_rad", 3, 0, 0, 0.75, 0.9);
        } else {
            ring(pt, sites, R, r, "prop_tent", cls == 2 ? 4 : cls == 1 ? 3 : 2, 2, 2, 0.4, 0.8);
            ring(pt, sites, R, r, "prop_pallet_sacks", 1 + cls, 1, 1, 0.3, 0.8);
            if (cls >= 1) ring(pt, sites, R, r, "prop_ammo_crates", 1, 1, 1, 0.3, 0.7);
        }
        // въезды укреплённых баз: прожекторы, у армии ещё шлагбаум, грибки, знак, «ежи» и колючка снаружи
        if (fortified || sci) {
            int k = 0;
            for (Direction g : DIRS) {
                int ux = g.getStepX(), uz = g.getStepZ(), vx = -uz, vz = ux;
                int bx = ux * (R - 3), bz = uz * (R - 3);
                int lx = bx + vx * 7, lz = bz + vz * 7;
                if (free(sites, lx, lz, 0)) put(pt, lx, lz, prop("prop_spotlight", g.getOpposite()));
                if (army) {
                    int sx = bx - vx * 5, sz = bz - vz * 5;
                    if (free(sites, sx, sz, 0)) put(pt, sx, sz, prop("prop_watch_booth", g));
                    int tx = bx + vx * 3 + ux, tz = bz + vz * 3 + uz;
                    if (free(sites, tx, tz, 0)) put(pt, tx, tz, prop("prop_sign_stop", g));
                    if (k++ == 0) {
                        // шлагбаум поперёк главного въезда (стрела идёт вдоль ограды)
                        int qx = bx - vx * 2, qz = bz - vz * 2;
                        Direction along = vx > 0 ? Direction.NORTH : vx < 0 ? Direction.SOUTH : vz > 0 ? Direction.EAST : Direction.WEST;
                        if (free(sites, qx, qz, 0)) put(pt, qx, qz, prop("prop_barrier", along));
                    }
                }
            }
            if (army) {
                // снаружи периметра: противотанковые ежи и спираль колючки
                for (int i = 0; i < 10; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    int x = (int) Math.round(Math.cos(a) * (R + 2)), z = (int) Math.round(Math.sin(a) * (R + 2));
                    put(pt, x, z, prop(i % 3 == 0 ? "prop_concertina" : "prop_hedgehog", Math.abs(x) > Math.abs(z) ? Direction.SOUTH : Direction.EAST));
                }
            }
        }
        // трансформатор и бетонная труба на больших базах
        if (cls >= 1) for (String k : new String[] {"prop_transformer", "prop_concrete_pipe"})
            for (int t = 0; t < 30; t++) {
                double a = r.nextDouble() * Math.PI * 2, d = R * (0.55 + r.nextDouble() * 0.35);
                int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
                if (freeBox(sites, x, z, 2, 2, R)) { put(pt, x, z, prop(k, dir(r))); break; }
            }
        // брошенная или служебная техника
        int cars = cls == 2 ? 3 : cls == 1 ? 2 : 1;
        for (int c = 0, t = 0; c < cars && t < 60; t++) {
            double a = r.nextDouble() * Math.PI * 2, d = R * (0.5 + r.nextDouble() * 0.35);
            int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
            boolean big = cls == 2 && c == 0;
            Direction f = r.nextBoolean() ? Direction.EAST : Direction.SOUTH;
            int hx = f == Direction.EAST ? (big ? 5 : 3) : 2, hz = f == Direction.EAST ? 2 : (big ? 5 : 3);
            if (!freeBox(sites, x, z, hx, hz, R)) continue;
            String kind = big ? (fortified ? "veh_btr" : BIG[r.nextInt(BIG.length)]) : fortified && r.nextBoolean() ? "veh_uaz" : CARS[r.nextInt(CARS.length)];
            put(pt, x, z, vehicle(kind, r.nextBoolean() ? f : f.getOpposite(), r, fortified ? r.nextInt(2) : 1 + r.nextInt(2)));
            c++;
        }
    }

    // ---------------------------------------------------------------- по миру

    /** Остов машины в чанке: легковушка, реже автобус или грузовик. Возвращает false, если не нашлось модели. */
    public static boolean chunkCar(ChunkAccess c, int x, int y, int z, Random r, boolean big) {
        String kind = big ? BIG[r.nextInt(BIG.length)] : CARS[r.nextInt(CARS.length)];
        BlockState s = vehicle(kind, r.nextBoolean() ? Direction.EAST : Direction.SOUTH, r, 1 + r.nextInt(2));
        if (s == null) return false;
        if (r.nextBoolean()) s = s.setValue(ObjPropBlock.FACING, s.getValue(ObjPropBlock.FACING).getOpposite());
        ObjPropBlock.placeInChunk(c, new BlockPos(x, y, z), s);
        return true;
    }

    /** 3D-пропс в чанке; false, если модели нет. */
    public static boolean chunkProp(ChunkAccess c, int x, int y, int z, String name, Random r) {
        BlockState s = prop(name, dir(r));
        if (s == null) return false;
        ObjPropBlock.placeInChunk(c, new BlockPos(x, y, z), s);
        return true;
    }
}
