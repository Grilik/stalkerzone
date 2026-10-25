package ru.stalkerzone.compat;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Бой с огнестрелом TaCZ: держать дистанцию, искать линию огня, стрелять очередями,
 * менять магазин, когда кончились патроны (магазин пополняется со склада фракции через refill).
 * Если в руках не оружие TaCZ, цель не активна и работает обычная рукопашная.
 */
public class GunAttackGoal extends Goal {
    /** Откуда NPC берёт новый магазин. Возвращает true, если патроны нашлись. */
    public interface Refill { boolean refill(PathfinderMob mob, CompatConfig.GunSpec spec); }

    private final PathfinderMob mob;
    private final Refill refill;
    private int seeTime, burstLeft, pause, reloadTicks, strafeTicks, drawn = -1;
    private boolean strafeLeft;

    public GunAttackGoal(PathfinderMob mob, Refill refill) {
        this.mob = mob;
        this.refill = refill;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private CompatConfig.GunSpec spec() {
        ItemStack s = mob.getMainHandItem();
        if (!Tacz.isGun(s)) return null;
        CompatConfig.GunSpec g = CompatConfig.GUNS.get(Tacz.gunId(s));
        return g != null ? g : new CompatConfig.GunSpec(Tacz.gunId(s), "", 30, "SEMI", 2, 24, 2, java.util.List.of());
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() && spec() != null;
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void start() {
        seeTime = 0; burstLeft = 0; pause = 10 + mob.getRandom().nextInt(20); reloadTicks = 0;
        Tacz.draw(mob);
        drawn = mob.tickCount;
        mob.setAggressive(true);
    }

    @Override
    public void stop() {
        Tacz.aim(mob, false);
        mob.setAggressive(false);
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        LivingEntity t = mob.getTarget();
        CompatConfig.GunSpec g = spec();
        if (t == null || g == null) return;
        double d = mob.distanceTo(t);
        boolean see = mob.getSensing().hasLineOfSight(t);
        seeTime = see ? Math.min(seeTime + 1, 100) : Math.max(seeTime - 1, -60);
        mob.getLookControl().setLookAt(t, 30f, 30f);

        // движение: подойти на дистанцию боя, держаться за пределами ближнего боя, стрейфиться
        double ideal = Math.max(6, g.range() * 0.6);
        if (!see || d > g.range()) {
            mob.getNavigation().moveTo(t, 1.05);
        } else if (d < Math.min(5, ideal * 0.5)) {
            Vec3 away = mob.position().subtract(t.position()).normalize().scale(6);
            mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.1);
        } else {
            mob.getNavigation().stop();
            if (--strafeTicks <= 0) { strafeTicks = 20 + mob.getRandom().nextInt(40); strafeLeft = mob.getRandom().nextBoolean(); }
            mob.getMoveControl().strafe(d > ideal * 1.3 ? 0.35f : 0f, strafeLeft ? 0.35f : -0.35f);
        }

        if (reloadTicks > 0) {
            if (--reloadTicks == 0) {
                if (refill.refill(mob, g)) Tacz.setAmmoIn(mob.getMainHandItem(), g.mag());
                else mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY); // патронов нет — в рукопашную
                Tacz.draw(mob);
            }
            return;
        }
        if (!see || seeTime < 6 || d > g.range() * 1.25) { Tacz.aim(mob, false); return; }
        if (mob.tickCount - drawn < 12) return;
        Tacz.aim(mob, true);
        if (pause > 0) { pause--; return; }

        // угол на центр цели с небольшим промахом: чем дальше, тем больше разброс
        Vec3 eye = mob.getEyePosition();
        Vec3 aimAt = t.position().add(0, t.getBbHeight() * 0.6, 0);
        Vec3 dv = aimAt.subtract(eye);
        double h = Math.sqrt(dv.x * dv.x + dv.z * dv.z);
        float spread = (float) (1.2 + d * 0.06);
        float yaw = (float) (Mth.atan2(dv.z, dv.x) * Mth.RAD_TO_DEG) - 90f + (mob.getRandom().nextFloat() - 0.5f) * spread * 2;
        float pitch = (float) (-(Mth.atan2(dv.y, h) * Mth.RAD_TO_DEG)) + (mob.getRandom().nextFloat() - 0.5f) * spread;
        String r = Tacz.shoot(mob, pitch, yaw);
        switch (r) {
            case "SUCCESS" -> {
                if (burstLeft <= 0) burstLeft = Math.max(1, g.burst() / 2 + mob.getRandom().nextInt(Math.max(1, g.burst())));
                if (--burstLeft <= 0) pause = "AUTO".equals(g.mode()) ? 12 + mob.getRandom().nextInt(25) : 8 + mob.getRandom().nextInt(20);
            }
            case "NO_AMMO" -> { reloadTicks = 50; Tacz.aim(mob, false); }
            case "NOT_DRAW", "NOT_GUN" -> { Tacz.draw(mob); drawn = mob.tickCount; }
            default -> { }
        }
    }
}
