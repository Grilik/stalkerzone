import ru.stalkerzone.sim.*;
import java.util.*;

/** Проверки логов Кордона (карточка 007). */
public class KordonLairsTest {
    static int f = 0;

    static void ck(boolean ok, String m) {
        System.out.println((ok ? "OK   " : "FAIL ") + m);
        if (!ok) f++;
    }

    public static void main(String[] a) {
        startLairs();
        raidsOnFarmOrVillage();
        respawn();
        tunnelDangerDrop();
        speciesVariety();
        persistence();
        System.out.println(f == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО " + f);
        System.exit(f > 0 ? 1 : 0);
    }

    static void startLairs() {
        Simulation s = Scenarios.create("kordon", 0);
        ck(s.lairs.size() == 5, "5 стартовых логов: " + s.lairs.size());
        ck(s.lairAt(13) != null && s.lairAt(13).kind == LairKind.DOGS, "холм — DOGS");
        ck(s.lairAt(9) != null && s.lairAt(9).kind == LairKind.DOGS, "дыра — DOGS");
        ck(s.lairAt(14) != null && s.lairAt(14).kind == LairKind.FLESH, "поля — FLESH");
        ck(s.lairAt(15) != null && s.lairAt(15).kind == LairKind.FLESH, "лес — FLESH");
        ck(s.lairAt(7) != null && s.lairAt(7).kind == LairKind.RODENTS, "туннель — RODENTS");
        ck(s.lairAt(3) == null && s.lairAt(6) == null && s.lairAt(12) == null,
                "на выходах логов нет");
    }

    static void raidsOnFarmOrVillage() {
        int totalRaids = 0;
        boolean auditOk = true, noCapOk = true;
        for (long seed : new long[]{1, 3, 7}) {
            Simulation s = Scenarios.create("kordon", seed);
            int raids = 0;
            for (int d = 0; d < 60; d++) {
                s.step();
                String prefix = "[день " + s.day + "]";
                for (Object o : s.events) {
                    String e = (String) o;
                    if (e.startsWith(prefix) && (e.contains("напало на «Свиноферма»") || e.contains("напало на «Деревня новичков»")))
                        raids++;
                }
                for (int site : KordonScenario.LAIR_SITES)
                    if (s.nodes.get(site).owner >= 0) noCapOk = false;
                if (!s.audit().isEmpty()) { auditOk = false; break; }
            }
            totalRaids += raids;
            ck(raids >= 1, "seed " + seed + " нападений: " + raids);
        }
        ck(totalRaids >= 3, "за 3 сида × 60 дней ≥3 нападений: " + totalRaids);
        ck(auditOk, "аудит сходится 60 дней");
        ck(noCapOk, "LAIR_SITES не захвачены");
    }

    static void respawn() {
        for (long seed : new long[]{1, 5}) {
            Simulation s = Scenarios.create("kordon", seed);
            Lair l = s.lairAt(13);
            ck(l != null, "seed " + seed + " логово на холме есть");
            if (l == null) continue;
            // также удалить логово у дыры, чтобы весь южный район был пуст
            Lair l9 = s.lairAt(9);
            if (l9 != null) s.mutantKilled(l9, 100, false);
            int destroyDay = s.day;
            s.mutantKilled(l, 100, false);
            ck(s.lairAt(13) == null && s.lairAt(9) == null && s.lairAt(16) == null,
                    "южный район пуст после зачистки");
            int respawnDay = -1;
            for (int d = 0; d < 40; d++) {
                s.step();
                if (s.lairAt(13) != null || s.lairAt(16) != null || s.lairAt(9) != null) {
                    respawnDay = s.day; break;
                }
            }
            ck(respawnDay >= 0, "seed " + seed + " логово возродилось в южном районе");
            if (respawnDay >= 0) {
                int gap = respawnDay - destroyDay;
                ck(gap >= 15 && gap <= 30, "seed " + seed + " возрождение через " + gap + " дн. (окно 15–30)");
            }
            ck(s.audit().isEmpty(), "seed " + seed + " аудит после возрождения");
        }
    }

    static void tunnelDangerDrop() {
        Simulation s = Scenarios.create("kordon", 7);
        double before = s.graph.danger(7, 10);
        Lair rodents = s.lairAt(7);
        ck(rodents != null && rodents.kind == LairKind.RODENTS, "RODENTS в туннеле есть");
        if (rodents == null) return;
        s.mutantKilled(rodents, 100, false);
        s.recalcPassDanger();
        double after = s.graph.danger(7, 10);
        ck(after < before, String.format("опасность туннеля упала: %.4f -> %.4f", before, after));
    }
static void speciesVariety() {
        for (String dist : new String[]{"south", "north"}) {
            Simulation s = Scenarios.create("kordon", dist.equals("south") ? 3L : 11L);
            Set<LairKind> seen = new HashSet<>();
            boolean allInPool = true;
            int clears = 0;
            for (int step = 0; step < 120 && clears < 20; ) {
                int[] sites = dist.equals("south") ? KordonBalance.DISTRICT_SOUTH : KordonBalance.DISTRICT_NORTH;
                Lair target = null;
                for (int site : sites) {
                    Lair ll = s.lairAt(site);
                    if (ll != null) { target = ll; break; }
                }
                if (target == null) { s.step(); step++; continue; }
                seen.add(target.kind);
                KordonBalance.KindWeight[] pool = dist.equals("south")
                        ? KordonBalance.POOL_SOUTH : KordonBalance.POOL_NORTH;
                boolean inPool = false;
                for (KordonBalance.KindWeight kw : pool)
                    if (kw.kind == target.kind) { inPool = true; break; }
                if (!inPool) { allInPool = false;
                    System.out.println("FAIL вид " + target.kind + " вне пула " + dist); }
                double cap = target.kind.cap;
                ck(cap >= 30 && cap <= 65, "cap " + cap + " в 30..65");
                s.mutantKilled(target, 100, false);
                clears++;
                int site = target.node;
                for (int d = 0; d < 35 && s.lairAt(site) == null; d++) s.step();
            }
            ck(seen.size() >= 2, "район " + dist + ": ≥2 вида: " + seen);
            ck(allInPool, "район " + dist + ": все виды из пула");
        }
    }

    static void persistence() {
        Simulation s1 = Scenarios.create("kordon", 7);
        s1.step(5);
        Lair l = s1.lairAt(13);
        if (l != null) s1.mutantKilled(l, 100, false);
        s1.step(3);
        Simulation s2 = Simulation.load(s1.save());
        ck(s2.save().equals(s1.save()), "сохранение v14 идентично");
        ck(s2.lairRespawnQueue.size() == s1.lairRespawnQueue.size(),
                "очередь возрождений совпадает: " + s2.lairRespawnQueue.size());
    }
}