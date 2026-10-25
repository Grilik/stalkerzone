package ru.stalkerzone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.stalkerzone.StalkerZoneMod;
import ru.stalkerzone.mutant.MutantEntity;
import ru.stalkerzone.mutant.MutantRegistry;
import ru.stalkerzone.mutant.MutantType;

/** Рендер мутантов: свои четвероногие модели, свиная модель для плоти и кабана, гуманоидная для остальных. */
@Mod.EventBusSubscriber(modid = StalkerZoneMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MutantRenderers {
    private MutantRenderers() {}

    private static ResourceLocation tex(MutantType t) {
        return new ResourceLocation(StalkerZoneMod.MOD_ID, "textures/entity/mutant/" + t.id + ".png");
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(MutantModels.DOG, MutantModels::dog);
        e.registerLayerDefinition(MutantModels.CHIMERA, MutantModels::chimera);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        for (MutantType t : MutantType.ALL) {
            var type = MutantRegistry.TYPES[t.ordinal()].get();
            switch (t.model) {
                case HUMANOID -> e.registerEntityRenderer(type, ctx -> new Humanoid(ctx, t));
                case PIG -> e.registerEntityRenderer(type, ctx -> new Simple(ctx, new PigModel<>(ctx.bakeLayer(ModelLayers.PIG)), t));
                case QUAD -> e.registerEntityRenderer(type, ctx -> new Simple(ctx,
                        new MutantModels.Quad<>(ctx.bakeLayer(t == MutantType.CHIMERA ? MutantModels.CHIMERA : MutantModels.DOG)), t));
            }
        }
    }

    static final class Simple extends MobRenderer<MutantEntity, EntityModel<MutantEntity>> {
        private final MutantType t;

        Simple(EntityRendererProvider.Context ctx, EntityModel<MutantEntity> model, MutantType t) {
            super(ctx, model, 0.5f * t.scale);
            this.t = t;
        }

        @Override
        public ResourceLocation getTextureLocation(MutantEntity e) { return tex(t); }

        @Override
        protected void scale(MutantEntity e, PoseStack pose, float partialTick) {
            pose.scale(t.scale, t.scale, t.scale);
        }
    }

    static final class Humanoid extends HumanoidMobRenderer<MutantEntity, HumanoidModel<MutantEntity>> {
        private final MutantType t;

        Humanoid(EntityRendererProvider.Context ctx, MutantType t) {
            super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
            this.t = t;
        }

        @Override
        public ResourceLocation getTextureLocation(MutantEntity e) { return tex(t); }

        @Override
        protected void scale(MutantEntity e, PoseStack pose, float partialTick) {
            pose.scale(t.scale, t.scale, t.scale);
        }
    }
}
