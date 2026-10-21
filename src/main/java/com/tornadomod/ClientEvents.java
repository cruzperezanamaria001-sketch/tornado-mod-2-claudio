package com.tornadomod;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Los tornados no tienen modelo: se dibujan solo con particulas (ver TornadoEntity / StormEntity).
 * Aqui solo registramos un renderer vacio.
 */
@Mod.EventBusSubscriber(modid = TornadoMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TORNADO.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.STORM.get(), NoopRenderer::new);
    }
}
