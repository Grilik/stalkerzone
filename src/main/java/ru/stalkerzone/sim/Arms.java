package ru.stalkerzone.sim;

/**
 * Качество вооружения в симуляции (v1.12): кустарное, прототипы и заводское с Большой земли.
 *
 * <p>Заводские патроны бьют сильнее и реже дают осечки: одна единица стоит 1,6 кустарной в бою.
 * Заводская броня держит пули лучше (1,6), но хуже защищает от аномалий (0,55); кустарная броня группировок
 * сделана под условия Зоны (1,0 и 1,0); прототипы — середина (1,15 и 0,95).
 * Профессиональные группы (снайперы, штурмовики, экспедиции) получают заводское первыми.
 */
public final class Arms {
    private Arms() {}

    public static final double AMMO_PRO_VALUE = 1.6;
    public static final double PROTO_COMBAT = 1.15, PRO_COMBAT = 1.6;
    public static final double PROTO_ANOMALY = 0.95, PRO_ANOMALY = 0.55;

    /** Боевая ценность патронов на складе. */
    public static double ammoValue(Stock s) {
        return s.get(Good.AMMO) + AMMO_PRO_VALUE * s.get(Good.AMMO_PRO);
    }

    /** Единиц снаряжения любого происхождения. */
    public static int gearUnits(Stock s) {
        return s.get(Good.GEAR) + s.get(Good.PROTO) + s.get(Good.GEAR_PRO);
    }

    /** Боевая ценность снаряжения (защита от пуль и оружие). */
    public static double gearCombat(Stock s) {
        return s.get(Good.GEAR) + PROTO_COMBAT * s.get(Good.PROTO) + PRO_COMBAT * s.get(Good.GEAR_PRO);
    }

    /** Средняя защита снаряжения от аномалий 0.55..1 (1 — всё кустарное зонное), 1 при пустом складе. */
    public static double anomalyGuard(Stock s) {
        int n = gearUnits(s);
        if (n == 0) return 1;
        return (s.get(Good.GEAR) + PROTO_ANOMALY * s.get(Good.PROTO) + PRO_ANOMALY * s.get(Good.GEAR_PRO)) / n;
    }

    /** Доля заводского снаряжения в общем 0..1. */
    public static double proShare(Stock s) {
        int n = gearUnits(s);
        return n == 0 ? 0 : s.get(Good.GEAR_PRO) / (double) n;
    }

    /** Бойцов профессиональных групп фракции. */
    public static int elite(Faction f) {
        if (f.military) return f.members;
        return (int) Math.ceil(f.members * f.eliteShare);
    }

    /**
     * Потратить патроны на бой или содержание. need — в «кустарных» единицах; заводские идут первыми
     * у армии и у профессиональных групп (доля proFirst от потребности). Возвращает полученную ценность.
     */
    public static double spendAmmo(Simulation s, Stock st, double need, double proFirst) {
        if (need <= 0) return 0;
        double got = 0;
        // доля потребности, которую закрывают заводскими (профессиональные группы)
        double proNeed = need * Math.max(0, Math.min(1, proFirst));
        int p = st.take(Good.AMMO_PRO, (int) Math.ceil(proNeed / AMMO_PRO_VALUE));
        s.ledger.destroyed[Good.AMMO_PRO.ordinal()] += p;
        got += p * AMMO_PRO_VALUE;
        int k = st.take(Good.AMMO, (int) Math.ceil(Math.max(0, need - got)));
        s.ledger.destroyed[Good.AMMO.ordinal()] += k;
        got += k;
        if (got < need) {
            // кустарные кончились: стреляют тем, что есть
            p = st.take(Good.AMMO_PRO, (int) Math.ceil((need - got) / AMMO_PRO_VALUE));
            s.ledger.destroyed[Good.AMMO_PRO.ordinal()] += p;
            got += p * AMMO_PRO_VALUE;
        }
        return got;
    }

    /** Потеря снаряжения (погибшие, брошенное в аномалиях): уходит пропорционально запасам. */
    public static int loseGear(Simulation s, Stock st, int n) {
        int lost = 0;
        for (int i = 0; i < n; i++) {
            int total = gearUnits(st);
            if (total == 0) break;
            int r = s.rng.nextInt(total);
            Good g = r < st.get(Good.GEAR) ? Good.GEAR : r < st.get(Good.GEAR) + st.get(Good.PROTO) ? Good.PROTO : Good.GEAR_PRO;
            st.take(g, 1);
            s.ledger.destroyed[g.ordinal()]++;
            lost++;
        }
        return lost;
    }

    /**
     * Особая нужда: только тогда группировка закупает дорогое заводское для профессиональных групп.
     * Война, наступление, месть, зачистка логов, отпор Монолиту; армия — всегда.
     */
    public static boolean specialNeed(Faction f) {
        if (f.military) return true;
        if (f.fanatic || f.passive) return false;
        if (f.phase == Phase.WAR) return true;
        return switch (f.goal) {
            case EXPAND, REVENGE, CLEANSE, RESIST -> true;
            default -> false;
        };
    }
}
