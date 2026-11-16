package ru.stalkerzone.container;

/**
 * Э1-17: контейнеры мода как в S.T.A.L.K.E.R. — 8 типов по два вида (A/B, выбор пользователя 16.11: оба, по месту случайно).
 * Вместимость и вес — по превью-листу ({@code tools/containers/preview.py}); размер модели — в 1/16 блока при взгляде
 * на переднюю грань (ширина w, высота h, глубина d; h = 32 — два блока, верхняя половина без блок-сущности).
 * Класс без Minecraft: его проверяет {@code ContainersTest}, по нему же {@code tools/containers/gen_assets.py} делает ресурсы.
 */
public enum ContainerKind {
    WOOD_CRATE("wood_crate", "1A", "Деревянный ящик", "Wooden Crate", Family.WOOD, 18, 15, 16, 16, 16),
    WOOD_CRATE_OLD("wood_crate_old", "1B", "Старый деревянный ящик", "Old Wooden Crate", Family.WOOD, 18, 15, 16, 16, 16),
    ARMY_CRATE("army_crate", "2A", "Армейский ящик", "Army Crate", Family.WOOD, 18, 20, 16, 12, 16),
    ARMY_CRATE_RED("army_crate_red", "2B", "Армейский ящик с красной полосой", "Army Crate (Red Stripe)", Family.WOOD, 18, 20, 16, 12, 16),
    MARKED_CRATE_AMMO("marked_crate_ammo", "3A", "Ящик с маркировкой «5,45×39»", "Marked Ammo Crate", Family.WOOD, 18, 15, 16, 14, 16),
    MARKED_CRATE_STORE("marked_crate_store", "3B", "Складской ящик с маркировкой", "Marked Storage Crate", Family.WOOD, 18, 15, 16, 14, 16),
    METAL_BOX("metal_box", "4A", "Металлический ящик", "Metal Box", Family.METAL, 27, 30, 16, 12, 16),
    METAL_BOX_RUSTY("metal_box_rusty", "4B", "Ржавый металлический ящик", "Rusty Metal Box", Family.METAL, 27, 30, 16, 12, 16),
    AMMO_ZINC("ammo_zinc", "5A", "Патронный цинк", "Ammo Tin", Family.ZINC, 6, 3, 10, 7, 12),
    AMMO_ZINC_GREEN("ammo_zinc_green", "5B", "Зелёный патронный цинк", "Green Ammo Tin", Family.ZINC, 6, 3, 10, 7, 12),
    LOCKER("locker", "6A", "Шкафчик раздевалки", "Locker", Family.CABINET, 27, 40, 16, 32, 12),
    LOCKER_BLUE("locker_blue", "6B", "Облезлый шкафчик раздевалки", "Faded Locker", Family.CABINET, 27, 40, 16, 32, 12),
    FILE_CABINET("file_cabinet", "7A", "Канцелярский шкаф", "File Cabinet", Family.CABINET, 18, 35, 16, 16, 14),
    FILE_CABINET_GREY("file_cabinet_grey", "7B", "Серый канцелярский шкаф", "Grey File Cabinet", Family.CABINET, 18, 35, 16, 16, 14),
    SAFE("safe", "8A", "Сейф", "Safe", Family.SAFE, 9, 120, 14, 16, 14),
    SAFE_OLD("safe_old", "8B", "Старый сейф", "Old Safe", Family.SAFE, 9, 120, 14, 16, 14);

    /** Семейство — общая таблица добычи и материал (звук, прочность). */
    public enum Family { WOOD, METAL, ZINC, CABINET, SAFE }

    public final String id, code, ru, en;
    public final Family family;
    /** Ячеек (окно — ряды по 9; лишние ячейки закрыты). */
    public final int slots;
    /** Вес ящика как предмета, кг. */
    public final double weight;
    public final int w, h, d;

    ContainerKind(String id, String code, String ru, String en, Family family, int slots, double weight, int w, int h, int d) {
        this.id = id; this.code = code; this.ru = ru; this.en = en; this.family = family;
        this.slots = slots; this.weight = weight; this.w = w; this.h = h; this.d = d;
    }

    /** Рядов в окне сундука (9 ячеек в ряду). */
    public int rows() { return (slots + 8) / 9; }

    /** Два блока в высоту: верхняя половина — продолжение модели. */
    public boolean tall() { return h > 16; }

    public boolean metal() { return family != Family.WOOD; }

    public static ContainerKind byId(String id) {
        for (ContainerKind k : values()) if (k.id.equals(id)) return k;
        return null;
    }
}
