import ru.stalkerzone.sim.*;
import java.util.Arrays;

/**
 * К2 (правила B/C HQ): состояние уничтоженной группировки и возрождение только вольными сталкерами.
 * <ul>
 *   <li>численность 0 → уничтожена; событие регистрируется один раз, в т.ч. после save/load;</li>
 *   <li>уничтоженная группировка не действует: ни войн, ни караванов, ни гарнизонов, ни решений;</li>
 *   <li>нет автоматического возрождения: ни новичками в пути, ни паломниками Монолита, ни призывом
 *       армии, ни пробуждением Монолита — 60 дней наблюдения;</li>
 *   <li>точка расширения reviveByFreeStalkers: основатели переходят из Одиночков (население сохраняется),
 *       склад и история не трогаются, повторный вызов не дублирует;</li>
 *   <li>длительные сценарии (zone и kordon_war, 365 дней): audit пуст ежедневно, численность
 *       не отрицательна, deaths монотонны, уничтоженные не воскрешают сами себя.</li>
 * </ul>
 * Гибель в бою проверяется CombatDeathsTest; здесь состояние задаётся напрямую (members=0),
 * чтобы изолировать последствия уничтожения.
 */
public class DestroyedFactionTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) throws Exception {
        long t0 = System.currentTimeMillis();
        destructionEventOnce();
        noAutoRevival();
        walkerDoesNotRevive();
        monolithDoesNotRevive();
        armyDoesNotRecruit();
        garrisonsGone();
        revivalExtensionPoint();
        saveLoadKeepsDestroyed();
        soakNoRevival();
        System.out.println("время: " + (System.currentTimeMillis() - t0) / 1000.0 + " с");
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    // ------------------------------------------------------------------ helpers

    static Simulation setup(long seed) {
        Simulation s = ZoneScenario.create(seed);
        s.step(10);
        return s;
    }

    static void destroy(Faction f) {
        f.members = 0;
        f.wounded = 0;
    }

    static boolean logged(Simulation s, String needle) {
        for (String e : s.events) if (e.contains(needle)) return true;
        return false;
    }

    static int ownedBy(Simulation s, Faction f) {
        int c = 0;
        for (Node n : s.nodes) if (n.owner == f.id) c++;
        return c;
    }

    /** Дать склад через Ledger (audit остаётся пустым). */
    static void give(Simulation s, Stock st, long money, int food) {
        st.money += money;
        s.ledger.moneyMinted += money;
        st.add(Good.FOOD, food);
        s.ledger.created[Good.FOOD.ordinal()] += food;
    }

    // ------------------------------------------------------------------ 1. событие уничтожения — один раз

    static void destructionEventOnce() {
        Simulation s = setup(51);
        Faction duty = s.factions.get(ZoneScenario.DUTY);
        destroy(duty);
        ck(duty.isDestroyed(), "событие: members=0 → уничтожена");
        s.step(1);
        ck(logged(s, "«" + duty.name + "» уничтожена"), "событие: журнал зафиксировал уничтожение");
        s.events.clear();
        s.step(3);
        ck(!logged(s, "«" + duty.name + "» уничтожена"), "событие: повторной регистрации нет");
    }

    // ------------------------------------------------------------------ 2. 60 дней без действий и возрождения

    static void noAutoRevival() {
        Simulation s = setup(52);
        Faction duty = s.factions.get(ZoneScenario.DUTY);
        s.caravans.removeIf(c -> c.owner == duty.id);
        destroy(duty);
        int deaths0 = duty.deaths, wars0 = duty.warsStarted;
        boolean membersOk = true, deathsOk = true, warsOk = true, phaseOk = true, caravanOk = true, auditOk = true;
        for (int d = 0; d < 60; d++) {
            s.step();
            if (duty.members != 0) membersOk = false;
            if (duty.deaths != deaths0) deathsOk = false;
            if (duty.warsStarted != wars0) warsOk = false;
            if (duty.phase == Phase.WAR) phaseOk = false;
            for (Caravan c : s.caravans) if (c.owner == duty.id) caravanOk = false;
            if (!s.audit().isEmpty()) auditOk = false;
        }
        ck(membersOk, "60 дней: численность осталась 0 (никто не воскресил)");
        ck(deathsOk, "60 дней: смерти не начисляются мёртвой фракции");
        ck(warsOk, "60 дней: фракция не начинает войн");
        ck(phaseOk, "60 дней: фракция не в состоянии войны");
        ck(caravanOk, "60 дней: фракция не отправляет караваны");
        ck(auditOk, "60 дней: audit пуст ежедневно");
    }

