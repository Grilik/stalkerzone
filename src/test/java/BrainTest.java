import ru.stalkerzone.sim.*;

import java.util.*;

public class BrainTest {
    static int failures = 0;

    static void check(boolean ok, String msg) {
        System.out.println((ok ? "OK   " : "FAIL ") + msg);
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        int days = 1440;
        // 1. поведение на нескольких сидах
        Map<String, int[]> goalDays = new TreeMap<>();
        int[] totalWars = new int[16];
        int dormantWars = 0;
        int[] totalCaptured = new int[16];
        int minMembers = Integer.MAX_VALUE;
        boolean auditOk = true;
        for (long seed = 1; seed <= 6; seed++) {
            Simulation sim = ZoneScenario.create(seed);
            for (int d = 0; d < days; d++) {
                sim.step();
                if (!sim.audit().isEmpty()) {
                    auditOk = false;
                    System.out.println("audit fail seed " + seed + " day " + sim.day + "\n" + sim.audit());
                    break;
                }
                if (!sim.monolithActive) dormantWars = Math.max(dormantWars, sim.monolith().warsStarted);
                for (Faction f : sim.factions) {
                    goalDays.computeIfAbsent(f.name, k -> new int[Goal.values().length])[f.goal.ordinal()]++;
                    minMembers = Math.min(minMembers, f.members);
                }
            }
            StringBuilder sb = new StringBuilder("seed " + seed + ": ");
            for (Faction f : sim.factions) {
                totalWars[f.id] += f.warsStarted;
                totalCaptured[f.id] += f.nodesCaptured;
                sb.append(String.format("%s %d чел. войн %d/зах %d | ", f.name, f.members, f.warsStarted, f.nodesCaptured));
            }
            System.out.println(sb);
        }
        check(auditOk, "аудит сходится на 6 сидах x " + days + " дней");
        System.out.println("Дни по целям (6 сидов, всего " + 6 * days + " дней на фракцию):");
        for (Map.Entry<String, int[]> e : goalDays.entrySet()) {
            StringBuilder sb = new StringBuilder(String.format("  %-9s", e.getKey()));
            for (Goal g : Goal.values()) sb.append(String.format("%s %4.0f%%  ", g.ru, 100.0 * e.getValue()[g.ordinal()] / (6 * days)));
            System.out.println(sb);
        }
        System.out.println("Войн суммарно: " + Arrays.toString(totalWars) + ", захватов: " + Arrays.toString(totalCaptured));
        check(minMembers >= 1, "все фракции живы (минимум людей " + minMembers + ")");
        int[] w = totalWars;
        check(w[0] > 0 && w[1] > 0 && w[2] > 0 && w[3] > 0, "Одиночки, Долг, Свобода и Бандиты воевали");
        check(w[ZoneScenario.SCIENTISTS] == 0, "учёные не начинают войн");
        check(dormantWars == 0, "спящий Монолит не нападает до отключения Выжигателя");
        // разный характер -> разное поведение
        int[] duty = goalDays.get("Долг");
        int[] loners = goalDays.get("Одиночки");
        check(loners[Goal.TRADE.ordinal()] > duty[Goal.TRADE.ordinal()], "Одиночки торгуют больше Долга");
        check(duty[Goal.CLEANSE.ordinal()] > goalDays.get("Свобода")[Goal.CLEANSE.ordinal()], "Долг зачищает больше Свободы");

        // 2. злонамеренный мозг: симуляция обязана выдержать мусорные команды
        Simulation rogue = ZoneScenario.create(42);
        Random r = new Random(5);
        for (Faction f : rogue.factions) {
            f.brain = (sim, fac, rng) -> {
                List<Command> out = new ArrayList<>();
                int[] sell = new int[Simulation.G];
                int[] want = new int[Simulation.G];
                for (int i = 0; i < sell.length; i++) {
                    sell[i] = r.nextBoolean() ? Integer.MAX_VALUE : -r.nextInt(1000);
                    want[i] = r.nextBoolean() ? Integer.MAX_VALUE : -5;
                }
                out.add(new Command.Trade(r.nextInt(30) - 5, sell, want, r.nextInt(100) - 10, Long.MAX_VALUE));
                out.add(new Command.Trade(0, new int[3], null, 1, 1));
                out.add(new Command.Attack(r.nextInt(30) - 5));
                out.add(new Command.Attack(fac.baseNode));
                out.add(new Command.Production(Double.NaN, -50, 1e9));
                return out;
            };
        }
        boolean rogueOk = true;
        try {
            for (int d = 0; d < 400; d++) {
                rogue.step();
                if (!rogue.audit().isEmpty()) {
                    rogueOk = false;
                    System.out.println("rogue audit fail day " + rogue.day + "\n" + rogue.audit());
                    break;
                }
            }
        } catch (RuntimeException e) {
            rogueOk = false;
            e.printStackTrace(System.out);
        }
        check(rogueOk, "мусорные команды не ломают закон сохранения и не роняют симуляцию (400 дней)");
        boolean baseKept = true;
        for (Faction f : rogue.factions) baseKept &= rogue.nodes.get(f.baseNode).owner == f.id;
        check(baseKept, "базы фракций не захватываются даже злым мозгом");

        // 2б. проход к ЧАЭС закрыт, пока Выжигатель работает
        Simulation gate = ZoneScenario.create(3);
        int psiNode = -1;
        for (Node n : gate.nodes) if (n.type == NodeType.PSI) psiNode = n.id;
        int chaes = gate.monolith().baseNode;
        check(!gate.graph.reachable(psiNode, chaes) && !gate.graph.reachable(gate.nodes.get(0).id, chaes), "дорога к ЧАЭС закрыта при работающем Выжигателе");
        gate.activateMonolith();
        check(gate.graph.reachable(gate.nodes.get(0).id, chaes), "дорога к ЧАЭС открывается после пробуждения");

        // 2в. злой мозг при активном Монолите: базы остаются у владельцев, аудит сходится
        Simulation rogue2 = ZoneScenario.create(11);
        for (Faction f : rogue2.factions) f.brain = rogue.factions.get(0).brain;
        rogue2.step(50);
        rogue2.activateMonolith();
        boolean rogue2Ok = true;
        for (int d = 0; d < 300 && rogue2Ok; d++) {
            rogue2.step();
            rogue2Ok = rogue2.audit().isEmpty();
        }
        boolean base2 = true;
        for (Faction f : rogue2.factions) base2 &= rogue2.nodes.get(f.baseNode).owner == f.id;
        check(rogue2Ok && base2, "злой мозг при активном Монолите: аудит сходится, базы не захвачены");

        // 3. сохранение и загрузка (формат v3)
        Simulation s1 = ZoneScenario.create(7);
        s1.step(200);
        Simulation s2 = Simulation.load(s1.save());
        s1.step(150);
        s2.step(150);
        check(s1.save().equals(s2.save()), "после загрузки симуляция идёт идентично (150 дней)");
        check(s1.audit().isEmpty() && s2.audit().isEmpty(), "аудит после загрузки");

        Simulation s3 = ZoneScenario.create(9);
        s3.step(100);
        s3.activateMonolith();
        s3.step(120);
        Simulation s4 = Simulation.load(s3.save());
        s3.step(100);
        s4.step(100);
        check(s3.save().equals(s4.save()), "загрузка посреди войны Монолита: ход событий идентичен");
        check(s4.monolithActive && s4.graph.reachable(0, s4.monolith().baseNode), "после загрузки Монолит активен, дорога открыта");

        System.out.println(failures == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО: " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }
}
