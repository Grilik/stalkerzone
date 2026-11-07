import ru.stalkerzone.sim.*;
import ru.stalkerzone.zonemap.build.*;

/**
 * Карточка 066 (решение пользователя 06.11, 23:50): элеватор (узел 4) в «Малом Кордоне» — малая застава одиночек:
 * тип OUTPOST, одиночки с начала, 3–4 человека, логово там не заводится. Укрытие от выброса — место «shelter»
 * площадки elevator (её подключает «Строитель» в П-22; пока площадки нет, проверка укрытия пропускается).
 * В kordon_war элеватор прежний (RUINS, ничей).
 */
public class ElevatorOutpostTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        for (long seed = 1; seed <= 3; seed++) {
            Simulation s = Scenarios.create("kordon", seed);
            Node e = s.nodes.get(4);
            Faction lon = s.factions.get(ZoneScenario.LONERS);
            ck(e.type == NodeType.OUTPOST && e.owner == lon.id, "сид " + seed + ": элеватор — застава (OUTPOST) одиночек");
            boolean noLair = false;
            for (int x : s.lairForbidden) if (x == 4) noLair = true;
            ck(noLair, "сид " + seed + ": на элеваторе логово не заводится");
            boolean pop = true;
            int owned = 0, lo = 99, hi = 0;
            for (int d = 0; d < 60; d++) {
                s.step();
                if (s.lairAt(4) != null) noLair = false;
                if (e.owner != lon.id) continue;
                owned++;
                int n = NodePopulation.number(s, lon, e);
                lo = Math.min(lo, n); hi = Math.max(hi, n);
                if (n < 3 || n > 4) pop = false;
            }
            ck(pop && owned >= 30, "сид " + seed + ": на элеваторе 3–4 человека (мин " + lo + ", макс " + hi + ", дней у одиночек " + owned + ")");
        }
        Site site = Prefabs.site("l01_escape", 4);
        if (site == null) ck(true, "площадки elevator ещё нет (П-22) — укрытие проверим после неё");
        else ck(Prefabs.points("l01_escape", (x, z) -> 90, Prefabs.POINT_LEVEL, 4).stream().anyMatch(p -> p.kind().equals("shelter")),
                "у площадки elevator есть укрытие shelter");
        Simulation w = Scenarios.create("kordon_war", 1);
        ck(w.nodes.get(4).type == NodeType.RUINS && w.nodes.get(4).owner < 0, "kordon_war: элеватор прежний");
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
