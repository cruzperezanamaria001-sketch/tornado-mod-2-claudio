package com.tornadomod;

import com.tornadomod.entity.StormEntity;
import com.tornadomod.entity.TornadoEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TornadoMod.MODID);

    public static final RegistryObject<EntityType<TornadoEntity>> TORNADO = ENTITIES.register("tornado",
            () -> EntityType.Builder.<TornadoEntity>of(TornadoEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .fireImmune()
                    .build("tornado"));

    public static final RegistryObject<EntityType<StormEntity>> STORM = ENTITIES.register("storm",
            () -> EntityType.Builder.<StormEntity>of(StormEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .fireImmune()
                    .build("storm"));
}
