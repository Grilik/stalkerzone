package ru.stalkerzone.pda;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.item.DataDriveItem;
import ru.stalkerzone.item.GoodItem;
import ru.stalkerzone.item.ModItems;
import ru.stalkerzone.net.Net;
import ru.stalkerzone.npc.TradeService;
import ru.stalkerzone.sim.Contract;
import ru.stalkerzone.sim.ContractBoard;
import ru.stalkerzone.sim.Faction;
import ru.stalkerzone.sim.Lab;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.PdaSnapshot;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.ZoneBounds;
import ru.stalkerzone.world.ZoneWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Серверная часть КПК: собирает снимок Зоны для игрока и выполняет действия с контрактами.
 * Формат снимка: строки, поля через табуляцию, первое поле — тип строки.
 */
public final class PdaService {
    public static final int REFRESH = 0, TAKE = 1, ABANDON = 2, DELIVER = 3, CLAIM = 4, LAB_DATA = 5;
    /** Маска доступных действий — общая с форматом снимка (PdaSnapshot), чтобы клиент и тест не разошлись. */
    public static final int CAN_TAKE = PdaSnapshot.CAN_TAKE, CAN_ABANDON = PdaSnapshot.CAN_ABANDON,
            CAN_DELIVER = PdaSnapshot.CAN_DELIVER, CAN_CLAIM = PdaSnapshot.CAN_CLAIM;
    /** Сдать можно в этом радиусе от центра точки, блоков. */
    public static final int TURN_IN_RADIUS = 45;

    private PdaService() {}

