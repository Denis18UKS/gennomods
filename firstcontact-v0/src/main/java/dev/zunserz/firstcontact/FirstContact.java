package dev.zunserz.firstcontact;

import com.mojang.brigadier.Command;
import com.mojang.serialization.MapCodec;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(FirstContact.ID)
public final class FirstContact {
    public static final String ID = "firstcontact";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredBlock<RadioBlock> RADIO = BLOCKS.register("radio_receiver",
        () -> new RadioBlock(BlockBehaviour.Properties.of().strength(2.8F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> RADIO_ITEM = ITEMS.register("radio_receiver",
        () -> new BlockItem(RADIO.get(), new Item.Properties()));
    private long serverTicks;

    public FirstContact(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
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
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }
        @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }
        @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                                 Player player, BlockHitResult hit) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) sendRadio(sp, pos);
            return InteractionResult.SUCCESS;
        }
    }

    public static void sendRadio(ServerPlayer player, BlockPos pos) {
        if (!player.serverLevel().getBlockState(pos).is(RADIO.get())) return;
        Story story = Story.of(player.serverLevel());
        PacketDistributor.sendToPlayer(player, new ContactNetwork.Snapshot(pos, story.messageReady,
                story.messageRead, story.getFrequency(player.serverLevel(), pos)));
    }

    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("firstcontact")
            .then(Commands.literal("status").executes(ctx -> {
                Story story = Story.of(ctx.getSource().getLevel());
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "First Contact | radar=" + story.radarCalibrated
                    + " | moving=" + story.radarMoving
                    + " | ship=" + story.shipReady
                    + " | message=" + story.messageReady
                    + " | read=" + story.messageRead), false);
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("radar")
                .then(Commands.literal("calibrate").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    BlockPos center = player.blockPosition();
                    BlockPos closest = null;
                    double shortest = Double.MAX_VALUE;
                    for (BlockPos p : BlockPos.betweenClosed(
                            center.offset(-24, -12, -24), center.offset(24, 12, 24))) {
                        if (!BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock())
                                .toString().equals("create:mechanical_bearing")) continue;
                        double distance = p.distSqr(center);
                        if (distance < shortest) { shortest = distance; closest = p.immutable(); }
                    }
                    if (closest == null) {
                        player.sendSystemMessage(Component.literal("Рядом нет Mechanical Bearing из Create (радиус 24 блока)."));
                        return 0;
                    }
                    List<Blueprint.Part> missing = Blueprint.missing(level, closest);
                    if (!missing.isEmpty()) {
                        Blueprint.Part part = missing.getFirst();
                        player.sendSystemMessage(Component.literal("Радар не готов: отсутствует "
                            + missing.size() + " элементов. Первый: " + part.id()
                            + " по смещению (" + part.x() + ", " + part.y() + ", " + part.z()
                            + ") от Mechanical Bearing. Схема — в JEI."));
                        return 0;
                    }
                    BlockState base = level.getBlockState(closest);
                    if (base.hasProperty(BlockStateProperties.FACING)
                        && base.getValue(BlockStateProperties.FACING) != Direction.UP) {
                        player.sendSystemMessage(Component.literal("Mechanical Bearing должен смотреть вверх."));
                        return 0;
                    }
                    Story story = Story.of(level);
                    story.radarCalibrated = true;
                    story.radarMoving = false;
                    story.radarPos = closest.asLong();
                    story.radarDimension = level.dimension().location().toString();
                    story.setDirty();
                    player.sendSystemMessage(Component.literal("Радар откалиброван. Приклей конструкцию и запусти Mechanical Bearing через Create."));
                    return 1;
                })))
            .then(Commands.literal("ship").requires(s -> s.hasPermission(2))
                .then(Commands.literal("ready").executes(ctx -> {
                    Story s = Story.of(ctx.getSource().getLevel());
                    s.shipReady = true; s.setDirty();
                    ctx.getSource().sendSuccess(() -> Component.literal("ТЕСТ: космический корабль признан готовым."), true);
                    return 1;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    Story s = Story.of(ctx.getSource().getLevel());
                    s.shipReady = false; s.setDirty();
                    ctx.getSource().sendSuccess(() -> Component.literal("ТЕСТ: готовность корабля отменена."), true);
                    return 1;
                })))
            .then(Commands.literal("radio").requires(s -> s.hasPermission(2))
                .then(Commands.literal("deliver").executes(ctx -> {
                    Story s = Story.of(ctx.getSource().getLevel());
                    s.messageReady = true;
                    s.waitUntil = -1;
                    s.setDirty();
                    ctx.getSource().sendSuccess(() -> Component.literal("ТЕСТ: сообщение Морзе доставлено в радиоприёмник."), true);
                    return 1;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    Story s = Story.of(ctx.getSource().getLevel());
                    s.messageReady = false;
                    s.messageRead = false;
                    s.waitUntil = -1;
                    s.setDirty();
                    ctx.getSource().sendSuccess(() -> Component.literal("Радиосообщение и отметка прочтения сброшены."), true);
                    return 1;
                })))
            .then(Commands.literal("radar").requires(s -> s.hasPermission(2))
                .then(Commands.literal("test_motion").executes(ctx -> {
                    Story s = Story.of(ctx.getSource().getLevel());
                    if (!s.radarCalibrated) {
                        ctx.getSource().sendFailure(Component.literal("Сначала собери и откалибруй радар."));
                        return 0;
                    }
                    s.radarMoving = true;
                    s.setDirty();
                    ctx.getSource().sendSuccess(() -> Component.literal("ТЕСТ: сигнал работающего привода подтверждён."), true);
                    return 1;
                })))
        );
    }

    private void tick(ServerTickEvent.Post event) {
        if (++serverTicks % 20 != 0) return;
        ServerLevel overworld = event.getServer().overworld();
        Story s = Story.of(overworld);
        if (s.radarCalibrated && !s.radarMoving) {
            ResourceLocation id = ResourceLocation.tryParse(s.radarDimension);
            ServerLevel target = id == null ? null :
                    event.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (target != null) {
                BlockPos pos = BlockPos.of(s.radarPos);
                if (target.hasChunkAt(pos) && isBearingMoving(target, pos)) {
                    s.radarMoving = true;
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
                s.setDirty();
            }
        }
    }

    private static boolean isBearingMoving(ServerLevel level, BlockPos pos) {
        if (!BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())
                .toString().equals("create:mechanical_bearing")) return false;
        Object blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return false;
        try {
            Method method = blockEntity.getClass().getMethod("getSpeed");
            Object result = method.invoke(blockEntity);
            return result instanceof Number n && Math.abs(n.floatValue()) > 0.001f;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static final class Blueprint {
        private Blueprint() {}
        public record Part(int x, int y, int z, String id) {}
        private static final List<Part> PARTS = build();
        public static List<Part> parts() { return PARTS; }
        private static List<Part> build() {
            ArrayList<Part> parts = new ArrayList<>();
            parts.add(new Part(0, 0, 0, "create:mechanical_bearing"));
            parts.add(new Part(0, 1, 0, "create:brass_casing"));
            parts.add(new Part(0, 2, 0, "create:brass_casing"));
            for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
                double r = Math.sqrt(x*x + z*z);
                if (r > 6.25) continue;
                parts.add(new Part(x, 3, z, x == 0 && z == 0
                    ? "create:brass_casing" : "minecraft:cut_copper"));
                if (r >= 4.25) parts.add(new Part(x, 4, z, "minecraft:cut_copper"));
                if (r >= 5.50) parts.add(new Part(x, 5, z, "create:brass_casing"));
            }
            parts.add(new Part(0, 4, 0, "minecraft:lightning_rod"));
            return List.copyOf(parts);
        }
        public static List<Part> missing(Level level, BlockPos bearing) {
            ArrayList<Part> missing = new ArrayList<>();
            for (Part part : PARTS) {
                ResourceLocation id = ResourceLocation.parse(part.id());
                if (!BuiltInRegistries.BLOCK.containsKey(id)
                  || !level.getBlockState(bearing.offset(part.x(), part.y(), part.z()))
                      .is(BuiltInRegistries.BLOCK.get(id))) missing.add(part);
            }
            return missing;
        }
    }

    public static final class Story extends SavedData {
        public boolean radarCalibrated;
        public boolean radarMoving;
        public boolean shipReady;
        public boolean messageReady;
        public boolean messageRead;
        public long radarPos;
        public String radarDimension = "minecraft:overworld";
        public long waitUntil = -1;
        private final java.util.Map<String, Integer> radioFrequencies = new java.util.HashMap<>();
        public static Story of(ServerLevel level) {
            return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(Story::new, Story::load),
                        "firstcontact_story");
        }
        private static String key(ServerLevel level, BlockPos pos) {
            return level.dimension().location() + "@" + pos.asLong();
        }
        public int getFrequency(ServerLevel level, BlockPos pos) {
            return radioFrequencies.getOrDefault(key(level,pos), 987);
        }
        public void setFrequency(ServerLevel level, BlockPos pos, int value) {
            radioFrequencies.put(key(level,pos), Math.clamp(value, 880, 1080));
            // 88.0-108.0 MHz: story broadcast uses 98.7 MHz.
            setDirty();
        }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("RadarCalibrated", radarCalibrated);
            tag.putBoolean("RadarMoving", radarMoving);
            tag.putBoolean("ShipReady", shipReady);
            tag.putBoolean("MessageReady", messageReady);
            tag.putBoolean("MessageRead", messageRead);
            tag.putLong("RadarPos", radarPos);
            tag.putString("RadarDimension", radarDimension);
            tag.putLong("WaitUntil", waitUntil);
            CompoundTag radio = new CompoundTag();
            radioFrequencies.forEach(radio::putInt);
            tag.put("RadioFrequencies", radio);
            return tag;
        }
        private static Story load(CompoundTag tag, HolderLookup.Provider registries) {
            Story s = new Story();
            s.radarCalibrated = tag.getBoolean("RadarCalibrated");
            s.radarMoving = tag.getBoolean("RadarMoving");
            s.shipReady = tag.getBoolean("ShipReady");
            s.messageReady = tag.getBoolean("MessageReady");
            s.messageRead = tag.getBoolean("MessageRead");
            s.radarPos = tag.getLong("RadarPos");
            s.radarDimension = tag.getString("RadarDimension");
            if (s.radarDimension.isEmpty()) s.radarDimension = "minecraft:overworld";
            s.waitUntil = tag.contains("WaitUntil") ? tag.getLong("WaitUntil") : -1;
            CompoundTag radio = tag.getCompound("RadioFrequencies");
            for (String key : radio.getAllKeys()) s.radioFrequencies.put(key, radio.getInt(key));
            return s;
        }
    }
}
