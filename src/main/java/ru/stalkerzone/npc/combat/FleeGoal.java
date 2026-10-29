package ru.stalkerzone.npc.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import ru.stalkerzone.compat.Tacz;
import ru.stalkerzone.npc.StalkerEntity;
import ru.stalkerzone.sim.tactics.Morale;

import java.util.EnumSet;

/**
 * Бегство (v1.15): сломленный боец уходит из боя к своей базе, а безоружный в панике — просто подальше
 * от врага. По дороге приходит в себя; собравшись (мораль выше 30), снова может воевать.
 */
public class FleeGoal extends Goal {
    private final StalkerEntity mob;
    private int repath;

    public FleeGoal(StalkerEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean scared() {
        Morale.State s = mob.combat().morale.state();
        return s == Morale.State.BROKEN || (s == Morale.State.PANIC && !Tacz.isGun(mob.getMainHandItem()) && mob.getTarget() != null);
    }

    @Override
    public boolean canUse() { return !mob.isTrader() && !mob.isDowned() && scared(); }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void start() {
        mob.setCrouch(false);
        mob.setAggressive(false);
        Tacz.aim(mob, false);
        mob.combat().shout(mob, mob.combat().morale.state() == Morale.State.BROKEN
                ? (mob.getRandom().nextBoolean() ? "Бежим! Нам их не сдержать!" : "Всё, я пас, ухожу!") : "Не подходи!", true);
        repath = 0;
    }

    @Override
    public void tick() {
        if (mob.tickCount % 20 == 0) mob.combat().morale.second(1, false, false, mob.hurtTime > 0);
        if (--repath > 0) return;
        repath = 20;
        LivingEntity t = mob.getTarget();
        Vec3 home = mob.homePos();
        boolean broken = mob.combat().morale.state() == Morale.State.BROKEN;
        if (broken && home != null && mob.distanceToSqr(home) > 36) {
            mob.getNavigation().moveTo(home.x, home.y, home.z, 1.3);
        } else if (t != null) {
            Vec3 away = mob.position().subtract(t.position());
            if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
            away = away.normalize().scale(14);
            mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.3);
        }
        if (broken && home != null && mob.distanceToSqr(home) <= 36) {
            mob.setTarget(null);      // добежал до своих — из боя вышел
            mob.setCrouch(true);
        }
    }

    @Override
    public void stop() {
        mob.setCrouch(false);
    }
}
