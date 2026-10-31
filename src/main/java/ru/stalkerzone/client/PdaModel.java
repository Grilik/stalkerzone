package ru.stalkerzone.client;

import java.util.ArrayList;
import java.util.List;

/** Разобранный снимок КПК. */
public final class PdaModel {
    public record NodeInfo(int id, String name, int x, int z, int owner, String type, String flags) {}
    public record FactionInfo(int id, String name, int members, int wounded, String phase, String leader, int exp, int stress, int unity, int nodes) {}
    public record ContractInfo(int id, String status, boolean mine, int actions, int x, int z, String text) {}
    public record LabInfo(int id, String name, String node, int tier, int rec, int parts, int del, boolean open, String lore, long reward, int x, int z) {}
    public record Mark(int x, int z, String name, int level) {}
    /** Проход через насыпь: цена (0 — бесплатный) и опасность (0 низкая … 2 высокая). */
    public record Passage(int x, int z, String name, long price, int risk, int owner) {}
    /** Репутация игрока у группировки. */
    public record Rep(int faction, int value) {}

    public int day, tick;
    public int px, pz;
    public String player = "";
    public long rubles;
    public int minX, minZ, maxX = 1, maxZ = 1;
    public final List<NodeInfo> nodes = new ArrayList<>();
    public final List<int[]> edges = new ArrayList<>();
    public final List<FactionInfo> factions = new ArrayList<>();
    public final List<int[]> hostility = new ArrayList<>();
    public final List<String> truces = new ArrayList<>();
    public String meeting;
    public final List<String> events = new ArrayList<>();
    public final List<ContractInfo> contracts = new ArrayList<>();
    public final List<LabInfo> labs = new ArrayList<>();
    public final List<Mark> anomalies = new ArrayList<>();
    public final List<Mark> lairs = new ArrayList<>();
    public final List<Mark> shelters = new ArrayList<>();
    /** Рюкзак игрока на месте гибели (карточка 030). */
    public final List<Mark> backpacks = new ArrayList<>();
    public final List<Mark> traders = new ArrayList<>();
    public final List<Passage> passages = new ArrayList<>();
    public final List<Rep> rep = new ArrayList<>();
    public int research, psiTech, cycle;
    public boolean scorcherOff, monolith;

    private static int i(String s) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static long l(String s) {
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    public static PdaModel parse(String text) {
        PdaModel m = new PdaModel();
        for (String line : text.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length == 0) continue;
            try {
                switch (f[0]) {
                    case "D" -> { m.day = i(f[1]); m.tick = i(f[2]); }
                    case "P" -> { m.px = i(f[1]); m.pz = i(f[2]); m.player = f[3]; m.rubles = l(f[4]); }
                    case "B" -> { m.minX = i(f[1]); m.minZ = i(f[2]); m.maxX = i(f[3]); m.maxZ = i(f[4]); }
                    case "N" -> m.nodes.add(new NodeInfo(i(f[1]), f[2], i(f[3]), i(f[4]), i(f[5]), f[6], f[7]));
                    case "G" -> m.edges.add(new int[] {i(f[1]), i(f[2])});
                    case "F" -> m.factions.add(new FactionInfo(i(f[1]), f[2], i(f[3]), i(f[4]), f[5], f[6], i(f[7]), i(f[8]), i(f[9]), i(f[10])));
                    case "H" -> {
                        String[] v = f[2].split(";");
                        int[] row = new int[v.length];
                        for (int k = 0; k < v.length; k++) row[k] = i(v[k]);
                        m.hostility.add(row);
                    }
                    case "T" -> m.truces.add(f[1]);
                    case "M" -> m.meeting = f[1];
                    case "E" -> m.events.add(f[1]);
                    case "C" -> m.contracts.add(new ContractInfo(i(f[1]), f[2], i(f[3]) == 1, i(f[4]), i(f[5]), i(f[6]), f[7]));
                    case "L" -> m.labs.add(new LabInfo(i(f[1]), f[2], f[3], i(f[4]), i(f[5]), i(f[6]), i(f[7]), i(f[8]) == 1, f[9], l(f[10]), i(f[11]), i(f[12])));
                    case "R" -> { m.research = i(f[1]); m.psiTech = i(f[2]); m.cycle = i(f[3]); m.scorcherOff = i(f[4]) == 1; m.monolith = i(f[5]) == 1; }
                    case "A" -> m.anomalies.add(new Mark(i(f[1]), i(f[2]), f[3], i(f[4])));
                    case "Z" -> m.lairs.add(new Mark(i(f[1]), i(f[2]), f[3], i(f[4])));
                    case "W" -> m.shelters.add(new Mark(i(f[1]), i(f[2]), f[3], 0));
                    case "K" -> m.backpacks.add(new Mark(i(f[1]), i(f[2]), f[3], 0));
                    case "S" -> m.traders.add(new Mark(i(f[1]), i(f[2]), f[3], 0));
                    case "X" -> m.passages.add(new Passage(i(f[1]), i(f[2]), f[3], l(f[4]), i(f[5]), i(f[6])));
                    case "Y" -> m.rep.add(new Rep(i(f[1]), i(f[2])));
                    default -> { }
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
                // повреждённая строка: пропускаем
            }
        }
        return m;
    }

    public FactionInfo faction(int id) {
        for (FactionInfo f : factions) if (f.id() == id) return f;
        return null;
    }

    public NodeInfo node(int id) {
        for (NodeInfo n : nodes) if (n.id() == id) return n;
        return null;
    }
}
