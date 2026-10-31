package ru.stalkerzone.sim;

/**
 * Пост на мосту (карточка 012): чистая логика региона {@code gate} без Minecraft, компилируется чистым javac.
 *
 * <p>Правила для игрока, вошедшего в регион:
 * <ul>
 *   <li>враг владельца (бандит на мосту военных) — предупреждение «Стоять! Назад!», через {@link #WARN_TICKS}
 *       часовые враждебны, пока он в регионе, и ещё {@link #HOSTILE_IN_TICKS} после выхода;</li>
 *   <li>остальные — цена пошлины {@link Simulation#tollFor(int, int)}; оплата открывает проход на {@link #PASS_TICKS};</li>
 *   <li>прошёл насквозь (вышел с противоположной стороны) без оплаты — военные враждебны {@link #HOSTILE_OUT_TICKS},
 *       репутация у владельца падает; если мост захвачен другой фракцией — те же правила от её лица.</li>
 * </ul>
 * Кто враг владельцу, решает {@link #enemyOf(Simulation, Faction, int)} — по матрице вражды симуляции.
 * Регион приходит из данных локации (поле {@code gate} файла точек), в коде мира нет хардкода Кордона.
 */
public final class Gate {
    /** Тиков в секунде (20 тиков = 1 с). */
    public static final int TICKS_PER_SECOND = 20;
    /** Предупреждение бандиту перед тем, как часовые откроют огонь, с. */
    public static final int WARN_TICKS = 5 * TICKS_PER_SECOND;
    /** Враждебность после выхода бандита из региона, с. */
    public static final int HOSTILE_IN_TICKS = 30 * TICKS_PER_SECOND;
    /** Враждебность за проход без оплаты, с. */
    public static final int HOSTILE_OUT_TICKS = 60 * TICKS_PER_SECOND;
    /** Сколько проход открыт после оплаты, с. */
    public static final int PASS_TICKS = 10 * 60 * TICKS_PER_SECOND;
    /** Падение репутации у владельца за проход без оплаты. */
    public static final int REP_PENALTY = 25;
    /** Запас зоны «у поста» вокруг шлагбаума, блоков (карточка 028): не меньше 6, чтобы оплата
     *  и предупреждение часового срабатывали не только стоя точно в регионе gate. */
    public static final double POST_MARGIN = 6.0;

    /** Событие очередного тика для игрового слоя. */
    public enum Event {
        /** Ничего не произошло. */
        NONE,
        /** Враг владельца вошёл: предупреждение «Стоять! Назад!». */
        WARN,
        /** Мирный вошёл: показать цену пошлины. */
        TOLL,
        /** Вход открыт (оплата или свой): ничего показывать не нужно. */
        OPEN,
        /** Прошёл насквозь без оплаты: враждебность и минус репутация. */
        VIOLATION
    }

    /** Стороны региона: точки снаружи. */
    public static final int SIDE_NONE = 0, SIDE_WEST = 1, SIDE_EAST = 2, SIDE_NORTH = 3, SIDE_SOUTH = 4;

    private Gate() {}

    /** Стороны лежат на одной оси и противоположны (значит, регион прошли насквозь). */
    static boolean opposite(int a, int b) {
        return (a == SIDE_WEST && b == SIDE_EAST) || (a == SIDE_EAST && b == SIDE_WEST)
                || (a == SIDE_NORTH && b == SIDE_SOUTH) || (a == SIDE_SOUTH && b == SIDE_NORTH);
    }

    /** Враг ли владельцу региона по матрице вражды симуляции. */
    public static boolean enemyOf(Simulation s, Faction owner, int visitorFaction) {
        if (s == null || owner == null || visitorFaction < 0) return false;
        return s.hostilityTo(owner, visitorFaction) >= Crossings.ENEMY;
    }

    /**
     * Прохождение региона одним игроком. Экземпляр держит состояние прохода: где он вошёл, оплачен ли проход,
     * до какого тика часовые враждебны. Всё в тиках — игровой слой передаёт тик сервера и позицию в блоках.
     */
    public static final class Pass {
        public final int x0, z0, x1, z1;

        private boolean inside;
        private int entrySide = SIDE_NONE;
        private double lastX = Double.NaN, lastZ = Double.NaN;
        private boolean hasLast;
        private long paidUntil;
        private long hostileFrom;
        private long hostileUntil;
        private int violations;

        public Pass(int x0, int z0, int x1, int z1) {
            this.x0 = Math.min(x0, x1);
            this.x1 = Math.max(x0, x1);
            this.z0 = Math.min(z0, z1);
            this.z1 = Math.max(z0, z1);
        }

        public boolean inside() { return inside; }
        /** Проход открыт (оплачен и не истёк). */
        public boolean open(long now) { return now < paidUntil; }
        /** Часовые враждебны прямо сейчас. */
        public boolean hostile(long now) { return now >= hostileFrom && now < hostileUntil; }
        /** Сколько раз игрок прошёл регион без оплаты. */
        public int violations() { return violations; }

        /** Оплата пошлины: проход открыт {@link Gate#PASS_TICKS}. */
        public void pay(long now) { paidUntil = now + PASS_TICKS; }

        /**
         * Продвинуть проверку на тике. {@code blocked} — игрок враг владельца (его не пускают).
         * Возвращает событие, по которому игровой слой показывает сообщение и/или меняет репутацию.
         */
        public Event tick(long now, double x, double z, boolean blocked) {
            boolean in = inRegion(x, z);
            Event ev = Event.NONE;
            if (in && !inside) {
                inside = true;
                entrySide = hasLast ? side(lastX, lastZ) : SIDE_NONE;
                if (blocked) {
                    hostileFrom = now + WARN_TICKS;
                    hostileUntil = Math.max(hostileUntil, hostileFrom + HOSTILE_IN_TICKS);
                    ev = Event.WARN;
                } else {
                    ev = open(now) ? Event.OPEN : Event.TOLL;
                }
            } else if (!in && inside) {
                inside = false;
                int exitSide = side(x, z);
                boolean through = opposite(entrySide, exitSide);
                if (blocked) {
                    // бандит: часовые не успокаиваются сразу, ещё 30 с после выхода
                    hostileUntil = Math.max(hostileUntil, now + HOSTILE_IN_TICKS);
                } else if (through && !open(now)) {
                    violations++;
                    hostileFrom = now;
                    hostileUntil = now + HOSTILE_OUT_TICKS;
                    ev = Event.VIOLATION;
                }
            }
            lastX = x; lastZ = z; hasLast = true;
            return ev;
        }

        public boolean inRegion(double x, double z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        /**
         * Зона «у поста» (карточка 028): регион шлагбаума с запасом {@link Gate#POST_MARGIN} блоков.
         * Внутри неё часовой сам предлагает оплату, а оплата проходит, даже если игрок стоит рядом
         * с постом, а не ровно в регионе gate.
         */
        public boolean nearPost(double x, double z) {
            return x >= x0 - POST_MARGIN && x <= x1 + POST_MARGIN
                    && z >= z0 - POST_MARGIN && z <= z1 + POST_MARGIN;
        }

        /** Сторона, на которой лежит точка снаружи региона. */
        private int side(double x, double z) {
            if (x < x0) return SIDE_WEST;
            if (x > x1) return SIDE_EAST;
            if (z < z0) return SIDE_NORTH;
            if (z > z1) return SIDE_SOUTH;
            return SIDE_NONE;
        }
    }
}
