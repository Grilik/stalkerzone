package ru.stalkerzone.sim;

/**
 * Население узла по симуляции (карточка 033). Игровой слой ({@code NpcManager}) спрашивает это число,
 * чтобы показать на точке столько людей, сколько там есть «по правде»: гарнизон точки, а где его нет
 * (база фракции, фанатики) — доля членов фракции, минус ушедшие на задание.
 *
 * <p>Чистая Java без Minecraft: считается и тестируется отдельно от мира.
 */
public final class NodePopulation {
    /** Потолок NPC на базе (узел типа {@link NodeType#BASE}). */
    public static final int BASE_CAP = 12;
    /** Потолок NPC на прочих занятых узлах. */
    public static final int NODE_CAP = 6;
    /** Потолок NPC на малых заставах (карточка 046). */
    public static final int SMALL_OUTPOST_CAP = 4;
    /** Минимум NPC на занятом узле. */
    public static final int MIN_PER_NODE = 2;

    private NodePopulation() {}

    /**
     * Сколько NPC показывать на узле: люди фракции на узле по симуляции минус ушедшие на задание,
     * в границах [минимум, потолок]. 0 — узел ничей или гарнизон пуст.
     */
    public static int number(Simulation s, Faction f, Node n) {
        if (s == null || f == null || n == null || n.owner != f.id) return 0;
        // карточка 066: элеватор «Малого Кордона» (узел 4) — малая застава одиночек на 3–4 человека.
        if (n.id == 4 && Scenarios.KORDON.equals(s.scenario)) {
            int here = peopleAtNode(s, f, n) - Math.max(0, away(s, f, n));
            return Math.max(3, Math.min(4, here));
        }
        return visible(peopleAtNode(s, f, n), away(s, f, n), n.type == NodeType.BASE,
                KordonScenario.isSmallOutpost(n.id));
    }

    /** Число NPC по гарнизону узла и ушедшим: 0 при пустом гарнизоне, иначе в границах [2, потолок]. */
    public static int visible(int garrison, int away, boolean base) {
        return visible(garrison, away, base, false);
    }

    /** Число NPC по гарнизону узла и ушедшим: 0 при пустом гарнизоне, иначе в границах [2, потолок]. */
    public static int visible(int garrison, int away, boolean base, boolean smallOutpost) {
        if (garrison <= 0) return 0;
        int cap = base ? BASE_CAP : (smallOutpost ? SMALL_OUTPOST_CAP : NODE_CAP);
        int here = garrison - Math.max(0, away);
        return Math.max(MIN_PER_NODE, Math.min(cap, here));
    }

    /**
     * Люди фракции на узле по симуляции: явный гарнизон точки ({@link Logistics#garrison}), а где его нет
     * (база фракции, фанатики) — доля членов фракции по числу её узлов, у главной базы вдвое.
     */
    public static int peopleAtNode(Simulation s, Faction f, Node n) {
        if (n.owner != f.id) return 0;
        int g = Logistics.garrison(s, f, n);
        if (g > 0) return g;
        int owned = Math.max(1, s.ownedCount(f));
        double share = (double) Math.max(0, f.members) / owned * (n.id == f.baseNode ? 2.0 : 1.0);
        return (int) Math.round(share);
    }

    /**
     * Люди фракции, ушедшие с узла «на задание» по данным симуляции: караваны и конвои, вышедшие отсюда,
     * (наступления идут конвоями с войсками). На узле они не появляются.
     */
    public static int away(Simulation s, Faction f, Node n) {
        int away = 0;
        for (Caravan c : s.caravans) {
            if (c.owner == f.id && c.route != null && c.route.length > 0 && c.route[0] == n.id) away += Math.max(0, c.guards);
        }
        for (Convoy c : s.logistics.convoys) {
            if (c.owner == f.id && c.from() == n.id) away += Math.max(0, c.guards) + Math.max(0, c.troops);
        }
        return away;
    }
}
