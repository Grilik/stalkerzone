import ru.stalkerzone.sim.*;
import ru.stalkerzone.sim.tactics.*;

import java.util.*;

/** v1.16: мораль, меткость, укрытия, приказы отряду, карьера игрока и скидки (закон сохранения). */
public class TacticsTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    /** Сетка: пол y=0 и набор твёрдых блоков. */
    static final class G implements CoverFinder.Grid {
        final Set<Long> s = new HashSet<>();
        static long k(int x, int y, int z) { return ((long) (x + 1000) << 40) | ((long) (y + 1000) << 20) | (z + 1000); }
        void set(int x, int y, int z) { s.add(k(x, y, z)); }
        public boolean solid(int x, int y, int z) { return y <= 0 || s.contains(k(x, y, z)); }
    }

    public static void main(String[] a) {
        morale();
        accuracy();
        cover();
        squad();
        career();
        trade();
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ОШИБОК: " + f);
        if (f > 0) System.exit(1);
    }

    static void morale() {
        Morale brave = new Morale(0.8, false, 80), coward = new Morale(-0.8, false, 80), fan = new Morale(0, true, 80);
        for (Morale m : List.of(brave, coward, fan)) { m.allyDown(4); m.allyDown(8); m.hit(0.4); m.leaderDown(); }
        ck(brave.value() > coward.value(), "храбрый держится лучше труса (" + (int) brave.value() + " против " + (int) coward.value() + ")");
        ck(coward.state() == Morale.State.PANIC || coward.state() == Morale.State.BROKEN, "трус после гибели лидера и двоих рядом в панике: " + coward.state());
        ck(fan.state() == Morale.State.STEADY && fan.value() == 80, "фанатик Монолита не ломается");
        Morale m = new Morale(0, false, 30);
        ck(m.state() == Morale.State.PANIC, "30 = паника");
        m.second(1, true, true, false);
        ck(m.value() > 33 && m.state() == Morale.State.PANIC, "из паники выходит не сразу (гистерезис): " + (int) m.value());
        for (int i = 0; i < 20; i++) m.second(1, true, true, false);
        ck(m.state() == Morale.State.STEADY, "в укрытии рядом с командиром за ~20 секунд собирается: " + (int) m.value());
        Morale out = new Morale(0, false, 70);
        for (int i = 0; i < 15; i++) out.second(4.0, false, false, true);
        ck(out.value() < 40, "под огнём при перевесе врага 4:1 мораль тает: " + (int) out.value());
        Morale br = new Morale(0, false, 10);
        br.second(1, false, false, false);
        ck(br.state() == Morale.State.BROKEN, "сломленный не оживает от одной секунды тишины");
    }

    static double hc(int skill, double d, Morale.State st, boolean moving, boolean braced) {
        double sp = Accuracy.spreadDeg(new Accuracy.Shot(skill, d, st, false, false, moving, false, braced, false, false));
        return Accuracy.hitChance(sp, d, 0.6, 1.8);
    }

    static void accuracy() {
        double master = hc(90, 30, Morale.State.STEADY, false, true), novice = hc(20, 30, Morale.State.STEADY, false, false);
        ck(master > 0.35 && master < 0.8, String.format("мастер в упоре на 30 блоках попадает %.0f%%", master * 100));
        ck(novice > 0.05 && novice < 0.3, String.format("новичок на 30 блоках попадает %.0f%%", novice * 100));
        double panic = hc(90, 30, Morale.State.PANIC, false, false), run = hc(90, 30, Morale.State.STEADY, true, false);
        ck(panic < master * 0.4, String.format("мастер в панике мажет: %.0f%%", panic * 100));
        ck(run < master, String.format("на бегу хуже, чем в упоре: %.0f%%", run * 100));
        ck(hc(50, 8, Morale.State.STEADY, false, false) > hc(50, 40, Morale.State.STEADY, false, false), "вблизи попадают чаще, чем издалека");
        ck(Accuracy.hitChance(Accuracy.spreadDeg(new Accuracy.Shot(90, 10, Morale.State.BROKEN, false, false, false, false, false, false, false)), 10, 0.6, 1.8) == 0,
                "сломленный не стреляет");
        ck(Accuracy.reactionTicks(90, Morale.State.STEADY) < Accuracy.reactionTicks(10, Morale.State.STEADY), "опытный реагирует быстрее");
        ck(Accuracy.rankAndFileSkill(80, true) > Accuracy.rankAndFileSkill(10, false), "обстрелянная элита стреляет лучше новобранцев");
    }

    static void cover() {
        // угроза в (0, 1.6, 0), боец в (7,1,0); высокая стена x=10, z=-3..3, высота 2
        G g = new G();
        for (int z = -3; z <= 3; z++) { g.set(10, 1, z); g.set(10, 2, z); }
        ck(CoverFinder.blocked(g, 0.5, 1.6, 0.5, 12.5, 1.6, 0.5), "прямая сквозь стену перекрыта");
        ck(!CoverFinder.blocked(g, 0.5, 1.6, 0.5, 8.5, 1.6, 0.5), "до стены прямая свободна");
        CoverFinder.Spot sp = CoverFinder.find(g, 7, 1, 0, 0.5, 1.6, 0.5, new CoverFinder.Wish(12, 0, false, 8), 400, new Random(1));
        ck(sp != null && sp.x() >= 11 && Math.abs(sp.z()) <= 4 && !sp.low(), "за высокой стеной найдено полное укрытие: " + sp);
        ck(sp != null && !CoverFinder.blocked(g, 0.5, 1.6, 0.5, sp.peekX() + 0.5, sp.peekY() + 1.62, sp.peekZ() + 0.5),
                "из клетки выглядывания противник виден");
        // низкая стенка в один блок: укрытие низкое (присесть), выглядывать стоя
        G lo = new G();
        for (int z = -3; z <= 3; z++) lo.set(10, 1, z);
        CoverFinder.Spot sl = CoverFinder.find(lo, 7, 1, 0, 0.5, 1.6, 0.5, new CoverFinder.Wish(12, 0, false, 8), 400, new Random(2));
        ck(sl != null && sl.low() && sl.x() >= 11, "за низкой стенкой укрытие низкое: " + sl);
        // чистое поле: укрытия нет
        ck(CoverFinder.find(new G(), 7, 1, 0, 0.5, 1.6, 0.5, new CoverFinder.Wish(12, 0, false, 8), 400, new Random(3)) == null, "в чистом поле укрытия нет");
        // два укрытия: слева и справа; фланг выбирает своё
        G two = new G();
        for (int x = 9; x <= 11; x++) { two.set(x, 1, 7); two.set(x, 2, 7); two.set(x, 1, -7); two.set(x, 2, -7); }
        CoverFinder.Spot l = CoverFinder.find(two, 6, 1, 0, 0.5, 1.6, 0.5, new CoverFinder.Wish(12, 1, false, 10), 600, new Random(4));
        CoverFinder.Spot r = CoverFinder.find(two, 6, 1, 0, 0.5, 1.6, 0.5, new CoverFinder.Wish(12, -1, false, 10), 600, new Random(4));
        ck(l != null && r != null && Integer.signum(l.z()) != Integer.signum(r.z()), "фланговые уходят в разные стороны: " + (l == null ? "-" : l.z()) + " и " + (r == null ? "-" : r.z()));
        // паника: укрытие подальше от врага
        G far = new G();
        for (int z = -2; z <= 2; z++) { far.set(9, 1, z); far.set(9, 2, z); far.set(15, 1, z); far.set(15, 2, z); }
        CoverFinder.Spot near = CoverFinder.find(far, 8, 1, 6, 0.5, 1.6, 0.5, new CoverFinder.Wish(10, 0, false, 10), 800, new Random(5));
        CoverFinder.Spot flee = CoverFinder.find(far, 8, 1, 6, 0.5, 1.6, 0.5, new CoverFinder.Wish(10, 0, true, 10), 800, new Random(5));
        ck(near != null && flee != null && flee.x() >= near.x(), "в панике выбирает дальнее укрытие: " + (near == null ? "-" : near.x()) + " → " + (flee == null ? "-" : flee.x()));
        long t0 = System.nanoTime();
        for (int i = 0; i < 200; i++) CoverFinder.find(g, 7, 1, 0, 0.5, 1.6, 0.5, new CoverFinder.Wish(12, 0, false, 10), 80, new Random(i));
        double ms = (System.nanoTime() - t0) / 1e6 / 200;
        ck(ms < 2.0, String.format("поиск укрытия дешёвый: %.3f мс", ms));
    }

    static void squad() {
        List<SquadOrders.Member> ms = new ArrayList<>(List.of(
                new SquadOrders.Member(5, 80, 0.2, false, true, true),
                new SquadOrders.Member(2, 30, 0.9, false, true, true),
                new SquadOrders.Member(9, 20, 0.7, false, true, true),
                new SquadOrders.Member(3, 25, -0.5, false, true, true),
                new SquadOrders.Member(7, 15, 0.1, true, true, true),
                new SquadOrders.Member(4, 10, 0.5, false, false, true)));
        List<SquadOrders.Assignment> as = SquadOrders.assign(ms);
        ck(SquadOrders.orderOf(as, 5) == SquadOrders.Order.LEADER, "командует лучший по командирским качествам");
        ck(SquadOrders.orderOf(as, 7) == SquadOrders.Order.MEDIC, "медик держится позади");
        long fl = as.stream().filter(x -> x.order() == SquadOrders.Order.FLANK_LEFT || x.order() == SquadOrders.Order.FLANK_RIGHT).count();
        ck(fl == 2, "из шестерых двое идут во фланг: " + fl);
        ck(SquadOrders.orderOf(as, 2) != SquadOrders.Order.SUPPRESS && SquadOrders.orderOf(as, 9) != SquadOrders.Order.SUPPRESS, "во фланг идут смелые");
        ck(SquadOrders.orderOf(as, 2) != SquadOrders.orderOf(as, 9), "фланговые по разные стороны");
        ck(SquadOrders.orderOf(as, 4) == SquadOrders.Order.SUPPRESS, "дрогнувшего во фланг не шлют");
        Collections.reverse(ms);
        ck(SquadOrders.assign(ms).stream().sorted(Comparator.comparingLong(SquadOrders.Assignment::id)).toList()
                .equals(as.stream().sorted(Comparator.comparingLong(SquadOrders.Assignment::id)).toList()), "приказы не зависят от порядка опроса");
    }

    static void career() {
        Simulation s = ZoneScenario.create(1);
        int open = 0;
        for (Faction fa : s.factions) if (PlayerCareer.joinable(fa)) open++;
        ck(!PlayerCareer.joinable(s.factions.get(ZoneScenario.MONOLITH)) && !PlayerCareer.joinable(s.factions.get(ZoneScenario.SCIENTISTS))
                && !PlayerCareer.joinable(s.factions.get(ZoneScenario.MILITARY)) && !PlayerCareer.joinable(s.factions.get(ZoneScenario.SIN)),
                "Монолит, учёные, военные и Грех не берут игрока");
        ck(open == 6, "вступить можно в 6 группировок: " + open);
        ck(PlayerCareer.refusal(s.factions.get(ZoneScenario.LONERS), 0) == null, "одиночки берут новичка с нулевой репутацией");
        ck(PlayerCareer.refusal(s.factions.get(ZoneScenario.DUTY), 5) != null && PlayerCareer.refusal(s.factions.get(ZoneScenario.DUTY), 25) == null,
                "Долг берёт только проверенных (репутация от 20)");
        ck(PlayerCareer.refusal(s.factions.get(ZoneScenario.BANDITS), -5) == null, "бандиты берут почти всех");
        ck(PlayerCareer.rank(0) == 0 && PlayerCareer.rank(60) == 1 && PlayerCareer.rank(799) == 3 && PlayerCareer.rank(5000) == 4, "звания по опыту");
        ck(PlayerCareer.toNext(50) == 10 && PlayerCareer.toNext(900) == 0, "сколько до следующего звания");
        ck(PlayerCareer.title(ZoneScenario.DUTY, 2).equals("Сержант") && PlayerCareer.title(ZoneScenario.BANDITS, 4).equals("Авторитет"), "звания по группировкам");
        double b0 = PlayerCareer.buyFactor(0, false, 0), bF = PlayerCareer.buyFactor(50, false, 0), bM = PlayerCareer.buyFactor(50, true, 4), bE = PlayerCareer.buyFactor(-30, false, 0);
        ck(b0 == 1.0 && bF < b0 && bM < bF && bM >= 0.9 && bE > 1.0, String.format("скидки: чужой %.2f, друг %.2f, свой мастер %.2f, недруг %.2f", b0, bF, bM, bE));
        ck(PlayerCareer.sellFactor(50, true, 4) > 1.0 && PlayerCareer.sellFactor(50, true, 4) <= 1.03 && PlayerCareer.sellFactor(-30, false, 0) < 1.0, "своим торговец платит чуть больше");
        ck(PlayerCareer.buyFactor(100, true, 4) * 1.1 > PlayerCareer.sellFactor(100, true, 4) * 0.9, "даже лучшему другу перепродажа в минус (спред торговца больше скидки)");
    }

    static void trade() {
        Simulation s = ZoneScenario.create(3);
        s.step(20);
        Node k = s.nodes.get(0);
        long cash = 20000, start = cash;
        s.playerBuyFactor = PlayerCareer.buyFactor(100, true, 4);
        s.playerSellFactor = PlayerCareer.sellFactor(100, true, 4);
        Simulation.Deal d = s.playerSell(k, Good.AMMO, 30);
        cash += d.money();
        Simulation.Deal b = s.playerBuy(k, Good.AMMO, d.qty(), cash);
        cash -= b.money();
        s.playerBuyFactor = 1; s.playerSellFactor = 1;
        ck(cash <= start, "со скидкой круг продажа-покупка всё равно не в плюс (" + start + " → " + cash + ")");
        ck(s.audit().isEmpty(), "аудит сходится после сделок со скидкой");
        long p1 = s.sellPrice(k, Good.MEDS);
        s.playerBuyFactor = 0.9;
        Simulation.Deal m = s.playerBuy(k, Good.MEDS, 1, 100000);
        s.playerBuyFactor = 1;
        ck(m.qty() == 0 || m.money() < p1, "свой платит меньше полной цены (" + m.money() + " < " + p1 + ")");
        ck(s.audit().isEmpty(), "аудит после покупки со скидкой");
    }
}
