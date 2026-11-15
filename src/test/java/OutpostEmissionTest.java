import ru.stalkerzone.sim.*;

/**
 * Э0-17 (D6): выброс сам по себе заставы одиночек на Кордоне не уничтожает — потеря только набегом, захватом, войной.
 * Сценарий kordon (без войны): 10 выбросов подряд — владельцы узлов 4, 19, 20, 22, 23 не меняются.
 */
public class OutpostEmissionTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        int[] watch = {4, 19, 20, 22, 23};
        for (long seed = 1; seed <= 5; seed++) {
            Simulation s = Scenarios.create("kordon", seed);
            for (int d = 0; d < 3; d++) s.step();
            int[] own = new int[watch.length];
            for (int i = 0; i < watch.length; i++) own[i] = s.nodes.get(watch[i]).owner;
            for (int k = 0; k < 10; k++) s.triggerEmission();
            for (int i = 0; i < watch.length; i++) {
                Node n = s.nodes.get(watch[i]);
                ck(n.owner == own[i], "сид " + seed + ": «" + n.name + "» (узел " + n.id + ") после 10 выбросов у прежнего владельца "
                        + own[i] + " (сейчас " + n.owner + ")");
            }
            ck(s.audit().isEmpty(), "сид " + seed + ": аудит пуст");
        }
        System.out.println(f == 0 ? "ALL OK" : "FAILED " + f);
        if (f > 0) System.exit(1);
    }
}
