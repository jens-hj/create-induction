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

    static void setResistance(GameTestHelper test, BlockPos pos, double ohms) {
        ((ResistorBlockEntity) test.getBlockEntity(pos)).setValue(ohms);
    }

    static double resistance(GameTestHelper test, BlockPos pos) {
        return ((ResistorBlockEntity) test.getBlockEntity(pos)).getValue();
    }
}
