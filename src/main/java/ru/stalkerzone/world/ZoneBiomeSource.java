package ru.stalkerzone.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ru.stalkerzone.StalkerZoneMod;

import java.util.stream.Stream;

/** Источник биомов Зоны: биом определяется локацией карты (см. {@link ZoneBiomes}). */
public class ZoneBiomeSource extends BiomeSource {
    public static final Codec<ZoneBiomeSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(s -> s.biomes)
    ).apply(i, ZoneBiomeSource::new));

    public static final DeferredRegister<Codec<? extends BiomeSource>> SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, StalkerZoneMod.MOD_ID);
    public static final RegistryObject<Codec<? extends BiomeSource>> ZONE = SOURCES.register("zone", () -> CODEC);

    private final HolderSet<Biome> biomes;

    public ZoneBiomeSource(HolderSet<Biome> biomes) {
        this.biomes = biomes;
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int i = ZoneBiomes.index(x << 2, z << 2);
        return biomes.get(Math.max(0, Math.min(biomes.size() - 1, i)));
    }
}
