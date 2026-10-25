package ru.stalkerzone.client;

import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import ru.stalkerzone.StalkerZoneMod;

/** Свои модели для четвероногих мутантов: поджарый пёс и химера. Анимацию ног и головы даёт QuadrupedModel. */
public final class MutantModels {
    public static final ModelLayerLocation DOG = new ModelLayerLocation(new ResourceLocation(StalkerZoneMod.MOD_ID, "blind_dog"), "main");
    public static final ModelLayerLocation CHIMERA = new ModelLayerLocation(new ResourceLocation(StalkerZoneMod.MOD_ID, "chimera"), "main");

    private MutantModels() {}

    public static class Quad<T extends Entity> extends QuadrupedModel<T> {
        public Quad(ModelPart root) {
            super(root, false, 4.0F, 4.0F, 2.0F, 2.0F, 24);
        }
    }

    /** Слепой пёс: длинное худое тело, вытянутая морда без глаз, высокие тонкие лапы. */
    public static LayerDefinition dog() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.5F, -2.5F, -5.0F, 5, 5, 5)
                        .texOffs(0, 10).addBox(-1.5F, -0.5F, -9.0F, 3, 3, 4)
                        .texOffs(16, 0).addBox(-2.5F, -4.5F, -2.0F, 1, 2, 1)
                        .texOffs(16, 0).addBox(1.5F, -4.5F, -2.0F, 1, 2, 1),
                PartPose.offset(0.0F, 11.0F, -7.0F));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(20, 4).addBox(-3.0F, -7.0F, -3.0F, 6, 14, 5),
                PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, (float) Math.PI / 2F, 0.0F, 0.0F));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 18).addBox(-1.0F, 0.0F, -1.0F, 2, 10, 2);
        root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-1.8F, 14.0F, 6.0F));
        root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(1.8F, 14.0F, 6.0F));
        root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-1.8F, 14.0F, -5.0F));
        root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(1.8F, 14.0F, -5.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** Химера: массивная грудь, вторая голова сбоку, мощные лапы. */
    public static LayerDefinition chimera() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0F, -3.0F, -6.0F, 6, 6, 6)
                        .texOffs(0, 12).addBox(-2.0F, 0.0F, -9.0F, 4, 3, 3)
                        .texOffs(0, 0).addBox(2.5F, -1.0F, -4.0F, 4, 4, 4, new CubeDeformation(-0.2F)),
                PartPose.offset(0.0F, 9.0F, -8.0F));
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(24, 0).addBox(-4.0F, -8.0F, -4.0F, 8, 16, 8)
                        .texOffs(24, 24).addBox(-3.0F, 6.0F, -2.0F, 6, 2, 4),
                PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, (float) Math.PI / 2F, 0.0F, 0.0F));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 18).addBox(-1.5F, 0.0F, -1.5F, 3, 10, 3);
        root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-2.5F, 14.0F, 7.0F));
        root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(2.5F, 14.0F, 7.0F));
        root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-2.5F, 14.0F, -6.0F));
        root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(2.5F, 14.0F, -6.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }
}
