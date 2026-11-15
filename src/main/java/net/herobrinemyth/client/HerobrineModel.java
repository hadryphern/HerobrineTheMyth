package net.herobrinemyth.client;

import net.herobrinemyth.entity.HerobrineEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The player model, wrong in small ways. */
public class HerobrineModel extends PlayerModel<HerobrineEntity> {

    public HerobrineModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(HerobrineEntity e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
        HerobrineEntity.Mode mode = e.getMode();

        if (mode.glide) {
            // perfectly still. no breathing, no idle sway.
            this.rightArm.xRot = 0F;
            this.leftArm.xRot = 0F;
            this.rightArm.zRot = 0F;
            this.leftArm.zRot = 0F;
            this.rightLeg.xRot = 0F;
            this.leftLeg.xRot = 0F;
        }
        if (mode == HerobrineEntity.Mode.CHASE) {
            // he runs without moving his arms
            this.rightArm.xRot = 0F;
            this.leftArm.xRot = 0F;
            this.rightArm.zRot = 0.05F;
            this.leftArm.zRot = -0.05F;
            this.body.xRot = 0.25F;
            this.head.xRot = Mth.clamp(this.head.xRot - 0.2F, -1F, 1F);
        }
        if (e.tilted()) {
            this.head.zRot = 0.5F;
        }
        this.hat.copyFrom(this.head);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
        this.jacket.copyFrom(this.body);
    }
}
