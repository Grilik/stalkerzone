package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.Random;

/**
 * Помощник строителя: локальные координаты объекта (x вправо, z вперёд/юг, y от земли в точке объекта),
 * поворот на 0/90/180/270 по часовой, поворот свойств блоков (facing, axis, форма рельсов).
 */
public final class B {
    public final BlockSink s;
    public final int ox, oy, oz, rot;
    public final Random r;
    /** Уровень развития объекта: 0 руины, 1 лагерь, 2 обжитой, 3 укреплённый. */
    public final int lvl;
    /** Износ: доля выбитых и состаренных блоков выше земли (зависит от уровня). */
    public double wear;
    private final long seed;

    public B(BlockSink s, int ox, int oz, int rot, long seed) { this(s, ox, oz, rot, seed, 2); }

    public B(BlockSink s, int ox, int oz, int rot, long seed, int lvl) {
        this.s = s;
        this.seed = seed;
        this.lvl = Math.max(0, Math.min(3, lvl));
        this.wear = switch (this.lvl) { case 0 -> 0.26; case 1 -> 0.07; default -> 0.0; };
        this.ox = ox;
        this.oz = oz;
        this.rot = ((rot % 360) + 360) % 360;
        this.oy = s.ground(ox, oz);
        this.r = new Random(seed);
    }

    public int wx(int lx, int lz) {
        return switch (rot) { case 90 -> ox - lz; case 180 -> ox - lx; case 270 -> ox + lz; default -> ox + lx; };
    }

    public int wz(int lx, int lz) {
        return switch (rot) { case 90 -> oz + lx; case 180 -> oz - lz; case 270 -> oz - lx; default -> oz + lz; };
    }

    private static final String[] DIRS = {"north", "east", "south", "west"};

    public String turn(String st) {
        if (rot == 0) return st;
        int steps = rot / 90;
        int i = st.indexOf("facing=");
        if (i >= 0) {
            int j = i + 7, k = j;
            while (k < st.length() && Character.isLetter(st.charAt(k))) k++;
            String d = st.substring(j, k);
            for (int n = 0; n < 4; n++) if (DIRS[n].equals(d)) { st = st.substring(0, j) + DIRS[(n + steps) % 4] + st.substring(k); break; }
        }
        if (steps % 2 == 1) {
            if (st.contains("axis=x")) st = st.replace("axis=x", "axis=z");
            else if (st.contains("axis=z")) st = st.replace("axis=z", "axis=x");
            if (st.contains("shape=east_west")) st = st.replace("shape=east_west", "shape=north_south");
            else if (st.contains("shape=north_south")) st = st.replace("shape=north_south", "shape=east_west");
            if (st.contains("north=true") || st.contains("east=true") || st.contains("south=true") || st.contains("west=true")) {
                boolean n = st.contains("north=true"), e = st.contains("east=true"), so = st.contains("south=true"), w = st.contains("west=true");
                // соединения решёток и заборов поворачиваем по кругу
                st = st.replaceAll("(north|east|south|west)=(true|false)", "");
                boolean[] v = {n, e, so, w};
                StringBuilder sb = new StringBuilder();
                for (int q = 0; q < 4; q++) sb.append(DIRS[(q + steps) % 4]).append('=').append(v[q]).append(',');
                st = st.replace("[,", "[").replace(",,", ",");
                int br = st.indexOf('[');
                if (br >= 0) st = st.substring(0, br + 1) + sb + st.substring(br + 1);
                st = st.replace(",]", "]").replace(",,", ",");
            }
        }
        return st;
    }

    /** Блок на высоте ly над землёй точки объекта. */
    public void set(int lx, int ly, int lz, String st) {
        int x = wx(lx, lz), z = wz(lx, lz), y = oy + ly;
        if (wear > 0 && ly >= 1) { st = age(st, x, y, z); if (st == null) return; }
        s.set(x, y, z, turn(st));
    }

