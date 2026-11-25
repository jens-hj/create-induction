package dev.jensh.createinduction.compat.powergrid;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.jensh.createinduction.Config;
import dev.jensh.createinduction.CreateInduction;
import dev.jensh.createinduction.content.slipring.SlipRingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.base.TerminalBoundingBox;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The slip ring in a Power Grid circuit: two wire terminals on its sides, each joined to the same terminal of the
 * ring it touches through the small resistance of the brushes.
 */
public class PowerGridSlipRingBlockEntity extends SlipRingBlockEntity implements IElectricEntity, IElectric {
    private static final Map<Direction, TerminalBoundingBox[]> TERMINALS = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) {
            TERMINALS.put(facing, new TerminalBoundingBox[] {
                terminal(facing, true).withColor(IDecoratedTerminal.RED),
                terminal(facing, false).withColor(IDecoratedTerminal.BLUE)
            });
        }
    }

    private ElectricBehaviour electric;
    private final ElectricWire[] brushes = new ElectricWire[2];
    private int retry;

    public PowerGridSlipRingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** A pad on one side of the body, which fills the back half of the block. */
    private static TerminalBoundingBox terminal(Direction facing, boolean first) {
        double[] min = {6, 6, 6}, max = {10, 10, 10};
        int along = facing.getAxis().ordinal();
        int across = facing.getAxis() == Direction.Axis.X ? 2 : 0;
        boolean positive = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        min[along] = positive ? 2 : 10;
        max[along] = positive ? 6 : 14;
        min[across] = first ? 14 : 0;
        max[across] = first ? 16 : 2;
        return new TerminalBoundingBox(first ? IDecoratedTerminal.POSITIVE : IDecoratedTerminal.NEGATIVE,
            min[0], min[1], min[2], max[0], max[1], max[2]);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        electric = new ElectricBehaviour(this);
        behaviours.add(electric);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(2);
    }

    @Override
    public float resistance() {
        return Config.SLIP_RING_RESISTANCE.get().floatValue();
    }

    @Override
    public float resistance(String suffix) {
        return resistance();
    }

    @Override
    public int terminalCount() {
        return 2;
    }

    @Override
    public ITerminalPlacement terminal(BlockState state, int index) {
        return TERMINALS.get(state.getValue(BlockStateProperties.FACING))[index];
    }

    @Override
    public void remove() {
        dropBrushes();
        super.remove();
        if (electric != null) electric.remove();
    }

    /** Only one ring of a pair holds the brushes, so they are never doubled. */
    private boolean owns() {
        return partner() != null && worldPosition.asLong() < partner().getBlockPos().asLong();
    }

    @Override
    protected void whileLinked() {
        // Only another wired ring has terminals to join; a commutator takes Forge Energy.
        if (!(partner() instanceof PowerGridSlipRingBlockEntity)) return;
        if (!owns() || brushes[0] != null || --retry > 0) return;
        retry = 20;
        try {
            for (int i = 0; i < 2; i++) {
                brushes[i] = GlobalElectricNetworks.makeSimpleConnection(level, new BlockWireEndpoint(worldPosition, i),
                    new BlockWireEndpoint(partner().getBlockPos(), i), resistance());
            }
        } catch (RuntimeException e) {
            CreateInduction.LOGGER.debug("Slip ring at {} could not join its partner yet", worldPosition, e);
            dropBrushes();
        }
    }

    @Override
    protected void onUnlinked() {
        dropBrushes();
    }

    private void dropBrushes() {
        for (int i = 0; i < 2; i++) {
            if (brushes[i] != null) {
                try {
                    brushes[i].remove();
                } catch (RuntimeException ignored) {
                }
                brushes[i] = null;
            }
        }
    }
}
