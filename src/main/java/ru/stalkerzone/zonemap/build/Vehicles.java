package ru.stalkerzone.zonemap.build;

import java.util.HashMap;
import java.util.Map;

/**
 * Советская техника Зоны (v1.11): брошенные легковушки, автобусы, пожарные машины, грузовики, БТР и трактор.
 * Каждая машина задана чертежом по слоям: слой — высота над землёй, в слое три строки (левый борт, середина,
 * правый борт), строка идёт от задка к передку. Буквы чертежа — части (краска, полублок, скос, стекло, колесо…).
 * Передок смотрит на +x локальных координат; поворачивается вместе со строителем.
 *
 * <p>Повреждения: 0 — ржавая, но целая; 1 — выбиты стёкла, сняты колёса; 2 — сгоревшая.
 */
public final class Vehicles {
    private Vehicles() {}

    /** Чертёж: имя, цвет по умолчанию, второй цвет, слои [y][z: -1,0,+1] строки по x. */
    record Plan(String id, String ru, String color, String second, String[][] layers) {}

    static final Map<String, Plan> PLANS = new HashMap<>();
    static final String[] CARS = {"zhiguli", "moskvich", "zaporozhets", "niva", "uaz"};
    static final String[] ALL = {"zhiguli", "moskvich", "zaporozhets", "niva", "uaz", "raf", "laz", "paz", "zil_fire", "zil", "kamaz", "btr", "tractor"};

    static void plan(String id, String ru, String color, String second, String[]... layers) {
        PLANS.put(id, new Plan(id, ru, color, second, layers));
    }

