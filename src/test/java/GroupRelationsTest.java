import ru.stalkerzone.sim.*;
import java.util.Base64;

/**
 * Э2-17 (К10): группы Кордона и их отношения. Две группы одной фракции по-разному относятся к третьей силе;
 * Faction.members — сумма по группам каждый день; спор снижает unity; переход человека между группами — событие;
 * сохранение без секции групп загружается (группы из сценария). Раскол после гибели лидера — Э7-02,
 * единый реестр людей (PersonId) — Э6-01: вне объёма этой карточки.
 */
public class GroupRelationsTest {
  static int f = 0;
  static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }
  static Group byId(Simulation s, String id) { for (Group g : s.groups) if (g.id.equals(id)) return g; return null; }

  static String withVersion(String data, int v) {
    byte[] b = Base64.getDecoder().decode(data);
    b[0] = (byte) (v >>> 24); b[1] = (byte) (v >>> 16); b[2] = (byte) (v >>> 8); b[3] = (byte) v;
    return Base64.getEncoder().encodeToString(b);
  }

  public static void main(String[] a) {
    composition();
    relations();
    sums();
    dispute();
    transfer();
    persistence();
    System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
    System.exit(f > 0 ? 1 : 0);
  }

  static void composition() {
    Simulation s = Scenarios.create("kordon", 5);
    Group free = byId(s, "free-stalkers");
    ck(free != null && free.faction == Person.NONE && free.baseNode == 0, "вольные сталкеры: без группировки, хаб — деревня новичков");
    ck(free != null && free.nodes.isEmpty() && free.isFree(), "вольные сталкеры не владеют узлами");
    Faction lon = s.factions.get(ZoneScenario.LONERS);
    ck(lon.groups.size() == 4, "одиночки: 4 группы (ферма, вагончик, дыра, проводники): " + lon.groups.size());
    Group farm = byId(s, "loners-farm");
    ck(farm != null && farm.baseNode == KordonScenario.FARM && lon.groups.get(0) == farm, "основная база одиночек — Свиноферма");
    Group wagon = byId(s, "loners-wagon"), hole = byId(s, "loners-hole"), guides = byId(s, "loners-guides");
    ck(wagon != null && wagon.baseNode == KordonScenario.WAGON, "зависимая группа: лагерь у вагончика");
    ck(hole != null && hole.baseNode == 9, "зависимая группа: дыра в заборе");
    ck(guides != null && guides.baseNode == 0, "проводники: деревня новичков");
    Faction ban = s.factions.get(ZoneScenario.BANDITS);
    ck(ban.groups.size() >= 2, "бандиты: конкурирующие банды: " + ban.groups.size());
    Group atp = byId(s, "bandits-atp"), hook = byId(s, "bandits-hook");
    ck(atp != null && atp.baseNode == 5 && hook != null && hook.baseNode == 11, "банды: АТП и Дальний блокпост");
    boolean owned = true;
    for (Node n : s.nodes) {
      Group g = s.groupAt(n.id);
      if (n.owner < 0) { if (g != null) owned = false; continue; }
      if (g == null || g.faction != n.owner) owned = false;
    }
    ck(owned, "узлом владеет группа; владелец-фракция выводится (Node.owner = фракция группы)");
    ck(farm != null && farm.aggression >= 0 && farm.aggression <= 1 && farm.expansion >= 0 && farm.discipline > 0
            && farm.cohesion > 0 && farm.caution >= 0 && farm.morale > 0 && farm.leaderInfluence > 0,
        "параметры группы: агрессия, тяга к расширению, дисциплина, сплочённость, осторожность, мораль, влияние лидера");
    ck(farm != null && farm.stock != null, "у группы есть склад и касса");
    Simulation z = Scenarios.create("zone", 3);
    boolean one = z.groups.size() == z.factions.size();
    for (Faction x : z.factions) if (x.groups.size() != 1 || x.groups.get(0).members != x.members) one = false;
    ck(one, "Зона: одна группа на фракцию, поведение не меняется");
  }

  static void relations() {
    Simulation s = Scenarios.create("kordon", 5);
    GroupRelations r = s.groupRelations;
    Group atp = byId(s, "bandits-atp"), hook = byId(s, "bandits-hook");
    double ha = r.hostility(s, atp, ZoneScenario.LONERS), hh = r.hostility(s, hook, ZoneScenario.LONERS);
    ck(ha > hh, "две группы одной фракции по-разному относятся к третьей силе: " + ha + " > " + hh);
    double base = s.hostilityTo(s.factions.get(ZoneScenario.BANDITS), ZoneScenario.LONERS);
    ck(Math.abs(ha - Math.min(1, base + r.deviation(atp.id, ZoneScenario.LONERS))) < 1e-9,
        "отношение группы = отношение фракции + отклонение");
    ck(r.deviation("нет-такой-группы", ZoneScenario.LONERS) == 0, "разреженная таблица: нет записи — нет отклонения");
    int before = r.size();
    r.set("loners-farm", ZoneScenario.MILITARY, 0.2);
    ck(r.size() == before + 1 && r.deviation("loners-farm", ZoneScenario.MILITARY) == 0.2, "запись отклонения");
    r.set("loners-farm", ZoneScenario.MILITARY, 0);
    ck(r.size() == before && r.deviation("loners-farm", ZoneScenario.MILITARY) == 0, "нулевое отклонение убирает запись");
    Group farm = byId(s, "loners-farm");
    r.set(farm.id, ZoneScenario.BANDITS, 5);
    ck(r.hostility(s, farm, ZoneScenario.BANDITS) <= 1.0, "враждебность не выше 1");
    r.set(farm.id, ZoneScenario.BANDITS, -5);
    ck(r.hostility(s, farm, ZoneScenario.BANDITS) >= 0.0, "враждебность не ниже 0");
    ck(r.hostility(s, atp, hook) == r.hostility(s, atp, ZoneScenario.BANDITS), "отношение к группе — через её фракцию");
    Group free = byId(s, "free-stalkers");
    ck(r.hostility(s, free, ZoneScenario.BANDITS) == 0, "вольные сталкеры нейтральны без отклонений");
  }

  static void sums() {
    for (String sc : new String[] {"kordon", "kordon_war"}) {
      Simulation s = Scenarios.create(sc, 3);
      int days = sc.equals("kordon") ? 365 : 120;
      boolean sumOk = true, ownOk = true, auditOk = true;
      for (int d = 0; d < days; d++) {
        s.step();
        for (Faction x : s.factions) {
          if (x.groupMembers() != x.members) {
            sumOk = false;
            System.out.println("  " + sc + " день " + s.day + ": «" + x.name + "» members=" + x.members + " сумма групп=" + x.groupMembers());
          }
        }
        for (Node n : s.nodes) {
          Group g = s.groupAt(n.id);
          if (n.owner >= 0 && (g == null || g.faction != n.owner)) ownOk = false;
          if (n.owner < 0 && g != null) ownOk = false;
        }
        if (!s.audit().isEmpty()) { auditOk = false; System.out.println(s.audit()); }
      }
      ck(sumOk, sc + ": Faction.members = сумма по группам каждый день " + days + " дней");
      ck(ownOk, sc + ": владение узлами согласовано с группами все дни");
      ck(auditOk, sc + ": аудит сходится все дни");
      Group free = byId(s, "free-stalkers");
      if (free != null) ck(free.members == s.people.freeStalkers().size(), sc + ": вольные сталкеры = пул People.freeStalkers()");
    }
  }

  static void dispute() {
    Simulation s = Scenarios.create("kordon", 5);
    s.step(5);
    Faction lon = s.factions.get(ZoneScenario.LONERS);
    Group a = lon.groups.get(0), b = lon.groups.get(1);
    double u0 = lon.unity;
    ck(s.politics.dispute(s, a, b), "спор групп одной фракции засчитан");
    ck(lon.unity < u0, "спор снижает unity: " + u0 + " -> " + lon.unity);
    ck(!s.politics.dispute(s, a, a), "спор группы с собой — нет");
    ck(!s.politics.dispute(s, a, byId(s, "bandits-atp")), "спор групп разных фракций — не внутренний спор");
  }

  static void transfer() {
    Simulation s = Scenarios.create("kordon", 5);
    s.step(10);
    Faction lon = s.factions.get(ZoneScenario.LONERS), ban = s.factions.get(ZoneScenario.BANDITS);
    Group farm = byId(s, "loners-farm"), atp = byId(s, "bandits-atp"), wagon = byId(s, "loners-wagon");
    Person p = null;
    for (Person x : s.people.of(ZoneScenario.LONERS)) if (x.role != Person.Role.LEADER && x.role != Person.Role.DEPUTY) { p = x; break; }
    ck(p != null, "есть именной одиночка для перехода");
    int lm = lon.members, bm = ban.members, fm = farm.members, am = atp.members;
    ck(s.transferPerson(p, farm, atp), "переход человека между группами — событие");
    ck(p.faction == ZoneScenario.BANDITS, "человек сменил фракцию вместе с группой");
    ck(farm.members == fm - 1 && atp.members == am + 1, "численность групп изменилась на 1");
    ck(lon.members == lm - 1 && ban.members == bm + 1, "численность фракций следует за переходом");
    ck(!s.transferPerson(p, farm, atp), "повторный переход из чужой группы — нет");
    Person q = null;
    for (Person x : s.people.of(ZoneScenario.LONERS)) if (x.role != Person.Role.LEADER) { q = x; break; }
    int lm2 = lon.members, fm2 = farm.members, wm = wagon.members;
    ck(q != null && s.transferPerson(q, farm, wagon), "переход внутри фракции");
    ck(lon.members == lm2 && farm.members == fm2 - 1 && wagon.members == wm + 1, "внутренний переход не меняет сумму фракции");
    s.step();
    boolean ok = true;
    for (Faction x : s.factions) if (x.groupMembers() != x.members) ok = false;
    ck(ok, "после перехода и шага дня сумма групп = members фракций");
    ck(s.audit().isEmpty(), "аудит после переходов");
  }

  static void persistence() {
    Simulation s = Scenarios.create("kordon", 7);
    s.step(20);
    String data = s.save();
    Simulation l = Simulation.load(data);
    ck(l.save().equals(data), "круг сохранение -> загрузка -> сохранение идентичен (секция групп v26)");
    boolean same = l.groups.size() == s.groups.size();
    for (int i = 0; same && i < s.groups.size(); i++) {
      Group g = s.groups.get(i), gl = l.groups.get(i);
      same = g.id.equals(gl.id) && g.members == gl.members && g.faction == gl.faction && g.baseNode == gl.baseNode
          && g.nodes.equals(gl.nodes) && g.stock.money == gl.stock.money
          && (g.leader == null ? gl.leader == null : gl.leader != null && g.leader.id == gl.leader.id);
    }
    ck(same, "группы восстановлены: id, члены, фракция, база, узлы, лидер, касса");
    ck(l.groupRelations.deviation("bandits-atp", ZoneScenario.LONERS) == s.groupRelations.deviation("bandits-atp", ZoneScenario.LONERS)
        && l.groupRelations.size() == s.groupRelations.size(), "отклонения отношений сохранены");
    s.step(30);
    l.step(30);
    ck(s.save().equals(l.save()), "после загрузки симуляция идёт так же (30 дней)");

    // сохранение без секции групп (v25): Зона грузится без сброса, группы — из сценария
    Simulation z = Scenarios.create("zone", 3);
    z.step(12);
    Simulation zl = Simulation.load(withVersion(z.save(), 25));
    ck(zl.resetFromVersion == -1 && zl.day == 12, "Зона v25 (без секции групп): загрузка без сброса, день 12");
    ck(zl.groups.size() == zl.factions.size(), "Зона v25: группы из сценария (одна на фракцию)");
    boolean zsum = true;
    for (Faction x : zl.factions) if (x.groupMembers() != x.members) zsum = false;
    ck(zsum, "Зона v25: члены групп согласованы с загруженной численностью фракций");
    // Кордон v25 — пересоздание с тем же сидом (политика версий Кордона), состав групп из сценария
    Simulation k = Scenarios.create("kordon", 9);
    k.step(8);
    Simulation kl = Simulation.load(withVersion(k.save(), 25));
    ck(kl.resetFromVersion == 25 && kl.day == 0 && kl.seed == 9, "Кордон v25: пересоздан с тем же сидом");
    ck(byId(kl, "loners-farm") != null && byId(kl, "bandits-hook") != null && byId(kl, "free-stalkers") != null,
        "Кордон v25: состав групп из сценария");
  }
}