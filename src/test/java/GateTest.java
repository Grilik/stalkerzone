import ru.stalkerzone.sim.*;

/**
 * Карточка 012: логика региона gate на мосту (пост военных) на чистой модели без Minecraft.
 * Проверяем вход, оплату, проход без оплаты и смену владельца моста.
 */
public class GateTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    // регион моста из файла точек l01_escape.json (поле gate узла «Разрушенный мост»)
    static final int X0 = 255, Z0 = -309, X1 = 279, Z1 = -285;
    // индекс узла «Разрушенный мост» (KordonScenario.BRIDGE, порядок узлов менять нельзя)
    static final int BRIDGE = 8;

    public static void main(String[] a) {
        entry();
        pay();
        postZone();
        passWithoutPay();
        backOutIsFine();
        ownerChange();
        banditTiming();
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    /** Карточка 028: зона «у поста» — регион шлагбаума с запасом не меньше 6 блоков. */
    static void postZone() {
        Gate.Pass p = new Gate.Pass(X0, Z0, X1, Z1);
        ck(Gate.POST_MARGIN >= 6.0, "запас зоны поста не меньше 6 блоков");
        ck(p.nearPost(X0, Z0), "в самом регионе — у поста");
        ck(p.nearPost(X0 - 6, Z0 - 6), "в 6 блоках от угла региона — ещё у поста");
        ck(p.nearPost(267, Z1 + 6), "в 6 блоках к северу от региона — ещё у поста");
        ck(!p.nearPost(X0 - 6.5, -297), "дальше 6 блоков к западу — уже не у поста");
        ck(!p.nearPost(267, Z1 + 6.5), "дальше 6 блоков к северу — уже не у поста");
        // оплатить можно рядом с постом, а не только стоя ровно в регионе
        ck(p.nearPost(X0 - 3, Z1 + 3), "рядом с постом оплата доступна");
    }

    /** Вход в регион: мирному — цена пошлины, врагу владельца — предупреждение. */
    static void entry() {
        Gate.Pass p = new Gate.Pass(X0, Z0, X1, Z1);
        // идём с юга (z больше региона) на север
        ck(p.tick(0, 267, -280, false) == Gate.Event.NONE, "снаружи — тихо");
        ck(p.tick(20, 267, -297, false) == Gate.Event.TOLL, "мирный вошёл в регион: показать цену");
        ck(p.inside(), "мирный внутри региона");
        ck(!p.hostile(20), "мирного не считают врагом");
        ck(p.violations() == 0, "проходов без оплаты пока нет");

        Gate.Pass b = new Gate.Pass(X0, Z0, X1, Z1);
        b.tick(0, 267, -280, true);
        ck(b.tick(20, 267, -297, true) == Gate.Event.WARN, "бандит вошёл: предупреждение «Стоять! Назад!»");
        ck(!b.hostile(20), "в первые 5 с часовые ещё не стреляют");
    }

    /** Оплата открывает проход на 10 минут и не даёт события при повторном входе. */
    static void pay() {
        Gate.Pass p = new Gate.Pass(X0, Z0, X1, Z1);
        p.tick(0, 267, -280, false);
        ck(p.tick(20, 267, -297, false) == Gate.Event.TOLL, "вход без оплаты: цена");
        ck(!p.open(40), "до оплаты проход закрыт");
        p.pay(100);
        ck(p.open(101), "после оплаты проход открыт");
        ck(!p.open(100 + Gate.PASS_TICKS), "через 10 минут проход снова закрыт");
        // вышли и вошли снова с оплаченным проходом — цены не показывают
        p.tick(200, 267, -280, false);
        ck(p.tick(220, 267, -297, false) == Gate.Event.OPEN, "с оплатой вход открыт, цены нет");
        ck(p.violations() == 0, "оплаченный проход не считается нарушением");
    }

    /** Прошёл насквозь без оплаты — военные враждебны и репутация минус. */
    static void passWithoutPay() {
        Gate.Pass p = new Gate.Pass(X0, Z0, X1, Z1);
        p.tick(0, 267, -280, false);                       // юг
        ck(p.tick(20, 267, -297, false) == Gate.Event.TOLL, "вошёл с юга без оплаты");
        Gate.Event e = p.tick(60, 267, -315, false);       // вышел на север, насквозь
        ck(e == Gate.Event.VIOLATION, "вышел с другой стороны без оплаты: нарушение");
        ck(p.violations() == 1, "нарушение учтено");
        ck(p.hostile(61), "сразу после нарушения военные враждебны");
        ck(p.hostile(60 + Gate.HOSTILE_OUT_TICKS - 1), "враждебны всю минуту");
        ck(!p.hostile(60 + Gate.HOSTILE_OUT_TICKS), "через минуту враждебность спадает");
    }

    /** Вышел с той же стороны (не прошёл насквозь) — нарушения нет. */
    static void backOutIsFine() {
        Gate.Pass p = new Gate.Pass(X0, Z0, X1, Z1);
        p.tick(0, 267, -280, false);
        p.tick(20, 267, -297, false);
        p.tick(60, 267, -280, false);   // вернулся назад на юг
        ck(p.violations() == 0, "вернулся той же стороной — не нарушение");
        ck(!p.hostile(61), "часовые не враждебны");

        // а вот пройти запад→восток без оплаты — нарушение
        Gate.Pass q = new Gate.Pass(X0, Z0, X1, Z1);
        q.tick(0, 250, -297, false);
        q.tick(20, 267, -297, false);
        ck(q.tick(60, 290, -297, false) == Gate.Event.VIOLATION, "проход запад→восток без оплаты — нарушение");
    }

    /** Смена владельца: врагов владельца не пускают (кто враг — решает матрица вражды). */
    static void ownerChange() {
        Simulation s = KordonScenario.create(0);
        Faction mil = s.military();
        Faction ban = s.factions.get(ZoneScenario.BANDITS);
        Faction lon = s.factions.get(ZoneScenario.LONERS);

        ck(s.nodes.get(BRIDGE).owner == militaryId(s), "мост принадлежит военным");
        ck(Gate.enemyOf(s, mil, ban.id), "бандит — враг военных");
        ck(!Gate.enemyOf(s, mil, lon.id), "одиночка — не враг военных");

        // мост захватили бандиты: теперь для военных он закрыт, а для бандитов открыт
        s.nodes.get(BRIDGE).owner = ban.id;
        Faction newOwner = s.factions.get(s.nodes.get(BRIDGE).owner);
        ck(newOwner.id == ban.id, "новый владелец моста — бандиты");
        ck(Gate.enemyOf(s, newOwner, militaryId(s)), "враг нового владельца (военный) не проходит");
        ck(!Gate.enemyOf(s, newOwner, ban.id), "бандит у своего моста проходит");
    }

    static int militaryId(Simulation s) { return s.military().id; }

    /** Тайминг: бандит получает предупреждение, через 5 с часовые враждебны, после выхода — ещё 30 с. */
    static void banditTiming() {
        Gate.Pass p = new Gate.Pass(X0, Z0, X1, Z1);
        p.tick(0, 267, -280, true);
        p.tick(20, 267, -297, true);
        ck(!p.hostile(20 + Gate.WARN_TICKS - 1), "до 5 с ещё не стреляют");
        ck(p.hostile(20 + Gate.WARN_TICKS), "через 5 с часовые враждебны, пока он в регионе");
        long exit = 20 + Gate.WARN_TICKS + 200;
        p.tick(exit, 267, -315, true);   // вышел на север
        ck(p.hostile(exit + Gate.HOSTILE_IN_TICKS - 1), "после выхода враждебны ещё 30 с");
        ck(!p.hostile(exit + Gate.HOSTILE_IN_TICKS), "через 30 с после выхода остыли");
    }
}
