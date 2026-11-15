package net.herobrinemyth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.herobrinemyth.HerobrineMyth;
import net.herobrinemyth.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.Deadmau5EarsLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Back to how it looked: the classic textures, and everyone is Steve. */
@Mod.EventBusSubscriber(modid = HerobrineMyth.MODID, value = Dist.CLIENT)
public final class OldSchool {
    private static final String CLASSIC_PACK = "programmer_art";
    private static boolean packChecked;
    private static SteveRenderer steve;

    /** Steve's wide model, Steve's skin, no capes, no ears. */
    static final class SteveRenderer extends PlayerRenderer {
        SteveRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, false);
            this.layers.removeIf(l -> l instanceof CapeLayer || l instanceof Deadmau5EarsLayer);
        }

        @Override
        public ResourceLocation getTextureLocation(AbstractClientPlayer player) {
            return EyeQuads.STEVE;
        }
    }

    static void createRenderer(EntityRendererProvider.Context ctx) {
        steve = new SteveRenderer(ctx);
    }

    // ------------------------------------------------------------------ textures

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Init.Post event) {
        if (packChecked || !(event.getScreen() instanceof TitleScreen)) return;
        packChecked = true;
        Minecraft m = Minecraft.getInstance();
        if (Config.OLD_LIGHTING.get() && m.options.ambientOcclusion().get()) {
            m.options.ambientOcclusion().set(false);
            m.options.save();
        }
        ClassicPack.ensure(m);
        if (!Config.OLD_TEXTURES.get()) return;
        Minecraft mc = Minecraft.getInstance();
        PackRepository repo = mc.getResourcePackRepository();
        if (!repo.getAvailableIds().contains(CLASSIC_PACK) || repo.getSelectedIds().contains(CLASSIC_PACK)) return;
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        // right above the vanilla textures, below any pack the player added
        int at = selected.indexOf("vanilla");
        selected.add(at < 0 ? 0 : at + 1, CLASSIC_PACK);
        repo.setSelected(selected);
        mc.options.updateResourcePacks(repo);
        HerobrineMyth.LOGGER.info("Classic textures enabled.");
    }

    // ------------------------------------------------------------------ skins

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (steve == null || event.getRenderer() instanceof SteveRenderer || !Config.STEVE_ONLY.get()) return;
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
        event.setCanceled(true);
        steve.render(player, player.getYRot(), event.getPartialTick(), event.getPoseStack(),
                event.getMultiBufferSource(), event.getPackedLight());
    }

    private static boolean renderingArm;

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        // Steve's own renderer fires this event again: let that one through
        if (renderingArm || steve == null || !Config.STEVE_ONLY.get()) return;
        event.setCanceled(true);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        renderingArm = true;
        try {
            net.minecraft.client.model.PlayerModel<AbstractClientPlayer> model = steve.getModel();
            boolean right = event.getArm() == HumanoidArm.RIGHT;
            model.attackTime = 0F;
            model.crouching = false;
            model.swimAmount = 0F;
            model.setupAnim(event.getPlayer(), 0F, 0F, 0F, 0F, 0F);
            net.minecraft.client.model.geom.ModelPart arm = right ? model.rightArm : model.leftArm;
            net.minecraft.client.model.geom.ModelPart sleeve = right ? model.rightSleeve : model.leftSleeve;
            arm.xRot = 0F;
            arm.render(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entitySolid(EyeQuads.STEVE)), event.getPackedLight(),
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            sleeve.xRot = 0F;
            sleeve.render(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(EyeQuads.STEVE)), event.getPackedLight(),
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        } finally {
            renderingArm = false;
        }
    }

    private OldSchool() {}
}
