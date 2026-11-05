package dev.timefracture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import java.util.Comparator;
import java.util.List;

/**
 * Visual artifact interface. Candidate list comes from actual network PlayerInfo
 * received from the server. Commands are ALWAYS validated server-side again.
 * Alternate echoes and NPCs cannot be selected through the GUI.
 */
public final class TimeArtifactScreen extends Screen {
    private int page=0;
    private String selected="";
    private EditBox parent;
    private List<String> online=List.of();
    private static final int WINDOW=0xEE101426, BORDER=0xFF7C60D4;
    private static final int LABEL=0xFFEAE5FF, MUTED=0xFFACA3C8;

    public TimeArtifactScreen(){
        super(Component.literal("Артефакт раскола времени"));
    }

    private void command(String value){
        Minecraft mc=Minecraft.getInstance();
        if(mc.getConnection()!=null)mc.getConnection().sendCommand(value);
    }

    @Override
    protected void init(){
        var mc=Minecraft.getInstance();
        online=mc.getConnection()==null ? List.of()
            :mc.getConnection().getOnlinePlayers().stream()
               .map((PlayerInfo p)->p.getProfile().getName())
               .distinct().sorted(Comparator.comparing(String::toLowerCase))
               .toList();
        int left=(width-396)/2,top=(height-290)/2;
        // During the alpha, this gorgeous client UI performs commands.
        // Every command does its own server-side UUID/online permission check.
        int start=page*7,end=Math.min(online.size(),start+7);
        for(int i=start;i<end;i++){
            final String name=online.get(i);
            int row=i-start;
            String label=(name.equals(selected)?"◆ ":"◇ ")+name;
            addRenderableWidget(Button.builder(Component.literal(label),b->{
                selected=name;
                command("timefracture focus "+name);
                rebuildWidgets();
            }).bounds(left+18,top+63+row*23,360,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("◀"),b->{
            if(page>0){page--;rebuildWidgets();}
        }).bounds(left+18,top+229,31,20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"),b->{
            if((page+1)*7<online.size()){page++;rebuildWidgets();}
        }).bounds(left+347,top+229,31,20).build());

        // Text box doubles as parent timeline ID for recursive branch and travel.
        parent=new EditBox(font,left+94,top+257,102,18,Component.literal("ID линии"));
        parent.setMaxLength(32);
        parent.setValue("-");
        addRenderableWidget(parent);
        addRenderableWidget(Button.builder(Component.literal("Создать ветвь"),b->
            command("timefracture branch "+validId())
        ).bounds(left+201,top+255,114,20).build());
        addRenderableWidget(Button.builder(Component.literal("Войти"),b->{
            if(!validId().equals("-"))command("timefracture enter "+validId());
        }).bounds(left+318,top+255,60,20).build());
        addRenderableWidget(Button.builder(Component.literal("Древо"),b->command("timefracture tree")
        ).bounds(left+55,top+229,82,20).build());
        addRenderableWidget(Button.builder(Component.literal("Вернуться"),b->command("timefracture return")
        ).bounds(left+143,top+229,105,20).build());
        addRenderableWidget(Button.builder(Component.literal("Обновить"),b->{
            page=0;rebuildWidgets();
        }).bounds(left+254,top+229,87,20).build());
    }

    private String validId(){
        String id=parent==null?"-":parent.getValue().trim();
        return id.matches("T-[0-9]{1,7}")?id:"-";
    }

    @Override
    public void render(GuiGraphics gui,int mouseX,int mouseY,float delta){
        int x=(width-396)/2,y=(height-290)/2;
        gui.fill(x-2,y-2,x+398,y+292,BORDER);
        gui.fill(x,y,x+396,y+290,WINDOW);
        gui.fill(x,y,x+396,y+4,0xFFBEA5FF);
        gui.drawCenteredString(font,"✦ АРТЕФАКТ ВРЕМЕННЫХ ЛИНИЙ ✦",width/2,y+13,LABEL);
        gui.drawCenteredString(font,"Источник временных ответвлений · реальные игроки онлайн",width/2,y+31,MUTED);
        gui.drawString(font,"Выбери судьбу:",x+18,y+50,LABEL,false);
        gui.drawString(font,"Игроков: "+online.size()+"  ·  стр. "+(page+1),x+215,y+50,MUTED,false);
        gui.drawString(font,"Выбран: "+(selected.isEmpty()?"никто":selected),x+18,y+208,0xFF6FE7DD,false);
        gui.drawString(font,"Линия:",x+18,y+262,LABEL,false);
        if(online.isEmpty()){
            gui.drawCenteredString(font,"На сервере нет полученных игроков",width/2,y+120,0xFFFFA6A6);
        }
        // Widgets (player list, action buttons, parent ID field) MUST be
        // rendered after the panel; otherwise the panel occludes them.
        super.render(gui,mouseX,mouseY,delta);
    }

    @Override
    public boolean isPauseScreen(){return false;}
}
