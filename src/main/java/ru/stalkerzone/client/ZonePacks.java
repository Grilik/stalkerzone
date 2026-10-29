package ru.stalkerzone.client;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;

import java.nio.file.Path;

/**
 * Встроенный текстурпак «Stalker Zone: текстуры»: лежит внутри мода (resourcepacks/stalker_textures)
 * и появляется в меню «Пакеты ресурсов». Включается по желанию, как обычный пак.
 */
public final class ZonePacks {
    private ZonePacks() {}

    public static void addPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        Path path = ModList.get().getModFileById("stalkerzone").getFile().findResource("resourcepacks/stalker_textures");
        Pack pack = Pack.readMetaAndCreate("builtin/stalker_textures", Component.literal("Stalker Zone: текстуры"), false,
                id -> new PathPackResources(id, path, true), PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
        // личный ресурспак игрока: текстуры блоков Зоны из его копии игры (tools/xray/make_pack.py), включён всегда
        java.io.File orig = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("stalkerzone").resolve("original_textures.zip").toFile();
        if (orig.isFile()) {
            Pack op = Pack.readMetaAndCreate("stalkerzone_original", Component.literal("Зона: оригинальные текстуры"), true,
                    id -> new net.minecraft.server.packs.FilePackResources(id, orig, false), PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.DEFAULT);
            if (op != null) event.addRepositorySource(consumer -> consumer.accept(op));
        }
    }
}
