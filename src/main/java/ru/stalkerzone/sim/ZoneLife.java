package ru.stalkerzone.sim;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Живая Зона (v1.14, этап 6): лента КПК, тайники погибших, слухи у костров. Чистая Java, без Minecraft.
 *
 * <ul>
 *   <li><b>Лента КПК.</b> Каждый день Зона «пишет» сама: гибель сталкеров (по настоящим потерям фракций),
 *       находки артефактов, болтовня и байки, бессмертный Семецкий.</li>
 *   <li><b>Тайники погибших.</b> С половиной погибших остаётся КПК: он лежит в поле, в нём отмечен тайник.
 *       Хабар тайника снят со склада фракции через учёт (закон сохранения не нарушается).</li>
 *   <li><b>Слухи.</b> У костров рассказывают о тайниках и созревших артефактах. Часть слухов правда (ведёт к
 *       настоящему тайнику), часть — враньё. Правдивый слух превращается в задание: место тайника отмечается.</li>
 * </ul>
 */
public final class ZoneLife {
    public static final class Msg {
        public final int day;
        public final String text;

        Msg(int day, String text) { this.day = day; this.text = text; }
    }

    public static final class Stash {
        public int id, day, faction;
        public String owner;
        /** Где лежит КПК погибшего и где сам тайник (в блоках). */
        public int pdaX, pdaZ, x, z;
        /** Хабар: число единиц по товарам (порядок Good), последний элемент — деньги. */
        public int[] loot;
        public boolean known, pdaPlaced, placed;
        public String note;
    }

    public static final class Rumor {
        public int id, day, stash = -1, x, z;
        public boolean truth;
        public String text;
    }

    public static final int MAX_FEED = 120, MAX_STASHES = 40, MAX_RUMORS = 14, RUMOR_DAYS = 25;

    public final List<Msg> feed = new ArrayList<>();
    public final List<Stash> stashes = new ArrayList<>();
    public final List<Rumor> rumors = new ArrayList<>();
    private int[] lastDeaths = new int[0];
    private int nextId = 1;

    private static final String[] TALK = {
            "Кто на Свалке? Бандиты опять трясут на мосту, обходите по насыпи.",
            "Мужики, у кого антирад лишний? Сменяю на патроны.",
            "Говорят, в Тёмной долине снова видели кровососа. Ночью туда ни ногой.",
            "Встретил бюреров у старой фермы. Еле ноги унёс, ствол бросил.",
            "Сидорович опять цены задрал. Совести у него нет.",
            "Ночью на Янтаре зомби бродят толпой. Учёные сидят тихо.",
            "Ребята, кто видел Шустрого? Ушёл к Агропрому третий день как.",
            "Доброй охоты, сталкеры. Не лезьте в «плешь» без болтов.",
            "Был у Бармена — водка нынче дороже патронов.",
            "Псы на Кордоне совсем обнаглели, днём к лагерю подходят.",
            "Слыхали? Долг со Свободой опять сцепились у Армейских складов.",
            "Кто-то видел Чёрного сталкера. Говорят, к удаче, если не заговорил.",
    };
    private static final String[] JOKES = {
            "Анекдот у костра: сталкер Петров нашёл артефакт «Батарейка» и неделю не мог уснуть.",
            "Анекдот: «Почему у Петрова три болта на поясе?» — «Два бросать, один на счастье».",
            "Анекдот: идут сталкер и учёный мимо «жарки». Учёный: «Изучим?» Сталкер: «Изучай, я подержу шапку».",
            "Анекдот: Петров спрашивает Сидоровича про скидку. Сидорович: «Скидка — это когда живым вернулся».",
            "Анекдот: «Как отличить новичка?» — «Он болты экономит».",
    };
    private static final String[] SEMETSKY = {
            "Семецкий: погиб. (опять)",
            "Сталкер Семецкий погиб у %s. Как обычно.",
            "КПК Семецкого прислал прощальное сообщение у %s. Ставим свечку… до завтра.",
    };
    private static final String[] LAST_WORDS = {
            "Тут тихо, слишком тихо…", "Кажется, я не один.", "Отметил схрон, вернусь за ним после выброса.",
            "Пёс, кажется, ранил ногу. Отлежусь.", "Слышу шаги, а никого нет.", "Если найдёте — заберите хабар, мне уже ни к чему.",
    };

    // ------------------------------------------------------------------ день

