import java.nio.file.Files;
import java.nio.file.Path;

import ru.stalkerzone.client.ZoneButtonLayout;

/**
 * Э1-11Б: кнопка «Зона» в меню создания мира. Раскладка (чистый расчёт): слева от ванильного
 * «Создать новый мир», на узких экранах — справа от «Отмена» или над кнопкой создания;
 * кнопка всегда в пределах экрана и не перекрывает ванильные. Обвязка клиента — по исходнику.
 */
public class ZoneButtonLayoutTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }
    static String read(String p) throws Exception { return Files.readString(Path.of(p)).replace("\r\n", "\n"); }

    static boolean overlaps(int x, int y, int w, int h, int x2, int y2, int w2, int h2) {
        return x < x2 + w2 && x2 < x + w && y < y2 + h2 && y2 < y + h;
    }

    public static void main(String[] a) throws Exception {
        // типичный экран 854×480: ванильный ряд по центру (310 = 150+10+150), y = height-28
        int[] p = ZoneButtonLayout.place(854, 272, 452, 150, 432, 150);
        ck(p[0] == 162 && p[1] == 452, "широкий экран: кнопка слева от «Создать новый мир»");
        // 600: слева ещё помещается
        p = ZoneButtonLayout.place(600, 145, 212, 150, 305, 150);
        ck(p[0] == 35 && p[1] == 212, "средний экран: слева с зазором");
        // 480 (GUI scale 4 на 1080p): слева не помещается, справа тоже — над кнопкой создания
        p = ZoneButtonLayout.place(480, 85, 242, 150, 245, 150);
        ck(p[0] == 85 && p[1] == 218, "узкий экран: над «Создать новый мир», без наложения");
        // ряд смещён влево (нестандартная раскладка): слева не помещается, справа — да
        p = ZoneButtonLayout.place(530, 100, 200, 150, 260, 150);
        ck(p[0] == 420 && p[1] == 200, "смещённый ряд: справа от «Отмена»");

        // инварианты для всех ширин: в пределах экрана, без наложения на ванильные кнопки
        boolean okBounds = true, okOverlap = true;
        for (int w = 320; w <= 1920; w += 8) {
            int h = 240 + (w % 97);
            int cx = (w - 310) / 2, cy = h - 28, ax = cx + 160;
            int[] q = ZoneButtonLayout.place(w, cx, cy, 150, ax, 150);
            int x = q[0], y = q[1];
            if (x < 0 || y < 0 || x + ZoneButtonLayout.WIDTH > w || y + ZoneButtonLayout.HEIGHT > h) okBounds = false;
            boolean sideBySide = y == cy;
            if (sideBySide && (overlaps(x, y, 100, 20, cx, cy, 150, 20) || overlaps(x, y, 100, 20, ax, cy, 150, 20))) okOverlap = false;
            if (!sideBySide && y + 20 > cy) okOverlap = false; // над рядом — не задевает его
        }
        ck(okBounds, "ширины 320..1920: кнопка всегда в пределах экрана");
        ck(okOverlap, "ширины 320..1920: ванильные кнопки не перекрываются");

        // обвязка клиента по исходнику (Minecraft-классы в javac-набор не входят)
        String zc = read("src/main/java/ru/stalkerzone/client/ZoneCreateWorld.java");
        ck(zc.contains("instanceof CreateWorldScreen") && zc.contains("ScreenEvent.Init.Post"),
                "кнопка добавляется в реальное меню создания мира (ScreenEvent.Init.Post)");
        ck(zc.contains("e.addListener(") && zc.contains("ZoneButtonLayout.place"), "кнопка регистрируется через раскладку");
        ck(zc.contains("Dist.CLIENT"), "обработчик только клиентский");
        String zs = read("src/main/java/ru/stalkerzone/client/ZoneWorldScreen.java");
        ck(zs.contains("GoldenCopy.createWorld"), "создание мира — из золотой копии (ядро Э1-11А)");
        ck(zs.contains("createWorldOpenFlows().loadLevel"), "игрок попадает в созданный мир (ванильная загрузка)");
        ck(zs.contains("access.renameLevel(folder)"), "имя мира — ванильным механизмом level.dat");
        ck(zs.contains("new Thread(") && zs.contains("setDaemon"), "копирование в фоне, клиент не блокируется");
        System.out.println(f == 0 ? "ZoneButtonLayoutTest: всё зелёное" : "ZoneButtonLayoutTest: ПРОВАЛ " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
