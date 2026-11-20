package dev.zunserz.firstcontact;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * One of eight deterministic expedition markers may become a tiny Create ruin.
 * This feature executes ONLY in the world-generation pipeline. It never runs on
 * chunk load, server tick, player proximity, commands or existing chunks.
 *
 * Safety: require natural untouched terrain in an entire 7x7 footprint;
 * ensure every write stays 3 blocks away from ALL chunk boundaries; never
 * overwrite non-natural blocks, tile entities, water or nearby bases.
 */
public final class ForgottenWorkshopFeature extends Feature<NoneFeatureConfiguration> {
    public ForgottenWorkshopFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level=ctx.level();
        if (!level.getLevel().dimension().equals(Level.OVERWORLD)) return false;
        int chunkX=ctx.origin().getX() >> 4;
        int chunkZ=ctx.origin().getZ() >> 4;
        ExpeditionSites.Site site=ExpeditionSites.atChunk(level.getLevel().getSeed(),chunkX,chunkZ);
        if(site==null||!site.hasStructure())return false;
        int y=level.getHeight(Heightmap.Types.WORLD_SURFACE_WG,site.x(),site.z());
        if(y<66||y>225)return false;

        // Validate all 49 ground blocks BEFORE placing a single block.
        for(int dx=-3;dx<=3;dx++) for(int dz=-3;dz<=3;dz++) {
            int x=site.x()+dx,z=site.z()+dz;
            int h=level.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z);
            if(Math.abs(h-y)>1 || h<65 || h>225)return false;
            BlockPos ground=new BlockPos(x,h-1,z);
            BlockState block=level.getBlockState(ground);
            if(!isNatural(block))return false;
            // Keep trees, village structures and other generated features intact.
            for(int rise=0;rise<=5;rise++) {
                BlockState above=level.getBlockState(new BlockPos(x,h+rise,z));
                if(!above.isAir()
                    && !above.is(Blocks.SHORT_GRASS) && !above.is(Blocks.TALL_GRASS))
                    return false;
            }
        }

        int kind=site.outcome();
        BlockState floor=block("create:andesite_casing");
        BlockState brass=block("create:brass_casing");
        BlockState shaft=block("create:shaft");
        BlockState gearbox=block("create:gearbox");
        BlockState cog=block("create:large_cogwheel");
        BlockState press=block("create:mechanical_press");
        BlockState depot=block("create:depot");
        // These are real Create components, placed unpowered as an ancient ruin.
        for(int dx=-3;dx<=3;dx++) for(int dz=-3;dz<=3;dz++) {
            if(kind==3 && Math.floorMod(dx*17+dz*13+site.index()*7,4)==0)continue;
            if(kind==1 && Math.floorMod(dx*11+dz*7+site.index(),9)==0)continue;
            place(level,new BlockPos(site.x()+dx,y-1,site.z()+dz),floor);
        }
        // Uneven fragments of a former rotating machine.
        for(int dx=-3;dx<=3;dx++) {
            if(kind==3 && dx%2==0)continue;
            place(level,new BlockPos(site.x()+dx,y,site.z()-3),brass);
        }
        for(int dz=-2;dz<=2;dz++) {
            if(kind!=2 && dz%2==0)continue;
            place(level,new BlockPos(site.x()-3,y,site.z()+dz),brass);
        }
        if(kind==2) {
            place(level,new BlockPos(site.x(),y,site.z()),gearbox);
            place(level,new BlockPos(site.x(),y+1,site.z()),shaft);
            place(level,new BlockPos(site.x(),y+2,site.z()),cog);
            place(level,new BlockPos(site.x()+1,y,site.z()+1),depot);
            place(level,new BlockPos(site.x()+1,y+2,site.z()+1),press);
            // Broken masts standing on opposite corners.
            for(int rise=0;rise<3;rise++){
                place(level,new BlockPos(site.x()-3,y+rise,site.z()-3),brass);
                place(level,new BlockPos(site.x()+3,y+rise,site.z()+3),brass);
            }
        } else if(kind==1) {
            place(level,new BlockPos(site.x(),y,site.z()),cog);
            place(level,new BlockPos(site.x()+1,y,site.z()),shaft);
            place(level,new BlockPos(site.x()-1,y,site.z()),depot);
        } else {
            place(level,new BlockPos(site.x()-1,y,site.z()),cog);
            place(level,new BlockPos(site.x()+2,y,site.z()-1),shaft);
        }

        // Small stash. Never placed at sites that are intentionally empty.
        if(kind==2 || kind==1) {
            BlockPos chestPos=new BlockPos(site.x()+2,y,site.z()+2);
            place(level,chestPos,Blocks.CHEST.defaultBlockState());
            if(level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                chest.setItem(0,new ItemStack(item("create:andesite_alloy"),3+site.index()%5));
                chest.setItem(3,new ItemStack(item("create:electron_tube"),1));
                if(kind==2)chest.setItem(5,new ItemStack(item("create:precision_mechanism"),1));
            }
        }
        return true;
    }

    private static void place(WorldGenLevel level,BlockPos pos,BlockState state) {
        level.setBlock(pos,state,2);
    }
    private static BlockState block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState();
    }
    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }
    private static boolean isNatural(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
            || state.is(Blocks.STONE) || state.is(Blocks.ANDESITE)
            || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE)
            || state.is(Blocks.DEEPSLATE) || state.is(Blocks.SAND)
            || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
            || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
            || state.is(Blocks.MYCELIUM) || state.is(Blocks.MOSS_BLOCK)
            || state.is(Blocks.TERRACOTTA);
    }
}
