package ru.stalkerzone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import ru.stalkerzone.StalkerZoneMod;
import ru.stalkerzone.npc.DeathBackpackEntity;

/** Рюкзак на земле (П-10): объёмная модель — мешок, клапан, карман, скатка сверху, лямки; стоит неподвижно. */
public class DeathBackpackRenderer extends EntityRenderer<DeathBackpackEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(StalkerZoneMod.MOD_ID, "death_backpack"), "main");
    private static final ResourceLocation TEX = new ResourceLocation(StalkerZoneMod.MOD_ID, "textures/entity/death_backpack.png");
    private final ModelPart root;

    public DeathBackpackRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.root = ctx.bakeLayer(LAYER);
        this.shadowRadius = 0.3f;
    }

    /** Модель в пикселях (1/16 блока), низ на y = 0 модели. */
    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        r.addOrReplaceChild("bag", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -9, -2.5f, 8, 9, 5), PartPose.ZERO);
        r.addOrReplaceChild("flap", CubeListBuilder.create().texOffs(0, 14).addBox(-4.5f, -9.5f, -3, 9, 4, 1), PartPose.ZERO);
        r.addOrReplaceChild("pocket", CubeListBuilder.create().texOffs(26, 0).addBox(-3, -5, -3.5f, 6, 4, 1), PartPose.ZERO);
        r.addOrReplaceChild("roll", CubeListBuilder.create().texOffs(0, 20).addBox(-5, -12, -1.5f, 10, 3, 3), PartPose.ZERO);
        r.addOrReplaceChild("strap_l", CubeListBuilder.create().texOffs(40, 0).addBox(-3, -9, 2.5f, 1, 8, 1), PartPose.ZERO);
        r.addOrReplaceChild("strap_r", CubeListBuilder.create().texOffs(40, 0).addBox(2, -9, 2.5f, 1, 8, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void render(DeathBackpackEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180 - e.getYRot()));
        ps.mulPose(Axis.XP.rotationDegrees(180));   // модель задана «вверх ногами», как у мобов
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutout(TEX));
        root.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override public ResourceLocation getTextureLocation(DeathBackpackEntity e) { return TEX; }
}
