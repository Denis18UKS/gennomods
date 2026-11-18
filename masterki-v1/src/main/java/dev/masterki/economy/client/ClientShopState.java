package dev.masterki.economy.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.neoforged.fml.loading.FMLPaths;

/** Client-only shop navigation, persisted per Minecraft player UUID. */
public final class ClientShopState {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static UUID owner;
    private static final Map<String,Integer> pages=new HashMap<>();
    private static final Map<String,String> categories=new HashMap<>();
    private static final Map<String,String> queries=new HashMap<>();
    private static String lastTab="Магазин";
    private ClientShopState(){}

    private static Path path(){
        return FMLPaths.CONFIGDIR.get().resolve("masterki")
            .resolve("shop_navigation_"+owner+".json");
    }
    private static void readMapStrings(JsonObject object,String key,Map<String,String> target){
        if(!object.has(key)||!object.get(key).isJsonObject())return;
        for(var entry:object.getAsJsonObject(key).entrySet())
            if(entry.getValue().isJsonPrimitive())
                target.put(entry.getKey(),entry.getValue().getAsString());
    }
    public static void load(UUID id){
        if(id==null)return;
        if(id.equals(owner))return;
        owner=id;pages.clear();categories.clear();queries.clear();lastTab="Магазин";
        try{
            if(!Files.isRegularFile(path()))return;
            JsonObject json=JsonParser.parseString(
                Files.readString(path(),StandardCharsets.UTF_8)).getAsJsonObject();
            if(json.has("lastTab"))lastTab=json.get("lastTab").getAsString();
            if(json.has("pages")&&json.get("pages").isJsonObject())
                for(Map.Entry<String,JsonElement> entry:json.getAsJsonObject("pages").entrySet())
                    if(entry.getValue().isJsonPrimitive())
                        pages.put(entry.getKey(),Math.clamp(entry.getValue().getAsInt(),0,100_000));
            readMapStrings(json,"categories",categories);
            readMapStrings(json,"queries",queries);
        }catch(Exception ex){
            System.err.println("[Masterki] Failed to read shop navigation: "+ex.getMessage());
        }
    }
    public static String lastTab(){return lastTab;}
    public static int page(String tab){return Math.max(0,pages.getOrDefault(tab,0));}
    public static String category(String tab){
        return categories.getOrDefault(tab,"Ресурсы");
    }
    public static String query(String tab){return queries.getOrDefault(tab,"");}
    public static void setPage(String tab,int page){
        if(owner==null)return;
        int next=Math.max(0,page);
        if(pages.getOrDefault(tab,0)==next)return;
        pages.put(tab,next);save();
    }
    public static void setTab(String tab){
        if(owner==null||lastTab.equals(tab))return;
        lastTab=tab;save();
    }
    public static void setCategory(String tab,String category){
        if(owner==null||category.equals(categories.getOrDefault(tab,"Ресурсы")))return;
        categories.put(tab,category);save();
    }
    public static void setQuery(String tab,String query){
        if(owner==null||query.equals(queries.getOrDefault(tab,"")))return;
        queries.put(tab,query);save();
    }
    private static void save(){
        if(owner==null)return;
        try{
            Path target=path();
            Files.createDirectories(target.getParent());
            JsonObject json=new JsonObject();
            json.addProperty("lastTab",lastTab);
            JsonObject p=new JsonObject(),c=new JsonObject(),q=new JsonObject();
            pages.forEach(p::addProperty);
            categories.forEach(c::addProperty);
            queries.forEach(q::addProperty);
            json.add("pages",p);json.add("categories",c);json.add("queries",q);
            Files.writeString(target,GSON.toJson(json),StandardCharsets.UTF_8);
        }catch(Exception ex){
            System.err.println("[Masterki] Failed to save shop navigation: "+ex.getMessage());
        }
    }
}
