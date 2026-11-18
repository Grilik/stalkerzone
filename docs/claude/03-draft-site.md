# Черновик Site.java (01.11.2026, архив из экспорта)

> **АРХИВ.** Класс `Site.java` — черновик механизма площадок, описанный 01.11.2026 до реализации. Реализованная версия — `src/main/java/ru/stalkerzone/zonemap/build/Site.java` (коммит 69311ad, PR #..., этап Э1). Этот документ объясняет **замысел** механизма: зачем площадки, как устроена генерация (рельеф → расчистка → постройки), что такое `Shaper` (плато, выемка).

Класс площадки для `src/main/java/ru/stalkerzone/zonemap/build/Site.java`. Не собирался и не тестировался: `Prefabs.Builder`/`Prefabs.Ground` — существующие интерфейсы, подключение к `LevelRegion`/`Prefabs`/`NpcManager` не сделано. Описание механизма — `claude/01-malyi-kordon.md` (архив).

```java
package ru.stalkerzone.zonemap.build;

import ru.stalkerzone.zonemap.BlockSink;

import java.util.ArrayList;
import java.util.List;

/**
 * Площадка уровня (механизм генерации построек, участок 1.0): где и как ставится группа построек одного узла.
 *
 * <p>Порядок генерации одинаковый для всех площадок:
 * <ol>
 *   <li><b>Рельеф.</b> Площадка правит карту высот уровня ещё до генерации чанков ({@link #shape}): плато — ровная
 *   земля на одной высоте с плавным откосом по краю, выемка — овраг под мостом. Рельеф Minecraft строится уже по
 *   исправленным высотам, поэтому постройки не висят и не тонут, а склоны не обрываются стенкой.</li>
 *   <li><b>Расчистка.</b> В границах {@link #clear} не ставятся кубики-воксели и деревья из геометрии оригинала.</li>
 *   <li><b>Постройки.</b> Сборщики площадки ставят блоки поверх уже ровной земли и отмечают рабочие места
 *   (пост, нары, костёр, патруль) — их берут NPC узла, к которому относится площадка.</li>
 * </ol>
 * Чистая Java (без Minecraft): площадки проверяются тестами без запуска игры.
 */
public final class Site {
    /** Высота плато: средняя (медиана) по исходной земле внутри прямоугольника. */
    public static final int AUTO = Integer.MIN_VALUE;

    /** Правка рельефа площадки: новая высота земли (в блоках, дробная) по исходной. */
    public interface Shaper {
        /** Прямоугольник {x0, z0, x1, z1}, вне которого рельеф не меняется. */
        int[] bounds();
        /** Новая высота в колонке (x, z); orig — исходная высота уровня; median — медиана исходной земли в плато. */
        double shape(int x, int z, double orig);
        /** Вызывается один раз перед правкой с функцией исходной высоты (для AUTO-высоты). */
        default void prepare(Prefabs.Ground g) {}
    }

    public final String id;
    /** Узел симуляции, чьи NPC работают на этой площадке (по номеру узла сценария «Кордон»). */
    public final int node;
    final List<int[]> clear = new ArrayList<>();
    final List<Shaper> shapers = new ArrayList<>();
    final List<Prefabs.Builder> builders = new ArrayList<>();

    public Site(String id, int node) { this.id = id; this.node = node; }

    /** Расчистить прямоугольник от вокселей и деревьев оригинала. */
    public Site clear(int x0, int z0, int x1, int z1) {
        clear.add(new int[] {Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)});
        return this;
    }

    public Site clear(List<int[]> rects) { for (int[] c : rects) clear(c[0], c[1], c[2], c[3]); return this; }

    /**
     * Плато: внутри прямоугольника земля ровно на высоте y (или медиане при {@link #AUTO}), на полосе margin
     * вокруг — плавный переход к исходному рельефу. Полоса тоже расчищается.
     */
    public Site plateau(int x0, int z0, int x1, int z1, int y, int margin) {
        Plateau p = new Plateau(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), y, margin);
        shapers.add(p);
        return clear(p.x0 - margin, p.z0 - margin, p.x1 + margin, p.z1 + margin);
    }

    public Site shaper(Shaper s) { shapers.add(s); return this; }

    public Site build(Prefabs.Builder b) { builders.add(b); return this; }

    public Site build(java.util.function.ObjIntConsumer<BlockSink> b) {
        builders.add(new Prefabs.Builder() {
            @Override public List<int[]> clear() { return List.of(); }
            @Override public void build(BlockSink sink, int lvl) { b.accept(sink, lvl); }
        });
        return this;
    }

    public List<int[]> clearRects() { return clear; }

    public List<Shaper> shapers() { return shapers; }

    public boolean covers(int x, int z) {
        for (int[] c : clear) if (x >= c[0] && x <= c[2] && z >= c[1] && z <= c[3]) return true;
        return false;
    }

    /** Ровная площадка с откосом. */
    public static final class Plateau implements Shaper {
        public final int x0, z0, x1, z1, margin;
        private final int want;
        private double y;

        Plateau(int x0, int z0, int x1, int z1, int y, int margin) {
            this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; this.want = y; this.margin = Math.max(1, margin);
            this.y = y == AUTO ? 0 : y + 0.5;
        }

        /** Высота поверхности плато в блоках (верхний блок земли). */
        public int top() { return (int) Math.floor(y); }

        @Override public int[] bounds() { return new int[] {x0 - margin, z0 - margin, x1 + margin, z1 + margin}; }

        @Override public void prepare(Prefabs.Ground g) {
            if (want != AUTO) return;
            List<Integer> hs = new ArrayList<>();
            for (int x = x0; x <= x1; x += 2) for (int z = z0; z <= z1; z += 2) hs.add(g.at(x, z));
            hs.sort(Integer::compare);
            y = hs.get(hs.size() / 2) + 0.5;
        }

        @Override public double shape(int x, int z, double orig) {
            double dx = Math.max(0, Math.max(x0 - x, x - x1)), dz = Math.max(0, Math.max(z0 - z, z - z1));
            double d = Math.hypot(dx, dz);
            if (d <= 0) return y;
            if (d >= margin) return orig;
            double t = d / margin;
            t = t * t * (3 - 2 * t);
            return y * (1 - t) + orig * t;
        }
    }

    /**
     * Выемка вдоль линии (овраг под мостом): дно на высоте bottom шириной 2·half, откосы шириной slope.
     * Земля только опускается — там, где исходный рельеф ниже, он остаётся.
     */
    public static final class Trench implements Shaper {
        final double ax, az, bx, bz, bottom, half, slope;

        public Trench(double ax, double az, double bx, double bz, double bottom, double half, double slope) {
            this.ax = ax; this.az = az; this.bx = bx; this.bz = bz; this.bottom = bottom; this.half = half; this.slope = slope;
        }

        @Override public int[] bounds() {
            int p = (int) Math.ceil(half + slope) + 1;
            return new int[] {(int) Math.floor(Math.min(ax, bx)) - p, (int) Math.floor(Math.min(az, bz)) - p,
                    (int) Math.ceil(Math.max(ax, bx)) + p, (int) Math.ceil(Math.max(az, bz)) + p};
        }

        /** Расстояние от точки до отрезка выемки. */
        public double dist(double x, double z) {
            double vx = bx - ax, vz = bz - az, l2 = vx * vx + vz * vz;
            double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / l2));
            return Math.hypot(x - (ax + t * vx), z - (az + t * vz));
        }

        @Override public double shape(int x, int z, double orig) {
            double d = dist(x + 0.5, z + 0.5);
            if (d >= half + slope) return orig;
            double t = d <= half ? 0 : (d - half) / slope;
            t = t * t * (3 - 2 * t);
            double v = bottom + 0.5 + (orig - bottom - 0.5) * t;
            return Math.min(orig, v);
        }
    }
}
```

## Зачем этот документ

Код `Site.java` в репозитории — результат нескольких итераций (PR #..., правки после тестов). Этот черновик показывает **исходный замысел** до реализации — полезно при рефакторинге или объяснении новому разработчику, почему площадки устроены именно так.

**Ключевые идеи:**
1. **Порядок генерации (рельеф → расчистка → постройки)** не меняется между площадками — одинаковый для деревни, АТП, элеватора.
2. **Shaper (плато, выемка)** правит карту высот **до генерации чанков** — поэтому рельеф Minecraft не конфликтует с постройками.
3. **Чистая Java, без Minecraft** — площадки тестируются без запуска игры (юнит-тесты `*Test.java` в `src/test/java`).

Актуальная реализация может отличаться (новые методы, оптимизации, исправления багов). Если нужна правда — читать `src/main/java/.../Site.java`, не этот архив.
