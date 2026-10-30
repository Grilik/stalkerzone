package ru.stalkerzone.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Встроенное командование: оценочный ИИ без внешних зависимостей. Каждый день:
 * 1) пересматривает стратегическую цель по характеру фракции, состоянию склада и памяти обид;
 * 2) расставляет приоритеты производства;
 * 3) выбирает торговца с учётом цен, кассы, дороги и памяти об ограблениях;
 * 4) решает, начинать ли наступление и на какую локацию.
 */
public final class UtilityBrain implements FactionBrain {
    private static final int REACH = 3;
    private static final int GOAL_REVIEW_DAYS = 8;

    @Override
    public List<Command> decide(Simulation sim, Faction f, Random rng) {
        if (f.fanatic) return crusade(sim, f, rng);
        List<Command> out = new ArrayList<>(4);
        out.add(stance(sim, f));
        reviseGoal(sim, f, rng);
        out.add(planProduction(f));
        Command trade = planTrade(sim, f);
        if (trade != null) out.add(trade);
        Command attack = planAttack(sim, f, rng);
        if (attack != null) out.add(attack);
        return out;
    }

    // ------------------------------------------------------------------ цель

    private void reviseGoal(Simulation sim, Faction f, Random rng) {
        boolean stale = sim.day - f.goalSince >= GOAL_REVIEW_DAYS;
        if (!stale) return;

        Doctrine d = f.doctrine;
        double ready = Math.min(1.5, sim.readiness(f));
        double avg = 0;
        int cnt = 0;
        int grudgeIdx = -1;
        double grudgeMax = 0;
        for (Faction o : sim.factions) {
            if (o == f) continue;
            avg += o.members;
            cnt++;
            double g = f.grudge[o.id] * d.vengeance;
            if (g > grudgeMax && reachable(sim, f, o)) {
                grudgeMax = g;
                grudgeIdx = o.id;
            }
        }
        avg /= Math.max(1, cnt);

        double[] score = new double[Goal.values().length];
        score[Goal.FORTIFY.ordinal()] = (1 - Math.min(1, ready)) * (0.7 + d.prudence) + (f.members < 0.6 * avg ? 0.25 : 0);
        score[Goal.TRADE.ordinal()] = d.mercantile * (0.55 + (f.stock.money < f.members * 40L ? 0.35 : 0));
        score[Goal.EXPAND.ordinal()] = d.expansion * (0.35 + 0.4 * f.aggression) * Math.min(1, ready);
        score[Goal.REVENGE.ordinal()] = grudgeMax > 0.8 ? d.vengeance * (0.5 + 0.5 * Math.min(2, grudgeMax)) : 0;
        score[Goal.CLEANSE.ordinal()] = hasCleanseTargets(sim, f) ? d.orderZeal * 0.8 * Math.min(1, ready) : 0;
        // Монолит проснулся: кто решил драться, идёт в отпор, остальные отсиживаются
        boolean monoWar = sim.monolithActive && !f.passive;
        if (monoWar && f.fightMonolith) score[Goal.RESIST.ordinal()] = (f.military ? 1.8 : 1.2) * Math.max(0.6, Math.min(1, ready));
        if (monoWar && !f.fightMonolith) score[Goal.FORTIFY.ordinal()] += 0.6;

        Goal best = f.goal;
        double bestScore = -1;
        for (Goal g : Goal.values()) {
            if (g == Goal.CRUSADE || (g == Goal.RESIST && score[g.ordinal()] == 0)) continue;
            double s = score[g.ordinal()] + 0.12 * rng.nextDouble() + (g == f.goal ? 0.15 : 0);
            if (s > bestScore) {
                bestScore = s;
                best = g;
            }
        }
        int newTarget = best == Goal.REVENGE ? grudgeIdx : -1;
        if (best != f.goal || newTarget != f.goalTarget) {
            String why = best == Goal.REVENGE && newTarget >= 0 ? " против «" + sim.factions.get(newTarget).name + "»" : "";
            sim.log(f.name + ": новая цель — " + best.ru.toLowerCase() + why);
        }
        f.goal = best;
        f.goalTarget = newTarget;
        f.goalSince = sim.day;
    }

    private boolean reachable(Simulation sim, Faction f, Faction o) {
        for (Node n : sim.nodes) {
            if (n.owner == o.id && n.id != o.baseNode && sim.minDistFromOwned(f, n.id) <= REACH) return true;
        }
        return false;
    }

