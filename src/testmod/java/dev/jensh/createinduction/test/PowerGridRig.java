package dev.jensh.createinduction.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;

/** Everything that touches Power Grid classes, kept apart so the tests still load without it. */
final class PowerGridRig {
    private PowerGridRig() {}

    static void wire(GameTestHelper test, BlockPos from, BlockPos to, int terminal) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("powergrid", "wire")), 4);
        var entity = HangingWireEntity.create(test.getLevel(), new BlockWireEndpoint(test.absolutePos(from), terminal),
            new BlockWireEndpoint(test.absolutePos(to), terminal), stack, null);
        test.assertTrue(test.getLevel().addFreshEntity(entity), "Wire should spawn");
    }

    /** Wires two blocks given by their real positions, which may be inside a sub-level. */
    static void wireAbsolute(GameTestHelper test, BlockPos from, BlockPos to, int terminal) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("powergrid", "wire")), 4);
        var entity = HangingWireEntity.create(test.getLevel(), new BlockWireEndpoint(from, terminal), new BlockWireEndpoint(to, terminal), stack, null);
        test.assertTrue(test.getLevel().addFreshEntity(entity), "Wire should spawn");
    }

    /** Hangs a wire between two terminals anywhere in the level. */
    static void wire(net.minecraft.server.level.ServerLevel level, BlockPos from, int fromTerminal, BlockPos to, int toTerminal) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("powergrid", "wire")), 8);
        var entity = HangingWireEntity.create(level, new BlockWireEndpoint(from, fromTerminal), new BlockWireEndpoint(to, toTerminal), stack, null);
        if (!level.addFreshEntity(entity)) throw new IllegalStateException("Wire did not spawn");
    }

    static String battery(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity battery) {
            return battery.getEnergy() + " of " + battery.getCapacity();
        }
        return "missing";
    }

    static void setResistance(GameTestHelper test, BlockPos pos, double ohms) {
        ((ResistorBlockEntity) test.getBlockEntity(pos)).setValue(ohms);
    }

    static double resistance(GameTestHelper test, BlockPos pos) {
        return ((ResistorBlockEntity) test.getBlockEntity(pos)).getValue();
    }
}
