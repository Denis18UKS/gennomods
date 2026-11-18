package dev.masterki.economy.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import dev.masterki.economy.MasterkiNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Live progress uses the player's inventory, but only the server can complete/pay
 * contracts. A persistent local setting chooses compact or expanded rendering.
 */
@EventBusSubscriber(modid="masterki",value=Dist.CLIENT)
public final class ClientContractHud {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static List<MasterkiNetwork.ContractStatus> contracts=List.of();
    private static final Set<String> announcedReady=new HashSet<>();
    private static boolean compact=true,enabled=true;
    static { loadSettings(); }
    private ClientContractHud(){}

    private static Path config(){
        return FMLPaths.CONFIGDIR.get().resolve("masterki").resolve("client_hud.json");
    }
    private static void loadSettings(){
        try{
            Path path=config();
            if(!Files.isRegularFile(path))return;
            JsonObject json=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
            if(json.has("contract_hud_enabled"))enabled=json.get("contract_hud_enabled").getAsBoolean();
            if(json.has("contract_hud_compact"))compact=json.get("contract_hud_compact").getAsBoolean();
        }catch(Exception e){
            System.err.println("[Masterki] Client HUD settings not loaded: "+e.getMessage());
        }
    }
    private static void saveSettings(){
        try{
            Path path=config();
            Files.createDirectories(path.getParent());
            JsonObject json=new JsonObject();
            json.addProperty("contract_hud_enabled",enabled);
            json.addProperty("contract_hud_compact",compact);
            Files.writeString(path,GSON.toJson(json),StandardCharsets.UTF_8);
        }catch(Exception e){
            System.err.println("[Masterki] Cannot save HUD settings: "+e.getMessage());
        }
    }
    public static boolean enabled(){return enabled;}
    public static boolean compact(){return compact;}
    public static void toggleEnabled(){enabled=!enabled;saveSettings();}
    public static void toggleMode(){compact=!compact;saveSettings();}

    public static void receive(List<MasterkiNetwork.ContractStatus> all){
        contracts=all.stream().filter(c->"accepted".equals(c.status())).toList();
        Set<String> live=new HashSet<>();
        for(var c:contracts)live.add(c.id());
        announcedReady.retainAll(live);
    }
    private static int inventoryCount(Minecraft mc,String id){
        ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null||!BuiltInRegistries.ITEM.containsKey(key)||mc.player==null)return 0;
        Item item=BuiltInRegistries.ITEM.get(key);
        if(item==Items.AIR)return 0;
        ItemStack sample=new ItemStack(item);
        int n=0;
        for(int i=0;i<36;i++){
            ItemStack stack=mc.player.getInventory().getItem(i);
            if(ItemStack.isSameItemSameComponents(stack,sample))n+=stack.getCount();
        }
        return n;
    }
    private static String name(String item){
        ResourceLocation id=ResourceLocation.tryParse(item);
        if(id==null||!BuiltInRegistries.ITEM.containsKey(id))return item;
        return new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
    }
    private static String fit(Minecraft mc,String text,int maxWidth){
        if(mc.font.width(text)<=maxWidth)return text;
        while(!text.isEmpty()&&mc.font.width(text+"…")>maxWidth)
            text=text.substring(0,text.length()-1);
        return text+"…";
    }
    private static String reward(long value){
        return String.format(Locale.ROOT,"%,d",value).replace(',',' ');
    }
    private static String remaining(long deadline){
        long secs=Math.max(0,(deadline-System.currentTimeMillis())/1000);
        if(secs>=86400)return String.format(Locale.ROOT,"%dд %02dч",secs/86400,(secs%86400)/3600);
        if(secs>=3600)return String.format(Locale.ROOT,"%dч %02dм",secs/3600,(secs%3600)/60);
        return String.format(Locale.ROOT,"%dм %02dс",secs/60,secs%60);
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event){
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen!=null||mc.options.hideGui||mc.player==null||contracts.isEmpty()
            ||!enabled||!ClientLayout.visible("hud_contracts"))return;

        // Notify for ALL accepted contracts, not just the visible first rows.
        for(var c:contracts){
            int inventory=inventoryCount(mc,c.item());
            boolean ready=inventory+c.deposited()>=c.amount();
            if(ready && announcedReady.add(c.id()))
                mc.player.displayClientMessage(Component.literal(
                    "Контракт готов к сдаче: "+name(c.item())),true);
            if(!ready)announcedReady.remove(c.id());
        }

        GuiGraphics g=event.getGuiGraphics();
        int x=10,y=12,w=compact?172:220;
        int rows=Math.min(compact?2:3,contracts.size()),stride=compact?38:44;
        int h=23+rows*stride+(contracts.size()>rows?13:0);
        ClientLayout.start(g,"hud_contracts",x,y);
        g.fill(x,y,x+w,y+h,0xBB211710);
        g.fill(x,y,x+w,y+2,0xFFD7A04B);
        g.drawString(mc.font,"АКТИВНЫЕ КОНТРАКТЫ",x+8,y+7,0xFFFFCE75,false);
        for(int i=0;i<rows;i++){
            var c=contracts.get(i);
            int cy=y+22+i*stride;
            int inventory=inventoryCount(mc,c.item());
            int count=Math.min(c.amount(),inventory+c.deposited());
            boolean ready=count>=c.amount();
            String title=fit(mc,name(c.item()),w-16);
            g.drawString(mc.font,title,x+8,cy,ready?0xFFA6E69A:0xFFF6D6A3,false);
            g.fill(x+8,cy+12,x+w-8,cy+17,0xAA493526);
            int inner=w-16;
            int filled=(int)Math.min(inner,(long)inner*count/Math.max(1,c.amount()));
            g.fill(x+8,cy+12,x+8+filled,cy+17,ready?0xFF84D46A:0xFFD8A04F);
            String left=(c.partialAllowed()&&c.deposited()>0
                ?c.deposited()+"+"+inventory:Integer.toString(count))+
                "/"+c.amount()+"  +"+reward(c.reward())+" М";
            String right=remaining(c.deadline());
            int rightWidth=mc.font.width(right);
            g.drawString(mc.font,fit(mc,left,w-25-rightWidth),x+8,cy+23,
                ready?0xFFA6E69A:0xFFE6BC70,false);
            g.drawString(mc.font,right,x+w-8-rightWidth,cy+23,
                c.deadline()-System.currentTimeMillis()<7200000L?0xFFF68A7A:0xFFC4C6AC,false);
        }
        if(contracts.size()>rows)
            g.drawString(mc.font,"+"+(contracts.size()-rows)+" контрактов",
                x+8,y+22+rows*stride,0xFFC9AC86,false);
        ClientLayout.end(g);
    }
}
