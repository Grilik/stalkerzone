import ru.stalkerzone.sim.*;

import java.util.*;

public class MonolithTest {
    public static void main(String[] args) {
        // 1. обычный ход: как долго идут исследования, сколько попыток за 6 лет
        System.out.println("=== Естественный ход, 2200 дней, 6 сидов");
        for (long seed = 1; seed <= 6; seed++) {
            Simulation sim = ZoneScenario.create(seed);
            int researchDone = -1;
            int firstAttempt = -1;
            for (int d = 0; d < 2200; d++) {
                sim.step();
                String a = sim.audit();
                if (!a.isEmpty()) { System.out.println("AUDIT FAIL seed " + seed + " day " + sim.day + "\n" + a); System.exit(1); }
                if (researchDone < 0 && sim.scorcher.research >= 100) researchDone = sim.day;
                if (firstAttempt < 0 && sim.scorcher.attempts > 0) firstAttempt = sim.day;
                if (sim.monolithActive) break;
            }
            Faction sci = sim.scientists();
            Faction mer = sim.mercenaries();
            System.out.printf("seed %d: исследования готовы на дне %d, первая попытка %d, попыток %d, лучший этап %d, Монолит %s (день %d), учёных %d, одиночек %d%n",
                    seed, researchDone, firstAttempt, sim.scorcher.attempts, sim.scorcher.bestStage,
                    sim.monolithActive ? "ПРОБУДИЛСЯ" : "спит", sim.day, sci.members, mer.members);
        }

        // 2. принудительная активация: война Монолита
        System.out.println("\n=== Принудительное пробуждение на дне 200, дальше 700 дней, 6 сидов");
        int sciRouted = 0, milRelief = 0;
        for (long seed = 1; seed <= 6; seed++) {
            Simulation sim = ZoneScenario.create(seed);
            sim.step(200);
            sim.activateMonolith();
            Faction mono = sim.monolith();
            int minMono = 999, maxMono = 0;
            Map<String, Integer> lowest = new TreeMap<>();
            for (int d = 0; d < 700; d++) {
                sim.step();
                String a = sim.audit();
                if (!a.isEmpty()) { System.out.println("AUDIT FAIL seed " + seed + " day " + sim.day + "\n" + a); System.exit(1); }
                minMono = Math.min(minMono, mono.members);
                maxMono = Math.max(maxMono, mono.members);
                for (Faction f : sim.factions) lowest.merge(f.name, f.members, Math::min);
            }
            StringBuilder sb = new StringBuilder("seed " + seed + ": Монолит " + mono.members + " чел. (мин " + minMono + ", макс " + maxMono + "), захватов " + mono.nodesCaptured + ", войн " + mono.warsStarted + " | минимум людей: ");
            lowest.forEach((k, v) -> sb.append(k).append(' ').append(v).append(", "));
            System.out.println(sb);
            long routs = sim.events.stream().filter(e -> e.contains("разгромили")).count();
            long sciRout = sim.events.stream().filter(e -> e.contains("технологии псишлема потеряны")).count();
            long fled = sim.events.stream().filter(e -> e.contains("отходят из")).count();
            long helped = sim.events.stream().filter(e -> e.contains("пришли на помощь учёным")).count();
            long posts = sim.events.stream().filter(e -> e.contains("Блокпост")).count();
            if (sciRout > 0) sciRouted++;
            boolean relief = sim.nodes.stream().anyMatch(n -> n.name.contains("Болотный") && n.owner == ZoneScenario.MILITARY);
            if (relief) milRelief++;
            System.out.println("   событий: разгромов " + routs + " (учёных " + sciRout + "), бегство " + fled + ", блокпосты в журнале " + posts + ", помощь учёным: " + helped + " раз, армия держит Болотный: " + relief);
            for (Faction f : sim.factions) {
                System.out.print("   " + f.name + " " + f.members + " чел., " + (f.fightMonolith ? "бой" : "отход") + ", цель " + f.goal.ru + "; ");
            }
            System.out.println();
        }
        System.out.println("Seeds с разгромом учёных: " + sciRouted + "/6, армия дошла до Болотного: " + milRelief + "/6");

        // 3. подробный журнал одного сида
        System.out.println("\n=== Журнал seed 2 (пробуждение дня 200)");
        Simulation sim = ZoneScenario.create(2);
        sim.step(200);
        sim.activateMonolith();
        sim.step(300);
        for (String e : sim.events) {
            if (e.contains("Монолит") || e.contains("МОНОЛИТ") || e.contains("разгромили") || e.contains("Блокпост") || e.contains("отходят") || e.contains("Военные") || e.contains("Учёные"))
                System.out.println(e);
        }
        System.out.println(Report.scorcher(sim));
    }
}
