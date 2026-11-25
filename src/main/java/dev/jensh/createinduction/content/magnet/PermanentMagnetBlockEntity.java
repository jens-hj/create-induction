package dev.jensh.createinduction.content.magnet;

import dev.jensh.createinduction.Config;
import dev.simulated_team.simulated.content.blocks.redstone_magnet.RedstoneMagnetBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3d;

/**
 * Lives in Simulated's magnet map so it attracts and repels Redstone Magnets like one of their own. Simulated's
 * particle code casts every entry of that map to its own block entity, hence the subclass.
 */
public class PermanentMagnetBlockEntity extends RedstoneMagnetBlockEntity {
    private static final int FULL_SIGNAL = 15;

    public PermanentMagnetBlockEntity(BlockPos pos, BlockState state) {
        this(dev.jensh.createinduction.ModContent.PERMANENT_MAGNET_ENTITY.get(), pos, state);
    }

    public PermanentMagnetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.signalStrength = FULL_SIGNAL;
        this.powered = true;
    }

    @Override
    public void updateSignal() {
        // Needs no redstone.
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        this.signalStrength = FULL_SIGNAL;
        this.powered = true;
    }

    @Override
    public Vector3d setMagneticMoment(Vector3d v) {
        Vec3i normal = getBlockState().getValue(BlockStateProperties.FACING).getNormal();
        return v.set(normal.getX(), normal.getY(), normal.getZ()).mul(Math.sqrt(Config.MAGNET_STRENGTH.get()));
    }

    @Override
    public boolean magnetActive() {
        return true;
    }
}