    // ------------------------------------------------------------------ 3. новичок в пути не воскрешает

    static void walkerDoesNotRevive() {
        Simulation s = setup(53);
        Faction duty = s.factions.get(ZoneScenario.DUTY);
        destroy(duty);
        People.Walker w = new People.Walker();
        w.dest = duty.id;
        w.path = new int[]{duty.baseNode};
        w.leg = 0;
        s.people.transit.add(w);
        s.step(1);
        ck(duty.members == 0, "новичок: дошедший до мёртвой базы не воскресил фракцию");
        ck(s.people.transit.isEmpty() || s.people.transit.stream().noneMatch(x -> x.dest == duty.id && x.leg >= x.path.length - 1),
                "новичок: ушёл к другой фракции или из Зоны");
        ck(s.audit().isEmpty(), "новичок: audit пуст");
    }

    // ------------------------------------------------------------------ 4. Монолит: пробуждение и паломники не воскрешают

    static void monolithDoesNotRevive() {
        Simulation s = setup(54);
        Faction mono = s.monolith();
        destroy(mono);
        int owned0 = ownedBy(s, mono);
        s.activateMonolith();
        ck(mono.members == 0, "Монолит: пробуждение не вернуло +42 уничтоженным");
        boolean noNewPosts = true;
        for (int d = 0; d < 12; d++) {
            s.step();
            // враги могут отбирать точки Монолита (уменьшение законно), но он сам не занимает новые
            if (ownedBy(s, mono) > owned0) noNewPosts = false;
        }
        ck(mono.members == 0, "Монолит: паломники не воскресили фракцию за 12 дней");
        ck(noNewPosts, "Монолит: уничтоженные не занимают блокпосты");
        ck(s.audit().isEmpty(), "Монолит: audit пуст");
    }

    // ------------------------------------------------------------------ 5. армия: призыв не воскрешает

    static void armyDoesNotRecruit() {
        Simulation s = setup(55);
        Faction mil = s.factions.get(ZoneScenario.MILITARY);
        destroy(mil);
        give(s, mil.stock, 50_000, 500); // деньги и еда для призыва есть
        for (int d = 0; d < 20; d++) s.step();
        ck(mil.members == 0, "армия: призыв за деньги не воскресил уничтоженных (20 дней)");
        ck(s.audit().isEmpty(), "армия: audit пуст");
    }

    // ------------------------------------------------------------------ 6. гарнизоны исчезли

    static void garrisonsGone() {
        Simulation s = setup(56);
        Faction duty = s.factions.get(ZoneScenario.DUTY);
        destroy(duty);
        boolean zero = true;
        int owned = 0;
        for (Node n : s.nodes) {
            if (n.owner != duty.id) continue;
            owned++;
            if (Logistics.garrison(s, duty, n) != 0) zero = false;
        }
        ck(owned > 0, "гарнизоны: у Долга есть точки для проверки");
        ck(zero, "гарнизоны: фиктивного гарнизона (1 чел.) на точках больше нет");
        ck(Logistics.outside(s, duty) == 0, "гарнизоны: вне базы людей нет");
    }

    // ------------------------------------------------------------------ 7. точка расширения: возрождение вольными сталкерами

