import ru.stalkerzone.sim.*;
import java.util.Base64;

/**
 * Э0-14a (D3): симуляция сообщает наружу, что пересоздана из-за несовместимой версии сохранения.
 * Simulation.SAVE_VERSION — одна константа вместо литералов; resetFromVersion = версия сброшенного сохранения, иначе −1.
 */
public class SaveResetTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    static String withVersion(String data, int v) {
        byte[] b = Base64.getDecoder().decode(data);
        b[0] = (byte) (v >>> 24); b[1] = (byte) (v >>> 16); b[2] = (byte) (v >>> 8); b[3] = (byte) v;
        return Base64.getEncoder().encodeToString(b);
    }

    static int firstInt(String data) {
        byte[] b = Base64.getDecoder().decode(data);
        return ((b[0] & 255) << 24) | ((b[1] & 255) << 16) | ((b[2] & 255) << 8) | (b[3] & 255);
    }

    public static void main(String[] a) {
        ck(Simulation.SAVE_VERSION == 22, "SAVE_VERSION = 22");
        Simulation s = Scenarios.create("kordon", 7);
        for (int d = 0; d < 10; d++) s.step();
        String data = s.save();
        ck(firstInt(data) == Simulation.SAVE_VERSION, "сохранение пишет SAVE_VERSION");

        Simulation same = Simulation.load(data);
        ck(same.resetFromVersion == -1 && same.day == 10, "своя версия: без сброса (resetFromVersion −1, день 10)");

        Simulation r = Simulation.load(withVersion(data, 21));
        ck(r.resetFromVersion == 21, "версия 21 на Кордоне: resetFromVersion = 21 (" + r.resetFromVersion + ")");
        ck(r.day == 0, "версия 21: симуляция пересоздана (день 0)");

        Simulation z = Scenarios.create("zone", 3);
        for (int d = 0; d < 3; d++) z.step();
        Simulation zr = Simulation.load(withVersion(z.save(), 11));
        ck(zr.resetFromVersion == 11 && zr.day == 0, "версия 11 в Зоне: пересоздана, resetFromVersion = 11");
        Simulation zs = Simulation.load(z.save());
        ck(zs.resetFromVersion == -1 && zs.day == 3, "Зона своей версии: без сброса");

        System.out.println(f == 0 ? "ALL OK" : "FAILED " + f);
        if (f > 0) System.exit(1);
    }
}
