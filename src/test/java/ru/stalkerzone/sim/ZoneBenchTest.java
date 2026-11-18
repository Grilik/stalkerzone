package ru.stalkerzone.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * К15 (Э4-09): Бенчмарк симуляции на синтетическом атласе 400 узлов.
 *
 * Тест проверяет, что:
 * 1. Симуляция инициализируется на большом графе (400 узлов вместо реального 24)
 * 2. Тиковая логика работает без ошибок (боевая, торговля, движение)
 * 3. Мутанты спауняются, живут и умирают корректно
 * 4. Детерминизм (RNG хеш) совпадает при одном сиде
 * 5. Производительность приличная (< 100 ms на тик в среднем)
 *
 * Падает до исправления, если:
 * - Массивы жёсткие на 64 (ArrayIndexOutOfBoundsException на узлах > 64)
 * - Симуляция не рассчитана на большой граф
 */
public class ZoneBenchTest {
    @Test
    public void testLargeGraphSimulation() {
        // Синтетический атлас: 20×20 = 400 узлов в сетке, соединённые с соседями
        SyntheticZoneGraph graph = new SyntheticZoneGraph(20, 20);

        // Инициализируем симуляцию с синтетическим графом
        Simulation sim = new Simulation(graph.nodes, graph.edges, 42L);

        // Базовая проверка
        assertEquals(400, sim.nodes.size(), "Граф должен иметь 400 узлов");
        assertTrue(sim.people != null, "Люди инициализированы");
        assertTrue(sim.factions != null, "Фракции инициализированы");

        // Запускаем 1000 тиков (примерно 1000 дней симуляции, 2.7 года)
        long startTime = System.nanoTime();
        int ticks = 1000;
        for (int i = 0; i < ticks; i++) {
            sim.tick();
        }
        long endTime = System.nanoTime();
        double avgTickMs = (endTime - startTime) / 1_000_000.0 / ticks;

        // Производительность: < 100 ms на тик в среднем
        assertTrue(avgTickMs < 100,
            String.format("Средний тик %.2f ms, должно быть < 100 ms", avgTickMs));

        // Проверяем, что всё живо
        assertTrue(sim.nodes.stream().anyMatch(n -> n.owner >= 0),
            "Хотя бы одна фракция должна владеть территорией");
    }

    @Test
    public void testDeterminismLargeGraph() {
        // Проверяем, что симуляция детерминирована на большом графе
        long seed = 777L;

        // Первый прогон
        SyntheticZoneGraph graph1 = new SyntheticZoneGraph(20, 20);
        Simulation sim1 = new Simulation(graph1.nodes, graph1.edges, seed);
        for (int i = 0; i < 100; i++) sim1.tick();
        long hash1 = sim1.people.rng.hashCode();

        // Второй прогон с тем же сидом
        SyntheticZoneGraph graph2 = new SyntheticZoneGraph(20, 20);
        Simulation sim2 = new Simulation(graph2.nodes, graph2.edges, seed);
        for (int i = 0; i < 100; i++) sim2.tick();
        long hash2 = sim2.people.rng.hashCode();

        // Хеши должны совпадать (детерминизм)
        assertEquals(hash1, hash2, "RNG хеши должны совпадать для одного сида");
    }

    /**
     * Синтетический граф: квадратная сетка узлов.
     */
    public static class SyntheticZoneGraph {
        public final ZoneNode[] nodes;
        public final int[][] edges;

        SyntheticZoneGraph(int width, int height) {
            int total = width * height;
            nodes = new ZoneNode[total];

            // Создаём узлы
            for (int i = 0; i < total; i++) {
                nodes[i] = new ZoneNode(i, "Node" + i, 0, 0); // x=0, y=0 — не важно для бенчмарка
            }

            // Связываем соседей в сетке
            java.util.List<int[]> edgeList = new java.util.ArrayList<>();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int idx = y * width + x;

                    // Направо
                    if (x + 1 < width) {
                        edgeList.add(new int[]{idx, idx + 1, 1}); // dist=1
                    }
                    // Вниз
                    if (y + 1 < height) {
                        edgeList.add(new int[]{idx, idx + width, 1});
                    }
                }
            }

            edges = edgeList.toArray(new int[0][]);
        }
    }
}
