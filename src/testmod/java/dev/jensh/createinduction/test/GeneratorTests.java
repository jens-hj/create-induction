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
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.List;

@GameTestHolder("create_induction")
@PrefixGameTestTemplate(false)
public final class GeneratorTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos MOTOR = new BlockPos(7, 1, 7);
    private static final BlockPos BEARING = new BlockPos(7, 2, 7);
    static final BlockPos HUB = new BlockPos(7, 3, 7);

    /** Motor, bearing and a two-magnet rotor; returns nothing, look blocks up by position. */
    static void buildRotor(GameTestHelper test, int rpm) {
        test.setBlock(MOTOR, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "creative_motor")).defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.UP));
        test.setBlock(BEARING, ModContent.DYNAMO_BEARING.get().defaultBlockState().setValue(DynamoBearingBlock.FACING, Direction.UP));
        test.setBlock(HUB, Blocks.IRON_BLOCK);
        test.setBlock(HUB.west(), Blocks.IRON_BLOCK);
        test.setBlock(HUB.east(), Blocks.IRON_BLOCK);
        test.setBlock(HUB.west(2), ModContent.PERMANENT_MAGNET.get().defaultBlockState().setValue(PermanentMagnetBlock.FACING, Direction.WEST));
        test.setBlock(HUB.east(2), ModContent.PERMANENT_MAGNET.get().defaultBlockState().setValue(PermanentMagnetBlock.FACING, Direction.EAST));
        BlockPos from = test.absolutePos(HUB.west(2));
        BlockPos to = test.absolutePos(HUB.east(2));
        test.getLevel().addFreshEntity(new SuperGlueEntity(test.getLevel(),
            new AABB(from.getX(), from.getY(), from.getZ(), to.getX() + 1, to.getY() + 1, to.getZ() + 1)));
        if (test.getBlockEntity(MOTOR) instanceof CreativeMotorBlockEntity motor) motor.generatedSpeed.setValue(rpm);
    }

    static void placeCoils(GameTestHelper test) {
        test.setBlock(HUB.west(4), ModContent.INDUCTION_COIL.get().defaultBlockState().setValue(InductionCoilBlock.FACING, Direction.EAST));
        test.setBlock(HUB.east(4), ModContent.INDUCTION_COIL.get().defaultBlockState().setValue(InductionCoilBlock.FACING, Direction.WEST));
        test.setBlock(HUB.north(4), ModContent.INDUCTION_COIL.get().defaultBlockState().setValue(InductionCoilBlock.FACING, Direction.SOUTH));
        test.setBlock(HUB.south(4), ModContent.INDUCTION_COIL.get().defaultBlockState().setValue(InductionCoilBlock.FACING, Direction.NORTH));
    }

    static DynamoBearingBlockEntity bearing(GameTestHelper test) {
        return (DynamoBearingBlockEntity) test.getBlockEntity(BEARING);
    }

    static InductionCoilBlockEntity coil(GameTestHelper test, BlockPos pos) {
        return (InductionCoilBlockEntity) test.getBlockEntity(pos);
    }

    @GameTest(template = "empty", batch = "spin", timeoutTicks = 200)
    public static void bearingSpinsRotorToShaftSpeed(GameTestHelper test) {
        buildRotor(test, 64);
        test.runAfterDelay(5, () -> bearing(test).assembleNextTick = true);
        test.runAfterDelay(15, () -> test.assertTrue(bearing(test).isAssembled(), "Bearing should have assembled its rotor"));
        test.runAfterDelay(120, () -> {
            DynamoBearingBlockEntity bearing = bearing(test);
            LOGGER.info("[induction] spin: rotor {} rpm, delivered {} W, impact {}", bearing.rotorSpeed(),
                bearing.deliveredWatts(), bearing.calculateStressApplied());
            test.assertTrue(Math.abs(Math.abs(bearing.rotorSpeed()) - 64) < 6, "Rotor should reach 64 RPM, was " + bearing.rotorSpeed());
            test.succeed();
        });
    }

    private static int storedInCoils(GameTestHelper test) {
        int stored = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) stored += coil(test, HUB.relative(side, 4)).energy().getEnergyStored();
        return stored;
    }

    /** Full coils carry no current, so they neither generate nor brake; with room to store they do both. */
    @GameTest(template = "empty", batch = "generate", timeoutTicks = 500)
    public static void generatingChargesCoilsAndLoadsTheShaft(GameTestHelper test) {
        Config.ENERGY_BUFFER.set(100);
        buildRotor(test, 96);
        placeCoils(test);
        double[] mark = new double[4];
        test.runAfterDelay(5, () -> bearing(test).assembleNextTick = true);
        test.runAfterDelay(100, () -> mark[0] = bearing(test).totalWork());
        test.runAfterDelay(200, () -> {
            DynamoBearingBlockEntity bearing = bearing(test);
            mark[1] = (bearing.totalWork() - mark[0]) / 5.0;
            mark[2] = bearing.calculateStressApplied();
            LOGGER.info("[induction] full: rotor {} rpm, idle {} W, impact {}, coil {} W", bearing.rotorSpeed(),
                mark[1], mark[2], coil(test, HUB.east(4)).power());
            test.assertTrue(storedInCoils(test) == 400, "Coils should be full");
            test.assertTrue(coil(test, HUB.east(4)).power() < 1, "A full coil has no load and should not generate");
            Config.ENERGY_BUFFER.set(40000000);
        });
        test.runAfterDelay(260, () -> {
            mark[0] = bearing(test).totalWork();
            mark[3] = storedInCoils(test);
        });
        test.runAfterDelay(460, () -> {
            DynamoBearingBlockEntity bearing = bearing(test);
            InductionCoilBlockEntity coil = coil(test, HUB.east(4));
            double mechanical = (bearing.totalWork() - mark[0]) / 10.0;
            double load = (storedInCoils(test) - mark[3]) / Config.FE_PER_WATT_TICK.get() / 200.0;
            double electrical = load * (Config.COIL_RESISTANCE.get() + Config.LOAD_RESISTANCE.get()) / Config.LOAD_RESISTANCE.get();
            LOGGER.info("[induction] generate: rotor {} rpm, shaft {} W (idle {} W), coils {} W into load, {} W electrical, impact {}, coil {} V {} A {} W",
                bearing.rotorSpeed(), mechanical, mark[1], load, electrical, bearing.calculateStressApplied(), coil.voltage(), coil.current(), coil.power());
            for (Direction side : Direction.Plane.HORIZONTAL) {
                InductionCoilBlockEntity each = coil(test, HUB.relative(side, 4));
                LOGGER.info("[induction] coil {}: {} W, {} V, {} A, {} FE", side, each.power(), each.voltage(), each.current(), each.energy().getEnergyStored());
            }
            Config.ENERGY_BUFFER.set(4000);
            test.assertTrue(load > 100, "Coils should be generating, were " + load + " W");
            double extra = mechanical - mark[1];
            test.assertTrue(extra > electrical * 0.7, "The shaft should pay for the electricity: extra " + extra + " W for " + electrical + " W");
            test.assertTrue(extra < electrical * 1.3, "The shaft should not pay much more than the electricity: extra " + extra + " W for " + electrical + " W");
            test.assertTrue(bearing.calculateStressApplied() > mark[2] * 1.5, "Generating should raise the stress impact");
            test.succeed();
        });
    }

    @GameTest(template = "empty", batch = "disassemble", timeoutTicks = 200)
    public static void disassemblyPutsTheRotorBack(GameTestHelper test) {
        buildRotor(test, 32);
        test.runAfterDelay(5, () -> bearing(test).assembleNextTick = true);
        test.runAfterDelay(20, () -> {
            test.assertTrue(bearing(test).isAssembled(), "Bearing should have assembled its rotor");
            test.assertBlockPresent(Blocks.AIR, HUB);
            bearing(test).assembleNextTick = true;
        });
        test.runAfterDelay(30, () -> {
            test.assertFalse(bearing(test).isAssembled(), "Bearing should have let go");
            test.assertBlockPresent(Blocks.IRON_BLOCK, HUB);
            test.assertBlockPresent(ModContent.PERMANENT_MAGNET.get(), HUB.east(2));
            test.succeed();
        });
    }

    @GameTest(template = "empty", batch = "electromagnet", timeoutTicks = 200)
    public static void poweredCoilRepelsAMagnetFacingIt(GameTestHelper test) {
        for (int x = 2; x <= 12; x++) for (int z = 5; z <= 9; z++) test.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        BlockPos magnetPos = new BlockPos(7, 2, 7);
        test.setBlock(magnetPos, ModContent.PERMANENT_MAGNET.get().defaultBlockState().setValue(PermanentMagnetBlock.FACING, Direction.EAST));
        BlockPos coilPos = new BlockPos(9, 2, 7);
        test.setBlock(coilPos, ModContent.INDUCTION_COIL.get().defaultBlockState().setValue(InductionCoilBlock.FACING, Direction.WEST));
        coil(test, coilPos).energy().receiveEnergy(4000, false);

        BlockPos absolute = test.absolutePos(magnetPos);
        SubLevel magnet = SubLevelAssemblyHelper.assembleBlocks(test.getLevel(), absolute, List.of(absolute), BoundingBox3i.from(List.of(absolute)));
        java.util.UUID id = magnet.getUniqueId();
        java.util.function.DoubleSupplier x = () -> dev.ryanhcode.sable.api.sublevel.SubLevelContainer
            .getContainer(test.getLevel()).getSubLevel(id).logicalPose().position().x();
        double[] start = new double[1];
        test.runAfterDelay(10, () -> start[0] = x.getAsDouble());
        test.runAfterDelay(30, () -> {
            double rest = x.getAsDouble();
            test.assertTrue(Math.abs(rest - start[0]) < 0.05, "An unpowered coil should leave the magnet alone, moved " + (rest - start[0]));
            test.setBlock(coilPos.above(), Blocks.REDSTONE_BLOCK);
        });
        test.runAfterDelay(80, () -> {
            double moved = x.getAsDouble() - start[0];
            InductionCoilBlockEntity coil = coil(test, coilPos);
            LOGGER.info("[induction] electromagnet: magnet moved {} blocks, coil {} W, stored {} FE", moved, coil.power(), coil.energy().getEnergyStored());
            test.assertTrue(moved < -0.5, "Like poles should push the magnet away (west), moved " + moved);
            test.assertTrue(coil.energy().getEnergyStored() < 4000, "An electromagnet should use energy");
            test.succeed();
        });
    }

    private static double[] rotorState(GameTestHelper test) {
        DynamoBearingBlockEntity bearing = bearing(test);
        var rotor = (dev.ryanhcode.sable.sublevel.ServerSubLevel) dev.ryanhcode.sable.api.sublevel.SubLevelContainer
            .getContainer(test.getLevel()).getSubLevel(bearing.subLevelId());
        var up = new org.joml.Vector3d(0, 1, 0);
        double inertia = rotor.getMassTracker().getInertiaTensor().transform(up, new org.joml.Vector3d()).dot(up);
        double spin = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(rotor).getAngularVelocity(new org.joml.Vector3d()).y;
        return new double[] {inertia, spin, rotor.getMassTracker().getMass()};
    }

    /** With the motor stopped the rotor coasts; what it loses beyond air drag must show up in the coils. */
    @GameTest(template = "empty", batch = "audit", timeoutTicks = 600)
    public static void coastingRotorEnergyGoesToTheCoils(GameTestHelper test) {
        Config.ENERGY_BUFFER.set(100);
        buildRotor(test, 96);
        placeCoils(test);
        double[] before = new double[3];
        double[] dragLoss = new double[1];
        int[] storedBefore = new int[1];
        test.runAfterDelay(5, () -> bearing(test).assembleNextTick = true);
        test.runAfterDelay(120, () -> {
            System.arraycopy(rotorState(test), 0, before, 0, 3);
            ((CreativeMotorBlockEntity) test.getBlockEntity(MOTOR)).generatedSpeed.setValue(0);
        });
        test.runAfterDelay(140, () -> {
            double[] after = rotorState(test);
            dragLoss[0] = 0.5 * before[0] * (before[1] * before[1] - after[1] * after[1]);
            LOGGER.info("[induction] audit idle: inertia {} mass {} spin {} -> {} rad/s, lost {} J in 1 s", before[0], before[2], before[1], after[1], dragLoss[0]);
            ((CreativeMotorBlockEntity) test.getBlockEntity(MOTOR)).generatedSpeed.setValue(96);
        });
        test.runAfterDelay(260, () -> {
            Config.ENERGY_BUFFER.set(40000000);
            System.arraycopy(rotorState(test), 0, before, 0, 3);
            storedBefore[0] = 0;
            for (Direction side : Direction.Plane.HORIZONTAL) storedBefore[0] += coil(test, HUB.relative(side, 4)).energy().getEnergyStored();
            ((CreativeMotorBlockEntity) test.getBlockEntity(MOTOR)).generatedSpeed.setValue(0);
        });
        test.runAfterDelay(280, () -> {
            double[] after = rotorState(test);
            int stored = 0;
            for (Direction side : Direction.Plane.HORIZONTAL) stored += coil(test, HUB.relative(side, 4)).energy().getEnergyStored();
            double lost = 0.5 * before[0] * (before[1] * before[1] - after[1] * after[1]);
            double loadEnergy = (stored - storedBefore[0]) / Config.FE_PER_WATT_TICK.get() * 0.05;
            double electrical = loadEnergy * (Config.COIL_RESISTANCE.get() + Config.LOAD_RESISTANCE.get()) / Config.LOAD_RESISTANCE.get();
            LOGGER.info("[induction] audit loaded: spin {} -> {} rad/s, lost {} J, electrical {} J (load {} J)", before[1], after[1], lost, electrical, loadEnergy);
            Config.ENERGY_BUFFER.set(4000);
            test.assertTrue(electrical > 0.7 * lost, "Most of what the rotor lost should be electricity: " + electrical + " J of " + lost + " J");
            test.assertTrue(electrical < lost, "Coils cannot make more than the rotor lost: " + electrical + " J of " + lost + " J");
            test.succeed();
        });
    }
}
