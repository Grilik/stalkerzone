package ru.stalkerzone.sim;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Люди Зоны (v1.5). Размножения нет: новички приходят с Большой земли через Кордон и идут к выбранной фракции.
 * В пути гибнут в аномалиях и от мутантов, у пси-полей превращаются в зомби. У фракций растёт опыт и стресс,
 * при высоком стрессе бойцы дезертируют. Раненые лечатся в медпункте, шанс спасти зависит от врачей (v1.6).
 * Именные сталкеры (лидеры, заместители, командиры отрядов, специалисты) хранятся поимённо.
 */
public final class People {
    public static final int ROSTER_MAX = 14;
    /** Сколько новичков в среднем приходит через Кордон за день. */
    public static double ARRIVALS_PER_DAY = 2.4;

    /** Новичок или дезертир в пути. */
    public static final class Walker {
        public int dest;
        public int[] path;
        public int leg;
        public double bravery, greed, loyalty;
        public boolean deserter;
    }

    public final List<Person> roster = new ArrayList<>();
    public final List<Walker> transit = new ArrayList<>();
    /** Зомбированные сталкеры по точкам. */
    public final double[] zombies = new double[64];
    public int nextId = 1;
    public int arrived, joined, diedOnRoad, zombified, deserted, leftZone, woundedSaved, woundedDied;
    private int[] lastDeaths;

    // ------------------------------------------------------------------ имена

    private static final String[] FIRST = {"Сергей", "Андрей", "Дмитрий", "Алексей", "Иван", "Николай", "Виктор", "Павел", "Олег",
            "Михаил", "Юрий", "Василий", "Григорий", "Степан", "Тарас", "Богдан", "Роман", "Евгений", "Фёдор", "Игорь"};
    private static final String[] LAST = {"Петренко", "Коваленко", "Бондарь", "Шевчук", "Ткаченко", "Мельник", "Кравец", "Лысенко",
            "Савченко", "Руденко", "Гончар", "Марченко", "Литвин", "Демченко", "Волков", "Зайцев", "Орлов", "Соколов", "Кузнецов", "Беляев"};
    private static final String[] NICK = {"Волк", "Шустрый", "Хмурый", "Болотник", "Шрам", "Клык", "Сыч", "Борода", "Тихий",
            "Кабан", "Лис", "Филин", "Гвоздь", "Бармалей", "Седой", "Шнур", "Пёс", "Кот", "Ворон", "Дрозд", "Мельник", "Грач", "Штырь",
            "Призрак", "Струна", "Бугор", "Цыган", "Химик", "Монах", "Лопата"};

    private String randomName(Random r, Faction f) {
        if (f.fanatic) return "Брат " + NICK[r.nextInt(NICK.length)];
        if (f.scientist) return FIRST[r.nextInt(FIRST.length)] + " " + LAST[r.nextInt(LAST.length)];
        return FIRST[r.nextInt(FIRST.length)] + " «" + NICK[r.nextInt(NICK.length)] + "» " + LAST[r.nextInt(LAST.length)];
    }

    private static double trait(Random r) {
        return Math.max(-1, Math.min(1, r.nextGaussian() * 0.5));
    }

    // ------------------------------------------------------------------ создание именных

    Person create(Simulation s, Faction f, Random r, Person.Cls cls) {
        Person p = new Person(nextId++);
        p.name = randomName(r, f);
        p.faction = f.id;
        p.cls = cls != null ? cls : pickCls(f, r);
        p.joined = s.day;
        p.bravery = trait(r);
        p.greed = trait(r) + (f.raider ? 0.3 : 0);
        p.loyalty = trait(r) + (f.military || f.fanatic ? 0.4 : 0);
        p.xp = Math.max(0, r.nextGaussian() * 30 + f.exp * 1.2);
        int base = 25 + (int) (f.exp * 0.4);
        p.combat = skill(r, base + (p.cls == Person.Cls.ASSAULT || p.cls == Person.Cls.SNIPER ? 25 : 0));
        p.medicine = skill(r, 10 + (p.cls == Person.Cls.MEDIC ? 35 : p.cls == Person.Cls.DOCTOR ? 55 : 0));
        p.tech = skill(r, 15 + (p.cls == Person.Cls.TECH ? 45 : 0));
        p.scouting = skill(r, 20 + (p.cls == Person.Cls.SCOUT ? 40 : 0));
        p.stress = f.stress;
        roster.add(p);
        return p;
    }

