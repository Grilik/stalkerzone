import ru.stalkerzone.sim.*;

/** Убийства мутантов в мире ослабляют логово, добыча проходит через учёт, логово можно выбить. */
public class MutantTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        Simulation s = ZoneScenario.create(3);
        s.step(120);
        ck(s.audit().isEmpty(), "аудит до охоты");
        ck(!s.lairs.isEmpty(), "логова есть: " + s.lairs.size());
        Lair l = s.lairs.get(0);
        double before = l.strength;
        boolean gone = s.mutantKilled(l, 7, true);
        s.mutantPartLoot(2);
        ck(!gone && l.strength < before, String.format("убийство ослабило логово: %.1f -> %.1f", before, l.strength));
        ck(s.audit().isEmpty(), "аудит после трофея");
        int kills = 1, art = 0;
        while (s.lairs.contains(l) && kills < 100) {
            kills++;
            if (s.mutantKilled(l, 7, true)) art = s.lairClearReward(l);
            s.mutantPartLoot(1);
        }
        ck(!s.lairs.contains(l), "логово выбито за " + kills + " убийств, находка: " + art + " артефактов");
        ck(art >= 1, "в логове есть артефакт");
        ck(s.audit().isEmpty(), "аудит после зачистки");
        int destroyed = s.lairsDestroyed;
        s.step(400);
        ck(s.audit().isEmpty(), "аудит через 400 дней");
        ck(!s.lairs.isEmpty(), "логова снова появляются: " + s.lairs.size() + " (уничтожено всего " + s.lairsDestroyed + ", было " + destroyed + ")");
        Simulation s2 = Simulation.load(s.save());
        ck(s2.save().equals(s.save()), "сохранение после охоты идентично");
        System.exit(f);
    }
}
