package ru.stalkerzone.npc;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.compat.CompatConfig;
import ru.stalkerzone.compat.GunQuality;
import ru.stalkerzone.compat.Tacz;
import ru.stalkerzone.gear.Backpack;
import ru.stalkerzone.gear.Suit;
import ru.stalkerzone.gear.SuitItem;
import ru.stalkerzone.item.GoodItem;
import ru.stalkerzone.item.ModItems;
import ru.stalkerzone.item.RubleItem;
import ru.stalkerzone.net.Net;
import ru.stalkerzone.net.Net.TraderView;
import ru.stalkerzone.pda.PdaService;
import ru.stalkerzone.sim.Contract;
import ru.stalkerzone.sim.Good;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.PdaSnapshot;
import ru.stalkerzone.sim.Simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * П-07: окно торговца (спецификация — docs/claude/04-interfaces.md, раздел 1; набросок — docs/ui/p07-trader.png).
 * Сервер авторитетный: окно присылает {@link Net.TraderAction}, здесь проверяется, что игрок рядом с живым торговцем,
 * сделка проводится через {@link TradeService} (касса и склад торговца в симуляции, учёт Ledger), и клиент получает
 * новое состояние {@link TraderView}. Цены и «итог до подтверждения» — пробные сделки симуляции (quoteBuy/quoteSell).
 */
public final class TraderWindow {
    private TraderWindow() {}

    public static final int REFRESH = 0, QUOTE = 1, BUY = 2, SELL = 3, REPAIR = 4, TAKE = 5, ABANDON = 6, DELIVER = 7, CLAIM = 8;
    public static final int SIDE_BUY = 0, SIDE_SELL = 1;
    /** Дальше этого окно не работает (как у /zone buy). */
    public static final double REACH = 8;
    private static final long NO_LIMIT = Long.MAX_VALUE / 4;
    /** Начало имени строки «@»: клиент подставляет название предмета на языке игрока (на сервере имена английские). */
    public static final String ITEM_NAME = "@";

    // ------------------------------------------------------------------ каталог торговца

    private enum Kind { GOOD, EQUIP, STACK }

    /** Что торговец продаёт: товар симуляции поштучно или вещь целиком за per единиц товара good. */
    private record Offer(Kind kind, ItemStack icon, String name, Good good, int per) {}

    private static List<Offer> catalog(ServerPlayer p, Simulation sim, Node n) {
        List<Offer> out = new ArrayList<>();
        ItemStack hand = p.getMainHandItem();
        CompatConfig.GunSpec held = Tacz.isGun(hand) ? CompatConfig.GUNS.get(Tacz.gunId(hand)) : null;
        for (Good g : Good.ALL) {
            Item it = ModItems.of(g);
            if (it == null) continue;
            String name = ITEM_NAME;
            // с оружием TaCZ в руке торговец отсыпает патроны его калибра (как в TradeService.buy)
            if (held != null && g.isAmmo()) name = ITEM_NAME + " " + held.ammo() + " ×" + CompatConfig.roundsPerUnit(held.ammo());
            out.add(new Offer(Kind.GOOD, new ItemStack(it), name, g, 1));
        }
        String owner = n.owner >= 0 && n.owner < sim.factions.size() ? sim.factions.get(n.owner).name : "";
        for (Suit s : Suit.ALL) {
            if (s == Suit.MONOLITH || (s.exclusive() && !owner.equals(s.faction))) continue;
            equip(out, ModItems.SUITS[s.ordinal()].get(), s.origin.good, s.gearUnits);
        }
        for (Backpack b : Backpack.ALL) equip(out, ModItems.BACKPACKS[b.ordinal()].get(), Good.GEAR, b.gearUnits);
        equip(out, ModItems.PDA.get(), Good.GEAR, 1);
        equip(out, ModItems.CONTAINER_LEAD.get(), Good.GEAR, 2);
        equip(out, ModItems.CONTAINER_PORCELAIN.get(), Good.GEAR, 4);
        int[] detUnits = {1, 3, 6, 12};
        for (int i = 0; i < ModItems.DETECTORS.length && i < detUnits.length; i++) equip(out, ModItems.DETECTORS[i].get(), Good.GEAR, detUnits[i]);
        equip(out, ModItems.FLASH_DRIVE.get(), Good.GEAR, 1);
        equip(out, ModItems.DATA_DISK.get(), Good.GEAR, 2);
        ItemStack bolts = new ItemStack(ModItems.BOLT.get(), 32);
        out.add(new Offer(Kind.STACK, bolts, ITEM_NAME + " ×32", Good.PARTS, 1));
        if (Tacz.loaded()) {
            for (CompatConfig.GunSpec g : CompatConfig.GUNS.values()) {
                for (boolean handMade : new boolean[] {true, false}) {
                    ItemStack gun = GunQuality.markGun(Tacz.gun(g.id(), g.mode(), g.mag()), handMade);
                    if (gun.isEmpty()) continue;
                    out.add(new Offer(Kind.STACK, gun, ITEM_NAME + (handMade ? " (кустарный)" : " (заводской)"),
                            handMade ? Good.GEAR : Good.GEAR_PRO, GunQuality.units(g, handMade)));
                }
            }
        }
        return out;
    }

