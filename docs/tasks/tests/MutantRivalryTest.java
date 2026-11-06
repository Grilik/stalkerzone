import ru.stalkerzone.sim.*;

/**
 * Карточка 064: грызня видов. LairTerritory.hostile(вид A, вид B, люди рядом): мутанты разных видов
 * дерутся, если людей поблизости нет; одного вида — не трогают друг друга; при людях рядом все бьют людей.
 * Simulation.mutantKilledByMutant(жертва, убийца, цена): логово жертвы слабеет на цену мутанта (как mutantKilled),
 * логово убийцы получает половину цены как добычу (Lair.feed).
 */
public class MutantRivalryTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        ck(LairTerritory.hostile(LairKind.DOGS, LairKind.FLESH, false), "псы и плоти без людей рядом — враги");
        ck(!LairTerritory.hostile(LairKind.DOGS, LairKind.FLESH, true), "люди рядом — мутанты не грызутся между собой");
        ck(!LairTerritory.hostile(LairKind.DOGS, LairKind.DOGS, false), "один вид — не враги");

        Simulation s = Scenarios.create("kordon", 2);
        Lair victim = null, killer = null;
        for (Lair l : s.lairs) {
            if (l.kind == LairKind.FLESH && victim == null) victim = l;
            if (l.kind == LairKind.DOGS && killer == null) killer = l;
        }
        ck(victim != null && killer != null, "в Кордоне есть логова плотей и псов");
        if (victim != null && killer != null) {
            killer.strength = 10;
            double vs = victim.strength, ks = killer.strength, eaten = killer.preyEaten;
            boolean destroyed = s.mutantKilledByMutant(victim, killer, victim.kind.mutantValue);
            ck(!destroyed && Math.abs(victim.strength - (vs - victim.kind.mutantValue)) < 1e-9, "логово жертвы ослабло на цену мутанта");
            ck(Math.abs(killer.strength - (ks + victim.kind.mutantValue * 0.5)) < 1e-9 && killer.preyEaten > eaten,
                    "логово убийцы получило половину цены как добычу");
            victim.strength = 5;
            boolean gone = s.mutantKilledByMutant(victim, killer, victim.kind.mutantValue);
            ck(gone && !s.lairs.contains(victim), "добитое логово уничтожено");
            ck(s.audit().isEmpty(), "аудит сходится");
        }
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
