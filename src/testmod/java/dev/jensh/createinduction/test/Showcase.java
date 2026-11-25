package dev.jensh.createinduction.test;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.jensh.createinduction.ModContent;
import dev.jensh.createinduction.content.bearing.DynamoBearingBlock;
import dev.jensh.createinduction.content.bearing.DynamoBearingBlockEntity;
import dev.jensh.createinduction.content.coil.InductionCoilBlock;
import dev.jensh.createinduction.content.coil.InductionCoilBlockEntity;
import dev.jensh.createinduction.content.magnet.PermanentMagnetBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Builds a four-magnet generator in a fresh flat world, spins it up and photographs it.
 * Enabled with -Dcreateinduction.showcase=<output dir>; run through scripts/headless.py.
 */
public final class Showcase {
    private static final int SITE_X = 8, SITE_Z = 8;
    private final Path out = Path.of(System.getProperty("createinduction.showcase"));
    private final Deque<Step> steps = new ArrayDeque<>();
    private final List<String> report = new ArrayList<>();
    private boolean started, planned, done;
    private int ticks, wait, ground;

    private interface Step {
        boolean run(Minecraft mc, MinecraftServer server) throws Exception;
    }

    public static void register() {
        Showcase showcase = new Showcase();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> showcase.tick());
    }

    private void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++ticks > 20 * 60 * 10) throw new IllegalStateException("Showcase timed out");
            if (!started) {
                if (!(mc.screen instanceof TitleScreen)) return;
                started = true;
                mc.createWorldOpenFlows().createFreshLevel("showcase",
                    new LevelSettings("Showcase", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(20261125L, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    new TitleScreen());
                return;
            }
            MinecraftServer server = mc.getSingleplayerServer();
            if (mc.level == null || mc.player == null || server == null) return;
            if (!planned) {
                planned = true;
                plan();
            }
            if (wait > 0) {
                wait--;
                return;
            }
            while (!steps.isEmpty() && steps.peek().run(mc, server)) {
                steps.poll();
                if (wait > 0) return;
            }
            if (steps.isEmpty()) finish(mc, "ok\n" + String.join("\n", report));
        } catch (Throwable error) {
            error.printStackTrace();
            finish(mc, error + "\n" + String.join("\n", report));
        }
    }

    private void finish(Minecraft mc, String message) {
        done = true;
        try {
            Files.createDirectories(out);
            Files.writeString(out.resolve("result.txt"), message);
        } catch (Exception ignored) {
        }
        mc.stop();
    }

    private void plan() {
        steps.add((mc, server) -> {
            mc.options.hideGui = true;
            return true;
        });
        steps.add(onServer(this::build));
        pause(20);
        steps.add(onServer(server -> view(server, 5.5, 3.2, 6.5)));
        pause(40);
        shot("rotor-built");
        steps.add(onServer(server -> bearing(server).assembleNextTick = true));
        pause(160);
        steps.add(onServer(server -> {
            DynamoBearingBlockEntity bearing = bearing(server);
            report.add("assembled=" + bearing.isAssembled() + " rpm=" + bearing.rotorSpeed() + " impact=" + bearing.calculateStressApplied());
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (server.overworld().getBlockEntity(hub().relative(side, 4)) instanceof InductionCoilBlockEntity coil) {
                    report.add(side + "=" + coil.power() + " W " + coil.voltage() + " V");
                }
            }
            if (!bearing.isAssembled()) throw new IllegalStateException("Rotor did not assemble");
        }));
        shot("generator");
        steps.add(onServer(server -> view(server, 0.5, 7.5, 3.0)));
        pause(30);
        shot("generator-above");
        steps.add(onServer(server -> view(server, -4.5, 1.6, 4.0)));
        pause(30);
        shot("generator-low");
    }

    private void pause(int ticks) {
        steps.add((mc, server) -> {
            wait = ticks;
            return true;
        });
    }

    private void shot(String name) {
        pause(10);
        steps.add((mc, server) -> {
            Files.createDirectories(out);
            Screenshot.grab(out.toFile(), name + ".png", mc.getMainRenderTarget(), message -> {});
            System.out.println("[showcase] shot " + name);
            wait = 5;
            return true;
        });
    }

    private Step onServer(java.util.function.Consumer<MinecraftServer> action) {
        return (mc, server) -> {
            CompletableFuture.runAsync(() -> action.accept(server), server).join();
            return true;
        };
    }

    // ---- scene --------------------------------------------------------------------------------------------

    private BlockPos hub() {
        return new BlockPos(SITE_X, ground + 2, SITE_Z);
    }

    private DynamoBearingBlockEntity bearing(MinecraftServer server) {
        return (DynamoBearingBlockEntity) server.overworld().getBlockEntity(hub().below());
    }

    private static Block create(String name) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", name));
    }

    private void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.setDayTime(4000);
        level.setWeatherParameters(100000, 0, false, false);
        ground = level.getChunk(SITE_X >> 4, SITE_Z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING, SITE_X & 15, SITE_Z & 15) + 1;
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                level.setBlock(new BlockPos(SITE_X + x, ground - 1, SITE_Z + z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            }
        }
        BlockPos hub = hub();
        level.setBlock(hub.below(2), create("creative_motor").defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.UP), 3);
        level.setBlock(hub.below(), ModContent.DYNAMO_BEARING.get().defaultBlockState().setValue(DynamoBearingBlock.FACING, Direction.UP), 3);
        level.setBlock(hub, create("andesite_casing").defaultBlockState(), 3);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            level.setBlock(hub.relative(side), create("andesite_casing").defaultBlockState(), 3);
            level.setBlock(hub.relative(side, 2), ModContent.PERMANENT_MAGNET.get().defaultBlockState().setValue(PermanentMagnetBlock.FACING, side), 3);
            BlockPos post = hub.relative(side, 4);
            level.setBlock(post.below(2), create("andesite_casing").defaultBlockState(), 3);
            level.setBlock(post.below(), create("andesite_casing").defaultBlockState(), 3);
            level.setBlock(post, ModContent.INDUCTION_COIL.get().defaultBlockState().setValue(InductionCoilBlock.FACING, side.getOpposite()), 3);
        }
        level.addFreshEntity(new SuperGlueEntity(level, new AABB(hub.getX() - 2, hub.getY(), hub.getZ() - 2, hub.getX() + 3, hub.getY() + 1, hub.getZ() + 3)));
        if (level.getBlockEntity(hub.below(2)) instanceof CreativeMotorBlockEntity motor) motor.generatedSpeed.setValue(64);
    }

    /** Puts the camera at an offset from the hub, looking at it. */
    private void view(MinecraftServer server, double dx, double dy, double dz) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.SPECTATOR);
        BlockPos hub = hub();
        double x = hub.getX() + 0.5 + dx, y = hub.getY() + 0.5 + dy, z = hub.getZ() + 0.5 + dz;
        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        float pitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.teleportTo(server.overworld(), x, y - player.getEyeHeight(), z, yaw, pitch);
    }
}
