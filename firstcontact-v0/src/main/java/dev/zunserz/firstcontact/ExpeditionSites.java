package dev.zunserz.firstcontact;

import net.minecraft.core.BlockPos;

/**
 * Deterministic radio expedition locations. Coordinates depend on world seed
 * and never require teleporting the player or touching already-loaded chunks.
 *
 * Actual workshops are generated ONLY during NEW OVERWORLD CHUNK GENERATION
 * by ForgottenWorkshopFeature. Old chunks, bases and player constructions
 * are never post-processed or replaced.
 */
public final class ExpeditionSites {
    private ExpeditionSites() {}
    public static final int SITE_COUNT=8;
    public static final int FIRST_SIGNAL_ID=18;

    /** 0=empty/decoy, 1=weathered, 2=intact, 3=collapsed. */
    public record Site(int index, int x, int z, int outcome) {
        public BlockPos center() { return new BlockPos(x, 0, z); }
        public boolean hasStructure() { return outcome != 0; }
        public int chunkX() { return x >> 4; }
        public int chunkZ() { return z >> 4; }
    }

    public static Site site(long seed,int index) {
        if(index < 0 || index >= SITE_COUNT) throw new IllegalArgumentException("site " + index);
        long roll=mix(seed + index*0x9e3779b97f4a7c15L + 0x1cf2509eL);
        // Eight distinct sectors with jitter, well outside ordinary spawn regions.
        double angle=index*Math.PI*2/SITE_COUNT
            + ((Long.remainderUnsigned(roll,1000)/1000d)-.5)*.34;
        int distance=4400+(int)Long.remainderUnsigned(mix(roll),6500);
        int cx=(int)Math.round(Math.cos(angle)*distance/16d);
        int cz=(int)Math.round(Math.sin(angle)*distance/16d);
        // 8-block offsets are safely inside a chunk, so a 7x7 site cannot
        // modify adjacent chunks even during world generation.
        int x=(cx<<4)+8, z=(cz<<4)+8;
        int outcomeRoll=(int)Long.remainderUnsigned(mix(roll^0x4137eFL),100);
        int outcome=outcomeRoll<29?0:outcomeRoll<66?1:outcomeRoll<90?2:3;
        return new Site(index,x,z,outcome);
    }

    public static Site atChunk(long seed,int cx,int cz) {
        for(int i=0;i<SITE_COUNT;i++){
            Site site=site(seed,i);
            if(site.chunkX()==cx && site.chunkZ()==cz)return site;
        }
        return null;
    }

    public static int indexForStation(int stationId) {
        int index=stationId-FIRST_SIGNAL_ID;
        return index >= 0 && index < SITE_COUNT?index:-1;
    }

    /** Intentional plaintext only on server; radios transmit encoded CW. */
    public static String encodedClue(long seed,int stationId) {
        int index=indexForStation(stationId);
        if(index<0)return "";
        Site site=site(seed,index);
        return "X "+(site.x()<0?"MINUS ":"")+Math.abs(site.x())
            +" Z "+(site.z()<0?"MINUS ":"")+Math.abs(site.z());
    }

    private static long mix(long n) {
        n^=n>>>30;n*=0xbf58476d1ce4e5b9L;
        n^=n>>>27;n*=0x94d049bb133111ebL;
        return n^(n>>>31);
    }
}
