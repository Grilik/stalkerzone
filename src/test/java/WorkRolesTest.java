import ru.stalkerzone.sim.BaseRoles;
import ru.stalkerzone.zonemap.build.Prefabs;

import java.util.ArrayList;
import java.util.List;

/**
 * Тест распределения ролей NPC базы (карточка 032): чистая Java, без Minecraft.
 * Проверяет решение «точки + число людей → роли» и точки Южного блокпоста.
 */
public class WorkRolesTest {
    static int f = 0;

    static void ck(boolean ok, String m) {
        System.out.println((ok ? "OK   " : "FAIL ") + m);
        if (!ok) f++;
    }

    public static void main(String[] a) {
        testAssign();
        testSouthPostPoints();
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    /** Правила: все guard (не больше половины), 1–2 patrol, 1 cook, 1 duty, остальные resident/mechanic. */
    private static void testAssign() {
        // kinds: [guard, patrol, cook, work, duty, bed, rest]
        // 8 человек на блокпосту с 3 вышками, 6 patrol, 1 cook, 2 work, 1 duty
        int[] kinds = {3, 6, 1, 2, 1, 10, 2};
        int[] got = BaseRoles.assign(8, kinds);
        // got: [guard, patrol, cook, mechanic, duty, resident]
        int guard = got[0], patrol = got[1], cook = got[2], mechanic = got[3], duty = got[4], resident = got[5];

        ck(guard == 3, "3 guard (3 вышки, 8/2=4) → " + guard);
        ck(patrol >= 1 && patrol <= 2, "1–2 patrol → " + patrol);
        ck(cook == 1, "1 cook → " + cook);
        ck(duty == 1, "1 duty → " + duty);
        ck(mechanic >= 1 && mechanic <= 2, "1–2 mechanic → " + mechanic);
        int sum = guard + patrol + cook + mechanic + duty + resident;
        ck(sum == 8, "сумма = 8 → " + sum);
        ck(guard <= kinds[0], "guards не больше вышек (" + guard + " <= " + kinds[0] + ")");
        ck(guard <= 8 / 2, "guards не больше половины людей (" + guard + " <= 4)");

        // 2 человека на блокпосту: 1 guard, остальное — жители
        got = BaseRoles.assign(2, kinds);
        guard = got[0]; resident = got[5];
        ck(guard == 1, "2 чел → 1 guard (min(3, 2/2=1) = 1) → " + guard);
        ck(got[0] + got[1] + got[2] + got[3] + got[4] + got[5] == 2, "2 чел сумма 2");
        ck(resident >= 0, "остальные — жители");

        // 16 человек: guards всё равно не больше половины (8)
        got = BaseRoles.assign(16, kinds);
        guard = got[0]; resident = got[5];
        ck(guard <= 8, "16 чел → guards не больше 8 → " + guard);
        sum = guard + got[1] + got[2] + got[3] + got[4] + resident;
        ck(sum == 16, "16 чел сумма = 16 → " + sum);

        // без вышек: guards = 0
        int[] noGuardKinds = {0, 1, 1, 1, 1, 5, 1};
        got = BaseRoles.assign(5, noGuardKinds);
        ck(got[0] == 0, "без вышек → guards=0 → " + got[0]);

        // без мест у техники: mechanics = 0
        int[] noWorkKinds = {1, 1, 1, 0, 1, 3, 1};
        got = BaseRoles.assign(4, noWorkKinds);
        ck(got[3] == 0, "без work-точек → mechanics=0 → " + got[3]);

        // только жители
        int[] bedOnly = {0, 0, 0, 0, 0, 5, 0};
        got = BaseRoles.assign(3, bedOnly);
        sum = got[0] + got[1] + got[2] + got[3] + got[4] + got[5];
        ck(sum == 3, "только bed → все resident (сумма " + sum + ")");
        ck(got[5] == 3, "3 resident → " + got[5]);
    }

    /** Точки Южного блокпоста через сборщик. */
    private static void testSouthPostPoints() {
        // земля: всё на y=70 для простоты
        Prefabs.Ground g = (x, z) -> 70;
        List<Prefabs.Point> pts = Prefabs.points("l01_escape", g);

        List<Prefabs.Point> guards = ofKind(pts, "guard");
        List<Prefabs.Point> beds = ofKind(pts, "bed");
        List<Prefabs.Point> patrols = ofKind(pts, "patrol");
        List<Prefabs.Point> cooks = ofKind(pts, "cook");
        List<Prefabs.Point> rests = ofKind(pts, "rest");
        List<Prefabs.Point> works = ofKind(pts, "work");
        List<Prefabs.Point> duties = ofKind(pts, "duty");

        ck(guards.size() >= 3, "≥3 guard на блокпосту → " + guards.size());
        ck(beds.size() >= 10, "≥10 bed → " + beds.size());
        ck(patrols.size() >= 4, "≥4 patrol → " + patrols.size());
        ck(cooks.size() >= 1, "≥1 cook → " + cooks.size());
        ck(rests.size() >= 2, "≥2 rest → " + rests.size());
        ck(works.size() >= 1, "≥1 work → " + works.size());
        ck(duties.size() >= 1, "≥1 duty → " + duties.size());

        // Все точки в границах замены (список сборщиков уровня, карточка 035)
        for (Prefabs.Point p : pts) {
            boolean in = false;
            for (int[] cl : Prefabs.clear("l01_escape"))
                if (p.x() >= cl[0] && p.x() <= cl[2] && p.z() >= cl[1] && p.z() <= cl[3]) in = true;
            ck(in, "точка " + p.kind() + " (" + p.x() + "," + p.z() + ") в одной из зон CLEAR");
        }

        // Не больше 8 точек на guard (3 вышки)
        ck(guards.size() <= 8, "guards не больше 8 → " + guards.size());
    }

    private static List<Prefabs.Point> ofKind(List<Prefabs.Point> pts, String kind) {
        List<Prefabs.Point> out = new ArrayList<>();
        for (Prefabs.Point p : pts) if (p.kind().equals(kind)) out.add(p);
        return out;
    }
}