import ru.stalkerzone.sim.*;
import java.util.Base64;
import java.util.Random;

/**
 * Э2-21: ведущие и зависимые группы Кордона. Шесть связей ровно по docs/thezone/11-group-links.md;
 * тип и доля — по идеологии ведущей силы (приказ — без доли, дань 30–50 %, договор 10–20 %);
 * напряжение растёт и падает по факторам docs/spec/faction-hierarchy.md §1; при пороге — событие
 * своего типа: договор — мирный уход, дань — мятеж, приказ — дезертирство поодиночке (группа не
 * откалывается), договор защиты — расторжение (хаб нейтрален). Доля и поддержка физичны: кассы групп,
 * существующие конвои, audit() сходится. 365 дней kordon: число групп не растёт, события ограничены.
 * Вне объёма: набег мутантов (Э2-22), нейтральная база деревни (Э2-23), раскол по гибели лидера
 * (Э7-02/Э7-08), звания 14 сил (Э7-10), PersonId (Э6-01).
 */
public class GroupLinksTest {
  static int f = 0;
  static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }
  static Group byId(Simulation s, String id) { for (Group g : s.groups) if (g.id.equals(id)) return g; return null; }
  static GroupLink link(Simulation s, String dependent) { return s.linkOf(dependent); }
  static boolean logged(Simulation s, String part) { for (String e : s.events) if (e.contains(part)) return true; return false; }
  static void mint(Simulation s, Stock st, long n) { st.money += n; s.ledger.moneyMinted += n; }

  static String withVersion(String data, int v) {
    byte[] b = Base64.getDecoder().decode(data);
    b[0] = (byte) (v >>> 24); b[1] = (byte) (v >>> 16); b[2] = (byte) (v >>> 8); b[3] = (byte) v;
    return Base64.getEncoder().encodeToString(b);
  }

  public static void main(String[] a) {
    composition();
    shares();
    order();
    tension();
    events();
    protection();
    persistence();
    longRun();
    System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
    System.exit(f > 0 ? 1 : 0);
  }

  /** Шесть связей Кордона: тип, доля по идеологии, поддержка, склонность к отколу, dependsOn. */
  static void composition() {
    for (String sc : new String[] {"kordon", "kordon_war"}) {
      Simulation s = Scenarios.create(sc, 5);
      ck(s.groupLinks.size() == 6, sc + ": шесть связей Кордона: " + s.groupLinks.size());
      GroupLink village = link(s, "free-stalkers");
      ck(village != null && village.kind == GroupLink.Kind.PROTECTION && village.leader.equals("loners-farm")
              && village.share == 0, sc + ": деревня ← Свиноферма — договор защиты, не подчинение, без доли");
      ck(village != null && village.supportProtection && !village.supportSupply, sc + ": деревня: поддержка — защита");
      GroupLink wagon = link(s, "loners-wagon"), hole = link(s, "loners-hole"), guides = link(s, "loners-guides");
      ck(wagon != null && wagon.kind == GroupLink.Kind.CONTRACT && wagon.leader.equals("loners-farm"), sc + ": вагончик ← Свиноферма — договор");
      ck(hole != null && hole.kind == GroupLink.Kind.CONTRACT && hole.leader.equals("loners-farm"), sc + ": дыра ← Свиноферма — договор");
      ck(guides != null && guides.kind == GroupLink.Kind.CONTRACT && guides.leader.equals("loners-farm"), sc + ": проводники ← Свиноферма — договор");
      boolean norms = true;
      for (GroupLink l : new GroupLink[] {wagon, hole, guides})
        if (l == null || l.share < 0.10 || l.share > 0.20) norms = false;
      ck(norms, sc + ": договор — доля 10–20 % по идеологии одиночек");
      ck(guides != null && wagon != null && guides.splitTendency > wagon.splitTendency && guides.splitTendency >= 0.6,
          sc + ": проводники — высокая склонность к отколу, вагончик — низкая");
      ck(wagon != null && wagon.supportPeople && wagon.supportSupply, sc + ": вагончик: поддержка — люди и снабжение");
      GroupLink bridge = link(s, "military-bridge");
      ck(bridge != null && bridge.kind == GroupLink.Kind.ORDER && bridge.leader.equals("military-south") && bridge.share == 0,
          sc + ": мост ← Южный блокпост — приказ, без доли");
      GroupLink far = link(s, "bandits-hook");
      ck(far != null && far.kind == GroupLink.Kind.TRIBUTE && far.leader.equals("bandits-atp")
              && far.share >= 0.30 && far.share <= 0.50, sc + ": Дальний блокпост ← АТП — дань 30–50 %");
      ck(far != null && far.splitTendency >= 0.6, sc + ": Дальний блокпост — высокая склонность к отколу");
      Group wagonG = byId(s, "loners-wagon"), bridgeG = byId(s, "military-bridge"), hookG = byId(s, "bandits-hook"), freeG = byId(s, "free-stalkers");
      ck(wagonG != null && "loners-farm".equals(wagonG.dependsOn), sc + ": Group.dependsOn — ведущая группа");
      ck(bridgeG != null && "military-south".equals(bridgeG.dependsOn) && bridgeG.faction == ZoneScenario.MILITARY
              && bridgeG.baseNode == 8, sc + ": гарнизон моста — группа военных, зависит от Южного блокпоста");
      ck(hookG != null && "bandits-atp".equals(hookG.dependsOn), sc + ": банда Крюка зависит от банды Борова");
      ck(freeG != null && "loners-farm".equals(freeG.dependsOn) && freeG.isFree(), sc + ": хаб зависит от фермы по договору защиты, оставаясь вольным");
      ck(byId(s, "loners-farm").dependsOn == null && byId(s, "bandits-atp").dependsOn == null, sc + ": ведущие группы независимы");
      Faction mil = s.factions.get(ZoneScenario.MILITARY);
      ck(mil.groups.size() == 2 && mil.groupMembers() == mil.members, sc + ": военные — две группы, сумма членов равна фракции");
    }
    Simulation z = Scenarios.create("zone", 3);
    ck(z.groupLinks.isEmpty(), "Зона: связей нет (стандарт Э2-21 — Кордон, вся Зона — Э7-10)");
  }

  /** Доля физична: кассы групп, конвои по существующим маршрутам, audit сходится, сумма сохраняется. */
  static void shares() {
    Simulation s = Scenarios.create("kordon_war", 5);
    Group wagon = byId(s, "loners-wagon"), farm = byId(s, "loners-farm");
    GroupLink l = link(s, "loners-wagon");
    mint(s, wagon.stock, 500);
    mint(s, farm.stock, 1000);
    long total0 = s.totalMoney();
    l.settle(s, new Random(1));
    ck(s.audit().isEmpty(), "доля: аудит сходится после расчёта");
    ck(s.totalMoney() == total0 + l.incomeRate, "доля: сумма денег изменилась только на приход услуги (mint), передача сохраняется");
    long due = Math.round(l.incomeRate * l.share);
    ck(wagon.stock.money == 500 + l.incomeRate - due, "доля ушла из кассы зависимой ровно на долю");
    boolean convoy = false;
    for (Convoy c : s.logistics.convoys) if ("loners-farm".equals(c.forGroup) && c.cargo.money == due) convoy = true;
    ck(convoy, "доля едет конвоем с адресатом-группой (существующий маршрут, без новых рёбер)");
    ck(s.graph.count() == 24, "новых маршрутов/узлов не введено");
    // изолируем связь: остальные снимаем, фракции неподвижны — касса фермы меняется только этой связью
    s.groupLinks.removeIf(x -> x != l);
    for (Faction x : s.factions) x.garrisonOnly = true;
    s.step(15);
    ck(s.logistics.convoysArrived > 0, "конвой доли дошёл");
    ck(farm.stock.money > 900, "доля дошла до кассы ведущей (снабжение не съело кассу): " + farm.stock.money);
    ck(s.audit().isEmpty(), "доля: аудит после доставки");

    // курьером — у неподвижного гарнизона (Малый Кордон: отряды запрещены, карточки 054/057)
    Simulation k = Scenarios.create("kordon", 5);
    k.factions.get(ZoneScenario.LONERS).garrisonOnly = true;
    Group kw = byId(k, "loners-wagon"), kf = byId(k, "loners-farm");
    GroupLink kl = link(k, "loners-wagon");
    mint(k, kw.stock, 500);
    int convoys0 = k.logistics.convoys.size();
    kl.settle(k, new Random(1));
    long dueK = Math.round(kl.incomeRate * kl.share);
    ck(kf.stock.money == dueK, "курьер: доля передана напрямую в кассу ведущей");
    ck(k.logistics.convoys.size() == convoys0, "курьер: конвоев неподвижного гарнизона нет");
    ck(k.audit().isEmpty(), "курьер: аудит сходится");

    // дань: половина пошлины с перехода на Свалку уходит АТП
    Simulation w = Scenarios.create("kordon_war", 5);
    Group hook = byId(w, "bandits-hook"), atp = byId(w, "bandits-atp");
    GroupLink tl = link(w, "bandits-hook");
    ck(tl.share == 0.5, "дань Дальнего блокпоста — половина пошлины");
    tl.settle(w, new Random(1));
    ck(hook.stock.money == tl.incomeRate - Math.round(tl.incomeRate * tl.share), "дань ушла из кассы зависимой");
    w.step(15);
    ck(atp.stock.money > 0, "дань дошла до АТП");
    ck(w.audit().isEmpty(), "дань: аудит сходится");

    // длительный прогон: доля платится, audit ежедневно
    Simulation p = Scenarios.create("kordon_war", 3);
    boolean audit = true;
    for (int d = 0; d < 60; d++) { p.step(); if (!p.audit().isEmpty()) { audit = false; System.out.println(p.audit()); } }
    ck(audit, "60 дней kordon_war: аудит сходится ежедневно");
    long paid = 0;
    for (GroupLink x : p.groupLinks) paid += x.sharePaid;
    ck(paid > 0, "за 60 дней доля передана ведущим: " + paid + " руб.");
    ck(p.ledger.moneyMinted > 0, "приход услуг учтён в Ledger (moneyMinted)");
  }

  /** Приказ: снабжение сверху из склада ведущей, добыча гарнизона сдаётся на склад; пошлина моста не тронута. */
  static void order() {
    Simulation s = Scenarios.create("kordon_war", 1);
    Faction lon = s.factions.get(ZoneScenario.LONERS), mil = s.military();
    Group south = byId(s, "military-south"), bridge = byId(s, "military-bridge");
    GroupLink l = link(s, "military-bridge");
    long mil0 = mil.stock.money;
    s.crossPass(lon, 8, lon.stock);
    long toll = s.tollCollected;
    ck(toll > 0 && mil.stock.money == mil0 + toll, "пошлина моста приходит военным как прежде (crossPass не тронут)");
    long total0 = s.totalMoney();
    l.settle(s, new Random(1));
    ck(bridge.stock.money > 0, "приказ: снабжение сверху пришло гарнизону моста");
    ck(mil.stock.money == mil0 + toll - bridge.stock.money, "приказ: снабжение со склада ведущей, пошлина осталась на складе");
    ck(s.totalMoney() == total0, "приказ: сумма денег сохранена (перенос, не создание)");
    ck(s.audit().isEmpty(), "приказ: аудит сходится");
    long b = bridge.stock.money;
    l.settle(s, new Random(1));
    ck(l.sharePaid == b, "приказ: добыча гарнизона сдана на склад ведущей (вся, без доли)");
    ck(l.share == 0, "приказ: доли нет");
    ck(mil.stock.money == mil0 + toll - bridge.stock.money, "приказ: склад ведущей пополнен сданной добычей");
    ck(s.audit().isEmpty(), "приказ: аудит после сдачи добычи");
  }

  /** Напряжение: рост от доли выше нормы, слабости ведущей, агрессии и амбиций; спад от помощи, побед, повышений, угрозы. */
  static void tension() {
    Simulation s = Scenarios.create("kordon_war", 5);
    Random r = new Random(1);
    GroupLink l = link(s, "loners-wagon");
    Group farm = byId(s, "loners-farm");
    Group wagon = byId(s, "loners-wagon");

    l.tension = 0.2; l.share = 0.5;
    l.daily(s, r);
    ck(l.tension > 0.2, "напряжение растёт: доля выше нормы идеологии");
    l.share = 0.10;

    l.tension = 0.2; farm.members = 2;
    l.daily(s, r);
    ck(l.tension > 0.2, "напряжение растёт: ведущая ослабла (людей меньше, чем у зависимой)");
    farm.members = 22;

    l.tension = 0.2;
    wagon.aggression = 1.0; wagon.leaderInfluence = 1.0; l.splitTendency = 1.0;
    l.daily(s, r);
    ck(l.tension > 0.2, "напряжение растёт: агрессивность и амбиция командира зависимой");
    wagon.aggression = 0.25; wagon.leaderInfluence = 0.4; l.splitTendency = 0.2;

    l.tension = 0.5; mint(s, farm.stock, 1000);
    l.settle(s, r);
    ck(l.tension < 0.5, "напряжение падает: помощь пришла");

    l.tension = 0.5;
    s.lairs.add(new Lair(19, LairKind.values()[0], 20, false));
    l.daily(s, r);
    ck(l.tension < 0.5, "напряжение падает: общая угроза (логово у точки зависимой)");
    s.lairs.clear();

    l.tension = 0.5; mint(s, farm.stock, 1000);
    s.factions.get(ZoneScenario.LONERS).nodesCaptured++;
    l.settle(s, r);
    ck(l.tension < 0.5, "напряжение падает: победы ведущей");

    l.tension = 0.5; mint(s, farm.stock, 1000);
    if (wagon.leader != null) { wagon.leader.xp = 500; l.lastLeaderRank = 0; }
    l.settle(s, r);
    ck(l.tension < 0.5, "напряжение падает: повышение командира зависимой");

    l.tension = 0.1; farm.stock.money = 0;
    l.settle(s, r);
    ck(l.tension > 0.1, "напряжение растёт: помощь не пришла");

    for (int i = 0; i < 5; i++) l.daily(s, r);
    ck(l.tension >= 0 && l.tension <= 1, "напряжение в диапазоне 0..1");
  }

  /** Пороговые события по типу связи: уход, мятеж, дезертирство; кулдаун; группа приказа не откалывается. */
  static void events() {
    // договор — мирный уход
    Simulation s = Scenarios.create("kordon_war", 3);
    Group wagon = byId(s, "loners-wagon");
    int wm = wagon.members;
    link(s, "loners-wagon").tension = 0.9;
    s.step();
    ck(link(s, "loners-wagon") == null && wagon.dependsOn == null, "договор: при пороге мирный уход — связь снята, зависимая самостоятельна");
    ck(wagon.members >= wm && wagon.faction == ZoneScenario.LONERS, "договор: уход мирный — люди и фракция целы");
    ck(logged(s, "УХОД"), "договор: событие в журнале");
    ck(s.audit().isEmpty(), "договор: аудит после ухода");

    // дань — мятеж: зависимая слабее — нападение и разрыв
    Simulation w = Scenarios.create("kordon_war", 3);
    Group hook = byId(w, "bandits-hook"), atp = byId(w, "bandits-atp");
    link(w, "bandits-hook").tension = 0.9;
    w.step();
    ck(link(w, "bandits-hook") == null, "дань: мятеж — связь разорвана");
    ck(hook.members < 12 && atp.members < 16, "дань: мятеж — потери в перестрелке с обеих сторон");
    ck(logged(w, "МЯТЕЖ"), "дань: мятеж в журнале");
    ck(w.audit().isEmpty(), "дань: аудит после мятежа");

    // дань — мятеж: зависимая сильнее — захват ведущей роли
    Simulation v = Scenarios.create("kordon_war", 3);
    Group hook2 = byId(v, "bandits-hook"), atp2 = byId(v, "bandits-atp");
    hook2.members = 30; atp2.members = 10;
    v.factions.get(ZoneScenario.BANDITS).members = 40;
    link(v, "bandits-hook").tension = 0.9;
    v.step();
    GroupLink flipped = link(v, "bandits-atp");
    ck(flipped != null && flipped.leader.equals("bandits-hook"), "дань: сильная зависимая забрала ведущую роль");
    ck(atp2.dependsOn != null && atp2.dependsOn.equals("bandits-hook") && hook2.dependsOn == null, "дань: dependsOn перевёрнут");
    ck(logged(v, "МЯТЕЖ"), "дань: переворот в журнале");
    ck(v.audit().isEmpty(), "дань: аудит после переворота");

    // приказ — дезертирство поодиночке, группа не откалывается
    Simulation o = Scenarios.create("kordon_war", 3);
    Group bridge = byId(o, "military-bridge");
    Faction mil = o.military();
    int b0 = bridge.members;
    // 0.96: в день недельного расчёта снабжение приказа приходит и снижает напряжение на 0.06
    link(o, "military-bridge").tension = 0.96;
    o.step();
    ck(bridge.members < b0, "приказ: при пороге дезертирство");
    ck(mil.groupMembers() == mil.members, "приказ: численность фракции согласована с группами");
    ck(link(o, "military-bridge") != null && "military-south".equals(bridge.dependsOn)
            && bridge.faction == ZoneScenario.MILITARY, "приказ: группа не откалывается, связь сохранена");
    ck(logged(o, "ДЕЗЕРТИРСТВО"), "приказ: дезертирство в журнале");
    ck(o.audit().isEmpty(), "приказ: аудит после дезертирства");
    // кулдаун: повторного события сразу нет (счётчик событий и день события не меняются;
    // численность может меняться от боевых потерь и перераспределения — это не событие связи)
    GroupLink ol = link(o, "military-bridge");
    int ev1 = ol.events, day1 = ol.lastEventDay;
    ol.tension = 0.96;
    o.step();
    ck(ol.events == ev1 && ol.lastEventDay == day1, "приказ: кулдаун — повторное дезертирство не сразу");
  }

  /** Договор защиты деревни: хаб нейтрален, при пороге договор расторгается, люди целы. */
  static void protection() {
    Simulation s = Scenarios.create("kordon", 5);
    Person p = new Person(900);
    p.name = "Тестовый вольный"; p.faction = Person.NONE; p.cls = Person.Cls.ASSAULT;
    s.people.roster.add(p);
    s.step();
    Group free = byId(s, "free-stalkers");
    ck(free.members == 1, "вольные сталкеры: пул наполнен, группа жива");
    GroupLink l = link(s, "free-stalkers");
    ck(l != null && l.kind == GroupLink.Kind.PROTECTION, "договор защиты на месте");
    l.tension = 0.9;
    s.step();
    ck(link(s, "free-stalkers") == null && free.dependsOn == null, "защита: при пороге договор расторгнут");
    ck(free.isFree() && free.members == 1, "защита: хаб нейтрален, люди целы, подчинения нет");
    ck(logged(s, "ЗАЩИТА"), "защита: расторжение в журнале");
    ck(s.audit().isEmpty(), "защита: аудит сходится");
  }

  /** Сохранение v27: секция связей, круг идентичен, поведение после загрузки то же; старые версии. */
  static void persistence() {
    Simulation s = Scenarios.create("kordon_war", 7);
    s.step(30);
    String data = s.save();
    Simulation l = Simulation.load(data);
    ck(l.save().equals(data), "круг сохранение -> загрузка -> сохранение идентичен (секция связей v27)");
    ck(l.groupLinks.size() == s.groupLinks.size(), "связи восстановлены");
    boolean same = true;
    for (GroupLink x : s.groupLinks) {
      GroupLink y = l.linkOf(x.dependent);
      if (y == null || y.kind != x.kind || y.share != x.share || y.tension != x.tension
              || y.sharePaid != x.sharePaid || !y.leader.equals(x.leader) || y.events != x.events) same = false;
    }
    ck(same, "параметры связей восстановлены: тип, доля, напряжение, переданная доля, события");
    ck(byId(l, "loners-wagon").dependsOn.equals("loners-farm"), "dependsOn восстановлен из связей");
    s.step(30); l.step(30);
    ck(s.save().equals(l.save()), "после загрузки симуляция идёт так же (30 дней)");

    Simulation z = Scenarios.create("zone", 3);
    z.step(12);
    Simulation zl = Simulation.load(withVersion(z.save(), 26));
    ck(zl.resetFromVersion == -1 && zl.day == 12 && zl.groupLinks.isEmpty(), "Зона v26 (без секции связей): загрузка без сброса");
    Simulation k = Scenarios.create("kordon", 9);
    k.step(8);
    Simulation kl = Simulation.load(withVersion(k.save(), 26));
    ck(kl.resetFromVersion == 26 && kl.day == 0 && kl.seed == 9 && kl.groupLinks.size() == 6,
        "Кордон v26: пересоздан с тем же сидом, связи из сценария");
  }

  /** 365 дней kordon: число групп не взрывается, мятежи не бесконечны, снежного кома нет, audit ежедневно. */
  static void longRun() {
    Simulation s = Scenarios.create("kordon", 5);
    int groups0 = s.groups.size();
    int lon0 = s.factions.get(ZoneScenario.LONERS).members;
    int ban0 = s.factions.get(ZoneScenario.BANDITS).members;
    int mil0 = s.factions.get(ZoneScenario.MILITARY).members;
    boolean audit = true, sums = true, count = true;
    for (int d = 0; d < 365; d++) {
      s.step();
      if (!s.audit().isEmpty()) { audit = false; System.out.println("день " + s.day + ": " + s.audit()); }
      for (Faction x : s.factions) if (x.groupMembers() != x.members) sums = false;
      if (s.groups.size() != groups0) count = false;
    }
    ck(audit, "kordon 365: аудит сходится ежедневно");
    ck(sums, "kordon 365: сумма членов групп равна фракции каждый день");
    ck(count, "kordon 365: число групп не взрывается (" + groups0 + ")");
    int events = 0;
    for (GroupLink x : s.groupLinks) events += x.events;
    ck(events <= 12, "kordon 365: события связей ограничены: " + events);
    double lonR = (double) s.factions.get(ZoneScenario.LONERS).members / lon0;
    double banR = (double) s.factions.get(ZoneScenario.BANDITS).members / ban0;
    double milR = (double) s.factions.get(ZoneScenario.MILITARY).members / mil0;
    ck(lonR >= 0.25 && lonR <= 4 && banR >= 0.25 && banR <= 4 && milR >= 0.25 && milR <= 4,
        "kordon 365: снежного кома нет (Л/Б/В " + String.format("%.2f/%.2f/%.2f", lonR, banR, milR) + ")");

    Simulation w = Scenarios.create("kordon_war", 5);
    int wg0 = w.groups.size();
    boolean wa = true, ws = true, wc = true;
    for (int d = 0; d < 120; d++) {
      w.step();
      if (!w.audit().isEmpty()) { wa = false; System.out.println("war день " + w.day + ": " + w.audit()); }
      for (Faction x : w.factions) if (x.groupMembers() != x.members) ws = false;
      if (w.groups.size() != wg0) wc = false;
    }
    ck(wa && ws && wc, "kordon_war 120: аудит, суммы и число групп каждый день");
    int we = 0;
    for (GroupLink x : w.groupLinks) we += x.events;
    ck(we <= 12, "kordon_war 120: события связей ограничены: " + we);
  }
}