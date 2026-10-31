package ru.stalkerzone.world;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import ru.stalkerzone.ZoneData;
import ru.stalkerzone.npc.PlayerFaction;
import ru.stalkerzone.npc.Reputation;
import ru.stalkerzone.sim.Faction;
import ru.stalkerzone.sim.Gate;
import ru.stalkerzone.sim.Node;
import ru.stalkerzone.sim.Simulation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Пост на мосту в мире (карточка 012): шлагбаум у региона {@code gate} платного прохода.
 * Игровой слой вокруг чистой логики {@link Gate}: сообщения, оплата (команда {@code /zone pay} или ПКМ по часовому),
 * минус репутация и короткая враждебность за проход без оплаты. Регион, владелец и цена — из данных
 * (сценарий и файл точек локации), без хардкода Кордона.
 *
 * <p>Мост может перейти к другой фракции — правила действуют от лица нового владельца: его враги не проходят.
 */
public final class BridgePost {
    /** Враждебность после прохода без оплаты держим на этом значении репутации у владельца. */
    private static final int HOSTILE_CAP = Reputation.HOSTILE;

    private static final Map<UUID, Gate.Pass> PASS = new HashMap<>();
    /** До какого тика держать игрока врагом владельца (за проход без оплаты). */
    private static final Map<UUID, Long> HOSTILE_UNTIL = new HashMap<>();
    /** Значение репутации, которое вернём, когда враждебность спадёт. */
    private static final Map<UUID, Integer> HOSTILE_KEEP = new HashMap<>();

    private BridgePost() {}

    /** Узел платного прохода из сценария (мост). -1, если прохода нет. */
    private static int tollNode(Simulation sim) { return sim.tollCrossing; }

    /** Регион gate узла из файла точек уровня или null. */
    private static int[] region(Simulation sim, int nodeId) {
        String levelId = LevelPoints.levelForScenario(sim.scenario);
        if (levelId == null) return null;
        com.google.gson.JsonObject pts = LevelPoints.nodePoints(levelId, nodeId);
        int[] g = LevelPoints.readVec(pts, "gate");
        return g != null && g.length >= 4 ? g : null;
    }

    private static Gate.Pass passFor(Simulation sim, int nodeId, UUID id) {
        int[] g = region(sim, nodeId);
        if (g == null) return null;
        Gate.Pass key = PASS.get(id);
        if (key != null && key.x0 == Math.min(g[0], g[2]) && key.z0 == Math.min(g[1], g[3])) return key;
        key = new Gate.Pass(g[0], g[1], g[2], g[3]);
        PASS.put(id, key);
        return key;
    }