    /** Шаг жизни Зоны (после шага симуляции). Возвращает новые сообщения ленты. */
    public List<Msg> daily(Simulation s, Random r) {
        List<Msg> out = new ArrayList<>();
        if (lastDeaths.length != s.factions.size()) {
            lastDeaths = new int[s.factions.size()];
            for (Faction f : s.factions) lastDeaths[f.id] = f.deaths;
            return out;
        }
        // гибели по настоящим потерям фракций
        for (Faction f : s.factions) {
            int d = f.deaths - lastDeaths[f.id];
            lastDeaths[f.id] = f.deaths;
            for (int i = 0; i < Math.min(2, d); i++) {
                Node at = nodeOf(s, f, r);
                String name = name(f, r);
                if (f.fanatic) continue;   // Монолит своих не оплакивает в общем канале
                Stash st = r.nextDouble() < 0.5 && !f.military ? stash(s, f, at, name, r) : null;
                add(out, s.day, "Погиб: " + name + " («" + f.name + "»), " + at.name + "." + (st != null ? " КПК подаёт маяк." : ""));
            }
        }
        // находки, разговоры, анекдоты, Семецкий
        if (r.nextDouble() < 0.35) {
            Node n = anomalyNode(s, r);
            if (n != null) {
                List<Anomaly> as = s.anomaliesAt(n.id);
                if (!as.isEmpty()) {
                    Faction f = s.factions.get(r.nextInt(s.factions.size()));
                    if (!f.fanatic && !f.military)
                        add(out, s.day, name(f, r) + ": нашёл «" + as.get(r.nextInt(as.size())).type.artifact + "» у " + n.name + "! Живём, мужики.");
                }
            }
        }
        if (r.nextDouble() < 0.4) add(out, s.day, name(s.factions.get(0), r) + ": " + TALK[r.nextInt(TALK.length)]);
        if (r.nextDouble() < 0.12) add(out, s.day, JOKES[r.nextInt(JOKES.length)]);
        if (r.nextDouble() < 0.06) add(out, s.day, String.format(SEMETSKY[r.nextInt(SEMETSKY.length)], s.nodes.get(r.nextInt(s.nodes.size())).name));
        if (s.lastEmissionDay == s.day) add(out, s.day, "Выброс прошёл. Кто жив — отзовитесь. Аномалии сместились, будьте осторожны.");
        // слухи
        rumors.removeIf(x -> s.day - x.day > RUMOR_DAYS);
        if (r.nextDouble() < 0.6 && rumors.size() < MAX_RUMORS) rumors.add(rumor(s, r));
        return out;
    }

    private void add(List<Msg> out, int day, String text) {
        Msg m = new Msg(day, text);
        out.add(m);
        feed.add(m);
        while (feed.size() > MAX_FEED) feed.remove(0);
    }

    private static String name(Faction f, Random r) {
        if (f.scientist) return People.FIRST[r.nextInt(People.FIRST.length)] + " " + People.LAST[r.nextInt(People.LAST.length)];
        return People.FIRST[r.nextInt(People.FIRST.length)] + " «" + People.NICK[r.nextInt(People.NICK.length)] + "»";
    }

    /** Где погиб: на своей земле или по соседству. */
    private static Node nodeOf(Simulation s, Faction f, Random r) {
        List<Node> own = new ArrayList<>();
        for (Node n : s.nodes) if (n.owner == f.id && n.id != f.baseNode) own.add(n);
        if (own.isEmpty() || r.nextDouble() < 0.4) {
            List<Node> near = new ArrayList<>();
            for (Node n : s.nodes) if (s.graph.edgeDays(f.baseNode, n.id) > 0) near.add(n);
            if (!near.isEmpty()) return near.get(r.nextInt(near.size()));
        }
        return own.isEmpty() ? s.nodes.get(f.baseNode) : own.get(r.nextInt(own.size()));
    }

    private static Node anomalyNode(Simulation s, Random r) {
        List<Node> c = new ArrayList<>();
        for (Node n : s.nodes) if (!s.anomaliesAt(n.id).isEmpty()) c.add(n);
        return c.isEmpty() ? null : c.get(r.nextInt(c.size()));
    }

    private static int[] around(Node n, Random r, int rmin, int rmax) {
        double a = r.nextDouble() * Math.PI * 2, d = rmin + r.nextDouble() * (rmax - rmin);
        return new int[] {n.x * 10 + (int) Math.round(Math.cos(a) * d), n.z * 10 + (int) Math.round(Math.sin(a) * d)};
    }

    /** КПК и тайник погибшего; хабар — со склада его фракции, через учёт. */
    private Stash stash(Simulation s, Faction f, Node at, String owner, Random r) {
        int[] loot = s.npcLoot(f.id, r);
        int sum = 0;
        for (int v : loot) sum += v;
        if (sum == 0) return null;
        Stash st = new Stash();
        st.id = nextId++;
        st.day = s.day;
        st.faction = f.id;
        st.owner = owner;
        int[] p = around(at, r, 40, 85);
        st.pdaX = p[0];
        st.pdaZ = p[1];
        double a = r.nextDouble() * Math.PI * 2, d = 25 + r.nextDouble() * 50;
        st.x = st.pdaX + (int) Math.round(Math.cos(a) * d);
        st.z = st.pdaZ + (int) Math.round(Math.sin(a) * d);
        st.loot = loot;
        st.note = LAST_WORDS[r.nextInt(LAST_WORDS.length)];
        stashes.add(st);
        // старые нетронутые тайники Зона забирает (хабар уже списан со склада при гибели)
        while (stashes.size() > MAX_STASHES) stashes.remove(0);
        return st;
    }

