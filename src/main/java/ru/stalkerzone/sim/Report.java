package ru.stalkerzone.sim;

/** Текстовые отчёты для консоли и игровых команд. */
public final class Report {
    private Report() {}

    public static String factions(Simulation sim) {
        StringBuilder sb = new StringBuilder("== Группировки (день " + sim.day + ") ==\n");
        for (Faction f : sim.factions) {
            sb.append(String.format("%s: %d чел., %s (%d дн.), готовность %.2f, %d руб.%n",
                    f.name, f.members, f.phase.ru, f.phaseDays, sim.readiness(f), f.stock.money));
            sb.append("  склад: ");
            for (Good g : Good.ALL) sb.append(g.ru).append(' ').append(f.stock.get(g)).append("  ");
            sb.append('\n');
            sb.append("  цель: ").append(f.goal.ru);
            if (f.goal == Goal.REVENGE && f.goalTarget >= 0) sb.append(" против «").append(sim.factions.get(f.goalTarget).name).append("»");
            for (Faction o : sim.factions) {
                if (o != f && f.grudge[o.id] > 0.5) sb.append(String.format(", обида на «%s» %.1f", o.name, f.grudge[o.id]));
            }
            sb.append('\n');
            sb.append("  территории: ");
            for (Node n : sim.nodes) if (n.owner == f.id) sb.append(n.name).append(", ");
            sb.append(String.format("%n  потери %d, войн начато %d, захвачено %d, потеряно %d, караванов разграблено %d%n",
                    f.deaths, f.warsStarted, f.nodesCaptured, f.nodesLost, f.caravansLost));
            if (f.phase == Phase.WAR && f.warNode >= 0) {
                sb.append("  штурм: ").append(sim.nodes.get(f.warNode).name).append(String.format(" (осада %.1f)%n", f.siege));
            }
        }
        return sb.toString();
    }

    public static String lairs(Simulation sim) {
        StringBuilder sb = new StringBuilder("== Логова мутантов (день " + sim.day + ") ==\n");
        if (sim.lairs.isEmpty()) sb.append("Логов нет.\n");
        for (Lair l : sim.lairs) {
            Node n = sim.nodes.get(l.node);
            String owner = n.owner < 0 ? "ничья земля" : sim.factions.get(n.owner).name;
            sb.append(String.format("%s у «%s» (%s): сила %.0f из %.0f, возраст %d дн.%n", l.title(), n.name, owner, l.strength, l.cap(), l.age));
        }
        sb.append("Уничтожено: ").append(sim.lairsDestroyed).append(", появилось новых: ").append(sim.lairsSpawned).append('\n');
        return sb.toString();
    }

    public static String buildings(Simulation sim) {
        StringBuilder sb = new StringBuilder("== Постройки (день " + sim.day + ") ==\n");
        for (Node n : sim.nodes) {
            java.util.List<Building> list = sim.buildingsAt(n.id);
            if (list.isEmpty()) continue;
            sb.append(String.format("%s (%s):%n", n.name, n.owner < 0 ? "ничья" : sim.factions.get(n.owner).name));
            for (Building b : list) {
                sb.append(String.format("  %s, уровень %d, %.0f%%, %s%n", b.type.ru, b.level, b.hp, b.condRu()));
                if (b.building()) sb.append(String.format("    стройка до уровня %d: материалы %d/%d, детали %d/%d, работа %.0f%%%n",
                        b.target, b.haveMat, b.needMat, b.haveParts, b.needParts, b.progress() * 100));
            }
        }
        return sb.toString();
    }

    public static String anomalies(Simulation sim) {
        StringBuilder sb = new StringBuilder("== Аномалии (день " + sim.day + ") ==\n");
        for (Node n : sim.nodes) {
            java.util.List<Anomaly> list = sim.anomaliesAt(n.id);
            if (list.isEmpty()) continue;
            java.util.Map<AnomalyType, Integer> byType = new java.util.EnumMap<>(AnomalyType.class);
            int pending = 0;
            double power = 0;
            for (Anomaly a : list) { byType.merge(a.type, 1, Integer::sum); pending += a.pending; power += sim.anomalyPower(a); }
            power /= list.size();
            sb.append(String.format("%s: %d аномалий, сила %.1f, изучено %.0f%%, артефактов ждёт %d%n   ", n.name, list.size(), power, sim.knowledge[n.id] * 100, pending));
            byType.forEach((t, c) -> sb.append(t.ru).append(" x").append(c).append(", "));
            sb.append('\n');
        }
        sb.append("Погибло в аномалиях: ").append(sim.anomalyDeaths).append(", артефактов выдано в мир: ").append(sim.artifactsWild).append('\n');
        return sb.toString();
    }

