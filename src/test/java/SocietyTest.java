import ru.stalkerzone.sim.*;

/** v1.4–1.7 и лаборатории: контракты игрока, люди, власть, данные лабораторий, сохранение. */
public class SocietyTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) {
        long paid = 0;
        int delivered = 0, claimed = 0;
        boolean auditOk = true;
        for (long seed = 1; seed <= 4; seed++) {
            Simulation s = ZoneScenario.create(seed);
            for (int d = 0; d < 800; d++) {
                s.step();
                // игрок берёт контракт на доставку и через 3 дня сдаёт его
                if (s.contracts.ofPlayer("Тестер").isEmpty()) {
                    for (Contract c : s.contracts.active()) {
                        if (c.status == Contract.Status.OPEN && (c.type == Contract.Type.FETCH_ARTIFACT || c.type == Contract.Type.SUPPLY)) {
                            s.contracts.take(s, c.id, "Тестер");
                            break;
                        }
                    }
                } else {
                    for (Contract c : s.contracts.ofPlayer("Тестер")) {
                        if (c.status == Contract.Status.TAKEN && s.day - c.created >= 3 && c.deadline >= s.day) {
                            ContractBoard.Reward r = s.contracts.deliver(s, c.id, "Тестер", c.qty);
                            if (r != null) { paid += r.money(); delivered++; }
                        }
                        if (c.status == Contract.Status.READY && s.contracts.claim(s, c.id, "Тестер") != null) claimed++;
                    }
                }
                if (!s.audit().isEmpty()) {
                    auditOk = false;
                    System.out.println("seed " + seed + " day " + s.day + "\n" + s.audit());
                    break;
                }
            }
            System.out.printf("seed %d: пришло %d, вступили %d, зомби стало %d, встреч %d, засад %d, договорённостей %d, расколов %d, экспедиций %d/%d%n",
                    seed, s.people.arrived, s.people.joined, s.people.zombified, s.politics.meetingsHeld, s.politics.ambushes,
                    s.politics.trucesSigned, s.politics.schisms, s.labs.expeditionsWon, s.labs.expeditions);
        }
        ck(auditOk, "аудит сходится на 4 сидах x 800 дней с контрактами игрока");
        ck(delivered > 5, "игрок сдал контрактов: " + delivered + " на " + paid + " руб.");

        Simulation s = ZoneScenario.create(7);
        s.step(30);
        ck(s.people.arrived > 30 && s.people.joined > 10, "новички идут через Кордон: пришло " + s.people.arrived + ", вступили " + s.people.joined);
        for (Faction x : s.factions) if (!x.isService()) ck(s.people.leader(x.id) != null, "у «" + x.name + "» есть лидер: " + (s.people.leader(x.id) != null ? s.people.leader(x.id).name : "—"));
        Person old = s.people.leader(1);
        s.people.kill(s, old.id, "убит игроком");
        Person neu = s.people.leader(1);
        ck(neu != null && neu.id != old.id, "после гибели лидера Долга власть перешла: " + (neu == null ? "никому" : neu.name));
        ck(s.audit().isEmpty(), "аудит после гибели лидера");

        // охрана встречи
        Simulation m = ZoneScenario.create(11);
        int guarded = -1;
        for (int d = 0; d < 300 && guarded < 0; d++) {
            m.step();
            Politics.Meeting mt = m.politics.meeting;
            if (mt == null) continue;
            for (Contract c : m.contracts.active()) {
                if (c.type == Contract.Type.GUARD_MEETING && c.status == Contract.Status.OPEN) {
                    m.contracts.take(m, c.id, "Охранник");
                    m.contracts.guardPresent(m, "Охранник");
                    guarded = c.id;
                    break;
                }
            }
        }
        ck(guarded > 0 && m.politics.meeting != null && m.politics.meeting.guarded, "игрок взял охрану встречи и встал на пост");
        m.step(6);
        Contract g = m.contracts.byId(guarded);
        ck(g != null && (g.status == Contract.Status.READY || g.status == Contract.Status.FAILED), "охрана встречи решена: " + (g == null ? "?" : g.status.ru));
        if (g != null && g.status == Contract.Status.READY) ck(m.contracts.claim(m, g.id, "Охранник") != null, "награда за охрану получена");
        ck(m.audit().isEmpty(), "аудит после охраны встречи");

        // лаборатории
        Simulation l = ZoneScenario.create(5);
        l.step(5);
        Lab x18 = l.labs.labs.get(1);
        ck(x18.code.equals("x18") && l.nodes.get(x18.node).name.equals("Тёмная долина"), "X-18 под Тёмной долиной");
        ck(l.labs.canDownload(l, x18, 0) == null, "терминал X-18 №1 доступен");
        int cyc = l.labs.downloaded(l, x18, 0, "Тестер");
        ck(l.labs.canDownload(l, x18, 0) != null, "повторно тот же терминал не скачать");
        double before = l.scorcher.research;
        long reward = l.labs.playerDeliver(l, x18, 0, cyc);
        ck(reward == x18.playerReward(), "учёные платят за данные X-18: " + reward);
        ck(l.scorcher.research > before, "исследования выросли");
        ck(l.labs.playerDeliver(l, x18, 0, cyc) == -2, "одни и те же данные дважды не сдать");
        int cyc2 = l.labs.downloaded(l, x18, 1, "Тестер");
        l.forceMonolithVictory();
        ck(l.labs.playerDeliver(l, x18, 1, cyc2) == -1, "после победы Монолита старые данные устарели");
        ck(l.labs.canDownload(l, x18, 0) == null && x18.deliveredCount() == 0, "терминалы снова зашифрованы, всё заново");
        Lab x2 = l.labs.labs.get(4);
        ck(!l.monolithActive && l.labs.canDownload(l, x2, 0) != null, "X-2 закрыта, пока работает Выжигатель");
        ck(l.audit().isEmpty(), "аудит после лабораторий");

        // сохранение
        Simulation s1 = ZoneScenario.create(3);
        s1.step(400);
        Simulation s2 = Simulation.load(s1.save());
        ck(s2.save().equals(s1.save()), "сохранение v7 идентично после загрузки");
        s1.step(150);
        s2.step(150);
        ck(s1.save().equals(s2.save()), "после загрузки симуляция идёт так же");
        System.out.println(Report.people(s1));
        System.out.println(Report.politics(s1));
        System.out.println(Report.contracts(s1));
        System.out.println(Report.labs(s1));
        System.exit(f);
    }
}