    static {
        // ВАЗ-2101 «Жигули»: седан 6×3
        plan("zhiguli", "ВАЗ-2101 «Жигули»", "*", "white",
                new String[] {"tWttWL", "tPPPPR", "tWttWL"},
                new String[] {"sGGGss", "sG.Gs<", "sGGGss"},
                new String[] {".sss..", ".sss..", ".sss.."});
        // «Москвич-412»: длиннее капот и багажник, скосы
        plan("moskvich", "«Москвич-412»", "*", "white",
                new String[] {"tWtttWL", "tPPPPPR", "tWtttWL"},
                new String[] {"s>GGGss", "s>G.G<s", "s>GGGss"},
                new String[] {"..sss..", "..sss..", "..sss.."});
        // ЗАЗ-968 «Запорожец»: маленький, мотор сзади (решётки-«уши»)
        plan("zaporozhets", "ЗАЗ-968 «Запорожец»", "*", "white",
                new String[] {"RWtWL", "tPPPP", "RWtWL"},
                new String[] {">GGG<", ">G.G<", ">GGG<"},
                new String[] {".ss..", ".ss..", ".ss.."});
        // ВАЗ-2121 «Нива»: высокая, запаска на задней двери
        plan("niva", "ВАЗ-2121 «Нива»", "*", "white",
                new String[] {"PWPWL", "PPPPR", "PWPWL"},
                new String[] {"GGGGs", "T..Gs", "GGGGs"},
                new String[] {"ssss.", "ssss.", "ssss."});
        // УАЗ-469: оливковый, брезентовый верх
        plan("uaz", "УАЗ-469", "olive", "olive",
                new String[] {"PWPWL", "PPPPR", "PWPWL"},
                new String[] {"CCGGs", "T..Gs", "CCGGs"},
                new String[] {"cccc.", "cccc.", "cccc."});
        // РАФ-2203 «Латвия»: скорая, белая с красной полосой и маячком
        plan("raf", "РАФ-2203 «Латвия» (скорая)", "white", "red",
                new String[] {"XWXXXWL", "PPPPPPR", "XWXXXWL"},
                new String[] {"GGKGGGs", "G.....G", "GGKGGGs"},
                new String[] {"ssssss.", "sssUss.", "ssssss."});
        // ЛАЗ-695: междугородный автобус, кремовый с красной полосой
        plan("laz", "Автобус ЛАЗ-695", "cream", "red",
                new String[] {"PPWPPPPPWPL", "PPPPPPPPPPR", "PPWPPPPPWPL"},
                new String[] {"XXXXXXXXXXs", "P.........G", "XXXX.XXXXXs"},
                new String[] {"GGGGGGGGGGG", "G.........G", "GGGG.GGGGGG"},
                new String[] {"ssssssssss.", "PPPPPPPPPP.", "ssssssssss."});
        // ПАЗ-672: короче, тупоносый
        plan("paz", "Автобус ПАЗ-672", "cream", "orange",
                new String[] {"PWPPPPWPL", "PPPPPPPPR", "PWPPPPWPL"},
                new String[] {"XXXXXXXXs", "P.......G", "XXX.XXXXs"},
                new String[] {"GGGGGGGG.", "G......G.", "GGG.GGGG."},
                new String[] {"sssssss..", "sPPPPPs..", "sssssss.."});
        // ЗИЛ-130 АЦ-40: пожарная автоцистерна, красная с белой полосой, лестница и рукава
        plan("zil_fire", "Пожарная АЦ-40 (ЗИЛ-130)", "red", "white",
                new String[] {"PWWPPPPWL", "PPPPPPPPR", "PWWPPPPWL"},
                new String[] {"XXXXXPGPs", "PPPPPP.Gs", "XXXXXPGPs"},
                new String[] {"PPPPPPGG.", "PPPPPP.G.", "PPPPPPGG."},
                new String[] {"BBBBBsss.", "HHHHH.U..", "BBBBBsss."});
        // ЗИЛ-130 бортовой: синяя кабина, деревянный кузов
        plan("zil", "ЗИЛ-130 бортовой", "blue", "blue",
                new String[] {"DWWDDDPWL", "DDDDDDPPR", "DWWDDDPWL"},
                new String[] {"FFFFFPGPs", "d....P.Gs", "FFFFFPGPs"},
                new String[] {".....PGG.", ".....P.G.", ".....PGG."},
                new String[] {".....ss..", ".....ss..", ".....ss.."});
        // КамАЗ-5320: оранжевая кабина над мотором, кузов под тентом
        plan("kamaz", "КамАЗ-5320", "orange", "orange",
                new String[] {"DWWDDDDPWL", "DDDDDDDPPR", "DWWDDDDPWL"},
                new String[] {"CCCCCCCPGG", "d......PGG", "CCCCCCCPGG"},
                new String[] {"CCCCCCCPGG", ".......P.G", "CCCCCCCPGG"},
                new String[] {"ccccccc.ss", "CCCCCCC.ss", "ccccccc.ss"});
        // БТР-70: восемь колёс, покатый корпус, башенка с КПВТ
        plan("btr", "БТР-70", "olive", "olive",
                new String[] {"PWPWPWPW", "PPPPPPPP", "PWPWPWPW"},
                new String[] {"<PPPPPP>", "<PPPPPP>", "<PPPPPP>"},
                new String[] {".sssssI.", ".ssPsss.", ".sssssI."},
                new String[] {"........", "...PN...", "........"});
        // МТЗ-80 «Беларус»: большие задние колёса, кабина со стеклом
        plan("tractor", "Трактор МТЗ-80 «Беларус»", "red", "white",
                new String[] {"WW.PWL", "PPPPPR", "WW.PWL"},
                new String[] {"WWGGs.", "PG.Gs.", "WWGGs."},
                new String[] {".GGG..", ".G.G..", ".GGG.."},
                new String[] {".sss..", ".sss..", ".sss.."});
    }

    /** Есть ли 3D-модель машины (veh_<вид>); задаётся модом, в превью без игры — нет, и машина собирается из блоков. */
    public static java.util.function.Predicate<String> OBJ = k -> false;

    static final String[] CAR_COLORS = {"red", "white", "blue", "cream", "green", "beige", "orange", "olive"};

    /** Поставить машину kind с центром в (cx, cz) локальных координат b, носом в сторону dir (0 — на +x, 90 — на +z…). */
    public static void vehicle(B b, String kind, int cx, int cz, int dir, String color, int damage) {
        vehicleAt(b, kind, cx, Integer.MIN_VALUE, cz, dir, color, damage);
    }

