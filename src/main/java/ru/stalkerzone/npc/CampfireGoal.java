package ru.stalkerzone.npc;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Отдых у костра (карточка 028): NPC садится на своё место в двух блоках от костра и смотрит на огонь.
 * Точка {@code campfire} в файле локации — центр костра, места вокруг раздаёт {@link NpcManager} на радиусе 2,
 * поэтому NPC не лезут в сам костёр (огонь непроходим, см. {@link StalkerEntity}).
 */
public class CampfireGoal extends Goal {
    /** Допуск у своего места, блоки: дальше — идём к месту. */
    private static final double SIT_DIST = 1.6;

    private final StalkerEntity mob;

    public CampfireGoal(StalkerEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** NPC у костра, на ногах и не в бою. */
    private boolean resting() {
        return mob.isCamper() && !mob.isDowned() && mob.getTarget() == null;
    }

    @Override
    public boolean canUse() { return resting(); }

    @Override
    public boolean canContinueToUse() { return resting(); }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        Vec3 seat = mob.homePos();
        if (seat == null) return;
        mob.setCrouch(true);                        // сидит у костра
        if (mob.distanceToSqr(seat) > SIT_DIST * SIT_DIST) {
            mob.getNavigation().moveTo(seat.x, seat.y, seat.z, 0.9);
            return;
        }
        mob.getNavigation().stop();
        Vec3 fire = mob.campfirePos();
        if (fire != null) mob.getLookControl().setLookAt(fire.x, fire.y + 1.0, fire.z, 15f, 15f);
    }

    @Override
    public void stop() {
        mob.setCrouch(false);
    }
}
