package io.github.minazukisora.rollercart;

import io.github.minazukisora.rollercart.block.ActivatorTiesBlock;
import io.github.minazukisora.rollercart.block.ActivatorTiesBlockEntity;
import io.github.minazukisora.rollercart.block.DetectorTiesBlock;
import io.github.minazukisora.rollercart.block.DetectorTiesBlockEntity;
import io.github.minazukisora.rollercart.block.ShuttleTiesBlock;
import io.github.minazukisora.rollercart.block.SwitchTiesBlock;
import io.github.minazukisora.rollercart.block.TrackTiesBlock;
import io.github.minazukisora.rollercart.block.TrackTiesBlockEntity;
import io.github.minazukisora.rollercart.entity.TrackFollowerEntity;
import io.github.minazukisora.rollercart.item.LoreBlockItem;
import io.github.minazukisora.rollercart.item.TrackItem;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Mod(RollerCart.MOD_ID)
public class RollerCart {
    public static final String MOD_ID = "rollercart";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);
    private static final DeferredRegister<ItemGroup> ITEM_GROUPS = DeferredRegister.create(RegistryKeys.ITEM_GROUP, MOD_ID);

    public static final RegistryObject<TrackTiesBlock> TRACK_TIES = BLOCKS.register("track_ties", () -> new TrackTiesBlock(AbstractBlock.Settings.copy(Blocks.RAIL)));
    public static final RegistryObject<SwitchTiesBlock> SWITCH_TIES = BLOCKS.register("switch_ties", () -> new SwitchTiesBlock(AbstractBlock.Settings.copy(Blocks.RAIL)));
    public static final RegistryObject<TrackTiesBlock> INVISIBLE_TIES = BLOCKS.register("invisible_ties", () -> new TrackTiesBlock(AbstractBlock.Settings.copy(Blocks.RAIL)) {
        @Override
        public BlockRenderType getRenderType(BlockState state) {
            return BlockRenderType.INVISIBLE;
        }
    });
    public static final RegistryObject<ShuttleTiesBlock> SHUTTLE_TIES = BLOCKS.register("shuttle_ties", () -> new ShuttleTiesBlock(AbstractBlock.Settings.copy(Blocks.RAIL)));
    public static final RegistryObject<DetectorTiesBlock> DETECTOR_TIES = BLOCKS.register("detector_ties", () -> new DetectorTiesBlock(AbstractBlock.Settings.copy(Blocks.RAIL)));
    public static final RegistryObject<ActivatorTiesBlock> ACTIVATOR_TIES = BLOCKS.register("activator_ties", () -> new ActivatorTiesBlock(AbstractBlock.Settings.copy(Blocks.RAIL)));

    public static final RegistryObject<TrackItem> TRACK = ITEMS.register("track", () -> new TrackItem(new Item.Settings(), TrackItem.Type.STANDARD));
    public static final RegistryObject<TrackItem> CHAIN_TRACK = ITEMS.register("chain_track", () -> new TrackItem(new Item.Settings(), TrackItem.Type.CHAIN));
    public static final RegistryObject<TrackItem> STATION_TRACK = ITEMS.register("station_track", () -> new TrackItem(new Item.Settings(), TrackItem.Type.STATION));
    public static final RegistryObject<TrackItem> BRAKE_TRACK = ITEMS.register("brake_track", () -> new TrackItem(new Item.Settings(), TrackItem.Type.BRAKE));
    public static final RegistryObject<TrackItem> MAGNETIC_TRACK = ITEMS.register("magnetic_track", () -> new TrackItem(new Item.Settings(), TrackItem.Type.MAGNETIC));
    public static final RegistryObject<Item> TRACK_TIES_ITEM = blockItem("track_ties", TRACK_TIES, "item.rollercart.track_ties.desc");
    public static final RegistryObject<Item> SWITCH_TIES_ITEM = blockItem("switch_ties", SWITCH_TIES, "item.rollercart.track_ties.desc", "item.rollercart.switch_ties.desc");
    public static final RegistryObject<Item> INVISIBLE_TIES_ITEM = blockItem("invisible_ties", INVISIBLE_TIES, "item.rollercart.track_ties.desc", "item.rollercart.invisible_ties.desc");
    public static final RegistryObject<Item> SHUTTLE_TIES_ITEM = blockItem("shuttle_ties", SHUTTLE_TIES, "item.rollercart.track_ties.desc", "item.rollercart.shuttle_ties.desc");
    public static final RegistryObject<Item> DETECTOR_TIES_ITEM = blockItem("detector_ties", DETECTOR_TIES, "item.rollercart.track_ties.desc", "item.rollercart.detector_ties.desc");
    public static final RegistryObject<Item> ACTIVATOR_TIES_ITEM = blockItem("activator_ties", ACTIVATOR_TIES, "item.rollercart.track_ties.desc", "item.rollercart.activator_ties.desc");

    public static final RegistryObject<BlockEntityType<TrackTiesBlockEntity>> TRACK_TIES_BE = BLOCK_ENTITY_TYPES.register("track_ties", () ->
            BlockEntityType.Builder.create(TrackTiesBlockEntity::new, TRACK_TIES.get(), SWITCH_TIES.get(), INVISIBLE_TIES.get(), SHUTTLE_TIES.get()).build(null));
    public static final RegistryObject<BlockEntityType<DetectorTiesBlockEntity>> DETECTOR_TIES_BE = BLOCK_ENTITY_TYPES.register("detector_ties", () ->
            BlockEntityType.Builder.create(DetectorTiesBlockEntity::new, DETECTOR_TIES.get()).build(null));
    public static final RegistryObject<BlockEntityType<ActivatorTiesBlockEntity>> ACTIVATOR_TIES_BE = BLOCK_ENTITY_TYPES.register("activator_ties", () ->
            BlockEntityType.Builder.create(ActivatorTiesBlockEntity::new, ACTIVATOR_TIES.get()).build(null));
    public static final RegistryObject<EntityType<TrackFollowerEntity>> TRACK_FOLLOWER = ENTITY_TYPES.register("track_follower", () ->
            EntityType.Builder.<TrackFollowerEntity>create(TrackFollowerEntity::new, SpawnGroup.MISC).setDimensions(0.25f, 0.25f).build(id("track_follower").toString()));
    public static final Identifier CHAIN_LIFT_SOUND_ID = id("entity.track_follower.lift");
    public static final RegistryObject<SoundEvent> CHAIN_LIFT_SOUND = SOUND_EVENTS.register("entity.track_follower.lift", () -> SoundEvent.of(CHAIN_LIFT_SOUND_ID));
    public static final RegistryObject<ItemGroup> MOD_GROUP = ITEM_GROUPS.register("rollercart", () -> ItemGroup.builder()
            .displayName(Text.translatable("itemGroup.rollercart"))
            .icon(() -> new ItemStack(TRACK.get()))
            .entries((context, entries) -> {
                entries.add(TRACK_TIES_ITEM.get());
                entries.add(SWITCH_TIES_ITEM.get());
                entries.add(INVISIBLE_TIES_ITEM.get());
                entries.add(SHUTTLE_TIES_ITEM.get());
                entries.add(DETECTOR_TIES_ITEM.get());
                entries.add(ACTIVATOR_TIES_ITEM.get());
                entries.add(TRACK.get());
                entries.add(CHAIN_TRACK.get());
                entries.add(STATION_TRACK.get());
                entries.add(BRAKE_TRACK.get());
                entries.add(MAGNETIC_TRACK.get());
            }).build());

    public static final TagKey<EntityType<?>> CARTS = TagKey.of(RegistryKeys.ENTITY_TYPE, id("carts"));
    public static final TagKey<Item> TRACK_TAG = TagKey.of(RegistryKeys.ITEM, id("track"));

    public RollerCart() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITY_TYPES.register(bus);
        ENTITY_TYPES.register(bus);
        SOUND_EVENTS.register(bus);
        ITEM_GROUPS.register(bus);
    }

    private static RegistryObject<Item> blockItem(String name, RegistryObject<? extends Block> block, String... descriptions) {
        return ITEMS.register(name, () -> new LoreBlockItem(block.get(), new Item.Settings(),
                java.util.Arrays.stream(descriptions).map(key -> (Text) Text.translatable(key).formatted(Formatting.GRAY, Formatting.ITALIC)).toList()));
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
