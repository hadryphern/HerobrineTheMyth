package net.herobrinemyth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.ZombieVillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/** Vanilla mobs, with his eyes: zombies, skeletons, villagers, pigs, cows, sheep. */
public class PossessedEyesLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static Field quadHead;

    public PossessedEyesLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    private static ModelPart quadrupedHead(QuadrupedModel<?> model) {
        try {
            if (quadHead == null) quadHead = ObfuscationReflectionHelper.findField(QuadrupedModel.class, "f_103492_");
            return (ModelPart) quadHead.get(model);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T e, float limbSwing, float limbAmount,
                       float partialTick, float age, float headYaw, float headPitch) {
        if (!ClientHorror.isPossessed(e.getId()) || e.isInvisible()) return;
        M model = this.getParentModel();
        pose.pushPose();
        try {
            if (model instanceof VillagerModel<?> v) {
                v.getHead().translateAndRotate(pose);
                EyeQuads.face(pose, buffers, true, light, -4F, -6F, -3F, -1F, 1F, 3F);
            } else if (model instanceof ZombieVillagerModel<?> zv) {
                zv.getHead().translateAndRotate(pose);
                EyeQuads.face(pose, buffers, true, light, -4F, -6F, -3F, -1F, 1F, 3F);
            } else if (model instanceof HeadedModel headed) {
                headed.getHead().translateAndRotate(pose);
                EyeQuads.face(pose, buffers, true, light, -4F, -4F, -3F, -1F, 1F, 3F);
            } else if (model instanceof QuadrupedModel<?> q) {
                ModelPart head = quadrupedHead(q);
                if (head == null) return;
                head.translateAndRotate(pose);
                if (e instanceof Pig) EyeQuads.face(pose, buffers, true, light, -8F, -1F, -4F, -2F, 2F, 4F);
                else if (e instanceof Cow) EyeQuads.face(pose, buffers, true, light, -6F, -2F, -4F, -2F, 2F, 4F);
                else if (e instanceof Sheep) EyeQuads.face(pose, buffers, true, light, -6F, -2F, -3F, -1F, 1F, 3F);
            }
        } finally {
            pose.popPose();
        }
    }
}
