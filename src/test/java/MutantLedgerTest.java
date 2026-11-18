import ru.stalkerzone.sim.*;

/**
 * К16 (Э8-10): третья книга Ledger — мутанты.
 *
 * Σ(мутантов в логовах) = сколько было на старте + родилось − погибло, каждый день.
 * Сейчас учёта нет: Simulation считает lairsSpawned/lairsDestroyed (число логов), но не мутантов,
 * книги Ledger про мутантов не знают, поэтому тест красный.
 *
 * Требуется от Э8-10: счётчики мутантов в Ledger (или рядом с ним) и сведение Σ по деньгам и товарам
 * вместе с новой книгой в audit(). Договорённость о именах — ниже, менять её вместе с тестом.
 */
public class MutantLedgerTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) throws Exception {
        countFormula();
        feedGrowsPack();
        bookOfMutants();
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    /** mutantCount: сила / цена вида, не меньше 1 и не больше MAX_MUTANTS. */
    static void countFormula() {
        Lair dogs = new Lair(0, LairKind.DOGS, 20, false);
        ck(dogs.mutantCount() == 4, "DOGS, сила 20 → 4 мутанта: " + dogs.mutantCount());

        Lair weak = new Lair(1, LairKind.DOGS, 0.1, false);
        ck(weak.mutantCount() == 1, "даже пустое логово держит 1 мутанта: " + weak.mutantCount());

        Lair huge = new Lair(2, LairKind.DOGS, 10_000, false);
        ck(huge.mutantCount() == Lair.MAX_MUTANTS, "потолок MAX_MUTANTS: " + huge.mutantCount());

        Lair burers = new Lair(3, LairKind.BURERS, 50, false);
        Lair flesh = new Lair(4, LairKind.FLESH, 50, false);
        ck(burers.mutantCount() <= flesh.mutantCount(),
                "бюреры дороже плоти: " + burers.mutantCount() + " ≤ " + flesh.mutantCount());
    }

    /** Кормление: сила растёт до потолка, добыча копится, стая не уменьшается. */
    static void feedGrowsPack() {
        Lair l = new Lair(5, LairKind.FLESH, 20, false);
        int before = l.mutantCount();
        double eaten = l.feed(15);
        ck(Math.abs(eaten - 15) < 1e-9 && Math.abs(l.strength - 35) < 1e-9, "feed(15): сила 20 → 35");
        ck(Math.abs(l.preyEaten - 15) < 1e-9, "preyEaten = 15");
        ck(l.mutantCount() >= before, "после кормления стая не меньше: " + before + " → " + l.mutantCount());

        double rest = l.feed(1000);
        ck(Math.abs(l.strength - l.cap()) < 1e-9, "сила не выше потолка: " + l.strength + " = " + l.cap());
        ck(Math.abs(rest - (l.cap() - 35)) < 1e-9, "съедено ровно до потолка: " + rest);

        Lair lab = new Lair(6, LairKind.DOGS, 30, true);
        Lair plain = new Lair(7, LairKind.DOGS, 30, false);
        ck(lab.cap() > plain.cap() && lab.growth() > plain.growth(), "лаборатория: потолок и рост выше");
    }

    /**
     * Книга мутантов в Ledger. Поля читаются по имени: пока их нет — проверка красная,
     * а компилируется весь набор тестов всё равно (в проекте один javac на все *Test.java).
     * Договорённые имена: Ledger.mutantsBorn, Ledger.mutantsDied (long).
     */
    static void bookOfMutants() throws Exception {
        Simulation s = KordonScenario.create(999L);
        long start = 0;
        for (Lair l : s.lairs) start += l.mutantCount();
        ck(start > 0, "на старте в Кордоне мутантов: " + start);

        for (int d = 0; d < 180; d++) s.step();

        long now = 0;
        for (Lair l : s.lairs) now += l.mutantCount();

        Long born = counter(s.ledger, "mutantsBorn");
        Long died = counter(s.ledger, "mutantsDied");
        if (born == null || died == null) {
            ck(false, "в Ledger нет книги мутантов (нужны long mutantsBorn, mutantsDied)");
            return;
        }

        ck(born + died > 0, "за 180 дней стаи рождались и гибли: родилось " + born + ", погибло " + died);
        ck(start + born - died == now,
                "книга мутантов сходится: " + start + " + " + born + " − " + died + " = " + now);
        ck(s.audit().isEmpty(), "audit() сходится вместе с книгой мутантов" + (s.audit().isEmpty() ? "" : ": " + s.audit()));
    }

    /** Значение long-поля по имени или null, если поля ещё нет. */
    static Long counter(Object target, String name) {
        try {
            java.lang.reflect.Field fld = target.getClass().getDeclaredField(name);
            fld.setAccessible(true);
            return fld.getLong(target);
        } catch (NoSuchFieldException e) {
            return null;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
