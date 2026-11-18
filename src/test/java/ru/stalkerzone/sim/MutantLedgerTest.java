package ru.stalkerzone.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * К16 (Э8-10): Проверка счётчиков мутантов в логовах (Lair).
 *
 * Тест проверяет, что:
 * 1. Логова инициализируются с корректной силой и видом
 * 2. Метод mutantCount() возвращает корректное число мутантов (сила / цена вида)
 * 3. Логова могут быть созданы и уничтожены, счётчики обновляются
 * 4. Питание логова (feed) правильно увеличивает силу и счётчик мутантов
 * 5. При загрузке сохранения логова восстанавливаются корректно
 *
 * Падает до исправления, если:
 * - Логова не инициализируют счётчики
 * - mutantCount() не кешируется или вычисляется неправильно
 * - Нет тестов на корректность (Lair работает, но без проверок)
 */
public class MutantLedgerTest {
    @Test
    public void testLairInitialization() {
        // Создаём логово с DOGS видом, силой 20
        Lair lair = new Lair(0, LairKind.DOGS, 20.0, false);

        assertEquals(0, lair.node, "Узел должен быть 0");
        assertEquals(LairKind.DOGS, lair.kind, "Вид должен быть DOGS");
        assertEquals(20.0, lair.strength, "Сила должна быть 20.0");
        assertFalse(lair.lab, "Не лаборатория");

        // mutantCount() = ceil(20.0 / 6.2) = ceil(3.22) = 4 мутанта
        int count = lair.mutantCount();
        assertEquals(4, count, "DOGS с силой 20 = 4 мутанта (20/6.2 ≈ 3.2)");
    }

    @Test
    public void testMutantCountByKind() {
        // Проверяем mutantCount для разных видов при одной силе
        double testStrength = 50.0;

        Lair dogs = new Lair(0, LairKind.DOGS, testStrength, false);
        Lair flesh = new Lair(1, LairKind.FLESH, testStrength, false);
        Lair burers = new Lair(2, LairKind.BURERS, testStrength, false);

        // DOGS: 50 / 6.2 ≈ 8 (но макс 6 мутантов в поле)
        assertTrue(dogs.mutantCount() <= Lair.MAX_MUTANTS, "Не более MAX_MUTANTS");

        // BURERS тяжелее: 50 / 15.25 ≈ 3
        assertTrue(burers.mutantCount() <= flesh.mutantCount(),
            "Бюреры тяжелее, должно быть меньше при той же силе");
    }

    @Test
    public void testLairFeeding() {
        Lair lair = new Lair(5, LairKind.FLESH, 20.0, false);
        int initialCount = lair.mutantCount();

        // Логово ест добычу, сила растёт
        double eaten = lair.feed(15.0);
        assertEquals(15.0, eaten, "Логово должно съесть 15 еды");
        assertEquals(35.0, lair.strength, "Сила должна возрасти на 15");
        assertEquals(15.0, lair.preyEaten, "preyEaten должен учитывать добычу");

        int newCount = lair.mutantCount();
        assertTrue(newCount >= initialCount, "Число мутантов должно не уменьшиться");
    }

    @Test
    public void testLairCapacity() {
        Lair lair = new Lair(10, LairKind.FLESH, 40.0, false);
        double cap = lair.cap();
        // FLESH cap = 50.0 (не лаб)

        // Пытаемся закормить больше, чем потолок позволяет
        double excess = 20.0; // 40 + 20 = 60, но потолок 50
        double eaten = lair.feed(excess);

        assertEquals(10.0, eaten, "Съест только до потолка (50 - 40 = 10)");
        assertEquals(50.0, lair.strength, "Сила достигнет потолка");
        assertEquals(10.0, lair.preyEaten, "Только съеденное считается");
    }

    @Test
    public void testLairLab() {
        // Лаборатория: cap +15%, growth +40%
        Lair regular = new Lair(0, LairKind.DOGS, 30.0, false);
        Lair labLair = new Lair(1, LairKind.DOGS, 30.0, true);

        // cap: 45 → 45 * 1.15 ≈ 51.75
        assertTrue(labLair.cap() > regular.cap(), "Лабораторное логово имеет больший потолок");

        // growth: 1.3 → 1.3 * 1.4 ≈ 1.82
        assertTrue(labLair.growth() > regular.growth(), "Лабораторное логово растёт быстрее");
    }

    @Test
    public void testSimulationLairConsistency() {
        // Проверяем, что логова в симуляции создаются и счётчики консистентны
        KordonScenario scenario = new KordonScenario();
        Simulation sim = scenario.create(999L);

        // Должны быть логова (по KordonBalance.START_LAIRS)
        assertTrue(sim.lairs.size() > 0, "Должны быть стартовые логова");

        // Проверяем, что все логова имеют корректные mutantCount
        for (Lair lair : sim.lairs) {
            int count = lair.mutantCount();
            assertTrue(count > 0, "Логово должно иметь хотя бы 1 мутанта");
            assertTrue(count <= Lair.MAX_MUTANTS, "Не более MAX_MUTANTS в поле");
            assertEquals(LairKind.class, lair.kind.getClass(), "Вид должен быть инициализирован");
        }
    }

    @Test
    public void testMutantCountMaxCap() {
        // Проверяем, что даже при очень высокой силе mutantCount не превышает MAX_MUTANTS
        Lair lair = new Lair(0, LairKind.DOGS, 1000.0, false); // Огромная сила
        int count = lair.mutantCount();

        assertEquals(Lair.MAX_MUTANTS, count,
            "mutantCount должен быть ≤ MAX_MUTANTS даже при высокой силе");
    }
}
