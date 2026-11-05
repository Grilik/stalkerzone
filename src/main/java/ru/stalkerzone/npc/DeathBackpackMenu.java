package ru.stalkerzone.npc;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;

/**
 * Окно рюкзака на месте гибели (П-10): сундук на 6 рядов. Кнопка «Забрать всё» — штатный механизм кнопок меню
 * ({@code clickMenuButton}, id 0), свой сетевой пакет не нужен. Сервер переносит вещи в инвентарь игрока.
 */
public class DeathBackpackMenu extends ChestMenu {
    public static final int TAKE_ALL = 0;
    private final DeathBackpackEntity pack;   // null на клиенте

    /** Клиент: содержимое придёт с сервера. */
    public DeathBackpackMenu(int id, Inventory inv) { this(id, inv, new SimpleContainer(DeathBackpackEntity.SIZE), null); }

    DeathBackpackMenu(int id, Inventory inv, Container c, DeathBackpackEntity pack) {
        super(ModMenus.DEATH_BACKPACK.get(), id, inv, c, 6);
        this.pack = pack;
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (id != TAKE_ALL) return false;
        for (int i = 0; i < DeathBackpackEntity.SIZE; i++) quickMoveStack(p, i);   // что не влезло — остаётся в рюкзаке
        broadcastChanges();
        return true;
    }

    @Override
    public boolean stillValid(Player p) { return pack == null || pack.usableBy(p); }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (pack != null) pack.emptied(p);
    }
}
