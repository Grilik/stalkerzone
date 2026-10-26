package ru.stalkerzone;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Report;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.SpawnFinder;
import ru.stalkerzone.world.ZoneBounds;
import ru.stalkerzone.world.ZoneWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Отладочные команды /zone. Позже заменятся КПК, пока позволяют наблюдать за симуляцией. */
public final class ZoneCommands {
    private ZoneCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("zone")
                .then(Commands.literal("factions")
                        .executes(c -> send(c, Report.factions(sim(c)))))
                .then(Commands.literal("scorcher")
                        .executes(c -> send(c, Report.scorcher(sim(c)))))
                .then(buyCommand())
                .then(Commands.literal("repair").executes(ZoneCommands::repair))
                .then(Commands.literal("sell").executes(ZoneCommands::sellHeld))
                .then(Commands.literal("compat").executes(c -> send(c, ru.stalkerzone.compat.CompatConfig.summary()))
                        .then(Commands.literal("scan").executes(c -> send(c, ru.stalkerzone.compat.CompatScan.report())))
                        .then(Commands.literal("apply").requires(s -> s.hasPermission(2)).executes(c -> {
                            int n = ru.stalkerzone.compat.CompatScan.apply();
                            return send(c, n < 0 ? "Не удалось записать compat.json" : "Заполнено списков: " + n + ". " + ru.stalkerzone.compat.CompatConfig.summary());
                        })))
                .then(Commands.literal("givegun").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("gun", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(c -> giveGun(c, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "gun")))))
                .then(Commands.literal("rebuild")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ZoneCommands::rebuild)))
                .then(Commands.literal("status")
                        .executes(c -> {
                            try {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                return send(c, ru.stalkerzone.survival.PlayerNeeds.describe(p));
                            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                                return send(c, "Команда только для игрока.");
                            }
                        }))
                .then(Commands.literal("wallet")
                        .executes(c -> {
                            try {
                                return send(c, "У вас " + ru.stalkerzone.npc.TradeService.countRubles(c.getSource().getPlayerOrException()) + " руб.");
                            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                                return send(c, "Команда только для игрока.");
                            }
                        }))
                .then(Commands.literal("pda").executes(c -> {
                    try { ru.stalkerzone.pda.PdaService.open(c.getSource().getPlayerOrException(), true); return 1; }
                    catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { return send(c, "Команда только для игрока."); }
                }))
                .then(Commands.literal("contracts").executes(c -> send(c, Report.contracts(sim(c)))))
                .then(Commands.literal("contract")
                        .then(Commands.literal("take").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(c -> pdaAction(c, ru.stalkerzone.pda.PdaService.TAKE))))
                        .then(Commands.literal("abandon").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(c -> pdaAction(c, ru.stalkerzone.pda.PdaService.ABANDON))))
                        .then(Commands.literal("deliver").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(c -> pdaAction(c, ru.stalkerzone.pda.PdaService.DELIVER))))
                        .then(Commands.literal("claim").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(c -> pdaAction(c, ru.stalkerzone.pda.PdaService.CLAIM)))))
                .then(Commands.literal("labdata").executes(c -> {
                    try {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        ZoneData data = ZoneData.get(c.getSource().getServer());
                        String msg = ru.stalkerzone.pda.PdaService.deliverLabData(p, data);
                        data.setDirty();
                        return send(c, msg);
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { return send(c, "Команда только для игрока."); }
                }))
                .then(Commands.literal("people").executes(c -> send(c, Report.people(sim(c)))))
                .then(Commands.literal("roster").then(Commands.argument("faction", IntegerArgumentType.integer(0, 6)).executes(c -> {
                    Simulation s = sim(c);
                    int f = IntegerArgumentType.getInteger(c, "faction");
                    return send(c, f < s.factions.size() ? Report.roster(s, s.factions.get(f)) : "Нет такой фракции");
                })))
                .then(Commands.literal("politics").executes(c -> send(c, Report.politics(sim(c)))))
                .then(Commands.literal("labs").executes(c -> send(c, Report.labs(sim(c)))))
                .then(Commands.literal("labtp").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("lab", IntegerArgumentType.integer(0, 9)).executes(c -> {
                            try {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                Simulation s = sim(c);
                                int i = IntegerArgumentType.getInteger(c, "lab");
                                if (i >= s.labs.labs.size()) return send(c, "Нет такой лаборатории");
                                ru.stalkerzone.sim.Lab l = s.labs.labs.get(i);
                                Node n = s.nodes.get(l.node);
                                int x = n.x * ZoneBounds.SCALE + l.dx, z = n.z * ZoneBounds.SCALE + l.dz + 7;
                                ServerLevel level = c.getSource().getServer().overworld();
                                level.getChunk(x >> 4, z >> 4);
                                BlockPos pos = SpawnFinder.groundAt(level, x, z);
                                p.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180f, 0f);
                                return send(c, "Вход в «" + l.name + "» перед вами (шахта в бункере).");
                            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { return send(c, "Команда только для игрока."); }
                        })))
                .then(Commands.literal("monolithwin").requires(s -> s.hasPermission(2)).executes(c -> {
                    ZoneData data = ZoneData.get(c.getSource().getServer());
                    data.sim.forceMonolithVictory();
                    data.setDirty();
                    return send(c, "Засчитана победа Монолита: данные лабораторий уничтожены.");
                }))
                .then(Commands.literal("anomalies")
                        .executes(c -> send(c, Report.anomalies(sim(c))))
                        .then(Commands.literal("near").executes(ZoneCommands::anomaliesNear)))
                .then(Commands.literal("artifacts").requires(s -> s.hasPermission(2)).executes(c -> {
                    ZoneData data = ZoneData.get(c.getSource().getServer());
                    StringBuilder sb = new StringBuilder("Спрятано артефактов: " + data.hidden.size());
                    for (int i = 0; i < Math.min(8, data.hidden.size()); i++) {
                        int[] h = data.hidden.get(i);
                        sb.append("\n").append(ru.stalkerzone.item.ModItems.ARTIFACT_IDS[h[3]]).append(" ур.").append(ru.stalkerzone.anomaly.Artifacts.tierOf(h[3]))
                                .append(" — ").append(h[0]).append(' ').append(h[1]).append(' ').append(h[2]);
                    }
                    return send(c, sb.toString());
                }))
                .then(Commands.literal("emission")
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> {
                            ZoneData data = ZoneData.get(c.getSource().getServer());
                            data.sim.triggerEmission();
                            data.setDirty();
                            ru.stalkerzone.anomaly.EmissionEvent.start(c.getSource().getServer());
                            return send(c, "Выброс запущен: аномалии сменили положение.");
                        }))
                .then(Commands.literal("buildings")
                        .executes(c -> send(c, Report.buildings(sim(c)))))
                .then(Commands.literal("logistics").executes(c -> send(c, Report.logistics(sim(c)))))
                .then(Commands.literal("world").executes(c -> send(c, Report.world(sim(c)))))
                .then(Commands.literal("construct").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, sb) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        new String[] {"rookie_house", "sidorovich_bunker", "kpp", "road_post", "atp", "farm_elevator", "military_base"}, sb))
                                .then(Commands.argument("from", IntegerArgumentType.integer(0, 3))
                                        .then(Commands.argument("to", IntegerArgumentType.integer(0, 3))
                                                .executes(ZoneCommands::constructHere)))))
                .then(Commands.literal("build").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, sb) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        new String[] {"rookie_house", "sidorovich_bunker", "kpp", "road_post", "atp", "farm_elevator", "military_base", "vehicle_showcase",
                                                "military_camp", "rail_wagons", "watchtower", "tent_camp", "bus_stop", "shed", "well"}, sb))
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 3))
                                        .executes(ZoneCommands::buildHere))))
                .then(Commands.literal("lairs")
                        .executes(c -> send(c, Report.lairs(sim(c))))
                        .then(Commands.literal("near").executes(c -> {
                            Simulation sim = sim(c);
                            net.minecraft.world.phys.Vec3 pos = c.getSource().getPosition();
                            StringBuilder sb = new StringBuilder("Логова мутантов (ближайшие сначала):\n");
                            List<ru.stalkerzone.sim.Lair> list = new ArrayList<>(sim.lairs);
                            list.sort(java.util.Comparator.comparingDouble(l -> {
                                BlockPos den = ru.stalkerzone.mutant.MutantManager.denXZ(sim, l);
                                return Math.hypot(den.getX() - pos.x, den.getZ() - pos.z);
                            }));
                            for (int i = 0; i < Math.min(5, list.size()); i++) {
                                ru.stalkerzone.sim.Lair l = list.get(i);
                                BlockPos den = ru.stalkerzone.mutant.MutantManager.denXZ(sim, l);
                                sb.append(String.format("%s у «%s»: сила %.0f, координаты %d %d, до него %d блоков%n", l.title(),
                                        sim.nodes.get(l.node).name, l.strength, den.getX(), den.getZ(),
                                        (int) Math.hypot(den.getX() - pos.x, den.getZ() - pos.z)));
                            }
                            return send(c, list.isEmpty() ? "Логов нет." : sb.toString());
                        })))
                .then(Commands.literal("wake")
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> {
                            ZoneData data = ZoneData.get(c.getSource().getServer());
                            data.sim.activateMonolith();
                            data.setDirty();
                            return send(c, "Выжигатель отключён, Монолит проснулся.");
                        }))
                .then(Commands.literal("where")
                        .executes(ZoneCommands::where))
                .then(Commands.literal("border")
                        .executes(c -> send(c, "Границы Зоны: " + ZoneWorld.bounds()
                                + "\nЗабор по периметру, снаружи невидимая стена из барьеров (" + ZoneBounds.BARRIER_THICKNESS
                                + " блока), запасной WorldBorder. Масштаб: " + ZoneBounds.SCALE + " блоков на единицу карты.")))
                .then(Commands.literal("tp")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ZoneCommands::tp)))
                .then(Commands.literal("economy")
                        .executes(c -> send(c, Report.economy(sim(c)))))
                .then(Commands.literal("market")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ZoneCommands::market)))
                .then(Commands.literal("events")
                        .executes(c -> events(c, 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(c -> events(c, IntegerArgumentType.getInteger(c, "count")))))
                .then(Commands.literal("audit")
                        .executes(c -> {
                            String a = sim(c).audit();
                            return send(c, a.isEmpty() ? "Аудит: товары и деньги сходятся." : "РАСХОЖДЕНИЕ:\n" + a);
                        }))
                .then(Commands.literal("step")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("days", IntegerArgumentType.integer(1, 365))
                                .executes(c -> {
                                    ZoneData data = ZoneData.get(c.getSource().getServer());
                                    data.sim.step(IntegerArgumentType.getInteger(c, "days"));
                                    data.setDirty();
                                    return send(c, "Симуляция продвинута, сейчас день " + data.sim.day);
                                })))
                .then(Commands.literal("reset")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                .executes(c -> {
                                    ZoneData.get(c.getSource().getServer()).reset(LongArgumentType.getLong(c, "seed"));
                                    return send(c, "Зона пересоздана.");
                                }))));
    }

    private static Simulation sim(CommandContext<CommandSourceStack> c) {
        return ZoneData.get(c.getSource().getServer()).sim;
    }

    private static int market(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name").trim();
        Simulation sim = sim(c);
        for (Node n : sim.nodes) {
            if (n.name.equalsIgnoreCase(name)) return send(c, Report.market(sim, n));
        }
        return send(c, "Нет такой локации. Есть: " + nodeNames(sim));
    }

    /** /zone buy <товар> [число]: товар задаётся отдельным подкомандой (русское и английское имя), кириллица в аргументе-слове не читается. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buyCommand() {
        com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buy = Commands.literal("buy");
        for (ru.stalkerzone.sim.Good g : ru.stalkerzone.sim.Good.ALL) {
            for (String name : new String[] {g.ru.toLowerCase(), g.name().toLowerCase(), goodAlias(g)}) {
                if (name.contains(" ")) continue;
                buy.then(Commands.literal(name)
                        .executes(c -> buy(c, g, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 1000))
                                .executes(c -> buy(c, g, IntegerArgumentType.getInteger(c, "count")))));
            }
        }
        for (ru.stalkerzone.gear.Suit suit : ru.stalkerzone.gear.Suit.ALL) {
            for (String name : new String[] {suit.alias(), suit.id}) {
                buy.then(Commands.literal(name).executes(c -> buySuit(c, suit)));
            }
        }
        for (ru.stalkerzone.gear.Backpack b : ru.stalkerzone.gear.Backpack.ALL) {
            String alias = switch (b) {
                case SMALL -> "рюкзак";
                case RAID -> "рейдовый";
                case ARMY -> "ранец";
            };
            for (String name : new String[] {alias, b.id}) {
                buy.then(Commands.literal(name).executes(c -> buyGear(c, ru.stalkerzone.item.ModItems.BACKPACKS[b.ordinal()].get(), b.gearUnits)));
            }
        }
        // КПК и накопители данных продаёт любой торговец, пси-шлем только учёные
        for (String name : new String[] {"кпк", "pda"}) buy.then(Commands.literal(name).executes(c -> buyGear(c, ru.stalkerzone.item.ModItems.PDA.get(), 1)));
        for (String name : new String[] {"болты", "bolts"}) buy.then(Commands.literal(name).executes(c -> buyBolts(c)));
        String[][] det = {{"отклик", "echo"}, {"медведь", "bear"}, {"велес", "veles"}, {"сварог", "svarog"}};
        int[] detUnits = {1, 3, 6, 12};
        for (int i = 0; i < det.length; i++) {
            final int di = i;
            for (String name : det[i]) buy.then(Commands.literal(name).executes(c -> buyGear(c, ru.stalkerzone.item.ModItems.DETECTORS[di].get(), detUnits[di])));
        }
        for (String name : new String[] {"флешка", "flash"}) buy.then(Commands.literal(name).executes(c -> buyGear(c, ru.stalkerzone.item.ModItems.FLASH_DRIVE.get(), 1)));
        for (String name : new String[] {"диск", "disk"}) buy.then(Commands.literal(name).executes(c -> buyGear(c, ru.stalkerzone.item.ModItems.DATA_DISK.get(), 2)));
        for (String name : new String[] {"пси-шлем", "psi_helmet"}) buy.then(Commands.literal(name).executes(c -> buyPsiHelmet(c, false)));
        for (String name : new String[] {"прототип", "psi_helmet_proto"}) buy.then(Commands.literal(name).executes(c -> buyPsiHelmet(c, true)));
        // оружие TaCZ (если мод установлен): названия из config/stalkerzone/compat.json
        ru.stalkerzone.compat.CompatConfig.load();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ru.stalkerzone.compat.CompatConfig.GunSpec g : ru.stalkerzone.compat.CompatConfig.GUNS.values()) {
            for (String name : g.names()) {
                if (name.isEmpty() || name.contains(" ") || !seen.add(name.toLowerCase())) continue;
                // по умолчанию кустарная сборка; «заводской» — с Большой земли, дороже и надёжнее
                buy.then(Commands.literal(name.toLowerCase()).executes(c -> buyGun(c, g, true))
                        .then(Commands.literal("заводской").executes(c -> buyGun(c, g, false)))
                        .then(Commands.literal("factory").executes(c -> buyGun(c, g, false)))
                        .then(Commands.literal("кустарный").executes(c -> buyGun(c, g, true))));
            }
        }
        return buy;
    }

    /** Короткие имена товаров для /zone buy. */
    static String goodAlias(ru.stalkerzone.sim.Good g) {
        return switch (g) {
            case FOOD -> "еда";
            case PARTS -> "детали";
            case AMMO -> "патроны";
            case MEDS -> "медикаменты";
            case GEAR -> "снаряжение";
            case ARTIFACT -> "артефакты";
            case MATERIALS -> "стройматериалы";
            case PROTO -> "прототипы";
            case AMMO_PRO -> "заводские-патроны";
            case GEAR_PRO -> "импорт";
        };
    }

    /** Костюм: товар происхождения; лучшие фракционные линейки только у торговца на земле своей группировки. */
    private static int buySuit(CommandContext<CommandSourceStack> c, ru.stalkerzone.gear.Suit suit) {
        try {
            ServerPlayer p = c.getSource().getPlayerOrException();
            ZoneData data = ZoneData.get(c.getSource().getServer());
            Node n = traderNear(p, data);
            if (n == null) return send(c, "Рядом нет торговца (подойдите ближе 8 блоков).");
            if (suit == ru.stalkerzone.gear.Suit.MONOLITH) return send(c, "Комбинезоны Монолита не продаются: их снимают с тел.");
            if (suit.exclusive()) {
                String owner = n.owner >= 0 && n.owner < data.sim.factions.size() ? data.sim.factions.get(n.owner).name : "";
                if (!owner.equals(suit.faction))
                    return send(c, "«" + suit.ru + "» шьют только мастерские группировки «" + suit.faction + "»: ищите у торговца на её земле.");
            }
            ru.stalkerzone.npc.TradeService.buyEquipment(p, data, n, ru.stalkerzone.item.ModItems.SUITS[suit.ordinal()].get(), suit.origin.good, suit.gearUnits);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return send(c, "Команда только для игрока.");
        }
    }

    private static int buyGun(CommandContext<CommandSourceStack> c, ru.stalkerzone.compat.CompatConfig.GunSpec g, boolean hand) {
        try {
            ServerPlayer p = c.getSource().getPlayerOrException();
            if (!ru.stalkerzone.compat.Tacz.loaded()) return send(c, "Огнестрел появится, если установить мод TaCZ (Timeless and Classics Zero).");
            ZoneData data = ZoneData.get(c.getSource().getServer());
            Node n = traderNear(p, data);
            if (n == null) return send(c, "Рядом нет торговца (подойдите ближе 8 блоков).");
            net.minecraft.world.item.ItemStack gun = ru.stalkerzone.compat.GunQuality.markGun(ru.stalkerzone.compat.Tacz.gun(g.id(), g.mode(), g.mag()), hand);
            if (gun.isEmpty()) return send(c, "TaCZ не отдал оружие " + g.id());
            ru.stalkerzone.npc.TradeService.buyStack(p, data, n, gun, hand ? ru.stalkerzone.sim.Good.GEAR : ru.stalkerzone.sim.Good.GEAR_PRO,
                    ru.stalkerzone.compat.GunQuality.units(g, hand));
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return send(c, "Команда только для игрока.");
        }
    }

    /** /zone givegun <id>: выдать оружие TaCZ с патронами (для проверки). */
    private static int giveGun(CommandContext<CommandSourceStack> c, String id) {
        try {
            ServerPlayer p = c.getSource().getPlayerOrException();
            ru.stalkerzone.compat.CompatConfig.GunSpec g = ru.stalkerzone.compat.CompatConfig.byName(id);
            if (g == null) return send(c, "Нет такого оружия в compat.json: " + id);
            net.minecraft.world.item.ItemStack gun = ru.stalkerzone.compat.Tacz.gun(g.id(), g.mode(), g.mag());
            if (gun.isEmpty()) return send(c, "TaCZ не установлен.");
            p.getInventory().add(gun);
            p.getInventory().add(ru.stalkerzone.compat.Tacz.ammo(g.ammo(), Math.min(60, g.mag() * 2)));
            return send(c, "Выдано: " + g.id() + " и патроны " + g.ammo());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return send(c, "Команда только для игрока.");
        }
    }

    /** /zone sell: продать предмет в руке ближайшему торговцу (удобно для оружия TaCZ, у которого ПКМ занят прицеливанием). */
    private static int sellHeld(CommandContext<CommandSourceStack> c) {
        try {
            ServerPlayer p = c.getSource().getPlayerOrException();
            ZoneData data = ZoneData.get(c.getSource().getServer());
            Node n = traderNear(p, data);
            if (n == null) return send(c, "Рядом нет торговца (подойдите ближе 8 блоков).");
            net.minecraft.world.item.ItemStack held = p.getMainHandItem();
            if (held.isEmpty()) return send(c, "Возьмите в руку то, что продаёте.");
            if (held.getItem() instanceof ru.stalkerzone.item.GoodItem || ru.stalkerzone.compat.Tacz.isAmmo(held))
                ru.stalkerzone.npc.TradeService.sell(p, data, n, held);
            else if (ru.stalkerzone.npc.TradeService.gearUnits(held) > 0)
                ru.stalkerzone.npc.TradeService.sellEquipment(p, data, n, held);
            else return send(c, "Торговец такое не берёт.");
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return send(c, "Команда только для игрока.");
        }
    }

    private static Node traderNear(ServerPlayer p, ZoneData data) {
        List<ru.stalkerzone.npc.StalkerEntity> near = p.serverLevel().getEntitiesOfClass(ru.stalkerzone.npc.StalkerEntity.class,
                p.getBoundingBox().inflate(8), e -> e.isTrader());
        return near.isEmpty() ? null : data.sim.nodes.get(near.get(0).homeNode());
    }

    private static int buyPsiHelmet(CommandContext<CommandSourceStack> c, boolean proto) {
        try {
            ServerPlayer p = c.getSource().getPlayerOrException();
            ZoneData data = ZoneData.get(c.getSource().getServer());
            Node n = traderNear(p, data);
            ru.stalkerzone.sim.Faction sci = data.sim.scientists();
            if (n == null || sci == null || n.id != sci.baseNode) return send(c, "Пси-шлемы делают только учёные: подойдите к их торговцу.");
            if (proto && !data.sim.labs.prototypeReady(data.sim))
                return send(c, "Прототип ещё не готов: учёным нужны первые данные лабораторий (пси-защита " + (int) data.sim.scorcher.psiTech + "%, нужно " + (int) ru.stalkerzone.sim.Labs.PROTO_PSI + "%).");
            if (!proto && !data.sim.labs.fullHelmetReady(data.sim))
                return send(c, "Полноценный шлем учёные сделают, когда изучат все данные X-16 (или пси-защита дойдёт до " + (int) ru.stalkerzone.sim.Labs.FULL_PSI + "%). Сейчас доступен " + (data.sim.labs.prototypeReady(data.sim) ? "прототип: /zone buy прототип" : "только ожидание"));
            ru.stalkerzone.npc.TradeService.buyEquipment(p, data, n, proto ? ru.stalkerzone.item.ModItems.PSI_HELMET_PROTO.get() : ru.stalkerzone.item.ModItems.PSI_HELMET.get(), proto ? 2 : 5);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return send(c, "Команда только для игрока.");
        }
    }

    /** Болты: 32 штуки за одну единицу запчастей. */
    private static int buyBolts(CommandContext<CommandSourceStack> c) {
        try {
            ServerPlayer p = c.getSource().getPlayerOrException();
            ZoneData data = ZoneData.get(c.getSource().getServer());
            Node n = traderNear(p, data);
            if (n == null) return send(c, "Рядом нет торговца (подойдите ближе 8 блоков).");
            ru.stalkerzone.npc.TradeService.buyStack(p, data, n, new net.minecraft.world.item.ItemStack(ru.stalkerzone.item.ModItems.BOLT.get(), 32), ru.stalkerzone.sim.Good.PARTS, 1);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return send(c, "Команда только для игрока.");
        }
    }

    private static int buyGear(CommandContext<CommandSourceStack> c, net.minecraft.world.item.Item item, int units) {
        try {
            ServerPlayer p = c.getSource().getPlayerOrException();
            ZoneData data = ZoneData.get(c.getSource().getServer());
            Node n = traderNear(p, data);
            if (n == null) return send(c, "Рядом нет торговца (подойдите ближе 8 блоков).");
            ru.stalkerzone.npc.TradeService.buyEquipment(p, data, n, item, units);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return send(c, "Команда только для игрока.");
        }
    }

    private static int repair(CommandContext<CommandSourceStack> c) {
        try {
            ServerPlayer p = c.getSource().getPlayerOrException();
            ZoneData data = ZoneData.get(c.getSource().getServer());
            Node n = traderNear(p, data);
            if (n == null) return send(c, "Чинят торговцы: подойдите к торговцу ближе 8 блоков.");
            ru.stalkerzone.npc.TradeService.repair(p, data, n, p.getMainHandItem());
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return send(c, "Команда только для игрока.");
        }
    }

    private static int buy(CommandContext<CommandSourceStack> c, ru.stalkerzone.sim.Good good, int count) {
        try {
            ServerPlayer p = c.getSource().getPlayerOrException();
            List<ru.stalkerzone.npc.StalkerEntity> near = p.serverLevel().getEntitiesOfClass(ru.stalkerzone.npc.StalkerEntity.class,
                    p.getBoundingBox().inflate(8), e -> e.isTrader());
            if (near.isEmpty()) return send(c, "Рядом нет торговца (подойдите ближе 8 блоков).");
            ZoneData data = ZoneData.get(c.getSource().getServer());
            ru.stalkerzone.npc.TradeService.buy(p, data, data.sim.nodes.get(near.get(0).homeNode()), good, count);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return send(c, "Команда только для игрока.");
        }
    }

    private static int rebuild(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name").trim();
        ZoneData data = ZoneData.get(c.getSource().getServer());
        for (Node n : data.sim.nodes) {
            if (!n.name.equalsIgnoreCase(name)) continue;
            ru.stalkerzone.world.StructureManager.forget(data, n);
            return send(c, "Лагерь «" + n.name + "» будет построен заново, когда вы будете рядом (подойдите: /zone tp " + n.name + ").");
        }
        return send(c, "Нет такой локации. Есть: " + nodeNames(data.sim));
    }

    private static int pdaAction(CommandContext<CommandSourceStack> c, int action) {
        try {
            ru.stalkerzone.pda.PdaService.action(c.getSource().getPlayerOrException(), action, IntegerArgumentType.getInteger(c, "id"));
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return send(c, "Команда только для игрока.");
        }
    }

    private static int anomaliesNear(CommandContext<CommandSourceStack> c) {
        try {
            ServerPlayer p = c.getSource().getPlayerOrException();
            Simulation sim = sim(c);
            StringBuilder sb = new StringBuilder("Аномалии в радиусе 120 блоков:\n");
            int found = 0;
            for (ru.stalkerzone.sim.Anomaly a : sim.anomalies) {
                Node n = sim.nodes.get(a.node);
                int ax = n.x * ZoneBounds.SCALE + a.dx, az = n.z * ZoneBounds.SCALE + a.dz;
                double dx = ax - p.getX(), dz = az - p.getZ();
                double dist = Math.hypot(dx, dz);
                if (dist > 120) continue;
                String dir = Math.abs(dx) > Math.abs(dz) * 2 ? (dx > 0 ? "восток" : "запад")
                        : Math.abs(dz) > Math.abs(dx) * 2 ? (dz > 0 ? "юг" : "север")
                        : (dz > 0 ? "юг" : "север") + "-" + (dx > 0 ? "восток" : "запад");
                sb.append(String.format("%s (%s, %s) в %d блоках на %s, координаты %d %d%n", a.type.ru, a.type.kind,
                        Simulation.TIER_RU[sim.anomalyTier(a)], (int) dist, dir, ax, az));
                found++;
            }
            return send(c, found == 0 ? "Поблизости аномалий нет." : sb.toString());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return send(c, "Команда только для игрока.");
        }
    }

    private static int tp(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "name").trim();
        Simulation sim = sim(c);
        for (Node n : sim.nodes) {
            if (!n.name.equalsIgnoreCase(name)) continue;
            try {
                ServerPlayer p = c.getSource().getPlayerOrException();
                ServerLevel level = c.getSource().getServer().overworld();
                BlockPos pos = SpawnFinder.safeNear(level, ZoneWorld.bounds(), n.x * ZoneBounds.SCALE, n.z * ZoneBounds.SCALE);
                p.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYRot(), p.getXRot());
                return send(c, "Телепорт: " + n.name + " (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                return send(c, "Команда только для игрока.");
            }
        }
        return send(c, "Нет такой локации. Есть: " + nodeNames(sim));
    }

    private static int where(CommandContext<CommandSourceStack> c) {
        try {
            ServerPlayer p = c.getSource().getPlayerOrException();
            Simulation sim = sim(c);
            ZoneBounds b = ZoneWorld.bounds();
            int x = p.getBlockX(), z = p.getBlockZ();
            Node best = null;
            double bd = Double.MAX_VALUE;
            for (Node n : sim.nodes) {
                double d = Math.hypot(x - n.x * ZoneBounds.SCALE, z - n.z * ZoneBounds.SCALE);
                if (d < bd) { bd = d; best = n; }
            }
            return send(c, "Вы в " + x + ", " + z + ". Ближайшая локация: " + best.name + " (" + (int) bd + " блоков), владелец: "
                    + (best.owner < 0 ? "никто" : sim.factions.get(best.owner).name)
                    + ".\nДо забора: " + b.distanceToFence(x, z) + " блоков.");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return send(c, "Команда только для игрока.");
        }
    }

    private static int events(CommandContext<CommandSourceStack> c, int count) {
        List<String> all = new ArrayList<>(sim(c).events);
        int from = Math.max(0, all.size() - count);
        String text = all.isEmpty() ? "Пока ничего не произошло." : String.join("\n", all.subList(from, all.size()));
        return send(c, text);
    }

    private static String nodeNames(Simulation sim) {
        StringBuilder sb = new StringBuilder();
        for (Node n : sim.nodes) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(n.name);
        }
        return sb.toString();
    }

    private static int send(CommandContext<CommandSourceStack> c, String text) {
        for (String line : text.split("\n")) {
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /** /zone build <тип> <уровень>: поставить постройку готовой карты перед игроком (для проверки уровней развития). */
    private static int buildHere(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer pl = c.getSource().getPlayerOrException();
        String kind = StringArgumentType.getString(c, "kind");
        int lvl = IntegerArgumentType.getInteger(c, "level");
        ru.stalkerzone.zonemap.Features.Placement probe = new ru.stalkerzone.zonemap.Features.Placement(kind, 0, 0, 0, 0, 0, 1);
        int half = Math.max(4, ru.stalkerzone.zonemap.build.Structures.reach(probe) - 8);
        // ставим объект впереди игрока, повёрнутым «лицом» к нему
        net.minecraft.core.Direction f = pl.getDirection();
        int x = pl.getBlockX() + f.getStepX() * (half + 3), z = pl.getBlockZ() + f.getStepZ() * (half + 3);
        int rot = switch (f) { case NORTH -> 0; case EAST -> 90; case SOUTH -> 180; default -> 270; };
        long seed = x * 31L + z * 17L;
        ru.stalkerzone.zonemap.Features.Placement p = new ru.stalkerzone.zonemap.Features.Placement(kind, x, z, rot, half * 2, half * 2, seed);
        int n = ru.stalkerzone.world.ZoneMapPlacer.build(pl.serverLevel(), p, lvl, true);
        return send(c, "Построено «" + kind + "», уровень " + lvl + ": " + n + " блоков в " + x + ", " + z);
    }

    /** /zone construct <тип> <с уровня> <до уровня>: бригада на глазах достраивает постройку перед игроком. */
    private static int constructHere(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer pl = c.getSource().getPlayerOrException();
        String kind = StringArgumentType.getString(c, "kind");
        int from = IntegerArgumentType.getInteger(c, "from"), to = IntegerArgumentType.getInteger(c, "to");
        ru.stalkerzone.zonemap.Features.Placement probe = new ru.stalkerzone.zonemap.Features.Placement(kind, 0, 0, 0, 0, 0, 1);
        int half = Math.max(4, ru.stalkerzone.zonemap.build.Structures.reach(probe) - 8);
        net.minecraft.core.Direction f = pl.getDirection();
        int x = pl.getBlockX() + f.getStepX() * (half + 3), z = pl.getBlockZ() + f.getStepZ() * (half + 3);
        int rot = switch (f) { case NORTH -> 0; case EAST -> 90; case SOUTH -> 180; default -> 270; };
        ru.stalkerzone.zonemap.Features.Placement p = new ru.stalkerzone.zonemap.Features.Placement(kind, x, z, rot, half * 2, half * 2, x * 31L + z * 17L);
        int n = ru.stalkerzone.world.ZoneMapPlacer.construct(pl.serverLevel(), p, from, to, ru.stalkerzone.sim.ZoneScenario.LONERS);
        return send(c, "Бригада начала стройку «" + kind + "» с уровня " + from + " до " + to + ": " + n + " блоков, около "
                + (n / 40 + 1) + " с.");
    }
}