    /** Склады точек, снабжённость, конвои, контракт армии и доходы группировок (v1.10). */
    public static String logistics(Simulation sim) {
        Logistics l = sim.logistics;
        StringBuilder sb = new StringBuilder("== Снабжение (день " + sim.day + ") ==\n");
        for (Node n : sim.nodes) {
            if (n.owner < 0) continue;
            Faction f = sim.factions.get(n.owner);
            int g = Logistics.garrison(sim, f, n);
            if (g == 0 && n.id != f.baseNode) continue;
            Stock d = n.id == f.baseNode ? f.stock : n.depot;
            sb.append(String.format("%s (%s%s): %s, еда %d, патроны %d (заводских %d), медицина %d, стройматериалы %d, сейф %d руб.%s%n",
                    n.name, f.name, n.id == f.baseNode ? ", база" : ", гарнизон " + g,
                    n.id == f.baseNode ? "склад фракции" : String.format("снабжение %.0f%%, сепаратизм %.0f%%", n.supply * 100, n.separatism * 100),
                    d.get(Good.FOOD), d.get(Good.AMMO) + d.get(Good.AMMO_PRO), d.get(Good.AMMO_PRO), d.get(Good.MEDS), d.get(Good.MATERIALS), d.money,
                    Logistics.fieldBase(sim, n) ? " [полевая база армии]" : ""));
        }
        sb.append("Конвои в пути: ").append(l.convoys.size()).append('\n');
        for (Convoy c : l.convoys) {
            sb.append(String.format("  «%s», %s: %s → %s, сейчас у «%s», охрана %d%s, груз на %d руб. (наличными %d)%n",
                    sim.factions.get(c.owner).name, c.kind.ru, sim.nodes.get(c.from()).name, sim.nodes.get(c.dest()).name,
                    sim.nodes.get(c.at()).name, c.guards, c.troops > 0 ? " + пополнение " + c.troops : "", c.value(), c.cargo.money));
        }
        sb.append(String.format("Контракт учёных с армией: %s, оплат %d, пропусков %d, всего %d руб., обязательства выполнены на %.0f%%%n",
                l.contractActive ? "действует" : "разорван", l.contractPaid, l.contractMissed, l.contractMoney, l.contractDuty * 100));
        sb.append(String.format("Конвоев отправлено %d, дошло %d, перехвачено %d (отбито %d руб.); дезертиров %d, отколовшихся гарнизонов %d, новобранцев с Большой земли %d%n",
                l.convoysSent, l.convoysArrived, l.convoysRobbed, l.cashRobbed, l.deserters, l.secessions, l.recruitsBought));
        sb.append(String.format("Доходы: охрана Бара (Долг) %d руб., рэкет бандитов %d руб., контрабанда Свободы %d руб.%n",
                l.dutyProtection, l.banditRacket, l.freedomSmuggled));
        return sb.toString();
    }

    /** Глобальный рынок Большой земли: цены, тренды, события, заказы в пути. */
    public static String world(Simulation sim) {
        GlobalMarket w = sim.world;
        StringBuilder sb = new StringBuilder("== Большая земля (день " + sim.day + "), торгуют только торговцы и учёные ==\n");
        for (Good g : Good.ALL) {
            int i = g.ordinal();
            if (!g.mainland) continue; // кустарное и прототипы есть только в Зоне
            String arrow = w.trend[i] > 0.004 ? "▲" : w.trend[i] < -0.004 ? "▼" : "•";
            sb.append(String.format("  %-15s %7.0f руб. (%+.0f%% к обычной) %s%n", g.ru, w.price[i], (w.price[i] / g.base - 1) * 100, arrow));
        }
        if (!w.events.isEmpty()) {
            sb.append("События:\n");
            for (GlobalMarket.Event e : w.events) sb.append("  ").append(e.title).append(" (до дня ").append(e.until).append(")\n");
        }
        sb.append("Кустарное и прототипы на Большой земле не продаются: их делают и перепродают только в Зоне.\n");
        sb.append("Заказов в пути: ").append(w.orders.size()).append(String.format(", оборот: куплено на %d, продано на %d руб.; отложено покупок %d, совершено %d%n",
                w.turnoverBuy, w.turnoverSell, w.waited, w.bought));
        return sb.toString();
    }

