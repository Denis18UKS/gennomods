package dev.masterki.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Server-only, conservative accessibility test. Minecraft has no universal API
 * that proves survival obtainability: loot, special machines and mod scripts can
 * supply objects without a recipe. Config overrides are therefore essential.
 * Scan server recipe RESULTS only (including custom registered RecipeTypes),
 * never the client JEI display list. Do not blacklist vanilla loot-only items.
 */
public final class MasterkiObtainability {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static RecipeManager lastManager;
    private static long checkedAt;
    private static boolean ready;
    private static Set<String> recipeOutputs=Set.of();
    private static Set<String> forceObtainable=Set.of();
    private static Set<String> forceUnobtainable=Set.of();
    private MasterkiObtainability(){}

    public static synchronized void invalidate(){
        lastManager=null;
        checkedAt=0L;
    }

    private static Path config(){
        return FMLPaths.CONFIGDIR.get().resolve("masterki").resolve("obtainability.json");
    }

    private static Set<String> readIds(JsonObject json,String key){
        Set<String> ids=new HashSet<>();
        if(json.has(key)&&json.get(key).isJsonArray())
            for(JsonElement element:json.getAsJsonArray(key)){
                if(!element.isJsonPrimitive())continue;
                ResourceLocation id=ResourceLocation.tryParse(element.getAsString());
                if(id!=null)ids.add(id.toString());
            }
        return Set.copyOf(ids);
    }
    private static void loadOverrides(){
        try{
            Path path=config();
            Files.createDirectories(path.getParent());
            if(!Files.exists(path)){
                JsonObject defaults=new JsonObject();
                defaults.add("force_obtainable",new JsonArray());
                defaults.add("force_unobtainable",new JsonArray());
                Files.writeString(path,GSON.toJson(defaults),StandardCharsets.UTF_8);
            }
            JsonObject json=JsonParser.parseString(
                Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
            forceObtainable=readIds(json,"force_obtainable");
            forceUnobtainable=readIds(json,"force_unobtainable");
        }catch(Exception ex){
            System.err.println("[Masterki] Cannot load obtainability.json: "+ex.getMessage());
        }
    }

    private static void scan(ServerLevel level){
        RecipeManager manager=level.getRecipeManager();
        long now=System.currentTimeMillis();
        if(lastManager==manager && now-checkedAt<30_000L)return;
        checkedAt=now;
        lastManager=manager;
        loadOverrides();
        Set<String> outputs=new HashSet<>();
        // Minecraft/NeoForge registers actual, server-loaded recipe outputs for
        // crafting, furnaces, Create process types and custom serializer types.
        for(RecipeHolder<?> holder:manager.getRecipes()){
            try{
                ItemStack result=holder.value().getResultItem(level.registryAccess());
                if(result!=null&&!result.isEmpty())
                    outputs.add(BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
            }catch(RuntimeException ex){
                // Some special recipes calculate results only from an input.
                // Such recipes are not proof that their output item is impossible.
            }
        }
        recipeOutputs=Set.copyOf(outputs);
        ready=!manager.getRecipes().isEmpty();
    }

    /** Known non-crafting drops/worldgen items from other mods.
     * All other modded recipe-less items require an explicit allow override:
     * dynamic decorative/encased variants are commonly unobtainable.
     */
    private static boolean obviousWorldLoot(ResourceLocation id){
        String path=id.getPath().toLowerCase(Locale.ROOT);
        return path.endsWith("_ore")||path.endsWith("_ores")||
            path.startsWith("raw_")||path.endsWith("_raw")||
            path.endsWith("_shard")||path.endsWith("_crystal")||
            path.endsWith("_gem")||path.endsWith("_seed")||
            path.endsWith("_seeds")||path.endsWith("_sapling")||
            path.endsWith("_flower")||path.endsWith("_fish")||
            path.endsWith("_fragment")||path.endsWith("_mob_drop")||
            path.endsWith("_trophy")||path.endsWith("_relic");
    }

    public static synchronized boolean obtainable(ServerLevel level,String item){
        ResourceLocation id=ResourceLocation.tryParse(item);
        if(id==null || !MasterkiCatalogPolicy.allowed(id))return false;
        scan(level);
        if(forceUnobtainable.contains(item))return false;
        if(forceObtainable.contains(item))return true;
        if(!ready)return true; // No recipes loaded yet: do not wipe save data.
        if(id.getNamespace().equals("minecraft"))return true;
        return recipeOutputs.contains(item)||obviousWorldLoot(id);
    }
}
