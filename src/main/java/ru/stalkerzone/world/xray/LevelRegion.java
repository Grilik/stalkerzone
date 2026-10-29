package ru.stalkerzone.world.xray;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Уровень оригинальной игры в масштабе 1:1 (формат .szl, см. tools/xray): рельеф с шагом 1 м, покрытие, вода,
 * постройки вокселями (материал X-Ray), вырезы подземных помещений, деревья. Координаты уже в блоках мира.
 * Чистая Java — без Minecraft, чтобы данные можно было проверять тестами.
 */
public final class LevelRegion {
    public final String id;
    public final int x0, z0, w, d, yOff;
    private final short[] ground;   // высота земли ×8
    private final byte[] surface;   // код покрытия
    private final short[] water;    // уровень воды ×8, < 0 — нет
    /** Воксели по чанкам: ключ chunkKey, значение — массив упакованных (x, y, z, материал). */
    private final Map<Long, int[]> vox = new HashMap<>();
    private final Map<Long, int[]> air = new HashMap<>();
    private final Map<Long, List<int[]>> trees = new HashMap<>();
    public int voxelCount, airCount, treeCount;

    /** Покрытие земли (код в .szl). */
    public static final int S_EARTH = 1, S_DIRT = 2, S_GRASS = 3, S_GRAVEL = 4, S_SAND = 5, S_ASPHALT = 6, S_DEATH = 7;

    public static long chunkKey(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    private LevelRegion(String id, int x0, int z0, int w, int d, int yOff, short[] g, byte[] s, short[] wt) {
        this.id = id; this.x0 = x0; this.z0 = z0; this.w = w; this.d = d; this.yOff = yOff;
        this.ground = g; this.surface = s; this.water = wt;
    }

    public static LevelRegion load(String id, InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16));
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (magic[0] != 'S' || magic[1] != 'Z' || magic[2] != 'L' || magic[3] != '1') throw new IOException("не .szl");
        ByteBuffer h = le(in, 20);
        int x0 = h.getInt(), z0 = h.getInt(), w = h.getInt(), d = h.getInt(), yOff = h.getInt();
        int n = w * d;
        short[] g = new short[n];
        le(in, n * 2).asShortBuffer().get(g);
        byte[] s = new byte[n];
        in.readFully(s);
        short[] wt = new short[n];
        le(in, n * 2).asShortBuffer().get(wt);
        LevelRegion r = new LevelRegion(id, x0, z0, w, d, yOff, g, s, wt);
        int nv = le(in, 4).getInt();
        ByteBuffer vb = le(in, nv * 7);
        Map<Long, List<int[]>> tmp = new HashMap<>();
        for (int i = 0; i < nv; i++) {
            int x = vb.getShort(), y = vb.getShort(), z = vb.getShort(), m = vb.get() & 0xff;
            tmp.computeIfAbsent(chunkKey(x >> 4, z >> 4), k -> new ArrayList<>()).add(new int[] {x, y, z, m});
        }
        for (var e : tmp.entrySet()) r.vox.put(e.getKey(), flat(e.getValue(), 4));
        r.voxelCount = nv;
        int na = le(in, 4).getInt();
        ByteBuffer ab = le(in, na * 6);
        tmp.clear();
        for (int i = 0; i < na; i++) {
            int x = ab.getShort(), y = ab.getShort(), z = ab.getShort();
            tmp.computeIfAbsent(chunkKey(x >> 4, z >> 4), k -> new ArrayList<>()).add(new int[] {x, y, z});
        }
        for (var e : tmp.entrySet()) r.air.put(e.getKey(), flat(e.getValue(), 3));
        r.airCount = na;
        int nt = le(in, 4).getInt();
        ByteBuffer tb = le(in, nt * 9);
        for (int i = 0; i < nt; i++) {
            int x = tb.getShort(), z = tb.getShort(), y = tb.getShort(), hgt = tb.get() & 0xff, rad = tb.get() & 0xff, kind = tb.get() & 0xff;
            r.trees.computeIfAbsent(chunkKey(x >> 4, z >> 4), k -> new ArrayList<>()).add(new int[] {x, y, z, hgt, rad, kind});
        }
        r.treeCount = nt;
        return r;
    }

    private static int[] flat(List<int[]> l, int k) {
        int[] o = new int[l.size() * k];
        for (int i = 0; i < l.size(); i++) System.arraycopy(l.get(i), 0, o, i * k, k);
        return o;
    }

    private static ByteBuffer le(DataInputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        in.readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
    }

    public boolean contains(int x, int z) { return x >= x0 && z >= z0 && x < x0 + w && z < z0 + d; }

    /** Расстояние от точки до прямоугольника уровня (0 внутри). */
    public double distance(double x, double z) {
        double dx = Math.max(0, Math.max(x0 - x, x - (x0 + w - 1)));
        double dz = Math.max(0, Math.max(z0 - z, z - (z0 + d - 1)));
        return Math.hypot(dx, dz);
    }

    private int idx(int x, int z) {
        int i = Math.max(0, Math.min(w - 1, x - x0)), j = Math.max(0, Math.min(d - 1, z - z0));
        return j * w + i;
    }

    /** Высота земли (дробная) с билинейной интерполяцией. */
    public double ground(double x, double z) {
        double fx = x - x0 - 0.5, fz = z - z0 - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        double a = gAt(i, j), b = gAt(i + 1, j), c = gAt(i, j + 1), e = gAt(i + 1, j + 1);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + e * tx) * tz;
    }

    private double gAt(int i, int j) {
        i = Math.max(0, Math.min(w - 1, i));
        j = Math.max(0, Math.min(d - 1, j));
        return ground[j * w + i] / 8.0;
    }

    public int groundBlock(int x, int z) { return (int) Math.floor(ground[idx(x, z)] / 8.0); }

    public int surface(int x, int z) { return surface[idx(x, z)]; }

    /** Уровень воды (верхний блок воды) или Integer.MIN_VALUE. */
    public int water(int x, int z) {
        short v = water[idx(x, z)];
        return v < 0 ? Integer.MIN_VALUE : (int) Math.floor(v / 8.0);
    }

    public int[] voxels(int cx, int cz) { return vox.getOrDefault(chunkKey(cx, cz), new int[0]); }

    public int[] air(int cx, int cz) { return air.getOrDefault(chunkKey(cx, cz), new int[0]); }

    public List<int[]> trees(int cx, int cz) { return trees.getOrDefault(chunkKey(cx, cz), List.of()); }
}
