package ru.stalkerzone.sim;

/**
 * Распределение ролей NPC базы (карточка 032) — чистая логика «точки + число людей → роли»,
 * без Minecraft, чтобы её можно было проверять юнит-тестом вместе с симуляцией.
 *
 * <p>Роли совпадают с {@code StalkerEntity.ROLE_GUARD..ROLE_RESIDENT}. Правила:
 * сначала все {@code guard} (но не больше половины людей), 1–2 {@code patrol}, 1 {@code cook},
 * 1 {@code duty}, остальные — {@code mechanic}/{@code resident}.
 */
public final class BaseRoles {
    public static final int GUARD = 4, PATROL = 5, COOK = 6, MECHANIC = 7, DUTY = 8, RESIDENT = 9;
    /** Роль пулемётчика (карточка 056): новая, после жителя; своего слота в {@link #assign} не имеет. */
    public static final int MG = 10;

    /** Пулемёт пулемётчика (ПКМ, TaCZ, пак szguns). */
    public static final String MG_GUN = "szguns:pkm";

    private BaseRoles() {}

    /**
     * Сколько пулемётчиков ставить на огневую точку (карточка 056): один, если на узле есть место {@code mg}
     * и людей не меньше трёх (двое на посту — пулемёт пустует). Больше одного пулемётчика не бывает.
     */
    public static int machineGunners(int people, int mgPoints) {
        return mgPoints > 0 && people >= 3 ? 1 : 0;
    }

    /**
     * Сколько людей на каждой роли. {@code kinds} — число свободных точек по видам в порядке
     * {@code [guard, patrol, cook, work, duty, bed, rest]}. Возвращает {@code [guard, patrol, cook,
     * mechanic, duty, resident]}.
     */
    public static int[] assign(int people, int[] kinds) {
        int guards = kinds[0], patrols = kinds[1], cooks = kinds[2], works = kinds[3], duties = kinds[4];
        int[] out = new int[6];
        int left = Math.max(0, people);

        // часовые: все свободные посты, но не больше половины людей
        out[0] = Math.min(guards, people / 2);
        out[0] = Math.min(out[0], left);
        left -= out[0];

        // патруль: 1–2 человека
        out[1] = patrols > 0 && left > 0 ? Math.min(2, Math.max(1, patrols / 4 + 1)) : 0;
        out[1] = Math.min(out[1], left);
        left -= out[1];

        // повар: 1
        out[2] = cooks > 0 && left > 0 ? 1 : 0;
        left -= out[2];

        // дежурный: 1
        out[4] = duties > 0 && left > 0 ? 1 : 0;
        left -= out[4];

        // механик: до 2, сколько есть мест у техники
        out[3] = works > 0 && left > 0 ? Math.min(2, works) : 0;
        out[3] = Math.min(out[3], left);
        left -= out[3];

        // остальные — жители
        out[5] = left;
        return out;
    }
}