    private static int skill(Random r, int mean) {
        return Math.max(1, Math.min(100, (int) Math.round(mean + r.nextGaussian() * 12)));
    }

    private static Person.Cls pickCls(Faction f, Random r) {
        if (f.scientist) return r.nextDouble() < 0.5 ? Person.Cls.DOCTOR : Person.Cls.TECH;
        double v = r.nextDouble();
        if (v < 0.35) return Person.Cls.ASSAULT;
        if (v < 0.5) return Person.Cls.SNIPER;
        if (v < 0.65) return Person.Cls.MEDIC;
        if (v < 0.73) return Person.Cls.DOCTOR;
        if (v < 0.86) return Person.Cls.TECH;
        return Person.Cls.SCOUT;
    }

    private static final String[][] LEADERS = {
            {"Волк", "Сидорович"}, {"Генерал Воронин", "Полковник Петренко"}, {"Лукаш", "Чехов"}, {"Боров", "Крюк"},
            {"Профессор Сахаров", "Профессор Круглов"}, {"Полковник Ковальский", "Майор Кузнецов"}, {"Голос Монолита", "Брат Пророк"}};

    private void seedLeaders(Simulation s, Random r) {
        for (Faction f : s.factions) {
            String[] names = f.id < LEADERS.length ? LEADERS[f.id] : new String[] {null, null};
            Person l = create(s, f, r, f.scientist ? Person.Cls.DOCTOR : Person.Cls.ASSAULT);
            if (names[0] != null) l.name = names[0];
            l.role = Person.Role.LEADER;
            l.xp = 220 + r.nextInt(80);
            l.loyalty = Math.max(l.loyalty, 0.5);
            l.combat = Math.max(l.combat, 60);
            Person d = create(s, f, r, null);
            if (names[1] != null) d.name = names[1];
            d.role = Person.Role.DEPUTY;
            d.xp = 120 + r.nextInt(60);
        }
    }

    // ------------------------------------------------------------------ запросы

    public List<Person> of(int faction) {
        List<Person> out = new ArrayList<>();
        for (Person p : roster) if (p.faction == faction) out.add(p);
        return out;
    }

    public Person leader(int faction) {
        for (Person p : roster) if (p.faction == faction && p.role == Person.Role.LEADER) return p;
        return null;
    }

    public Person byId(int id) {
        for (Person p : roster) if (p.id == id) return p;
        return null;
    }

    public int count(int faction, Person.Role role) {
        int c = 0;
        for (Person p : roster) if (p.faction == faction && p.role == role) c++;
        return c;
    }

    /** Множитель боевой силы: опыт, стресс (паника), лидер и командиры отрядов. */
    public double combatQuality(Faction f) {
        double q = 0.8 + 0.4 * f.exp / 100.0;
        if (!f.fanatic) q *= 1 - 0.3 * Math.max(0, f.stress - 50) / 50.0;
        Person l = leader(f.id);
        q *= l == null ? 0.88 : 0.97 + 0.0012 * l.combat + 0.04 * l.rank() / 3.0;
        q *= 1 + 0.025 * Math.min(4, count(f.id, Person.Role.SQUAD_LEADER));
        return q;
    }

    /** Лучший навык медицины среди здоровых медиков и врачей фракции. */
    public int bestMedic(int faction) {
        int best = 15;
        for (Person p : roster) if (p.faction == faction && p.injury == 0 && (p.cls == Person.Cls.DOCTOR || p.cls == Person.Cls.MEDIC)) best = Math.max(best, p.medicine);
        return best;
    }

    public int medics(int faction) {
        int c = 0;
        for (Person p : roster) if (p.faction == faction && p.injury == 0 && (p.cls == Person.Cls.DOCTOR || p.cls == Person.Cls.MEDIC)) c++;
        return c;
    }

    // ------------------------------------------------------------------ день

