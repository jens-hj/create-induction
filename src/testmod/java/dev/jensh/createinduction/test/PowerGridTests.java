package dev.jensh.createinduction.test;

import com.mojang.logging.LogUtils;
import dev.jensh.createinduction.content.bearing.DynamoBearingBlockEntity;
import dev.jensh.createinduction.content.coil.InductionCoilBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** The coil as a Power Grid source: wired to a resistor, the circuit carries the load instead of Forge Energy. */
@GameTestHolder("create_induction")
@PrefixGameTestTemplate(false)
public class PowerGridTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos COIL = GeneratorTests.HUB.east(4);
    private static final BlockPos RESISTOR = GeneratorTests.HUB.east(6);

    @GameTest(template = "empty", batch = "powergrid", timeoutTicks = 500)
    public static void wiredCoilFeedsAResistorAndLoadsTheShaft(GameTestHelper test) {
        if (!net.neoforged.fml.ModList.get().isLoaded("powergrid")) {
            test.succeed();
            return;
        }
        GeneratorTests.buildRotor(test, 96);
        GeneratorTests.placeCoils(test);
        test.setBlock(RESISTOR, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("powergrid", "creative_resistor")));
        double[] mark = new double[3];
        PowerGridRig.setResistance(test, RESISTOR, 2.0);
        test.runAfterDelay(5, () -> GeneratorTests.bearing(test).assembleNextTick = true);
        // The other three coils are left to fill up, so only the wired one loads the rotor.
        test.runAfterDelay(200, () -> mark[0] = GeneratorTests.bearing(test).totalWork());
        test.runAfterDelay(260, () -> {
            mark[1] = (GeneratorTests.bearing(test).totalWork() - mark[0]) / 3.0;
            PowerGridRig.wire(test, COIL, RESISTOR, 0);
            PowerGridRig.wire(test, COIL, RESISTOR, 1);
        });
        test.runAfterDelay(320, () -> mark[0] = GeneratorTests.bearing(test).totalWork());
        test.runAfterDelay(440, () -> {
            DynamoBearingBlockEntity bearing = GeneratorTests.bearing(test);
            InductionCoilBlockEntity coil = GeneratorTests.coil(test, COIL);
            double ohms = PowerGridRig.resistance(test, RESISTOR);
            double loaded = (bearing.totalWork() - mark[0]) / 6.0;
            double heat = coil.current() * coil.current() * ohms;
            LOGGER.info("[induction] powergrid: resistor {} ohm, coil {} V {} A {} W, resistor heat {} W, shaft {} W (before {} W), stored {} FE",
                ohms, coil.voltage(), coil.current(), coil.power(), heat, loaded, mark[1], coil.energy().getEnergyStored());
            test.assertTrue(Math.abs(coil.current()) > 0.5, "Current should flow through the wired coil, was " + coil.current() + " A");
            test.assertTrue(coil.power() > 10, "The wired coil should deliver power, was " + coil.power() + " W");
            test.assertTrue(loaded > mark[1] + coil.power() * 0.5, "The circuit load should brake the rotor: " + loaded + " W vs " + mark[1] + " W");
            test.succeed();
        });
    }
}
