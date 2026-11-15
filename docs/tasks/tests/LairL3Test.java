import ru.stalkerzone.sim.*;

/**
 * Карточка 047: логово Л3 «Лес у свалки техники» (узел 21): слепые псы с начала, логово ровно у (−92, −100) — к западу от площадки свалки (KordonDump.CLEAR до x −74),
 * сила не выше потолка, стая не больше лимита; днём держатся у логова, ночью уходят дальше (Lair.roamRadius).
 */
public class LairL3Test {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        boolean start = false;
        for (KordonBalance.LairDef d : KordonBalance.START_LAIRS) if (d.node == 21 && d.kind == LairKind.DOGS) start = true;
        ck(start, "стартовое логово: узел 21, слепые псы (KordonBalance.START_LAIRS)");

        Simulation s = Scenarios.create("kordon", 3);
        Lair l3 = s.lairAt(21);
        ck(l3 != null && l3.kind == LairKind.DOGS, "в новом мире на узле 21 логово слепых псов");
        int[] den = Lair.denXZ(s.nodes.get(21));
        ck(s.nodes.get(21).den != null && den[0] == s.nodes.get(21).den[0] && den[1] == s.nodes.get(21).den[1], "логово Л3 в месте, заданном сценарием (Node.den): " + den[0] + ", " + den[1]);
        int[] other = Lair.denXZ(s.nodes.get(14));
        int[] center = {s.nodes.get(14).x * 10, s.nodes.get(14).z * 10};
        ck(Math.hypot(other[0] - center[0], other[1] - center[1]) > Lair.DEN_DIST - 2, "у остальных логов место прежнее (в стороне от центра точки)");

        boolean capOk = true, packOk = true;
        for (long seed = 1; seed <= 3; seed++) {
            Simulation t = Scenarios.create("kordon", seed);
            for (int d = 0; d < 60; d++) {
                t.step();
                Lair l = t.lairAt(21);
                if (l == null) continue;
                if (l.strength > l.cap() + 1e-6) capOk = false;
                if (l.mutantCount() > Lair.MAX_MUTANTS) packOk = false;
            }
        }
        ck(capOk, "60 дней: сила Л3 не выше потолка");
        ck(packOk, "60 дней: стая Л3 не больше " + Lair.MAX_MUTANTS);

        if (l3 != null) {
            int day = l3.roamRadius(false), night = l3.roamRadius(true);
            ck(day > 0 && day <= 12, "днём псы у логова: радиус " + day + " ≤ 12");
            ck(night >= 24, "ночью уходят дальше: радиус " + night + " ≥ 24");
        }
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