    void daily(Simulation s, Random r) {
        if (lastDeaths == null || lastDeaths.length != s.factions.size()) {
            lastDeaths = new int[s.factions.size()];
            for (Faction f : s.factions) lastDeaths[f.id] = f.deaths;
            if (roster.isEmpty()) seedLeaders(s, r);
        }
        reconcileDeaths(s, r);
        reinforcements(s, r);
        arrivals(s, r);
        travel(s, r);
        factionStats(s, r);
        treatWounded(s, r);
        zombiesDaily(s, r);
        maintainRoster(s, r);
    }

    /** Гибель людей за день (из любой причины) задевает и именных. */
    private void reconcileDeaths(Simulation s, Random r) {
        for (Faction f : s.factions) {
            int delta = f.deaths - lastDeaths[f.id];
            lastDeaths[f.id] = f.deaths;
            if (delta <= 0) continue;
            double p = Math.min(0.9, delta / (double) (f.members + delta));
            for (Iterator<Person> it = roster.iterator(); it.hasNext(); ) {
                Person x = it.next();
                if (x.faction != f.id) continue;
                double mult = x.role == Person.Role.LEADER ? 0.35 : x.role == Person.Role.DEPUTY ? 0.6 : 1.0;
                if (r.nextDouble() < p * mult) {
                    it.remove();
                    personDied(s, f, x, "погиб");
                } else if (r.nextDouble() < p * 0.8) {
                    x.injury = Math.max(x.injury, 4 + r.nextInt(10));
                    x.stress = Math.min(100, x.stress + 15);
                }
            }
        }
    }

    /** Именной сталкер выбыл: если это лидер, власть переходит к преемнику. */
    void personDied(Simulation s, Faction f, Person x, String how) {
        if (x.role == Person.Role.LEADER || x.role == Person.Role.DEPUTY) {
            s.log("«" + f.name + "»: " + how + " " + x.role.ru + " " + x.name);
        }
        if (x.role == Person.Role.LEADER) succession(s, f);
    }

    /** Преемник: лучший заместитель, иначе лучший из именных; если никого, фракция без лидера. */
    void succession(Simulation s, Faction f) {
        Person best = null;
        for (Person p : roster) {
            if (p.faction != f.id) continue;
            double score = p.command() + (p.role == Person.Role.DEPUTY ? 40 : p.role == Person.Role.SQUAD_LEADER ? 15 : 0);
            if (best == null || score > best.command() + (best.role == Person.Role.DEPUTY ? 40 : best.role == Person.Role.SQUAD_LEADER ? 15 : 0)) best = p;
        }
        if (best == null) {
            f.unity = Math.max(0, f.unity - 0.25);
            s.log("«" + f.name + "» остались без лидера: начинается безвластие");
            return;
        }
        best.role = Person.Role.LEADER;
        f.unity = Math.max(0, f.unity - 0.12);
        s.log("Новый лидер «" + f.name + "»: " + best.name + " (" + best.rankRu() + ")");
    }

    /** Убить конкретного именного (засада, игрок убил лидера в мире). Возвращает true, если он был. */
    public boolean kill(Simulation s, int personId, String how) {
        Person x = byId(personId);
        if (x == null) return false;
        roster.remove(x);
        Faction f = s.factions.get(x.faction);
        if (f.members > 1) {
            f.members--;
            f.deaths++;
            lastDeaths[f.id]++;
        }
        personDied(s, f, x, how);
        return true;
    }

    public int reinforced;

    /** Армию пополняет государство через Периметр: чем тяжелее обстановка, тем чаще. */
    private void reinforcements(Simulation s, Random r) {
        Faction m = s.military();
        if (m == null || m.members < 1) return;
        // в мирное время армия пополняется только за деньги контракта через КПП (Logistics.mainland);
        // государство бесплатно шлёт людей лишь после пробуждения Монолита
        if (!s.monolithActive) return;
        int cap = s.housingOf(m);
        if (m.members >= cap * 0.7) return;
        int every = s.monolithActive ? 2 : 4;
        if (s.day % every != 0) return;
        int add = Math.min(cap - m.members, 1 + (s.monolithActive ? r.nextInt(2) : 0));
        m.exp = (m.exp * m.members + 25.0 * add) / (m.members + add);
        m.members += add;
        reinforced += add;
    }