    private static void equip(List<Offer> out, Item item, Good good, int units) {
        out.add(new Offer(Kind.EQUIP, new ItemStack(item), ITEM_NAME, good, units));
    }

    // ------------------------------------------------------------------ что торговец даст за вещь игрока

    /** Продажа вещи из слота: per предметов в одной единице сделки, good × units — сколько товара уходит торговцу. */
    private record Sale(Good good, int units, int per, String why) {
        boolean ok() { return why.isEmpty(); }
    }

    private static Sale sale(ItemStack s) {
        if (s.isEmpty()) return new Sale(null, 0, 1, "пусто");
        if (s.getItem() instanceof RubleItem || s.getItem() == ModItems.PDA.get()) return new Sale(null, 0, 1, "не продаётся");
        if (Tacz.isAmmo(s)) {
            int per = CompatConfig.roundsPerUnit(Tacz.ammoId(s));
            return s.getCount() < per ? new Sale(null, 0, per, "пачками по " + per) : new Sale(GunQuality.ammoGood(s), 1, per, "");
        }
        if (s.getItem() instanceof GoodItem gi) return new Sale(gi.good, gi.units(), 1, "");
        int units = TradeService.gearUnits(s);
        if (units > 0) {
            double cond = SuitItem.condition(s);
            int worth = (int) Math.floor(units * cond * 0.8);
            if (worth == 0 && cond >= 0.4) worth = 1;   // как TradeService.sellEquipment
            return worth <= 0 ? new Sale(null, 0, 1, "почините") : new Sale(TradeService.gearGood(s), worth, 1, "");
        }
        return new Sale(null, 0, 1, "не берёт");
    }

    // ------------------------------------------------------------------ открыть и обновить

    /** ПКМ по торговцу: открыть окно. */
    public static void open(ServerPlayer p, StalkerEntity trader) {
        Net.sendTo(p, view(p, trader, true, null, ""));
    }

    public static void action(ServerPlayer p, Net.TraderAction a) {
        Net.sendTo(p, handle(p, a));
    }

    /** Провести просьбу окна и вернуть новое состояние окна (или «закрыть» с причиной). */
    public static TraderView handle(ServerPlayer p, Net.TraderAction a) {
        Entity e = p.level().getEntity(a.entity);
        if (!(e instanceof StalkerEntity t) || !t.isTrader() || !t.isAlive() || p.distanceTo(t) > REACH || !p.isAlive())
            return closed(a.entity, "Торговец далеко: подойдите ближе.");
        ZoneData data = ZoneData.get(p.getServer());
        Node n = node(data.sim, t);
        if (n == null) return closed(a.entity, "Здесь не торгуют.");
        String me = PdaService.id(p);
        List<String> msgs = new ArrayList<>();
        TraderView.Quote quote = null;
        int qty = Math.max(1, Math.min(a.qty, 10_000));
        switch (a.action) {
            case QUOTE -> quote = quote(p, data.sim, n, a.side, a.index, qty);
            case BUY -> {
                msgs.addAll(TradeService.captured(() -> buy(p, data, n, a.index, qty)));
                quote = quote(p, data.sim, n, SIDE_BUY, a.index, qty);
            }
            case SELL -> {
                msgs.addAll(TradeService.captured(() -> sell(p, data, n, a.index, qty)));
                quote = quote(p, data.sim, n, SIDE_SELL, a.index, qty);
            }
            case REPAIR -> {
                if (a.index >= 0 && a.index < p.getInventory().items.size())
                    msgs.addAll(TradeService.captured(() -> TradeService.repair(p, data, n, p.getInventory().items.get(a.index))));
            }
            case TAKE, ABANDON, DELIVER, CLAIM -> msgs.add(contract(p, data, n, me, a.action, a.index));
            default -> { }
        }
        data.setDirty();
        return view(p, t, false, quote, String.join(" ", msgs));
    }

