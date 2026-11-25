package dev.jensh.createinduction.test;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("create_induction_test")
public final class CreateInductionTest {
    public CreateInductionTest() {
        if (FMLEnvironment.dist.isClient() && System.getProperty("createinduction.showcase") != null) Showcase.register();
    }
}
