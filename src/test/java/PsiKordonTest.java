import ru.stalkerzone.sim.*;

/** Пси-поле не действует на Кордоне (карточка 024). */
public class PsiKordonTest {
    static int f = 0;

    static void ck(boolean ok, String m) {
        System.out.println((ok ? "OK   " : "FAIL ") + m);
        if (!ok) f++;
    }

    public static void main(String[] a) {
        // Кордон 1.0: Радара и Выжигателя на локации нет — пси на поверхности везде 0
        Simulation k = Scenarios.create("kordon", 0);
        ck(k.scenario.equals("kordon"), "сценарий kordon: " + k.scenario);
        ck(Psi.surfaceLevel(k, 282, -288) == 0, "Кордон (282,-288): пси нет");
        ck(Psi.surfaceLevel(k, 0, 0) == 0, "Кордон (0,0): пси нет");
        ck(Psi.surfaceLevel(k, 210, -420) == 0, "Кордон (210,-420): пси нет");

        // Полная Зона: прежнее поведение — у источника Выжигателя уровень > 0
        Simulation z = Scenarios.create("zone", 0);
        ck(z.scenario.equals("zone"), "сценарий zone: " + z.scenario);
        ck(Psi.surfaceLevel(z, 0, 0) == 0, "Зона (0,0): далеко от пси-поля");
        ck(Psi.surfaceLevel(z, 860, -5160) > 0, "Зона (860,-5160): точка внутри пси-поля");
        ck(Psi.surfaceLevel(z, 860, -5160) == 3, "Зона у Выжигателя: смертельный уровень 3");

        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
