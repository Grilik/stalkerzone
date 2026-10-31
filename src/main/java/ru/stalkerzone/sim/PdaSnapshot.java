package ru.stalkerzone.sim;

import ru.stalkerzone.world.ZoneBounds;

import java.util.Iterator;

/**
 * Снимок КПК (карточка 015). Построчный формат, поля через табуляцию, первое поле — тип строки.
 * Чистая логика без Minecraft: и сервер ({@code PdaService}), и юнит-тест собирают один и тот же текст.
 *
 * <p>Строки: D — день и тик, P — игрок, B — границы Зоны, N — узлы, G — дороги, F — группировки,
 * H — вражда, T — перемирия, M — встреча лидеров, E — журнал Зоны (новости), C — контракты,
 * L — лаборатории, R — исследования, A — аномалии, Z — логова мутантов,
 * X — проходы через насыпь (цена и опасность), S — торговец точки (Сидорович).
 */
public final class PdaSnapshot {
    /** Мировых блоков на единицу карты симуляции (то же, что {@link ZoneBounds#SCALE}). */
    public static final int SCALE = ZoneBounds.SCALE;

    /** Права на действие с контрактом в строке {@code C} (младшие биты). */
    public static final int CAN_TAKE = 1, CAN_ABANDON = 2, CAN_DELIVER = 4, CAN_CLAIM = 8;

