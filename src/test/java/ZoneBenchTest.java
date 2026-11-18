import ru.stalkerzone.sim.*;

/**
 * К15 (Э4-09): бенчмарк ядра на синтетическом атласе 400 узлов.
 *
 * Быстрый режим (то, что гоняет обычный CI): 30 дней × 3 сида; падение — исключение, расход книг
 * Ledger или шаг медленнее 15 мс по медиане. Полный режим (365 дней) — через -Dbench.full=true,
 * он для ночного CI; в обычном прогоне не включается.
 */
public class ZoneBenchTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    static final int SIDE = 20;         // 20 × 20 = 400 узлов
    static final int DAYS = 365;
    static final double MEDIAN_MS = 15; // порог Э4-09 для суточного шага

    public static void main(String[] a) {
        boolean full = Boolean.getBoolean("bench.full");
        System.out.println("режим: " + (full ? "полный " + DAYS + " дней" : "быстрый 30 дней") + ", атлас " + SIDE * SIDE + " узлов");

        noExceptionsAndBooks(full);
        determinism();
        stepCost(full);

        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    static int days(boolean full) { return full ? DAYS : 30; }

    /** Нет исключений, книги сходятся каждый день, ни одна группа не съедена подчистую. */
    static void noExceptionsAndBooks(boolean full) {
        int total = days(full);
        boolean ok = true, books = true;
        int daysRun = 0;
        String err = "";
        for (long seed : new long[]{1, 7, 42}) {
            Simulation s;
            try {
                s = new BigAtlas(SIDE, 6).create(seed);
            } catch (RuntimeException e) {
                ok = false;
                err = "сид " + seed + ", создание мира: " + e;
                break;
            }
            int aliveStart = s.factions.size();
            for (int d = 0; d < total; d++) {
                try {
                    s.step();
                } catch (RuntimeException e) {
                    ok = false;
                    err = "сид " + seed + ", день " + s.day + ": " + e;
                    break;
                }
                if (!s.audit().isEmpty()) {
                    books = false;
                    ck(false, "сид " + seed + ", день " + s.day + ": книги разошлись — " + s.audit());
                    break;
                }
            }
            daysRun += s.day;
            if (!ok) break;
            ck(s.factions.size() == aliveStart, "сид " + seed + ": групп было " + aliveStart + ", стало " + s.factions.size());
        }
        ck(ok, total + " дней × 3 сида без исключений" + (ok ? "" : ": " + err));
        // книги проверяем только на тех днях, что реально прожиты (иначе пустой прогон зеленеет)
        ck(books && daysRun >= total, "книги Ledger сходятся каждый день (" + daysRun + " дней прожито)");
    }

    /** Один сид — одна подпись состояния: детерминизм на большом графе. */
    static void determinism() {
        Simulation a, b;
        try {
            a = new BigAtlas(SIDE, 6).create(777L);
            b = new BigAtlas(SIDE, 6).create(777L);
        } catch (RuntimeException e) {
            ck(false, "создание мира на 400 узлах: " + e);
            return;
        }
        a.step(60);
        b.step(60);
        ck(BigAtlas.signature(a) == BigAtlas.signature(b),
                "сид 777: подписи состояния совпадают после 60 дней");
        ck(a.lairs.size() == b.lairs.size(), "сид 777: одинаковое число логов " + a.lairs.size());
    }

    /** Стоимость суточного шага: медиана ≤ 15 мс (Э4-09 быстрый режим). */
    static void stepCost(boolean full) {
        int total = days(full);
        Simulation s;
        try {
            s = new BigAtlas(SIDE, 6).create(99L);
        } catch (RuntimeException e) {
            ck(false, "создание мира на 400 узлах: " + e);
            return;
        }
        long[] us = new long[total];
        try {
            for (int d = 0; d < total; d++) {
                long t0 = System.nanoTime();
                s.step();
                us[d] = (System.nanoTime() - t0) / 1000;
            }
        } catch (RuntimeException e) {
            ck(false, "шаг симуляции на 400 узлах: " + e);
            return;
        }
        long[] sorted = us.clone();
        java.util.Arrays.sort(sorted);
        double median = sorted[sorted.length / 2] / 1000.0;
        double p99 = sorted[(int) Math.min(sorted.length - 1, sorted.length * 0.99)] / 1000.0;
        System.out.printf("шаг: медиана %.2f мс, 99-й перцентиль %.2f мс, узлов %d%n", median, p99, s.nodes.size());
        ck(median <= MEDIAN_MS, "медиана шага " + String.format("%.2f", median) + " мс ≤ " + MEDIAN_MS + " мс");
    }
}
