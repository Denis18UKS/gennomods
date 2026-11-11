package dev.masterki.economy.client;

import dev.masterki.economy.MasterkiNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid="masterki",value=Dist.CLIENT)
public final class ClientEconomy {
    public static final ResourceLocation COIN=ResourceLocation.fromNamespaceAndPath("masterki","textures/gui/coin.png");
    public static final java.util.Set<String> favorites=new java.util.HashSet<>();
    public static boolean showHud=true;
    private static long balance=0;
    private static long changedAt=0;
    private static boolean received=false;
    private ClientEconomy(){}

    public static void receive(MasterkiNetwork.Snapshot snapshot){
        Minecraft mc=Minecraft.getInstance();
        if(received&&balance!=snapshot.balance())changedAt=System.currentTimeMillis();
        received=true;
        balance=snapshot.balance();
        ClientLayout.receive(snapshot.layout());
        if(mc.screen instanceof ShopScreen screen)screen.snapshot(snapshot);
        else if(snapshot.open()&&"layout_editor".equals(snapshot.tab()))
            mc.setScreen(new LayoutEditorScreen());
        else if(snapshot.open())mc.setScreen(new ShopScreen(snapshot,snapshot.tab()));
    }

    private static String format(){return String.format(java.util.Locale.ROOT,"%,d М",balance).replace(',',' ');}

    @SubscribeEvent
    public static void inventory(ScreenEvent.Render.Post event){
        if(!(event.getScreen() instanceof InventoryScreen)||!received)return;
        GuiGraphics g=event.getGuiGraphics();
        int x=Math.max(0,(event.getScreen().width-117)/2);
        int y=Math.max(8,Math.min(event.getScreen().height/2+86,event.getScreen().height-32));
        if(!ClientLayout.visible("inventory_wallet"))return;
        ClientLayout.start(g,"inventory_wallet",x,y);
        g.fill(x,y,x+117,y+31,0xE52A1A13);
        g.fill(x,y,x+117,y+2,0xFFD7A04B);
        g.fill(x,y+29,x+117,y+31,0xFF9D662A);
        if(ClientLayout.visible("inventory_coin")){
            ClientLayout.start(g,"inventory_coin",x+4,y+3);
            ClientLayout.coin(g,x+4,y+3,25);
            ClientLayout.end(g);
        }
        if(ClientLayout.visible("inventory_balance")){
            ClientLayout.start(g,"inventory_balance",x+33,y+5);
            g.drawString(Minecraft.getInstance().font,"Мастерки",x+33,y+5,0xFFE6C188,false);
            g.drawString(Minecraft.getInstance().font,format(),x+33,y+17,0xFFFFFFFF,false);
            ClientLayout.end(g);
        }
        ClientLayout.end(g);
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event){
        if(!showHud||!received||!ClientLayout.visible("hud_wallet"))return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen!=null||mc.options.hideGui)return;
        GuiGraphics g=event.getGuiGraphics();
        int x=g.guiWidth()-129,y=10;
        ClientLayout.start(g,"hud_wallet",x,y);
        g.fill(x,y,x+115,y+29,0xDE211710);
        g.fill(x,y,x+115,y+2,0xFFD7A04B);
        if(ClientLayout.visible("hud_coin")){
            ClientLayout.start(g,"hud_coin",x+3,y+3);
            ClientLayout.coin(g,x+3,y+3,23);
            ClientLayout.end(g);
        }
        if(ClientLayout.visible("hud_balance")){
            ClientLayout.start(g,"hud_balance",x+29,y+9);
            g.drawString(mc.font,format(),x+29,y+10,
                System.currentTimeMillis()-changedAt<2500?0xFFFFF1A8:0xFFFFCE79,false);
            ClientLayout.end(g);
        }
        ClientLayout.end(g);
    }
}
