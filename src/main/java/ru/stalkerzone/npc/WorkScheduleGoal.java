package ru.stalkerzone.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import ru.stalkerzone.zonemap.build.Prefabs;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Распорядок NPC базы по времени суток (карточка 032).
 * Бой ({@link ru.stalkerzone.npc.combat.TacticalCombatGoal}) выше по приоритету и прерывает работу;
 * после боя цель возвращает NPC на место.
 */
public class WorkScheduleGoal extends Goal {
    private static final int DAY_START = 6000;
    private static final int EVENING_START = 18000;
    private static final int NIGHT_START = 22000;
    private static final double REACH_DIST = 2.5;
    private static final int PATROL_PAUSE = 40;

    private final StalkerEntity mob;
    private final Random rng = new Random();
    private int patrolIndex, pauseTicks, residentTicks, residentTarget;

    public WorkScheduleGoal(StalkerEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return mob.workRole() > 0 && !mob.isDowned() && mob.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        patrolIndex = 0; pauseTicks = 0; residentTicks = 0; residentTarget = rng.nextInt(3);
    }

    @Override
    public void tick() {
        int time = (int) (mob.level().getDayTime() % 24000);
        boolean dayShift = time >= DAY_START && time < EVENING_START;
        int role = mob.workRole();

        if (role == StalkerEntity.ROLE_GUARD) {
            boolean myShift = (mob.getId() % 2 == 0) == dayShift;
            if (!myShift) { goToBed(); return; }
            guardPost();
            return;
        }
        if (dayShift) {
            switch (role) {
                case StalkerEntity.ROLE_PATROL -> patrol();
                case StalkerEntity.ROLE_COOK -> goToPoint("cook");
                case StalkerEntity.ROLE_MECHANIC -> goToPoint("work");
                case StalkerEntity.ROLE_DUTY -> goToPoint("duty");
                case StalkerEntity.ROLE_RESIDENT -> residentWander();
            }
        } else if (time >= EVENING_START && time < NIGHT_START) {
            goToCampfire();
        } else {
            goToBed();
        }
    }

    // ---- guard ----
    private void guardPost() {
        Vec3 post = mob.homePos();
        if (post == null) return;
        mob.setCrouch(false);
        if (mob.distanceToSqr(post) > REACH_DIST * REACH_DIST) {
            mob.getNavigation().moveTo(post.x, post.y, post.z, 1.0);
            return;
        }
        mob.getNavigation().stop();
        double yaw = outwardYaw(post) + 45.0 * Math.sin(mob.tickCount / 240.0 * Math.PI * 2.0);
        double rad = Math.toRadians(yaw);
        double tx = mob.getX() - Math.sin(rad) * 8.0;
        double tz = mob.getZ() + Math.cos(rad) * 8.0;
        mob.getLookControl().setLookAt(tx, mob.getEyeY(), tz, 20f, 20f);
    }

