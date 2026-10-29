package ru.stalkerzone.world.xray;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ru.stalkerzone.StalkerZoneMod;

/**
 * Плотность рельефа: внутри уровней оригинала — точная поверхность из файлов игры, вокруг — плавный переход
 * к обычному рельефу Зоны (fallback). Положительная плотность — твёрдое.
 */
public record XrayTerrain(DensityFunction fallback) implements DensityFunction {
    public static final Codec<XrayTerrain> DATA_CODEC = RecordCodecBuilder.create(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("fallback").forGetter(XrayTerrain::fallback)
    ).apply(i, XrayTerrain::new));
    public static final KeyDispatchDataCodec<XrayTerrain> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    public static final DeferredRegister<Codec<? extends DensityFunction>> TYPES = DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, StalkerZoneMod.MOD_ID);
    public static final RegistryObject<Codec<? extends DensityFunction>> XRAY = TYPES.register("xray_terrain", () -> DATA_CODEC);

    /** На каком расстоянии от уровней обычный рельеф Зоны поднимается до их высоты (уровни выше моря на 40–70 блоков). */
    static final int LIFT_R = 520;
    /** Средняя высота обычного рельефа Зоны. */
    static final int BASE = 66;

    @Override
    public double compute(FunctionContext c) {
        int x = c.blockX(), z = c.blockZ();
        LevelRegion r = LevelRegions.near(x, z, LevelRegions.BLEND);
        double own = 0, dist = Double.MAX_VALUE;
        if (r != null) {
            own = Math.max(-1.5, Math.min(1.5, (r.ground(x, z) - c.blockY()) / 12.0));
            dist = r.distance(x, z);
            if (dist <= 0) return own;
        }
        // обычный рельеф Зоны = y_clamped_gradient(36..96) + шумы без y (noise_settings/zone.json): подъём на lift —
        // это тот же градиент, сдвинутый по y; шумы и интерполяция генератора остаются как есть
        double lift = lift(x, z);
        double fb = fallback.compute(c);
        if (lift != 0) fb += grad(c.blockY() - lift) - grad(c.blockY());
        if (r == null) return fb;
        double t = dist / LevelRegions.BLEND;
        t = t * t * (3 - 2 * t);
        return own * (1 - t) + fb * t;
    }

    static double grad(double y) {
        return y <= 36 ? 1 : y >= 96 ? -1 : 1 - 2 * (y - 36) / 60.0;
    }

    /** Подъём считается по столбцам чанка один раз (генератор зовёт плотность для каждого блока). */
    private static final ThreadLocal<long[]> LKEY = ThreadLocal.withInitial(() -> new long[] {Long.MIN_VALUE});
    private static final ThreadLocal<double[]> LVAL = ThreadLocal.withInitial(() -> new double[256]);

    static double lift(int x, int z) {
        long key = ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL);
        double[] v = LVAL.get();
        long[] k = LKEY.get();
        if (k[0] != key) {
            k[0] = key;
            int bx = x & ~15, bz = z & ~15;
            // по углам 4×4 и билинейно — подъём плавный, точнее не нужно
            double[] g = new double[25];
            for (int i = 0; i <= 4; i++) for (int j = 0; j <= 4; j++) g[i * 5 + j] = liftAt(bx + i * 4, bz + j * 4);
            for (int i = 0; i < 16; i++) for (int j = 0; j < 16; j++) {
                int ci = i >> 2, cj = j >> 2;
                double tx = (i & 3) / 4.0, tz = (j & 3) / 4.0;
                v[i * 16 + j] = (g[ci * 5 + cj] * (1 - tx) + g[(ci + 1) * 5 + cj] * tx) * (1 - tz)
                        + (g[ci * 5 + cj + 1] * (1 - tx) + g[(ci + 1) * 5 + cj + 1] * tx) * tz;
            }
        }
        return v[(x & 15) * 16 + (z & 15)];
    }

    /**
     * Подъём обычного рельефа между уровнями: взвешенная высота краёв ближних уровней над {@link #BASE},
     * плавно сходящая на нет к {@link #LIFT_R}. Так между Кордоном и Свалкой — холмы на их высоте, а не провал.
     */
    static double liftAt(int x, int z) {
        double sw = 0, sh = 0, dmin = Double.MAX_VALUE;
        for (LevelRegion r : LevelRegions.all()) {
            double d = r.distance(x, z);
            if (d >= LIFT_R) continue;
            double w = 1.0 / Math.max(16, d);
            w *= w;
            sw += w;
            sh += w * (r.ground(x, z) - BASE);
            dmin = Math.min(dmin, d);
        }
        if (sw == 0) return 0;
        double f = 1 - dmin / LIFT_R;
        f = f * f * (3 - 2 * f);
        return Math.max(0, sh / sw) * f;
    }

    @Override
    public void fillArray(double[] out, ContextProvider p) { p.fillAllDirectly(out, this); }

    @Override
    public DensityFunction mapAll(Visitor v) { return v.apply(new XrayTerrain(fallback.mapAll(v))); }

    @Override
    public double minValue() { return Math.min(-1.5, fallback.minValue()); }

    @Override
    public double maxValue() { return Math.max(1.5, fallback.maxValue()); }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
