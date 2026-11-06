import ru.stalkerzone.sim.*;

/**
 * Карточка 063: набег от перенаселения. Ничьё логово идёт в налёт, только когда стая переполнена —
 * mutantCount() == Lair.MAX_MUTANTS (и сила не ниже lairRaidMin, перезарядка 0). Пока места в стае есть — сидит дома.
 */
public class LairOverpopulationTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    static Simulation withLair(long seed, double strength) {
        Simulation s = Scenarios.create("kordon", seed);
        for (Lair x : s.lairs) { x.raidCooldown = 99; }
        Lair l = s.lairAt(21);
        l.strength = strength;
        l.raidCooldown = 0;
        return s;
    }

    public static void main(String[] a) {
        Lair probe = new Lair(21, LairKind.DOGS, 30.5, false);
        ck(probe.mutantCount() < Lair.MAX_MUTANTS, "псы с силой 30,5 — в стае ещё есть место (" + probe.mutantCount() + ")");
        int quiet = 0, raided = 0;
        for (long seed = 1; seed <= 5; seed++) {
            Simulation s = withLair(seed, 30.5);
            s.step();
            if (s.lairAt(21) != null && s.lairAt(21).raidCooldown == 0) quiet++;
            Simulation t = withLair(seed, 40);
            t.step();
            Lair l = t.lairAt(21);
            if (l == null || l.raidCooldown > 0) raided++;
        }
        ck(quiet == 5, "5 сидов: стая не переполнена (сила 30,5 > lairRaidMin) — налёта нет: " + quiet + " из 5");
        ck(raided == 5, "5 сидов: стая переполнена (сила 40) — налёт в тот же день: " + raided + " из 5");
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
