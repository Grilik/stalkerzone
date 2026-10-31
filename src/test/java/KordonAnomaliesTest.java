import ru.stalkerzone.sim.*;

import java.util.*;

/**
 * Аномалии, радиация и выброс на Кордоне (карточка 013).
 * Состав полей по районам, неизменность районов после выброса, электрические артефакты туннеля.
 */
public class KordonAnomaliesTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    // узлы Кордона (порядок из KordonScenario)
    static final int VILLAGE = 0, ELEVATOR = 4, TUNNEL = 7, BRIDGE = 8, HOLE = 9, FARM = 10, RAVINE = 16, EDGE = 18;

    public static void main(String[] a) {
        composition();
        districtsCoverNodes();
        afterEmission();
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    /** Состав полей по районам: туннель — электр, дыра — гравитационные, разброс — жарки, туман, трамплины. */
    static void composition() {
        boolean total = true, tunnel = true, tunnelTypes = true, hole = true, holeTypes = true;
        boolean edge = true, edgeTypes = true, elevator = true, ravine = true;
        for (long seed = 1; seed <= 8; seed++) {
            Simulation s = Scenarios.create("kordon", seed);
            if (s.anomalies.size() < 15) total = false;

            List<Anomaly> t = s.anomaliesAt(TUNNEL);
            if (t.size() < 6 || t.size() > 9) tunnel = false;
            for (Anomaly an : t) if (an.type != AnomalyType.ELECTRA) tunnelTypes = false;

            List<Anomaly> h = s.anomaliesAt(HOLE);
            if (h.size() < 5 || h.size() > 7) hole = false;
            for (Anomaly an : h) if (!(an.type == AnomalyType.SPRINGBOARD || an.type == AnomalyType.WHIRLIGIG
                    || an.type == AnomalyType.MOSQUITO)) holeTypes = false;

            List<Anomaly> e = s.anomaliesAt(EDGE);
            if (e.size() < 2 || e.size() > 4) edge = false;
            for (Anomaly an : e) if (!(an.type == AnomalyType.SPRINGBOARD || an.type == AnomalyType.MOSQUITO)) edgeTypes = false;

            List<Anomaly> el = s.anomaliesAt(ELEVATOR);
            if (el.size() < 1 || el.size() > 2) elevator = false;
            for (Anomaly an : el) if (an.type != AnomalyType.FRY) elevator = false;

            List<Anomaly> rv = s.anomaliesAt(RAVINE);
            if (rv.size() != 1 || rv.get(0).type != AnomalyType.FOG) ravine = false;
        }
        ck(total, "на Кордоне не меньше 15 аномалий");
        ck(tunnel && tunnelTypes, "туннель — поле из 6–9 электр");
        ck(hole && holeTypes, "дыра в заборе — трамплины, карусели, плеши");
        ck(edge && edgeTypes, "у дороги на Свалку — 2–4 трамплина/плеши");
        ck(elevator, "у сгоревших домов — 1–2 жарки");
        ck(ravine, "в овраге — один туман");
    }

    /** Каждое поле района — из набора района; фоновая плотность Зоны на прочих узлах сохраняется. */
    static void districtsCoverNodes() {
        boolean ok = true, background = true;
        for (long seed = 1; seed <= 8; seed++) {
            Simulation s = Scenarios.create("kordon", seed);
            for (Anomaly an : s.anomalies) {
                AnomalyDistrict d = s.districtAt(an.node);
                if (d == null) continue;
                boolean in = false;
                for (AnomalyType t : d.pool) if (t == an.type) in = true;
                if (!in) ok = false;
            }
            // у каждого района есть поля, и на узлах без района Зона остаётся живой
            for (AnomalyDistrict d : s.anomalyDistricts) if (s.anomaliesAt(d.node).isEmpty()) ok = false;
            if (s.anomaliesAt(VILLAGE).size() < 2) background = false;
        }
        ck(ok, "поле района всегда из набора района, и у каждого района есть поля");
        ck(background, "вне районов сохраняется фоновая плотность аномалий Зоны");
    }

    /** После выброса поля остались в своих районах, туннель — электр, артефакты зреют заново. */
    static void afterEmission() {
        boolean sameNode = true, tunnel = true, aromatic = true, fresh = true, audit = true, districts = true;
        for (long seed = 1; seed <= 8; seed++) {
            Simulation s = Scenarios.create("kordon", seed);
            s.step(20);
            int[] was = new int[s.anomalies.size()];
            boolean[] hadDistrict = new boolean[s.anomalies.size()];
            for (int i = 0; i < s.anomalies.size(); i++) {
                was[i] = s.anomalies.get(i).node;
                hadDistrict[i] = s.districtAt(was[i]) != null;
            }
            for (Anomaly an : s.anomalies) an.pending = 0;

            s.triggerEmission();
            s.triggerEmission();
            s.triggerEmission(); // несколько выбросов подряд: районы держат состав

            for (int i = 0; i < s.anomalies.size(); i++) if (s.anomalies.get(i).node != was[i]) sameNode = false;
            for (int i = 0; i < s.anomalies.size(); i++) {
                Anomaly an = s.anomalies.get(i);
                if (an.pending < 1) fresh = false;
                if (!hadDistrict[i]) continue;
                AnomalyDistrict d = s.districtAt(an.node);
                if (d == null) { districts = false; continue; }
                boolean in = false;
                for (AnomalyType ty : d.pool) if (ty == an.type) in = true;
                if (!in) districts = false;
                if (an.node == TUNNEL) {
                    if (an.type != AnomalyType.ELECTRA) tunnel = false;
                    if (!an.type.artifact.equals("Вспышка")) aromatic = false;
                }
            }
            if (!s.audit().isEmpty()) { audit = false; System.out.println("аудит: " + s.audit()); }
        }
        ck(sameNode, "после выбросов аномалии не покинули свои узлы");
        ck(districts, "после выбросов все аномалии по-прежнему в районах");
        ck(tunnel, "туннель после выброса остаётся полем электр");
        ck(aromatic, "артефакты туннеля — электрические («Вспышка» на все поля)");
        ck(fresh, "после выброса в каждом поле созрел свежий артефакт");
        ck(audit, "аудит сходится после выбросов");
    }
}
