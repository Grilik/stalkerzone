package ru.stalkerzone.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Рюкзак на месте гибели игрока (П-10, вместо выпавшего предмета карточки 030): стоит на земле моделью рюкзака,
 * ПКМ — окно со снаряжением (как сундук: по одному или «Забрать всё»). Неуязвим, не горит, водой не уносится,
 * держится {@link DeathBackpack#HOLD_TICKS} (20 минут), опустевший исчезает и снимает метку в КПК.
 */
public class DeathBackpackEntity extends Entity {
    /** Ячеек в рюкзаке: весь инвентарь игрока (36 + броня 4 + вторая рука) с запасом. */
    public static final int SIZE = 54;
    final SimpleContainer items = new SimpleContainer(SIZE);
    String owner = "";
    int age;

    public DeathBackpackEntity(EntityType<? extends DeathBackpackEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        // падает на землю, но не скользит и не плывёт
        Vec3 v = getDeltaMovement();
        if (!onGround()) v = v.add(0, -0.04, 0);
        move(MoverType.SELF, new Vec3(0, v.y, 0));
        setDeltaMovement(onGround() ? Vec3.ZERO : new Vec3(0, v.y * 0.98, 0));
        if (!level().isClientSide) {
            if (++age >= DeathBackpack.HOLD_TICKS || getY() < level().getMinBuildHeight() - 64) discard();
        }
    }

    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return isAlive(); }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean hurt(DamageSource src, float amount) { return false; }
    @Override public boolean fireImmune() { return true; }
    @Override public boolean ignoreExplosion() { return true; }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (p instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider((id, inv, pl) -> new DeathBackpackMenu(id, inv, items, this),
                    Component.translatable("container.stalkerzone.death_backpack",
                            owner.isEmpty() ? "?" : owner)));
        }
        return InteractionResult.CONSUME;
    }

    /** Рюкзак опустел: убрать с земли и снять метку в КПК у хозяина. */
    void emptied(Player by) {
        if (!items.isEmpty() || !isAlive()) return;
        discard();
        if (level().getServer() != null) {
            ServerPlayer o = level().getServer().getPlayerList().getPlayerByName(owner);
            if (o != null) PlayerFaction.backpackTaken(o);
        }
    }

    boolean usableBy(Player p) { return isAlive() && p.distanceToSqr(this) < 64; }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        items.fromTag(t.getList("Items", Tag.TAG_COMPOUND));
        owner = t.getString("Owner");
        age = t.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.put("Items", items.createTag());
        t.putString("Owner", owner);
        t.putInt("Age", age);
    }
}
