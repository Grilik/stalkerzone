package ru.stalkerzone.sim;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Лаборатории и исследования. Фракции и игрок добывают данные с терминалов и передают учёным:
 * исследования Выжигателя и пси-защиты растут, за полное изучение лаборатории учёные получают прорыв.
 * Победа Монолита (разгром лагеря учёных) уничтожает все данные: цикл начинается заново.
 */
public final class Labs {
    public final List<Lab> labs = new ArrayList<>();
    public int cycle;
    public int expeditions, expeditionsWon, resets;

    Labs(Simulation s) {
        int agro = find(s, "Агропром"), dark = find(s, "Тёмная долина"), yantar = find(s, "Янтарь"), radar = find(s, "Радар"), chaes = find(s, "ЧАЭС");
        if (agro >= 0) labs.add(new Lab(labs.size(), "agroprom", "Подземелья НИИ Агропром", agro, 1, 2, 38, 34,
                "Тоннели под институтом: документы о военных разработках и тайник Стрелка."));
        if (dark >= 0) labs.add(new Lab(labs.size(), "x18", "Лаборатория X-18", dark, 2, 3, -48, -36,
                "Под заводом в Тёмной долине: опыты с артефактами и мутантами, документы о X-16."));
        if (yantar >= 0) labs.add(new Lab(labs.size(), "x16", "Лаборатория X-16", yantar, 3, 3, 52, -42,
                "Под заводом «Янтарь»: пси-установка, превращающая людей в зомби. Без пси-защиты не войти."));
        if (radar >= 0) labs.add(new Lab(labs.size(), "x10", "Лаборатория X-10", radar, 4, 4, -10, -50,
                "Под Радаром: управление Выжигателем мозгов, охрана Монолита."));
        if (chaes >= 0) labs.add(new Lab(labs.size(), "x2", "Лаборатория X-2 (саркофаг)", chaes, 5, 4, 46, 48,
                "Секретная лаборатория под саркофагом ЧАЭС: О-Сознание и тайна Зоны."));
    }

    private static int find(Simulation s, String name) {
        for (Node n : s.nodes) if (n.name.equals(name)) return n.id;
        return -1;
    }

    public Lab at(int node) {
        for (Lab l : labs) if (l.node == node) return l;
        return null;
    }

    /** Доступна ли лаборатория вообще: к X-2 не пройти, пока работает Выжигатель. */
    public boolean open(Simulation s, Lab l) {
        return l.tier < 5 || s.monolithActive;
    }

    /** Прототип пси-шлема учёные делают рано: хватает первых данных или накопленной пси-защиты. */
    public static final double PROTO_PSI = 10, FULL_PSI = 75;

    public boolean prototypeReady(Simulation s) {
        return s.scorcher.psiTech >= PROTO_PSI;
    }

    /** Полноценный пси-шлем: после полного изучения данных X-16 (как в оригинале) или при очень высокой пси-защите. */
    public boolean fullHelmetReady(Simulation s) {
        for (Lab l : labs) if (l.code.equals("x16") && l.deliveredCount() >= l.parts) return true;
        return s.scorcher.psiTech >= FULL_PSI;
    }

    /** Ближайшая цель учёных: самая простая лаборатория с нескачанными данными. */
    public Lab nextTarget(Simulation s) {
        for (Lab l : labs) if (!l.complete() && open(s, l)) return l;
        return null;
    }

    void daily(Simulation s, Random r) {
        for (Faction f : s.factions) {
            if (f.passive || f.fanatic || f.members < 30 || f.phase == Phase.WAR) continue;
            if (s.day < 40 || s.readiness(f) < 0.9 || r.nextDouble() > 1.0 / 75) continue;
            Lab target = null;
            for (Lab l : labs) {
                if (l.complete() || !open(s, l) || !s.graph.reachable(f.baseNode, l.node)) continue;
                if (l.tier >= 4 && f.members < 50) continue;
                target = l;
                break;
            }
            if (target != null) expedition(s, f, target, r);
        }
    }

    private void expedition(Simulation s, Faction f, Lab l, Random r) {
        expeditions++;
        // в экспедицию идёт профессиональная группа: заводские патроны первыми
        int proBefore = f.stock.get(Good.AMMO_PRO);
        Arms.spendAmmo(s, f.stock, 80 * l.tier, 0.8);
        boolean proAmmo = proBefore - f.stock.get(Good.AMMO_PRO) >= 20 * l.tier;
        s.takeGood(f, Good.MEDS, 4 * l.tier);
        double p = 0.36 - 0.07 * l.tier + 0.0015 * f.exp + (s.nodes.get(l.node).owner == f.id ? 0.1 : 0);
        // лаборатории полны аномалий: зонная броня выручает, заводская подводит; заводские патроны — против охраны
        p *= 0.8 + 0.25 * Arms.anomalyGuard(f.stock);
        if (proAmmo) p += 0.04;
        if (l.tier >= 3) p -= 0.12 * (1 - s.scorcher.psiTech / 100.0);
        p *= s.people.combatQuality(f);
        boolean ok = r.nextDouble() < Math.max(0.03, Math.min(0.6, p));
        s.applyCasualtiesOf(f, l.tier + (ok ? 0 : 1 + 2 * l.tier));
        if (!ok) {
            s.log("Экспедиция «" + f.name + "» в «" + l.name + "» провалилась");
            return;
        }
        expeditionsWon++;
        int part = firstFree(l);
        l.recovered |= 1 << part;
        long grant = 1200L * l.tier;
        f.stock.money += grant;
        s.ledger.moneyMinted += grant;
        f.exp = Math.min(100, f.exp + 2);
        s.log("«" + f.name + "» добыли данные в «" + l.name + "» (" + l.recoveredCount() + "/" + l.parts + ") и продали учёным за " + grant + " руб.");
        deliver(s, l, part);
    }

