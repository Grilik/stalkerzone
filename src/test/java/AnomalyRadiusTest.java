import ru.stalkerzone.sim.*;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Карточка 043: аномалия поля всегда внутри радиуса своего поля — и при раскладке нового мира, и после выбросов.
 * Сейчас Simulation.placeInDistrict округляет cos·r и sin·r по отдельности, и точка на краю круга
 * выходит за радиус до ~0,7 блока (например, поле узла 7, r = 30: аномалия на (24, −19) от центра).
 * Случайная последовательность выброса задаётся явно (поле rng), чтобы проверка не зависела от удачи.
 */
public class AnomalyRadiusTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    /** Аномалии вне радиуса своего поля: «узел (dx,dz) r=…» или пусто. */
    static String outside(Simulation s) {
        for (AnomalyDistrict d : s.anomalyDistricts)
            for (Anomaly an : s.anomaliesAt(d.node))
                if (Math.hypot(an.dx - d.cdx, an.dz - d.cdz) > d.radius)
                    return "узел " + d.node + " (" + (an.dx - d.cdx) + "," + (an.dz - d.cdz) + ") от центра, r=" + d.radius;
        return "";
    }

    public static void main(String[] a) throws Exception {
        Field rng = Simulation.class.getDeclaredField("rng");
        rng.setAccessible(true);

        String seeded = "";
        for (long seed = 1; seed <= 60 && seeded.isEmpty(); seed++) {
            String o = outside(Scenarios.create("kordon", seed));
            if (!o.isEmpty()) seeded = "сид " + seed + ": " + o;
        }
        ck(seeded.isEmpty(), "новый мир (сиды 1–60): аномалии внутри радиуса поля " + seeded);

        // известные последовательности, на которых аномалия выходила за радиус (сид мира, сид rng выброса)
        long[][] known = {{5, 1546}, {4, 2811}, {6, 2861}};
        String bad = "";
        for (long[] k : known) {
            Simulation s = Scenarios.create("kordon", k[0]);
            rng.set(s, new Random(k[1]));
            for (int e = 0; e < 20 && bad.isEmpty(); e++) {
                s.triggerEmission();
                String o = outside(s);
                if (!o.isEmpty()) bad = "мир " + k[0] + ", rng " + k[1] + ", выброс " + (e + 1) + ": " + o;
            }
        }
        ck(bad.isEmpty(), "20 выбросов на известных последовательностях: аномалии внутри радиуса " + bad);

        String any = "";
        for (int k = 1; k <= 400 && any.isEmpty(); k++) {
            Simulation s = Scenarios.create("kordon", 1 + k % 6);
            rng.set(s, new Random(100_000L + k));
            for (int e = 0; e < 20 && any.isEmpty(); e++) {
                s.triggerEmission();
                String o = outside(s);
                if (!o.isEmpty()) any = "rng " + (100_000L + k) + ", выброс " + (e + 1) + ": " + o;
            }
        }
        ck(any.isEmpty(), "400 случайных последовательностей × 20 выбросов: аномалии внутри радиуса " + any);
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
