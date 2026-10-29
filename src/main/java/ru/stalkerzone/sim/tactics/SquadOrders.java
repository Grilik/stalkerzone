package ru.stalkerzone.sim.tactics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Приказы отряду в бою (v1.16). Отряд — бойцы одной группировки рядом, воюющие с одним противником.
 * Командир — лучший по командирским качествам. На каждые трёх бойцов один уходит во фланг (смелый и
 * собранный), медики держатся позади и поднимают раненых, остальные прижимают противника огнём.
 * Вход и выход детерминированы (сортировка по id), поэтому все бойцы получают согласованные приказы.
 */
public final class SquadOrders {
    private SquadOrders() {}

    public enum Order { LEADER, SUPPRESS, FLANK_LEFT, FLANK_RIGHT, HOLD, MEDIC }

    public record Member(long id, double command, double bravery, boolean medic, boolean steady, boolean gun) {}

    public record Assignment(long id, Order order) {}

    public static List<Assignment> assign(List<Member> members) {
        List<Member> ms = new ArrayList<>(members);
        ms.sort(Comparator.comparingLong(Member::id));
        List<Assignment> out = new ArrayList<>();
        if (ms.isEmpty()) return out;
        Member leader = ms.get(0);
        for (Member m : ms) if (m.command() > leader.command()) leader = m;
        out.add(new Assignment(leader.id(), Order.LEADER));
        List<Member> rest = new ArrayList<>();
        for (Member m : ms) if (m != leader) rest.add(m);
        int flankers = ms.size() >= 3 ? ms.size() / 3 : 0;
        // кандидаты во фланг: с оружием, собранные, смелые
        List<Member> cand = new ArrayList<>();
        for (Member m : rest) if (!m.medic() && m.gun() && m.steady()) cand.add(m);
        cand.sort(Comparator.comparingDouble(Member::bravery).reversed().thenComparingLong(Member::id));
        java.util.Set<Long> flank = new java.util.HashSet<>();
        for (int i = 0; i < Math.min(flankers, cand.size()); i++) flank.add(cand.get(i).id());
        int side = 0;
        for (Member m : rest) {
            Order o;
            if (m.medic()) o = Order.MEDIC;
            else if (flank.contains(m.id())) o = (side++ % 2 == 0) ? Order.FLANK_LEFT : Order.FLANK_RIGHT;
            else if (!m.gun()) o = Order.HOLD;
            else o = Order.SUPPRESS;
            out.add(new Assignment(m.id(), o));
        }
        return out;
    }

    public static Order orderOf(List<Assignment> a, long id) {
        for (Assignment x : a) if (x.id() == id) return x.order();
        return Order.SUPPRESS;
    }
}
