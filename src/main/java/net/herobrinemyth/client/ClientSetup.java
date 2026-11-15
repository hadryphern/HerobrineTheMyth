package net.herobrinemyth.client;

import net.herobrinemyth.HerobrineMyth;
import net.herobrinemyth.registry.ModEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineMyth.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    /** Humanoid mobs that can show his eyes when possessed. */
    private static final EntityType<?>[] POSSESSABLE = {
            EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.SKELETON, EntityType.STRAY,
            EntityType.WITHER_SKELETON, EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE,
            EntityType.VILLAGER, EntityType.ZOMBIE_VILLAGER, EntityType.PIG, EntityType.COW, EntityType.SHEEP
    };

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.HEROBRINE.get(), HerobrineRenderer::new);
        event.registerEntityRenderer(ModEntities.EYES.get(), HerobrineEyesRenderer::new);
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void layers(EntityRenderersEvent.AddLayers event) {
        OldSchool.createRenderer(event.getContext());
        for (EntityType<?> type : POSSESSABLE) {
            try {
                LivingEntityRenderer r = event.getRenderer((EntityType) type);
                if (r != null) {
                    r.addLayer(new PossessedEyesLayer(r));
                }
            } catch (ClassCastException ignored) {
            }
        }
    }

    @SubscribeEvent
    public static void skies(net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent event) {
        event.register(new net.minecraft.resources.ResourceLocation("overworld"), new Sky.Overcast());
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("horror", HorrorOverlay::render);
    }

    private ClientSetup() {}
}