    public static String id(ServerPlayer p) { return p.getGameProfile().getName(); }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ');
    }

    public static void open(ServerPlayer p, boolean show) {
        Net.sendTo(p, new Net.PdaSnapshot(snapshot(p), show));
    }

    public static boolean nearNode(ServerPlayer p, Node n, int radius) {
        return Math.hypot(p.getX() - n.x * ZoneBounds.SCALE, p.getZ() - n.z * ZoneBounds.SCALE) <= radius;
    }

    public static String snapshot(ServerPlayer p) {
        ZoneData data = ZoneData.get(p.getServer());
        Simulation s = data.sim;
        String me = id(p);
        StringBuilder sb = new StringBuilder(PdaSnapshot.build(s, me, s.day, data.tickCounter, p.getX(), p.getZ(),
                TradeService.countRubles(p), ZoneWorld.bounds(), tollFor(p, s)));
        appendReputation(p, s, sb);
        appendShelters(s, sb);
        return sb.toString();
    }

    /** Пошлина за платный проход для игрока — та же цена, что берёт пост на мосту (карточка 012). */
    private static long tollFor(ServerPlayer p, Simulation s) {
        int node = s.tollCrossing;
        if (node < 0 || node >= s.nodes.size()) return 0;
        int owner = s.nodes.get(node).owner;
        if (owner < 0) return 0;
        return s.tollFor(ru.stalkerzone.npc.Reputation.get(p, owner), ru.stalkerzone.npc.PlayerFaction.rank(p));
    }

    /** Отношения: репутация игрока у каждой группировки среза (вкладка «Отношения»). */
    private static void appendReputation(ServerPlayer p, Simulation s, StringBuilder sb) {
        for (Faction f : s.factions) {
            sb.append("Y\t").append(f.id).append('\t').append(ru.stalkerzone.npc.Reputation.get(p, f.id)).append('\n');
        }
    }

    /** Укрытия от выброса из файла точек уровня (карточка 013): метки на карте КПК. */
    private static void appendShelters(Simulation s, StringBuilder sb) {
        String level = ru.stalkerzone.world.LevelPoints.levelForScenario(s.scenario);
        if (level == null) return;
        for (Node n : s.nodes) {
            com.google.gson.JsonObject pts = ru.stalkerzone.world.LevelPoints.nodePoints(level, n.id);
            if (pts == null) continue;
            for (int[] v : ru.stalkerzone.world.LevelPoints.readVecs(pts, "shelter")) {
                if (v.length < 3) continue;
                sb.append("W\t").append(v[0]).append('\t').append(v[2]).append('\t').append(clean(n.name)).append('\n');
            }
        }
    }


    // ------------------------------------------------------------------ действия

    public static void action(ServerPlayer p, int action, int id) {
        ZoneData data = ZoneData.get(p.getServer());
        Simulation s = data.sim;
        String me = id(p);
        String msg = null;
        switch (action) {
            case TAKE -> {
                String err = s.contracts.take(s, id, me);
                msg = err != null ? err : "Контракт #" + id + " взят. Подробности на вкладке «Задания».";
            }
            case ABANDON -> {
                String err = s.contracts.abandon(s, id, me);
                msg = err != null ? err : "Вы отказались от контракта #" + id;
            }
            case DELIVER -> msg = deliver(p, data, id);
            case CLAIM -> {
                ru.stalkerzone.sim.Contract cc = s.contracts.byId(id);
                ContractBoard.Reward r = s.contracts.claim(s, id, me);
                if (r == null) msg = "Награда недоступна";
                else {
                    if (cc != null) ru.stalkerzone.npc.PlayerFaction.contractDone(p, s, cc.issuer);
                    give(p, r);
                    msg = "Награда получена: " + describe(r);
                }
            }
            case LAB_DATA -> msg = deliverLabData(p, data);
            default -> { }
        }
        data.setDirty();
        if (msg != null) p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.YELLOW));
        open(p, false);
    }

    public static String describe(ContractBoard.Reward r) {
        return r.money() + " руб." + (r.qty() > 0 ? " и " + r.good().ru.toLowerCase() + " " + r.qty() + " шт." : "");
    }

    /** Выдать награду: рубли и товар в инвентарь (что не влезло, падает под ноги). */
    public static void give(ServerPlayer p, ContractBoard.Reward r) {
        if (r == null) return;
        TradeService.giveRubles(p, r.money());
        int q = r.qty();
        List<ItemStack> stacks = new ArrayList<>();
        while (q > 0) {
            int c = Math.min(64, q);
            stacks.add(new ItemStack(ModItems.of(r.good()), c));
            q -= c;
        }
        TradeService.giveItems(p, stacks);
    }

    /** Сдать товар по контракту: нужно стоять у точки сдачи и иметь нужное количество. */
    private static String deliver(ServerPlayer p, ZoneData data, int id) {
        Simulation s = data.sim;
        Contract c = s.contracts.byId(id);
        if (c == null || c.taker != Contract.PLAYER || !c.player.equals(id(p)) || c.status != Contract.Status.TAKEN) return "Это не ваш контракт";
        if (c.type == Contract.Type.LAB_DATA) return deliverLabData(p, data);
        if (c.type != Contract.Type.FETCH_ARTIFACT && c.type != Contract.Type.SUPPLY) return "Этот контракт закрывается сам, когда цель выполнена";
        Node n = s.nodes.get(c.node);
        if (!nearNode(p, n, TURN_IN_RADIUS)) return "Сдать можно только на «" + n.name + "» (ближе " + TURN_IN_RADIUS + " блоков к центру)";
        Inventory inv = p.getInventory();
        int have = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof GoodItem gi && gi.good == c.good) have += gi.units() * st.getCount();
        }
        if (have < c.qty) return "Не хватает: нужно " + c.qty + " ед. («" + c.good.ru + "»), у вас " + have;
        int need = c.qty;
        for (int i = 0; i < inv.getContainerSize() && need > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!(st.getItem() instanceof GoodItem gi) || gi.good != c.good) continue;
            while (!st.isEmpty() && need > 0) {
                st.shrink(1);
                need -= gi.units();
            }
        }
        ContractBoard.Reward r = s.contracts.deliver(s, id, id(p), c.qty);
        if (r == null) return "Сдать не удалось";
        ru.stalkerzone.npc.PlayerFaction.contractDone(p, s, c.issuer);
        give(p, r);
        return "Контракт #" + id + " выполнен. Награда: " + describe(r);
    }

    /** Сдать учёным все накопители с данными из инвентаря. Нужно быть у лагеря учёных. */
    public static String deliverLabData(ServerPlayer p, ZoneData data) {
        Simulation s = data.sim;
        Faction sci = s.scientists();
        if (sci == null) return "Учёных в Зоне нет";
        Node base = s.nodes.get(sci.baseNode);
        if (!nearNode(p, base, TURN_IN_RADIUS)) return "Данные принимают учёные на «" + base.name + "»";
        Inventory inv = p.getInventory();
        long total = 0;
        int parts = 0;
        StringBuilder notes = new StringBuilder();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!(st.getItem() instanceof DataDriveItem) || !DataDriveItem.hasData(st)) continue;
            int labId = DataDriveItem.lab(st);
            if (labId < 0 || labId >= s.labs.labs.size()) continue;
            Lab l = s.labs.labs.get(labId);
            long r = s.labs.playerDeliver(s, l, DataDriveItem.part(st), DataDriveItem.cycle(st));
            st.removeTagKey("LabData");
            if (r == -1) { notes.append(" Данные «").append(l.name).append("» устарели: Монолит уничтожил прежние исследования."); continue; }
            if (r == -2) { notes.append(" Эти данные «").append(l.name).append("» учёные уже изучили."); continue; }
            total += r;
            parts++;
            ContractBoard.Reward cr = s.contracts.labDelivered(s, labId, id(p));
            if (cr != null) {
                give(p, cr);
                notes.append(" Контракт на «").append(l.name).append("» закрыт: ").append(describe(cr)).append('.');
            }
        }
        if (parts == 0 && notes.length() == 0) return "У вас нет накопителей с данными";
        TradeService.giveRubles(p, total);
        return (parts > 0 ? "Учёные приняли данные (" + parts + " шт.), премия институтов " + total + " руб." : "") + notes;
    }
}
