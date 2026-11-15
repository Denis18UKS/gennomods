package dev.masterki.economy;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Optional Apotheosis 1.21 World Tier integration.
 * Reads the actual per-player apotheosis:world_tier attachment.
 * No Apotheosis classes are linked; the mod also runs without Apotheosis.
 */
public final class MasterkiDifficulty {
    private MasterkiDifficulty(){}
    private static final ResourceLocation APOTHEOSIS_TIER=
        ResourceLocation.fromNamespaceAndPath("apotheosis","world_tier");

    public static String tier(ServerPlayer player) {
        if(!ModList.get().isLoaded("apotheosis"))return "vanilla";
        try{
            AttachmentType<?> type=NeoForgeRegistries.ATTACHMENT_TYPES.get(APOTHEOSIS_TIER);
            if(type==null)return "vanilla";
            Object tier=player.getData(type);
            if(tier instanceof Enum<?> enumeration)
                return enumeration.name().toLowerCase(Locale.ROOT);
            return "vanilla";
        }catch(RuntimeException exception){
            // Missing, incompatible or disabled optional feature => neutral payouts.
            return "vanilla";
        }
    }

    public static int percent(ServerPlayer player) {
        return switch(tier(player)){
            case "frontier" -> 115;
            case "ascent" -> 130;
            case "summit" -> 150;
            case "pinnacle" -> 175;
            default -> 100; // Haven or no Apotheosis
        };
    }
    public static long adjusted(long base,ServerPlayer player) {
        if(base<=0)return 0;
        int pct=percent(player);
        if(base>Long.MAX_VALUE/pct)return Long.MAX_VALUE;
        return Math.max(1,(base*pct+50)/100L);
    }
}