    private boolean hasCleanseTargets(Simulation sim, Faction f) {
        for (Node n : sim.nodes) {
            if (n.owner == f.id) continue;
            boolean criminal = n.owner >= 0 && sim.factions.get(n.owner).raider && sim.factions.get(n.owner).baseNode != n.id;
            if ((n.owner < 0 || criminal) && sim.minDistFromOwned(f, n.id) <= REACH) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ производство

    private Command planProduction(Faction f) {
        double ammo;
        double gear;
        switch (f.goal) {
            case TRADE:
                ammo = 0.8;
                gear = 0.7;
                break;
            case FORTIFY:
                ammo = 1.2;
                gear = 1.2;
                break;
            default:
                ammo = 1.3;
                gear = 1.2;
                break;
        }
        double medsRatio = f.stock.get(Good.MEDS) / (double) Math.max(1, f.targetOf(Good.MEDS));
        double meds = 1 + 0.5 * Math.max(0, Math.min(1, 1 - medsRatio));
        return new Command.Production(ammo, gear, meds);
    }

    // ------------------------------------------------------------------ торговля

    private Command planTrade(Simulation sim, Faction f) {
        if (sim.day - f.lastDispatch < 3) return null;
        if (sim.countCaravans(f) >= (f.members > 60 ? 2 : 1)) return null;
        Stock s = f.stock;
        int m = f.members;

        int[] sell = new int[Simulation.G];
        int[] want = new int[Simulation.G];

        int labReserve = s.get(Good.MEDS) < f.targetOf(Good.MEDS) ? Math.max(1, m / 20) : 0;
        if (f.scientist) labReserve = Math.max(labReserve, 3); // артефакты нужны для исследований
        sell[Good.ARTIFACT.ordinal()] = Math.max(0, s.get(Good.ARTIFACT) - labReserve);
        sell[Good.FOOD.ordinal()] = Math.max(0, s.get(Good.FOOD) - f.targetOf(Good.FOOD) * 2);
        sell[Good.PARTS.ordinal()] = Math.max(0, s.get(Good.PARTS) - (int) (f.targetOf(Good.PARTS) * 1.6));
        sell[Good.AMMO.ordinal()] = Math.max(0, s.get(Good.AMMO) - f.targetOf(Good.AMMO) * 2);
        sell[Good.MEDS.ordinal()] = Math.max(0, s.get(Good.MEDS) - (int) (f.targetOf(Good.MEDS) * 2.5));
        // кустарное снаряжение сверх нужного везут торговцам (мастерская работает и на продажу)
        sell[Good.GEAR.ordinal()] = Math.max(0, s.get(Good.GEAR) - (int) Math.ceil(f.targetOf(Good.GEAR) * 1.2));
        // прототипы учёные собирают на продажу: излишек сверх своего запаса уходит торговцам
        sell[Good.PROTO.ordinal()] = Math.max(0, s.get(Good.PROTO) - (f.scientist ? f.targetOf(Good.PROTO) : f.targetOf(Good.PROTO) * 2));
        // заводское не продают: его покупали под задачу (лишнее — только при большом избытке)
        sell[Good.AMMO_PRO.ordinal()] = f.military ? 0 : Math.max(0, s.get(Good.AMMO_PRO) - Math.max(200, f.targetOf(Good.AMMO_PRO) * 3));
        sell[Good.GEAR_PRO.ordinal()] = f.military ? 0 : Math.max(0, s.get(Good.GEAR_PRO) - Math.max(4, f.targetOf(Good.GEAR_PRO) * 3));

        long sellValue = 0;
        long wantValue = 0;
        for (Good g : Good.ALL) {
            int i = g.ordinal();
            sellValue += (long) sell[i] * g.base;
            if (g == Good.ARTIFACT) continue;
            int deficit = Math.max(0, f.targetOf(g) - s.get(g));
            // деталей не докупаем, пока они не нужны для патронов
            if (g == Good.PARTS) deficit = Math.min(deficit, m * 4);
            // заводское дорого: на профессиональные группы уходит не больше трети кассы
            if (g == Good.AMMO_PRO || g == Good.GEAR_PRO) deficit = (int) Math.min(deficit, s.money / 3 / Math.max(1, (long) (g.base * 1.3)));
            want[i] = deficit;
            wantValue += (long) deficit * g.base;
        }
        boolean worthSelling = sellValue >= 500;
        boolean worthBuying = wantValue >= 300 && s.money >= 300 + (f.fanatic ? 0 : m * 15L);
        if (!worthSelling && !worthBuying) return null;

        int guards = Math.min(6, Math.max(1, m / 10));
        // казна держит резерв на жалованье и войну: на закупки уходит то, что сверх него
        long reserve = f.fanatic ? 0 : m * 15L;
        long budget = (long) Math.min(Math.max(0, s.money - reserve) * 0.85, wantValue * 1.4 + 200);
        int hub = chooseHub(sim, f, sell, want, guards, sellValue + budget);
        if (hub < 0) return null;
        return new Command.Trade(hub, sell, want, guards, budget);
    }

    /** Выбор торговца: выручка и доступность нужного против времени в пути, риска дороги и памяти об ограблениях. */
    private int chooseHub(Simulation sim, Faction f, int[] sell, int[] want, int guards, long cargoValue) {
        int best = -1;
        double bestUtility = Double.NEGATIVE_INFINITY;
        for (Node h : sim.nodes) {
            if (!h.trader) continue;
            int dist = sim.graph.dist(f.baseNode, h.id);
            if (dist >= 1000) continue;

            double proceeds = 0;
            double avail = 0;
            for (Good g : Good.ALL) {
                int i = g.ordinal();
                proceeds += (double) sell[i] * sim.buyPrice(h, g);
                if (g != Good.ARTIFACT) avail += (double) Math.min(want[i], h.market.get(g)) * g.base;
            }
            proceeds = Math.min(proceeds, h.market.money * 0.8);

            double risk = pathRisk(sim, f, sim.graph.path(f.baseNode, h.id), guards) + 0.3 * f.hubRisk[h.id];
            risk = Math.min(0.95, risk);
            double utility = (proceeds + 0.5 * avail) / (1 + 0.12 * 2 * dist) - risk * cargoValue;
            if (utility > bestUtility) {
                bestUtility = utility;
                best = h.id;
            }
        }
        return best;
    }

    /** Шанс потерять груз за поход туда и обратно: мутанты на дорогах и налёты бандитов на их землях. */
    private double pathRisk(Simulation sim, Faction f, int[] route, int guards) {
        double guard = 1 - Math.min(0.6, 0.08 * guards);
        double raid = 0.3 * (1 - Math.min(0.7, 0.07 * guards));
        double survive = 1;
        for (int i = 0; i + 1 < route.length; i++) {
            int a = route[i];
            int b = route[i + 1];
            survive *= 1 - Math.min(0.9, sim.graph.danger(a, b) * sim.graph.edgeDays(a, b)) * guard;
            Node n = sim.nodes.get(b);
            if (n.owner >= 0 && n.owner != f.id && sim.factions.get(n.owner).raider) survive *= 1 - raid;
        }
        return 1 - survive * survive;
    }

    // ------------------------------------------------------------------ война

    private double attackMultiplier(Faction f) {
        switch (f.goal) {
            case FORTIFY: return 0;
            case TRADE: return 1 - 0.8 * f.doctrine.mercantile;
            case REVENGE: return 1.4;
            case CLEANSE: return 1.2;
            case RESIST: return 1.6;
            default: return 1.0;
        }
    }

    private Command planAttack(Simulation sim, Faction f, Random rng) {
        if (f.phase != Phase.ACCUMULATE || f.passive) return null;
        double mult = attackMultiplier(f);
        if (mult <= 0) return null;
        Doctrine d = f.doctrine;
        boolean resist = f.goal == Goal.RESIST;
        if (f.phaseDays < (resist ? 3 : 8 + 12 * d.prudence)) return null;
        if (sim.readiness(f) < (resist ? 0.6 : 0.7 + 0.3 * d.prudence)) return null;
        if (f.stock.money < f.members * 15L) return null;
        if (rng.nextDouble() >= (resist ? 0.3 : f.aggression * 0.15 * mult)) return null;
        int node = chooseTarget(sim, f, rng);
        return node < 0 ? null : new Command.Attack(node);
    }

    private int chooseTarget(Simulation sim, Faction f, Random rng) {
        Doctrine d = f.doctrine;
        double minRatio = 0.45 + 0.5 * d.prudence;
        double bestScore = 0;
        int best = -1;
        boolean resist = f.goal == Goal.RESIST;
        // армия идёт к учёным: точки на этом пути особенно важны
        Faction sci = sim.scientists();
        boolean[] onPath = new boolean[sim.nodes.size()];
        if (f.military && sci != null) {
            for (int id : sim.graph.path(f.baseNode, sci.baseNode)) onPath[id] = true;
        }
        for (Node n : sim.nodes) {
            if (n.owner == f.id) continue;
            if (sim.noCapture(n.id)) continue; // v14: выходы и логовища не захватывают
            Faction owner = n.owner >= 0 ? sim.factions.get(n.owner) : null;
            if (owner != null && owner.baseNode == n.id) continue;
            // армия возвращает свои полевые базы на дороге к учёным: это условие контракта
            boolean duty = f.military && Logistics.isFieldBaseSite(n) && !resist;
            if (n.type == NodeType.OUTPOST && !resist && !duty) continue;
            if (resist && (owner == null || !owner.fanatic)) continue;
            int dist = sim.minDistFromOwned(f, n.id);
            if (dist > REACH) continue;

            double hostile = owner != null ? sim.hostilityTo(f, owner.id) : 0.35;
            if (hostile < 0.3) continue;
            // после недавних провалов под этой точкой не повторяют штурм, пока потери не забудутся
            if (f.nodeFailure[n.id] >= 1.5 - 0.5 * d.vengeance) continue;

            // нейтральную точку берут, только если сил хватает с запасом (мутанты и аномалии не прощают ошибок)
            if (owner == null) {
                double spread = Math.max(0.5, 1 - 0.08 * (sim.ownedCount(f) - 1));
                double myPower = f.members * 0.8 * spread * 1.3;
                if (myPower < sim.nodeDefense(n) * 1.25) continue;
            }

            boolean revenge = owner != null && f.goal == Goal.REVENGE && owner.id == f.goalTarget;
            double ratio = owner != null ? f.members / (double) Math.max(1, owner.members) : 1.0;
            if (owner != null && ratio < (resist ? minRatio * 0.5 : revenge ? minRatio * 0.8 : minRatio)) continue;

            double value = (n.farmCap + n.partsCap * 1.2 + n.artifactCap * 30.0 + n.housing * 0.5) * d.expansion;
            if (owner != null && owner.raider) value *= 1 + d.orderZeal;
            if (owner == null && f.goal == Goal.CLEANSE) value *= 1 + d.orderZeal;
            if (revenge) value *= 1.8;
            if (resist) value = (value + 20) * 2;
            if (onPath[n.id]) value *= 4;
            if (duty) value = (value + 30) * 6;

            double defense = owner != null ? 1 + owner.members * 0.5 : sim.nodeDefense(n);
            double strength = owner != null ? Math.pow(ratio, 1.5) : 1.0;
            double score = hostile * value * strength / ((dist + 1) * Math.sqrt(defense))
                    / (1 + f.nodeFailure[n.id]) * (0.8 + 0.4 * rng.nextDouble());
            if (score > bestScore) {
                bestScore = score;
                best = n.id;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ Монолит и отпор

    /** Драться или отходить: решает смелость фракции и соотношение сил. Учёные не воюют. */
    private Command stance(Simulation sim, Faction f) {
        Faction m = sim.monolith();
        boolean fight = false;
        if (m != null && sim.monolithActive && !f.passive) {
            fight = f.doctrine.courage + 0.6 * f.members / Math.max(1.0, m.members) > 0.9;
        }
        return new Command.Stance(fight);
    }

    /** Командование Монолита: спит, пока работает Выжигатель, затем идёт походом на учёных и сильнейших. */
    private List<Command> crusade(Simulation sim, Faction f, Random rng) {
        List<Command> out = new ArrayList<>(2);
        f.goal = sim.monolithActive ? Goal.CRUSADE : Goal.FORTIFY;
        out.add(new Command.Production(1.4, 1.0, 1.2));
        if (!sim.monolithActive || f.phase != Phase.ACCUMULATE || f.phaseDays < 2 || sim.readiness(f) < 0.55) return out;
        int node = chooseCrusadeTarget(sim, f, rng);
        if (node >= 0) out.add(new Command.Attack(node));
        return out;
    }

    private int chooseCrusadeTarget(Simulation sim, Faction f, Random rng) {
        Faction sci = sim.scientists();
        boolean sciAlive = sci != null && (sim.scorcher.psiTech > 0 || sci.members > 6);
        boolean[] onPath = new boolean[sim.nodes.size()];
        if (sciAlive) for (int id : sim.graph.path(f.baseNode, sci.baseNode)) onPath[id] = true;

        double bestScore = 0;
        int best = -1;
        for (Node n : sim.nodes) {
            if (n.owner == f.id) continue;
            Faction owner = n.owner >= 0 ? sim.factions.get(n.owner) : null;
            int dist = sim.minDistFromOwned(f, n.id);
            if (dist > REACH) continue;
            if (f.nodeFailure[n.id] >= 1.5) continue;
            if (owner != null && f.members < 0.4 * owner.members) continue;

            double value = 10 + n.partsCap + n.farmCap + n.artifactCap * 5.0;
            if (owner != null) value += owner.members * 0.8;
            if (owner != null && owner.baseNode == n.id) value *= 1.5;
            if (sciAlive && owner == sci) value += 400;
            if (onPath[n.id]) value *= 2.5;
            double defense = owner != null ? 1 + owner.members * 0.5 : sim.nodeDefense(n);
            double score = value / ((dist + 1) * Math.sqrt(defense)) / (1 + f.nodeFailure[n.id])
                    * (0.8 + 0.4 * rng.nextDouble());
            if (score > bestScore) {
                bestScore = score;
                best = n.id;
            }
        }
        return best;
    }
}
