package dev.jensh.createinduction.test;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.jensh.createinduction.Config;
import dev.jensh.createinduction.ModContent;
import dev.jensh.createinduction.content.bearing.DynamoBearingBlock;
import dev.jensh.createinduction.content.coil.InductionCoilBlock;
import dev.jensh.createinduction.content.coil.InductionCoilBlockEntity;
import dev.jensh.createinduction.content.magnet.PermanentMagnetBlock;
import dev.jensh.createinduction.content.slipring.SlipRingBlock;
import dev.jensh.createinduction.content.slipring.SlipRingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** The armature on the rotor, the magnets standing around it, and slip rings to bring the electricity out. */
@GameTestHolder("create_induction")
@PrefixGameTestTemplate(false)
public class InsideOutTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos MOTOR = new BlockPos(7, 1, 7);
    private static final BlockPos BEARING = new BlockPos(7, 2, 7);
    private static final BlockPos HUB = new BlockPos(7, 3, 7);
    private static final BlockPos ROTOR_RING = HUB.above();
    private static final BlockPos EAST_COIL = ROTOR_RING.east();
    private static final BlockPos WEST_COIL = ROTOR_RING.west();
    private static final BlockPos FIXED_RING = ROTOR_RING.above();

    /** A hub carrying a slip ring between two outward-facing coils, inside four fixed magnets. */
    private static void build(GameTestHelper test) {
        test.setBlock(MOTOR, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"))
            .defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        test.setBlock(BEARING, ModContent.DYNAMO_BEARING.get().defaultBlockState().setValue(DynamoBearingBlock.FACING, Direction.UP));
        test.setBlock(HUB, Blocks.IRON_BLOCK);
        test.setBlock(ROTOR_RING, ModContent.SLIP_RING.get().defaultBlockState().setValue(SlipRingBlock.FACING, Direction.UP));
        test.setBlock(EAST_COIL, ModContent.INDUCTION_COIL.get().defaultBlockState().setValue(InductionCoilBlock.FACING, Direction.EAST));
        test.setBlock(WEST_COIL, ModContent.INDUCTION_COIL.get().defaultBlockState().setValue(InductionCoilBlock.FACING, Direction.WEST));
        BlockPos from = test.absolutePos(WEST_COIL.below());
        BlockPos to = test.absolutePos(EAST_COIL);
        test.getLevel().addFreshEntity(new SuperGlueEntity(test.getLevel(),
            new AABB(from.getX(), from.getY(), from.getZ(), to.getX() + 1, to.getY() + 1, to.getZ() + 1)));
        // Poles alternate around the ring so every coil sees north, south, north, south.
        test.setBlock(ROTOR_RING.east(3), ModContent.PERMANENT_MAGNET.get().defaultBlockState().setValue(PermanentMagnetBlock.FACING, Direction.WEST));
        test.setBlock(ROTOR_RING.west(3), ModContent.PERMANENT_MAGNET.get().defaultBlockState().setValue(PermanentMagnetBlock.FACING, Direction.EAST));
        test.setBlock(ROTOR_RING.north(3), ModContent.PERMANENT_MAGNET.get().defaultBlockState().setValue(PermanentMagnetBlock.FACING, Direction.NORTH));
        test.setBlock(ROTOR_RING.south(3), ModContent.PERMANENT_MAGNET.get().defaultBlockState().setValue(PermanentMagnetBlock.FACING, Direction.SOUTH));
    }

    private static void spin(GameTestHelper test, int rpm) {
        ((CreativeMotorBlockEntity) test.getBlockEntity(MOTOR)).generatedSpeed.setValue(rpm);
    }

    /** Where a block that was assembled into the rotor now lives, counted from the bearing's plate. */
    private static BlockPos onRotor(GameTestHelper test, BlockPos relative) {
        BlockPos plate = GeneratorTests.bearing(test).platePos();
        return plate.offset(relative.subtract(BEARING));
    }

    @GameTest(template = "empty", batch = "insideout", timeoutTicks = 500)
    public static void coilsOnTheRotorFeedThroughSlipRings(GameTestHelper test) {
        Config.ENERGY_BUFFER.set(40000000);
        build(test);
        BlockPos sink = FIXED_RING.above();
        BlockPos[] rotorRing = new BlockPos[1];
        test.runAfterDelay(5, () -> GeneratorTests.bearing(test).assembleNextTick = true);
        test.runAfterDelay(25, () -> {
            test.assertTrue(GeneratorTests.bearing(test).isAssembled(), "Bearing should have assembled");
            rotorRing[0] = onRotor(test, ROTOR_RING);
            test.setBlock(FIXED_RING, ModContent.SLIP_RING.get().defaultBlockState().setValue(SlipRingBlock.FACING, Direction.DOWN));
            test.setBlock(sink, ModContent.INDUCTION_COIL.get().defaultBlockState().setValue(InductionCoilBlock.FACING, Direction.UP));
            spin(test, 96);
        });
        test.runAfterDelay(300, () -> {
            SlipRingBlockEntity fixed = (SlipRingBlockEntity) test.getBlockEntity(FIXED_RING);
            SlipRingBlockEntity turning = (SlipRingBlockEntity) test.getLevel().getBlockEntity(rotorRing[0]);
            InductionCoilBlockEntity store = (InductionCoilBlockEntity) test.getBlockEntity(sink);
            LOGGER.info("[induction] inside out: rotor {} rpm, rings linked {}, passing {} FE/t, sink holds {} FE",
                GeneratorTests.bearing(test).rotorSpeed(), fixed.isLinked(), fixed.rate(), store.energy().getEnergyStored());
            Config.ENERGY_BUFFER.set(4000);
            test.assertTrue(turning != null && fixed.partner() == turning, "The two slip rings should be in contact");
            test.assertTrue(Math.abs(GeneratorTests.bearing(test).rotorSpeed()) > 40, "The rotor should be turning");
            test.assertTrue(store.energy().getEnergyStored() > 2000, "Energy from the turning coils should arrive on the fixed side, got "
                + store.energy().getEnergyStored() + " FE");
            test.succeed();
        });
    }

    @GameTest(template = "empty", batch = "insideoutgrid", timeoutTicks = 500)
    public static void slipRingsCarryPowerGridCurrent(GameTestHelper test) {
        if (!ModList.get().isLoaded("powergrid")) {
            test.succeed();
            return;
        }
        build(test);
        test.setBlock(WEST_COIL, Blocks.IRON_BLOCK); // one coil, so the circuit is unambiguous
        BlockPos resistor = FIXED_RING.above(2);
        BlockPos[] coil = new BlockPos[1];
        test.runAfterDelay(5, () -> GeneratorTests.bearing(test).assembleNextTick = true);
        test.runAfterDelay(25, () -> {
            test.assertTrue(GeneratorTests.bearing(test).isAssembled(), "Bearing should have assembled");
            coil[0] = onRotor(test, EAST_COIL);
            test.assertTrue(test.getLevel().getBlockEntity(coil[0]) instanceof InductionCoilBlockEntity, "The coil should be on the rotor at " + coil[0]);
            BlockPos ring = onRotor(test, ROTOR_RING);
            test.setBlock(FIXED_RING, ModContent.SLIP_RING.get().defaultBlockState().setValue(SlipRingBlock.FACING, Direction.DOWN));
            test.setBlock(resistor, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("powergrid", "creative_resistor")));
            PowerGridRig.setResistance(test, resistor, 2.0);
            for (int terminal = 0; terminal < 2; terminal++) {
                PowerGridRig.wireAbsolute(test, coil[0], ring, terminal);
                PowerGridRig.wireAbsolute(test, test.absolutePos(FIXED_RING), test.absolutePos(resistor), terminal);
            }
        });
        test.runAfterDelay(60, () -> spin(test, 96));
        test.runAfterDelay(300, () -> {
            InductionCoilBlockEntity turning = (InductionCoilBlockEntity) test.getLevel().getBlockEntity(coil[0]);
            LOGGER.info("[induction] inside out grid: rotor {} rpm, coil {} V {} A {} W", GeneratorTests.bearing(test).rotorSpeed(),
                turning.voltage(), turning.current(), turning.power());
            test.assertTrue(Math.abs(turning.current()) > 0.5, "Current should flow from the turning coil through the slip rings, was "
                + turning.current() + " A");
            test.assertTrue(turning.power() > 5, "The turning coil should deliver power, was " + turning.power() + " W");
            test.succeed();
        });
    }
}
