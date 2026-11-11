package dev.masterki.economy.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.masterki.economy.MasterkiNetwork;
import org.lwjgl.glfw.GLFW;

/** Admin-only server-authoritative editor.  The server validates every mutation. */
public final class LayoutEditorScreen extends Screen {
    private static final String[] IDS={
        "inventory_wallet","inventory_coin","inventory_balance",
        "hud_wallet","hud_coin","hud_balance",
        "shop_balance","shop_gear","shop_delivery","shop_tasks"
    };
    private final Map<String,MasterkiNetwork.LayoutEntry> staged=new HashMap<>();
    private int selection=0;
    private boolean dragging=false;
    private int dragStartX,dragStartY,initialX,initialY;

    public LayoutEditorScreen(){super(Component.literal("Редактор Masterki HUD"));}
    @Override protected void init(){
        super.init();
        staged.clear();
        for(String id:IDS)staged.put(id,ClientLayout.get(id));
    }
    private String id(){return IDS[selection];}
    private MasterkiNetwork.LayoutEntry current(){return staged.get(id());}
    private void change(int x,int y,int scale,boolean visible){
        var old=current();
        x=Math.clamp(x,-4096,4096);
        y=Math.clamp(y,-4096,4096);
        scale=Math.clamp(scale,50,250);
        staged.put(id(),new MasterkiNetwork.LayoutEntry(id(),x,y,scale,visible));
        if(old.x()!=x)PacketDistributor.sendToServer(new MasterkiNetwork.Action("layout_x",id(),x));
        if(old.y()!=y)PacketDistributor.sendToServer(new MasterkiNetwork.Action("layout_y",id(),y));
        if(old.scale()!=scale)PacketDistributor.sendToServer(new MasterkiNetwork.Action("layout_scale",id(),scale));
        if(old.visible()!=visible)PacketDistributor.sendToServer(new MasterkiNetwork.Action("layout_visible",id(),visible?1:0));
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xF017120F);
        super.render(g,mx,my,partial);
        int left=18,top=36;
        g.drawString(font,"MASTERKI ECONOMY / DEV LAYOUT",left,13,0xFFFFCE75,false);
        g.drawString(font,"Выбери индикатор, перетащи макет или используй стрелки",left,25,0xFFEAD6AE,false);
        for(int n=0;n<IDS.length;n++){
            int rowY=top+n*21;
            g.fill(left,rowY,left+175,rowY+19,n==selection?0xFF74451F:0xFF3A281E);
            g.drawString(font,IDS[n],left+6,rowY+6,n==selection?0xFFFFD47B:0xFFE3C69D,false);
        }
        var e=current();
        int cx=left+210+(width-left-230)/2;
        int cy=height/2;
        g.fill(cx-105,cy-60,cx+105,cy+60,0xFF281D16);
        g.renderOutline(cx-105,cy-60,210,120,0xFF95662D);
        int dx=Math.clamp(e.x(),-90,90),dy=Math.clamp(e.y(),-45,45);
        g.fill(cx-60+dx,cy-16+dy,cx+60+dx,cy+16+dy,
            e.visible()?0xFF573722:0xFF373737);
        g.renderOutline(cx-60+dx,cy-16+dy,120,32,0xFFFFC65A);
        g.drawCenteredString(font,e.id(),cx+dx,cy-5+dy,0xFFFFE1A0);
        g.drawString(font,"X: "+e.x()+"   Y: "+e.y()+"    Размер: "+e.scale()+"%    Показ: "+(e.visible()?"ДА":"НЕТ"),
            left+202,height-75,0xFFFFDC9E,false);
        g.drawString(font,"[+] [-] масштаб     [V] видимость     [R] сброс",
            left+202,height-58,0xFFEAD6AE,false);
        g.fill(width-105,height-35,width-14,height-9,0xFFB07D36);
        g.drawCenteredString(font,"СОХРАНИТЬ",width-59,height-25,0xFF180F07);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0 && mx>=18 && mx<193 && my>=36 && my<36+IDS.length*21){
            int n=(int)((my-36)/21);
            if(n>=0&&n<IDS.length)selection=n;
            return true;
        }
        if(button==0 && mx>=width-105 && mx<width-14 && my>=height-35 && my<height-9){
            onClose();return true;
        }
        int left=18;
        int cx=left+210+(width-left-230)/2,cy=height/2;
        var e=current();int dx=Math.clamp(e.x(),-90,90),dy=Math.clamp(e.y(),-45,45);
        if(button==0 && mx>=cx-60+dx && mx<cx+60+dx && my>=cy-16+dy && my<cy+16+dy){
            dragging=true;dragStartX=(int)mx;dragStartY=(int)my;
            initialX=e.x();initialY=e.y();return true;
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dragX,double dragY){
        if(dragging && button==0){
            // Preview follows mouse. Changes are sent when the drag completes.
            var e=current();
            staged.put(id(),new MasterkiNetwork.LayoutEntry(id(),
                Math.clamp(initialX+(int)x-dragStartX,-4096,4096),
                Math.clamp(initialY+(int)y-dragStartY,-4096,4096),e.scale(),e.visible()));
            return true;
        }
        return super.mouseDragged(x,y,button,dragX,dragY);
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        if(dragging){
            dragging=false;
            var e=current();
            PacketDistributor.sendToServer(new MasterkiNetwork.Action("layout_x",id(),e.x()));
            PacketDistributor.sendToServer(new MasterkiNetwork.Action("layout_y",id(),e.y()));
            return true;
        }
        return super.mouseReleased(x,y,button);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        var e=current();int step=(modifiers&GLFW.GLFW_MOD_SHIFT)!=0?10:1;
        switch(key){
            case GLFW.GLFW_KEY_LEFT -> change(e.x()-step,e.y(),e.scale(),e.visible());
            case GLFW.GLFW_KEY_RIGHT -> change(e.x()+step,e.y(),e.scale(),e.visible());
            case GLFW.GLFW_KEY_UP -> change(e.x(),e.y()-step,e.scale(),e.visible());
            case GLFW.GLFW_KEY_DOWN -> change(e.x(),e.y()+step,e.scale(),e.visible());
            case GLFW.GLFW_KEY_EQUAL,GLFW.GLFW_KEY_KP_ADD -> change(e.x(),e.y(),e.scale()+5,e.visible());
            case GLFW.GLFW_KEY_MINUS,GLFW.GLFW_KEY_KP_SUBTRACT -> change(e.x(),e.y(),e.scale()-5,e.visible());
            case GLFW.GLFW_KEY_V -> change(e.x(),e.y(),e.scale(),!e.visible());
            case GLFW.GLFW_KEY_R -> {
                PacketDistributor.sendToServer(new MasterkiNetwork.Action("layout_reset",id(),1));
                staged.put(id(),new MasterkiNetwork.LayoutEntry(id(),
                    "inventory_wallet".equals(id())?-32:0,0,100,true));
            }
            default -> {return super.keyPressed(key,scan,modifiers);}
        }
        return true;
    }
    @Override public boolean isPauseScreen(){return false;}
}