    /** Вызывать раз в секунду (тик сервера). */
    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level.players().isEmpty()) return;
        ZoneData d = ZoneData.get(server);
        Simulation sim = d.sim;
        int nodeId = tollNode(sim);
        if (nodeId < 0 || nodeId >= sim.nodes.size()) return;
        Node n = sim.nodes.get(nodeId);
        if (n.owner < 0) return;                       // мост ничей — поста нет
        Faction owner = sim.factions.get(n.owner);
        if (region(sim, nodeId) == null) return;       // у локации не задан регион прохода
        long t = server.getTickCount();

        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            UUID id = p.getUUID();
            restore(p, sim, t);                        // спавшая враждебность — вернуть репутацию
            Gate.Pass pass = passFor(sim, nodeId, id);
            if (pass == null) continue;

            int mine = PlayerFaction.faction(p);
            boolean friend = mine == owner.id;
            if (friend) pass.pay(t);                   // свои проходят без пошлины, пока у поста
            boolean blocked = !friend && Gate.enemyOf(sim, owner, mine);
            Gate.Event ev = pass.tick(t, p.getX(), p.getZ(), blocked);
            switch (ev) {
                case WARN -> {
                    p.sendSystemMessage(Component.literal("Часовые на мосту: «Стоять! Назад! Стреляю без предупреждения!»")
                            .withStyle(ChatFormatting.RED));
                    level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.HOSTILE, 1.0f, 0.6f);
                }
                case TOLL -> {
                    long toll = sim.tollFor(Reputation.get(p, owner.id), PlayerFaction.rank(p));
                    p.sendSystemMessage(Component.literal("Пост на мосту («" + owner.name + "»): проход стоит " + toll
                            + " руб. Оплатить: /zone pay или ПКМ по часовому.").withStyle(ChatFormatting.GOLD));
                }
                case VIOLATION -> violation(p, d, sim, owner, t);
                default -> { }
            }
        }
    }

    /** Проход без оплаты: минус репутация и короткая враждебность часовых. */
    private static void violation(ServerPlayer p, ZoneData d, Simulation sim, Faction owner, long t) {
        UUID id = p.getUUID();
        Reputation.add(p, sim, owner.id, -Gate.REP_PENALTY, "проход через мост без оплаты");
        int now = Reputation.get(p, owner.id);
        HOSTILE_KEEP.put(id, now);
        if (now > HOSTILE_CAP) {                       // держим врагом на время погони
            Reputation.add(p, sim, owner.id, HOSTILE_CAP - now, null);
        }
        HOSTILE_UNTIL.put(id, t + Gate.HOSTILE_OUT_TICKS);
        d.setDirty();
        p.sendSystemMessage(Component.literal("«" + owner.name + "»: «Стой! Ты прошёл без пошлины!» Часовые открыли огонь.")
                .withStyle(ChatFormatting.RED));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.5f, 2.0f);
    }

    /** Вернуть репутацию, когда враждебность за проход без оплаты спала. */
    private static void restore(ServerPlayer p, Simulation sim, long t) {
        UUID id = p.getUUID();
        Long until = HOSTILE_UNTIL.get(id);
        if (until == null || t < until) return;
        HOSTILE_UNTIL.remove(id);
        Integer keep = HOSTILE_KEEP.remove(id);
        if (keep == null) return;
        int nodeId = tollNode(sim);
        if (nodeId < 0 || nodeId >= sim.nodes.size()) return;
        int ownerId = sim.nodes.get(nodeId).owner;
        if (ownerId < 0) return;
        Reputation.add(p, sim, ownerId, keep - Reputation.get(p, ownerId), null);
    }

    /** Оплата пошлины на посту. Возвращает текст для игрока. */
    public static String pay(ServerPlayer p, ZoneData d) {
        Simulation sim = d.sim;
        int nodeId = tollNode(sim);
        if (nodeId < 0 || nodeId >= sim.nodes.size()) return "Здесь нет платного прохода.";
        return payAt(p, d, nodeId);
    }

    /** Оплата у конкретного узла (ПКМ по часовому знает свой узел). */
    public static String payAt(ServerPlayer p, ZoneData d, int nodeId) {
        Simulation sim = d.sim;
        if (nodeId != tollNode(sim)) return "";
        Node n = sim.nodes.get(nodeId);
        if (n.owner < 0) return "Проход никем не охраняется.";
        Faction owner = sim.factions.get(n.owner);
        Gate.Pass pass = passFor(sim, nodeId, p.getUUID());
        if (pass == null) return "У этой локации не задан регион прохода.";
        if (!pass.inside()) return "Вы не у поста. Подойдите к шлагбауму на мосту.";
        if (Gate.enemyOf(sim, owner, PlayerFaction.faction(p)))
            return "«" + owner.name + "»: «Тебе здесь не место». Сначала заслужите доверие.";
        long t = p.serverLevel().getGameTime();
        if (pass.open(t)) return "Проход уже оплачен, идите.";
        long toll = sim.tollFor(Reputation.get(p, owner.id), PlayerFaction.rank(p));
        long cash = ru.stalkerzone.npc.TradeService.countRubles(p);
        if (cash < toll) return "Нужно " + toll + " руб., у вас " + cash + ". Не хватает.";
        ru.stalkerzone.npc.TradeService.takeRubles(p, toll);
        sim.playerBribe(owner, toll);                  // деньги — в кассу владельца через Ledger (как в 003)
        d.setDirty();
        pass.pay(t);
        return "Пошлина " + toll + " руб. уплачена «" + owner.name + "». Проход открыт 10 минут.";
    }

    /** Текст состояния поста для /zone post (смоук-проверка карточки 012). */
    public static String describe(Simulation sim) {
        int nodeId = tollNode(sim);
        if (nodeId < 0 || nodeId >= sim.nodes.size()) return "Платного прохода нет.";
        Node n = sim.nodes.get(nodeId);
        String who = n.owner < 0 ? "никто" : sim.factions.get(n.owner).name;
        int[] g = region(sim, nodeId);
        String reg = g == null ? "не задан" : g[0] + "," + g[1] + " .. " + g[2] + "," + g[3];
        return "Пост на мосту: «" + n.name + "», владелец — " + who + ".\n"
                + "Регион gate: " + reg + ". Часовые у точки guards — фракция владельца (" + who + ").";
    }
}

