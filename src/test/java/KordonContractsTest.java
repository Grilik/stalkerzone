import ru.stalkerzone.sim.*;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/** Проверки контрактов Кордона (карточка 006). */
public class KordonContractsTest {
    static int f = 0;

    static void ck(boolean ok, String m) {
        System.out.println((ok ? "OK   " : "FAIL ") + m);
        if (!ok) f++;
    }

    /** Все виды, которые должны выставляться на Кордоне (LAB_DATA — только у учёных, их на срезе нет). */
    static final Contract.Type[] NEEDED = {
            Contract.Type.FETCH_ARTIFACT, Contract.Type.SUPPLY, Contract.Type.CLEAR_LAIR,
            Contract.Type.GUARD_MEETING, Contract.Type.AMBUSH_MEETING,
            Contract.Type.ESCORT_COURIER, Contract.Type.CAPTURE_NODE
    };

    public static void main(String[] a) {
        allTypesIssued();
        npcClosesContracts();
        rewardsDebited();
        escortRaisesCourierBatches();
        captureOnlyOnOwnerChange();
        banditContractsAppear();
        npcDoNotTakeBanditContracts();
        meetingAmbushNeedsCash();
        banditResolution();
        oldSaveLoads();
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    /** За 30 дней выставлен каждый вид, кроме LAB_DATA. */
    static void allTypesIssued() {
        for (long seed : new long[]{1, 2, 3}) {
            Simulation s = Scenarios.create("kordon", seed);
            Set<Contract.Type> seen = new HashSet<>();
            boolean labData = false;
            for (int d = 0; d < 30; d++) {
                s.step();
                for (Contract c : s.contracts.list) {
                    seen.add(c.type);
                    if (c.type == Contract.Type.LAB_DATA) labData = true;
                }
            }
            for (Contract.Type t : NEEDED) ck(seen.contains(t), "сид " + seed + ": выставлен вид «" + t.ru + "»");
            ck(!labData, "сид " + seed + ": заказов на данные лабораторий на Кордоне нет");
        }
    }

    /** Нейтральные сталкеры закрыли ≥5 контрактов за 30 дней. */
    static void npcClosesContracts() {
        for (long seed : new long[]{1, 2, 3}) {
            Simulation s = Scenarios.create("kordon", seed);
            for (int d = 0; d < 30; d++) s.step();
            ck(s.contracts.doneByNpc >= 5, "сид " + seed + ": NPC закрыли " + s.contracts.doneByNpc + " контрактов (нужно ≥5)");
        }
    }

    /** Награды списаны с заказчиков: деньги лежат в залоге, аудит сходится каждый день. */
    static void rewardsDebited() {
        for (long seed : new long[]{1, 2, 3}) {
            Simulation s = Scenarios.create("kordon", seed);
            boolean ok = true;
            long maxEscrow = 0;
            for (int d = 0; d < 30; d++) {
                s.step();
                maxEscrow = Math.max(maxEscrow, s.contracts.escrowTotal());
                String audit = s.audit();
                if (!audit.isEmpty()) {
                    ok = false;
                    System.out.println("аудит сид " + seed + " день " + s.day + ":\n" + audit);
                    break;
                }
            }
            ck(ok, "сид " + seed + ": аудит сходится 30 дней");
            ck(maxEscrow > 0, "сид " + seed + ": награды держались в залоге у заказчиков (" + maxEscrow + " руб.)");
        }
    }

    /** Выполненное сопровождение курьера повышает число доставленных курьерских партий. */
    static void escortRaisesCourierBatches() {
        // прямое закрытие игроком: контракт сопровождения
        Simulation s = Scenarios.create("kordon", 3);
        for (int d = 0; d < 8; d++) s.step();
        Faction traders = s.traders();
        Contract c = new Contract(900001, Contract.Type.ESCORT_COURIER, traders.id);
        c.node = KordonBalance.VILLAGE_NODE;
        c.qty = ZoneScenario.LONERS;
        c.created = s.day;
        c.deadline = s.day + 10;
        c.difficulty = 2;
        c.escrow = 1000;
        traders.stock.money -= 1000;
        s.contracts.list.add(c);
        ck(s.contracts.take(s, 900001, "Тестер") == null, "контракт сопровождения взят игроком");
        int before = s.courierDelivered;
        ContractBoard.Reward r = s.contracts.escortArrived(s, 900001, "Тестер");
        ck(r != null && r.money() == 1000, "за сопровождение выплачена награда из залога");
        ck(c.status == Contract.Status.DONE, "контракт сопровождения закрыт");
        ck(s.courierDelivered == before + 1,
                "выполненное сопровождение повысило доставленные партии: " + before + " → " + s.courierDelivered);
        ck(s.audit().isEmpty(), "аудит после выплаты за сопровождение сходится");

        // живая симуляция: каждое закрытое NPC сопровождение увеличивает счётчик
        Simulation sim = Scenarios.create("kordon", 4);
        boolean seen = false, ok = true;
        for (int d = 0; d < 90; d++) {
            int doneBefore = escortsDone(sim);
            int counterBefore = sim.courierDelivered;
            sim.step();
            if (escortsDone(sim) > doneBefore) {
                seen = true;
                if (sim.courierDelivered <= counterBefore) ok = false;
            }
        }
        ck(seen, "за 90 дней одиночки хотя бы раз сопроводили курьера");
        ck(ok, "каждое выполненное сопровождение увеличивало число доставленных партий");
    }

    /** Сколько контрактов сопровождения закрыто в текущем списке. */
    static int escortsDone(Simulation s) {
        int n = 0;
        for (Contract c : s.contracts.list)
            if (c.type == Contract.Type.ESCORT_COURIER && c.status == Contract.Status.DONE) n++;
        return n;
    }

    /** Контракт на захват узла закрывается только со сменой владельца. */
    static void captureOnlyOnOwnerChange() {
        for (long seed : new long[]{2, 5}) {
            Simulation s = Scenarios.create("kordon", seed);
            Set<Integer> issued = new HashSet<>();
            Set<Integer> checked = new HashSet<>();
            boolean closed = false, consistent = true;
            for (int d = 0; d < 90; d++) {
                for (Contract c : s.contracts.list)
                    if (c.type == Contract.Type.CAPTURE_NODE) issued.add(c.id);
                s.step();
                for (Contract c : s.contracts.list) {
                    // проверяем момент закрытия: владелец мог смениться и обратно позже
                    if (c.type != Contract.Type.CAPTURE_NODE || c.status != Contract.Status.DONE) continue;
                    if (!checked.add(c.id)) continue;
                    closed = true;
                    if (s.nodes.get(c.node).owner == c.qty) consistent = false;
                }
                if (!s.audit().isEmpty()) consistent = false;
            }
            ck(!issued.isEmpty(), "сид " + seed + ": заказы на захват узла выставлялись");
            ck(closed, "сид " + seed + ": хотя бы один захват узла закрыт");
            ck(consistent, "сид " + seed + ": CAPTURE_NODE закрывается только при смене владельца");
        }

        // без смены владельца контракт не закрывается: деревня уже за одиночками
        Simulation s = Scenarios.create("kordon", 9);
        for (int d = 0; d < 3; d++) s.step();
        int village = KordonBalance.VILLAGE_NODE;
        int owner = s.nodes.get(village).owner;
        Contract noChange = new Contract(900002, Contract.Type.CAPTURE_NODE, ZoneScenario.BANDITS);
        noChange.node = village;
        noChange.qty = owner;
        noChange.created = s.day;
        noChange.deadline = s.day + 20;
        noChange.difficulty = 1;
        s.contracts.list.add(noChange);
        noChange.status = Contract.Status.TAKEN;
        noChange.taker = ZoneScenario.LONERS;
        noChange.resolveDay = s.day;
        s.step();
        ck(s.nodes.get(village).owner == owner, "владелец деревни не менялся");
        ck(noChange.status != Contract.Status.DONE, "без смены владельца захват не закрыт: " + noChange.status.ru);

        // владелец сменился с записанного в контракте — цель достигнута, контракт закрывается
        int atp = 5;
        int atpOwner = s.nodes.get(atp).owner;
        Contract changed = new Contract(900003, Contract.Type.CAPTURE_NODE, ZoneScenario.LONERS);
        changed.node = atp;
        changed.qty = -1; // в контракте записан ничей узел, врага с узла уже выбили
        changed.created = s.day;
        changed.deadline = s.day + 20;
        changed.difficulty = 1;
        s.contracts.list.add(changed);
        changed.status = Contract.Status.TAKEN;
        changed.taker = ZoneScenario.MILITARY;
        changed.resolveDay = s.day;
        int doneBefore = s.contracts.doneByNpc;
        s.step();
        ck(changed.status == Contract.Status.DONE && s.contracts.doneByNpc > doneBefore,
                "смена владельца узла закрывает захват (АТП владел " + atpOwner + ", в контракте −1)");
    }

    /** Старое сохранение (версия 15) загружается и даёт то же состояние. */
    static void oldSaveLoads() {
        Simulation s1 = Scenarios.create("kordon", 7);
        s1.step(5);
        s1.courierDelivered = 0;
        String save = s1.save();
        byte[] full = Base64.getDecoder().decode(save);
        // настоящее v15: версия 15 и без хвостового счётчика курьеров (последние 4 байта)
        byte[] raw = java.util.Arrays.copyOf(full, full.length - 4);
        raw[0] = 0; raw[1] = 0; raw[2] = 0; raw[3] = 15;
        Simulation s2 = Simulation.load(Base64.getEncoder().encodeToString(raw));
        ck(s2 != null, "сохранение версии 15 загружается");
        ck(s2.save().equals(save), "старое сохранение даёт то же состояние");
        // счётчик доставленных партий переживает сохранение v16
        s1.courierDelivered = 7;
        ck(Simulation.load(s1.save()).courierDelivered == 7, "счётчик курьерских партий сохраняется");
    }

    /** За 30 дней у Бандитов появляется хотя бы один заказ: засада на курьера или налёт (карточка 021). */
    static void banditContractsAppear() {
        for (long seed : new long[]{1, 2, 3, 4, 5}) {
            Simulation s = Scenarios.create("kordon", seed);
            boolean ambush = false, raid = false;
            for (int d = 0; d < 30; d++) {
                s.step();
                for (Contract c : s.contracts.list) {
                    if (c.issuer != ZoneScenario.BANDITS) continue;
                    if (c.type == Contract.Type.AMBUSH_COURIER) ambush = true;
                    if (c.type == Contract.Type.RAID_NODE) raid = true;
                }
            }
            ck(ambush || raid, "сид " + seed + ": у Бандитов появился заказ на засаду (" + ambush + ") или налёт (" + raid + ")");
        }
    }

    /** Заказы Бандитов берёт только игрок-бандит: NPC их не разбирают (карточка 021). */
    static void npcDoNotTakeBanditContracts() {
        for (long seed : new long[]{1, 2, 3, 4, 5}) {
            Simulation s = Scenarios.create("kordon", seed);
            boolean npcTook = false;
            for (int d = 0; d < 30; d++) {
                s.step();
                for (Contract c : s.contracts.list) {
                    if (c.type != Contract.Type.AMBUSH_COURIER && c.type != Contract.Type.RAID_NODE) continue;
                    if (c.taker >= 0) npcTook = true;
                }
            }
            ck(!npcTook, "сид " + seed + ": NPC не берут заказы Бандитов");
        }
    }

    /** При пустой кассе заказчика засада на встречу на Кордоне не выставляется (карточка 021). */
    static void meetingAmbushNeedsCash() {
        for (long seed : new long[]{1, 2, 3}) {
            Simulation s = Scenarios.create("kordon", seed);
            boolean any = false;
            for (int d = 0; d < 40; d++) {
                for (Faction f : s.factions) f.stock.money = 0; // кассы пусты: заказчик не может оплатить
                s.step();
                for (Contract c : s.contracts.list) if (c.type == Contract.Type.AMBUSH_MEETING) any = true;
            }
            ck(!any, "сид " + seed + ": без денег AMBUSH_MEETING не выставляется");
        }
    }

    /** Налёт: запасы еды уходят бандитам, владелец не меняется; засада на курьера — груз пропал (карточка 021). */
    static void banditResolution() {
        Simulation s = Scenarios.create("kordon", 5);
        for (int d = 0; d < 3; d++) s.step();
        int farm = KordonBalance.CONTRACT_RAID_FARM;
        int owner = s.nodes.get(farm).owner;
        long foodBefore = s.nodes.get(farm).depot.get(Good.FOOD);
        long banditFood = s.factions.get(ZoneScenario.BANDITS).stock.get(Good.FOOD);
        Contract raid = new Contract(900100, Contract.Type.RAID_NODE, ZoneScenario.BANDITS);
        raid.node = farm; raid.qty = owner; raid.created = s.day; raid.deadline = s.day + 20; raid.difficulty = 1;
        s.contracts.list.add(raid);
        raid.status = Contract.Status.TAKEN; raid.taker = Contract.PLAYER; raid.player = "test";
        s.contracts.raidedFarm(s, farm, "test");
        ck(raid.status == Contract.Status.DONE, "налёт закрыт при удачном налёте: " + raid.status.ru);
        ck(s.nodes.get(farm).owner == owner, "владелец фермы не сменился");
        ck(s.nodes.get(farm).depot.get(Good.FOOD) < foodBefore, "у фермы убыло еды");
        ck(s.factions.get(ZoneScenario.BANDITS).stock.get(Good.FOOD) > banditFood, "бандиты получили еду");

        Contract amb = new Contract(900101, Contract.Type.AMBUSH_COURIER, ZoneScenario.BANDITS);
        amb.node = 0; amb.qty = ZoneScenario.LONERS; amb.created = s.day; amb.deadline = s.day + 20; amb.difficulty = 1;
        s.contracts.list.add(amb);
        amb.status = Contract.Status.TAKEN; amb.taker = Contract.PLAYER; amb.player = "test";
        ck(s.contracts.courierAmbushed(s, ZoneScenario.LONERS, "test") != null && amb.status == Contract.Status.DONE,
                "засада на курьера закрыта по пропавшему грузу: " + amb.status.ru);
    }
}