    public static String economy(Simulation sim) {
        StringBuilder sb = new StringBuilder("== Экономика (день " + sim.day + ") ==\n");
        sb.append("Деньги в Зоне: ").append(sim.totalMoney())
                .append(" (пришло ").append(sim.ledger.moneyMinted)
                .append(", ушло ").append(sim.ledger.moneyBurned).append(")\n");
        sb.append("Караванов в пути: ").append(sim.caravans.size()).append('\n');
        for (Node n : sim.nodes) {
            if (!n.trader) continue;
            sb.append("Торговец «").append(n.name).append("», касса ").append(n.market.money).append(" руб.\n");
            for (Good g : Good.ALL) {
                sb.append(String.format("  %-12s склад %5d  покупает %4d  продаёт %4d%n",
                        g.ru, n.market.get(g), sim.buyPrice(n, g), sim.sellPrice(n, g)));
            }
        }
        sb.append("Поток товаров (создано / уничтожено / ввезено / вывезено):\n");
        for (Good g : Good.ALL) {
            int i = g.ordinal();
            sb.append(String.format("  %-12s %7d / %7d / %6d / %6d%n", g.ru,
                    sim.ledger.created[i], sim.ledger.destroyed[i], sim.ledger.imported[i], sim.ledger.exported[i]));
        }
        return sb.toString();
    }

    public static String market(Simulation sim, Node n) {
        StringBuilder sb = new StringBuilder("== «" + n.name + "» ==\n");
        if (!n.trader) {
            sb.append("Здесь нет торговца.\n");
        } else {
            sb.append("Касса торговца: ").append(n.market.money).append(" руб.\n");
            for (Good g : Good.ALL) {
                sb.append(String.format("%-12s в наличии %4d  купит за %4d  продаст за %4d%n",
                        g.ru, n.market.get(g), sim.buyPrice(n, g), sim.sellPrice(n, g)));
            }
        }
        return sb.toString();
    }

    public static String scorcher(Simulation sim) {
        ScorcherProject p = sim.scorcher;
        StringBuilder sb = new StringBuilder("== Выжигатель (день " + sim.day + ") ==\n");
        sb.append(p.disabled ? "Выжигатель ОТКЛЮЧЁН\n" : "Выжигатель работает\n");
        sb.append(String.format("Исследования: %.1f / %.0f, пси-технологии: %.1f%n", p.research, ScorcherProject.RESEARCH_GOAL, p.psiTech));
        sb.append("Экспедиций: ").append(p.attempts).append(", лучший достигнутый этап: ")
                .append(p.attempts == 0 ? "—" : ScorcherProject.STAGE_NAMES[Math.min(p.bestStage, ScorcherProject.STAGE_NAMES.length - 1)]).append('\n');
        if (!p.disabled && p.research >= ScorcherProject.RESEARCH_GOAL) {
            int left = Math.max(0, ScorcherProject.ATTEMPT_COOLDOWN - (sim.day - p.lastAttempt));
            sb.append("Исследования завершены, следующая попытка прорыва не ранее чем через ").append(left).append(" дн.\n");
        }
        Faction m = sim.monolith();
        if (m != null) {
            sb.append("Монолит: ").append(sim.monolithActive ? "АКТИВЕН" : "спит").append(", ").append(m.members).append(" чел., территории: ");
            for (Node n : sim.nodes) if (n.owner == m.id) sb.append(n.name).append(", ");
            sb.append('\n');
        }
        for (Faction f : sim.factions) {
            if (sim.monolithActive && !f.passive && !f.fanatic) {
                sb.append("  ").append(f.name).append(": ").append(f.fightMonolith ? "принимают бой" : "уходят от боя").append('\n');
            }
        }
        return sb.toString();
    }