    private void arrivals(Simulation s, Random r) {
        Node kordon = s.nodes.get(0);
        double mean = ARRIVALS_PER_DAY * (1 + 0.35 * Math.sin(s.day / 37.0));
        int n = s.randomRound(mean);
        for (int i = 0; i < n; i++) {
            Walker w = new Walker();
            w.bravery = trait(r);
            w.greed = trait(r);
            w.loyalty = trait(r);
            arrived++;
            if (!route(s, w, kordon.id, -1, r)) leftZone++;
        }
    }

    /** Выбрать фракцию и маршрут. Возвращает false, если идти некуда (новичок уходит из Зоны). */
    private boolean route(Simulation s, Walker w, int from, int exclude, Random r) {
        double total = 0;
        double[] wt = new double[s.factions.size()];
        for (Faction f : s.factions) {
            if (!f.takesNewcomers || f.id == exclude || f.members < 1) continue;
            int room = s.housingOf(f) - f.members - pendingTo(f.id);
            if (room <= 0) continue;
            if (!s.graph.reachable(from, f.baseNode)) continue;
            double dl = w.loyalty - f.idealLoyalty, dg = w.greed - f.idealGreed;
            double fit = Math.exp(-(dl * dl + dg * dg) * 1.2);
            double prestige = Math.sqrt(f.members) * (1 + 0.15 * s.ownedCount(f));
            wt[f.id] = fit * prestige * Math.min(1, room / 4.0) * (s.nodes.get(f.baseNode).id == from ? 1.3 : 1.0);
            total += wt[f.id];
        }
        if (total <= 0) return false;
        double v = r.nextDouble() * total;
        int dest = -1;
        for (int i = 0; i < wt.length; i++) {
            v -= wt[i];
            if (wt[i] > 0 && v <= 0) { dest = i; break; }
        }
        if (dest < 0) return false;
        w.dest = dest;
        w.path = s.graph.path(from, s.factions.get(dest).baseNode);
        w.leg = 0;
        transit.add(w);
        return true;
    }

    private int pendingTo(int faction) {
        int c = 0;
        for (Walker w : transit) if (w.dest == faction) c++;
        return c;
    }

    private void travel(Simulation s, Random r) {
        List<Walker> done = new ArrayList<>();
        for (Iterator<Walker> it = transit.iterator(); it.hasNext(); ) {
            Walker w = it.next();
            int cur = w.path[w.leg];
            if (w.leg >= w.path.length - 1) {
                it.remove();
                done.add(w);
                continue;
            }
            int next = w.path[w.leg + 1];
            Node nn = s.nodes.get(next);
            double die = 0.45 * s.graph.danger(cur, next) + 0.012 * (1 - s.knowledgeOf(next)) + 0.004 * s.nodeLairPower(next) / 30.0;
            double zomb = nn.type == NodeType.PSI ? 0.25 : nn.name.equals("Радар") ? 0.04 : 0.001;
            if (r.nextDouble() < zomb) {
                it.remove();
                zombified++;
                zombies[next] += 1;
                continue;
            }
            if (r.nextDouble() < die) {
                it.remove();
                diedOnRoad++;
                continue;
            }
            w.leg++;
        }
        for (Walker w : done) join(s, w, r);
    }

    private void join(Simulation s, Walker w, Random r) {
        Faction f = s.factions.get(w.dest);
        long fee = 120;
        boolean room = s.housingOf(f) > f.members;
        if (!room || f.stock.money < fee || !f.takesNewcomers) {
            // не взяли: ищет другую фракцию отсюда же, иначе уходит
            if (!route(s, w, s.factions.get(w.dest).baseNode, w.dest, r)) leftZone++;
            return;
        }
        f.stock.money -= fee;
        s.ledger.moneyBurned += fee;
        f.exp = (f.exp * f.members + 5) / (f.members + 1);
        f.members++;
        joined++;
        int named = 0;
        for (Person p : roster) if (p.faction == f.id) named++;
        if (named < rosterTarget(f) && r.nextDouble() < 0.25) {
            Person p = create(s, f, r, null);
            p.bravery = w.bravery;
            p.greed = w.greed;
            p.loyalty = w.loyalty;
            p.xp = w.deserter ? 60 : 0;
        }
    }

    private int rosterTarget(Faction f) {
        return Math.min(ROSTER_MAX, Math.min(f.members, 4 + f.members / 8));
    }

