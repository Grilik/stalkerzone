import ru.stalkerzone.gear.TaczWeight;
import ru.stalkerzone.sim.*;
import ru.stalkerzone.zonemap.build.*;

import java.util.*;

/**
 * Карточка 056: пулемётчик южного блокпоста. Место «mg» сборщик ставит только с уровня 2 (П-20, PR #99).
 * BaseRoles.MG — роль пулемётчика; BaseRoles.machineGunners(людей, мест mg) — сколько пулемётчиков
 * (1, если есть место mg и людей не меньше 3, иначе 0); BaseRoles.MG_GUN — пулемёт TaCZ (ПКМ).
 */
public class MachineGunnerTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    static long mg(int lvl) {
        return Prefabs.points("l01_escape", (x, z) -> 90, lvl, Prefabs.NODE_SOUTH_POST).stream().filter(p -> p.kind().equals("mg")).count();
    }

    public static void main(String[] a) {
        ck(BaseRoles.MG != BaseRoles.GUARD && BaseRoles.MG > BaseRoles.RESIDENT, "роль MG — новая, после RESIDENT: " + BaseRoles.MG);
        ck(TaczWeight.gunType(BaseRoles.MG_GUN).equals("mg"), "оружие пулемётчика — пулемёт: " + BaseRoles.MG_GUN);
        ck(mg(1) == 0 && mg(0) == 0, "ур. 0–1: места mg нет");
        ck(mg(2) == 1 && mg(3) == 1, "ур. 2–3: одно место mg");
        ck(BaseRoles.machineGunners(10, (int) mg(1)) == 0, "ур. 1: пулемётчика нет");
        ck(BaseRoles.machineGunners(10, (int) mg(2)) == 1, "ур. 2: один пулемётчик");
        ck(BaseRoles.machineGunners(2, 1) == 0, "двое на посту — пулемёт пустует (нужно ≥ 3 человек)");
        ck(BaseRoles.machineGunners(3, 1) == 1 && BaseRoles.machineGunners(30, 3) == 1, "пулемётчик один, даже если мест mg больше");
        System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
