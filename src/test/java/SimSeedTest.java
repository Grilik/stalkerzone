import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import ru.stalkerzone.sim.Scenarios;
import ru.stalkerzone.sim.SimSeed;
import ru.stalkerzone.sim.Simulation;

/**
 * Э1-11В: отдельный simSeed каждого мира. Случайный (не от MC-seed), уникальный,
 * сохраняется и восстанавливается, стабилен при перезагрузке; разные сиды — разные истории,
 * один сид — детерминизм; старые сохранения без simSeed историю сохраняют.
 * Обвязка ZoneData (NBT, отказ от overworld().getSeed()) проверяется по исходнику — как TheZoneNpcTest.
 */
public class SimSeedTest {
    static int f = 0;
    static void ck(boolean c, String m) { System.out.println((c ? "OK   " : "FAIL ") + m); if (!c) f++; }
    static String read(String p) throws Exception { return Files.readString(Path.of(p)).replace("\r\n", "\n"); }

    static Simulation days(long seed, int n) {
        Simulation s = Scenarios.create(seed);
        for (int i = 0; i < n; i++) s.step();
        return s;
    }

    public static void main(String[] a) throws Exception {
        String old = System.getProperty("stalkerzone.scenario");
        System.setProperty("stalkerzone.scenario", "kordon");
        try {
            // уникальность: два мира не получают одну историю случайно
            Set<Long> seeds = new HashSet<>();
            for (int i = 0; i < 10_000; i++) seeds.add(SimSeed.newSeed());
            ck(seeds.size() == 10_000, "10000 новых simSeed: все различны");

            // симуляция использует сид как основу случайных событий
            long s1 = SimSeed.newSeed();
            Simulation sim = Scenarios.create(s1);
            ck(sim.seed == s1, "Scenarios.create(simSeed): симуляция получает именно simSeed");

            // сохранение/загрузка: сид восстанавливается и не меняется
            String save = sim.save();
            Simulation loaded = Simulation.load(save);
            ck(loaded.seed == s1, "после загрузки simSeed восстановлен");
            Simulation loaded2 = Simulation.load(loaded.save());
            ck(loaded2.seed == s1, "повторная загрузка simSeed не меняет");

            // разные сиды — разные истории; один сид — детерминизм
            Simulation x = days(101, 30);
            Simulation y = days(202, 30);
            ck(!x.save().equals(y.save()), "разные simSeed: истории миров различаются (30 дней)");
            Simulation x2 = days(101, 30);
            ck(x.save().equals(x2.save()), "один simSeed: история детерминирована");

            // совместимость: старое сохранение без отдельного simSeed — сид из симуляции, история сохранена
            long legacySeed = -840995054016536013L; // сид TheZone: старые миры получали sim из него
            Simulation legacy = days(legacySeed, 10);
            String legacySave = legacy.save();
            long resolved = SimSeed.resolve(false, 0, Simulation.load(legacySave).seed);
            ck(resolved == legacySeed, "старый мир: simSeed = сид симуляции, история не сброшена");
            ck(SimSeed.resolve(true, 42, 99) == 42, "сохранённый simSeed приоритетнее сида симуляции");
            ck(Simulation.load(legacySave).seed == legacySeed, "сид legacy-сохранения переживает загрузку");

            // обвязка ZoneData по исходнику (Minecraft-класс в javac-набор не входит)
            String zd = read("src/main/java/ru/stalkerzone/ZoneData.java");
            ck(!zd.contains("overworld().getSeed()"), "ZoneData больше не выводит sim из сида мира Minecraft");
            ck(zd.contains("SimSeed.newSeed()"), "новый мир: simSeed случаен (SimSeed.newSeed)");
            int gi = zd.indexOf("SimSeed.newSeed()");
            int gj = zd.indexOf("NAME);", gi);
            ck(gi >= 0 && gj > gi && zd.substring(gi, gj).contains("setDirty()"),
                    "новый мир: данные помечаются к сохранению сразу (simSeed переживёт выход до первого дня симуляции)");
            ck(zd.contains("tag.putLong(\"simSeed\", simSeed)"), "simSeed сохраняется в данные мира (NBT)");
            ck(zd.contains("SimSeed.resolve(hasSeed, tag.getLong(\"simSeed\"), sim.seed)"),
                    "загрузка: сохранённый simSeed, иначе legacy-сид симуляции");
            ck(zd.contains("simSeed = seed;"), "reset(seed) обновляет simSeed мира");
            String sm = read("src/main/java/ru/stalkerzone/sim/Simulation.java");
            ck(sm.contains("this.seed = seed;") && sm.contains("out.writeLong(seed);"),
                    "Simulation: сид — основа RNG и сохраняется в строке сохранения");
        } finally {
            if (old == null) System.clearProperty("stalkerzone.scenario");
            else System.setProperty("stalkerzone.scenario", old);
        }
        System.out.println(f == 0 ? "SimSeedTest: всё зелёное" : "SimSeedTest: ПРОВАЛ " + f);
        System.exit(f > 0 ? 1 : 0);
    }
}
