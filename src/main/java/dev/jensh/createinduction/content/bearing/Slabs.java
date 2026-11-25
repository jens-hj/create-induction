package dev.jensh.createinduction.content.bearing;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/** Full-width slices of a block measured along its facing, from the back (0) to the front (16). */
final class Slabs {
    private Slabs() {}

    static Map<Direction, VoxelShape> slice(double from, double to) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.UP, Block.box(0, from, 0, 16, to, 16));
        shapes.put(Direction.DOWN, Block.box(0, 16 - to, 0, 16, 16 - from, 16));
        shapes.put(Direction.SOUTH, Block.box(0, 0, from, 16, 16, to));
        shapes.put(Direction.NORTH, Block.box(0, 0, 16 - to, 16, 16, 16 - from));
        shapes.put(Direction.EAST, Block.box(from, 0, 0, to, 16, 16));
        shapes.put(Direction.WEST, Block.box(16 - to, 0, 0, 16 - from, 16, 16));
        return shapes;
    }
}