    private void factionStats(Simulation s, Random r) {
        for (Faction f : s.factions) {
            boolean war = f.phase == Phase.WAR;
            f.exp = Math.min(100, f.exp + (war ? 0.45 : 0.03));
            if (f.fanatic) {
                f.stress = 0;
                continue;
            }
            double rest = 0.45 + 0.12 * s.qualityOf(f, BuildingType.BARRACKS) + 0.1 * s.qualityOf(f, BuildingType.CLINIC);
            double discipline = f.military ? 0.6 : 1.0;
            f.stress += (war ? 1.6 * discipline : -rest) + 0.25 * discipline; // Зона давит всегда
            if (s.stock(f, Good.FOOD) < f.members) f.stress += 1.5;
            f.stress = Math.max(0, Math.min(100, f.stress));
            if (f.stress > 70 && f.members > 10 && !f.scientist) {
                int d = s.randomRound(f.members * 0.004 * (f.stress - 70) / 30.0);
                d = Math.min(d, f.members - 8);
                for (int i = 0; i < d; i++) {
                    f.members--;
                    deserted++;
                    Walker w = new Walker();
                    w.deserter = true;
                    w.greed = Math.min(1, trait(r) + 0.4);
                    w.loyalty = trait(r) - 0.3;
                    w.bravery = trait(r);
                    if (!route(s, w, f.baseNode, f.id, r)) leftZone++;
                }
                if (d >= 3) s.log("«" + f.name + "»: от усталости и страха дезертировали " + d + " человек");
            }
            for (Person p : roster) {
                if (p.faction != f.id) continue;
                p.xp += war ? 1.0 + r.nextDouble() : 0.12;
                p.stress += (f.stress - p.stress) * 0.2 - p.bravery * 0.5;
                p.stress = Math.max(0, Math.min(100, p.stress));
                if (p.injury > 0) p.injury--;
                if (war && r.nextDouble() < 0.05) p.kills++;
            }
        }
    }

    /** Медпункт: раненых лечат медики и врачи, на лечение уходят медикаменты; без помощи часть умирает. */
    private void treatWounded(Simulation s, Random r) {
        for (Faction f : s.factions) {
            f.wounded = Math.max(0, Math.min(f.wounded, f.members - 1));
            if (f.wounded == 0) continue;
            double clinic = s.qualityOf(f, BuildingType.CLINIC);
            int skill = bestMedic(f.id);
            int capacity = 2 + (int) Math.round(4 * clinic) + 2 * medics(f.id);
            int treat = Math.min(f.wounded, capacity);
            int meds = s.takeGood(f, Good.MEDS, treat);
            int healed = 0, died = 0;
            for (int i = 0; i < f.wounded; i++) {
                boolean treated = i < meds;
                double rec = treated ? 0.22 + 0.06 * clinic + skill / 300.0 : 0.05;
                double die = treated ? 0.025 * (1 - skill / 130.0) : 0.06;
                double v = r.nextDouble();
                if (v < die) died++;
                else if (v < die + rec) healed++;
            }
            f.wounded -= healed + died;
            woundedSaved += healed;
            woundedDied += died;
            died = Math.min(died, f.members - 1);
            f.members -= died;
            f.deaths += died;
        }
    }

    private void zombiesDaily(Simulation s, Random r) {
        for (Node n : s.nodes) {
            double z = zombies[n.id];
            if (z <= 0) continue;
            z *= 0.985;
            if (n.owner >= 0) {
                Faction f = s.factions.get(n.owner);
                z *= f.fanatic ? 1.0 : 0.85;
                if (!f.fanatic && z > 5 && r.nextDouble() < 0.08 && f.members > 5) {
                    f.members--;
                    f.deaths++;
                }
            }
            zombies[n.id] = Math.min(40, z < 0.3 ? 0 : z);
        }
    }

    /** Добавить зомби у точки (погибшие под выбросом, у пси-полей). */
    void addZombies(int node, double n) {
        if (node >= 0 && node < zombies.length) zombies[node] = Math.min(40, zombies[node] + n);
    }

    /** Игрок или сталкер убил зомби в мире. */
    public void zombieKilled(int node) {
        if (node >= 0 && node < zombies.length) zombies[node] = Math.max(0, zombies[node] - 1);
    }

