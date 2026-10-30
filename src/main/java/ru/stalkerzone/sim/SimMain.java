package ru.stalkerzone.sim;

/** Запуск симуляции без Minecraft: java ru.stalkerzone.sim.SimMain [дней] [seed] */
public final class SimMain {
    public static void main(String[] args) {
        int days = args.length > 0 ? Integer.parseInt(args[0]) : 720;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 1L;
        boolean verbose = args.length > 2;

        Simulation sim = Scenarios.create(seed);
        for (int d = 1; d <= days; d++) {
            sim.step();
            String audit = sim.audit();
            if (!audit.isEmpty()) {
                System.out.println("АУДИТ НЕ СОШЁЛСЯ на дне " + sim.day + ":\n" + audit);
                System.exit(1);
            }
            if (d % 30 == 0) printRow(sim);
        }
        System.out.println();
        System.out.println(Report.economy(sim));
        System.out.println(Report.factions(sim));
        if (verbose) for (String e : sim.events) System.out.println(e);
        System.out.println("Аудит: товары и деньги сходятся, дней: " + sim.day);
    }

    private static void printRow(Simulation sim) {
        StringBuilder sb = new StringBuilder(String.format("день %4d | ", sim.day));
        for (Faction f : sim.factions) {
            sb.append(String.format("%s %3d люд %-3s %6d₽ | ", f.name.substring(0, 3), f.members, f.phase.ru.substring(0, 3), f.stock.money));
        }
        sb.append("деньги в Зоне ").append(sim.totalMoney());
        System.out.println(sb);
    }
}
