package dev.jensh.createinduction;

import com.simibubi.create.api.stress.BlockStressValues;
import dev.jensh.createinduction.content.coil.CoilPhysics;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@Mod(CreateInduction.ID)
public final class CreateInduction {
    public static final String ID = "create_induction";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final boolean POWER_GRID = ModList.get().isLoaded("powergrid");

    public CreateInduction(IEventBus modBus, ModContainer container) {
        ModContent.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerCapabilities);
        SableEventPlatform.INSTANCE.onPhysicsTick(CoilPhysics::physicsTick);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() ->
            BlockStressValues.IMPACTS.register(ModContent.DYNAMO_BEARING.get(), Config.BEARING_BASE_IMPACT::get));
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModContent.INDUCTION_COIL_ENTITY.get(),
            (coil, side) -> coil.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModContent.SLIP_RING_ENTITY.get(),
            (ring, side) -> ring.energy());
    }
}
