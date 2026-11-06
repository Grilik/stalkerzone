import ru.stalkerzone.sim.*;

import java.util.*;

/**
 * Карточка 042: симуляция детерминирована и без шага дня — один и тот же мир (сид, сохранение) даёт тот же выброс.
 * Сейчас Simulation.rng = new Random() без сида до первого step(), поэтому /zone emission сразу после
 * создания мира или загрузки сохранения раскладывает аномалии случайно — отсюда «плавающий» AnomalyPlacementTest.
 */
public class SimDeterminismTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    static String anomalies(Simulation s) {
        StringBuilder b = new StringBuilder();
        for (Anomaly a : s.anomalies) b.append(a.node).append(':').append(a.dx).append(',').append(a.dz).append(':').append(a.type).append(' ');
        return b.toString();
    }

    public static void main(String[] a) {
        boolean fresh = true, loaded = true, stepped = true;
        for (long seed = 1; seed <= 4; seed++) {
            // 1. два новых мира с одним сидом: выбросы без шага дня дают одно и то же
            Simulation s1 = Scenarios.create("kordon", seed), s2 = Scenarios.create("kordon", seed);
            for (int e = 0; e < 8; e++) { s1.triggerEmission(); s2.triggerEmission(); }
            if (!anomalies(s1).equals(anomalies(s2))) fresh = false;

            // 2. одно сохранение, загруженное дважды: выброс сразу после загрузки одинаков
            Simulation base = Scenarios.create("kordon", seed);
            for (int d = 0; d < 7; d++) base.step();
            String save = base.save();
            Simulation l1 = Simulation.load(save), l2 = Simulation.load(save);
            for (int e = 0; e < 8; e++) { l1.triggerEmission(); l2.triggerEmission(); }
            if (!anomalies(l1).equals(anomalies(l2))) loaded = false;

            // 3. обычный ход дней остаётся детерминированным (так было и раньше — не сломать)
            Simulation d1 = Scenarios.create("kordon", seed), d2 = Scenarios.create("kordon", seed);
            for (int d = 0; d < 30; d++) { d1.step(); d2.step(); }
            if (!d1.save().equals(d2.save())) stepped = false;
        }
        ck(fresh, "новый мир: 8 выбросов подряд без шага дня одинаковы при одном сиде");
        ck(loaded, "сохранение, загруженное дважды: выбросы сразу после загрузки одинаковы");
        ck(stepped, "30 дней симуляции с одним сидом дают одинаковое сохранение");
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
