import ru.stalkerzone.sim.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * К2 (правило B HQ): счётчик смертей отражает фактические потери, а не расчётные.
 * <ul>
 *   <li>5 участников, погибло 2 → осталось 3, смертей 2;</li>
 *   <li>5 участников, погибло 5 → осталось 0, смертей 5, группировка уничтожена;</li>
 *   <li>5 участников, расчётные потери 7 → фактических смертей 5, численность 0;</li>
 *   <li>повторная обработка события не начисляет смерти повторно и не «воскрешает» фракцию;</li>
 *   <li>последний именной гибнет по-настоящему (People.kill), игровой NPC — тоже (npcKilled);</li>
 *   <li>раненые входят в численность и могут погибнуть все (treatWounded), wounded <= members;</li>
 *   <li>applyCasualties: раненые — часть потерь, deaths равен фактической убыли.</li>
 * </ul>
 * Детерминизм: приватный rng подменяется контролированным Random с подобранным сидом
 * (прецедент: AnomalyRadiusTest читает приватный rng; WarLedgerTest вызывает приватные методы).
 */
public class CombatDeathsTest {
    static int f = 0;
    static void ck(boolean ok, String m) { System.out.println((ok ? "OK   " : "FAIL ") + m); if (!ok) f++; }

    public static void main(String[] a) throws Exception {
        long t0 = System.currentTimeMillis();
        fiveLoseTwo();
        fiveLoseFive();
        calculatedSeven();
        casualtiesExact();
        noRepeatNoRevival();
        killLastNamed();
        npcKilledLast();
        treatWoundedLast();
        System.out.println("время: " + (System.currentTimeMillis() - t0) / 1000.0 + " с");
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    // ------------------------------------------------------------------ helpers

    static Simulation setup(long seed) {
        Simulation s = ZoneScenario.create(seed);
        s.step(5);
        return s;
    }

    static void setRng(Simulation s, long seed) throws Exception {
        Field rng = Simulation.class.getDeclaredField("rng");
        rng.setAccessible(true);
        rng.set(s, new Random(seed));
    }

    /** Первый сид, у которого nextInt(bound) == want (детерминированный подбор). */
    static long seedForInt(int bound, int want) {
        for (long seed = 0; seed < 1_000_000; seed++) if (new Random(seed).nextInt(bound) == want) return seed;
        throw new AssertionError("сид не найден: nextInt(" + bound + ") == " + want);
    }

    static long seedForDoubleBelow(double p) {
        for (long seed = 0; seed < 1_000_000; seed++) if (new Random(seed).nextDouble() < p) return seed;
        throw new AssertionError("сид не найден: nextDouble() < " + p);
    }

    static Method simMethod(String name, Class<?>... types) throws Exception {
        Method m = Simulation.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }

    /** Обнулить склад через Ledger (audit остаётся пустым) и убрать клиники (чтобы потери не лечились). */
    static void strip(Simulation s, Faction f) {
        for (Good g : Good.ALL) s.ledger.destroyed[g.ordinal()] += f.stock.take(g, f.stock.get(g));
        long m = f.stock.money;
        f.stock.money = 0;
        s.ledger.moneyBurned += m;
        s.buildings.removeIf(b -> b.type == BuildingType.CLINIC && !b.building());
    }

    static Caravan guardsCaravan(Simulation s, Faction owner, int guards) {
        Caravan c = new Caravan(owner.id, new int[]{owner.baseNode, s.traders().baseNode}, guards);
        s.caravans.add(c);
        return c;
    }

    // ------------------------------------------------------------------ 1. 5 − 2 = 3, смертей 2

    static void fiveLoseTwo() throws Exception {
        Simulation s = setup(41);
        Faction victim = s.factions.get(ZoneScenario.DUTY), raider = s.factions.get(ZoneScenario.BANDITS);
        victim.members = 5; victim.wounded = 0; victim.deaths = 0;
        Caravan c = guardsCaravan(s, victim, 8); // gd = nextInt(6)
        setRng(s, seedForInt(6, 2));
        simMethod("raidCargo", Caravan.class, Faction.class, Faction.class).invoke(s, c, victim, raider);
        ck(victim.members == 3, "5−2: осталось 3 (факт " + victim.members + ")");
        ck(victim.deaths == 2, "5−2: смертей 2 (факт " + victim.deaths + ")");
        ck(c.guards == 6, "5−2: охрана каравана уменьшилась на 2");
        ck(victim.wounded <= victim.members && !victim.isDestroyed(), "5−2: wounded <= members, фракция жива");
    }

    // ------------------------------------------------------------------ 2. 5 − 5 = 0, смертей 5

    static void fiveLoseFive() throws Exception {
        Simulation s = setup(42);
        Faction victim = s.factions.get(ZoneScenario.DUTY), raider = s.factions.get(ZoneScenario.BANDITS);
        victim.members = 5; victim.wounded = 0; victim.deaths = 0;
        Caravan c = guardsCaravan(s, victim, 8);
        setRng(s, seedForInt(6, 5));
        simMethod("raidCargo", Caravan.class, Faction.class, Faction.class).invoke(s, c, victim, raider);
        ck(victim.members == 0, "5−5: численность 0 (факт " + victim.members + ")");
        ck(victim.deaths == 5, "5−5: смертей 5, без бессмертного (факт " + victim.deaths + ")");
        ck(victim.isDestroyed(), "5−5: группировка уничтожена");
        ck(victim.wounded == 0, "5−5: раненых не осталось (входят в members)");
    }

    // ------------------------------------------------------------------ 3. расчётные 7 при 5 → фактических 5

    static void calculatedSeven() throws Exception {
        Simulation s = setup(43);
        Faction victim = s.factions.get(ZoneScenario.DUTY), raider = s.factions.get(ZoneScenario.BANDITS);
        victim.members = 5; victim.wounded = 0; victim.deaths = 0;
        Caravan c = guardsCaravan(s, victim, 12); // gd = nextInt(8)
        setRng(s, seedForInt(8, 7));
        simMethod("raidCargo", Caravan.class, Faction.class, Faction.class).invoke(s, c, victim, raider);
        ck(victim.members == 0, "7 при 5: численность 0 (факт " + victim.members + ")");
        ck(victim.deaths == 5, "7 при 5: фактических смертей 5, не 7 (факт " + victim.deaths + ")");
        ck(victim.isDestroyed(), "7 при 5: группировка уничтожена");
    }

    // ------------------------------------------------------------------ 4. applyCasualties: точный учёт

    static void casualtiesExact() throws Exception {
        Method cas = simMethod("applyCasualtiesOf", Faction.class, int.class);

        // Монолит (fanatic): раненых нет, losses = deaths; расчётные 7 при 5 → фактических 5
        Simulation s = setup(44);
        Faction mono = s.monolith();
        strip(s, mono);
        mono.members = 5; mono.wounded = 0; mono.deaths = 0;
        cas.invoke(s, mono, 7);
        ck(mono.members == 0 && mono.deaths == 5, "applyCasualties 7 при 5: members=0, deaths=5 (факт "
                + mono.members + "/" + mono.deaths + ")");

        // не-фанатик: 2 потери = 1 погибший + 1 раненый (40% в медпункт), deaths = фактическая убыль
        Simulation s2 = setup(45);
        Faction duty = s2.factions.get(ZoneScenario.DUTY);
        strip(s2, duty);
        duty.members = 5; duty.wounded = 0; duty.deaths = 0;
        cas.invoke(s2, duty, 2);
        ck(duty.members == 4 && duty.deaths == 1 && duty.wounded == 1,
                "applyCasualties 2: 1 погибший + 1 раненый, members=4 (факт " + duty.members + "/" + duty.deaths + "/" + duty.wounded + ")");
        ck(duty.wounded <= duty.members, "applyCasualties: wounded <= members");

        // отрицательные расчётные потери не воскрешают
        cas.invoke(s2, duty, -3);
        ck(duty.members == 4 && duty.deaths == 1, "applyCasualties −3: ничего не изменилось");
    }

    // ------------------------------------------------------------------ 5. повторная обработка уничтоженной

    static void noRepeatNoRevival() throws Exception {
        Simulation s = setup(46);
        Faction victim = s.factions.get(ZoneScenario.DUTY), raider = s.factions.get(ZoneScenario.BANDITS);
        victim.members = 5; victim.wounded = 0; victim.deaths = 0;
        Caravan c = guardsCaravan(s, victim, 8);
        setRng(s, seedForInt(6, 5));
        simMethod("raidCargo", Caravan.class, Faction.class, Faction.class).invoke(s, c, victim, raider);
        ck(victim.members == 0 && victim.deaths == 5, "повтор: фракция уничтожена (0/5)");

        int deaths0 = victim.deaths;
        simMethod("applyCasualtiesOf", Faction.class, int.class).invoke(s, victim, 5);
        ck(victim.members == 0 && victim.deaths == deaths0, "повтор: applyCasualties не воскрешает и не начисляет смерти");
        s.npcKilled(victim.id);
        ck(victim.members == 0 && victim.deaths == deaths0, "повтор: npcKilled не воскрешает и не начисляет смерти");
        Caravan c2 = guardsCaravan(s, victim, 8);
        setRng(s, seedForInt(6, 3));
        simMethod("raidCargo", Caravan.class, Faction.class, Faction.class).invoke(s, c2, victim, raider);
        ck(victim.members == 0 && victim.deaths == deaths0, "повтор: новый налёт на мёртвую фракцию — deaths не растут");
        ck(s.audit().isEmpty(), "повтор: audit пуст");
    }

    // ------------------------------------------------------------------ 6. последний именной

    static void killLastNamed() {
        Simulation s = setup(47);
        Faction loners = s.factions.get(ZoneScenario.LONERS);
        Person l = s.people.leader(loners.id);
        ck(l != null, "kill: у Одиночек есть лидер");
        loners.members = 1; loners.wounded = 0;
        int deaths0 = loners.deaths;
        ck(s.people.kill(s, l.id, "тестовая гибель"), "kill: именной найден и убит");
        ck(loners.members == 0, "kill: последний участник погиб (members=0, факт " + loners.members + ")");
        ck(loners.deaths == deaths0 + 1, "kill: смерть учтена (факт +" + (loners.deaths - deaths0) + ")");
        ck(loners.isDestroyed(), "kill: группировка уничтожена");
        s.step(1);
        ck(s.people.of(loners.id).isEmpty(), "kill: фиктивных именных не осталось после шага");
        ck(loners.deaths == deaths0 + 1, "kill: reconcileDeaths не начислил смерть повторно");
    }

    // ------------------------------------------------------------------ 7. последний игровой NPC

    static void npcKilledLast() {
        Simulation s = setup(48);
        Faction duty = s.factions.get(ZoneScenario.DUTY);
        duty.members = 1; duty.wounded = 0;
        int deaths0 = duty.deaths;
        s.npcKilled(duty.id);
        ck(duty.members == 0 && duty.deaths == deaths0 + 1, "npcKilled: последний NPC гибнет (0/+" + (duty.deaths - deaths0) + ")");
        ck(duty.isDestroyed(), "npcKilled: группировка уничтожена");
        s.npcKilled(duty.id);
        ck(duty.members == 0 && duty.deaths == deaths0 + 1, "npcKilled: повтор не начисляет смерть");
    }

    // ------------------------------------------------------------------ 8. раненые: все могут погибнуть

    static void treatWoundedLast() throws Exception {
        Simulation s = setup(49);
        Faction duty = s.factions.get(ZoneScenario.DUTY);
        strip(s, duty);
        duty.members = 1; duty.wounded = 1;
        int deaths0 = duty.deaths, wd0 = s.people.woundedDied;
        Method tw = People.class.getDeclaredMethod("treatWounded", Simulation.class, Random.class);
        tw.setAccessible(true);
        // без медикаментов шанс смерти раненого 0.06; подбираем сид, где он погибает
        tw.invoke(s.people, s, new Random(seedForDoubleBelow(0.06)));
        ck(duty.members == 0, "treatWounded: последний раненый погиб (members=0, факт " + duty.members + ")");
        ck(duty.deaths == deaths0 + 1, "treatWounded: смерть учтена");
        ck(s.people.woundedDied == wd0 + 1, "treatWounded: статистика woundedDied фактическая");
        ck(duty.wounded == 0 && duty.isDestroyed(), "treatWounded: раненых нет, группировка уничтожена");
    }
}