package ru.stalkerzone.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.item.GoodItem;
import ru.stalkerzone.item.ModItems;
import ru.stalkerzone.item.RubleItem;
import ru.stalkerzone.sim.Good;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;

/** Сделки игрока с торговцем. Всё идёт через кассу и склад торговца в симуляции, поэтому деньги и товары не берутся из воздуха. */
public final class TradeService {
    private TradeService() {}

    /** П-07: пока открыто окно торговца, итог сделки идёт в окно (строка внизу), а не в чат. */
    private static final ThreadLocal<java.util.List<String>> CAPTURE = new ThreadLocal<>();

    static void say(ServerPlayer p, String msg) {
        java.util.List<String> c = CAPTURE.get();
        if (c != null) c.add(msg);
        else p.sendSystemMessage(Component.literal(msg));
    }

    /** Выполнить сделки окна торговца, собрав их сообщения; возвращает сообщения по порядку. */
    static java.util.List<String> captured(Runnable r) {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.List<String> prev = CAPTURE.get();
        CAPTURE.set(out);
        try { r.run(); } finally { CAPTURE.set(prev); }
        return out;
    }

    public static long countRubles(ServerPlayer p) {
        long sum = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof RubleItem r) sum += (long) r.value * s.getCount();
        }
        return sum;
    }

    public static void giveItems(ServerPlayer p, Iterable<ItemStack> stacks) {
        for (ItemStack s : stacks) {
            if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false);
        }
    }

    public static void giveRubles(ServerPlayer p, long amount) {
        if (amount > 0) giveItems(p, ModItems.rubleStacks(amount));
    }

    /** Забрать у игрока amount рублей, сдачу вернуть. Вызывать, только убедившись, что денег хватает. */
    public static void takeRubles(ServerPlayer p, long amount) {
        Inventory inv = p.getInventory();
        long taken = 0;
        for (int i = 0; i < inv.getContainerSize() && taken < amount; i++) {
            ItemStack s = inv.getItem(i);
            if (!(s.getItem() instanceof RubleItem r)) continue;
            while (!s.isEmpty() && taken < amount) {
                s.shrink(1);
                taken += r.value;
            }
        }
        if (taken > amount) giveRubles(p, taken - amount);
    }

    /** Продать весь стак из руки. Штука продаётся целиком: у частей мутантов в одной штуке несколько единиц товара. */
    public static void sell(ServerPlayer p, ZoneData data, Node n, ItemStack held) {
        PlayerFaction.applyTrade(p, data.sim, n);   // v1.16: скидки своим и друзьям
        try { sell0(p, data, n, held); } finally { PlayerFaction.resetTrade(data.sim); }
    }

    private static void sell0(ServerPlayer p, ZoneData data, Node n, ItemStack held) {
        if (ru.stalkerzone.compat.Tacz.isAmmo(held)) { sellTaczAmmo(p, data, n, held); return; }
        if (!(held.getItem() instanceof GoodItem gi)) return;
        int units = gi.units();
        String name = held.getHoverName().getString();
        int sold = 0;
        long money = 0;
        while (sold < held.getCount()) {
            Simulation.Deal d = units == 1 ? data.sim.playerSell(n, gi.good, held.getCount() - sold)
                    : data.sim.playerSellAll(n, gi.good, units);
            if (d.qty() == 0) break;
            sold += units == 1 ? d.qty() : 1;
            money += d.money();
            if (units == 1) break;
        }
        if (sold == 0) {
            say(p, "Торговец сейчас не может заплатить за «" + name + "».");
            return;
        }
        held.shrink(sold);
        giveRubles(p, money);
        data.setDirty();
        say(p, "Продано: " + name + " x" + sold + " за " + money + " руб.");
    }

    public static void buy(ServerPlayer p, ZoneData data, Node n, Good g, int qty) {
        PlayerFaction.applyTrade(p, data.sim, n);   // v1.16: скидки своим и друзьям
        try { buy0(p, data, n, g, qty); } finally { PlayerFaction.resetTrade(data.sim); }
    }

    private static void buy0(ServerPlayer p, ZoneData data, Node n, Good g, int qty) {
        long cash = countRubles(p);
        Simulation.Deal d = data.sim.playerBuy(n, g, qty, cash);
        if (d.qty() == 0) {
            say(p, "Не вышло купить «" + g.ru + "»: нет на складе или не хватает денег (у вас " + cash + " руб.).");
            return;
        }
        takeRubles(p, d.money());
        ItemStack inHand = p.getMainHandItem();
        ru.stalkerzone.compat.CompatConfig.GunSpec spec = g.isAmmo() && ru.stalkerzone.compat.Tacz.isGun(inHand)
                ? ru.stalkerzone.compat.CompatConfig.GUNS.get(ru.stalkerzone.compat.Tacz.gunId(inHand)) : null;
        if (spec != null) {
            // с оружием TaCZ в руке торговец отсыпает патроны нужного калибра: кустарные или заводские
            boolean hand = g == Good.AMMO;
            int rounds = d.qty() * ru.stalkerzone.compat.CompatConfig.roundsPerUnit(spec.ammo());
            int total = rounds;
            while (rounds > 0) {
                int c = Math.min(60, rounds);
                giveItems(p, java.util.List.of(ru.stalkerzone.compat.GunQuality.markAmmo(ru.stalkerzone.compat.Tacz.ammo(spec.ammo(), c), hand)));
                rounds -= c;
            }
            data.setDirty();
            say(p, "Куплено: " + (hand ? "кустарные" : "заводские") + " патроны " + spec.ammo() + " x" + total + " за " + d.money() + " руб.");
            return;
        }
        int left = d.qty();
        while (left > 0) {
            int c = Math.min(64, left);
            giveItems(p, java.util.List.of(new ItemStack(ModItems.of(g), c)));
            left -= c;
        }
        data.setDirty();
        say(p, "Куплено: " + g.ru + " x" + d.qty() + " за " + d.money() + " руб.");
    }

    /** Патроны TaCZ: торговец берёт их как товар «Патроны» (целыми единицами). */
    public static void sellTaczAmmo(ServerPlayer p, ZoneData data, Node n, ItemStack held) {
        PlayerFaction.applyTrade(p, data.sim, n);   // v1.16: скидки своим и друзьям
        try { sellTaczAmmo0(p, data, n, held); } finally { PlayerFaction.resetTrade(data.sim); }
    }

    private static void sellTaczAmmo0(ServerPlayer p, ZoneData data, Node n, ItemStack held) {
        String ammo = ru.stalkerzone.compat.Tacz.ammoId(held);
        int per = ru.stalkerzone.compat.CompatConfig.roundsPerUnit(ammo);
        int units = held.getCount() / per;
        if (units <= 0) {
            say(p, "Слишком мало патронов: торговец берёт пачками по " + per + " шт.");
            return;
        }
        Good g = ru.stalkerzone.compat.GunQuality.ammoGood(held);
        Simulation.Deal d = data.sim.playerSell(n, g, units);
        if (d.qty() == 0) {
            say(p, "Торговец сейчас не может заплатить за патроны (или ему некуда их девать).");
            return;
        }
        held.shrink(d.qty() * per);
        giveRubles(p, d.money());
        data.setDirty();
        say(p, "Продано: патроны " + ammo + " x" + d.qty() * per + " за " + d.money() + " руб.");
    }

    /** Купить готовый предмет за единицы снаряжения со склада торговца (оружие TaCZ): good — кустарное или заводское. */
    /** @return true, если куплено. */
    public static boolean buyStack(ServerPlayer p, ZoneData data, Node n, ItemStack stack, Good good, int units) {
        PlayerFaction.applyTrade(p, data.sim, n);   // v1.16: скидки своим и друзьям
        try { return buyStack0(p, data, n, stack, good, units); } finally { PlayerFaction.resetTrade(data.sim); }
    }

    private static boolean buyStack0(ServerPlayer p, ZoneData data, Node n, ItemStack stack, Good good, int units) {
        long cash = countRubles(p);
        Simulation.Deal d = data.sim.playerBuyAll(n, good, units, cash);
        String name = stack.getHoverName().getString();
        if (d.qty() == 0) {
            say(p, "Не вышло купить «" + name + "»: нужно " + units + " ед. товара «" + good.ru + "» на складе (есть "
                    + n.market.get(good) + ") и около " + units * data.sim.sellPrice(n, good) + " руб. (у вас " + cash + ").");
            return false;
        }
        takeRubles(p, d.money());
        giveItems(p, java.util.List.of(stack));
        data.setDirty();
        say(p, "Куплено: " + name + " за " + d.money() + " руб.");
        return true;
    }

    /** Каким товаром симуляции торгуется предмет: кустарное, прототипы или заводское снаряжение. */
    public static Good gearGood(ItemStack s) {
        if (ru.stalkerzone.compat.Tacz.isGun(s)) return ru.stalkerzone.compat.GunQuality.gunGood(s);
        if (s.getItem() instanceof ru.stalkerzone.gear.SuitItem si) return si.suit.origin.good;
        return Good.GEAR;
    }

    /** Единиц снаряжения ({@link #gearGood}), которые стоит предмет (костюмы, рюкзаки, оружие TaCZ), иначе 0. */
    public static int gearUnits(ItemStack s) {
        if (ru.stalkerzone.compat.Tacz.isGun(s)) {
            ru.stalkerzone.compat.CompatConfig.GunSpec g = ru.stalkerzone.compat.CompatConfig.GUNS.get(ru.stalkerzone.compat.Tacz.gunId(s));
            return g == null ? 2 : ru.stalkerzone.compat.GunQuality.units(g, ru.stalkerzone.compat.GunQuality.handGun(s));
        }
        if (s.getItem() instanceof ru.stalkerzone.gear.SuitItem si) return si.suit.gearUnits;
        if (s.getItem() instanceof ru.stalkerzone.gear.BackpackItem bi) return bi.pack.gearUnits;
        return 0;
    }

    /** Купить костюм или рюкзак: со склада торговца уходит нужное число единиц снаряжения того же происхождения. */
    public static boolean buyEquipment(ServerPlayer p, ZoneData data, Node n, net.minecraft.world.item.Item item, int units) {
        return buyEquipment(p, data, n, item, gearGood(new ItemStack(item)), units);
    }

    /** @return true, если куплено. */
    public static boolean buyEquipment(ServerPlayer p, ZoneData data, Node n, net.minecraft.world.item.Item item, Good good, int units) {
        PlayerFaction.applyTrade(p, data.sim, n);   // v1.16: скидки своим и друзьям
        try { return buyEquipment0(p, data, n, item, good, units); } finally { PlayerFaction.resetTrade(data.sim); }
    }

    private static boolean buyEquipment0(ServerPlayer p, ZoneData data, Node n, net.minecraft.world.item.Item item, Good good, int units) {
        long cash = countRubles(p);
        Simulation.Deal d = data.sim.playerBuyAll(n, good, units, cash);
        String name = new ItemStack(item).getHoverName().getString();
        if (d.qty() == 0) {
            say(p, "Не вышло купить «" + name + "»: нужно " + units + " ед. товара «" + good.ru + "» на складе (есть "
                    + n.market.get(good) + ") и около " + units * data.sim.sellPrice(n, good) + " руб. (у вас " + cash + ").");
            return false;
        }
        takeRubles(p, d.money());
        giveItems(p, java.util.List.of(new ItemStack(item)));
        data.setDirty();
        say(p, "Куплено: " + name + " за " + d.money() + " руб.");
        return true;
    }

    /** Продать костюм или рюкзак из руки: цена зависит от износа. */
    public static void sellEquipment(ServerPlayer p, ZoneData data, Node n, ItemStack held) {
        PlayerFaction.applyTrade(p, data.sim, n);   // v1.16: скидки своим и друзьям
        try { sellEquipment0(p, data, n, held); } finally { PlayerFaction.resetTrade(data.sim); }
    }

    private static void sellEquipment0(ServerPlayer p, ZoneData data, Node n, ItemStack held) {
        int units = gearUnits(held);
        double cond = ru.stalkerzone.gear.SuitItem.condition(held);
        int worth = (int) Math.floor(units * cond * 0.8);
        if (worth == 0 && units > 0 && cond >= 0.4) worth = 1; // дорогие единицы (импорт, прототипы): одна единица за вещь в приличном состоянии
        String name = held.getHoverName().getString();
        if (worth <= 0) {
            say(p, "Торговец не берёт «" + name + "» в таком состоянии. Почините: /zone repair");
            return;
        }
        Simulation.Deal d = data.sim.playerSellAll(n, gearGood(held), worth);
        if (d.qty() == 0) {
            say(p, "У торговца не хватает денег на «" + name + "».");
            return;
        }
        held.shrink(1);
        giveRubles(p, d.money());
        data.setDirty();
        say(p, "Продано: " + name + " (состояние " + Math.round(cond * 100) + "%) за " + d.money() + " руб.");
    }

    /** Ремонт предмета в руке: тратит детали со склада торговца и плату за работу. */
    public static void repair(ServerPlayer p, ZoneData data, Node n, ItemStack held) {
        PlayerFaction.applyTrade(p, data.sim, n);   // v1.16: скидки своим и друзьям
        try { repair0(p, data, n, held); } finally { PlayerFaction.resetTrade(data.sim); }
    }

    private static void repair0(ServerPlayer p, ZoneData data, Node n, ItemStack held) {
        if (held.isEmpty() || !held.isDamageableItem() || held.getDamageValue() == 0) {
            say(p, "Возьмите в руку изношенную вещь (костюм, оружие, инструмент).");
            return;
        }
        double wear = held.getDamageValue() / (double) held.getMaxDamage();
        int units = Math.max(1, gearUnits(held));
        int parts = Math.max(1, (int) Math.ceil(wear * units * 4));
        long fee = 15L * parts;
        long cash = countRubles(p);
        Simulation.Deal d = data.sim.playerBuyAll(n, Good.PARTS, parts, Math.max(0, cash - fee));
        if (d.qty() == 0) {
            say(p, "Ремонт не вышел: нужно деталей " + parts + " (на складе " + n.market.get(Good.PARTS)
                    + ") и около " + (parts * data.sim.sellPrice(n, Good.PARTS) + fee) + " руб. (у вас " + cash + ").");
            return;
        }
        data.sim.playerPay(n, fee);
        takeRubles(p, d.money() + fee);
        held.setDamageValue(0);
        data.setDirty();
        say(p, "Починено: " + held.getHoverName().getString() + ". Деталей " + parts + ", всего " + (d.money() + fee) + " руб.");
    }
}
