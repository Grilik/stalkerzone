package ru.stalkerzone.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Э6-01-К2: теневой реестр населения Зоны. Каждый человек — запись с постоянным {@code PersonId}
 * (long, не переиспользуется), именем и кличкой, группой, ролью, ровно одним состоянием и позицией
 * (узел + сектор). Реестр ведётся параллельно счётчикам {@link Faction#members} / {@link Group#members}
 * и ничего не решает: создание записей детерминированно от {@code simSeed}, случайность симуляции
 * ({@code Simulation.rng}) не затрагивается, в сохранение реестр не входит (теневой режим).
 *
 * <p>Сверка каждый день ({@link #sync}): число живых записей группы равно {@code Group.members},
 * сумма по фракции равна {@code Faction.members}. Именные сталкеры ({@link People#roster}) связаны
 * с записями ссылкой {@link Record#personRef}; их жизненный цикл вызывают хуки {@link People}
 * (рождение, гибель, уход). Анонимные бойцы-счётчики отражаются записями, которые сверка добивает
 * до численности группы. Изменения численности вне {@link People} (войны, расколы, мутанты)
 * покрывает ежедневная сверка.
 *
 * <p>К3 (книга населения) и К13 (смерть и наследование вещей) — отдельные карточки; переключение
 * счётчиков на реестр и секция сохранения — отдельный PR (Э6-01, шаг 5–6).
 */
public final class PersonRegistry {
    /** Ровно одно состояние записи (г2, г-П4). */
    public enum State {
        NODE("на узле"), SQUAD("в отряде"), CARAVAN("в караване"), WOUNDED("ранен"),
        CAPTIVE("в плену"), SHELTER("в укрытии"), RENTED("в аренде"), MISSING("пропал"), DEAD("мёртв");
        public final String ru;
        State(String ru) { this.ru = ru; }
    }

    /** Запись населения. PersonId постоянен и не переиспользуется; мёртвые записи хранятся (архив). */
    public static final class Record {
        public final long personId;
        public String name;
        public String nickname;
        public int faction;
        /** Группа (id строкой); null — не распределён (вольный сталкер до хаба или излишек). */
        public String group;
        public Person.Role role;
        public State state;
        /** Позиция: узел. */
        public int node;
        /** Сектор на узле (−1 — нет; сектора появятся с Э2-15). */
        public int sector = -1;
        /** День появления записи. */
        public int bornDay;
        /** Ссылка на именного Person ростера (People.nextId); −1 — анонимный боец-счётчик. */
        public int personRef = -1;

        Record(long personId, int faction, String group, Person.Role role, State state, int node, int bornDay) {
            this.personId = personId;
            this.faction = faction;
            this.group = group;
            this.role = role;
            this.state = state;
            this.node = node;
            this.bornDay = bornDay;
        }

        /** Живой = состоит в населении: не мёртв и не пропал без вести. */
        public boolean alive() { return state != State.DEAD && state != State.MISSING; }
    }

    public final List<Record> records = new ArrayList<>();
    private final Map<Integer, Record> linked = new HashMap<>();
    private long nextId = 1;
    private Random nameRnd;

    /** Сколько PersonId выдано (монотонно, не переиспользуется). */
    public long issued() { return nextId - 1; }

    /** Запись именного сталкера по ссылке Person.id (может быть мёртвой). */
    public Record byPerson(int personRef) { return linked.get(personRef); }

    /** Число живых записей группы. */
    public int aliveOf(String group) {
        int n = 0;
        for (Record r : records) if (r.alive() && group != null && group.equals(r.group)) n++;
        return n;
    }

    /** Число живых записей фракции. */
    public int aliveOfFaction(int faction) {
        int n = 0;
        for (Record r : records) if (r.alive() && r.faction == faction) n++;
        return n;
    }

    // ------------------------------------------------------------------ хуки People

    /** Рождение именного: запись связана с Person ростера (вызывается из People.create). */
    void birthPerson(Simulation s, Person p) {
        Record r = new Record(nextId++, p.faction, null, p.role, p.injury > 0 ? State.WOUNDED : State.NODE,
                baseOf(s, p.faction), s.day);
        r.name = p.name;
        r.nickname = nickOf(p.name);
        r.personRef = p.id;
        records.add(r);
        linked.put(p.id, r);
    }

    /** Гибель именного: запись мертва (вызывается из People.personDied). */
    void deathPerson(int personRef) {
        Record r = linked.get(personRef);
        if (r != null && r.alive()) r.state = State.DEAD;
    }

    /** Пополнение анонимными бойцами-счётчиками (People.join, People.reinforcements). */
    void birthAnonymous(Simulation s, Faction f, int n) {
        for (int i = 0; i < n; i++) {
            Record r = new Record(nextId++, f.id, null, Person.Role.MEMBER, State.NODE, f.baseNode, s.day);
            String nick = People.NICK[rnd(s).nextInt(People.NICK.length)];
            r.name = People.FIRST[rnd(s).nextInt(People.FIRST.length)] + " «" + nick + "» "
                    + People.LAST[rnd(s).nextInt(People.LAST.length)];
            r.nickname = nick;
            records.add(r);
        }
    }

    /** Уход из фракции (дезертирство, отток в People): n анонимных записей пропадают без вести. */
    void leftFaction(int faction, int n) {
        for (int k = 0; k < n; k++) {
            Record best = null;
            for (Record r : records) {
                if (r.personRef >= 0 || !r.alive() || r.faction != faction) continue;
                if (best == null || r.personId > best.personId) best = r;
            }
            if (best == null) break;
            best.state = State.MISSING;
        }
    }

    // ------------------------------------------------------------------ ежедневная сверка

    /**
     * Сверка реестра со счётчиками (конец дня, после Simulation.syncGroups): живые записи группы =
     * Group.members, сумма по фракции = Faction.members. Детерминированно, без случайности симуляции.
     */
    public void sync(Simulation s) {
        rnd(s);
        Map<Integer, Person> rosterById = new HashMap<>();
        for (Person p : s.people.roster) rosterById.put(p.id, p);

        // 1. именные: живая запись на каждого Person ростера; группа сбрасывается для переназначения
        for (Person p : s.people.roster) {
            Record r = linked.get(p.id);
            if (r == null || r.state == State.DEAD) {
                r = new Record(nextId++, p.faction, null, p.role, State.NODE, baseOf(s, p.faction), s.day);
                r.personRef = p.id;
                records.add(r);
                linked.put(p.id, r);
            }
            r.faction = p.faction;
            r.role = p.role;
            r.name = p.name;
            r.nickname = nickOf(p.name);
            r.group = null;
            r.state = p.injury > 0 ? State.WOUNDED : State.NODE;
        }
        // 2. именные записи, чьи Person покинули ростер → мёртв (страховка к хуку personDied)
        for (Record r : records) {
            if (r.personRef >= 0 && r.alive() && !rosterById.containsKey(r.personRef)) r.state = State.DEAD;
        }

        // 3. распределение именных по группам фракции: вместимость = Group.members
        for (Faction f : s.factions) {
            int n = f.groups.size();
            if (n == 0) continue;
            int[] rem = new int[n];
            for (int i = 0; i < n; i++) rem[i] = f.groups.get(i).members;
            HashSet<Integer> placed = new HashSet<>();
            // лидеры групп — в свою группу, если есть место
            for (int i = 0; i < n; i++) {
                Person l = f.groups.get(i).leader;
                if (l == null) continue;
                Record r = linked.get(l.id);
                if (r == null || !r.alive() || rem[i] <= 0) continue;
                place(r, f.groups.get(i));
                rem[i]--;
                placed.add(l.id);
            }
            // прочие именные — по группам с остатком вместимости; излишек (раскол) — пропал
            for (Person p : s.people.of(f.id)) {
                if (placed.contains(p.id)) continue;
                Record r = linked.get(p.id);
                if (r == null || !r.alive()) continue;
                int gi = -1;
                for (int i = 0; i < n; i++) if (rem[i] > 0) { gi = i; break; }
                if (gi >= 0) {
                    place(r, f.groups.get(gi));
                    rem[gi]--;
                } else {
                    r.state = State.MISSING;
                    r.group = null;
                }
            }
        }
        // вольные сталкеры: группа без фракции, хаб — деревня новичков
        Group free = freeGroup(s);
        if (free != null) {
            for (Person p : s.people.freeStalkers()) {
                Record r = linked.get(p.id);
                if (r != null && r.alive()) {
                    r.faction = Person.NONE;
                    r.group = free.id;
                    r.node = free.baseNode;
                }
            }
        }

        // 4. анонимные записи: раздать нераспределённые и добить до Group.members
        Map<String, List<Record>> anonByGroup = new HashMap<>();
        List<Record> unassigned = new ArrayList<>();
        Map<String, Integer> namedInGroup = new HashMap<>();
        for (Record r : records) {
            if (!r.alive()) continue;
            if (r.personRef >= 0) {
                if (r.group != null) namedInGroup.merge(r.group, 1, Integer::sum);
                continue;
            }
            if (r.group == null) unassigned.add(r);
            else anonByGroup.computeIfAbsent(r.group, k -> new ArrayList<>()).add(r);
        }
        for (Faction f : s.factions) {
            for (Group g : f.groups) {
                int need = g.members - namedInGroup.getOrDefault(g.id, 0);
                List<Record> anon = anonByGroup.computeIfAbsent(g.id, k -> new ArrayList<>());
                while (anon.size() < need && !unassigned.isEmpty()) {
                    Record r = unassigned.remove(unassigned.size() - 1);
                    r.faction = f.id;
                    r.group = g.id;
                    r.node = g.baseNode;
                    anon.add(r);
                }
                while (anon.size() < need) {
                    Record r = new Record(nextId++, f.id, g.id, Person.Role.MEMBER, State.NODE, g.baseNode, s.day);
                    String nick = People.NICK[nameRnd.nextInt(People.NICK.length)];
                    r.name = People.FIRST[nameRnd.nextInt(People.FIRST.length)] + " «" + nick + "» "
                            + People.LAST[nameRnd.nextInt(People.LAST.length)];
                    r.nickname = nick;
                    records.add(r);
                    anon.add(r);
                }
                if (anon.size() > need) {
                    anon.sort((a, b) -> Long.compare(b.personId, a.personId));
                    while (anon.size() > need) anon.remove(0).state = State.DEAD;
                }
            }
        }
        // нераспределённые анонимные, которым не хватило места → мёртв (излишек популяции)
        for (Record r : unassigned) r.state = State.DEAD;
    }

    /** Сверка для теста/оператора: пустая строка — расхождений нет. */
    public String audit(Simulation s) {
        StringBuilder sb = new StringBuilder();
        for (Faction f : s.factions) {
            if (aliveOfFaction(f.id) != f.members) {
                sb.append("фракция «").append(f.name).append("»: записей ").append(aliveOfFaction(f.id))
                        .append(", members ").append(f.members).append('\n');
            }
            for (Group g : f.groups) {
                int a = aliveOf(g.id);
                if (a != g.members) {
                    sb.append("группа «").append(g.name).append("»: записей ").append(a)
                            .append(", members ").append(g.members).append('\n');
                }
            }
        }
        Group free = freeGroup(s);
        if (free != null && aliveOf(free.id) != free.members) {
            sb.append("вольные сталкеры: записей ").append(aliveOf(free.id)).append(", members ").append(free.members).append('\n');
        }
        HashSet<Long> ids = new HashSet<>();
        for (Record r : records) {
            if (!ids.add(r.personId)) sb.append("дубль PersonId ").append(r.personId).append('\n');
            if (r.state == null) sb.append("запись ").append(r.personId).append(" без состояния\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ внутреннее

    private Random rnd(Simulation s) {
        if (nameRnd == null) nameRnd = new Random(s.seed * 1_000_003L + 17);
        return nameRnd;
    }

    private static void place(Record r, Group g) {
        r.faction = g.faction;
        r.group = g.id;
        r.node = g.baseNode;
    }

    private static Group freeGroup(Simulation s) {
        for (Group g : s.groups) if (g.isFree()) return g;
        return null;
    }

    private static int baseOf(Simulation s, int faction) {
        if (faction >= 0 && faction < s.factions.size()) return s.factions.get(faction).baseNode;
        Group free = freeGroup(s);
        return free != null ? free.baseNode : 0;
    }

    private static String nickOf(String name) {
        int a = name.indexOf('«'), b = name.indexOf('»');
        return a >= 0 && b > a ? name.substring(a + 1, b) : "";
    }
}