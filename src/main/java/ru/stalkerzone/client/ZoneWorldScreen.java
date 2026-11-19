package ru.stalkerzone.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.fml.loading.FMLPaths;
import ru.stalkerzone.world.golden.GoldenCopy;

import java.nio.file.Path;

/**
 * Э1-11Б: экран создания мира «Зона». Имя мира → фоновое копирование старшей целой золотой копии
 * TheZone ({@link GoldenCopy}) в папку сохранений → ванильное переименование уровня → вход в мир.
 * Копирование идёт в отдельном потоке (мир ~1.8 ГБ), клиент не блокируется; при ошибке временный
 * каталог уже удалён ядром, сообщение показывается здесь, эталон и существующие миры не тронуты.
 * Каждый созданный мир получает собственный simSeed ({@code ZoneData}, Э1-11В).
 */
public class ZoneWorldScreen extends Screen {
    private final Screen parent;
    private EditBox nameEdit;
    private Button createButton;
    private Button backButton;
    private Component status = Component.empty();
    private volatile boolean busy;

    protected ZoneWorldScreen(Screen parent) {
        super(Component.literal("Новый мир — Зона"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        nameEdit = new EditBox(font, width / 2 - 100, height / 2 - 46, 200, 20, Component.literal("Имя мира"));
        nameEdit.setValue("Зона");
        nameEdit.setMaxLength(64);
        addRenderableWidget(nameEdit);
        createButton = Button.builder(Component.literal("Создать мир Зоны"), b -> startCreate())
                .bounds(width / 2 - 100, height / 2 - 10, 200, 20).build();
        addRenderableWidget(createButton);
        backButton = Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(width / 2 - 100, height / 2 + 14, 200, 20).build();
        addRenderableWidget(backButton);
        setInitialFocus(nameEdit);
    }

    private void startCreate() {
        if (busy) return;
        String name = nameEdit.getValue().trim();
        if (name.isEmpty()) {
            status = Component.literal("Введите имя мира").withStyle(ChatFormatting.RED);
            return;
        }
        busy = true;
        createButton.active = false;
        backButton.active = false;
        status = Component.literal("Копирование золотой копии TheZone…");
        Thread worker = new Thread(() -> {
            try {
                Path saves = minecraft.getLevelSource().getBaseDir();
                String folder = GoldenCopy.uniqueName(saves, name);
                GoldenCopy.createWorld(goldenRoot(), saves, folder);
                // ванильный механизм имени мира (level.dat → LevelName), без своей работы с NBT
                try (LevelStorageSource.LevelStorageAccess access =
                             minecraft.getLevelSource().validateAndCreateAccess(folder)) {
                    access.renameLevel(folder);
                }
                minecraft.execute(() -> minecraft.createWorldOpenFlows().loadLevel(this, folder));
            } catch (Exception ex) {
                minecraft.execute(() -> {
                    busy = false;
                    createButton.active = true;
                    backButton.active = true;
                    status = Component.literal("Ошибка: " + ex.getMessage()).withStyle(ChatFormatting.RED);
                });
            }
        }, "zone-world-create");
        worker.setDaemon(true);
        worker.start();
    }

    /** Каталог золотых копий: <папка игры>/stalkerzone/golden; переопределение — -Dstalkerzone.golden=… */
    static Path goldenRoot() {
        String override = System.getProperty("stalkerzone.golden");
        if (override != null && !override.isBlank()) return Path.of(override);
        return FMLPaths.GAMEDIR.get().resolve("stalkerzone").resolve("golden");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, height / 2 - 76, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal("Независимая копия TheZone со своей историей симуляции")
                .withStyle(ChatFormatting.GRAY), width / 2, height / 2 - 62, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
        if (!status.getString().isEmpty()) drawWrapped(g, status, width / 2, height / 2 + 44, width - 40);
    }

    private void drawWrapped(GuiGraphics g, Component text, int centerX, int y, int maxWidth) {
        String rest = text.getString();
        while (!rest.isEmpty()) {
            String line = font.plainSubstrByWidth(rest, maxWidth);
            if (line.isEmpty()) line = rest.substring(0, 1);
            g.drawCenteredString(font, Component.literal(line).withStyle(text.getStyle()), centerX, y, 0xFFFFFF);
            rest = rest.substring(line.length()).stripLeading();
            y += font.lineHeight + 1;
        }
    }

    @Override
    public void onClose() {
        if (busy) return; // копирование идёт — выход только после завершения или ошибки
        minecraft.setScreen(parent);
    }
}
