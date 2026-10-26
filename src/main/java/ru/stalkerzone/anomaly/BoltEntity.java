package ru.stalkerzone.anomaly;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import ru.stalkerzone.item.ModItems;
import ru.stalkerzone.npc.ModEntities;

/**
 * Брошенный болт (v1.14, этап 2). Пролетая через аномалию, срабатывает её: та выдаёт себя вспышкой и на несколько
 * секунд разряжается — можно пройти. Мясорубка и жарка болт уничтожают, в остальных случаях он падает на землю, и
 * его можно подобрать (если не затерялся в траве).
 */
public class BoltEntity extends ThrowableItemProjectile {
    private long lastKey = Long.MIN_VALUE;
    private boolean destroyed;

    public BoltEntity(EntityType<? extends BoltEntity> t, Level level) { super(t, level); }

    public BoltEntity(Level level, LivingEntity owner) { super(ModEntities.BOLT.get(), owner, level); }

    @Override
    protected Item getDefaultItem() { return ModItems.BOLT.get(); }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        AnomalyManager.Hit h = AnomalyManager.anomalyAt(sl, getX(), getY(), getZ());
        if (h == null || h.key() == lastKey) return;
        lastKey = h.key();
        destroyed = AnomalyManager.trigger(sl, h, this);
        if (destroyed) discard();
    }

    @Override
    protected void onHit(HitResult r) {
        super.onHit(r);
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        if (!destroyed && random.nextDouble() < 0.8) {
            ItemEntity it = new ItemEntity(sl, getX(), getY() + 0.1, getZ(), new ItemStack(ModItems.BOLT.get()));
            it.setDeltaMovement(0, 0, 0);
            it.setPickUpDelay(10);
            sl.addFreshEntity(it);
        }
        discard();
    }
}
