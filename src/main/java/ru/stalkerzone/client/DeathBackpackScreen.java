package ru.stalkerzone.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.stalkerzone.npc.DeathBackpackMenu;

/** Окно рюкзака на месте гибели (П-10): сундук на 6 рядов + кнопка «Забрать всё» в строке заголовка. */
public class DeathBackpackScreen extends ContainerScreen {
    public DeathBackpackScreen(net.minecraft.world.inventory.ChestMenu menu, Inventory inv, Component title) { super(menu, inv, title); }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("gui.stalkerzone.take_all"), b -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, DeathBackpackMenu.TAKE_ALL);
        }).bounds(leftPos + imageWidth - 78, topPos + 3, 72, 12).build());
    }
}
