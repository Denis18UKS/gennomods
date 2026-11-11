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
    public static boolean showHud=false;
    private static long balance=0;
    private static long changedAt=0;
    private static boolean received=false;
    private ClientEconomy(){}
    public static void receive(MasterkiNetwork.Snapshot snapshot){
        Minecraft mc=Minecraft.getInstance();
        if(received&&balance!=snapshot.balance())changedAt=System.currentTimeMillis();
        received=true;
        balance=snapshot.balance();
        if(mc.screen instanceof ShopScreen screen)screen.snapshot(snapshot);
        else if(snapshot.open())mc.setScreen(new ShopScreen(snapshot,snapshot.tab()));
    }
    @SubscribeEvent
    public static void inventory(ScreenEvent.Render.Post event){
        if(!(event.getScreen() instanceof InventoryScreen)||!received)return;
        GuiGraphics g=event.getGuiGraphics();
        int x=Math.min(event.getScreen().width-117,event.getScreen().width/2+93);
        int y=Math.max(8,event.getScreen().height/2-82);
        if(x<0)return;
        g.fill(x,y,x+117,y+31,0xE52A1A13);
        g.fill(x,y,x+117,y+2,0xFFD7A04B);
        g.fill(x,y+29,x+117,y+31,0xFF9D662A);
        g.blit(COIN,x+4,y+3,0,0,25,25,64,64);
        g.drawString(Minecraft.getInstance().font,"Мастерки",x+33,y+5,0xFFE6C188,false);
        g.drawString(Minecraft.getInstance().font,String.format("%,d М",balance).replace(',',' '),x+33,y+17,0xFFFFFFFF,false);
    }
    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event){
        if(!showHud||!received||System.currentTimeMillis()-changedAt>6000)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen!=null)return;
        GuiGraphics g=event.getGuiGraphics();
        int x=g.guiWidth()-119,y=g.guiHeight()-35;
        g.fill(x,y,x+115,y+28,0xCC211710);
        g.blit(COIN,x+3,y+3,0,0,21,21,64,64);
        g.drawString(mc.font,String.format("%,d М",balance).replace(',',' '),x+29,y+10,0xFFFFCE79,false);
    }
}
