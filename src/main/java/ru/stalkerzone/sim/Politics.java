package ru.stalkerzone.sim;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Фракции и власть (v1.7): единство, раскол отрезанных гарнизонов, встречи лидеров с переговорами
 * (перемирие, союз), засады на встречи, участие игрока (охрана или засада).
 */
public final class Politics {
    public static final class Truce {
        public final int a, b;
        public int until;
        public final boolean alliance;
        Truce(int a, int b, int until, boolean alliance) { this.a = a; this.b = b; this.until = until; this.alliance = alliance; }
    }

    public static final class Meeting {
        public int host, guest, venue, day;
        /** true: переговоры враждующих о перемирии; false: союзники согласуют действия. */
        public boolean negotiation;
        /** Игрок охраняет встречу (выставляет игровой слой). */
        public boolean guarded;
        public String title(Simulation s) {
            return "Встреча лидеров «" + s.factions.get(host).name + "» и «" + s.factions.get(guest).name + "» на «"
                    + s.nodes.get(venue).name + "», день " + day + (negotiation ? " (переговоры о перемирии)" : " (согласование союза)");
        }
    }

    public final List<Truce> truces = new ArrayList<>();
    public Meeting meeting;
    public int nextMeetingDay = 12;
    public final int[] cutDays = new int[64];
    public int meetingsHeld, ambushes, schisms, trucesSigned;

    public boolean truce(int a, int b) {
        for (Truce t : truces) if ((t.a == a && t.b == b) || (t.a == b && t.b == a)) return true;
        return false;
    }

    public Truce truceOf(int a, int b) {
        for (Truce t : truces) if ((t.a == a && t.b == b) || (t.a == b && t.b == a)) return t;
        return null;
    }

    void daily(Simulation s, Random r) {
        for (Iterator<Truce> it = truces.iterator(); it.hasNext(); ) {
            Truce t = it.next();
            if (s.day >= t.until) {
                it.remove();
                s.log((t.alliance ? "Союз" : "Перемирие") + " «" + s.factions.get(t.a).name + "» и «" + s.factions.get(t.b).name + "» истекло");
            }
        }
        unity(s);
        enclaves(s, r);
        meetings(s, r);
    }

    private void unity(Simulation s) {
        for (Faction f : s.factions) {
            if (f.isService()) continue;
            Person l = s.people.leader(f.id);
            double target = 0.82 + (l != null ? 0.12 * l.loyalty + 0.03 * l.rank() : -0.35)
                    - 0.25 * Math.max(0, f.stress - 55) / 45.0 - (f.phase == Phase.RECOVER ? 0.03 : 0);
            if (f.fanatic || f.military) target = Math.max(target, 0.9);
            f.unity += (Math.max(0, Math.min(1, target)) - f.unity) * 0.05;
        }
    }

    /** Точки фракции, не связанные с базой через свои точки. */
    public boolean[] connected(Simulation s, Faction f) {
        boolean[] seen = new boolean[s.nodes.size()];
        if (s.nodes.get(f.baseNode).owner != f.id) return seen;
        ArrayList<Integer> q = new ArrayList<>();
        q.add(f.baseNode);
        seen[f.baseNode] = true;
        while (!q.isEmpty()) {
            int c = q.remove(q.size() - 1);
            for (Node n : s.nodes) {
                if (seen[n.id] || n.owner != f.id || s.graph.edgeDays(c, n.id) <= 0) continue;
                seen[n.id] = true;
                q.add(n.id);
            }
        }
        return seen;
    }

    private void enclaves(Simulation s, Random r) {
        for (Faction f : s.factions) {
            if (f.fanatic || f.isService()) continue;
            boolean[] ok = connected(s, f);
            for (Node n : s.nodes) {
                if (n.owner != f.id || n.id == f.baseNode || ok[n.id]) {
                    if (n.owner == f.id || n.owner < 0) cutDays[n.id] = 0;
                    continue;
                }
                cutDays[n.id]++;
                if (cutDays[n.id] < 25) continue;
                if (f.unity > 0.7 && r.nextDouble() > 0.03) continue;
                splitNode(s, f, n);
                cutDays[n.id] = 0;
            }
            // общий раскол при развале власти
            if (f.unity < 0.3 && f.members > 40 && r.nextDouble() < 0.02) {
                Faction g = friendliest(s, f, -1);
                int k = f.members / 6;
                if (g != null && k > 0) {
                    f.members -= k;
                    g.members += k;
                    f.unity = Math.min(1, f.unity + 0.2);
                    schisms++;
                    s.log("РАСКОЛ: " + k + " бойцов «" + f.name + "» недовольны властью и ушли к «" + g.name + "»");
                }
            }
        }
    }

