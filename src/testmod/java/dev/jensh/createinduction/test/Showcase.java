package dev.jensh.createinduction.test;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.jensh.createinduction.ModContent;
import dev.jensh.createinduction.content.bearing.DynamoBearingBlock;
import dev.jensh.createinduction.content.bearing.DynamoBearingBlockEntity;
import dev.jensh.createinduction.content.coil.InductionCoilBlock;
import dev.jensh.createinduction.content.magnet.PermanentMagnetBlock;
import dev.jensh.createinduction.content.slipring.SlipRingBlock;
import dev.jensh.createinduction.content.slipring.SlipRingBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Builds the gallery scenes in a fresh flat world and photographs them: a plain generator, a water-wheel build
 * charging a Power Grid battery, a rotor ringed with riveted coils, and an armature on the rotor with slip rings.
 * Enabled with -Dcreateinduction.showcase=<output dir>; run through scripts/headless.py. Scenes whose mods are
 * missing are skipped and say so in result.txt.
 */
public final class Showcase {
    private static final int SPACING = 72;
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
            if (++ticks > 20 * 60 * 15) throw new IllegalStateException("Showcase timed out");
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
        boolean grid = ModList.get().isLoaded("powergrid");
        boolean rivets = ModList.get().isLoaded("simulatedcoasters");
        steps.add((mc, server) -> {
            mc.options.hideGui = true;
            mc.options.fov().set(55);
            return true;
        });
        steps.add(onServer(server -> {
            ServerLevel level = server.overworld();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.setDayTime(4000);
            level.setWeatherParameters(100000, 0, false, false);
            ground = level.getChunk(0, 0).getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8) + 1;
        }));

        String only = System.getProperty("createinduction.showcase.only", "");
        if (!only.isEmpty()) {
            grid = only.contains("mill");
            rivets = only.contains("rivet");
        }
        if (only.isEmpty() || only.contains("plain")) {
        scene(0, this::buildPlain, 5.5, 3.2, 6.5, 2);
        shot("blocks");
        steps.add(onServer(server -> bearing(server, origin(0).above()).assembleNextTick = true));
        pause(160);
        steps.add(onServer(server -> describe(server, "plain", origin(0).above())));
        shot("generator");
        steps.add(onServer(server -> view(server, origin(0).above(2), 0.5, 7.5, 3.0)));
        pause(30);
        shot("generator-above");
        }

        // 2: water wheels, geared up, turning a wheel-shaped rotor that charges a battery.
        if (grid) {
            scene(1, this::buildWaterMill, 9.5, 4.5, 11.5, 4);
            pause(200);
            steps.add(onServer(server -> report.add("watermill battery before=" + PowerGridRig.battery(server.overworld(), mill().offset(3, 0, 12)))));
            steps.add(onServer(server -> bearing(server, mill().offset(3, 4, 6)).assembleNextTick = true));
            pause(500);
            steps.add(onServer(server -> {
                describe(server, "watermill", mill().offset(3, 4, 6));
                report.add("watermill battery=" + PowerGridRig.battery(server.overworld(), mill().offset(3, 0, 12)));
            }));
            shot("watermill");
            steps.add(onServer(server -> view(server, mill().offset(3, 4, 8), -7.5, 2.5, 5.5)));
            pause(30);
            shot("watermill-wheels");
        } else {
            report.add("watermill skipped: no Power Grid");
        }

        // 3: four coils on the grid and four more riveted in between, at 45 degrees.
        if (rivets) {
            scene(2, this::buildPlain, 5.5, 3.2, 6.5, 2);
            for (int corner = 0; corner < 4; corner++) {
                int index = corner;
                steps.add(onServer(server -> standOverCorner(server, index)));
                pause(10);
                steps.add((mc, server) -> {
                    placeRivet(index);
                    return true;
                });
                pause(10);
            }
            steps.add(onServer(this::mountRivetedCoils));
            steps.add(onServer(server -> bearing(server, origin(2).above()).assembleNextTick = true));
            steps.add(onServer(server -> view(server, origin(2).above(2), 4.5, 4.2, 6.5)));
            pause(160);
            steps.add(onServer(server -> describe(server, "riveted", origin(2).above())));
            shot("riveted");
            steps.add(onServer(server -> view(server, origin(2).above(2), 0.5, 8.5, 2.5)));
            pause(30);
            shot("riveted-above");
        } else {
            report.add("riveted skipped: no Simulated Coasters");
        }

        boolean wires = ModList.get().isLoaded("powergrid");
        if (wires && (only.isEmpty() || only.contains("motor"))) motorScene();
        if (!only.isEmpty() && !only.contains("inside")) return;
        // 4: coils on the rotor, magnets around it, slip rings on the axis.
        scene(3, this::buildInsideOut, 5.5, 4.0, 6.5, 3);
        steps.add(onServer(server -> bearing(server, origin(3).above()).assembleNextTick = true));
        pause(30);
        steps.add(onServer(server -> finishInsideOut(server, wires)));
        pause(200);
        steps.add(onServer(server -> {
            describe(server, "insideout", origin(3).above());
            if (server.overworld().getBlockEntity(origin(3).above(5)) instanceof SlipRingBlockEntity ring) {
                report.add("insideout ring linked=" + (ring.partner() != null) + " rate=" + ring.rate());
            }
            if (wires) report.add("insideout battery=" + PowerGridRig.battery(server.overworld(), origin(3).offset(5, 0, 2)));
        }));
        shot("inside-out");
        steps.add(onServer(server -> view(server, origin(3).above(4), 4.2, 0.9, 1.6)));
        pause(30);
        shot("slip-rings");
    }

    // 5: a brushed motor. Battery to the brushes, commutator and coils on the rotor, the bearing turning machines.
    private void motorScene() {
        scene(4, this::buildMotor, 6.0, 3.0, 5.0, 3);
        steps.add(onServer(server -> bearing(server, origin(4).above(2)).assembleNextTick = true));
        pause(30);
        steps.add(onServer(server -> {
            ServerLevel level = server.overworld();
            BlockPos brushes = origin(4).above(5);
            for (int x : new int[] {-3, 3}) {
                for (int y = 0; y <= 6; y++) level.setBlock(origin(4).offset(x, y, -2), casing(), 3);
            }
            for (int x = -3; x <= 3; x++) level.setBlock(brushes.offset(x, 1, -2), casing(), 3);
            for (int z = -1; z <= 0; z++) level.setBlock(brushes.offset(0, 1, z), casing(), 3);
            level.setBlock(brushes, ModContent.SLIP_RING.get().defaultBlockState().setValue(SlipRingBlock.FACING, Direction.DOWN), 3);
            BlockPos battery = brushes.offset(2, 2, -2);
            level.setBlock(battery, grid("battery").defaultBlockState(), 3);
            for (int terminal = 0; terminal < 2; terminal++) PowerGridRig.wire(level, brushes, terminal, battery, terminal);
        }));
        pause(240);
        steps.add(onServer(server -> {
            DynamoBearingBlockEntity bearing = bearing(server, origin(4).above(2));
            report.add("motor rotor=" + bearing.rotorSpeed() + " rpm shaft=" + bearing.getSpeed() + " output=" + Math.round(bearing.outputWatts())
                + " W battery=" + PowerGridRig.battery(server.overworld(), origin(4).above(5).offset(2, 2, -2)));
            if (Math.abs(bearing.getSpeed()) < 8) throw new IllegalStateException("motor: shaft is not turning");
        }));
        shot("motor");
        steps.add(onServer(server -> view(server, origin(4).above(4), 3.6, 1.6, 2.6)));
        pause(30);
        shot("commutator");
    }

    private void buildMotor(ServerLevel level) {
        BlockPos base = origin(4);
        BlockPos hub = base.above(3);
        BlockPos commutator = hub.above();
        // Below the bearing: a gearbox turning the shaft sideways to a drill and a saw.
        level.setBlock(base, create("gearbox").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3);
        level.setBlock(base.above(), create("shaft").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y), 3);
        level.setBlock(base.south(), create("shaft").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 3);
        level.setBlock(base.south(2), create("mechanical_drill").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH), 3);
        level.setBlock(base.north(), create("large_cogwheel").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 3);
        level.setBlock(base.above(2), ModContent.DYNAMO_BEARING.get().defaultBlockState().setValue(DynamoBearingBlock.FACING, Direction.UP), 3);
        level.setBlock(hub, casing(), 3);
        level.setBlock(commutator, ModContent.COMMUTATOR.get().defaultBlockState().setValue(SlipRingBlock.FACING, Direction.UP), 3);
        for (Direction side : Direction.Plane.HORIZONTAL) level.setBlock(commutator.relative(side), coil(side), 3);
        glue(level, hub.offset(-1, 0, -1), commutator.offset(1, 0, 1));
        // One north and one south pole facing the armature.
        for (Direction side : new Direction[] {Direction.EAST, Direction.WEST}) {
            BlockPos post = commutator.relative(side, 3);
            for (int y = 1; y <= 4; y++) level.setBlock(post.below(y), casing(), 3);
            level.setBlock(post, magnet(Direction.WEST), 3);
        }
    }

    /** Goes to a scene, builds it and points the camera at {@code lookUp} blocks above its origin. */
    private void scene(int index, Consumer<ServerLevel> builder, double dx, double dy, double dz, int lookUp) {
        steps.add(onServer(server -> view(server, origin(index).above(lookUp), dx, dy, dz)));
        pause(40);
        steps.add(onServer(server -> {
            floor(server.overworld(), origin(index), 9);
            builder.accept(server.overworld());
        }));
        steps.add(onServer(server -> view(server, origin(index).above(lookUp), dx, dy, dz)));
        pause(40);
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

    private Step onServer(Consumer<MinecraftServer> action) {
        return (mc, server) -> {
            CompletableFuture.runAsync(() -> action.accept(server), server).join();
            return true;
        };
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    /** First air block above the ground in the middle of a scene. */
    private BlockPos origin(int scene) {
        return new BlockPos(8 + scene * SPACING, ground, 8);
    }

    private static Block create(String name) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", name));
    }

    private static Block grid(String name) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("powergrid", name));
    }

    private static BlockState casing() {
        return create("andesite_casing").defaultBlockState();
    }

    private static BlockState coil(Direction facing) {
        return ModContent.INDUCTION_COIL.get().defaultBlockState().setValue(InductionCoilBlock.FACING, facing);
    }

    private static BlockState magnet(Direction facing) {
        return ModContent.PERMANENT_MAGNET.get().defaultBlockState().setValue(PermanentMagnetBlock.FACING, facing);
    }

    private static void floor(ServerLevel level, BlockPos origin, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            }
        }
    }

    private static void glue(ServerLevel level, BlockPos from, BlockPos to) {
        level.addFreshEntity(new SuperGlueEntity(level, new AABB(
            Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()),
            Math.max(from.getX(), to.getX()) + 1, Math.max(from.getY(), to.getY()) + 1, Math.max(from.getZ(), to.getZ()) + 1)));
    }

    private static DynamoBearingBlockEntity bearing(MinecraftServer server, BlockPos pos) {
        return (DynamoBearingBlockEntity) server.overworld().getBlockEntity(pos);
    }

    private void describe(MinecraftServer server, String name, BlockPos bearingPos) {
        DynamoBearingBlockEntity bearing = bearing(server, bearingPos);
        report.add(name + " assembled=" + bearing.isAssembled() + " shaft=" + bearing.getSpeed() + " rotor=" + bearing.rotorSpeed()
            + " rpm delivered=" + Math.round(bearing.deliveredWatts()) + " W impact=" + bearing.calculateStressApplied() + " budget=" + Math.round(bearing.powerBudget()) + " W");
        if (!bearing.isAssembled()) throw new IllegalStateException(name + ": rotor did not assemble");
    }

    /** Puts the camera at an offset from a point, looking at it. */
    private void view(MinecraftServer server, BlockPos target, double dx, double dy, double dz) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.SPECTATOR);
        double x = target.getX() + 0.5 + dx, y = target.getY() + 0.5 + dy, z = target.getZ() + 0.5 + dz;
        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        float pitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.teleportTo(server.overworld(), x, y - player.getEyeHeight(), z, yaw, pitch);
    }

    // ---- scene 1 and 3: an upright rotor with four magnets between four coils on posts ---------------------------

    private void buildPlain(ServerLevel level) {
        BlockPos base = nearestOrigin(level);
        BlockPos hub = base.above(2);
        level.setBlock(base, create("creative_motor").defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.UP), 3);
        level.setBlock(base.above(), ModContent.DYNAMO_BEARING.get().defaultBlockState().setValue(DynamoBearingBlock.FACING, Direction.UP), 3);
        level.setBlock(hub, casing(), 3);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            level.setBlock(hub.relative(side), casing(), 3);
            level.setBlock(hub.relative(side, 2), magnet(side), 3);
            BlockPos post = hub.relative(side, 4);
            level.setBlock(post.below(2), casing(), 3);
            level.setBlock(post.below(), casing(), 3);
            level.setBlock(post, coil(side.getOpposite()), 3);
        }
        glue(level, hub.offset(-2, 0, -2), hub.offset(2, 0, 2));
        if (level.getBlockEntity(base) instanceof CreativeMotorBlockEntity motor) motor.generatedSpeed.setValue(64);
    }

    private int building;

    /** The scene the camera was last sent to; scene builders are shared between scenes. */
    private BlockPos nearestOrigin(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
        building = Math.max(0, Math.round((float) (player.getX() - 8) / SPACING));
        return origin(building);
    }

    // ---- scene 2: water wheels ----------------------------------------------------------------------------------

    private BlockPos mill() {
        return origin(1).offset(-3, 0, -4);
    }

    private void buildWaterMill(ServerLevel level) {
        BlockPos o = mill();
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        // Three wheels on one shaft, a roof over them so water only runs down one side, and a pool to catch it.
        for (int z = 0; z <= 2; z++) {
            level.setBlock(o.offset(0, 3, z), create("water_wheel").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH), 3);
            level.setBlock(o.offset(0, 4, z), stone, 3);
            level.setBlock(o.offset(-2, 4, z), stone, 3);
            level.setBlock(o.offset(-1, 5, z), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int x = -2; x <= 0; x++) {
            level.setBlock(o.offset(x, 4, -1), stone, 3);
            level.setBlock(o.offset(x, 4, 3), stone, 3);
        }
        for (int x = -3; x <= 1; x++) {
            for (int z = -2; z <= 4; z++) {
                boolean rim = x == -3 || x == 1 || z == -2 || z == 4;
                if (rim) level.setBlock(o.offset(x, 0, z), stone, 3);
            }
        }
        for (int y = 0; y <= 3; y++) {
            level.setBlock(o.offset(0, y, -1), stone, 3);
            if (y < 3) level.setBlock(o.offset(0, y, 3), stone, 3);
        }
        for (int z = 0; z <= 2; z++) level.setBlock(o.offset(-1, 4, z), Blocks.WATER.defaultBlockState(), 3);

        // 8 RPM at the wheels, doubled three times: large cog onto small cog, stepping along the shaft each time.
        BlockState large = create("large_cogwheel").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        BlockState small = create("cogwheel").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        level.setBlock(o.offset(0, 3, 3), large, 3);
        level.setBlock(o.offset(1, 4, 3), small, 3);
        level.setBlock(o.offset(1, 4, 4), large, 3);
        level.setBlock(o.offset(2, 3, 4), small, 3);
        level.setBlock(o.offset(2, 3, 5), large, 3);
        level.setBlock(o.offset(3, 4, 5), small, 3);
        for (int y = 0; y <= 2; y++) {
            level.setBlock(o.offset(1, y, 4), casing(), 3);
            level.setBlock(o.offset(3, y, 6), casing(), 3);
        }
        level.setBlock(o.offset(1, 3, 4), casing(), 3);
        level.setBlock(o.offset(3, 3, 6), casing(), 3);

        // The rotor turns like a wheel on a bearing facing south.
        BlockPos bearing = o.offset(3, 4, 6);
        // The arms sit one block out on the axle so they do not rub on the bearing's own support.
        BlockPos hub = bearing.south(2);
        level.setBlock(bearing, ModContent.DYNAMO_BEARING.get().defaultBlockState().setValue(DynamoBearingBlock.FACING, Direction.SOUTH), 3);
        level.setBlock(bearing.south(), casing(), 3);
        level.setBlock(hub, casing(), 3);
        for (Direction side : new Direction[] {Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}) {
            level.setBlock(hub.relative(side), casing(), 3);
            level.setBlock(hub.relative(side, 2), magnet(side), 3);
        }
        glue(level, hub.offset(-2, -2, -1), hub.offset(2, 2, 0));
        // Two coils level with the hub on posts, one on the ground under it.
        for (Direction side : new Direction[] {Direction.EAST, Direction.WEST}) {
            BlockPos post = hub.relative(side, 4);
            for (int y = 1; y <= 4; y++) level.setBlock(post.below(y), casing(), 3);
            level.setBlock(post, coil(side.getOpposite()), 3);
        }
        BlockPos battery = o.offset(3, 0, 12);
        level.setBlock(battery, grid("battery").defaultBlockState(), 3);
        for (Direction side : new Direction[] {Direction.EAST, Direction.WEST}) {
            for (int terminal = 0; terminal < 2; terminal++) PowerGridRig.wire(level, hub.relative(side, 4), terminal, battery, terminal);
        }
    }

    // ---- scene 3: rivets ----------------------------------------------------------------------------------------

    private static final int[][] CORNERS = {{3, 3}, {-3, 3}, {-3, -3}, {3, -3}};

    private BlockPos cornerPost(int corner) {
        return origin(2).offset(CORNERS[corner][0], 0, CORNERS[corner][1]);
    }

    private void standOverCorner(MinecraftServer server, int corner) {
        ServerLevel level = server.overworld();
        BlockPos post = cornerPost(corner);
        level.setBlock(post, casing(), 3);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath("simulatedcoasters", "rivet")), 16));
        player.teleportTo(level, post.getX() + 0.5, post.getY() + 1.6, post.getZ() + 0.5, 0, 90);
    }

    /** Asks the server for a rivet on top of a corner post, turned 45 degrees, the way the item does. */
    private void placeRivet(int corner) throws Exception {
        BlockPos post = cornerPost(corner);
        Object payload = Class.forName("dev.silvergold.simulatedcoasters.rivet.RivetPlacePayload")
            .getConstructor(BlockPos.class, Direction.class, Vec3.class, boolean.class, int.class, InteractionHand.class)
            .newInstance(post, Direction.UP, new Vec3(post.getX() + 0.5, post.getY() + 1.0, post.getZ() + 0.5), false, 45, InteractionHand.MAIN_HAND);
        PacketDistributor.sendToServer((CustomPacketPayload) payload);
    }

    /** Sets a coil on every rivet, turned to whichever side points at the rotor. */
    private void mountRivetedCoils(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos hub = origin(2).above(2);
        Block rivet = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("simulatedcoasters", "rivet"));
        int mounted = 0;
        for (ServerSubLevel subLevel : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            BlockPos centre = subLevel.getPlot().getCenterBlock();
            BlockPos rivetPos = null;
            for (BlockPos candidate : BlockPos.betweenClosed(centre.offset(-2, -2, -2), centre.offset(2, 2, 2))) {
                if (level.getBlockState(candidate).is(rivet)) {
                    rivetPos = candidate.immutable();
                    break;
                }
            }
            if (rivetPos == null) continue;
            Vector3d here = subLevel.logicalPose().transformPosition(new Vector3d(rivetPos.getX() + 0.5, rivetPos.getY() + 0.5, rivetPos.getZ() + 0.5), new Vector3d());
            if (Math.abs(here.x - hub.getX()) > 12) continue;
            Vector3d toHub = new Vector3d(hub.getX() + 0.5 - here.x, 0, hub.getZ() + 0.5 - here.z).normalize();
            Direction best = Direction.NORTH;
            double bestDot = -2;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                Vector3d world = subLevel.logicalPose().orientation().transform(new Vector3d(side.getStepX(), 0, side.getStepZ()));
                if (world.dot(toHub) > bestDot) {
                    bestDot = world.dot(toHub);
                    best = side;
                }
            }
            BlockPos coilPos = rivetPos.above();
            level.setBlock(coilPos, coil(best), 3);
            Vector3d coilWorld = subLevel.logicalPose().transformPosition(new Vector3d(coilPos.getX() + 0.5, coilPos.getY() + 0.5, coilPos.getZ() + 0.5), new Vector3d());
            report.add("riveted coil at " + String.format("%.2f %.2f %.2f", coilWorld.x - hub.getX(), coilWorld.y - hub.getY(), coilWorld.z - hub.getZ())
                + " from the hub, aim " + String.format("%.2f", bestDot) + ", rivet state " + level.getBlockState(rivetPos));
            mounted++;
        }
        report.add("riveted coils mounted=" + mounted);
        server.getPlayerList().getPlayers().get(0).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    // ---- scene 4: armature on the rotor -------------------------------------------------------------------------

    private void buildInsideOut(ServerLevel level) {
        BlockPos base = origin(3);
        BlockPos hub = base.above(2);
        BlockPos ring = hub.above();
        level.setBlock(base, create("creative_motor").defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.UP), 3);
        level.setBlock(base.above(), ModContent.DYNAMO_BEARING.get().defaultBlockState().setValue(DynamoBearingBlock.FACING, Direction.UP), 3);
        level.setBlock(hub, casing(), 3);
        level.setBlock(ring, ModContent.SLIP_RING.get().defaultBlockState().setValue(SlipRingBlock.FACING, Direction.UP), 3);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            level.setBlock(ring.relative(side), coil(side), 3);
            // Fixed magnets on posts, poles alternating round the ring.
            BlockPos post = ring.relative(side, 3);
            for (int y = 1; y <= 3; y++) level.setBlock(post.below(y), casing(), 3);
            level.setBlock(post, magnet(side.getAxis() == Direction.Axis.X ? side.getOpposite() : side), 3);
        }
        glue(level, hub.offset(-1, 0, -1), ring.offset(1, 0, 1));
    }

    /** After the rotor exists: the fixed slip ring on a gantry over it, the wiring, and the motor. */
    private void finishInsideOut(MinecraftServer server, boolean grid) {
        ServerLevel level = server.overworld();
        BlockPos base = origin(3);
        BlockPos ring = base.above(3);
        BlockPos fixed = ring.above();
        for (int z : new int[] {-3, 3}) {
            for (int y = 1; y <= 2; y++) level.setBlock(ring.offset(0, y, z), casing(), 3);
        }
        for (int z = -3; z <= 3; z++) level.setBlock(fixed.above().offset(0, 0, z), casing(), 3);
        level.setBlock(fixed, ModContent.SLIP_RING.get().defaultBlockState().setValue(SlipRingBlock.FACING, Direction.DOWN), 3);
        if (grid) {
            BlockPos battery = base.offset(5, 0, 2);
            level.setBlock(battery, Showcase.grid("battery").defaultBlockState(), 3);
            BlockPos plate = bearing(server, base.above()).platePos();
            BlockPos ringOnRotor = plate.above(2);
            for (int terminal = 0; terminal < 2; terminal++) {
                for (Direction side : Direction.Plane.HORIZONTAL) PowerGridRig.wire(level, ringOnRotor.relative(side), terminal, ringOnRotor, terminal);
                PowerGridRig.wire(level, fixed, terminal, battery, terminal);
            }
        }
        if (level.getBlockEntity(base) instanceof CreativeMotorBlockEntity motor) motor.generatedSpeed.setValue(48);
    }
}