    private int firstFree(Lab l) {
        for (int i = 0; i < l.parts; i++) if (!l.taken(i)) return i;
        return 0;
    }

    /** Учёные изучают часть данных: растут исследования Выжигателя и пси-защиты, полное изучение даёт прорыв. */
    private void deliver(Simulation s, Lab l, int part) {
        boolean was = l.deliveredCount() >= l.parts;
        l.delivered |= 1 << part;
        ScorcherProject sp = s.scorcher;
        if (!sp.disabled) sp.research = Math.min(ScorcherProject.RESEARCH_GOAL, sp.research + 3 * l.tier);
        sp.psiTech = Math.min(100, sp.psiTech + 3 * l.tier);
        if (!was && l.deliveredCount() >= l.parts) {
            switch (l.code) {
                case "x16" -> { sp.psiTech = Math.min(100, sp.psiTech + 20); s.log("Учёные изучили данные X-16: готов полноценный пси-шлем, с ним можно пройти поле Выжигателя"); }
                case "x10" -> { sp.labBonus = 1.2; s.log("Учёные изучили данные X-10: известны слабые места Выжигателя"); }
                case "x2" -> {
                    Faction m = s.monolith();
                    if (m != null) {
                        int lost = m.members / 3;
                        m.members -= lost;
                        m.deaths += lost;
                    }
                    s.log("Учёные изучили данные X-2: тайна О-Сознания раскрыта, Монолит ослаб");
                }
                default -> s.log("Учёные изучили данные «" + l.name + "»");
            }
        }
    }

    /** Игрок начинает скачивание терминала. Возвращает текст ошибки или null. */
    public String canDownload(Simulation s, Lab l, int part) {
        if (part < 0 || part >= l.parts) return "Терминал не работает";
        if (!open(s, l)) return "Терминал заблокирован: О-Сознание не пускает";
        if (l.taken(part)) return "Данные с этого терминала уже скачаны";
        return null;
    }

    /** Скачивание завершено: терминал пуст до сброса цикла. Возвращает цикл (для накопителя). */
    public int downloaded(Simulation s, Lab l, int part, String player) {
        l.recovered |= 1 << part;
        s.log(player + " скачал данные в «" + l.name + "» (" + l.recoveredCount() + "/" + l.parts + ")");
        return cycle;
    }

    /** Игрок сдаёт данные учёным. Возвращает награду институтов или −1, если данные устарели. */
    public long playerDeliver(Simulation s, Lab l, int part, int dataCycle) {
        if (dataCycle != cycle) return -1;
        if ((l.delivered & (1 << part)) != 0) return -2;
        deliver(s, l, part);
        return l.playerReward();
    }

    /** Победа Монолита: все данные уничтожены, исследования заново. */
    void monolithVictory(Simulation s) {
        cycle++;
        resets++;
        for (Lab l : labs) {
            l.recovered = 0;
            l.delivered = 0;
        }
        s.scorcher.research = 0;
        s.scorcher.psiTech = 0;
        s.scorcher.labBonus = 1;
        s.log("ПОБЕДА МОНОЛИТА: данные всех лабораторий уничтожены, терминалы снова зашифрованы. Исследования придётся добывать заново");
    }

    /** Монолит занял точку с лабораторией: её данные потеряны. */
    void monolithSealed(Simulation s, int node) {
        Lab l = at(node);
        if (l == null || (l.recovered == 0 && l.delivered == 0)) return;
        l.recovered = 0;
        l.delivered = 0;
        s.log("Монолит запечатал «" + l.name + "»: её данные потеряны");
    }

    void write(DataOutputStream out) throws IOException {
        out.writeInt(cycle);
        out.writeInt(expeditions);
        out.writeInt(expeditionsWon);
        out.writeInt(resets);
        out.writeInt(labs.size());
        for (Lab l : labs) {
            out.writeInt(l.recovered);
            out.writeInt(l.delivered);
        }
    }

    void read(DataInputStream in) throws IOException {
        cycle = in.readInt();
        expeditions = in.readInt();
        expeditionsWon = in.readInt();
        resets = in.readInt();
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            int rec = in.readInt(), del = in.readInt();
            if (i < labs.size()) {
                labs.get(i).recovered = rec;
                labs.get(i).delivered = del;
            }
        }
    }
}
