package dev.zunserz.firstcontact;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Integrates with existing Masterki Economy 1.0.14 without changing its JAR.
 *
 * Existing Masterki policy checks config/masterki/blocked_items.json before
 * constructing the market. We add exactly this mod's radio id to the list,
 * preserve every other entry, reload policy, and clear cached offers.
 * Never modify the economy mod's files or other players' shop items.
 */
public final class MarketCompatibility {
    private MarketCompatibility() {}
    public static final String RADIO_ID="firstcontact:radio_receiver";

    public static void ensureBlocked() {
        if (!ModList.get().isLoaded("masterki")) return;
        Path file=FMLPaths.CONFIGDIR.get().resolve("masterki").resolve("blocked_items.json");
        try {
            Files.createDirectories(file.getParent());
            JsonObject root=Files.exists(file)
                ? JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject()
                : new JsonObject();
            JsonArray blocked=root.has("blocked_items") && root.get("blocked_items").isJsonArray()
                ? root.getAsJsonArray("blocked_items") : new JsonArray();
            boolean hasItem=false;
            for(JsonElement item:blocked)
                if(item.isJsonPrimitive() && RADIO_ID.equalsIgnoreCase(item.getAsString()))
                    hasItem=true;
            if(!hasItem)blocked.add(RADIO_ID);
            root.add("blocked_items",blocked);
            if(!root.has("blocked_namespaces"))root.add("blocked_namespaces",new JsonArray());
            if(!hasItem || !Files.exists(file)) {
                Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(root),
                    StandardCharsets.UTF_8);
            }
            // Masterki 1.0.14 exposes this reload hook. Reflection preserves
            // optional dependency and avoids changes to the existing stable JAR.
            Class<?> policy=Class.forName("dev.masterki.economy.MasterkiCatalogPolicy");
            policy.getMethod("reload").invoke(null);
            try {
                Field offers=Class.forName("dev.masterki.economy.Masterki")
                    .getDeclaredField("offers");
                offers.setAccessible(true);
                offers.set(null,null);
            } catch(ReflectiveOperationException ignored) {
                // On compatible versions first market open calls policy.reload.
            }
        } catch(Exception ex) {
            System.err.println("[First Contact] Could not block radio in Masterki shop: "
                +ex.getMessage()+". Add "+RADIO_ID
                +" manually to config/masterki/blocked_items.json");
        }
    }
}
