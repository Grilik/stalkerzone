package ru.stalkerzone.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import ru.stalkerzone.block.ModBlocks;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;

import java.util.Random;

/**
 * Радиоактивные пятна на локации (карточка 013). Фонят насыпь с цистернами, техника у дыры в заборе и
 * свиноферма: туда кладутся блоки с тегом {@code stalkerzone:radioactive} (бочки с отходами), которые
 * чувствует счётчик Гейгера. Точки задаёт сценарий (узлы {@link Simulation#radiationNodes}), код мира
 * только расставляет источники — новая локация заработает без правок здесь.
 */
public final class RadioactiveSpots {
    private RadioactiveSpots() {}

    /** Расставить источники фона вокруг узла. true — если что-то поставлено. */
    public static boolean place(ServerLevel level, Simulation sim, Node n, int worldCX, int worldCZ) {
        if (!sim.isRadioactive(n.id)) return false;
        BlockState barrel = ModBlocks.DECOR.get("radioactive_barrel").get().defaultBlockState();
        Random r = new Random(n.id * 7919L + 13);
        int placed = 0;
        for (int i = 0; i < 6; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = 8 + r.nextDouble() * 10;
            int x = worldCX + (int) Math.round(Math.cos(ang) * rad);
            int z = worldCZ + (int) Math.round(Math.sin(ang) * rad);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos ground = SpawnFinder.groundAt(level, x, z);
            if (ground.getY() <= level.getMinBuildHeight()) continue;
            if (!level.getFluidState(ground.below()).isEmpty()) continue;
            if (!level.getBlockState(ground).isAir()) continue;
            level.setBlock(ground, barrel, 18);
            placed++;
        }
        return placed > 0;
    }
}
