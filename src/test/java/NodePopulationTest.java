import ru.stalkerzone.sim.NodePopulation;

/**
 * Тест формулы числа NPC узла (карточка 033): чистая Java, без Minecraft.
 * Проверяет границы [минимум, потолок] и учёт ушедших на задание.
 */
public class NodePopulationTest {
    static int f = 0;

    static void ck(boolean ok, String m) {
        System.out.println((ok ? "OK   " : "FAIL ") + m);
        if (!ok) f++;
    }

    public static void main(String[] a) {
        // базовый случай из карточки: потолок базы 12
        ck(NodePopulation.visible(30, 0, true) == 12, "гарнизон 30 на базе → 12 (потолок)");
        // обычный узел: 5 человек — без обрезки
        ck(NodePopulation.visible(5, 0, false) == 5, "гарнизон 5 → 5");
        // ушедшие на задание вычитаются
        ck(NodePopulation.visible(5, 3, false) == 2, "ушли 3 из 5 → 2");
        // минимум 2 на занятом узле, даже если ушли все
        ck(NodePopulation.visible(4, 4, false) == 2, "ушли все (4 из 4) → минимум 2");
        // потолок обычного узла 6
        ck(NodePopulation.visible(30, 0, false) == 6, "гарнизон 30 на блокпосту → 6 (потолок)");
        // пустой гарнизон — никого
        ck(NodePopulation.visible(0, 0, true) == 0, "пустой гарнизон → 0");
        ck(NodePopulation.visible(0, 3, false) == 0, "пустой гарнизон с ушедшими → 0");

        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