    private double outwardYaw(Vec3 post) {
        double[] c = mob.nodeCenterXZ();
        double dx = post.x - (c != null ? c[0] : post.x);
        double dz = post.z - (c != null ? c[1] : post.z);
        if (dx * dx + dz * dz < 1.0) { dx = 1.0; dz = 0.0; }
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    // ---- patrol ----
    private void patrol() {
        List<Prefabs.Point> route = prefabPoints("patrol");
        if (route.isEmpty()) { idleAtHome(); return; }
        if (pauseTicks > 0) { pauseTicks--; mob.getNavigation().stop(); lookAround(); return; }
        Prefabs.Point target = route.get(patrolIndex % route.size());
        Vec3 tp = new Vec3(target.x() + 0.5, target.y(), target.z() + 0.5);
        if (mob.distanceToSqr(tp) < REACH_DIST * REACH_DIST) {
            mob.getNavigation().stop(); patrolIndex++; pauseTicks = PATROL_PAUSE + rng.nextInt(PATROL_PAUSE);
            return;
        }
        mob.getNavigation().moveTo(tp.x, tp.y, tp.z, 0.9);
    }

    // ---- cook / mechanic / duty ----
    private void goToPoint(String kind) {
        List<Prefabs.Point> pts = prefabPoints(kind);
        if (pts.isEmpty()) { idleAtHome(); return; }
        Prefabs.Point p = pts.get(Math.abs(mob.getId()) % pts.size());
        Vec3 target = new Vec3(p.x() + 0.5, p.y(), p.z() + 0.5);
        navigateTo(target);
        if (mob.distanceToSqr(target) < REACH_DIST * REACH_DIST) {
            mob.getNavigation().stop();
            lookAround();
        }
    }

    // ---- resident ----
    private void residentWander() {
        String[] kinds = {"rest", "cook", "work"};
        if (residentTicks <= 0) {
            residentTarget = (residentTarget + 1 + rng.nextInt(2)) % 3;
            residentTicks = 200 + rng.nextInt(400);
        }
        residentTicks--;
        List<Prefabs.Point> pts = prefabPoints(kinds[residentTarget]);
        if (pts.isEmpty()) { idleAtHome(); return; }
        Prefabs.Point p = pts.get(rng.nextInt(pts.size()));
        Vec3 target = new Vec3(p.x() + 0.5, p.y(), p.z() + 0.5);
        navigateTo(target);
        if (mob.distanceToSqr(target) < REACH_DIST * REACH_DIST) {
            mob.getNavigation().stop();
            mob.setCrouch(true);
            lookAround();
        }
    }

    // ---- evening ----
    private void goToCampfire() {
        List<Prefabs.Point> pts = prefabPoints("cook");
        if (pts.isEmpty()) { goToBed(); return; }
        Prefabs.Point p = pts.get(rng.nextInt(pts.size()));
        int[][] ring = {{2,0},{-2,0},{0,2},{0,-2},{2,2},{2,-2},{-2,2},{-2,-2}};
        int idx = Math.abs(mob.getId()) % ring.length;
        Vec3 target = new Vec3(p.x() + ring[idx][0] + 0.5, p.y(), p.z() + ring[idx][1] + 0.5);
        navigateTo(target);
        if (mob.distanceToSqr(target) < 1.6 * 1.6) {
            mob.getNavigation().stop();
            mob.setCrouch(true);
            mob.getLookControl().setLookAt(p.x(), p.y() + 1.0, p.z(), 15f, 15f);
        }
    }

    // ---- night ----
    private void goToBed() {
        List<Prefabs.Point> pts = prefabPoints("bed");
        if (pts.isEmpty()) { idleAtHome(); return; }
        Prefabs.Point p = pts.get(Math.abs(mob.getId()) % pts.size());
        Vec3 target = new Vec3(p.x() + 0.5, p.y(), p.z() + 0.5);
        navigateTo(target);
        if (mob.distanceToSqr(target) < REACH_DIST * REACH_DIST) {
            mob.getNavigation().stop();
            mob.setCrouch(true);
            mob.getLookControl().setLookAt(mob.getX(), mob.getEyeY() - 1, mob.getZ(), 15f, 15f);
        }
    }

    // ---- helpers ----
    private void navigateTo(Vec3 target) {
        mob.setCrouch(false);
        if (mob.getNavigation().getPath() != null) {
            BlockPos cur = mob.getNavigation().getTargetPos();
            if (cur != null && cur.distSqr(new BlockPos((int)target.x, (int)target.y, (int)target.z)) < 2.0) return;
        }
        mob.getNavigation().moveTo(target.x, target.y, target.z, 0.85);
    }

    private void lookAround() {
        double a = mob.tickCount * 0.02;
        mob.getLookControl().setLookAt(mob.getX() + Math.sin(a)*5.0, mob.getEyeY(), mob.getZ() + Math.cos(a)*5.0, 10f, 10f);
    }

    private void idleAtHome() {
        Vec3 home = mob.homePos();
        if (home == null) return;
        if (mob.distanceToSqr(home) > REACH_DIST * REACH_DIST) {
            mob.getNavigation().moveTo(home.x, home.y, home.z, 0.85);
            return;
        }
        mob.getNavigation().stop();
        lookAround();
    }

    private List<Prefabs.Point> prefabPoints(String kind) {
        return NpcManager.prefabPointsByKind((ServerLevel) mob.level(), mob.sim(), mob.homeNode(), kind);
    }
}
