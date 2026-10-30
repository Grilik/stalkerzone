package ru.stalkerzone.sim;

/**
 * Три прохода через насыпь Кордона (карточка 003): туннель с электрами, пост военных у моста и дыра в заборе.
 * Здесь только чистая логика: допуск по владельцу прохода, пошлина за мост и вес прохода в цене пути.
 * Без импортов Minecraft, компилируется чистым javac.
 *
 * <p>Проход выбирается по цене пути: время (дни) + опасность + пошлина. Опасность прохода тем дешевле,
 * чем смелее фракция; пошлина тем дороже в днях, чем беднее фракция. Поэтому бедные и смелые чаще идут
 * через туннель и дыру, а богатые и осторожные — через мост.
 */
public final class Crossings {
    private Crossings() {}

    /** Базовая пошлина за проход через мост для чужих, руб. */
    public static final long BASE_TOLL = 120;
    /** Скидка за пункт положительной репутации. */
    public static final double REP_DISCOUNT = 0.005;
    /** Скидка за звание (0..4). */
    public static final double RANK_DISCOUNT = 0.05;
    /** Наценка за пункт отрицательной репутации. */
    public static final double REP_MARKUP = 0.01;
    /** Предел скидки и предел наценки, доли цены. */
    public static final double MAX_DISCOUNT = 0.45, MAX_MARKUP = 0.5;

    /** Сколько дней пути «стоит» единица опасности ребра. */
    public static final double DANGER_WEIGHT = 60.0;
    /** Смелость снижает вес опасности: при courage=1 опасность дешевле на эту долю. */
    public static final double COURAGE_DANGER = 0.4;
    /** Сколько дней пути «стоит» пошлина: доля цены в днях за каждый рубль. */
    public static final double TOLL_PER_RUB = 0.02;
    /** Насколько бедность удорожает пошлину (множитель при нулевых деньгах сверх запаса). */
    public static final double TOLL_POOR = 0.5;
    /** Разброс выбора прохода по цене, дней: чем больше, тем чаще берут не самый дешёвый проход. */
    public static final double PASS_SPREAD = 3.0;
    /** С какой вражды владелец прохода не пускает чужую фракцию. */
    public static final double ENEMY = 0.5;

    /** Пошлина за мост по репутации у военных и званию (0..4). Одна функция с константами. */
    public static long tollFor(int rep, int rank) {
        double disc = Math.min(MAX_DISCOUNT / 2, Math.max(0, rep) * REP_DISCOUNT)
                + Math.min(MAX_DISCOUNT / 2, Math.max(0, rank) * RANK_DISCOUNT);
        double markup = Math.min(MAX_MARKUP, Math.max(0, -rep) * REP_MARKUP);
        double mult = 1.0 - disc + markup;
        return Math.max(1, Math.round(BASE_TOLL * mult));
    }

    /** Пошлина для фракции: репутация выводится из вражды владельца к плательщику. */
    public static long tollForFaction(Simulation s, Faction owner, Faction payer, int rank) {
        double h = s.hostilityTo(owner, payer.id);
        int rep = (int) Math.round((0.5 - h) * 100);
        return tollFor(rep, rank);
    }

    /** Цена пошлины в днях пути: бедным дороже, богатым дешевле. */
    public static double tollDays(Faction f, long toll) {
        if (toll <= 0) return 0;
        long reserve = (long) f.members * 8 + 200;
        long spare = Math.max(0, f.stock.money - reserve);
        double afford = Math.min(1.0, spare / (double) Math.max(1, reserve));
        return TOLL_PER_RUB * toll * (1.0 + TOLL_POOR * (1.0 - afford));
    }
}
