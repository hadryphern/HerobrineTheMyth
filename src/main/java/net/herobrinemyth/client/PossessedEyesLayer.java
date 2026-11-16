package net.herobrinemyth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.ChickenModel;
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

/** Vanilla mobs, with his eyes: zombies, skeletons, villagers, pigs, cows, sheep, chickens. Babies too. */
public class PossessedEyesLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static Field quadHead, chickenHead, scaleHead, babyHeadScale, babyY, babyZ;
    private static boolean reflectionFailed;

    public PossessedEyesLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    private static void reflect() {
        if (quadHead != null || reflectionFailed) return;
        try {
            quadHead = ObfuscationReflectionHelper.findField(QuadrupedModel.class, "f_103492_");
            chickenHead = ObfuscationReflectionHelper.findField(ChickenModel.class, "f_102381_");
            scaleHead = ObfuscationReflectionHelper.findField(AgeableListModel.class, "f_102007_");
            babyHeadScale = ObfuscationReflectionHelper.findField(AgeableListModel.class, "f_102010_");
            babyY = ObfuscationReflectionHelper.findField(AgeableListModel.class, "f_170338_");
            babyZ = ObfuscationReflectionHelper.findField(AgeableListModel.class, "f_170339_");
        } catch (Exception e) {
            reflectionFailed = true;
        }
    }

    private static ModelPart part(Field f, Object model) {
        try {
            return f == null ? null : (ModelPart) f.get(model);
        } catch (Exception e) {
            return null;
        }
    }

    /** A baby's head is drawn smaller and lower than the model says: do the same before the eyes. */
    private static boolean babyHead(PoseStack pose, Object model) {
        if (!(model instanceof AgeableListModel<?> m) || !m.young) return true;
        if (reflectionFailed) return false;
        try {
            if (scaleHead.getBoolean(m)) {
                float f = 1.5F / babyHeadScale.getFloat(m);
                pose.scale(f, f, f);
            }
            pose.translate(0F, babyY.getFloat(m) / 16F, babyZ.getFloat(m) / 16F);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T e, float limbSwing, float limbAmount,
                       float partialTick, float age, float headYaw, float headPitch) {
        if (!ClientHorror.isPossessed(e.getId()) || e.isInvisible()) return;
        reflect();
        M model = this.getParentModel();
        pose.pushPose();
        try {
            // villagers are scaled as a whole by their renderer, so they need nothing extra
            if (model instanceof VillagerModel<?> v) {
                v.getHead().translateAndRotate(pose);
                EyeQuads.face(pose, buffers, true, light, -4F, -6F, -3F, -1F, 1F, 3F);
                return;
            }
            if (!babyHead(pose, model)) return;
            if (model instanceof ZombieVillagerModel<?> zv) {
                zv.getHead().translateAndRotate(pose);
                EyeQuads.face(pose, buffers, true, light, -4F, -6F, -3F, -1F, 1F, 3F);
            } else if (model instanceof HeadedModel headed) {
                headed.getHead().translateAndRotate(pose);
                EyeQuads.face(pose, buffers, true, light, -4F, -4F, -3F, -1F, 1F, 3F);
            } else if (model instanceof ChickenModel<?> c) {
                ModelPart head = part(chickenHead, c);
                if (head == null) return;
                head.translateAndRotate(pose);
                EyeQuads.face(pose, buffers, true, light, -2F, -5F, -2F, -1F, 1F, 2F);
            } else if (model instanceof QuadrupedModel<?> q) {
                ModelPart head = part(quadHead, q);
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
