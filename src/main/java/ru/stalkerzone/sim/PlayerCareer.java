package ru.stalkerzone.sim;

/**
 * Карьера игрока в группировке (v1.15): кого принимают, звания по опыту, скидки у торговцев.
 * Чистая логика без Minecraft; хранение и события — в ru.stalkerzone.npc.PlayerFaction.
 */
public final class PlayerCareer {
    private PlayerCareer() {}

    public static final int NONE = -1;
    /** Опыт для званий 0..4. */
    public static final int[] RANK_XP = {0, 60, 180, 400, 800};

    /** Опыт за дела. */
    public static final int XP_KILL_ENEMY = 8, XP_KILL_ENEMY_LEADER = 60, XP_KILL_MUTANT = 3, XP_CONTRACT_OWN = 25,
            XP_CONTRACT_OTHER = 10, XP_REVIVE = 5;

    /** Можно ли игроку вступить: учёные, военные, Монолит и Грех чужих не берут. */
    public static boolean joinable(Faction f) {
        return !f.fanatic && !f.scientist && !f.military && f.id != ZoneScenario.SIN;
    }

    /** Почему не берут (null — берут). rep — репутация игрока у группировки. */
    public static String refusal(Faction f, int rep) {
        if (!joinable(f)) {
            if (f.scientist) return "Учёные не берут сталкеров в штат. Работайте по контрактам.";
            if (f.military) return "Военные набирают по призыву с Большой земли, а не в Зоне.";
            return "С этими не договориться.";
        }
        int need = minRep(f);
        if (rep < need) return "«" + f.name + "» не доверяют вам: нужна репутация от " + need + " (сейчас " + rep + ").";
        return null;
    }

    /** Порог репутации для вступления: бандиты берут всех, кто им не враг, одиночки — всех, остальные — проверенных. */
    public static int minRep(Faction f) {
        if (f.raider) return -10;
        if (f.id == ZoneScenario.LONERS) return 0;
        if (f.mercenary) return 10;
        return 20;
    }

    public static int rank(int xp) {
        int r = 0;
        for (int i = 0; i < RANK_XP.length; i++) if (xp >= RANK_XP[i]) r = i;
        return r;
    }

    /** Сколько опыта до следующего звания (0 — высшее). */
    public static int toNext(int xp) {
        int r = rank(xp);
        return r + 1 < RANK_XP.length ? RANK_XP[r + 1] - xp : 0;
    }

    private static final String[][] TITLES = {
            {"Отмычка", "Сталкер", "Опытный сталкер", "Ветеран", "Мастер"},                 // Одиночки
            {"Рядовой", "Ефрейтор", "Сержант", "Лейтенант", "Капитан"},                    // Долг
            {"Новичок", "Боец", "Бывалый", "Ветеран", "Вольный мастер"},                   // Свобода
            {"Шестёрка", "Браток", "Бригадир", "Смотрящий", "Авторитет"},                  // Бандиты
            {"Лаборант", "Ассистент", "Младший научный", "Научный сотрудник", "Профессор"}, // Учёные (не вступают)
            {"Рядовой", "Ефрейтор", "Сержант", "Старшина", "Прапорщик"},                   // Военные (не вступают)
            {"Послушник", "Брат", "Брат", "Брат", "Брат"},                                 // Монолит
            {"Новобранец", "Боец", "Профи", "Ветеран", "Командир группы"},                 // Наёмники
            {"Новичок", "Боец", "Следопыт", "Ветеран", "Мастер"},                          // Чистое небо
            {"Неофит", "Носитель", "Носитель", "Носитель", "Носитель"},                    // Грех
    };

    public static String title(int faction, int rank) {
        String[] t = faction >= 0 && faction < TITLES.length ? TITLES[faction] : TITLES[0];
        return t[Math.max(0, Math.min(t.length - 1, rank))];
    }

    /**
     * Множитель цены покупки у торговца: друзьям и своим скидка, недругам наценка.
     * member — игрок состоит в группировке, которой принадлежит точка торговца; rank — его звание там.
     */
    public static double buyFactor(int rep, boolean member, int rank) {
        double disc = rep >= 40 ? 0.03 : rep >= 20 ? 0.01 : 0;
        if (member) disc += 0.03 + 0.015 * Math.max(0, rank);
        disc = Math.min(0.10, disc);
        double markup = rep <= -20 ? 0.12 : 0;
        return 1.0 - disc + markup;
    }

    /** Множитель цены продажи торговцу: своим он платит чуть больше, недругам меньше. */
    public static double sellFactor(int rep, boolean member, int rank) {
        double bonus = Math.min(0.03, (rep >= 40 ? 0.01 : 0) + (member ? 0.01 + 0.005 * Math.max(0, rank) : 0));
        double cut = rep <= -20 ? 0.1 : 0;
        return 1.0 + bonus - cut;
    }
}