    /** Без износа (обломки, заплатки, то, что поставлено нарочно). */
    public void put(int lx, int ly, int lz, String st) {
        s.set(wx(lx, lz), oy + ly, wz(lx, lz), turn(st));
    }

    /** Новый строитель для вложенного объекта с тем же уровнем. */
    public B sub(int lx, int lz, int drot) {
        B c = new B(s, wx(lx, lz), wz(lx, lz), rot + drot, r.nextLong(), lvl);
        c.wear = wear;
        return c;
    }

    /** Детерминированный шум 0..1 по мировой позиции. */
    public double h(int x, int y, int z) {
        long v = x * 73856093L ^ y * 19349663L ^ z * 83492791L ^ seed * 0x9E3779B97F4A7C15L;
        v ^= v >>> 29; v *= 0xBF58476D1CE4E5B9L; v ^= v >>> 32;
        return (v >>> 11) * 0x1.0p-53;
    }

    /** Состарить блок: дыра (null), старый вариант или как есть. Дыры кучкуются, как настоящие проломы. */
    private String age(String st, int x, int y, int z) {
        if (st.equals(AIR)) return st;
        String id = st.contains("[") ? st.substring(0, st.indexOf('[')) : st;
        double cl = h(x >> 2, y >> 1, z >> 2), v = h(x, y, z);
        if (id.endsWith("glass_pane") || id.endsWith(":glass")) return v < wear * 2.6 ? AIR : st;
        if (id.endsWith("lantern") || id.endsWith("campfire") || id.endsWith("_bed") || id.endsWith("redstone_lamp"))
            return v < wear * 2.5 ? null : st;
        if (id.endsWith("ladder") || id.endsWith("rail") || id.endsWith("_carpet") || id.endsWith("cobweb") || id.endsWith("fern")
                || id.endsWith("grass") || id.endsWith("dead_bush") || id.endsWith("vine") || id.endsWith("moss_carpet")) return st;
        boolean hole = cl < wear * 0.9 && v < 0.75 || v < wear * 0.18;
        if (hole) return null;
        if (v < wear * 0.9) return switch (id) {
            case "minecraft:stone_bricks" -> "minecraft:cracked_stone_bricks";
            case "minecraft:cobblestone" -> "minecraft:mossy_cobblestone";
            case "minecraft:spruce_planks" -> "minecraft:dark_oak_planks";
            case "minecraft:light_gray_concrete", "minecraft:white_concrete" -> "minecraft:gray_concrete";
            case "minecraft:white_terracotta" -> "minecraft:terracotta";
            case "minecraft:iron_block" -> "minecraft:raw_iron_block";
            case "stalkerzone:whitewash" -> "stalkerzone:whitewash_damaged";
            default -> st;
        };
        return st;
    }

    /** Блок на высоте ly над землёй в этой колонке (для протяжённых объектов на рельефе). */
    public void setG(int lx, int ly, int lz, String st) {
        int x = wx(lx, lz), z = wz(lx, lz);
        s.set(x, s.ground(x, z) + ly, z, turn(st));
    }

    public int groundAt(int lx, int lz) {
        return s.ground(wx(lx, lz), wz(lx, lz)) - oy;
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String st) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, st);
    }

    /** Полая коробка (стены без пола и потолка). */
    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, String st) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            if (x != x1 && x != x2 && z != z1 && z != z2) continue;
            for (int y = y1; y <= y2; y++) set(x, y, z, st);
        }
    }

    /** Фундамент: заполнить от земли вниз до y=-depth, чтобы на склоне не висело. */
    public void foundation(int x1, int z1, int x2, int z2, int depth, String st) {
        fill(x1, -depth, z1, x2, -1, z2, st);
    }

    public void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, AIR);
    }

    public boolean chance(double p) { return r.nextDouble() < p; }

    public static final String AIR = "minecraft:air";
}
