package ru.stalkerzone.npc;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import ru.stalkerzone.StalkerZoneMod;

/** Гуманоидная модель со скином по фракции. */
public class StalkerRenderer extends HumanoidMobRenderer<StalkerEntity, HumanoidModel<StalkerEntity>> {
    private static final String[] SKINS = {"loner", "duty", "freedom", "bandit", "scientist", "military", "monolith"};

    public StalkerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
        // костюмы группировок видны на NPC (v1.13): слой брони поверх скина
        this.addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)), ctx.getModelManager()));
    }

    /** С огнестрелом TaCZ сталкер держит оружие двумя руками, а в бою вскидывает к плечу. */
    @Override
    public void render(StalkerEntity e, float yaw, float pt, com.mojang.blaze3d.vertex.PoseStack ps,
                       net.minecraft.client.renderer.MultiBufferSource buf, int light) {
        HumanoidModel<StalkerEntity> m = this.getModel();
        if (ru.stalkerzone.compat.Tacz.isGun(e.getMainHandItem())) {
            HumanoidModel.ArmPose pose = e.isAggressive() ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.CROSSBOW_HOLD;
            m.rightArmPose = pose;
            m.leftArmPose = pose;
        } else {
            m.rightArmPose = e.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
            m.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        }
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(StalkerEntity e) {
        String name = e.isTrader() ? "trader" : SKINS[Math.floorMod(e.faction(), SKINS.length)];
        return new ResourceLocation(StalkerZoneMod.MOD_ID, "textures/entity/" + name + ".png");
    }
}
