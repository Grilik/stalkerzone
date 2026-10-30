package ru.stalkerzone.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Балансовые константы и логика логов для сценария «Кордон 1.0» (карточки 005, 007).
 * Без импортов Minecraft, компилируется чистым javac.
 */
public final class KordonBalance {

    private KordonBalance() {}

    // --- стартовые логова: узел, вид, начальная сила ---------------------------------
    public static final LairDef[] START_LAIRS = {
            new LairDef(13, LairKind.DOGS, 28.0),
            new LairDef(9, LairKind.DOGS, 28.0),
            new LairDef(14, LairKind.FLESH, 26.0),
            new LairDef(15, LairKind.FLESH, 26.0),
            new LairDef(7, LairKind.RODENTS, 24.0),
    };

    /** Описание стартового логова. */
    public static final class LairDef {
        public final int node;
        public final LairKind kind;
        public final double strength;
        public LairDef(int node, LairKind kind, double strength) {
            this.node = node; this.kind = kind; this.strength = strength;
        }
    }

    // --- районы логов -----------------------------------------------------------------
    public static final int[] DISTRICT_SOUTH  = {13, 16, 9};
    public static final int[] DISTRICT_NORTH  = {14, 15, 17, 18};
    public static final int[] DISTRICT_TUNNEL = {7};
    public static final int[][] ALL_DISTRICTS = {DISTRICT_SOUTH, DISTRICT_NORTH, DISTRICT_TUNNEL};

    // --- пулы видов для возрождения ----------------------------------------------------
    public static final KindWeight[] POOL_SOUTH = {
            new KindWeight(LairKind.DOGS, 50), new KindWeight(LairKind.FLESH, 30),
            new KindWeight(LairKind.RODENTS, 15), new KindWeight(LairKind.BLOODSUCKERS, 5),
    };
    public static final KindWeight[] POOL_NORTH = {
            new KindWeight(LairKind.FLESH, 40), new KindWeight(LairKind.DOGS, 35),
            new KindWeight(LairKind.POLTERGEISTS, 15), new KindWeight(LairKind.BLOODSUCKERS, 10),
    };
    public static final KindWeight[] POOL_TUNNEL = {
            new KindWeight(LairKind.RODENTS, 70), new KindWeight(LairKind.POLTERGEISTS, 30),
    };
    public static final KindWeight[][] ALL_POOLS = {POOL_SOUTH, POOL_NORTH, POOL_TUNNEL};

    public static final class KindWeight {
        public final LairKind kind;
        public final int weight;
        public KindWeight(LairKind kind, int weight) { this.kind = kind; this.weight = weight; }
    }

    // --- константы возрождения ---------------------------------------------------------
    public static final int RESPAWN_MIN = 15;
    public static final int RESPAWN_MAX = 30;
    public static final int SHIFT_STEP = 5;
    public static final int SHIFT_MAX_CAP = 65; // BLOODSUCKERS
    public static final int SHIFT_RESET_DAYS = 30;

    // --- веса целей для нападений -------------------------------------------------------
    public static final double[] RAID_TARGET_WEIGHT = new double[19];
    static { RAID_TARGET_WEIGHT[0] = 3.0; RAID_TARGET_WEIGHT[10] = 3.0; }

    // --- опасность проходов -------------------------------------------------------------
    public static final int[][] PASS_EDGES = {
            {0, 7}, {7, 10}, {5, 9}, {9, 11},
    };
    public static final double PASS_DANGER_FLOOR = 0.5;
    /** Базовые значения опасности проходов (заполняется сценарием до первого recalc). */
    public static final double[] PASS_BASE_DANGER = new double[PASS_EDGES.length];

    public static double passDangerFactor(double strength, double cap, int daysDist) {
        if (cap <= 0) return 1.0;
        double ratio = Math.min(1.0, strength / cap);
        return PASS_DANGER_FLOOR + (1.0 - PASS_DANGER_FLOOR) * ratio / (1.0 + daysDist);
    }
// --- поиск района и окно cap ----------------------------------------------------
    public static int districtOf(int node) {
        for (int i = 0; i < ALL_DISTRICTS.length; i++)
            for (int v : ALL_DISTRICTS[i]) if (v == node) return i;
        return -1;
    }

    public static KindWeight[] pool(int district) { return ALL_POOLS[district]; }

    public static LairKind pickKindInWindow(List<LairKind> kinds, List<Integer> weights,
                                             double prevCap, int shift, Random rng) {
        double lo = prevCap - 5 + shift;
        double hi = Math.min(prevCap + 20 + shift, SHIFT_MAX_CAP);
        int totalW = 0;
        int[] w = new int[kinds.size()];
        for (int i = 0; i < kinds.size(); i++) {
            double c = kinds.get(i).cap;
            if (c >= lo && c <= hi) { w[i] = weights.get(i); totalW += w[i]; }
        }
        if (totalW <= 0) return null;
        int pick = rng.nextInt(totalW);
        for (int i = 0; i < kinds.size(); i++) {
            pick -= w[i];
            if (pick < 0) return kinds.get(i);
        }
        return kinds.get(0);
    }
}