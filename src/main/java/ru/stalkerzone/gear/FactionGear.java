package ru.stalkerzone.gear;

import ru.stalkerzone.sim.Arms;
import ru.stalkerzone.sim.Faction;
import ru.stalkerzone.sim.Good;

import java.util.Map;
import java.util.Random;

/**
 * Во что одеты NPC группировок (v1.12). Рядовые — в кустарное своей мастерской, профессиональные группы
 * (доля {@link Faction#eliteShare}) — в лучшие линейки, прототипы или заводское, если оно есть на складе фракции.
 */
public final class FactionGear {
    private FactionGear() {}

    private record Pick(Suit[] suits, int[] weights) {
        Suit roll(Random r) {
            int sum = 0;
            for (int w : weights) sum += w;
            int k = r.nextInt(Math.max(1, sum));
            for (int i = 0; i < suits.length; i++) { k -= weights[i]; if (k < 0) return suits[i]; }
            return suits[0];
        }
    }

    private static Pick p(Object... a) {
        Suit[] s = new Suit[a.length / 2];
        int[] w = new int[a.length / 2];
        for (int i = 0; i < s.length; i++) { s[i] = (Suit) a[i * 2]; w[i] = (Integer) a[i * 2 + 1]; }
        return new Pick(s, w);
    }

    private static final Map<String, Pick> RANK = Map.of(
            "Одиночки", p(Suit.JACKET, 4, Suit.ZARYA, 5, Suit.PROTO_SCOUT, 1),
            "Долг", p(Suit.DUTY, 6, Suit.ZARYA, 1),
            "Свобода", p(Suit.FREEDOM, 6, Suit.ZARYA, 1),
            "Бандиты", p(Suit.BANDIT, 6, Suit.JACKET, 3),
            "Учёные", p(Suit.ECOLOG, 6),
            "Военные", p(Suit.BERILL, 7),
            "Монолит", p(Suit.MONOLITH, 7));

    private static final Map<String, Pick> ELITE = Map.of(
            "Одиночки", p(Suit.SEVA, 3, Suit.PROTO_GUARD, 1, Suit.EXO, 1),
            "Долг", p(Suit.DUTY_HEAVY, 3, Suit.EXO, 1, Suit.BULAT, 1),
            "Свобода", p(Suit.FREEDOM_GUARD, 3, Suit.EXO, 1, Suit.MERC, 1),
            "Бандиты", p(Suit.ZARYA, 2, Suit.PROTO_SCOUT, 1, Suit.MERC, 1),
            "Учёные", p(Suit.ECOLOG_M, 2, Suit.PROTO_GUARD, 2, Suit.BERILL, 1),
            "Военные", p(Suit.BULAT, 3, Suit.SKAT, 2),
            "Монолит", p(Suit.MONOLITH, 2, Suit.EXO, 1));

    /** Входит ли боец в профессиональную группу (лидеры и заместители — всегда). */
    public static boolean elite(Faction f, boolean leader, Random r) {
        return leader || f.military || r.nextDouble() < f.eliteShare;
    }

    /** Костюм для бойца; заводское — только если оно есть у фракции (иначе берут кустарное своей линейки). */
    public static Suit suit(Faction f, boolean elite, Random r) {
        Pick pick = (elite ? ELITE : RANK).get(f.name);
        if (pick == null) return Suit.JACKET;
        Suit s = pick.roll(r);
        if (s.origin == Suit.Origin.IMPORT && !f.military && f.stock.get(Good.GEAR_PRO) <= 0) s = RANK.get(f.name).roll(r);
        if (s.origin == Suit.Origin.PROTO && f.stock.get(Good.PROTO) <= 0 && !f.scientist) s = RANK.get(f.name).roll(r);
        return s;
    }

    /** Заводской ли ствол у бойца: армия и профессиональные группы с заводским на складе. */
    public static boolean factoryGun(Faction f, boolean elite) {
        if (f.military) return true;
        return elite && f.stock.get(Good.GEAR_PRO) > 0 || Arms.gearUnits(f.stock) > 0 && f.stock.get(Good.GEAR) == 0 && f.stock.get(Good.GEAR_PRO) > 0;
    }
}