    static void revivalExtensionPoint() {
        Simulation s = setup(57);
        Faction duty = s.factions.get(ZoneScenario.DUTY);
        Faction loners = s.mercenaries();
        ck(loners != null && !loners.isDestroyed(), "возрождение: Одиночки живы (инициатор есть)");
        destroy(duty);
        long money0 = duty.stock.money;
        int[] goods0 = duty.stock.qty.clone();
        int loners0 = loners.members;
        long total0 = s.totalMoney();

        ck(s.reviveByFreeStalkers(duty, 3), "возрождение: состоялось");
        ck(duty.members == 3 && !duty.isDestroyed(), "возрождение: численность 3, фракция жива");
        ck(loners.members == loners0 - 3, "возрождение: основатели ушли из Одиночков (население сохранено)");
        ck(duty.phase == Phase.ACCUMULATE && duty.phaseDays == 0 && duty.wounded == 0,
                "возрождение: состояние сброшено (фаза, раненые)");
        ck(duty.stock.money == money0 && Arrays.equals(goods0, duty.stock.qty),
                "возрождение: стартовые ресурсы повторно не начислялись");
        ck(s.totalMoney() == total0 && s.audit().isEmpty(), "возрождение: денежный баланс не тронут");
        ck(!s.reviveByFreeStalkers(duty, 3), "возрождение: повторный вызов — false (без дублирования)");
        ck(!s.reviveByFreeStalkers(s.factions.get(ZoneScenario.FREEDOM), 0), "возрождение: 0 основателей — false");
        ck(!s.reviveByFreeStalkers(s.factions.get(ZoneScenario.FREEDOM), loners.members + 1),
                "возрождение: не хватает основателей — false");
        ck(s.reviveByFreeStalkers(null, 3) == false, "возрождение: null — false");

        give(s, duty.stock, 5000, 200); // еда и деньги для штатного роста
        for (int d = 0; d < 30; d++) s.step();
        ck(duty.members > 0, "возрождение: фракция живёт и растёт штатно (30 дней, факт " + duty.members + ")");
        ck(!s.people.of(duty.id).isEmpty(), "возрождение: именные восстановлены штатным maintainRoster");
        ck(s.people.leader(duty.id) != null, "возрождение: лидер восстановлен");
        ck(s.audit().isEmpty(), "возрождение: audit пуст после 30 дней");
    }

    // ------------------------------------------------------------------ 8. save/load: состояние и однократность события

    static void saveLoadKeepsDestroyed() {
        Simulation s = setup(58);
        Faction duty = s.factions.get(ZoneScenario.DUTY);
        destroy(duty);
        s.step(1);
        int deaths0 = duty.deaths;
        String save = s.save();
        Simulation l = Simulation.load(save);
        Faction ld = l.factions.get(ZoneScenario.DUTY);
        ck(ld.members == 0 && ld.isDestroyed(), "save/load: уничтоженная фракция загружена уничтоженной");
        ck(ld.deaths == deaths0, "save/load: история потерь сохранена");
        l.events.clear();
        l.step(1);
        ck(!logged(l, "«" + ld.name + "» уничтожена"), "save/load: уничтожение не зарегистрировано повторно");
        s.events.clear(); // журнал входит в сохранение: сравниваем симметрично (только новые события дня)
        s.step(1);
        ck(s.save().equals(l.save()), "save/load: поведение после загрузки идентично (детерминизм)");
    }

    // ------------------------------------------------------------------ 9. длительные сценарии

    static void soakNoRevival() {
        soak("zone", 61, 365);
        soak("kordon_war", 62, 365);
    }

    static void soak(String scenario, long seed, int days) {
        Simulation s = Scenarios.create(scenario, seed);
        int n = s.factions.size();
        int[] prevDeaths = new int[n];
        boolean[] wasDestroyed = new boolean[n];
        for (Faction f : s.factions) {
            prevDeaths[f.id] = f.deaths;
            wasDestroyed[f.id] = f.isDestroyed(); // пустые фракции сценария не считаются новыми уничтожениями
        }
        String bad = null;
        int destructions = 0;
        for (int d = 0; d < days && bad == null; d++) {
            s.step();
            String aud = s.audit();
            if (!aud.isEmpty()) { bad = "д" + s.day + ": audit " + aud.trim(); break; }
            for (Faction f : s.factions) {
                if (f.members < 0) { bad = "д" + s.day + ": " + f.name + " members=" + f.members; break; }
                if (f.wounded > f.members) { bad = "д" + s.day + ": " + f.name + " wounded=" + f.wounded + " > members=" + f.members; break; }
                if (f.deaths < prevDeaths[f.id]) { bad = "д" + s.day + ": " + f.name + " deaths уменьшился"; break; }
                prevDeaths[f.id] = f.deaths;
                if (f.isService()) continue;
                if (f.members == 0 && !wasDestroyed[f.id]) { wasDestroyed[f.id] = true; destructions++; }
                if (wasDestroyed[f.id] && f.members > 0) { bad = "д" + s.day + ": " + f.name + " воскресла сама (members=" + f.members + ")"; break; }
            }
        }
        ck(bad == null, "соак " + scenario + " seed " + seed + " " + days + " дн: инварианты целы" + (bad == null ? "" : " — " + bad));
        System.out.println(scenario + " seed " + seed + ": уничтожений за прогон — " + destructions);
    }
}