    /** Люди: поток новичков, опыт, стресс, раненые, зомби, именные сталкеры. */
    public static String people(Simulation sim) {
        People p = sim.people;
        StringBuilder sb = new StringBuilder("== Люди Зоны (день " + sim.day + ") ==\n");
        sb.append(String.format("Через Кордон пришло %d, вступили %d, погибли в пути %d, стали зомби %d, дезертиров %d, ушли из Зоны %d, в пути сейчас %d%n",
                p.arrived, p.joined, p.diedOnRoad, p.zombified, p.deserted, p.leftZone, p.transit.size()));
        sb.append(String.format("Медпункты: спасено раненых %d, умерло от ран %d%n", p.woundedSaved, p.woundedDied));
        for (Faction f : sim.factions) {
            Person l = p.leader(f.id);
            sb.append(String.format("%s: %d чел. (ранены %d), опыт %.0f, стресс %.0f, единство %.0f%%, лидер: %s%n", f.name, f.members, f.wounded,
                    f.exp, f.stress, f.unity * 100, l == null ? "нет" : l.name + " (" + l.rankRu() + ")"));
        }
        StringBuilder z = new StringBuilder();
        for (Node n : sim.nodes) if (p.zombies[n.id] >= 1) z.append(n.name).append(' ').append((int) p.zombies[n.id]).append(", ");
        sb.append("Зомби: ").append(z.length() == 0 ? "нет" : z).append('\n');
        return sb.toString();
    }

    public static String roster(Simulation sim, Faction f) {
        StringBuilder sb = new StringBuilder("== Именные сталкеры «" + f.name + "» ==\n");
        for (Person x : sim.people.of(f.id)) sb.append(x.describe(sim)).append('\n');
        return sb.toString();
    }

    public static String politics(Simulation sim) {
        Politics p = sim.politics;
        StringBuilder sb = new StringBuilder("== Власть и дипломатия (день " + sim.day + ") ==\n");
        sb.append(String.format("Встреч %d, засад %d, договорённостей %d, расколов %d%n", p.meetingsHeld, p.ambushes, p.trucesSigned, p.schisms));
        if (p.meeting != null) sb.append(p.meeting.title(sim)).append('\n');
        else sb.append("Следующая встреча готовится к дню ").append(p.nextMeetingDay).append('\n');
        for (Politics.Truce t : p.truces) sb.append(String.format("%s «%s» и «%s» до дня %d%n", t.alliance ? "Союз" : "Перемирие",
                sim.factions.get(t.a).name, sim.factions.get(t.b).name, t.until));
        return sb.toString();
    }

    public static String contracts(Simulation sim) {
        StringBuilder sb = new StringBuilder("== Контракты (день " + sim.day + ") ==\n");
        int n = 0;
        for (Contract c : sim.contracts.active()) { sb.append(c.describe(sim)).append('\n'); n++; }
        if (n == 0) sb.append("Открытых контрактов нет\n");
        sb.append(String.format("Выполнено игроками %d, сталкерами %d, провалено %d, истекло %d%n", sim.contracts.doneByPlayer,
                sim.contracts.doneByNpc, sim.contracts.failed, sim.contracts.expired));
        return sb.toString();
    }

    public static String labs(Simulation sim) {
        StringBuilder sb = new StringBuilder("== Лаборатории (цикл " + (sim.labs.cycle + 1) + ") ==\n");
        for (Lab l : sim.labs.labs) {
            sb.append(String.format("%s (%s), сложность %d: скачано %d/%d, изучено учёными %d/%d%s%n", l.name, sim.nodes.get(l.node).name, l.tier,
                    l.recoveredCount(), l.parts, l.deliveredCount(), l.parts, sim.labs.open(sim, l) ? "" : ", закрыта (Выжигатель)"));
        }
        sb.append(String.format("Экспедиций фракций %d, успешных %d, сбросов Монолитом %d; исследования Выжигателя %.0f%%, пси-защита %.0f%%%n",
                sim.labs.expeditions, sim.labs.expeditionsWon, sim.labs.resets, sim.scorcher.research, sim.scorcher.psiTech));
        return sb.toString();
    }
}
