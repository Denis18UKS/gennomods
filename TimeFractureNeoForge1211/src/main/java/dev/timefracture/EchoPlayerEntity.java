package dev.timefracture;

import com.mojang.authlib.properties.Property;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import java.util.UUID;

/**
 * A genuinely separate, player-shaped NPC entity, not a Villager and not
 * registered as a real online ServerPlayer. Its own UUID avoids duplicates.
 *
 * Skin texture PROPERTY is frozen when this echo is CREATED, then stored
 * in entity NBT and synced to clients. A later original-player skin change
 * cannot alter the already created version.
 *
 * This is basic vanilla goal-based survival AI (navigation, combat, defense).
 * It does NOT yet mine, construct Create machines or plan independent lives.
 */
public final class EchoPlayerEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> SUBJECT =
        SynchedEntityData.defineId(EchoPlayerEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> NAME =
        SynchedEntityData.defineId(EchoPlayerEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> LINE =
        SynchedEntityData.defineId(EchoPlayerEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_VALUE =
        SynchedEntityData.defineId(EchoPlayerEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_SIGNATURE =
        SynchedEntityData.defineId(EchoPlayerEntity.class,EntityDataSerializers.STRING);
    private int lifetimeExperience=0;
    private boolean paradoxResolved=false;

    public EchoPlayerEntity(EntityType<? extends EchoPlayerEntity> type,Level world){
        super(type,world);
        setPersistenceRequired();
    }
    public static AttributeSupplier.Builder attributes(){
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH,20)
            .add(Attributes.MOVEMENT_SPEED,.28)
            .add(Attributes.ATTACK_DAMAGE,4)
            .add(Attributes.FOLLOW_RANGE,24);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){
        super.defineSynchedData(builder);
        builder.define(SUBJECT,"");
        builder.define(NAME,"");
        builder.define(LINE,"");
        builder.define(SKIN_VALUE,"");
        builder.define(SKIN_SIGNATURE,"");
    }
    public String subjectId(){return entityData.get(SUBJECT);}
    public String subjectName(){return entityData.get(NAME);}
    public String timelineId(){return entityData.get(LINE);}
    public String frozenSkinValue(){return entityData.get(SKIN_VALUE);}
    public String frozenSkinSignature(){return entityData.get(SKIN_SIGNATURE);}
    public int experience(){return lifetimeExperience;}

    public void initializeFrom(ServerPlayer original,String timeline){
        entityData.set(SUBJECT,original.getUUID().toString());
        entityData.set(NAME,original.getGameProfile().getName());
        entityData.set(LINE,timeline);
        // One-time immutable property snapshot. The skin manager can load it
        // asynchronously. We never look up the original's live skin again.
        for(Property property:original.getGameProfile().getProperties().get("textures")){
            entityData.set(SKIN_VALUE,property.value());
            entityData.set(SKIN_SIGNATURE,property.signature()==null?"":property.signature());
            break;
        }
        setCustomName(Component.literal("§d"+subjectName()+" §8["+timeline+"]"));
        setCustomNameVisible(true);
        setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
        setPersistenceRequired();
    }

    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(1,new MeleeAttackGoal(this,1.1,false));
        goalSelector.addGoal(4,new WaterAvoidingRandomStrollGoal(this,.75));
        goalSelector.addGoal(5,new LookAtPlayerGoal(this,Player.class,10));
        goalSelector.addGoal(6,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new HurtByTargetGoal(this));
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Monster.class,false));
    }

    @Override public boolean doHurtTarget(net.minecraft.world.entity.Entity target){
        boolean hit=super.doHurtTarget(target);
        if(hit){lifetimeExperience=Math.min(1_000_000,lifetimeExperience+1);}
        return hit;
    }

    @Override public void aiStep(){
        super.aiStep();
        if(level().isClientSide || paradoxResolved || tickCount%40!=0)return;
        if(!(level() instanceof ServerLevel level))return;
        if(subjectId().isBlank())return;
        UUID source;
        try{source=UUID.fromString(subjectId());}
        catch(IllegalArgumentException ex){return;}
        ServerPlayer original=level.getServer().getPlayerList().getPlayer(source);
        // Only a contact WITH THE SAME PERSON counts. Other players may
        // safely meet an echo without causing a "cosmic" freeze.
        if(original==null || original.level()!=level || distanceToSqr(original)>2.4)return;
        paradoxResolved=true;
        original.sendSystemMessage(Component.literal(
            "§4§lВРЕМЕННОЙ ПАРАДОКС! §cДве версии одной личности столкнулись."));
        TimeFractureMod.L.frozen=true;
        TimeFractureMod.L.save();
        if(TimeFractureMod.TRAVEL!=null)
            original.sendSystemMessage(Component.literal(TimeFractureMod.TRAVEL.leave(original)));
        original.sendSystemMessage(Component.literal(
            "§eВетвления заморожены. DEV: /timefracture balance restore all"));
    }

    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);
        tag.putString("TemporalSubject",subjectId());
        tag.putString("TemporalName",subjectName());
        tag.putString("TemporalLine",timelineId());
        tag.putString("TemporalSkinValue",frozenSkinValue());
        tag.putString("TemporalSkinSignature",frozenSkinSignature());
        tag.putInt("TemporalExperience",lifetimeExperience);
        tag.putBoolean("ParadoxResolved",paradoxResolved);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);
        entityData.set(SUBJECT,tag.getString("TemporalSubject"));
        entityData.set(NAME,tag.getString("TemporalName"));
        entityData.set(LINE,tag.getString("TemporalLine"));
        entityData.set(SKIN_VALUE,tag.getString("TemporalSkinValue"));
        entityData.set(SKIN_SIGNATURE,tag.getString("TemporalSkinSignature"));
        lifetimeExperience=tag.getInt("TemporalExperience");
        paradoxResolved=tag.getBoolean("ParadoxResolved");
    }
}
