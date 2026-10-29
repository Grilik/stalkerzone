package ru.stalkerzone.npc.combat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import ru.stalkerzone.sim.tactics.Morale;
import ru.stalkerzone.sim.tactics.SquadOrders;

/**
 * Боевое состояние сталкера (v1.15): мораль, приказ в отряде, подавление, крики.
 * Живёт только в мире (не сохраняется): после перезагрузки боец спокоен.
 */
public final class CombatState {
    public final Morale morale;
    public SquadOrders.Order order = SquadOrders.Order.SUPPRESS;
    public boolean leaderNear;
    public double enemyRatio = 1;
    public int suppressedTicks;
    public boolean inCover;
    private long lastShout = -1000;

    public CombatState(double bravery, boolean fanatic) {
        this.morale = new Morale(bravery, fanatic, 75 + 20 * Math.max(0, bravery));
    }

    public boolean suppressed() { return suppressedTicks > 0; }

    /** По бойцу стреляют: свистит над головой. */
    public void suppress() {
        if (suppressedTicks <= 0) morale.suppressed();
        suppressedTicks = 40;
    }

    /** Крикнуть товарищам (видят игроки в 24 блоках). Не чаще раза в 6 секунд. */
    public void shout(LivingEntity who, String text, boolean urgent) {
        if (!(who.level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (!urgent && now - lastShout < 120) return;
        if (now - lastShout < 40) return;
        lastShout = now;
        Component c = Component.literal("[" + who.getName().getString() + "] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(text).withStyle(urgent ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(who) < 24 * 24) p.sendSystemMessage(c);
    }
}
