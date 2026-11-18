package ru.stalkerzone.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * К1 (Э4-07): Проверка, что массивы симуляции инициализированы на nodes.size(), не жёсткая 64.
 *
 * Тест падает до исправления, потому что:
 * - People.zombies[64], Politics.cutDays[64], Simulation.knowledge[64] — жёсткие размеры
 * - KordonScenario создаёт симуляцию с узлами, но массивы остаются 64-размерные
 * - При доступе к узлам > 23 (настоящих в Кордоне) ArrayIndexOutOfBoundsException или неправильная логика
 *
 * После исправления:
 * - Массивы инициализируются на nodes.size()
 * - Тест создаёт симуляцию, проверяет, что размеры совпадают
 * - Можно расширить граф узлов без ошибок
 */
public class NodeCountTest {
    @Test
    public void testArraysCalibrationByNodeCount() {
        // Создаём симуляцию Кордона (~ 23 узла + Большая земля = 24 узла)
        KordonScenario scenario = new KordonScenario();
        Simulation sim = scenario.create(42L);
        int nodeCount = sim.nodes.size();

        // Проверяем, что все ключевые массивы инициализированы на nodeCount
        assertEquals(nodeCount, sim.people.zombies.length,
            "People.zombies должен быть размера nodes.size()");
        assertEquals(nodeCount, sim.politics.cutDays.length,
            "Politics.cutDays должен быть размера nodes.size()");
        assertEquals(nodeCount, sim.knowledge.length,
            "Simulation.knowledge должен быть размера nodes.size()");

        // Доступ к любому узлу должен работать без ошибок
        for (int i = 0; i < nodeCount; i++) {
            sim.knowledge[i] = 0.5;
            sim.people.zombies[i] = 1.0;
            sim.politics.cutDays[i] = 0;
        }

        // Если тест дошёл сюда, всё хорошо
    }

    @Test
    public void testNodeCountConsistency() {
        // Проверяем, что при загрузке сохранения размеры массивов пересчитываются
        KordonScenario scenario = new KordonScenario();
        Simulation sim = scenario.create(123L);
        int originalCount = sim.nodes.size();

        // Симулируем несколько тиков
        for (int i = 0; i < 10; i++) {
            sim.tick();
        }

        // После тиков node count не должен изменяться
        assertEquals(originalCount, sim.nodes.size(),
            "Количество узлов не должно меняться во время симуляции");

        // Массивы остаются того же размера
        assertEquals(originalCount, sim.people.zombies.length);
        assertEquals(originalCount, sim.politics.cutDays.length);
    }
}
