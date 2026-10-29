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

    @Override
    public double compute(FunctionContext c) {
        int x = c.blockX(), z = c.blockZ();
        LevelRegion r = LevelRegions.near(x, z, LevelRegions.BLEND);
        if (r == null) return fallback.compute(c);
        double target = r.ground(x, z);
        double own = Math.max(-1.5, Math.min(1.5, (target - c.blockY()) / 12.0));
        double dist = r.distance(x, z);
        if (dist <= 0) return own;
        double t = dist / LevelRegions.BLEND;
        t = t * t * (3 - 2 * t);
        return own * (1 - t) + fallback.compute(c) * t;
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
