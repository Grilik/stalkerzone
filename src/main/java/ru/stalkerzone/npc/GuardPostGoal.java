package ru.stalkerzone.npc;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Часовой на посту (карточка 028): стоит на точке {@code guards}, поворачивается по сектору,
 * отходит не дальше {@link #MAX_DIST} блоков. С поста уходит только в бой: боевые цели
 * ({@link ru.stalkerzone.npc.combat.TacticalCombatGoal}, рукопашная) выше по приоритету и забирают
 * управление, а после боя цель снова возвращается на точку.
 *
 * <p>Как и у остальных NPC, костёр и огонь непроходимы (штраф пути {@link StalkerEntity}), поэтому
 * часовой не заходит в костёр у поста.
 */
public class GuardPostGoal extends Goal {
    /** Насколько часовой может отойти от точки, блоки. */
    public static final double MAX_DIST = 3.0;
    /** Полуширина сектора обзора, градусы. */
    private static final double SECTOR = 45.0;
    /** Сколько тиков длится один проход по сектору в одну сторону. */
    private static final double SWEEP_TICKS = 240.0;

    private final StalkerEntity mob;

    public GuardPostGoal(StalkerEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Часовой на посту: он на ногах и не в бою (в бою управление забирают боевые цели). */
    private boolean onDuty() {
        return mob.isGuard() && !mob.isDowned() && mob.getTarget() == null;
    }

    @Override
    public boolean canUse() { return onDuty(); }

    @Override
    public boolean canContinueToUse() { return onDuty(); }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        mob.setCrouch(false);
    }

    @Override
    public void tick() {
        Vec3 post = mob.homePos();
        if (post == null) return;
        if (mob.distanceToSqr(post) > MAX_DIST * MAX_DIST) {
            mob.getNavigation().moveTo(post.x, post.y, post.z, 1.0);
            return;
        }
        mob.getNavigation().stop();
        // поворот по сектору: плавно сканирует вокруг направления «наружу» от центра точки
        double yaw = outwardYaw(post) + SECTOR * Math.sin(mob.tickCount / SWEEP_TICKS * Math.PI * 2.0);
        double rad = Math.toRadians(yaw);
        double tx = mob.getX() - Math.sin(rad) * 8.0;
        double tz = mob.getZ() + Math.cos(rad) * 8.0;
        mob.getLookControl().setLookAt(tx, mob.getEyeY(), tz, 20f, 20f);
    }

    /** Курс «наружу» от центра точки: часовой смотрит на подходы к посту, а не на свои палатки. */
    private double outwardYaw(Vec3 post) {
        double[] c = mob.nodeCenterXZ();
        double dx = post.x - (c != null ? c[0] : post.x);
        double dz = post.z - (c != null ? c[1] : post.z);
        if (dx * dx + dz * dz < 1.0) { dx = 1.0; dz = 0.0; }   // стоит в центре точки — смотреть на восток
        return Math.toDegrees(Math.atan2(-dx, dz));
    }
}
