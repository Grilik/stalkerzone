import ru.stalkerzone.sim.*;

/**
 * Карточка 062: логово — база стаи, куда тащат добычу. Lair.feed(еда): сила растёт (не выше потолка),
 * съеденное копится в Lair.preyEaten. Каждый человек, убитый налётом логова, — добыча этого логова
 * (KordonBalance.PREY_PER_PERSON силы за человека). Число мутантов растёт с силой (mutantCount) — это и есть размножение.
 */
public class LairPreyTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        Lair l = new Lair(21, LairKind.DOGS, 10, false);
        int before = l.mutantCount();
        double eaten = l.feed(15);
        ck(Math.abs(l.strength - 25) < 1e-9 && Math.abs(eaten - 15) < 1e-9, "feed(15): сила 10 → 25, съедено 15");
        ck(l.mutantCount() > before, "добыча → стая растёт: " + before + " → " + l.mutantCount());
        double e2 = l.feed(1000);
        ck(Math.abs(l.strength - l.cap()) < 1e-9 && Math.abs(e2 - (l.cap() - 25)) < 1e-9, "сытое логово не растёт выше потолка, лишнее не съедено");
        ck(Math.abs(l.preyEaten - (15 + l.cap() - 25)) < 1e-9, "preyEaten — всё съеденное: " + l.preyEaten);
        ck(l.feed(-5) == 0 && Math.abs(l.strength - l.cap()) < 1e-9, "отрицательная еда ничего не меняет");
        ck(KordonBalance.PREY_PER_PERSON > 0, "за убитого человека логово получает добычу");

        // налёты с жертвами кормят логово
        int seedsFed = 0;
        for (long seed = 1; seed <= 4; seed++) {
            Simulation s = Scenarios.create("kordon", seed);
            for (int d = 0; d < 150; d++) s.step();
            double total = 0;
            for (Lair x : s.lairs) total += x.preyEaten;
            boolean deaths = false;
            for (String ev : s.events) if (ev.contains("напало на") && !ev.endsWith("погибло 0")) deaths = true;
            if (total > 0) seedsFed++;
            ck(!deaths || total > 0, "сид " + seed + ": налёты с жертвами кормят логова (съедено " + String.format("%.1f", total) + ")");
            ck(s.audit().isEmpty(), "сид " + seed + ": аудит сходится");
        }
        ck(seedsFed >= 1, "хотя бы в одном сиде логово съело добычу за 150 дней: " + seedsFed);
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
