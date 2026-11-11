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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;

/** Human-editable server catalogue; generated on first start. */
public final class MasterkiConfig {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private MasterkiConfig(){}

    static List<Masterki.Offer> loadOrCreate(List<Masterki.Offer> defaults){
        Path file=FMLPaths.CONFIGDIR.get().resolve("masterki").resolve("products.json");
        try {
            Files.createDirectories(file.getParent());
            if(!Files.exists(file)) {
                JsonArray array=new JsonArray();
                for(Masterki.Offer p:defaults){
                    JsonObject row=new JsonObject();
                    row.addProperty("id",p.id());
                    row.addProperty("category",p.category());
                    row.addProperty("buy",p.buy());
                    row.addProperty("sell",p.sell());
                    row.addProperty("maxStock",p.cap());
                    row.addProperty("dailyRestock",p.daily());
                    array.add(row);
                }
                Files.writeString(file,GSON.toJson(array),StandardCharsets.UTF_8);
                return defaults;
            }
            try(Reader in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){
                JsonElement raw=JsonParser.parseReader(in);
                if(!raw.isJsonArray())return defaults;
                List<Masterki.Offer> parsed=new ArrayList<>();
                for(JsonElement row:raw.getAsJsonArray()){
                    if(!row.isJsonObject())continue;
                    try {
                        JsonObject o=row.getAsJsonObject();
                        String id=o.get("id").getAsString();
                        ResourceLocation location=ResourceLocation.tryParse(id);
                        if(location==null||!BuiltInRegistries.ITEM.containsKey(location)||
                            BuiltInRegistries.ITEM.get(location)==Items.AIR)continue;
                        String category=o.has("category")?o.get("category").getAsString():"Ресурсы";
                        long buy=Math.max(1,o.get("buy").getAsLong());
                        long sell=Math.max(0,Math.min(buy-1,o.get("sell").getAsLong()));
                        int cap=Math.max(1,Math.min(1000000,o.get("maxStock").getAsInt()));
                        int daily=Math.max(0,Math.min(cap,o.get("dailyRestock").getAsInt()));
                        parsed.add(new Masterki.Offer(id,category,buy,sell,cap,daily));
                    } catch(Exception ignored) { }
                }
                return parsed.isEmpty()?defaults:List.copyOf(parsed);
            }
        } catch(Exception e) {
            System.err.println("[Masterki] Could not read catalogue: "+e.getMessage());
            return defaults;
        }
    }
}
