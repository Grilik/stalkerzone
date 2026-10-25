package ru.stalkerzone.npc;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;

/** Медик бежит к тяжело раненому товарищу и через три секунды ставит его на ноги (v1.6). */
public class ReviveAllyGoal extends Goal {
    private final StalkerEntity self;
    private StalkerEntity patient;
    private int work;

    public ReviveAllyGoal(StalkerEntity self) {
        this.self = self;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!self.isMedic() || self.isDowned() || self.getTarget() != null) return false;
        if (self.tickCount % 10 != 0) return false;
        List<StalkerEntity> near = self.level().getEntitiesOfClass(StalkerEntity.class, self.getBoundingBox().inflate(18),
                e -> e != self && e.isDowned() && e.faction() == self.faction());
        if (near.isEmpty()) return false;
        patient = near.get(0);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return patient != null && patient.isAlive() && patient.isDowned() && !self.isDowned();
    }

    @Override
    public void start() {
        work = 0;
        self.getNavigation().moveTo(patient, 1.2);
    }

    @Override
    public void tick() {
        self.getLookControl().setLookAt(patient, 30f, 30f);
        if (self.distanceToSqr(patient) > 4) {
            if (self.tickCount % 20 == 0) self.getNavigation().moveTo(patient, 1.2);
            return;
        }
        self.getNavigation().stop();
        if (++work >= 60) {
            patient.revive(0.4f);
            patient = null;
        }
    }

    @Override
    public void stop() {
        patient = null;
        work = 0;
    }
}
