package dev.masterki.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Auto-extends the previous products.json without losing manual prices,
 * while removing disallowed entries from old configurations.
 * Individual exclusions belong in masterki/blocked_items.json.
 */
public final class MasterkiConfig {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private MasterkiConfig(){}

    static List<Masterki.Offer> loadOrCreate(List<Masterki.Offer> defaults){
        Path file=FMLPaths.CONFIGDIR.get().resolve("masterki").resolve("products.json");
        Map<String,Masterki.Offer> catalog=new LinkedHashMap<>();
        for(Masterki.Offer offer:defaults)
            if(MasterkiCatalogPolicy.allowed(offer.id()))catalog.put(offer.id(),offer);
        int oldCount=0,blocked=0,merged=0;

        try {
            Files.createDirectories(file.getParent());
            if(Files.isRegularFile(file)){
                // Preserve the user's existing file in case the schema has been edited.
                Path backup=file.resolveSibling("products.pre-1.0.4.json.bak");
                if(!Files.exists(backup))Files.copy(file,backup);
                try(Reader in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){
                    JsonElement raw=JsonParser.parseReader(in);
                    if(raw.isJsonArray()){
                        for(JsonElement row:raw.getAsJsonArray()){
                            if(!row.isJsonObject())continue;
                            oldCount++;
                            try {
                                JsonObject o=row.getAsJsonObject();
                                String id=o.get("id").getAsString();
                                if(!MasterkiCatalogPolicy.allowed(id)){blocked++;continue;}
                                ResourceLocation location=ResourceLocation.tryParse(id);
                                if(location==null || !BuiltInRegistries.ITEM.containsKey(location)){
                                    blocked++;continue;
                                }
                                Masterki.Offer suggested=catalog.get(id);
                                String fallback=suggested==null?"Другие моды":suggested.category();
                                String category=o.has("category")?o.get("category").getAsString():fallback;
                                if(category.isBlank()||category.length()>48)category=fallback;
                                long buy=Math.clamp(o.get("buy").getAsLong(),2,1_000_000_000L);
                                long sell=o.has("sell")?Math.clamp(o.get("sell").getAsLong(),0,buy-1):Math.max(1,buy/3);
                                int cap=o.has("maxStock")?Math.clamp(o.get("maxStock").getAsInt(),1,1_000_000):
                                    suggested==null?128:suggested.cap();
                                int daily=o.has("dailyRestock")?Math.clamp(o.get("dailyRestock").getAsInt(),0,cap):
                                    suggested==null?32:suggested.daily();
                                catalog.put(id,new Masterki.Offer(id,category,buy,sell,cap,daily));
                                merged++;
                            }catch(Exception ignored){}
                        }
                    }
                }catch(Exception ex){
                    // Do not overwrite a malformed file: the backup remains available.
                    System.err.println("[Masterki] Invalid products.json (using generated catalog): "+ex.getMessage());
                    return List.copyOf(catalog.values());
                }
            }
            JsonArray array=new JsonArray();
            for(Masterki.Offer offer:catalog.values()){
                JsonObject row=new JsonObject();
                row.addProperty("id",offer.id());
                row.addProperty("category",offer.category());
                row.addProperty("buy",offer.buy());
                row.addProperty("sell",offer.sell());
                row.addProperty("maxStock",offer.cap());
                row.addProperty("dailyRestock",offer.daily());
                array.add(row);
            }
            Path temporary=file.resolveSibling(file.getFileName().toString()+".tmp");
            Files.writeString(temporary,GSON.toJson(array),StandardCharsets.UTF_8);
            try {
                Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            }catch(java.nio.file.AtomicMoveNotSupportedException unsupported){
                Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("[Masterki] Catalog updated: "+catalog.size()+" eligible items, "+
                merged+" preserved overrides, "+blocked+" disallowed old entries removed.");
        }catch(Exception ex) {
            System.err.println("[Masterki] Could not save generated catalogue: "+ex.getMessage());
        }
        return List.copyOf(catalog.values());
    }
}
