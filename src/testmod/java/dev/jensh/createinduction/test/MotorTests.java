package dev.jensh.createinduction.test;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.jensh.createinduction.Config;
import dev.jensh.createinduction.ModContent;
import dev.jensh.createinduction.content.bearing.DynamoBearingBlock;
import dev.jensh.createinduction.content.bearing.DynamoBearingBlockEntity;
import dev.jensh.createinduction.content.coil.InductionCoilBlock;
import dev.jensh.createinduction.content.coil.InductionCoilBlockEntity;
import dev.jensh.createinduction.content.magnet.PermanentMagnetBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * The generator run backwards: a free rotor of magnets, coils around it fed with energy, and Create's redstone
 * contacts as the commutator that switches each coil on just after a magnet has gone by.
 */
@GameTestHolder("create_induction")
@PrefixGameTestTemplate(false)
public class MotorTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos MOTOR = new BlockPos(7, 2, 7);
    private static final BlockPos BEARING = MOTOR.above();
    private static final BlockPos HUB = BEARING.above();
    private static final Direction[] ARMS = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};

    /** A point given for the east arm, turned to arm {@code k}; y is counted from the hub. */
    private static BlockPos at(int k, int x, int y, int z) {
        for (int i = 0; i < k; i++) {
            int t = x;
            x = -z;
            z = t;
        }
        return HUB.offset(x, y, z);
    }

    private static Block create(String name) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", name));
    }

    private static void glue(GameTestHelper test, BlockPos a, BlockPos b) {
        BlockPos from = test.absolutePos(new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())));
        BlockPos to = test.absolutePos(new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
        test.getLevel().addFreshEntity(new SuperGlueEntity(test.getLevel(),
            new AABB(from.getX(), from.getY(), from.getZ(), to.getX() + 1, to.getY() + 1, to.getZ() + 1)));
    }

    private static InductionCoilBlockEntity coil(GameTestHelper test, int k) {
        return (InductionCoilBlockEntity) test.getBlockEntity(at(k, 4, 0, 0));
    }

    private static void run(GameTestHelper test, String name, int kick, boolean powered) {
        Config.ENERGY_BUFFER.set(40000000);
        test.setBlock(MOTOR, create("creative_motor").defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        test.setBlock(BEARING, ModContent.DYNAMO_BEARING.get().defaultBlockState().setValue(DynamoBearingBlock.FACING, Direction.UP));
        test.setBlock(HUB, Blocks.IRON_BLOCK);
        for (int k = 0; k < 4; k++) {
            Direction arm = ARMS[k];
            // Rotor: arm, magnet with its north pole outwards, and a contact hanging under the magnet.
            test.setBlock(at(k, 1, 0, 0), Blocks.IRON_BLOCK);
            test.setBlock(at(k, 2, 0, 0), ModContent.PERMANENT_MAGNET.get().defaultBlockState().setValue(PermanentMagnetBlock.FACING, arm));
            test.setBlock(at(k, 2, -1, 0), create("redstone_contact").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN));
            glue(test, HUB, at(k, 2, 0, 0));
            glue(test, at(k, 2, 0, 0), at(k, 2, -1, 0));
            // Stator: a contact the rotor's one passes over a little after its magnet has passed the coil,
            // redstone from it to the block under the coil, and the coil facing the rotor.
            test.setBlock(at(k, 2, -2, 1), create("redstone_contact").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
            for (int[] dust : new int[][] {{2, 2}, {3, 2}, {4, 2}}) {
                test.setBlock(at(k, dust[0], -3, dust[1]), Blocks.STONE);
                test.setBlock(at(k, dust[0], -2, dust[1]), Blocks.REDSTONE_WIRE);
            }
            test.setBlock(at(k, 4, -3, 1), Blocks.STONE);
            test.setBlock(at(k, 4, -2, 1), Blocks.STONE);
            test.setBlock(at(k, 4, -1, 1), Blocks.REDSTONE_WIRE);
            test.setBlock(at(k, 4, -1, 0), Blocks.STONE);
            test.setBlock(at(k, 4, 0, 0), ModContent.INDUCTION_COIL.get().defaultBlockState().setValue(InductionCoilBlock.FACING, arm.getOpposite()));
        }
        ((CreativeMotorBlockEntity) test.getBlockEntity(MOTOR)).generatedSpeed.setValue(kick);
        test.runAfterDelay(5, () -> ((DynamoBearingBlockEntity) test.getBlockEntity(BEARING)).assembleNextTick = true);
        test.runAfterDelay(40, () -> {
            // Kick over: take the motor away, the bearing is now only a hinge.
            test.setBlock(MOTOR, Blocks.AIR);
            if (powered) for (int k = 0; k < 4; k++) coil(test, k).energy().receiveEnergy(2000000, false);
        });
        int[] pulses = new int[1];
        for (int t = 41; t < 640; t++) {
            int tick = t;
            test.runAfterDelay(t, () -> {
                if (test.getBlockState(at(0, 2, -2, 1)).getValue(BlockStateProperties.POWERED)) pulses[0]++;
                if (tick % 60 == 0) {
                    DynamoBearingBlockEntity bearing = (DynamoBearingBlockEntity) test.getBlockEntity(BEARING);
                    LOGGER.info("[induction] {} t={} rotor {} rpm, shaft {}, contact ticks {}, coil {} A {} W, left {} FE", name, tick,
                        bearing.rotorSpeed(), bearing.getSpeed(), pulses[0], coil(test, 0).current(), coil(test, 0).power(), coil(test, 0).energy().getEnergyStored());
                }
            });
        }
        test.runAfterDelay(640, () -> {
            Config.ENERGY_BUFFER.set(4000);
            test.succeed();
        });
    }

    private static double rpm(GameTestHelper test) {
        return ((DynamoBearingBlockEntity) test.getBlockEntity(BEARING)).rotorSpeed();
    }

    /**
     * After a nudge, with nothing but energy in the coils, the rotor speeds up and keeps turning. It needs the
     * nudge because, as built, it rests with every contact open, like a single-phase motor on a dead spot.
     */
    @GameTest(template = "empty", batch = "motora", timeoutTicks = 700)
    public static void contactsCommutateCoilsIntoAMotor(GameTestHelper test) {
        run(test, "motor", -4, true);
        test.runAfterDelay(630, () -> test.assertTrue(Math.abs(rpm(test)) > 15, "The motor should be running, was " + rpm(test) + " rpm"));
    }

    /** Pushed the wrong way round, it stops, turns back and runs the way its contacts are set. */
    @GameTest(template = "empty", batch = "motorb", timeoutTicks = 700)
    public static void motorHasADirection(GameTestHelper test) {
        run(test, "motor reversed", 16, true);
        test.runAfterDelay(630, () -> test.assertTrue(rpm(test) < -15, "The motor should have turned itself round, was " + rpm(test) + " rpm"));
    }

    /**
     * With nothing else turning its shaft, the bearing hands the rotor's turning on to the kinetic network, and
     * what the network takes holds the rotor back.
     */
    @GameTest(template = "empty", batch = "motord", timeoutTicks = 700)
    public static void motorTurnsMachinesOnItsShaft(GameTestHelper test) {
        run(test, "motor loaded", -4, true);
        // A drill hanging off the shaft, in place of the kick motor.
        test.runAfterDelay(300, () -> test.setBlock(MOTOR, create("mechanical_drill").defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.DOWN)));
        double[] free = new double[1];
        test.runAfterDelay(295, () -> free[0] = Math.abs(rpm(test)));
        test.runAfterDelay(630, () -> {
            DynamoBearingBlockEntity bearing = (DynamoBearingBlockEntity) test.getBlockEntity(BEARING);
            var drill = (com.simibubi.create.content.kinetics.base.KineticBlockEntity) test.getBlockEntity(MOTOR);
            LOGGER.info("[induction] motor loaded: free {} rpm, loaded {} rpm, shaft {} rpm, drill {} rpm, output {} W",
                free[0], rpm(test), bearing.getSpeed(), drill.getSpeed(), bearing.outputWatts());
            test.assertTrue(Math.abs(drill.getSpeed()) >= 8, "The drill should be turning, was " + drill.getSpeed() + " rpm");
            test.assertTrue(bearing.outputWatts() > 5, "The rotor should be giving power to the shaft, was " + bearing.outputWatts() + " W");
            test.assertTrue(Math.abs(rpm(test)) < free[0] - 1, "The load should slow the rotor: " + free[0] + " rpm free, " + rpm(test) + " loaded");
        });
    }

    /** The same machine with empty coils is only a flywheel. */
    @GameTest(template = "empty", batch = "motorc", timeoutTicks = 700)
    public static void withoutEnergyTheRotorCoastsToAStop(GameTestHelper test) {
        run(test, "coast", 16, false);
        test.runAfterDelay(630, () -> test.assertTrue(Math.abs(rpm(test)) < 1, "An unpowered rotor should have stopped, was " + rpm(test) + " rpm"));
    }

    // --- brushed motor: armature on the rotor, commutator on the axis ---------------------------------------------

    private static final BlockPos COMMUTATOR = HUB.above();
    private static final BlockPos BRUSHES = COMMUTATOR.above();

    private static dev.jensh.createinduction.content.commutator.CommutatorBlockEntity commutator(GameTestHelper test) {
        BlockPos plate = ((DynamoBearingBlockEntity) test.getBlockEntity(BEARING)).platePos();
        return (dev.jensh.createinduction.content.commutator.CommutatorBlockEntity) test.getLevel().getBlockEntity(plate.offset(COMMUTATOR.subtract(BEARING)));
    }

    /** Four coils round a commutator on the rotor, between a north and a south pole, fed through a slip ring. */
    private static void brushed(GameTestHelper test, String name, int pairs, boolean load) {
        brushed(test, name, pairs, load, true);
    }

    private static void brushed(GameTestHelper test, String name, int pairs, boolean load, boolean feed) {
        test.setBlock(BEARING, ModContent.DYNAMO_BEARING.get().defaultBlockState().setValue(DynamoBearingBlock.FACING, Direction.UP));
        test.setBlock(HUB, Blocks.IRON_BLOCK);
        test.setBlock(COMMUTATOR, ModContent.COMMUTATOR.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            test.setBlock(COMMUTATOR.relative(side), ModContent.INDUCTION_COIL.get().defaultBlockState().setValue(InductionCoilBlock.FACING, side));
        }
        glue(test, HUB.offset(-1, 0, -1), COMMUTATOR.offset(1, 0, 1));
        // North pole towards the axis on the east side, south pole towards it on the west.
        test.setBlock(COMMUTATOR.east(3), ModContent.PERMANENT_MAGNET.get().defaultBlockState().setValue(PermanentMagnetBlock.FACING, Direction.WEST));
        test.setBlock(COMMUTATOR.west(3), ModContent.PERMANENT_MAGNET.get().defaultBlockState().setValue(PermanentMagnetBlock.FACING, Direction.WEST));
        if (load) test.setBlock(MOTOR, create("mechanical_drill").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        test.runAfterDelay(5, () -> ((DynamoBearingBlockEntity) test.getBlockEntity(BEARING)).assembleNextTick = true);
        test.runAfterDelay(25, () -> {
            commutator(test).setPairs(pairs);
            test.setBlock(BRUSHES, ModContent.SLIP_RING.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        });
        long[] fed = new long[1];
        for (int t = 40; t < 400; t++) {
            int tick = t;
            test.runAfterDelay(t, () -> {
                var ring = (dev.jensh.createinduction.content.slipring.SlipRingBlockEntity) test.getBlockEntity(BRUSHES);
                if (feed) fed[0] += ring.energy().receiveEnergy(1000, false);
                if (tick % 60 == 0) {
                    DynamoBearingBlockEntity bearing = (DynamoBearingBlockEntity) test.getBlockEntity(BEARING);
                    LOGGER.info("[induction] {} t={} rotor {} rpm, shaft {} rpm, output {} W, armature {}, linked {}, fed {} FE", name, tick,
                        bearing.rotorSpeed(), bearing.getSpeed(), bearing.outputWatts(), commutator(test).armatureSize(), ring.isLinked(), fed[0]);
                }
            });
        }
    }

    /** No redstone and no nudge: energy into the brushes and the armature starts turning. */
    @GameTest(template = "empty", batch = "brusheda", timeoutTicks = 500)
    public static void commutatorMakesAMotorThatStartsItself(GameTestHelper test) {
        brushed(test, "brushed", 1, false);
        test.runAfterDelay(400, () -> {
            test.assertTrue(commutator(test).armatureSize() == 4, "The commutator should have found its four coils");
            test.assertTrue(rpm(test) > 15, "The motor should be running forwards, was " + rpm(test) + " rpm");
            test.succeed();
        });
    }

    @GameTest(template = "empty", batch = "brushedb", timeoutTicks = 500)
    public static void negativePoleCountReversesTheMotor(GameTestHelper test) {
        brushed(test, "brushed reversed", -1, false);
        test.runAfterDelay(400, () -> {
            test.assertTrue(rpm(test) < -15, "The motor should be running backwards, was " + rpm(test) + " rpm");
            test.succeed();
        });
    }

    @GameTest(template = "empty", batch = "brushedc", timeoutTicks = 500)
    public static void brushedMotorTurnsMachines(GameTestHelper test) {
        brushed(test, "brushed loaded", 1, true);
        test.runAfterDelay(400, () -> {
            var drill = (com.simibubi.create.content.kinetics.base.KineticBlockEntity) test.getBlockEntity(MOTOR);
            DynamoBearingBlockEntity bearing = (DynamoBearingBlockEntity) test.getBlockEntity(BEARING);
            test.assertTrue(Math.abs(drill.getSpeed()) >= 8, "The drill should be turning, was " + drill.getSpeed() + " rpm");
            test.assertTrue(bearing.outputWatts() > 5, "The rotor should be giving power to the shaft, was " + bearing.outputWatts() + " W");
            test.succeed();
        });
    }

    /** With Power Grid the brushes are wired up instead: a battery on the slip ring's terminals runs the motor. */
    @GameTest(template = "empty", batch = "brushedd", timeoutTicks = 500)
    public static void brushedMotorRunsOffAPowerGridBattery(GameTestHelper test) {
        if (!net.neoforged.fml.ModList.get().isLoaded("powergrid")) {
            test.succeed();
            return;
        }
        brushed(test, "brushed grid", 1, false, false);
        BlockPos battery = BRUSHES.offset(3, 0, 2);
        double[] before = new double[1];
        test.runAfterDelay(30, () -> {
            test.setBlock(battery, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("powergrid", "battery")));
            for (int terminal = 0; terminal < 2; terminal++) {
                PowerGridRig.wire(test.getLevel(), test.absolutePos(BRUSHES), terminal, test.absolutePos(battery), terminal);
            }
            before[0] = PowerGridRig.batteryEnergy(test.getLevel(), test.absolutePos(battery));
        });
        test.runAfterDelay(400, () -> {
            double used = before[0] - PowerGridRig.batteryEnergy(test.getLevel(), test.absolutePos(battery));
            LOGGER.info("[induction] brushed grid: rotor {} rpm, battery gave {} J of {}", rpm(test), used, before[0]);
            test.assertTrue(Math.abs(rpm(test)) > 15, "The motor should run off the battery, was " + rpm(test) + " rpm");
            test.assertTrue(used > 100, "The battery should have been drawn on, gave " + used);
            test.succeed();
        });
    }
}
