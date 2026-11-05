package dev.timefracture.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import dev.timefracture.TimeFractureMod;
import dev.timefracture.ConvergenceService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.UUID;

/**
 * Required: one Create shaft below the core (axis Y), a brass casing in each
 * cardinal direction, and an amethyst block above.
 *
 * Insert 1 Nether Star by right click to arm.
 * Then maintain ACTUAL maximum Create rotational speed for 600 ticks (30 s).
 * If speed drops after charging began, there is an 8% chance of a contained
 * in-world explosion (no file deletion / no ban / no destructive rollback).
 */
public class ChronalCoreBlockEntity extends KineticBlockEntity {
    private static final int REQUIRED_TICKS = 600;
    private static final int HAZARD_CHANCE = 12; // 1 in 12, only on speed loss after charge
    private int progress;
    private boolean armed;
    private UUID owner;
    private boolean riskCheckArmed;

    public ChronalCoreBlockEntity(BlockPos pos, BlockState state) {
        super(TimeFractureMod.CHRONAL_CORE_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> list) { }

    public boolean arm(Player player) {
        if (!(player instanceof ServerPlayer sp) || level == null || level.isClientSide) return false;
        if (armed) {
            sp.sendSystemMessage(Component.literal("§eХроноконденсатор уже заряжается. " + status()));
            return false;
        }
        if (TimeFractureMod.P == null || TimeFractureMod.P.count() < 2) {
            sp.sendSystemMessage(Component.literal("§cДля объединения нужно как минимум две сохранённые временные ветви."));
            return false;
        }
        if (!structureValid()) {
            sp.sendSystemMessage(Component.literal("§cМультиструктура не собрана: 4 латунных корпуса Create по сторонам, аметист над ядром и вертикальный Create-вал под ним."));
            return false;
        }
        if (TimeFractureMod.L.frozen) {
            sp.sendSystemMessage(Component.literal("§cВремя заморожено. Сначала восстанови баланс."));
            return false;
        }
        owner = sp.getUUID();
        progress = 0;
        riskCheckArmed = false;
        armed = true;
        setChanged();
        sp.sendSystemMessage(Component.literal("§dХроноконденсатор заряжен звездой Незера. "+
            "Подай максимальные "+maxRpm()+" RPM от Create на 30 секунд. При срыве возможна авария!"));
        return true;
    }

    public String status() {
        float speed = Math.abs(getSpeed());
        return "§d[Хроноконденсатор] §fCreate " + Math.round(speed) + "/" + maxRpm()
            + " RPM · " + progress + "/" + REQUIRED_TICKS
            + " тиков · " + (armed ? "§aзарядка" : "§7ожидание")
            + (structureValid() ? " · §aконструкция готова" : " · §cнеполная структура");
    }

    public static int maxRpm() {
        // Uses REAL Create server config rather than hard-coded 256 in worlds
        // where the server's maximum rotation speed was changed.
        return AllConfigs.server().kinetics.maxRotationSpeed.get();
    }

    private boolean isBlock(BlockPos pos, String key) {
        if(level == null) return false;
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())
            .equals(ResourceLocation.parse(key));
    }

    public boolean structureValid() {
        if(level == null)return false;
        if(!level.getBlockState(worldPosition.above()).is(Blocks.AMETHYST_BLOCK))return false;
        if(!isBlock(worldPosition.below(),"create:shaft"))return false;
        BlockState shaft=level.getBlockState(worldPosition.below());
        if(shaft.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)
            && shaft.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)!=Direction.Axis.Y)
            return false;
        for(Direction direction:Direction.Plane.HORIZONTAL) {
            if(!isBlock(worldPosition.relative(direction),"create:brass_casing"))return false;
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel server) || !armed) return;
        if (owner == null || !structureValid()) {
            if(++progress > 10)cancel("Мультиструктура разрушена.");
            return;
        }
        // Do not hold the machine hostage when the original player disconnected.
        ServerPlayer player=server.getServer().getPlayerList().getPlayer(owner);
        if(player==null) return;
        float current=Math.abs(getSpeed());
        int required=maxRpm();
        boolean fast = current >= required - 0.01f && !isOverStressed();
        if(fast) {
            progress++;
            riskCheckArmed=true;
            if(progress % 100 == 0)
                player.sendSystemMessage(Component.literal("§bХронозаряд: "+progress+"/"+REQUIRED_TICKS+
                    " · "+Math.round(current)+" RPM"));
            if(progress >= REQUIRED_TICKS) {
                armed=false;
                progress=0;
                setChanged();
                ConvergenceService.start(server,worldPosition,owner);
            }
        }else {
            // A single hazard roll per interruption, not once per tick.
            if(riskCheckArmed) {
                riskCheckArmed=false;
                if(server.random.nextInt(HAZARD_CHANCE)==0) {
                    cancel("Перегрузка! Произошёл аварийный энергетический взрыв.");
                    server.explode(null,worldPosition.getX()+.5,worldPosition.getY()+.5,
                        worldPosition.getZ()+.5,1.5f,Level.ExplosionInteraction.NONE);
                } else {
                    progress=0;
                    player.sendSystemMessage(Component.literal("§eСкорость упала ниже "+required+
                        " RPM. Заряд потерян; при следующем сбое возможен взрыв."));
                }
            }
        }
        if(progress%20==0)setChanged();
    }

    private void cancel(String message) {
        armed=false;
        progress=0;
        owner=null;
        riskCheckArmed=false;
        setChanged();
        if(level instanceof ServerLevel l)
            for(ServerPlayer p:l.getPlayers(x->x.blockPosition().closerThan(worldPosition,20)))
                p.sendSystemMessage(Component.literal("§c[Хроноконденсатор] "+message));
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putInt("Charge",progress);
        tag.putBoolean("Armed",armed);
        tag.putBoolean("Risk",riskCheckArmed);
        if(owner!=null)tag.putUUID("Owner",owner);
        super.write(tag,registries,clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag,registries,clientPacket);
        progress=tag.getInt("Charge");
        armed=tag.getBoolean("Armed");
        riskCheckArmed=tag.getBoolean("Risk");
        owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;
    }
}
