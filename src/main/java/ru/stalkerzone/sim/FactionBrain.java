package ru.stalkerzone.sim;

import java.util.List;
import java.util.Random;

/**
 * Командование фракции. Вызывается каждый игровой день. Реализация не должна хранить своё состояние
 * (память фракции лежит в {@link Faction}), чтобы сохранение и загрузка давали тот же ход событий,
 * а случайность брала только из переданного rng.
 */
public interface FactionBrain {
    List<Command> decide(Simulation sim, Faction f, Random rng);
}
