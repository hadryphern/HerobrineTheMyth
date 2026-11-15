package net.herobrinemyth.registry;

import net.herobrinemyth.HerobrineMyth;
import net.herobrinemyth.entity.HerobrineEntity;
import net.herobrinemyth.entity.HerobrineEyesEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HerobrineMyth.MODID);

    public static final RegistryObject<EntityType<HerobrineEntity>> HEROBRINE = ENTITIES.register("herobrine",
            () -> EntityType.Builder.<HerobrineEntity>of(HerobrineEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build("herobrine"));

    public static final RegistryObject<EntityType<HerobrineEyesEntity>> EYES = ENTITIES.register("herobrine_eyes",
            () -> EntityType.Builder.<HerobrineEyesEntity>of(HerobrineEyesEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(10)
                    .updateInterval(5)
                    .fireImmune()
                    .noSave()
                    .build("herobrine_eyes"));

    private ModEntities() {}
}