    /** Поддерживать именных: численность, лидера, заместителя, командиров отрядов по размеру фракции. */
    private void maintainRoster(Simulation s, Random r) {
        for (Faction f : s.factions) {
            List<Person> mine = of(f.id);
            int target = rosterTarget(f);
            while (mine.size() > Math.max(1, Math.min(target, f.members))) {
                Person drop = null;
                for (Person p : mine) if (p.role == Person.Role.MEMBER || p.role == Person.Role.SPECIALIST) { drop = p; break; }
                if (drop == null) drop = mine.get(mine.size() - 1);
                mine.remove(drop);
                roster.remove(drop);
                if (drop.role == Person.Role.LEADER) succession(s, f);
            }
            if (mine.size() < target && r.nextDouble() < 0.3) mine.add(create(s, f, r, null));
            if (leader(f.id) == null && !mine.isEmpty()) succession(s, f);
            if (count(f.id, Person.Role.DEPUTY) == 0) promote(mine, Person.Role.DEPUTY);
            int squads = Math.min(4, f.members / 15);
            while (count(f.id, Person.Role.SQUAD_LEADER) < squads && promote(mine, Person.Role.SQUAD_LEADER)) { }
            for (Person p : mine) if (p.role == Person.Role.MEMBER && (p.cls == Person.Cls.DOCTOR || p.cls == Person.Cls.TECH || p.cls == Person.Cls.MEDIC)) p.role = Person.Role.SPECIALIST;
        }
    }

    private boolean promote(List<Person> mine, Person.Role role) {
        Person best = null;
        for (Person p : mine) {
            if (p.role != Person.Role.MEMBER && p.role != Person.Role.SPECIALIST) continue;
            if (best == null || p.command() > best.command()) best = p;
        }
        if (best == null) return false;
        best.role = role;
        return true;
    }

    // ------------------------------------------------------------------ сохранение

    void write(DataOutputStream out) throws IOException {
        out.writeInt(nextId);
        out.writeInt(arrived);
        out.writeInt(joined);
        out.writeInt(diedOnRoad);
        out.writeInt(zombified);
        out.writeInt(deserted);
        out.writeInt(leftZone);
        out.writeInt(woundedSaved);
        out.writeInt(woundedDied);
        out.writeInt(lastDeaths == null ? 0 : lastDeaths.length);
        if (lastDeaths != null) for (int v : lastDeaths) out.writeInt(v);
        out.writeInt(roster.size());
        for (Person p : roster) p.write(out);
        out.writeInt(transit.size());
        for (Walker w : transit) {
            out.writeInt(w.dest);
            out.writeInt(w.path.length);
            for (int v : w.path) out.writeInt(v);
            out.writeInt(w.leg);
            out.writeDouble(w.bravery);
            out.writeDouble(w.greed);
            out.writeDouble(w.loyalty);
            out.writeBoolean(w.deserter);
        }
        for (double z : zombies) out.writeDouble(z);
    }

    void read(DataInputStream in) throws IOException {
        nextId = in.readInt();
        arrived = in.readInt();
        joined = in.readInt();
        diedOnRoad = in.readInt();
        zombified = in.readInt();
        deserted = in.readInt();
        leftZone = in.readInt();
        woundedSaved = in.readInt();
        woundedDied = in.readInt();
        int ld = in.readInt();
        lastDeaths = ld == 0 ? null : new int[ld];
        for (int i = 0; i < ld; i++) lastDeaths[i] = in.readInt();
        roster.clear();
        int rn = in.readInt();
        for (int i = 0; i < rn; i++) roster.add(Person.read(in));
        transit.clear();
        int tn = in.readInt();
        for (int i = 0; i < tn; i++) {
            Walker w = new Walker();
            w.dest = in.readInt();
            w.path = new int[in.readInt()];
            for (int k = 0; k < w.path.length; k++) w.path[k] = in.readInt();
            w.leg = in.readInt();
            w.bravery = in.readDouble();
            w.greed = in.readDouble();
            w.loyalty = in.readDouble();
            w.deserter = in.readBoolean();
            transit.add(w);
        }
        for (int i = 0; i < zombies.length; i++) zombies[i] = in.readDouble();
    }
}
