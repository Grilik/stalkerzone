package ru.stalkerzone.sim;

import ru.stalkerzone.world.ZoneBounds;

/**
 * Пси-излучение Выжигателя: какой уровень действует в точке на поверхности (карточка 024).
 * Ядро симуляции, без Minecraft; слой мира ({@code anomaly/PsiField}) берёт уровень отсюда,
 * чтобы логику можно было проверить тестом симуляции.
 */
public final class Psi {
    /** Источник пси-излучения — Выжигатель к северу от Радара; смещение и радиусы в блоках. */
    public static final int OFFSET_Z = -450, LETHAL = 300, REACH = 600;

    private Psi() {}

    /**
     * Координаты источника пси-излучения (Выжигатель к северу от узла «Радар») для сценария.
     * {@code null}, если в сценарии такого узла нет — тогда поля на поверхности тоже нет.
     * Единственный источник правды для всей поверхностной пси-логики: слой мира
     * ({@code anomaly/PsiField}) берёт координаты отсюда же, чтобы поле и зона зомбирования не разошлись.
     */
    public static int[] source(Simulation sim) {
        for (Node n : sim.nodes)
            if ("Радар".equals(n.name)) return new int[] {n.x * ZoneBounds.SCALE, n.z * ZoneBounds.SCALE + OFFSET_Z};
        return null;
    }

    /**
     * Уровень излучения в точке на поверхности: 0 — нет, 1 — слабое, 2 — сильное, 3 — смертельное.
     * В Кордоне 1.0 Радара нет, поэтому пси-поле старой «всей карты» здесь не действует.
     */
    public static int surfaceLevel(Simulation sim, double x, double z) {
        if (sim.scorcher.disabled) return 0;
        if (Scenarios.KORDON.equals(sim.scenario)) return 0;
        int[] src = source(sim);
        if (src == null) return 0;
        double d = Math.hypot(x - src[0], z - src[1]);
        if (d < LETHAL) return 3;
        if (d < LETHAL + 60) return 2;
        if (d < REACH) return 1;
        return 0;
    }
}
