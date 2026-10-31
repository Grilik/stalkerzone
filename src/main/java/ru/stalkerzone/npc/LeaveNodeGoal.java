package ru.stalkerzone.npc;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Уход NPC с узла «по делам» (карточка 033). Когда гарнизон узла уменьшился, лишний NPC не пропадает
 * на глазах: он идёт к краю зоны узла ({@link NpcManager#AREA}), а исчезает только за её пределами и
 * вне поля зрения игроков (дальше {@link #VIEW_DIST} от ближайшего игрока). В бою и разговоре не участвует.
 */
public class LeaveNodeGoal extends Goal {
    /** Дальше этого от центра узла NPC уже за краем зоны и может исчезнуть. */
    private static final double EDGE = NpcManager.AREA;
    /** Пока игрок ближе этого расстояния, NPC не исчезает (он ещё в поле зрения). */
    private static final double VIEW_DIST = 64.0;
    /** Дольше этого в пути NPC не задерживается: страховка от «зависшего» ухода. */
    private static final int MAX_TICKS = 6000;

    private final StalkerEntity mob;
    private int ticks;

    public LeaveNodeGoal(StalkerEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return mob.isLeaving() && !mob.isDowned();
    }

    @Override
    public boolean canContinueToUse() {
        return mob.isLeaving() && !mob.isDowned();
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        ticks = 0;
        mob.setCrouch(false);
    }

    @Override
    public void tick() {
        ticks++;
        Vec3 target = mob.leaveTarget();
        if (target == null) { mob.discard(); return; }
        double[] c = mob.nodeCenterXZ();
        double fromCenter = c == null ? Double.MAX_VALUE : Math.hypot(mob.getX() - c[0], mob.getZ() - c[1]);
        double near = nearestPlayerDist();
        // за краем зоны узла и вне поля зрения — исчезает; страховка по времени тоже вне вида
        if ((fromCenter >= EDGE && near > VIEW_DIST) || (ticks > MAX_TICKS && near > VIEW_DIST)) {
            mob.discard();
            return;
        }
        if (fromCenter >= EDGE) return;   // на краю: ждёт, пока игроки отвернутся
        mob.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
    }

    private double nearestPlayerDist() {
        double best = Double.MAX_VALUE;
        for (net.minecraft.world.entity.player.Player p : mob.level().players()) {
            best = Math.min(best, Math.hypot(mob.getX() - p.getX(), mob.getZ() - p.getZ()));
        }
        return best;
    }
}
