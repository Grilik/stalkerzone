import ru.stalkerzone.sim.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Синтетический атлас для бенчмарков (карточки К1, К15). Не тест: имя без *Test, CI его не запускает.
 *
 * Сетка width×height узлов, соединённых с соседями; фракции по одной на «полосу» сетки.
 * Нужен, чтобы прогнать симуляцию на сотнях узлов, не имея настоящего атласа Зоны (он появится в Э5).
 */
public final class BigAtlas {
    public final List<Node> nodes = new ArrayList<>();
    public final ZoneGraph graph;
    public final List<Faction> factions = new ArrayList<>();
    public final double[][] hostility;
    public final int width, height;

    public BigAtlas(int size, int factionsCount) {
        this(size, size, factionsCount);
    }

    public BigAtlas(int width, int height, int factionsCount) {
        this.width = width;
        this.height = height;
        int total = width * height;

        for (int i = 0; i < total; i++) {
            NodeType t = (i % 11 == 0) ? NodeType.BASE : (i % 5 == 0) ? NodeType.RUINS : NodeType.SETTLEMENT;
            Node n = new Node(i, "Узел " + i, t, (i % width) * 100, (i / width) * 100);
            n.farmCap = 4 + i % 5;
            n.partsCap = i % 4;
            n.artifactCap = i % 3;
            n.housing = 20 + i % 30;
            n.defense = 15 + i % 20;
            nodes.add(n);
        }

        graph = new ZoneGraph(total);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (x + 1 < width) graph.connect(idx, idx + 1, 1, 0.02);
                if (y + 1 < height) graph.connect(idx, idx + width, 1, 0.02);
            }
        }

        for (int f = 0; f < factionsCount; f++) {
            int base = (f * total) / factionsCount;
            nodes.get(base).owner = f;
            factions.add(new Faction(f, "Группа " + f, base, 0.4, false, 40 + f * 5,
                    new Doctrine(0.5, 0.5, 0.5, 0.5, 0.2, 0.5)));
        }

        hostility = new double[factionsCount][factionsCount];
        for (int a = 0; a < factionsCount; a++)
            for (int b = 0; b < factionsCount; b++)
                hostility[a][b] = a == b ? 0 : 0.35;
    }

    public Simulation create(long seed) {
        Simulation sim = new Simulation(seed, nodes, graph, factions, hostility);
        sim.scenario = "synthetic";
        sim.finishSetup();
        return sim;
    }

    /** Подпись состояния: одинаковый сид — одинаковая подпись (детерминизм). */
    public static long signature(Simulation s) {
        long h = s.day * 31L + s.events.size();
        for (Faction f : s.factions) h = h * 1000003L + f.id * 7919L + f.members;
        for (Node n : s.nodes) h = h * 31L + n.owner + 7L * n.market.money;
        for (Lair l : s.lairs)
            h = h * 131L + l.node + Math.round(l.strength * 1000) + l.kind.ordinal();
        return h;
    }
}
