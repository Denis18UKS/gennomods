package dev.masterki.economy.client;

import java.util.HashSet;
import java.util.List;
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

/** Client-side, every-frame progress from inventory; server still verifies payouts. */
@EventBusSubscriber(modid="masterki",value=Dist.CLIENT)
public final class ClientContractHud {
    private static List<MasterkiNetwork.ContractStatus> contracts=List.of();
    private static final Set<String> announcedReady=new HashSet<>();
    private ClientContractHud(){}

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
        String name=new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
        return name.length()>25?name.substring(0,24)+"…":name;
    }
    private static String remaining(long deadline){
        long secs=Math.max(0,(deadline-System.currentTimeMillis())/1000);
        if(secs>=86400)return String.format("%dд %02dч",secs/86400,(secs%86400)/3600);
        if(secs>=3600)return String.format("%dч %02dм",secs/3600,(secs%3600)/60);
        return String.format("%dм %02dс",secs/60,secs%60);
    }
    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event){
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen!=null||mc.options.hideGui||mc.player==null||contracts.isEmpty()
            ||!ClientLayout.visible("hud_contracts"))return;
        GuiGraphics g=event.getGuiGraphics();
        int x=10,y=12,width=226,rows=Math.min(3,contracts.size());
        int height=23+rows*44+(contracts.size()>3?13:0);
        ClientLayout.start(g,"hud_contracts",x,y);
        g.fill(x,y,x+width,y+height,0xDC211710);
        g.fill(x,y,x+width,y+2,0xFFD7A04B);
        g.drawString(mc.font,"АКТИВНЫЕ КОНТРАКТЫ",x+9,y+7,0xFFFFCE75,false);
        for(int i=0;i<rows;i++){
            var contract=contracts.get(i);
            int count=Math.min(contract.amount(),inventoryCount(mc,contract.item()));
            boolean ready=count>=contract.amount();
            if(ready && announcedReady.add(contract.id()))
                mc.player.displayClientMessage(Component.literal(
                    "Контракт готов к сдаче: "+name(contract.item())),true);
            if(!ready)announcedReady.remove(contract.id());
            int cy=y+24+i*44;
            String title=name(contract.item());
            g.drawString(mc.font,title,x+9,cy,ready?0xFF9DEC86:0xFFF6D6A3,false);
            g.drawString(mc.font,count+"/"+contract.amount(),x+width-9-mc.font.width(count+"/"+contract.amount()),
                cy,0xFFFFFFFF,false);
            g.fill(x+9,cy+13,x+width-9,cy+19,0xFF493526);
            int available=width-18;
            int completed=(int)Math.min(available,(long)available*count/Math.max(1,contract.amount()));
            g.fill(x+9,cy+13,x+9+completed,cy+19,ready?0xFF84D46A:0xFFD8A04F);
            String footer=ready?"ГОТОВ К СДАЧЕ":remaining(contract.deadline());
            g.drawString(mc.font,"+"+contract.reward()+" М",x+9,cy+25,0xFFE6BC70,false);
            g.drawString(mc.font,footer,x+width-9-mc.font.width(footer),cy+25,
                contract.deadline()-System.currentTimeMillis()<7200000L?0xFFF68A7A:0xFFBFC39C,false);
        }
        if(contracts.size()>3)
            g.drawString(mc.font,"Ещё активных контрактов: "+(contracts.size()-3),
                x+9,y+23+rows*44,0xFFC9AC86,false);
        ClientLayout.end(g);
    }
}
