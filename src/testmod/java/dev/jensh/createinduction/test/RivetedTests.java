package dev.jensh.createinduction.test;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.jensh.createinduction.Config;
import dev.jensh.createinduction.ModContent;
import dev.jensh.createinduction.content.bearing.DynamoBearingBlockEntity;
import dev.jensh.createinduction.content.coil.InductionCoilBlock;
import dev.jensh.createinduction.content.coil.InductionCoilBlockEntity;
import dev.jensh.createinduction.content.magnet.PermanentMagnetBlock;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Coils and magnets that sit on sub-levels of their own, held by fixed joints the way a rivet holds them:
 * to the ground at an angle, or to the rotor.
 */
@GameTestHolder("create_induction")
@PrefixGameTestTemplate(false)
public class RivetedTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Object> JOINTS = new ArrayList<>();

    /** Lifts one block into a sub-level of its own and locks it to {@code host} (or the ground), turned about Y. */
    private static BlockPos rivet(GameTestHelper test, BlockPos relative, @Nullable ServerSubLevel host, double degrees) {
        BlockPos absolute = test.absolutePos(relative);
        ServerSubLevel own = (ServerSubLevel) SubLevelAssemblyHelper.assembleBlocks(test.getLevel(), absolute, List.of(absolute),
            BoundingBox3i.from(List.of(absolute)));
        Vector3d world = new Vector3d(absolute.getX() + 0.5, absolute.getY() + 0.5, absolute.getZ() + 0.5);
        Vector3d inOwn = own.logicalPose().transformPositionInverse(world, new Vector3d());
        Vector3d inHost = host == null ? world : host.logicalPose().transformPositionInverse(world, new Vector3d());
        ServerSubLevelContainer container = SubLevelContainer.getContainer(test.getLevel());
        JOINTS.add(container.physicsSystem().getPipeline().addConstraint(host, own,
            new FixedConstraintConfiguration(inHost, inOwn, new Quaterniond().rotateY(Math.toRadians(degrees)))));
        return BlockPos.containing(inOwn.x, inOwn.y, inOwn.z);
    }

    /** A coil riveted to the ground at an angle still generates from the rotor's magnets. */
    @GameTest(template = "empty", batch = "rivetedcoil", timeoutTicks = 400)
    public static void coilOnAnAngledSubLevelGenerates(GameTestHelper test) {
        Config.ENERGY_BUFFER.set(40000000);
        GeneratorTests.buildRotor(test, 96);
        BlockPos coilPos = GeneratorTests.HUB.east(4);
        test.setBlock(coilPos, ModContent.INDUCTION_COIL.get().defaultBlockState().setValue(InductionCoilBlock.FACING, Direction.WEST));
        BlockPos[] plot = new BlockPos[1];
        test.runAfterDelay(3, () -> plot[0] = rivet(test, coilPos, null, 25));
        test.runAfterDelay(5, () -> GeneratorTests.bearing(test).assembleNextTick = true);
        int[] stored = new int[1];
        test.runAfterDelay(200, () -> stored[0] = ((InductionCoilBlockEntity) test.getLevel().getBlockEntity(plot[0])).energy().getEnergyStored());
        test.runAfterDelay(300, () -> {
            InductionCoilBlockEntity coil = (InductionCoilBlockEntity) test.getLevel().getBlockEntity(plot[0]);
            double watts = (coil.energy().getEnergyStored() - stored[0]) / Config.FE_PER_WATT_TICK.get() / 100.0;
            LOGGER.info("[induction] riveted coil: {} W into load, {} V, rotor {} rpm", watts, coil.voltage(), GeneratorTests.bearing(test).rotorSpeed());
            Config.ENERGY_BUFFER.set(4000);
            test.assertTrue(watts > 60, "A coil on its own sub-level should generate, made " + watts + " W");
            test.succeed();
        });
    }

    /** Magnets riveted to the rotor instead of built into it: same electricity, and the shaft still pays for it. */
    @GameTest(template = "empty", batch = "rivetedmagnets", timeoutTicks = 600)
    public static void magnetsRivetedToTheRotorGenerateAndBrakeIt(GameTestHelper test) {
        Config.ENERGY_BUFFER.set(40000000);
        GeneratorTests.buildRotor(test, 0);
        test.setBlock(GeneratorTests.HUB.east(2), Blocks.AIR);
        test.setBlock(GeneratorTests.HUB.west(2), Blocks.AIR);
        double[] mark = new double[3];
        test.runAfterDelay(5, () -> GeneratorTests.bearing(test).assembleNextTick = true);
        test.runAfterDelay(25, () -> {
            DynamoBearingBlockEntity bearing = GeneratorTests.bearing(test);
            test.assertTrue(bearing.isAssembled(), "Bearing should have assembled");
            ServerSubLevel rotor = (ServerSubLevel) SubLevelContainer.getContainer(test.getLevel()).getSubLevel(bearing.subLevelId());
            for (Direction side : new Direction[] {Direction.EAST, Direction.WEST}) {
                BlockPos pos = GeneratorTests.HUB.relative(side, 2);
                test.setBlock(pos, ModContent.PERMANENT_MAGNET.get().defaultBlockState().setValue(PermanentMagnetBlock.FACING, side));
                rivet(test, pos, rotor, 0);
            }
            ((CreativeMotorBlockEntity) test.getBlockEntity(new BlockPos(7, 1, 7))).generatedSpeed.setValue(96);
        });
        // Spin up without load first, then let the coils in.
        test.runAfterDelay(160, () -> mark[0] = GeneratorTests.bearing(test).totalWork());
        test.runAfterDelay(220, () -> {
            mark[1] = (GeneratorTests.bearing(test).totalWork() - mark[0]) / 3.0;
            GeneratorTests.placeCoils(test);
        });
        test.runAfterDelay(300, () -> {
            mark[0] = GeneratorTests.bearing(test).totalWork();
            mark[2] = GeneratorTests.storedInCoils(test);
        });
        test.runAfterDelay(500, () -> {
            DynamoBearingBlockEntity bearing = GeneratorTests.bearing(test);
            double mechanical = (bearing.totalWork() - mark[0]) / 10.0;
            double load = (GeneratorTests.storedInCoils(test) - mark[2]) / Config.FE_PER_WATT_TICK.get() / 200.0;
            double electrical = load * (Config.COIL_RESISTANCE.get() + Config.LOAD_RESISTANCE.get()) / Config.LOAD_RESISTANCE.get();
            LOGGER.info("[induction] riveted magnets: rotor {} rpm, shaft {} W (idle {} W), {} W electrical", bearing.rotorSpeed(), mechanical, mark[1], electrical);
            Config.ENERGY_BUFFER.set(4000);
            test.assertTrue(Math.abs(bearing.rotorSpeed()) > 40, "The rotor should still be turning, was " + bearing.rotorSpeed());
            test.assertTrue(load > 100, "Riveted magnets should generate like built-in ones, made " + load + " W");
            double extra = mechanical - mark[1];
            test.assertTrue(extra > electrical * 0.7 && extra < electrical * 1.3,
                "The shaft should pay for the electricity: extra " + extra + " W for " + electrical + " W");
            test.succeed();
        });
    }
}
