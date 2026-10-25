package ru.stalkerzone.pda;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.item.DataDriveItem;
import ru.stalkerzone.item.GoodItem;
import ru.stalkerzone.item.ModItems;
import ru.stalkerzone.mutant.MutantManager;
import ru.stalkerzone.net.Net;
import ru.stalkerzone.npc.TradeService;
import ru.stalkerzone.sim.Anomaly;
import ru.stalkerzone.sim.Contract;
import ru.stalkerzone.sim.ContractBoard;
import ru.stalkerzone.sim.Faction;
import ru.stalkerzone.sim.Good;
import ru.stalkerzone.sim.Lab;
import ru.stalkerzone.sim.Lair;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Person;
import ru.stalkerzone.sim.Politics;
import ru.stalkerzone.sim.Simulation;
import ru.stalkerzone.world.ZoneBounds;
import ru.stalkerzone.world.ZoneWorld;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Серверная часть КПК: собирает снимок Зоны для игрока и выполняет действия с контрактами.
 * Формат снимка: строки, поля через табуляцию, первое поле — тип строки.
 */
public final class PdaService {
    public static final int REFRESH = 0, TAKE = 1, ABANDON = 2, DELIVER = 3, CLAIM = 4, LAB_DATA = 5;
    public static final int CAN_TAKE = 1, CAN_ABANDON = 2, CAN_DELIVER = 4, CAN_CLAIM = 8;
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
        StringBuilder sb = new StringBuilder();
        String me = id(p);
        ZoneBounds b = ZoneWorld.bounds();
        sb.append("D\t").append(s.day).append('\t').append(data.tickCounter).append('\n');
        sb.append("P\t").append((int) p.getX()).append('\t').append((int) p.getZ()).append('\t').append(clean(me)).append('\t')
                .append(TradeService.countRubles(p)).append('\n');
        sb.append("B\t").append(b.minX).append('\t').append(b.minZ).append('\t').append(b.maxX).append('\t').append(b.maxZ).append('\n');
        Politics.Meeting m = s.politics.meeting;
        for (Node n : s.nodes) {
            StringBuilder fl = new StringBuilder();
            for (Faction f : s.factions) if (f.phase == ru.stalkerzone.sim.Phase.WAR && f.warNode == n.id) { fl.append('w'); break; }
            if (s.lairAt(n.id) != null) fl.append('l');
            if (s.labs.at(n.id) != null) fl.append('b');
            if (n.trader) fl.append('t');
            if (m != null && m.venue == n.id) fl.append('m');
            for (Faction f : s.factions) if (f.baseNode == n.id && n.owner == f.id) { fl.append('h'); break; }
            sb.append("N\t").append(n.id).append('\t').append(clean(n.name)).append('\t').append(n.x * ZoneBounds.SCALE).append('\t')
                    .append(n.z * ZoneBounds.SCALE).append('\t').append(n.owner).append('\t').append(n.type.name()).append('\t')
                    .append(fl.length() == 0 ? "-" : fl).append('\n');
        }
        for (Node a : s.nodes) for (Node c : s.nodes) if (a.id < c.id && s.graph.edgeDays(a.id, c.id) > 0) sb.append("G\t").append(a.id).append('\t').append(c.id).append('\n');
        for (Faction f : s.factions) {
            Person l = s.people.leader(f.id);
            sb.append("F\t").append(f.id).append('\t').append(clean(f.name)).append('\t').append(f.members).append('\t').append(f.wounded).append('\t')
                    .append(phaseRu(f)).append('\t').append(clean(l == null ? "нет" : l.name + " (" + l.rankRu() + ")")).append('\t')
                    .append((int) f.exp).append('\t').append((int) f.stress).append('\t').append((int) (f.unity * 100)).append('\t')
                    .append(s.ownedCount(f)).append('\n');
            StringBuilder h = new StringBuilder();
            for (Faction o : s.factions) h.append(h.length() > 0 ? ";" : "").append(o.id == f.id ? -1 : (int) Math.round(s.hostilityTo(f, o.id) * 100));
            sb.append("H\t").append(f.id).append('\t').append(h).append('\n');
        }
        for (Politics.Truce t : s.politics.truces) {
            sb.append("T\t").append(clean((t.alliance ? "Союз «" : "Перемирие «") + s.factions.get(t.a).name + "» и «" + s.factions.get(t.b).name
                    + "» до дня " + t.until)).append('\n');
        }
        if (m != null) sb.append("M\t").append(clean(m.title(s))).append('\n');
        int ev = 0;
        for (Iterator<String> it = s.events.descendingIterator(); it.hasNext() && ev < 60; ev++) sb.append("E\t").append(clean(it.next())).append('\n');
        for (Contract c : s.contracts.list) {
            if (!c.active()) continue;
            boolean mine = c.taker == Contract.PLAYER && c.player.equals(me);
            if (c.taker == Contract.PLAYER && !mine) continue;
            int act = 0;
            if (c.status == Contract.Status.OPEN) act |= CAN_TAKE;
            if (mine && c.status == Contract.Status.TAKEN) {
                act |= CAN_ABANDON;
                if (c.type == Contract.Type.FETCH_ARTIFACT || c.type == Contract.Type.SUPPLY || c.type == Contract.Type.LAB_DATA) act |= CAN_DELIVER;
            }
            if (mine && c.status == Contract.Status.READY) act |= CAN_CLAIM;
            sb.append("C\t").append(c.id).append('\t').append(c.status.name()).append('\t').append(mine ? 1 : 0).append('\t').append(act).append('\t')
                    .append(c.node >= 0 ? s.nodes.get(c.node).x * ZoneBounds.SCALE : 0).append('\t').append(c.node >= 0 ? s.nodes.get(c.node).z * ZoneBounds.SCALE : 0)
                    .append('\t').append(clean(c.describe(s))).append('\n');
        }
        for (Lab l : s.labs.labs) {
            Node n = s.nodes.get(l.node);
            sb.append("L\t").append(l.id).append('\t').append(clean(l.name)).append('\t').append(clean(n.name)).append('\t').append(l.tier).append('\t')
                    .append(l.recoveredCount()).append('\t').append(l.parts).append('\t').append(l.deliveredCount()).append('\t')
                    .append(s.labs.open(s, l) ? 1 : 0).append('\t').append(clean(l.lore)).append('\t').append(l.playerReward()).append('\t')
                    .append(n.x * ZoneBounds.SCALE + l.dx).append('\t').append(n.z * ZoneBounds.SCALE + l.dz).append('\n');
        }
        sb.append("R\t").append((int) s.scorcher.research).append('\t').append((int) s.scorcher.psiTech).append('\t').append(s.labs.cycle + 1)
                .append('\t').append(s.scorcher.disabled ? 1 : 0).append('\t').append(s.monolithActive ? 1 : 0).append('\n');
        // аномалии: известные разведке точки и всё в 60 блоках от игрока
        for (Anomaly a : s.anomalies) {
            Node n = s.nodes.get(a.node);
            int ax = n.x * ZoneBounds.SCALE + a.dx, az = n.z * ZoneBounds.SCALE + a.dz;
            boolean close = Math.hypot(ax - p.getX(), az - p.getZ()) < 60;
            if (!close && s.knowledgeOf(a.node) < 0.6) continue;
            sb.append("A\t").append(ax).append('\t').append(az).append('\t').append(clean(a.type.ru)).append('\t').append(s.anomalyTier(a)).append('\n');
        }
        for (Lair l : s.lairs) {
            BlockPos d = MutantManager.denXZ(s, l);
            sb.append("Z\t").append(d.getX()).append('\t').append(d.getZ()).append('\t').append(clean(l.title())).append('\t').append((int) l.strength).append('\n');
        }
        return sb.toString();
    }

    private static String phaseRu(Faction f) {
        return switch (f.phase) {
            case WAR -> "война";
            case RECOVER -> "восстановление";
            default -> "накопление";
        };
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
                ContractBoard.Reward r = s.contracts.claim(s, id, me);
                if (r == null) msg = "Награда недоступна";
                else {
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
