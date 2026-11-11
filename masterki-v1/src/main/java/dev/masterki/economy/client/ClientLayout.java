package dev.masterki.economy.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.masterki.economy.MasterkiNetwork;
import net.minecraft.client.gui.GuiGraphics;

/** Read-only client cache of world-wide layout settings sent by the server. */
public final class ClientLayout {
    private static final Map<String,MasterkiNetwork.LayoutEntry> entries=new HashMap<>();
    private ClientLayout(){}
    public static void receive(List<MasterkiNetwork.LayoutEntry> fromServer){
        entries.clear();
        for(var e:fromServer)entries.put(e.id(),e);
    }
    public static MasterkiNetwork.LayoutEntry get(String id){
        return entries.getOrDefault(id,new MasterkiNetwork.LayoutEntry(id,
            "inventory_wallet".equals(id)?-32:0,0,100,true));
    }
    public static int x(String id){return get(id).x();}
    public static int y(String id){return get(id).y();}
    public static float scale(String id){return get(id).scale()/100f;}
    public static boolean visible(String id){return get(id).visible();}
    public static void start(GuiGraphics g,String id,int anchorX,int anchorY){
        var p=g.pose();
        p.pushPose();
        p.translate(anchorX+x(id),anchorY+y(id),0);
        p.scale(scale(id),scale(id),1f);
        p.translate(-anchorX,-anchorY,0);
    }
    public static void end(GuiGraphics g){g.pose().popPose();}
    /** Render the ENTIRE 64x64 icon rather than the old cropped top-left rectangle. */
    public static void coin(GuiGraphics g,int x,int y,int size) {
        g.pose().pushPose();
        g.pose().translate(x,y,0);
        float scale=size/64f;
        g.pose().scale(scale,scale,1);
        g.blit(ClientEconomy.COIN,0,0,0,0,64,64,64,64);
        g.pose().popPose();
    }
}
