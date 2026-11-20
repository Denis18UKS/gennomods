package dev.zunserz.firstcontact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * One single authoritative build plan. Both JEI 3D rendering and the
 * /firstcontact radar check command use precisely the same coordinates.
 *
 * Bearing center = (0,0,0). Panels form a connected 13-block copper dish;
 * glue all moving pieces as a contraption before activating Radar Bearing.
 */
public final class RadarBlueprint {
    private RadarBlueprint() {}
    public record Part(int x,int y,int z,String block) {
        public BlockPos offset(BlockPos bearing) {return bearing.offset(x,y,z);}
    }
    private static final List<Part> PARTS=build();

    public static List<Part> parts(){return PARTS;}
    private static List<Part> build(){
        List<Part> list=new ArrayList<>();
        list.add(new Part(0,0,0,RadarIntegration.BEARING));
        list.add(new Part(0,1,0,"create:brass_casing"));
        list.add(new Part(0,2,0,RadarIntegration.RECEIVER));
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){
            double radius=Math.hypot(x,z);
            if(radius>6.25)continue;
            // Connected flat back surface, then rising dish rim.
            // Additional higher layers connect orthogonally to the base plate.
            list.add(new Part(x,3,z,RadarIntegration.PLATE));
            if(radius>=3.7)
                list.add(new Part(x,4,z,RadarIntegration.DISH));
            if(radius>=5.2)
                list.add(new Part(x,5,z,RadarIntegration.DISH));
        }
        return List.copyOf(list);
    }
    public static Map<String,Integer> materials(){
        Map<String,Integer> totals=new LinkedHashMap<>();
        for(Part p:PARTS)totals.merge(p.block(),1,Integer::sum);
        return totals;
    }
    public static List<Part> missing(Level level,BlockPos bearing){
        List<Part> missing=new ArrayList<>();
        for(Part part:PARTS){
            BlockPos pos=part.offset(bearing);
            ResourceLocation key=ResourceLocation.parse(part.block());
            if(!BuiltInRegistries.BLOCK.containsKey(key)
                ||!level.getBlockState(pos).is(BuiltInRegistries.BLOCK.get(key)))
                    missing.add(part);
        }
        return missing;
    }
    public static int maxHeight(){return 5;}
}
