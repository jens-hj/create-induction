package dev.jensh.createinduction;

import dev.jensh.createinduction.content.bearing.DynamoBearingBlock;
import dev.jensh.createinduction.content.bearing.DynamoBearingBlockEntity;
import dev.jensh.createinduction.content.bearing.DynamoPlateBlock;
import dev.jensh.createinduction.content.bearing.DynamoPlateBlockEntity;
import dev.jensh.createinduction.content.coil.InductionCoilBlock;
import dev.jensh.createinduction.content.coil.InductionCoilBlockEntity;
import dev.jensh.createinduction.content.magnet.PermanentMagnetBlock;
import dev.jensh.createinduction.content.magnet.PermanentMagnetBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateInduction.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateInduction.ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateInduction.ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateInduction.ID);

    private static BlockBehaviour.Properties metal(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(3.0f, 6.0f)
            .requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK);
    }

    public static final DeferredBlock<PermanentMagnetBlock> PERMANENT_MAGNET = BLOCKS.register("permanent_magnet",
        () -> new PermanentMagnetBlock(metal(MapColor.COLOR_GRAY)));
    public static final DeferredBlock<InductionCoilBlock> INDUCTION_COIL = BLOCKS.register("induction_coil",
        () -> new InductionCoilBlock(metal(MapColor.COLOR_ORANGE).sound(SoundType.COPPER)));
    public static final DeferredBlock<DynamoBearingBlock> DYNAMO_BEARING = BLOCKS.register("dynamo_bearing",
        () -> new DynamoBearingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).strength(2.5f, 6.0f)
            .sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<DynamoPlateBlock> DYNAMO_BEARING_PLATE = BLOCKS.register("dynamo_bearing_plate",
        () -> new DynamoPlateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).strength(2.5f, 6.0f)
            .sound(SoundType.WOOD).noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));

    public static final DeferredItem<BlockItem> PERMANENT_MAGNET_ITEM = ITEMS.registerSimpleBlockItem(PERMANENT_MAGNET);
    public static final DeferredItem<BlockItem> INDUCTION_COIL_ITEM = ITEMS.registerSimpleBlockItem(INDUCTION_COIL);
    public static final DeferredItem<BlockItem> DYNAMO_BEARING_ITEM = ITEMS.registerSimpleBlockItem(DYNAMO_BEARING);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PermanentMagnetBlockEntity>> PERMANENT_MAGNET_ENTITY =
        BLOCK_ENTITIES.register("permanent_magnet", () -> BlockEntityType.Builder
            .of(PermanentMagnetBlockEntity::new, PERMANENT_MAGNET.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InductionCoilBlockEntity>> INDUCTION_COIL_ENTITY =
        BLOCK_ENTITIES.register("induction_coil", () -> BlockEntityType.Builder
            .of(InductionCoilBlockEntity::create, INDUCTION_COIL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DynamoBearingBlockEntity>> DYNAMO_BEARING_ENTITY =
        BLOCK_ENTITIES.register("dynamo_bearing", () -> BlockEntityType.Builder
            .of(DynamoBearingBlockEntity::new, DYNAMO_BEARING.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DynamoPlateBlockEntity>> DYNAMO_BEARING_PLATE_ENTITY =
        BLOCK_ENTITIES.register("dynamo_bearing_plate", () -> BlockEntityType.Builder
            .of(DynamoPlateBlockEntity::new, DYNAMO_BEARING_PLATE.get()).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.create_induction"))
            .icon(() -> new ItemStack(INDUCTION_COIL_ITEM.get()))
            .displayItems((parameters, output) -> {
                output.accept(DYNAMO_BEARING_ITEM.get());
                output.accept(INDUCTION_COIL_ITEM.get());
                output.accept(PERMANENT_MAGNET_ITEM.get());
            })
            .build());

    private ModContent() {}

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
    }
}
