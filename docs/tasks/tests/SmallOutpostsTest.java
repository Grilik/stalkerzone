import ru.stalkerzone.sim.*;
import ru.stalkerzone.zonemap.build.Prefabs;

import java.util.*;

/**
 * Карточка 046: население застав «Малого Кордона» — О7 (узел 19), О12 (узел 20) и, если уже есть (059), заставы
 * у входов туннеля (узлы 22, 23): одиночки, 2–4 человека
 * (NodePopulation.number), застава не пустеет надолго; места площадки О7 раздаются по ролям (часовой, повар, сон).
 */
public class SmallOutpostsTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        int[] outposts = Scenarios.create("kordon", 1).nodes.size() >= 24 ? new int[]{19, 20, 22, 23} : new int[]{19, 20};
        int k = outposts.length;
        for (long seed = 1; seed <= 4; seed++) {
            Simulation s = Scenarios.create("kordon", seed);
            Faction lon = s.factions.get(ZoneScenario.LONERS);
            int[] owned = new int[k], badCount = new int[k], empty = new int[k], run = new int[k], lairs = new int[k];
            int[] worst = new int[k], most = new int[k];
            Arrays.fill(worst, Integer.MAX_VALUE);
            for (int d = 1; d <= 60; d++) {
                s.step();
                for (int i = 0; i < k; i++) {
                    Node n = s.nodes.get(outposts[i]);
                    if (s.lairAt(n.id) != null) lairs[i]++;
                    // ничья застава (людей выбили мутанты) — одиночки занимают её снова в пределах 5 дней;
                    // захват другой группировкой — не про эту карточку (группировки Малого Кордона — 054)
                    if (n.owner < 0) { run[i]++; empty[i] = Math.max(empty[i], run[i]); continue; }
                    run[i] = 0;
                    if (n.owner != lon.id) continue;
                    owned[i]++;
                    int num = NodePopulation.number(s, lon, n);
                    worst[i] = Math.min(worst[i], num); most[i] = Math.max(most[i], num);
                    if (num < 2 || num > 4) badCount[i]++;
                }
            }
            for (int i = 0; i < k; i++) {
                String nm = new String[]{"О7", "О12", "южный вход туннеля", "северный вход туннеля"}[i];
                ck(badCount[i] == 0, "сид " + seed + ": " + nm + " — 2–4 человека, когда застава у одиночек (мин " + worst[i] + ", макс " + most[i] + ")");
                ck(owned[i] >= 20, "сид " + seed + ": " + nm + " у одиночек ≥ 20 из 60 дней (" + owned[i] + ")");
                ck(empty[i] <= 5, "сид " + seed + ": " + nm + " не стоит ничьей дольше 5 дней подряд (" + empty[i] + ")");
                ck(lairs[i] == 0, "сид " + seed + ": на заставе " + nm + " логово не заводится (" + lairs[i] + " дн.)");
            }
        }
        // места О7 по ролям: 4 человека на местах площадки wagon — есть часовой и ночлег
        List<Prefabs.Point> pts = Prefabs.points("l01_escape", (x, z) -> 95, Prefabs.POINT_LEVEL, 19);
        String[] kinds = {"guard", "patrol", "cook", "work", "duty", "bed", "rest"};
        int[] kk = new int[kinds.length];
        for (Prefabs.Point p : pts) for (int i = 0; i < kinds.length; i++) if (p.kind().equals(kinds[i])) kk[i]++;
        int[] want = BaseRoles.assign(4, kk);
        ck(want.length >= 1 && want[0] >= 1, "О7: из 4 человек хотя бы один часовой: " + Arrays.toString(want));
        int sum = 0; for (int w : want) sum += w;
        ck(sum == 4, "О7: роли раздаются всем 4: " + Arrays.toString(want));
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
