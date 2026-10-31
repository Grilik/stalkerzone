import ru.stalkerzone.sim.PdaSnapshot;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.sim.Scenarios;
import ru.stalkerzone.world.ZoneBounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Формат снимка КПК для Кордона (карточка 015). Снимок собирает чистая {@link PdaSnapshot} — она же
 * используется серверной частью {@code PdaService}, поэтому тест проверяет настоящий формат.
 * Обязательное: метки трёх проходов (с ценой и опасностью) и метка Сидоровича.
 */
public class PdaKordonTest {
    static int f = 0;

    static void ck(boolean ok, String m) {
        System.out.println((ok ? "OK   " : "FAIL ") + m);
        if (!ok) f++;
    }

    public static void main(String[] a) {
        findsProchody();
        findsSidorovich();
        formatIntact();
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    /** Снимок Кордона: три прохода с ценой (только мост платный) и опасностью. */
    static void findsProchody() {
        Simulation s = Scenarios.create("kordon", 1);
        String text = PdaSnapshot.build(s, "Тестер", s.day, 0, 0, 0, 0, ZoneBounds.fromNodes(s.nodes), 120);
        List<String[]> pass = lines(text, "X");
        ck(pass.size() == 3, "снимок КПК: метки трёх проходов через насыпь (нашли " + pass.size() + ")");
        ck(has(pass, "Туннель с электрами"), "снимок КПК: проход «Туннель с электрами» есть");
        ck(has(pass, "Разрушенный мост"), "снимок КПК: проход «Разрушенный мост» есть");
        ck(has(pass, "Дыра в заборе"), "снимок КПК: проход «Дыра в заборе» есть");
        // мост — единственный платный проход: цена из аргумента, у остальных ноль
        for (String[] p : pass) ck(p.length == 7, "снимок КПК: строка прохода «" + p[3] + "» из 7 полей");
        String[] bridge = get(pass, "Разрушенный мост");
        String[] tunnel = get(pass, "Туннель с электрами");
        String[] hole = get(pass, "Дыра в заборе");
        ck(bridge != null && "120".equals(bridge[4]), "снимок КПК: пошлина за мост 120 руб.");
        ck(tunnel != null && "0".equals(tunnel[4]), "снимок КПК: туннель без пошлины");
        ck(hole != null && "0".equals(hole[4]), "снимок КПК: дыра без пошлины");
        ck(tunnel != null && "1".equals(tunnel[5]), "снимок КПК: опасность туннеля — средняя (1)");
        ck(bridge != null && "0".equals(bridge[5]), "снимок КПК: опасность моста — низкая (0)");
        ck(hole != null && "2".equals(hole[5]), "снимок КПК: опасность дыры — высокая (2)");
    }

    /** Снимок Кордона: Сидорович в бункере торговцев, координаты — из узла сценария. */
    static void findsSidorovich() {
        Simulation s = Scenarios.create("kordon", 1);
        String text = PdaSnapshot.build(s, "Тестер", s.day, 0, 0, 0, 0, ZoneBounds.fromNodes(s.nodes), 0);
        List<String[]> traders = lines(text, "S");
        ck(traders.size() == 1, "снимок КПК: на Кордоне один торговец (нашли " + traders.size() + ")");
        String[] t = traders.isEmpty() ? null : traders.get(0);
        ck(t != null && t.length == 4, "снимок КПК: строка торговца из 4 полей");
        ck(t != null && "Сидорович".equals(t[3]), "снимок КПК: метка «Сидорович» есть");
        int bunker = -1;
        for (int i = 0; i < s.nodes.size(); i++) if ("Бункер Сидоровича".equals(s.nodes.get(i).name)) bunker = i;
        ck(bunker >= 0 && t != null && t[1].equals(String.valueOf(s.nodes.get(bunker).x * PdaSnapshot.SCALE))
                && t[2].equals(String.valueOf(s.nodes.get(bunker).z * PdaSnapshot.SCALE)),
                "снимок КПК: Сидорович стоит на узле бункера");
        ck(bunker >= 0 && s.nodes.get(bunker).trader, "снимок КПК: узел бункера помечен как торговый");
    }

    /** Строки снимка целы: известные типы, фиксированное число полей там, где оно задано. */
    static void formatIntact() {
        Simulation s = Scenarios.create("kordon", 2);
        String text = PdaSnapshot.build(s, "Тестер", s.day, 0, 100, -200, 500, ZoneBounds.fromNodes(s.nodes), 90);
        String known = "DPBNGFHTMECLRAZXSYW";
        boolean bad = false;
        int nodes = 0;
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) continue;
            char type = line.charAt(0);
            if (known.indexOf(type) < 0 || line.length() < 2 || line.charAt(1) != '\t') { bad = true; break; }
        }
        ck(!bad, "снимок КПК: все строки начинаются с известного типа и табуляции");
        nodes = lines(text, "N").size();
        ck(nodes == s.nodes.size(), "снимок КПК: узлов в снимке столько же, сколько в симуляции (" + nodes + ")");
        ck(!lines(text, "D").isEmpty() && lines(text, "D").get(0).length == 3, "снимок КПК: строка дня из 3 полей");
        ck(!lines(text, "B").isEmpty() && lines(text, "B").get(0).length == 5, "снимок КПК: границы из 5 полей");
        ck(!lines(text, "F").isEmpty(), "снимок КПК: группировки есть");
        ck(!lines(text, "Z").isEmpty(), "снимок КПК: известные логова есть");
    }

    static List<String[]> lines(String text, String type) {
        List<String[]> out = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (line.startsWith(type + "\t")) out.add(line.split("\t", -1));
        }
        return out;
    }

    static boolean has(List<String[]> rows, String name) {
        return get(rows, name) != null;
    }

    static String[] get(List<String[]> rows, String name) {
        for (String[] r : rows) if (r.length > 3 && name.equals(r[3])) return r;
        return null;
    }
}
