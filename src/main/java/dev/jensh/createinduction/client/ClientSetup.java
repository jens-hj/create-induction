package dev.jensh.createinduction.client;

import dev.jensh.createinduction.CreateInduction;
import dev.jensh.createinduction.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = CreateInduction.ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModContent.DYNAMO_BEARING_ENTITY.get(), DynamoBearingRenderer::new);
    }

    /** Create's hold-shift item descriptions, from the tooltip.summary lang keys. */
    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (var item : java.util.List.of(ModContent.PERMANENT_MAGNET_ITEM.get(), ModContent.INDUCTION_COIL_ITEM.get(), ModContent.DYNAMO_BEARING_ITEM.get(), ModContent.SLIP_RING_ITEM.get())) {
                com.simibubi.create.foundation.item.TooltipModifier.REGISTRY.register(item,
                    new com.simibubi.create.foundation.item.ItemDescription.Modifier(item, net.createmod.catnip.lang.FontHelper.Palette.STANDARD_CREATE));
            }
        });
    }
}
