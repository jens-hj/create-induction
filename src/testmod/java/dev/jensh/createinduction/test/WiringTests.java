package dev.jensh.createinduction.test;

import dev.jensh.createinduction.ModContent;
import dev.jensh.createinduction.content.coil.InductionCoilBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.IElectric;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Wiring coils to each other the way a player does it: clicking terminals with a wire in hand. */
@GameTestHolder("create_induction")
@PrefixGameTestTemplate(false)
public class WiringTests {
    private static final Logger LOGGER = LoggerFactory.getLogger("create_induction/tests");
    private static final BlockPos A = new BlockPos(2, 2, 2), B = new BlockPos(5, 2, 2);

    /** Clicks the given terminal pad on the back (south side) of a north-facing coil. */
    private static Object click(GameTestHelper test, Player player, BlockPos coil, boolean positive) {
        BlockPos at = test.absolutePos(coil);
        Vec3 point = new Vec3(at.getX() + (positive ? 11 : 5) / 16.0, at.getY() + 0.5, at.getZ() + 1.0);
        var context = new UseOnContext(test.getLevel(), player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
            new BlockHitResult(point, Direction.SOUTH, at, false));
        IElectric electric = IElectric.getAt(test.getLevel(), at);
        return electric.onWire(test.getLevel().getBlockState(at), context);
    }

    private static int wires(GameTestHelper test) {
        return test.getLevel().getEntitiesOfClass(HangingWireEntity.class, new AABB(test.absolutePos(A)).inflate(8)).size();
    }

    @GameTest(template = "empty", batch = "wiring", timeoutTicks = 100)
    public static void coilsWireToEachOtherByHand(GameTestHelper test) {
        if (!net.neoforged.fml.ModList.get().isLoaded("powergrid")) {
            test.succeed();
            return;
        }
        for (BlockPos pos : new BlockPos[] {A, B}) {
            test.setBlock(pos, ModContent.INDUCTION_COIL.get().defaultBlockState().setValue(InductionCoilBlock.FACING, Direction.NORTH));
        }
        test.runAfterDelay(10, () -> {
            Player player = test.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("powergrid", "wire")), 64));
            boolean[][] pairs = {{false, false}, {true, true}, {false, true}};
            for (boolean[] pair : pairs) {
                int before = wires(test);
                Object first = click(test, player, A, pair[0]);
                Object second = click(test, player, B, pair[1]);
                LOGGER.info("[induction] wiring {} -> {}: first {}, second {}, wires {} -> {}", pair[0] ? "+" : "-", pair[1] ? "+" : "-",
                    first, second, before, wires(test));
            }
            test.assertTrue(wires(test) == 3, "All three wires should hang, found " + wires(test));
            test.succeed();
        });
    }
}
