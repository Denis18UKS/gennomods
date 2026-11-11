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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Shared server-side market admission policy.
 *
 * There is no universal Minecraft/mod-loader API proving that an arbitrary
 * modded item can be acquired in survival (loot, exploration, trading, machine
 * outputs, advancements and custom mod logic can grant items without recipes).
 * Reject known developer-only / command-only items and expose a server-side
 * denylist for modpack-specific exclusions.
 */
public final class MasterkiCatalogPolicy {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> VANILLA_FORBIDDEN = Set.of(
        "air", "cave_air", "void_air", "bedrock", "barrier", "light",
        "structure_block", "structure_void", "jigsaw", "command_block",
        "chain_command_block", "repeating_command_block", "command_block_minecart",
        "debug_stick", "knowledge_book", "spawner", "end_portal_frame",
        "reinforced_deepslate", "budding_amethyst", "petrified_oak_slab",
        "frogspawn", "vault", "trial_spawner", "ominous_vault",
        "end_portal", "end_gateway", "nether_portal", "moving_piston",
        "enchanted_book", "filled_map", "written_book", "player_head",
        "tipped_arrow", "potion", "splash_potion", "lingering_potion"
    );
    private static final Set<String> UNSAFE_TOKENS = Set.of(
        "creative","debug","developer","admin","operator","cheat",
        "devonly","placeholder","incomplete","unobtainable","internal",
        "dummy","fake","testonly","wip","prototype","infinite"
    );
    private static volatile Set<String> blockedItems = Set.of();
    private static volatile Set<String> blockedNamespaces = Set.of();

    private MasterkiCatalogPolicy(){}

    public static void reload(){
        Path file=FMLPaths.CONFIGDIR.get().resolve("masterki").resolve("blocked_items.json");
        Set<String> items=new HashSet<>(),namespaces=new HashSet<>();
        try{
            Files.createDirectories(file.getParent());
            if(!Files.exists(file)){
                JsonObject root=new JsonObject();
                root.add("blocked_items",new JsonArray());
                root.add("blocked_namespaces",new JsonArray());
                Files.writeString(file,JSON.toJson(root),StandardCharsets.UTF_8);
            }
            JsonObject root=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
            read(root,"blocked_items",items);
            read(root,"blocked_namespaces",namespaces);
        }catch(Exception ex){
            System.err.println("[Masterki] Cannot read blocked_items.json: "+ex.getMessage());
            // Keep the hardcoded safety rules even when the optional file is invalid.
        }
        blockedItems=Set.copyOf(items);
        blockedNamespaces=Set.copyOf(namespaces);
    }

    private static void read(JsonObject root,String key,Set<String> target){
        if(!root.has(key) || !root.get(key).isJsonArray())return;
        for(JsonElement entry:root.getAsJsonArray(key)){
            if(entry.isJsonPrimitive()){
                String value=entry.getAsString().toLowerCase(Locale.ROOT).trim();
                if(!value.isBlank() && value.length()<160)target.add(value);
            }
        }
    }

    public static boolean allowed(String rawId){
        ResourceLocation id=ResourceLocation.tryParse(rawId);
        return id!=null && allowed(id);
    }
    public static boolean allowed(ResourceLocation id){
        if(id==null || id.toString().length()>128 || !BuiltInRegistries.ITEM.containsKey(id))return false;
        Item item=BuiltInRegistries.ITEM.get(id);
        if(item==Items.AIR || new ItemStack(item).isEmpty())return false;
        String namespace=id.getNamespace().toLowerCase(Locale.ROOT);
        String path=id.getPath().toLowerCase(Locale.ROOT);
        if(blockedNamespaces.contains(namespace) || blockedItems.contains(id.toString()))return false;
        if(namespace.equals("masterki") || namespace.equals("neoforge") ||
           namespace.equals("forge") || namespace.equals("fabric"))return false;
        if(namespace.equals("minecraft") && VANILLA_FORBIDDEN.contains(path))return false;
        if(path.endsWith("_spawn_egg") || path.equals("spawn_egg"))return false;
        if(path.contains("creative") || path.contains("debug"))return false;
        for(String token:path.split("[_./\\-]")){
            if(UNSAFE_TOKENS.contains(token))return false;
        }
        return true;
    }

    /** A conservative, editable market price guess; curated/manual prices take priority. */
    public static int approximateBuy(ResourceLocation id,Item item){
        String path=id.getPath().toLowerCase(Locale.ROOT);
        boolean minecraft=id.getNamespace().equals("minecraft");
        int price;
        if(has(path,"netherite","ancient_debris","dragon","elytra"))price=3200;
        else if(has(path,"precision","mechanism","processor","circuit","controller","engine","motor"))price=730;
        else if(has(path,"drill","mixer","press","harvester","deploy","bearing","crusher","machine","generator","reactor"))price=620;
        else if(has(path,"diamond","emerald","ender","blaze","amethyst","rare","heart","star"))price=225;
        else if(has(path,"gearbox","large_cogwheel","brass_tunnel","brass_funnel","pump","tank","fan"))price=230;
        else if(has(path,"brass","gold","steel","silver","quartz","redstone","alloy"))price=75;
        else if(has(path,"ore","raw_","ingot","sheet","plate","cogwheel","gear","pipe"))price=42;
        else if(has(path,"sword","pickaxe","axe","hammer","hoe","bow","wrench","tool","drillbit"))price=170;
        else if(has(path,"helmet","chestplate","leggings","boots","armor","armour","shield"))price=250;
        else if(has(path,"log","plank","stone","dirt","sand","gravel","stair","slab","fence","wall","brick","glass"))price=18;
        else if(has(path,"seed","crop","flower","wheat","carrot","potato","apple","bread","food","berry"))price=15;
        else if(has(path,"shaft","nugget","dust","stick","torch","button","lever"))price=12;
        else price=minecraft?55:100;
        if(path.endsWith("_block")&&price<=225)price=Math.min(1700,price*5);
        // Keep weak deterministic variation to avoid identical prices for every mod item.
        int adjustment=Math.floorMod(id.toString().hashCode(),17)-8;
        long adjusted=(long)price*(100+adjustment)/100;
        if(new ItemStack(item).getMaxStackSize()==1 && price<250)adjusted=Math.max(35,adjusted*2);
        return (int)Math.clamp(adjusted,2,1_000_000);
    }

    private static boolean has(String value,String... terms){
        for(String term:terms)if(value.contains(term))return true;
        return false;
    }
}
