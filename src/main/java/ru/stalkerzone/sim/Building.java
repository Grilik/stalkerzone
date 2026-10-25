package ru.stalkerzone.sim;

/**
 * Постройка лагеря. Уровень 0: руины, 1: лагерь, 2: укреплённый лагерь, 3: форт.
 * hp: 0..100, от него зависят и польза, и внешний вид (целая, потрескавшаяся, пробитая, обрушенная).
 */
public final class Building {
    public static final int MAX_LEVEL = 3;

    public final int node;
    public final BuildingType type;
    public int level;
    public double hp;

    // --- стройка (как в MineColonies): проект ждёт материалов, строители работают по дням ---
    /** Уровень, до которого строят (0 или равен level: стройки нет). */
    public int target;
    /** Сколько материалов и деталей нужно на весь проект и сколько уже завезли на площадку. */
    public int needMat, needParts, haveMat, haveParts;
    /** Выполненная и нужная работа (человеко-дни строителей). */
    public double work, workNeed;
    public int startedDay;
    /** Чья стройка (-1 нет): если точку потеряли, стройка бросается. */
    public int builder = -1;

    public boolean building() { return target > level; }

    /** Готовность стройки 0..1 (для картинки лесов). */
    public double progress() { return workNeed <= 0 ? 0 : Math.min(1, work / workNeed); }

    public Building(int node, BuildingType type, int level, double hp) {
        this.node = node;
        this.type = type;
        this.level = level;
        this.hp = hp;
    }

    /** Полезность: уровень, умноженный на целостность. */
    public double quality() { return level * hp / 100.0; }

    /** Состояние для картинки: 0 целая, 1 потрескавшаяся, 2 пробитая, 3 обрушенная. */
    public int condition() {
        if (level == 0 || hp < 10) return 3;
        if (hp < 40) return 2;
        if (hp < 70) return 1;
        return 0;
    }

    public String condRu() {
        return switch (condition()) {
            case 0 -> "целая";
            case 1 -> "потрескавшаяся";
            case 2 -> "пробитая";
            default -> level == 0 ? "руины" : "обрушена";
        };
    }
}