    private Rumor rumor(Simulation s, Random r) {
        Rumor ru = new Rumor();
        ru.id = nextId++;
        ru.day = s.day;
        List<Stash> open = new ArrayList<>();
        for (Stash st : stashes) if (!st.known && !st.placed) open.add(st);
        if (!open.isEmpty() && r.nextDouble() < 0.6) {
            Stash st = open.get(r.nextInt(open.size()));
            ru.truth = true;
            ru.stash = st.id;
            ru.x = st.x + r.nextInt(31) - 15;
            ru.z = st.z + r.nextInt(31) - 15;
            ru.text = "Говорят, " + st.owner + " перед смертью припрятал хабар. Где-то около " + ru.x + ", " + ru.z + ".";
        } else {
            Node n = s.nodes.get(r.nextInt(s.nodes.size()));
            int[] p = around(n, r, 50, 120);
            ru.truth = false;
            ru.x = p[0];
            ru.z = p[1];
            String[] lies = {"Будто бы у " + n.name + " лежит «Мамины бусы», прямо на виду. Около " + p[0] + ", " + p[1] + ".",
                    "Слышал, военные бросили ящик с патронами у " + n.name + ", около " + p[0] + ", " + p[1] + ".",
                    "Бармен шепнул: у " + n.name + " есть схрон Стрелка. Координаты вроде " + p[0] + ", " + p[1] + "."};
            ru.text = lies[r.nextInt(lies.length)];
        }
        return ru;
    }

    public Stash stash(int id) {
        for (Stash s : stashes) if (s.id == id) return s;
        return null;
    }

    public Rumor rumor(int id) {
        for (Rumor x : rumors) if (x.id == id) return x;
        return null;
    }

    /** Игрок услышал слух: если он правдив — тайник становится известен (задание). */
    public Stash hear(Rumor ru) {
        if (ru == null || !ru.truth) return null;
        Stash st = stash(ru.stash);
        if (st != null) st.known = true;
        return st;
    }

    // ------------------------------------------------------------------ сохранение

    public void write(DataOutputStream out) throws IOException {
        out.writeInt(1);
        out.writeInt(nextId);
        out.writeInt(lastDeaths.length);
        for (int v : lastDeaths) out.writeInt(v);
        out.writeInt(feed.size());
        for (Msg m : feed) { out.writeInt(m.day); out.writeUTF(m.text); }
        out.writeInt(stashes.size());
        for (Stash s : stashes) {
            out.writeInt(s.id); out.writeInt(s.day); out.writeInt(s.faction); out.writeUTF(s.owner); out.writeUTF(s.note);
            out.writeInt(s.pdaX); out.writeInt(s.pdaZ); out.writeInt(s.x); out.writeInt(s.z);
            out.writeInt(s.loot.length);
            for (int v : s.loot) out.writeInt(v);
            out.writeBoolean(s.known); out.writeBoolean(s.pdaPlaced); out.writeBoolean(s.placed);
        }
        out.writeInt(rumors.size());
        for (Rumor x : rumors) {
            out.writeInt(x.id); out.writeInt(x.day); out.writeInt(x.stash); out.writeInt(x.x); out.writeInt(x.z);
            out.writeBoolean(x.truth); out.writeUTF(x.text);
        }
    }

    public static ZoneLife read(DataInputStream in) throws IOException {
        ZoneLife l = new ZoneLife();
        in.readInt();
        l.nextId = in.readInt();
        l.lastDeaths = new int[in.readInt()];
        for (int i = 0; i < l.lastDeaths.length; i++) l.lastDeaths[i] = in.readInt();
        for (int i = 0, n = in.readInt(); i < n; i++) l.feed.add(new Msg(in.readInt(), in.readUTF()));
        for (int i = 0, n = in.readInt(); i < n; i++) {
            Stash s = new Stash();
            s.id = in.readInt(); s.day = in.readInt(); s.faction = in.readInt(); s.owner = in.readUTF(); s.note = in.readUTF();
            s.pdaX = in.readInt(); s.pdaZ = in.readInt(); s.x = in.readInt(); s.z = in.readInt();
            s.loot = new int[in.readInt()];
            for (int k = 0; k < s.loot.length; k++) s.loot[k] = in.readInt();
            s.known = in.readBoolean(); s.pdaPlaced = in.readBoolean(); s.placed = in.readBoolean();
            l.stashes.add(s);
        }
        for (int i = 0, n = in.readInt(); i < n; i++) {
            Rumor x = new Rumor();
            x.id = in.readInt(); x.day = in.readInt(); x.stash = in.readInt(); x.x = in.readInt(); x.z = in.readInt();
            x.truth = in.readBoolean(); x.text = in.readUTF();
            l.rumors.add(x);
        }
        return l;
    }
}
