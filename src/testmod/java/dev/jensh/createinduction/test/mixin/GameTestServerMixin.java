package dev.jensh.createinduction.test.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The test server normally runs its tests millions of blocks out, where the physics engine can no longer tell
 * half a block from a whole one. Keep them near the origin so distances between magnets and coils are real.
 */
@Mixin(GameTestServer.class)
abstract class GameTestServerMixin {
    @Redirect(method = "startTests", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/util/RandomSource;nextIntBetweenInclusive(II)I"))
    private int createInduction$nearOrigin(RandomSource random, int min, int max) {
        return 64;
    }
}
