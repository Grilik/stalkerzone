import ru.stalkerzone.sim.*;
import java.util.HashSet;

/**
 * Э6-01-К2: теневой реестр населения (PersonId). Реестр ведётся параллельно счётчикам и ничего не решает.
 * Каждый день 365-дневного прогона kordon и zone: число живых записей группы = Group.members
 * (сумма по фракции = Faction.members); нет двух записей с одним PersonId; у каждой ровно одно состояние;
 * PersonId не переиспользуется; создание детерминированно от simSeed. К3 (книга населения) и К13
 * (наследование вещей) — вне объёма. Переключение счётчиков на реестр — отдельный PR.
 */
public class PersonRegistryTest {
  static int f = 0;
  static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

  public static void main(String[] a) {
    for (long seed : new long[] {1, 7, 42}) run365("kordon", seed);
    for (long seed : new long[] {1, 7, 42}) run365("zone", seed);
    identity();
    nonReuse();
    determinism();
    System.out.println(f == 0 ? "Все проверки пройдены" : "ПРОВАЛОВ: " + f);
    System.exit(f > 0 ? 1 : 0);
  }

  /** Каждый день: сверка реестра со счётчиками, уникальность PersonId, ровно одно состояние. */
  static void run365(String scenario, long seed) {
    Simulation s = Scenarios.create(scenario, seed);
    PersonRegistry reg = s.people.registry;
    boolean countsOk = true, uniqOk = true, stateOk = true;
    for (int d = 0; d < 365; d++) {
      s.step();
      String audit = reg.audit(s);
      if (!audit.isEmpty()) {
        countsOk = false;
        System.out.println("  " + scenario + " сид " + seed + " день " + s.day + ": " + audit);
        break;
      }
      HashSet<Long> ids = new HashSet<>();
      for (PersonRegistry.Record r : reg.records) {
        if (!ids.add(r.personId)) uniqOk = false;
        if (r.state == null) stateOk = false;
      }
    }
    ck(countsOk, scenario + " сид " + seed + ": живые записи группы = Group.members, сумма = Faction.members (365 дней)");
    ck(uniqOk, scenario + " сид " + seed + ": нет двух записей с одним PersonId");
    ck(stateOk, scenario + " сид " + seed + ": у каждой записи ровно одно состояние");
    // сумма по фракции явно
    boolean sumOk = true;
    for (Faction x : s.factions) if (reg.aliveOfFaction(x.id) != x.members) sumOk = false;
    ck(sumOk, scenario + " сид " + seed + ": сумма живых записей по фракции = Faction.members");
    ck(s.audit().isEmpty(), scenario + " сид " + seed + ": аудит симуляции сходится (реестр ничего не решает)");
  }

  /** Именные связаны с записями: лидер группы — запись с его именем и PersonId-ссылкой. */
  static void identity() {
    Simulation s = Scenarios.create("kordon", 5);
    s.step(10);
    PersonRegistry reg = s.people.registry;
    Faction lon = s.factions.get(ZoneScenario.LONERS);
    Person l = s.people.leader(lon.id);
    ck(l != null, "у одиночек есть лидер");
    PersonRegistry.Record r = reg.byPerson(l.id);
    ck(r != null && r.alive() && r.personRef == l.id && r.name.equals(l.name), "лидер — живая запись с его именем и ссылкой");
    ck(r != null && r.group != null && r.role == Person.Role.LEADER, "запись лидера состоит в группе и имеет роль");
    // гибель именного: запись умирает (хук People)
    int id = l.id;
    s.people.kill(s, id, "убит игроком");
    PersonRegistry.Record dead = reg.byPerson(id);
    ck(dead != null && dead.state == PersonRegistry.State.DEAD, "гибель именного: запись мертва (хук People.kill)");
    ck(s.audit().isEmpty(), "аудит после гибели лидера");
  }

  /** PersonId не переиспользуется: после гибели новая запись получает новый id. */
  static void nonReuse() {
    Simulation s = Scenarios.create("zone", 3);
    s.step(20);
    PersonRegistry reg = s.people.registry;
    long maxBefore = reg.issued();
    Person p = s.people.leader(ZoneScenario.DUTY);
    ck(p != null, "у Долга есть лидер");
    s.people.kill(s, p.id, "убит");
    long deadId = reg.byPerson(p.id).personId;
    s.step(30);
    ck(reg.issued() > maxBefore, "после гибели и пополнения выданы новые PersonId");
    boolean reused = false;
    HashSet<Long> aliveIds = new HashSet<>();
    for (PersonRegistry.Record r : reg.records) if (r.alive()) aliveIds.add(r.personId);
    ck(!aliveIds.contains(deadId), "PersonId погибшего не переиспользован живой записью");
    HashSet<Long> all = new HashSet<>();
    boolean dup = false;
    for (PersonRegistry.Record r : reg.records) if (!all.add(r.personId)) dup = true;
    ck(!dup, "все PersonId уникальны (включая мёртвые)");
  }

  /** Детерминизм от simSeed: один сид — одинаковые записи. */
  static void determinism() {
    Simulation a = Scenarios.create("kordon", 777);
    Simulation b = Scenarios.create("kordon", 777);
    a.step(40);
    b.step(40);
    PersonRegistry ra = a.people.registry, rb = b.people.registry;
    ck(ra.records.size() == rb.records.size() && ra.issued() == rb.issued(), "один сид — одинаковое число записей");
    boolean same = ra.records.size() == rb.records.size();
    for (int i = 0; same && i < ra.records.size(); i++) {
      PersonRegistry.Record x = ra.records.get(i), y = rb.records.get(i);
      same = x.personId == y.personId && x.name.equals(y.name) && x.state == y.state
          && java.util.Objects.equals(x.group, y.group);
    }
    ck(same, "один сид — одинаковые PersonId, имена, состояния и группы (детерминизм от simSeed)");
  }
}