    private static Node node(Simulation sim, StalkerEntity t) {
        int h = t.homeNode();
        return sim != null && h >= 0 && h < sim.nodes.size() && sim.nodes.get(h).trader ? sim.nodes.get(h) : null;
    }

    private static TraderView closed(int entity, String why) {
        return new TraderView(false, entity, "", "", why, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), null);
    }

    /** Состояние окна: товары, рюкзак игрока с ценами выкупа, ремонт, контракты, итог сделки. */
    public static TraderView view(ServerPlayer p, StalkerEntity t, boolean open, TraderView.Quote quote, String message) {
        ZoneData data = ZoneData.get(p.getServer());
        Simulation sim = data.sim;
        Node n = node(sim, t);
        if (n == null) return closed(t.getId(), "Здесь не торгуют.");
        String note = PlayerFaction.tradeNote(p, sim, n);
        if (note == null) note = "цены обычные";
        String title = t.getName().getString() + " — торговец · " + n.name;
        List<TraderView.Row> offers = new ArrayList<>(), items = new ArrayList<>(), repairs = new ArrayList<>();
        PlayerFaction.applyTrade(p, sim, n);
        try {
            List<Offer> cat = catalog(p, sim, n);
            for (int i = 0; i < cat.size(); i++) {
                Offer o = cat.get(i);
                int stock = n.market.get(o.good()) / o.per();
                if (o.kind() != Kind.GOOD && stock == 0) continue;   // снаряжения нет на складе — не показываем
                Simulation.Deal one = sim.quoteBuy(n, o.good(), o.per(), 1, NO_LIMIT);
                long price = one.qty() > 0 ? one.money() : (long) o.per() * sim.sellPrice(n, o.good());
                offers.add(new TraderView.Row(i, o.icon(), o.name(), stock, price, stock > 0 ? "" : "нет", stock > 0));
            }
            List<ItemStack> inv = p.getInventory().items;
            for (int slot = 0; slot < inv.size(); slot++) {
                ItemStack s = inv.get(slot);
                if (s.isEmpty()) continue;
                Sale sl = sale(s);
                long price = 0;
                String why = sl.why();
                if (sl.ok()) {
                    Simulation.Deal one = sim.quoteSell(n, sl.good(), sl.units(), 1);
                    price = one.money();
                    if (one.qty() == 0) why = n.market.money <= 0 ? "нет денег" : "не возьмёт";
                }
                String name = ITEM_NAME + (sl.per() > 1 && sl.ok() ? " (пачка ×" + sl.per() + ")" : "");
                items.add(new TraderView.Row(slot, s.copyWithCount(1), name, s.getCount() / Math.max(1, sl.per()), price, why, why.isEmpty()));
                if (s.isDamageableItem() && s.getDamageValue() > 0) {
                    long cost = repairCost(sim, n, s);
                    repairs.add(new TraderView.Row(slot, s.copyWithCount(1), ITEM_NAME,
                            (int) Math.round(100.0 * (1 - s.getDamageValue() / (double) s.getMaxDamage())), cost, cost < 0 ? "нет деталей" : "", cost >= 0));
                }
            }
        } finally {
            PlayerFaction.resetTrade(sim);
        }
        List<TraderView.ContractRow> contracts = new ArrayList<>();
        String me = PdaService.id(p);
        for (Contract c : sim.contracts.list) {
            int f = PdaSnapshot.actions(c, me);
            if (f < 0) continue;
            boolean mine = c.taker == Contract.PLAYER && c.player.equals(me);
            if (!mine && c.issuer != n.owner && c.node != n.id) continue;
            contracts.add(new TraderView.ContractRow(c.id, c.describe(sim), f));
        }
        return new TraderView(open, t.getId(), title, note, message, n.market.money, TradeService.countRubles(p),
                ru.stalkerzone.gear.Weight.total(p), ru.stalkerzone.gear.Weight.limit(p), offers, items, repairs, contracts, quote);
    }

    /** Цена ремонта как в TradeService.repair: детали со склада + работа; −1 — деталей не хватает. */
    private static long repairCost(Simulation sim, Node n, ItemStack s) {
        double wear = s.getDamageValue() / (double) s.getMaxDamage();
        int units = Math.max(1, TradeService.gearUnits(s));
        int parts = Math.max(1, (int) Math.ceil(wear * units * 4));
        Simulation.Deal d = sim.quoteBuy(n, Good.PARTS, parts, 1, NO_LIMIT);
        return d.qty() == 0 ? -1 : d.money() + 15L * parts;
    }

    // ------------------------------------------------------------------ итог сделки до подтверждения

    private static TraderView.Quote quote(ServerPlayer p, Simulation sim, Node n, int side, int index, int qty) {
        PlayerFaction.applyTrade(p, sim, n);
        try {
            if (side == SIDE_BUY) {
                List<Offer> cat = catalog(p, sim, n);
                if (index < 0 || index >= cat.size()) return null;
                Offer o = cat.get(index);
                long cash = TradeService.countRubles(p);
                Simulation.Deal d = sim.quoteBuy(n, o.good(), o.per(), qty, cash);
                String why = d.qty() >= qty ? "" : n.market.get(o.good()) / o.per() < qty ? "на складе меньше" : "не хватает денег";
                return new TraderView.Quote(side, index, qty, d.qty(), d.money(), why);
            }
            List<ItemStack> inv = p.getInventory().items;
            if (index < 0 || index >= inv.size()) return null;
            ItemStack s = inv.get(index);
            Sale sl = sale(s);
            if (!sl.ok()) return new TraderView.Quote(side, index, qty, 0, 0, sl.why());
            int have = s.getCount() / sl.per();
            Simulation.Deal d = sim.quoteSell(n, sl.good(), sl.units(), Math.min(qty, have));
            String why = d.qty() >= qty ? "" : have < qty ? "у вас меньше" : "у торговца нет денег";
            return new TraderView.Quote(side, index, qty, d.qty(), d.money(), why);
        } finally {
            PlayerFaction.resetTrade(sim);
        }
    }

    // ------------------------------------------------------------------ сделки

    private static void buy(ServerPlayer p, ZoneData data, Node n, int index, int qty) {
        List<Offer> cat = catalog(p, data.sim, n);
        if (index < 0 || index >= cat.size()) return;
        Offer o = cat.get(index);
        switch (o.kind()) {
            case GOOD -> TradeService.buy(p, data, n, o.good(), qty);
            case EQUIP -> { for (int i = 0; i < qty && TradeService.buyEquipment(p, data, n, o.icon().getItem(), o.good(), o.per()); i++) { } }
            case STACK -> { for (int i = 0; i < qty && TradeService.buyStack(p, data, n, o.icon().copy(), o.good(), o.per()); i++) { } }
        }
    }

    /** Продать qty единиц сделки из слота: товар — через кассу симуляции, вещь из нескольких единиц — целиком. */
    private static void sell(ServerPlayer p, ZoneData data, Node n, int slot, int qty) {
        List<ItemStack> inv = p.getInventory().items;
        if (slot < 0 || slot >= inv.size()) return;
        ItemStack s = inv.get(slot);
        Sale sl = sale(s);
        String name = s.getHoverName().getString();
        if (!sl.ok()) { TradeService.say(p, "«" + name + "»: " + sl.why() + "."); return; }
        int want = Math.min(qty, s.getCount() / sl.per());
        PlayerFaction.applyTrade(p, data.sim, n);
        try {
            if (TradeService.gearUnits(s) > 0 && !(s.getItem() instanceof GoodItem)) {
                // вещи (костюм, рюкзак, оружие) — по одной: у каждой своё состояние
                int sold = 0;
                long money = 0;
                while (sold < want && !s.isEmpty()) {
                    Sale one = sale(s);
                    if (!one.ok()) break;
                    Simulation.Deal d = data.sim.playerSellAll(n, one.good(), one.units());
                    if (d.qty() == 0) break;
                    s.shrink(1);
                    money += d.money();
                    sold++;
                }
                finish(p, data, name, sold, money);
                return;
            }
            Simulation.Deal d = data.sim.playerSellPacks(n, sl.good(), sl.units(), want);
            s.shrink(d.qty() * sl.per());
            finish(p, data, name + (sl.per() > 1 ? " (пачки ×" + sl.per() + ")" : ""), d.qty(), d.money());
        } finally {
            PlayerFaction.resetTrade(data.sim);
        }
    }

    private static void finish(ServerPlayer p, ZoneData data, String name, int sold, long money) {
        if (sold == 0) { TradeService.say(p, "Торговец сейчас не может заплатить за «" + name + "»."); return; }
        TradeService.giveRubles(p, money);
        data.setDirty();
        TradeService.say(p, "Продано: " + name + " x" + sold + " за " + money + " руб.");
    }

    private static String contract(ServerPlayer p, ZoneData data, Node n, String me, int action, int id) {
        int pda = switch (action) {
            case TAKE -> PdaService.TAKE;
            case ABANDON -> PdaService.ABANDON;
            case DELIVER -> PdaService.DELIVER;
            default -> PdaService.CLAIM;
        };
        return PdaService.actionResult(p, pda, id);
    }
}