    private Faction friendliest(Simulation s, Faction f, int node) {
        Faction best = null;
        double bestH = 0.35;
        for (Faction g : s.factions) {
            if (g == f || g.fanatic || g.isService() || g.members < 5) continue;
            if (node >= 0 && s.minDistFromOwned(g, node) > 1) continue;
            double h = s.hostilityTo(f, g.id);
            if (h < bestH) { bestH = h; best = g; }
        }
        return best;
    }

    private void splitNode(Simulation s, Faction f, Node n) {
        int garrison = Math.max(1, Math.min(f.members - 5, (int) Math.round(f.members * 0.3 / Math.max(1, s.ownedCount(f)))));
        Faction g = friendliest(s, f, n.id);
        schisms++;
        f.nodesLost++;
        if (g != null && garrison > 0) {
            n.owner = g.id;
            f.members -= garrison;
            g.members += garrison;
            g.nodesCaptured++;
            s.log("РАСКОЛ: гарнизон «" + f.name + "» на «" + n.name + "» отрезан от базы и перешёл к «" + g.name + "» (" + garrison + " чел.)");
        } else {
            n.owner = -1;
            s.log("РАСКОЛ: гарнизон «" + f.name + "» на «" + n.name + "» отрезан от базы и разбежался");
        }
        f.unity = Math.max(0, f.unity - 0.05);
    }

    // ------------------------------------------------------------------ встречи

    private void meetings(Simulation s, Random r) {
        if (meeting == null && s.day >= nextMeetingDay) plan(s, r);
        if (meeting != null && s.day >= meeting.day) hold(s, r);
    }

    private boolean eligible(Faction f) {
        return !f.fanatic && f.members >= 10;
    }

    private void plan(Simulation s, Random r) {
        nextMeetingDay = s.day + 15 + r.nextInt(12);
        List<int[]> hostile = new ArrayList<>(), friendly = new ArrayList<>();
        for (Faction a : s.factions) {
            for (Faction b : s.factions) {
                if (a.id >= b.id || !eligible(a) || !eligible(b) || truce(a.id, b.id)) continue;
                double h = Math.max(s.hostilityTo(a, b.id), s.hostilityTo(b, a.id));
                if (h >= 0.45 && h < 0.97 && !a.raider && !b.raider) hostile.add(new int[] {a.id, b.id});
                else if (h < 0.3 && a.raider == b.raider) friendly.add(new int[] {a.id, b.id});
            }
        }
        boolean neg = !hostile.isEmpty() && (friendly.isEmpty() || r.nextDouble() < 0.55);
        List<int[]> pool = neg ? hostile : friendly;
        if (pool.isEmpty()) return;
        int[] pair = pool.get(r.nextInt(pool.size()));
        Faction a = s.factions.get(pair[0]), b = s.factions.get(pair[1]);
        Faction host = a.aggression <= b.aggression ? a : b;
        Meeting m = new Meeting();
        m.host = host.id;
        m.guest = host == a ? b.id : a.id;
        m.venue = host.baseNode;
        m.day = s.day + 4;
        m.negotiation = neg;
        meeting = m;
        s.log("Готовится: " + m.title(s));
        s.contracts.meetingContracts(s, m, r);
    }

    /** Кто может устроить засаду на встречу: враждебная обоим (или хозяину) фракция рядом с местом встречи. */
    public Faction ambusher(Simulation s, Meeting m) {
        Faction best = null;
        double bestScore = 0;
        for (Faction h : s.factions) {
            if (h.id == m.host || h.id == m.guest || h.passive || h.members < 15) continue;
            double hostile = Math.max(s.hostilityTo(h, m.host), s.hostilityTo(h, m.guest));
            if (hostile < 0.6 || s.minDistFromOwned(h, m.venue) > 2) continue;
            double score = hostile * (0.4 + h.aggression);
            if (score > bestScore) { bestScore = score; best = h; }
        }
        return best;
    }