    private PdaSnapshot() {}

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ');
    }

    /**
     * Собрать снимок КПК для игрока. {@code b} — границы Зоны (для рамки карты и обрезки меток),
     * {@code toll} — цена пошлины за платный проход для этого игрока (0 — пошлины нет).
     */
    public static String build(Simulation s, String me, int day, int tick, double px, double pz,
                               long rubles, ZoneBounds b, long toll) {
        StringBuilder sb = new StringBuilder();
        sb.append("D\t").append(day).append('\t').append(tick).append('\n');
        sb.append("P\t").append((int) px).append('\t').append((int) pz).append('\t').append(clean(me)).append('\t')
                .append(rubles).append('\n');
        if (b != null) sb.append("B\t").append(b.minX).append('\t').append(b.minZ).append('\t')
                .append(b.maxX).append('\t').append(b.maxZ).append('\n');
        Politics.Meeting m = s.politics.meeting;
        for (Node n : s.nodes) {
            StringBuilder fl = new StringBuilder();
            for (Faction f : s.factions) if (f.phase == Phase.WAR && f.warNode == n.id) { fl.append('w'); break; }
            if (s.lairAt(n.id) != null) fl.append('l');
            if (s.labs.at(n.id) != null) fl.append('b');
            if (n.trader) fl.append('t');
            if (isCrossing(s, n.id)) fl.append('p');
            if (m != null && m.venue == n.id) fl.append('m');
            for (Faction f : s.factions) if (f.baseNode == n.id && n.owner == f.id) { fl.append('h'); break; }
            sb.append("N\t").append(n.id).append('\t').append(clean(n.name)).append('\t').append(n.x * SCALE).append('\t')
                    .append(n.z * SCALE).append('\t').append(n.owner).append('\t').append(n.type.name()).append('\t')
                    .append(fl.length() == 0 ? "-" : fl).append('\n');
        }
        for (Node a : s.nodes) for (Node c : s.nodes)
            if (a.id < c.id && s.graph.edgeDays(a.id, c.id) > 0) sb.append("G\t").append(a.id).append('\t').append(c.id).append('\n');
        for (Faction f : s.factions) {
            Person l = s.people.leader(f.id);
            sb.append("F\t").append(f.id).append('\t').append(clean(f.name)).append('\t').append(f.members).append('\t').append(f.wounded).append('\t')
                    .append(phaseRu(f)).append('\t').append(clean(l == null ? "нет" : l.name + " (" + l.rankRu() + ")")).append('\t')
                    .append((int) f.exp).append('\t').append((int) f.stress).append('\t').append((int) (f.unity * 100)).append('\t')
                    .append(s.ownedCount(f)).append('\n');
            StringBuilder h = new StringBuilder();
            for (Faction o : s.factions) h.append(h.length() > 0 ? ";" : "").append(o.id == f.id ? -1 : (int) Math.round(s.hostilityTo(f, o.id) * 100));
            sb.append("H\t").append(f.id).append('\t').append(h).append('\n');
        }
        for (Politics.Truce t : s.politics.truces) {
            sb.append("T\t").append(clean((t.alliance ? "Союз «" : "Перемирие «") + s.factions.get(t.a).name + "» и «" + s.factions.get(t.b).name
                    + "» до дня " + t.until)).append('\n');
        }
        if (m != null) sb.append("M\t").append(clean(m.title(s))).append('\n');
        int ev = 0;
        for (Iterator<String> it = s.events.descendingIterator(); it.hasNext() && ev < 60; ev++) sb.append("E\t").append(clean(it.next())).append('\n');
        sb.append(contracts(s, me));
        sb.append(scorcherAndLabs(s));
        sb.append(marks(s, px, pz, b, toll));
        return sb.toString();
    }

    /** Контракты: видны чужие открытые и свои взятые (вместе с целью на карте). */
    private static String contracts(Simulation s, String me) {
        StringBuilder sb = new StringBuilder();
        for (Contract c : s.contracts.list) {
            if (!c.active()) continue;
            boolean mine = c.taker == Contract.PLAYER && c.player.equals(me);
            if (c.taker == Contract.PLAYER && !mine) continue;
            int act = 0;
            if (c.status == Contract.Status.OPEN) act |= CAN_TAKE;
            if (mine && c.status == Contract.Status.TAKEN) {
                act |= CAN_ABANDON;
                if (c.type == Contract.Type.FETCH_ARTIFACT || c.type == Contract.Type.SUPPLY || c.type == Contract.Type.LAB_DATA) act |= CAN_DELIVER;
            }
            if (mine && c.status == Contract.Status.READY) act |= CAN_CLAIM;
            // карточка 026: у своего контракта на логово видно счётчик целей — сколько мутантов убито из скольких
            String text = c.describe(s);
            if (mine && c.type == Contract.Type.CLEAR_LAIR && c.lairTotal > 0) {
                text += "; Мутанты: убито " + Math.min(c.lairKilled, c.lairTotal) + " из " + c.lairTotal;
            }
            // карточка 026: метка цели — на логове (смещено от центра точки), иначе центр точки
            int mx = 0, mz = 0;
            if (c.node >= 0) {
                Lair lr = s.lairAt(c.node);
                if (c.type == Contract.Type.CLEAR_LAIR && lr != null) {
                    int[] den = Lair.denXZ(s.nodes.get(c.node));
                    mx = den[0];
                    mz = den[1];
                } else {
                    mx = s.nodes.get(c.node).x * SCALE;
                    mz = s.nodes.get(c.node).z * SCALE;
                }
            }
            sb.append("C\t").append(c.id).append('\t').append(c.status.name()).append('\t').append(mine ? 1 : 0).append('\t').append(act).append('\t')
                    .append(mx).append('\t').append(mz)
                    .append('\t').append(clean(text)).append('\n');
        }
        return sb.toString();
    }

    /** Лаборатории, ход исследований и Выжигатель — вкладка «Лаборатории». */
    private static String scorcherAndLabs(Simulation s) {
        StringBuilder sb = new StringBuilder();
        for (Lab l : s.labs.labs) {
            Node n = s.nodes.get(l.node);
            sb.append("L\t").append(l.id).append('\t').append(clean(l.name)).append('\t').append(clean(n.name)).append('\t').append(l.tier).append('\t')
                    .append(l.recoveredCount()).append('\t').append(l.parts).append('\t').append(l.deliveredCount()).append('\t')
                    .append(s.labs.open(s, l) ? 1 : 0).append('\t').append(clean(l.lore)).append('\t').append(l.playerReward()).append('\t')
                    .append(n.x * SCALE + l.dx).append('\t').append(n.z * SCALE + l.dz).append('\n');
        }
        sb.append("R\t").append((int) s.scorcher.research).append('\t').append((int) s.scorcher.psiTech).append('\t').append(s.labs.cycle + 1)
                .append('\t').append(s.scorcher.disabled ? 1 : 0).append('\t').append(s.monolithActive ? 1 : 0).append('\n');
        return sb.toString();
    }

    /** Метки карты: аномалии, логова, проходы (цена и опасность) и торговцы точек. */
    private static String marks(Simulation s, double px, double pz, ZoneBounds b, long toll) {
        StringBuilder sb = new StringBuilder();
        // аномалии: известные разведке точки и всё в 60 блоках от игрока
        for (Anomaly a : s.anomalies) {
            Node n = s.nodes.get(a.node);
            int ax = n.x * SCALE + a.dx, az = n.z * SCALE + a.dz;
            boolean close = Math.hypot(ax - px, az - pz) < 60;
            if (!close && s.knowledgeOf(a.node) < 0.6) continue;
            sb.append("A\t").append(ax).append('\t').append(az).append('\t').append(clean(a.type.ru)).append('\t').append(s.anomalyTier(a)).append('\n');
        }
        for (Lair l : s.lairs) {
            int[] den = Lair.denXZ(s.nodes.get(l.node));
            sb.append("Z\t").append(b == null ? den[0] : b.clampX(den[0])).append('\t')
                    .append(b == null ? den[1] : b.clampZ(den[1])).append('\t').append(clean(l.title())).append('\t').append((int) l.strength).append('\n');
        }
        // проходы через насыпь: цена (только платный мост) и опасность
        for (int p : s.crossingNodes) {
            if (p < 0 || p >= s.nodes.size()) continue;
            Node n = s.nodes.get(p);
            long price = p == s.tollCrossing ? Math.max(0, toll) : 0;
            sb.append("X\t").append(n.x * SCALE).append('\t').append(n.z * SCALE).append('\t').append(clean(n.name)).append('\t')
                    .append(price).append('\t').append(risk(s, p)).append('\t').append(n.owner).append('\n');
        }
        // торговцы точек: на Кордоне это Сидорович в бункере
        for (Node n : s.nodes) {
            if (!n.trader) continue;
            sb.append("S\t").append(n.x * SCALE).append('\t').append(n.z * SCALE).append('\t')
                    .append(clean(TraderNames.name(n))).append('\n');
        }
        return sb.toString();
    }

    private static boolean isCrossing(Simulation s, int node) {
        for (int p : s.crossingNodes) if (p == node) return true;
        return false;
    }

    /** Опасность прохода: 0 — низкая, 1 — средняя, 2 — высокая. */
    private static int risk(Simulation s, int node) {
        double r = s.crossingRiskOf(node);
        return r >= 0.20 ? 2 : r >= 0.10 ? 1 : 0;
    }

    private static String phaseRu(Faction f) {
        return switch (f.phase) {
            case WAR -> "война";
            case RECOVER -> "восстановление";
            default -> "накопление";
        };
    }


}
