import ru.stalkerzone.sim.*;
import java.util.*;

/** v1.10: склады точек, конвои, контракт учёных с армией, стройка из стройматериалов, дезертирство и сепаратизм. */
public class LogisticsTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    static String line(Simulation s) {
        StringBuilder sb = new StringBuilder("д" + s.day + ": ");
        for (Faction x : s.factions) sb.append(x.name, 0, 3).append(' ').append(x.members).append('/').append(s.ownedCount(x)).append(' ').append(x.stock.money).append("р  ");
        return sb.toString();
    }

    public static void main(String[] a) {
        boolean audit = true;
        int built = 0, contractPaid = 0, convoys = 0, robbed = 0, arrived = 0, recruits = 0, deserted = 0, seceded = 0;
        int milAlive = 0;
        long duty = 0, racket = 0, smuggled = 0;
        for (long seed = 1; seed <= 6; seed++) {
            Simulation s = ZoneScenario.create(seed);
            for (int d = 0; d < 1440; d++) {
                s.step();
                if (!s.audit().isEmpty()) { audit = false; System.out.println("audit fail seed " + seed + " day " + s.day + "\n" + s.audit()); break; }
                if (seed == 1 && s.day % 240 == 0) System.out.println(line(s));
            }
            Logistics l = s.logistics;
            long b = s.events.stream().filter(e -> e.contains("строители закончили")).count();
            built += countAll(s, "строители закончили");
            contractPaid += l.contractPaid; convoys += l.convoysSent; robbed += l.convoysRobbed; arrived += l.convoysArrived;
            recruits += l.recruitsBought; deserted += l.deserters; seceded += l.secessions;
            if (s.military().members >= 20) milAlive++;
            duty += l.dutyProtection; racket += l.banditRacket; smuggled += l.freedomSmuggled;
            System.out.printf("seed %d: конвоев %d (дошло %d, перехвачено %d, потеряно %d), контракт: оплат %d, пропусков %d, %d руб.; "
                    + "новобранцев %d, дезертиров %d, отколов %d, армия %d чел.%n", seed, l.convoysSent, l.convoysArrived, l.convoysRobbed,
                    l.convoysLost, l.contractPaid, l.contractMissed, l.contractMoney, l.recruitsBought, l.deserters, l.secessions, s.military().members);
        }
        ck(audit, "аудит сходится с конвоями, складами точек и стройками (6 сидов x 1440 дней)");
        ck(contractPaid >= 6 * 100, "учёные регулярно платят армии по контракту (" + contractPaid + " оплат)");
        ck(arrived > convoys / 2, "большая часть конвоев доходит (" + arrived + " из " + convoys + ")");
        ck(robbed > 0, "конвои перехватывают (" + robbed + ")");
        ck(recruits > 0, "армия покупает новобранцев на КПП (" + recruits + ")");
        ck(milAlive >= 5, "армия держится на контракте без бесплатного финансирования (" + milAlive + "/6 сидов)");
        ck(built > 0, "строители заканчивают стройки из стройматериалов");
        ck(duty > 0 && racket > 0 && smuggled > 0, "у группировок свои доходы: охрана Бара " + duty + ", рэкет " + racket + ", контрабанда " + smuggled);

        // путь денег учёных: Янтарь → полевая база → КПП
        Simulation s = ZoneScenario.create(3);
        Faction sci = s.scientists(), mil = s.military();
        long kppBefore = 0;
        boolean toKpp = false;
        for (int d = 0; d < 60; d++) {
            s.step();
            for (Convoy c : s.logistics.convoys) {
                if (c.owner == mil.id && c.kind == Convoy.Kind.RETURN && c.dest() == mil.baseNode && c.cargo.money > 0
                        && Logistics.fieldBase(s, s.nodes.get(c.from()))) toKpp = true;
            }
        }
        ck(s.logistics.contractDelivered > 0, "плата учёных доезжает конвоем из Янтаря на полевую базу армии (" + s.logistics.contractDelivered + ")");
        ck(toKpp, "с полевой базы деньги везут на КПП «Периметр»");
        boolean supplyOut = false;
        for (int d = 0; d < 60 && !supplyOut; d++) {
            s.step();
            for (Convoy c : s.logistics.convoys)
                if (c.owner == mil.id && c.kind == Convoy.Kind.SUPPLY && c.from() == mil.baseNode && (c.cargo.get(Good.AMMO) > 0 || c.troops > 0)) supplyOut = true;
        }
        ck(supplyOut, "с КПП на базы идут патроны и пополнение");

        // разрыв контракта: учёные разорены → армия без денег → дезертирство и сепаратизм
        Simulation t = ZoneScenario.create(4);
        t.step(30);
        int mil0 = t.military().members;
        t.logistics.grantFrozen = true; // Большая земля заморозила грант: учёным нечем платить армии
        t.step(400);
        ck(!t.logistics.contractActive || t.logistics.contractMissed >= 3, "без денег учёных контракт срывается (пропусков " + t.logistics.contractMissed + ")");
        ck(t.logistics.deserters > 0, "без снабжения бойцы дезертируют (" + t.logistics.deserters + ")");
        System.out.println("армия: было " + mil0 + ", стало " + t.military().members + ", отколов " + t.logistics.secessions);
        t.events.stream().filter(e -> e.contains("ДЕЗЕРТИР") || e.contains("СЕПАРАТ") || e.contains("КОНТРАКТ")).limit(8).forEach(System.out::println);

        // стройка ждёт материалов
        Simulation u = ZoneScenario.create(2);
        u.step(200);
        u.events.stream().filter(e -> e.contains("стройка") || e.contains("строители")).limit(6).forEach(System.out::println);

        Simulation s1 = ZoneScenario.create(5);
        s1.step(300);
        Simulation s2 = Simulation.load(s1.save());
        s1.step(150); s2.step(150);
        ck(s1.save().equals(s2.save()), "сохранение v9 с конвоями и складами идентично");
        System.exit(f);
    }

    static int countAll(Simulation s, String key) {
        return (int) s.events.stream().filter(e -> e.contains(key)).count();
    }
}