    private void hold(Simulation s, Random r) {
        Meeting m = meeting;
        meeting = null;
        Faction a = s.factions.get(m.host), b = s.factions.get(m.guest);
        if (s.people.leader(a.id) == null || s.people.leader(b.id) == null || a.members < 5 || b.members < 5) {
            s.log("Встреча «" + a.name + "» и «" + b.name + "» сорвалась: нет лидера");
            s.contracts.meetingResolved(s, m, false, false);
            return;
        }
        meetingsHeld++;
        Faction h = ambusher(s, m);
        boolean ambush = false;
        if (h != null) {
            double p = 0.12 + 0.25 * h.aggression + 0;
            if (m.guarded) p *= 0.35;
            ambush = r.nextDouble() < p;
        }
        boolean leaderDied = false;
        if (ambush) {
            ambushes++;
            s.log("ЗАСАДА: «" + h.name + "» напали на встречу лидеров «" + a.name + "» и «" + b.name + "» на «" + s.nodes.get(m.venue).name + "»");
            for (Faction x : new Faction[] {a, b}) {
                x.resent(h.id, 2.0);
                int dead = Math.min(x.members - 1, 2 + r.nextInt(4));
                x.members -= dead;
                x.deaths += dead;
                Person l = s.people.leader(x.id);
                if (l != null && r.nextDouble() < (m.guarded ? 0.1 : 0.3)) {
                    s.people.kill(s, l.id, "убит в засаде");
                    leaderDied = true;
                }
            }
            int lost = Math.min(h.members - 1, 1 + r.nextInt(4));
            h.members -= lost;
            h.deaths += lost;
            s.contracts.meetingResolved(s, m, false, leaderDied);
            return;
        }
        Person la = s.people.leader(a.id), lb = s.people.leader(b.id);
        double hostile = Math.max(s.hostilityTo(a, b.id), s.hostilityTo(b, a.id));
        double p = m.negotiation ? 0.4 + 0.25 * (la.loyalty + lb.loyalty) / 2 - 0.15 * (la.greed + lb.greed) / 2 - 0.3 * Math.max(0, hostile - 0.7)
                : 0.8;
        boolean ok = r.nextDouble() < Math.max(0.1, Math.min(0.9, p));
        if (ok) {
            trucesSigned++;
            truces.add(new Truce(a.id, b.id, s.day + (m.negotiation ? 40 : 60), !m.negotiation));
            a.grudge[b.id] *= 0.3;
            b.grudge[a.id] *= 0.3;
            if (a.phase == Phase.WAR && a.warNode >= 0 && s.nodes.get(a.warNode).owner == b.id) s.stopWar(a, "перемирие");
            if (b.phase == Phase.WAR && b.warNode >= 0 && s.nodes.get(b.warNode).owner == a.id) s.stopWar(b, "перемирие");
            a.unity = Math.min(1, a.unity + 0.04);
            b.unity = Math.min(1, b.unity + 0.04);
            s.log((m.negotiation ? "ПЕРЕМИРИЕ: " : "СОЮЗ: ") + la.name + " («" + a.name + "») и " + lb.name + " («" + b.name + "») договорились на "
                    + (m.negotiation ? 40 : 60) + " дней");
        } else {
            a.resent(b.id, 0.3);
            b.resent(a.id, 0.3);
            s.log("Переговоры «" + a.name + "» и «" + b.name + "» ни к чему не привели");
        }
        s.contracts.meetingResolved(s, m, ok, false);
    }

    void write(DataOutputStream out) throws IOException {
        out.writeInt(truces.size());
        for (Truce t : truces) {
            out.writeInt(t.a);
            out.writeInt(t.b);
            out.writeInt(t.until);
            out.writeBoolean(t.alliance);
        }
        out.writeBoolean(meeting != null);
        if (meeting != null) {
            out.writeInt(meeting.host);
            out.writeInt(meeting.guest);
            out.writeInt(meeting.venue);
            out.writeInt(meeting.day);
            out.writeBoolean(meeting.negotiation);
            out.writeBoolean(meeting.guarded);
        }
        out.writeInt(nextMeetingDay);
        for (int c : cutDays) out.writeInt(c);
        out.writeInt(meetingsHeld);
        out.writeInt(ambushes);
        out.writeInt(schisms);
        out.writeInt(trucesSigned);
    }

    void read(DataInputStream in) throws IOException {
        truces.clear();
        int tn = in.readInt();
        for (int i = 0; i < tn; i++) truces.add(new Truce(in.readInt(), in.readInt(), in.readInt(), in.readBoolean()));
        meeting = null;
        if (in.readBoolean()) {
            Meeting m = new Meeting();
            m.host = in.readInt();
            m.guest = in.readInt();
            m.venue = in.readInt();
            m.day = in.readInt();
            m.negotiation = in.readBoolean();
            m.guarded = in.readBoolean();
            meeting = m;
        }
        nextMeetingDay = in.readInt();
        for (int i = 0; i < cutDays.length; i++) cutDays[i] = in.readInt();
        meetingsHeld = in.readInt();
        ambushes = in.readInt();
        schisms = in.readInt();
        trucesSigned = in.readInt();
    }
}
