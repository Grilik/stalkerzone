package ru.stalkerzone.zonemap;

/**
 * Куда строители объектов пишут блоки. Состояние блока задаётся строкой в формате Minecraft,
 * например "minecraft:oak_stairs[facing=north]". Один и тот же код строит и мир (через адаптер Forge),
 * и превью (дамп в файл).
 */
public interface BlockSink {
    void set(int x, int y, int z, String state);

    /** Высота земли (верхний твёрдый блок + 1) в точке мира; для зданий на рельефе. */
    int ground(int x, int z);

    /** Рабочее место сборщика (карточка 031): вид («guard», «bed»…), координаты. По умолчанию — никуда. */
    default void mark(String kind, int x, int y, int z) {}

    default void fill(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, state);
    }
}