    /** То же, но на заданной высоте y над точкой b (под мостом, в боксе гаража); MIN_VALUE — на земле в этом месте. */
    public static void vehicleAt(B b, String kind, int cx, int y0, int cz, int dir, String color, int damage) {
        Plan p = PLANS.get(kind);
        if (p == null) return;
        B v = b.sub(cx, cz, dir);
        int dy = y0 == Integer.MIN_VALUE ? 0 : b.oy + y0 - v.oy;
        String main = p.color.equals("*") ? (color != null ? color : CAR_COLORS[v.r.nextInt(CAR_COLORS.length)]) : p.color;
        if (OBJ.test("veh_" + kind)) {
            // одна округлая OBJ-модель с корпусом из невидимых блоков; нос на +x, как у чертежа
            v.put(0, dy, 0, "stalkerzone:veh_" + kind + "[facing=east,paint=" + main + ",damage=" + Math.max(0, Math.min(2, damage)) + "]");
            return;
        }
        int len = p.layers[0][0].length();
        int x0 = -len / 2;
        for (int y = 0; y < p.layers.length; y++) {
            for (int zi = 0; zi < 3; zi++) {
                String row = p.layers[y][zi];
                for (int i = 0; i < row.length(); i++) {
                    String st = part(row.charAt(i), main, p.second, zi - 1, damage, v);
                    if (st == null) continue;
                    v.put(x0 + i, y + dy, zi - 1, st);
                }
            }
        }
        // без колёс машина оседает на диски; у сгоревшей кабина пустая
        if (damage >= 2) for (int i = 0; i < 4; i++) v.put(x0 + v.r.nextInt(len), dy, v.r.nextInt(3) - 1, "minecraft:coal_block");
    }

    /** Блок для буквы чертежа. null — ничего не ставить. */
    static String part(char c, String main, String second, int side, int damage, B v) {
        String P = "stalkerzone:car_paint_" + main, X = "stalkerzone:car_paint_" + second;
        boolean burnt = damage >= 2 && v.chance(0.55);
        String body = burnt ? (v.chance(0.5) ? "minecraft:blackstone" : "minecraft:coal_block") : P;
        String bodySlab = burnt ? "minecraft:blackstone_slab[type=bottom]" : P + "_slab[type=bottom]";
        return switch (c) {
            case 'P' -> body;
            case 'X' -> burnt ? body : X;
            case 'K' -> burnt ? body : v.chance(0.5) ? X : P;
            case 's' -> bodySlab;
            case 'x' -> burnt ? bodySlab : X + "_slab[type=bottom]";
            case 't' -> burnt ? "minecraft:blackstone_slab[type=top]" : P + "_slab[type=top]";
            case '<' -> burnt ? body : P + "_stairs[facing=west,half=bottom,shape=straight]";
            case '>' -> burnt ? body : P + "_stairs[facing=east,half=bottom,shape=straight]";
            case 'G' -> damage >= 1 && v.chance(damage >= 2 ? 0.95 : 0.6) ? null : "stalkerzone:car_glass";
            case 'W' -> damage >= 1 && v.chance(damage >= 2 ? 0.9 : 0.5) ? "minecraft:cobblestone_slab[type=bottom]" : "stalkerzone:car_wheel[axis=z]";
            case 'R' -> damage >= 2 ? "minecraft:blackstone" : "stalkerzone:car_grille";
            case 'L' -> damage >= 2 ? "minecraft:blackstone" : side == 0 ? "stalkerzone:car_grille" : "stalkerzone:car_headlight";
            case 'C' -> damage >= 2 ? null : "minecraft:green_wool";
            case 'c' -> damage >= 2 ? null : "minecraft:green_carpet";
            case 'T' -> "stalkerzone:car_wheel[axis=x]";
            case 'D' -> damage >= 2 ? "minecraft:coal_block" : "minecraft:dark_oak_planks";
            case 'd' -> "minecraft:spruce_slab[type=bottom]";
            case 'F' -> damage >= 2 ? null : "minecraft:spruce_fence";
            case 'B' -> "minecraft:iron_bars";
            case 'H' -> "minecraft:chain[axis=x]";
            case 'U' -> damage >= 1 ? "minecraft:redstone_lamp" : "minecraft:sea_lantern";
            case 'I' -> "minecraft:iron_trapdoor[facing=east,half=bottom,open=false]";
            case 'N' -> "minecraft:lightning_rod[facing=east]";
            default -> null;
        };
    }

    /** Случайная легковушка (для обочин и дворов). */
    public static String randomCar(B b) { return CARS[b.r.nextInt(CARS.length)]; }
}
