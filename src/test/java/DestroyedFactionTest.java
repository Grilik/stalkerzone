import ru.stalkerzone.sim.*;
import java.util.Arrays;

/**
 * К2 (правила B/C HQ): состояние уничтоженной группировки и возрождение только вольными сталкерами.
 * <ul>
 *   <li>численность 0 → уничтожена; событие регистрируется один раз, в т.ч. после save/load;</li>
 *   <li>уничтоженная группировка не действует: ни войн, ни караванов, ни гарнизонов, ни решений;</li>
 *   <li>нет автоматического возрождения: ни новичками в пути, ни паломниками Монолита, ни призывом
 *       армии, ни пробуждением Монолита — 60 дней наблюдения;</li>
 *   <li>точка расширения reviveByFreeStalkers: основатели — только люди без группировки
 *       (Person.faction == Person.NONE, вольные сталкеры; docs/spec/faction-hierarchy.md §3).
 *       Одиночки (группировка) и другие группировки людей не теряют; пул уменьшается только
 *       на фактическом переходе; больше, чем есть вольных, взять нельзя; людей из воздуха нет;
 *       склад и история не трогаются, повторный вызов не дублирует;</li>
 *   <li>пул вольных сохраняется save/load (SAVE_VERSION не менялся), возрождение детерминировано;</li>
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
        freePoolNoAutoRevival();
        saveLoadFreePool();
        revivalDeterminism();
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

    /**
     * Вольный сталкер в пуле: человек без группировки (faction = Person.NONE). Фикстура будущего
     * притока через хаб (Э6-02): в нормальной симуляции К2 пул пуст, людей из воздуха код не создаёт.
     */
    static Person freeStalker(Simulation s, String name) {
        Person p = new Person(s.people.nextId++);
        p.name = name;
        p.faction = Person.NONE;
        p.cls = Person.Cls.ASSAULT;
        p.combat = 40; p.medicine = 10; p.tech = 15; p.scouting = 20;
        p.bravery = 0.2; p.greed = 0.0; p.loyalty = 0.1;
        s.people.roster.add(p);
        return p;
    }

    static int[] membersSnapshot(Simulation s) {
        int[] m = new int[s.factions.size()];
        for (Faction f : s.factions) m[f.id] = f.members;
        return m;
    }

    static boolean othersUnchanged(Simulation s, int[] snap, int except) {
        for (Faction f : s.factions) if (f.id != except && f.members != snap[f.id]) return false;
        return true;
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

    // ------------------------------------------------------------------ 7. точка расширения: возрождение только вольными сталкерами

    static void revivalExtensionPoint() {
        Simulation s = setup(57);
        Faction duty = s.factions.get(ZoneScenario.DUTY);
        Faction loners = s.mercenaries();
        ck(loners != null && !loners.isDestroyed(), "возрождение: Одиночки живы (группировка, не источник основателей)");
        destroy(duty);
        long money0 = duty.stock.money;
        int[] goods0 = duty.stock.qty.clone();
        int loners0 = loners.members;
        int lonersNamed0 = s.people.of(loners.id).size();
        int[] members0 = membersSnapshot(s);
        long total0 = s.totalMoney();
        ck(s.people.freeStalkers().isEmpty(), "возрождение: в текущей симуляции пул вольных пуст (приток Э6-02 не реализован)");

        // источник — только люди без группировки: Одиночки не подменяют пул, людей из воздуха нет
        ck(!s.reviveByFreeStalkers(duty, 3), "возрождение: пустой пул — false, хотя Одиночки полны (они не источник)");
        ck(duty.members == 0 && loners.members == loners0, "возрождение: отказ не тронул ни Долг, ни Одиночек");
        ck(!s.reviveByFreeStalkers(duty, loners.members), "возрождение: основатели не берутся из численности Одиночек");

        Person a = freeStalker(s, "Вольный «Тихий»");
        Person b = freeStalker(s, "Вольный «Грач»");
        Person c = freeStalker(s, "Вольный «Штырь»");
        ck(s.people.freeStalkers().size() == 3, "возрождение: пул — люди без группировки (faction=none)");
        ck(!s.reviveByFreeStalkers(duty, 4), "возрождение: больше, чем есть вольных, взять нельзя — false");
        ck(s.people.freeStalkers().size() == 3 && duty.members == 0,
                "возрождение: неудачный вызов не тронул пул (частичного перехода нет)");

        ck(s.reviveByFreeStalkers(duty, 3), "возрождение: состоялось");
        ck(duty.members == 3 && !duty.isDestroyed(), "возрождение: численность 3, фракция жива");
        ck(a.faction == duty.id && b.faction == duty.id && c.faction == duty.id,
                "возрождение: основатели — те самые вольные (переход none → Долг, других людей нет)");
        ck(s.people.freeStalkers().isEmpty(), "возрождение: пул уменьшился ровно на число основателей");
        ck(loners.members == loners0 && s.people.of(loners.id).size() == lonersNamed0,
                "возрождение: Одиночки не потеряли ни одного человека (ни числом, ни именными)");
        ck(othersUnchanged(s, members0, duty.id), "возрождение: остальные группировки не тронуты");
        ck(duty.phase == Phase.ACCUMULATE && duty.phaseDays == 0 && duty.wounded == 0,
                "возрождение: состояние сброшено (фаза, раненые)");
        ck(duty.stock.money == money0 && Arrays.equals(goods0, duty.stock.qty),
                "возрождение: стартовые ресурсы повторно не начислялись");
        ck(s.totalMoney() == total0 && s.audit().isEmpty(), "возрождение: денежный баланс не тронут");
        ck(!s.reviveByFreeStalkers(duty, 3), "возрождение: повторный вызов — false (без дублирования)");
        ck(!s.reviveByFreeStalkers(s.factions.get(ZoneScenario.FREEDOM), 0), "возрождение: 0 основателей — false");
        ck(!s.reviveByFreeStalkers(s.factions.get(ZoneScenario.FREEDOM), 1),
                "возрождение: живую фракцию «возродить» нельзя (за чужую фракцию не инициируют)");
        ck(s.reviveByFreeStalkers(null, 3) == false, "возрождение: null — false");

        give(s, duty.stock, 5000, 200); // еда и деньги для штатного роста
        for (int d = 0; d < 30; d++) s.step();
        ck(duty.members > 0, "возрождение: фракция живёт и растёт штатно (30 дней, факт " + duty.members + ")");
        ck(!s.people.of(duty.id).isEmpty(), "возрождение: именные восстановлены штатным maintainRoster");
        ck(s.people.leader(duty.id) != null, "возрождение: лидер восстановлен (из самих основателей)");
        ck(s.audit().isEmpty(), "возрождение: audit пуст после 30 дней");
    }

    // ------------------------------------------------------------------ 7б. пул вольных не возрождает сам

    static void freePoolNoAutoRevival() {
        Simulation s = setup(59);
        Faction duty = s.factions.get(ZoneScenario.DUTY);
        s.caravans.removeIf(c -> c.owner == duty.id);
        destroy(duty);
        for (int i = 0; i < 5; i++) freeStalker(s, "Вольный «Бродяга» " + i);
        int free0 = s.people.freeStalkers().size();
        boolean membersOk = true, poolOk = true;
        for (int d = 0; d < 60; d++) {
            s.step();
            if (duty.members != 0) membersOk = false;
            if (s.people.freeStalkers().size() != free0) poolOk = false;
        }
        ck(membersOk, "вольные в пуле: 60 дней — уничтоженная фракция не возродилась сама");
        ck(poolOk, "вольные в пуле: ежедневные циклы не расходуют пул без отдельного события");
        ck(s.audit().isEmpty(), "вольные в пуле: audit пуст");
    }

    // ------------------------------------------------------------------ 7в. save/load: разделение Одиночек и вольных

    static void saveLoadFreePool() {
        Simulation s = setup(60);
        Faction duty = s.factions.get(ZoneScenario.DUTY);
        destroy(duty);
        Person a = freeStalker(s, "Вольный «Лис»");
        Person b = freeStalker(s, "Вольный «Кот»");
        s.step(1);
        Simulation l = Simulation.load(s.save());
        ck(l.people.freeStalkers().size() == 2, "save/load: пул вольных сохранён (faction=none)");
        ck(l.factions.get(ZoneScenario.DUTY).isDestroyed(), "save/load: уничтоженная осталась уничтоженной");
        ck(l.people.byId(a.id).faction == Person.NONE && l.people.byId(b.id).faction == Person.NONE,
                "save/load: вольные не приписаны ни к одной группировке (Одиночки отдельно)");
        int loners0 = l.mercenaries().members;
        ck(l.reviveByFreeStalkers(l.factions.get(ZoneScenario.DUTY), 2), "save/load: возрождение после загрузки состоялось");
        ck(l.people.freeStalkers().isEmpty(), "save/load: пул израсходован ровно на основателей");
        ck(l.mercenaries().members == loners0, "save/load: Одиночки не потеряли людей");
        ck(l.people.byId(a.id).faction == ZoneScenario.DUTY && l.people.byId(b.id).faction == ZoneScenario.DUTY,
                "save/load: основатели сохранены как участники Долга");
        // детерминизм: то же событие на исходной симуляции даёт идентичное сохранение
        ck(s.reviveByFreeStalkers(s.factions.get(ZoneScenario.DUTY), 2), "save/load: возрождение на исходной симуляции");
        s.events.clear(); // журнал входит в сохранение: сравниваем симметрично (только новые события дня)
        l.events.clear();
        s.step(1);
        l.step(1);
        ck(s.save().equals(l.save()), "save/load: поведение после возрождения идентично (детерминизм)");
    }

    // ------------------------------------------------------------------ 7г. детерминизм возрождения

    static void revivalDeterminism() {
        Simulation s1 = setup(63);
        Simulation s2 = setup(63);
        for (Simulation s : new Simulation[]{s1, s2}) {
            Faction duty = s.factions.get(ZoneScenario.DUTY);
            destroy(duty);
            freeStalker(s, "Вольный «Один»");
            freeStalker(s, "Вольный «Два»");
            freeStalker(s, "Вольный «Три»");
            ck(s.reviveByFreeStalkers(duty, 3), "детерминизм: возрождение состоялось");
        }
        for (int d = 0; d < 30; d++) { s1.step(); s2.step(); }
        ck(s1.save().equals(s2.save()), "детерминизм: одинаковый сид и событие — идентичные сохранения (30 дней)");
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