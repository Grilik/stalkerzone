package ru.stalkerzone.sim;

/**
 * Типы аномалий оригинальной игры. Радиус и урон в блоках и единицах здоровья (для игрового слоя),
 * artifact: какой артефакт здесь рождается (соответствие условное).
 */
public enum AnomalyType {
    SPRINGBOARD("Трамплин", "гравитационная", 4, 6, "Колобок"),
    WHIRLIGIG("Карусель", "гравитационная", 5, 5, "Каменный цветок"),
    GRINDER("Мясорубка", "гравитационная", 4, 14, "Каменная кровь"),
    MOSQUITO("Комариная плешь", "гравитационная", 5, 4, "Душа"),
    FRY("Жарка", "термическая", 4, 6, "Огненный шар"),
    FLUFF("Жгучий пух", "термическая", 5, 3, "Капля"),
    ELECTRA("Электра", "электрическая", 4, 8, "Вспышка"),
    FOG("Туман", "химическая", 6, 2, "Колючка");

    public static final AnomalyType[] ALL = values();

    public final String ru;
    public final String kind;
    public final int radius;
    public final int damage;
    public final String artifact;

    AnomalyType(String ru, String kind, int radius, int damage, String artifact) {
        this.ru = ru;
        this.kind = kind;
        this.radius = radius;
        this.damage = damage;
        this.artifact = artifact;
    }
}
