package ru.stalkerzone.container;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Э1-17: содержимое контейнера мода. Добыча — по таблице {@code LootTable} (ставит правка мира), генерируется один раз
 * (при первом открытии или если ящик сломали) и записывается в Ledger как приход из мира ({@link WorldLoot}).
 * Окно — обычное окно сундука на rows() рядов; ячейки сверх kind.slots закрыты (canPlaceItem), добыча в них не кладётся.
 */
public class ZoneContainerBlockEntity extends RandomizableContainerBlockEntity {
    private final ContainerKind kind;
    private NonNullList<ItemStack> items;

    public ZoneContainerBlockEntity(BlockPos pos, BlockState state) {
        super(ModContainers.BLOCK_ENTITY.get(), pos, state);
        this.kind = state.getBlock() instanceof ZoneContainerBlock b ? b.kind : ContainerKind.WOOD_CRATE;
        this.items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
    }

    public ContainerKind kind() { return kind; }

    @Override
    public int getContainerSize() { return kind.rows() * 9; }

    @Override
    public boolean canPlaceItem(int slot, ItemStack st) { return slot < kind.slots; }

    @Override
    protected NonNullList<ItemStack> getItems() { return items; }

    @Override
    protected void setItems(NonNullList<ItemStack> list) { items = list; }

    @Override
    protected Component getDefaultName() { return Component.translatable(getBlockState().getBlock().getDescriptionId()); }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        MenuType<ChestMenu> t = switch (kind.rows()) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            default -> MenuType.GENERIC_9x3;
        };
        return new ChestMenu(t, id, inv, this, kind.rows());
    }

    @Override
    public void unpackLootTable(Player player) {
        boolean fresh = lootTable != null && level instanceof ServerLevel;
        super.unpackLootTable(player);
        if (!fresh) return;
        // добыча в закрытых ячейках — в открытые; что не влезло, не выдаётся
        List<ItemStack> got = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) got.add(items.get(i));
            items.set(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < got.size() && i < kind.slots; i++) items.set(i, got.get(i));
        WorldLoot.account(((ServerLevel) level).getServer(), items.subList(0, kind.slots));
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) ContainerHelper.loadAllItems(tag, items);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!trySaveLootTable(tag)) ContainerHelper.saveAllItems(tag, items);
    }
}
