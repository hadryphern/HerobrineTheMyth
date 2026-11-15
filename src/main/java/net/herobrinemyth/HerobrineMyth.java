package net.herobrinemyth;

import com.mojang.logging.LogUtils;
import net.herobrinemyth.entity.HerobrineEntity;
import net.herobrinemyth.network.Net;
import net.herobrinemyth.registry.ModEntities;
import net.herobrinemyth.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Herobrine: The Myth.
 * He was never removed.
 */
@Mod(HerobrineMyth.MODID)
public class HerobrineMyth {
    public static final String MODID = "herobrinemyth";
    public static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("removal")
    public HerobrineMyth() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::attributes);
        modBus.addListener(this::creativeTabs);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "herobrinemyth-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, "herobrinemyth-server.toml");

        MinecraftForge.EVENT_BUS.register(net.herobrinemyth.server.ServerEvents.class);
        MinecraftForge.EVENT_BUS.register(net.herobrinemyth.server.Artifacts.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
        LOGGER.info("He is here.");
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.HEROBRINE.get(), HerobrineEntity.createAttributes().build());
    }

    private void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.HEROBRINE_SPAWN_EGG);
        }
    }
}
