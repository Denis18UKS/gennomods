package dev.zunserz.firstcontact;

import com.mojang.brigadier.Command;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * First Contact — independent story mod. Create: Radars supplies the real antenna
 * and scans; Create: Aeronautics supplies the physical ship. The radio is ours.
 * Game/server authoritative story state is shared by both multiplayer players.
 */
@Mod(FirstContact.ID)
public final class FirstContact {
    public static final String ID = "firstcontact";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> MORSE_DOT =
        SOUNDS.register("morse_dot", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(ID, "morse_dot")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MORSE_DASH =
        SOUNDS.register("morse_dash", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(ID, "morse_dash")));
    public static final DeferredHolder<SoundEvent, SoundEvent> STATIC =
        SOUNDS.register("radio_static", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(ID, "radio_static")));

    public static final DeferredBlock<RadioBlock> RADIO = BLOCKS.register("radio_receiver",
        () -> new RadioBlock(BlockBehaviour.Properties.of()
            .strength(2.8F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> RADIO_ITEM = ITEMS.register("radio_receiver",
        () -> new BlockItem(RADIO.get(), new Item.Properties()));

    private long serverTicks;

    public FirstContact(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        bus.addListener(ContactNetwork::register);
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    public static final class RadioBlock extends HorizontalDirectionalBlock {
        public static final MapCodec<RadioBlock> CODEC = simpleCodec(RadioBlock::new);
        @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
        RadioBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }
        @Override protected void createBlockStateDefinition(
                StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }
        @Override protected InteractionResult useWithoutItem(BlockState state, Level level,
                 BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) sendRadio(sp, pos);
            return InteractionResult.SUCCESS;
        }
    }

    public static void sendRadio(ServerPlayer player, BlockPos pos) {
        if (!player.serverLevel().getBlockState(pos).is(RADIO.get())) return;
        Story story = Story.of(player.serverLevel());
        PacketDistributor.sendToPlayer(player, new ContactNetwork.Snapshot(pos,
            story.messageReady, story.messageRead,
            story.getFrequency(player.serverLevel(), pos)));
    }

    private void commands(RegisterCommandsEvent event) {
        var root = Commands.literal("firstcontact");
        root.then(Commands.literal("status").executes(ctx -> {
            Story s = Story.of(ctx.getSource().getLevel());
            ctx.getSource().sendSuccess(() -> Component.literal(
                "First Contact | radar=" + s.radarCalibrated + " | scanning=" + s.radarMoving
                + " | dishes=" + s.radarDishCount + " | range=" + s.radarRange
                + " | tracks=" + s.radarTracks
                + " | ship=" + s.shipReady + (s.shipVerified ? " [Aeronautics]" : " [manual]")
                + " | readyOverride=" + s.manualReady
                + " | message=" + s.messageReady + " | read=" + s.messageRead), false);
            return 1;
        }));
        var radar = Commands.literal("radar");
        radar.then(Commands.literal("link").executes(ctx -> linkRadar(ctx.getSource().getPlayerOrException())));
        radar.then(Commands.literal("calibrate").executes(ctx -> linkRadar(ctx.getSource().getPlayerOrException())));
        radar.then(Commands.literal("test_motion").requires(src -> src.hasPermission(2))
            .executes(ctx -> {
                Story s = Story.of(ctx.getSource().getLevel());
                s.manualReady = true; s.radarCalibrated = true; s.radarMoving = true;
                s.setDirty();
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "DEV: принудительно подтверждена работа радара."), true);
                return 1;
            }));
        root.then(radar);

        var ship = Commands.literal("ship");
        ship.then(Commands.literal("verify").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            AeronauticsIntegration.ShipCheck result = AeronauticsIntegration.check(player);
            if (!result.aboard()) {
                player.sendSystemMessage(Component.literal("Корабль не подтверждён: " + result.description()));
                return 0;
            }
            Story s = Story.of(player.serverLevel());
            s.shipReady = true; s.shipVerified = true; s.setDirty();
            player.sendSystemMessage(Component.literal(
                "Настоящий корабль Aeronautics обнаружен! Готовность подтверждена."));
            return 1;
        }));
        ship.then(Commands.literal("ready").requires(src -> src.hasPermission(2))
            .executes(ctx -> {
                Story s = Story.of(ctx.getSource().getLevel());
                s.shipReady = true; s.shipVerified = false; s.setDirty();
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "DEV: готовность корабля подтверждена без проверки физической конструкции."), true);
                return 1;
            }));
        ship.then(Commands.literal("reset").requires(src -> src.hasPermission(2))
            .executes(ctx -> {
                Story s = Story.of(ctx.getSource().getLevel());
                s.shipReady = false; s.shipVerified = false; s.setDirty();
                ctx.getSource().sendSuccess(() -> Component.literal("Готовность корабля отменена."), true);
                return 1;
            }));
        root.then(ship);

        var radio = Commands.literal("radio").requires(src -> src.hasPermission(2));
        radio.then(Commands.literal("deliver").executes(ctx -> {
            Story s = Story.of(ctx.getSource().getLevel());
            s.messageReady = true; s.waitUntil = -1; s.setDirty();
            ctx.getSource().sendSuccess(() -> Component.literal(
                "DEV: неизвестная передача сохранена в приёмнике (98.7 MHz)."), true);
            return 1;
        }));
        radio.then(Commands.literal("reset").executes(ctx -> {
            Story s = Story.of(ctx.getSource().getLevel());
            s.messageReady = false; s.messageRead = false;
            s.waitUntil = -1; s.setDirty();
            ctx.getSource().sendSuccess(() -> Component.literal(
                "Радиоархив очищен; новый сигнал сможет поступить по сюжету."), true);
            return 1;
        }));
        root.then(radio);

        // The user's explicit "everything is ready" trigger: one OP-only command.
        // It starts a delayed recording and never sends spoiler text to global chat.
        root.then(Commands.literal("ready").requires(src -> src.hasPermission(2))
            .executes(ctx -> {
                Story s = Story.of(ctx.getSource().getLevel());
                s.manualReady = true;
                s.radarCalibrated = true;
                s.radarMoving = true;
                s.shipReady = true;
                s.shipVerified = false;
                if (!s.messageReady)
                    s.waitUntil = ctx.getSource().getLevel().getServer().overworld().getGameTime() + 1200;
                s.setDirty();
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "DEV: всё готово. Передача появится в радиоархиве через 60 игровых секунд. Открой радио позднее."), true);
                return Command.SINGLE_SUCCESS;
            })
            .then(Commands.literal("reset").executes(ctx -> {
                Story s = Story.of(ctx.getSource().getLevel());
                s.manualReady = false; s.shipReady = false; s.shipVerified = false;
                s.radarCalibrated = false; s.radarMoving = false;
                s.messageReady = false; s.messageRead = false; s.waitUntil = -1;
                s.setDirty();
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "DEV: история и принудительная готовность сброшены. Связь с радаром сохранена."), true);
                return 1;
            })));
        event.getDispatcher().register(root);
    }

    private static int linkRadar(ServerPlayer player) {
        BlockPos nearest = RadarIntegration.findNearby(player.serverLevel(), player.blockPosition());
        if (nearest == null) {
            player.sendSystemMessage(Component.literal(
                "Рядом нет настоящего Radar Bearing из Create: Radars (32 блока)."));
            return 0;
        }
        Story s = Story.of(player.serverLevel());
        s.radarPos = nearest.asLong();
        s.radarDimension = player.serverLevel().dimension().location().toString();
        s.manualReady = false;
        RadarIntegration.Status st = RadarIntegration.probe(player.serverLevel(), nearest);
        s.radarCalibrated = st.assembled() && st.dishPanels() >= 8;
        s.radarMoving = st.ready();
        s.radarDishCount = st.dishPanels();
        s.radarRange = st.range();
        s.radarTracks = st.tracks();
        s.setDirty();
        player.sendSystemMessage(Component.literal("Create: Radars привязан: "
            + nearest.toShortString() + ". Панелей: " + st.dishPanels()
            + ", дальность: " + st.range() + ", состояние: " + st.detail()
            + ". Чтобы радар засчитался, собери минимум 8 панелей, установи Receiver и подай вращение."));
        return 1;
    }

    private void tick(ServerTickEvent.Post event) {
        if (++serverTicks % 20 != 0) return;
        ServerLevel overworld = event.getServer().overworld();
        Story s = Story.of(overworld);
        if (!s.manualReady) {
            ResourceLocation dim = ResourceLocation.tryParse(s.radarDimension);
            ServerLevel radarWorld = dim == null ? null :
                event.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            if (s.radarPos != Long.MIN_VALUE && radarWorld != null
                    && radarWorld.hasChunkAt(BlockPos.of(s.radarPos))) {
                RadarIntegration.Status st = RadarIntegration.probe(radarWorld, BlockPos.of(s.radarPos));
                boolean calibrate = st.assembled() && st.dishPanels() >= 8;
                boolean moving = st.ready();
                if (calibrate != s.radarCalibrated || moving != s.radarMoving
                        || s.radarDishCount != st.dishPanels() || s.radarTracks != st.tracks()
                        || s.radarRange != st.range()) {
                    s.radarCalibrated = calibrate;
                    s.radarMoving = moving;
                    s.radarDishCount = st.dishPanels();
                    s.radarRange = st.range();
                    s.radarTracks = st.tracks();
                    s.setDirty();
                }
            }
        }
        if (s.shipReady && s.radarMoving && !s.messageReady) {
            if (s.waitUntil < 0) {
                s.waitUntil = overworld.getGameTime() + 1200;
                s.setDirty();
            } else if (overworld.getGameTime() >= s.waitUntil) {
                s.messageReady = true;
                s.waitUntil = -1;
                s.setDirty();
            }
        } else if (!s.messageReady && s.waitUntil >= 0 && !s.manualReady) {
            s.waitUntil = -1;
            s.setDirty();
        }
    }

    public static final class Story extends SavedData {
        public boolean radarCalibrated;
        public boolean radarMoving;
        public boolean shipReady;
        public boolean shipVerified;
        public boolean manualReady;
        public boolean messageReady;
        public boolean messageRead;
        public long radarPos = Long.MIN_VALUE;
        public String radarDimension = "minecraft:overworld";
        public long waitUntil = -1;
        public int radarDishCount;
        public float radarRange;
        public int radarTracks;
        private final Map<String, Integer> frequencies = new HashMap<>();

        public static Story of(ServerLevel level) {
            return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(Story::new, Story::load),
                    "firstcontact_story");
        }
        private static String key(ServerLevel level, BlockPos pos) {
            return level.dimension().location() + "@" + pos.asLong();
        }
        public int getFrequency(ServerLevel level, BlockPos pos) {
            return frequencies.getOrDefault(key(level,pos), 987);
        }
        public void setFrequency(ServerLevel level, BlockPos pos, int value) {
            frequencies.put(key(level,pos), Math.clamp(value, 880, 1080));
            setDirty();
        }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            tag.putBoolean("RadarCalibrated", radarCalibrated);
            tag.putBoolean("RadarMoving", radarMoving);
            tag.putBoolean("ShipReady", shipReady);
            tag.putBoolean("ShipVerified", shipVerified);
            tag.putBoolean("ManualReady", manualReady);
            tag.putBoolean("MessageReady", messageReady);
            tag.putBoolean("MessageRead", messageRead);
            tag.putLong("RadarPos", radarPos);
            tag.putString("RadarDimension", radarDimension);
            tag.putLong("WaitUntil", waitUntil);
            tag.putInt("RadarDishCount", radarDishCount);
            tag.putFloat("RadarRange", radarRange);
            tag.putInt("RadarTracks", radarTracks);
            CompoundTag radio = new CompoundTag();
            frequencies.forEach(radio::putInt);
            tag.put("RadioFrequencies", radio);
            return tag;
        }
        private static Story load(CompoundTag tag, HolderLookup.Provider provider) {
            Story s = new Story();
            s.radarCalibrated = tag.getBoolean("RadarCalibrated");
            s.radarMoving = tag.getBoolean("RadarMoving");
            s.shipReady = tag.getBoolean("ShipReady");
            s.shipVerified = tag.getBoolean("ShipVerified");
            s.manualReady = tag.getBoolean("ManualReady");
            s.messageReady = tag.getBoolean("MessageReady");
            s.messageRead = tag.getBoolean("MessageRead");
            s.radarPos = tag.contains("RadarPos") ? tag.getLong("RadarPos") : Long.MIN_VALUE;
            s.radarDimension = tag.getString("RadarDimension");
            if (s.radarDimension.isEmpty()) s.radarDimension = "minecraft:overworld";
            s.waitUntil = tag.contains("WaitUntil") ? tag.getLong("WaitUntil") : -1;
            s.radarDishCount = tag.getInt("RadarDishCount");
            s.radarRange = tag.getFloat("RadarRange");
            s.radarTracks = tag.getInt("RadarTracks");
            CompoundTag radio = tag.getCompound("RadioFrequencies");
            for (String key : radio.getAllKeys()) s.frequencies.put(key, radio.getInt(key));
            return s;
        }
    }
}
