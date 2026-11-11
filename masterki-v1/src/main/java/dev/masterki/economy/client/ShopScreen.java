package dev.masterki.economy.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import com.mojang.math.Axis;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import dev.masterki.economy.MasterkiNetwork;

public final class ShopScreen extends Screen {
    private static final int VW=900, VH=510;
    private static final int BG=0xFF1C120E, PANEL=0xFF302017, MID=0xFF432818;
    private static final int BRASS=0xFFC48635, GOLD=0xFFFFCA68, LIGHT=0xFFF9E6C1;
    private static final int DARK=0xFF110D0D, GREEN=0xFF80D46A, RED=0xFFE68464;
    private static final String[] TABS={"Магазин","Продажа","Контракты","Задания","История","Настройки"};
    private static final String[] CATEGORIES={"Ресурсы","Create","Инструменты","Строительство","Еда и фермы","Редкости","Другие моды","Избранное"};
    private MasterkiNetwork.Snapshot data;
    private String tab="Магазин",category="Ресурсы",search="";
    private int page=0,qty=64,chosen=0,focus=0,sortMode=0;
    private boolean onlyStock=false;
    private boolean searching=false;
    private float uiScale;private int px,py;
    private final List<MasterkiNetwork.Product> visible=new ArrayList<>();
    private MasterkiNetwork.Product selected;
    private int clickedCard=-1;
    public ShopScreen(MasterkiNetwork.Snapshot snapshot,String initialTab){
        super(Component.literal("Магазин мастерков"));
        data=snapshot;
        if(initialTab.equals("contracts"))tab="Контракты";
        else if(initialTab.equals("history"))tab="История";
    }
    public void snapshot(MasterkiNetwork.Snapshot next){this.data=next;filter();}
    @Override protected void init(){
        super.init();
        uiScale=Math.min((width-8f)/VW,(height-8f)/VH);
        px=(int)((width-VW*uiScale)/2f);py=(int)((height-VH*uiScale)/2f);
        filter();
    }
    private List<MasterkiNetwork.Task> tasksForTab(){
        boolean daily=tab.equals("Задания");
        return data.tasks().stream().filter(t->t.id().startsWith("daily:")==daily).toList();
    }
    private void filter(){
        visible.clear();
        String query=search.toLowerCase(Locale.ROOT);
        for(MasterkiNetwork.Product product:data.products()){
            if(!"Избранное".equals(category) && !category.equals(product.category()))continue;
            if("Избранное".equals(category)&& !ClientEconomy.favorites.contains(product.id()))continue;
            ItemStack item=stack(product.id());
            String name=item.isEmpty()?product.id():item.getHoverName().getString();
            if(!query.isBlank()&&!name.toLowerCase(Locale.ROOT).contains(query)&&!product.id().toLowerCase(Locale.ROOT).contains(query))continue;
            if(onlyStock&&product.stock()==0)continue;
            visible.add(product);
        }
        if(sortMode==1)visible.sort(java.util.Comparator.comparingLong(MasterkiNetwork.Product::buy));
        else if(sortMode==2)visible.sort(java.util.Comparator.comparingLong(MasterkiNetwork.Product::buy).reversed());
        int maxPage=Math.max(0,(visible.size()-1)/15);
        page=Math.max(0,Math.min(page,maxPage));
        if(selected==null&&!visible.isEmpty())selected=visible.get(0);
        else if(selected!=null){
            String id=selected.id();
            selected=data.products().stream().filter(p->p.id().equals(id)).findFirst().orElse(null);
        }
    }
    private static ItemStack stack(String id){
        ResourceLocation resource=ResourceLocation.tryParse(id);
        if(resource==null||!BuiltInRegistries.ITEM.containsKey(resource))return ItemStack.EMPTY;
        Item item=BuiltInRegistries.ITEM.get(resource);
        return item==Items.AIR?ItemStack.EMPTY:new ItemStack(item);
    }
    private void text(GuiGraphics g,String s,int x,int y,int color){
        g.pose().pushPose();
        g.pose().translate(x,y,0);
        g.pose().scale(1.18f,1.18f,1);
        g.drawString(font,s,0,0,color,false);
        g.pose().popPose();
    }
    private void centered(GuiGraphics g,String s,int x,int y,int col){
        g.pose().pushPose();
        g.pose().translate(x,y,0);
        g.pose().scale(1.13f,1.13f,1);
        g.drawString(font,s,-font.width(s)/2,0,col,false);
        g.pose().popPose();
    }
    private static void panel(GuiGraphics g,int x,int y,int w,int h,boolean lit){
        g.fill(x+2,y+3,x+w+2,y+h+3,0xAB000000);
        g.fill(x,y,x+w,y+h,BRASS);
        g.fill(x+3,y+3,x+w-3,y+h-3,BG);
        g.fill(x+5,y+5,x+w-5,y+h-5,lit?MID:PANEL);
        int edge=lit?GOLD:0xFF79532D;
        g.fill(x+5,y+5,x+w-5,y+7,edge);
        g.fill(x+5,y+h-7,x+w-5,y+h-5,edge);
        for(int a:xAndW(x,w)){for(int b:yAndH(y,h)){
            g.fill(a,b,a+3,b+3,0xFFFFCE69);
            g.fill(a+1,b+1,a+2,b+2,0xFF9B5526);
        }}
    }
    private static int[] xAndW(int x,int w){return new int[]{x+3,x+w-6};}
    private static int[] yAndH(int y,int h){return new int[]{y+3,y+h-6};}
    private static void button(GuiGraphics g,int x,int y,int w,int h,String label,boolean active,int color){
        panel(g,x,y,w,h,active);
        float size=1.16f;
        int textWidth=Minecraft.getInstance().font.width(label);
        g.pose().pushPose();
        g.pose().translate(x+(w-textWidth*size)/2,y+(h-8*size)/2,0);
        g.pose().scale(size,size,1);
        g.drawString(Minecraft.getInstance().font,label,0,0,color,false);
        g.pose().popPose();
    }
    private void coin(GuiGraphics g,int x,int y,int size){
        ClientLayout.coin(g,x,y,size);
    }
    private String timeLeft(){
        long s=Math.max(0,(data.nextRestock()-System.currentTimeMillis())/1000);
        return String.format(Locale.ROOT,"%02d:%02d:%02d",s/3600,s/60%60,s%60);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xE60A0707);
        g.pose().pushPose();
        g.pose().translate(px,py,0);
        g.pose().scale(uiScale,uiScale,1f);
        g.fill(0,0,VW,VH,BG);
        // Copper piping and gears, matching the attached Create-inspired layout.
        for(int i=0;i<9;i++){
            int x=8+i*110;g.fill(x,0,x+3,VH,0x226F350F);
        }
        panel(g,10,8,445,62,true);
        coin(g,16,12,52);
        text(g,"МАГАЗИН МАСТЕРКОВ",78,19,GOLD);
        text(g,"M A S T E R K I   E C O N O M Y",78,40,0xFFE2B276);
        if(ClientLayout.visible("shop_gear")){
            ClientLayout.start(g,"shop_gear",433,30);
            gear(g,433,30,18,0xFFB66B23);
            ClientLayout.end(g);
        }

        if(ClientLayout.visible("shop_balance")){
            ClientLayout.start(g,"shop_balance",610,8);
            panel(g,610,8,242,62,true);
            text(g,"Ваш баланс:",657,19,LIGHT);
            coin(g,625,30,26);
            text(g,format(data.balance())+" М",659,39,GOLD);
            ClientLayout.end(g);
        }
        button(g,856,11,34,52,"X",false,GOLD);

        int[] starts={10,160,310,460,610,760};
        for(int i=0;i<6;i++){
            button(g,starts[i],77,142,40,TABS[i],tab.equals(TABS[i]),tab.equals(TABS[i])?GOLD:LIGHT);
            ItemStack tabIcon=switch(i){
                case 0 -> new ItemStack(Items.CHEST);
                case 1 -> new ItemStack(Items.EMERALD);
                case 2 -> new ItemStack(Items.WRITABLE_BOOK);
                case 3 -> new ItemStack(Items.BOOK);
                case 4 -> new ItemStack(Items.CLOCK);
                default -> new ItemStack(Items.COMPARATOR);
            };
            g.renderItem(tabIcon,starts[i]+7,88);
        }

        panel(g,10,123,170,332,false);
        text(g,"⚙  Категории",28,134,GOLD);
        for(int i=0;i<CATEGORIES.length;i++){
            int y=157+i*36;
            button(g,18,y,153,32,CATEGORIES[i],CATEGORIES[i].equals(category),LIGHT);
            ItemStack categoryIcon=switch(i){
                case 0 -> new ItemStack(Items.IRON_INGOT);
                case 1 -> stack("create:cogwheel").isEmpty()?new ItemStack(Items.COPPER_INGOT):stack("create:cogwheel");
                case 2 -> new ItemStack(Items.IRON_PICKAXE);
                case 3 -> new ItemStack(Items.BRICKS);
                case 4 -> new ItemStack(Items.WHEAT);
                case 5 -> new ItemStack(Items.NETHER_STAR);
                case 6 -> new ItemStack(Items.ENCHANTING_TABLE);
                default -> new ItemStack(Items.NETHER_STAR);
            };
            g.renderItem(categoryIcon,25,y+9);
        }

        panel(g,186,123,454,332,false);
        if(tab.equals("Магазин")||tab.equals("Продажа")){
            g.fill(196,132,422,160,0xFF211611);frame(g,196,132,226,28,0xFF7A4C25);
            text(g,"⌕ "+search+(searching&&System.currentTimeMillis()%900<450?"_":""),204,141,search.isBlank()?0xFFB5A08B:LIGHT);
            button(g,429,132,94,28,onlyStock?"В наличии":"Фильтры",onlyStock,LIGHT);
            button(g,530,132,100,28,sortMode==0?"По цене ↕":sortMode==1?"Цена ↑":"Цена ↓",sortMode!=0,LIGHT);
            int offset=page*15;
            for(int i=0;i<15;i++){
                int index=offset+i;
                if(index>=visible.size())break;
                MasterkiNetwork.Product item=visible.get(index);
                int c=i%5,r=i/5;
                int x=197+c*88,y=168+r*85;
                boolean current=selected!=null&&selected.id().equals(item.id());
                panel(g,x,y,83,79,current);
                ItemStack icon=stack(item.id());
                if(!icon.isEmpty())g.renderItem(icon,x+29,y+9);
                String name=icon.isEmpty()?item.id():icon.getHoverName().getString();
                centered(g,shorten(name,12),x+41,y+35,LIGHT);
                centered(g,"Склад: "+item.stock(),x+41,y+48,0xFFBFA58C);
                coin(g,x+10,y+61,11);
                text(g,format(tab.equals("Продажа")?item.sell():item.buy()),x+25,y+63,GOLD);
            }
            button(g,344,427,34,22,"<",false,GOLD);
            centered(g,(page+1)+"/"+Math.max(1,(visible.size()+14)/15),414,434,LIGHT);
            button(g,464,427,34,22,">",false,GOLD);
        } else if(tab.equals("Контракты")||tab.equals("Задания")){
            text(g,tab.equals("Задания")?"ЕЖЕДНЕВНЫЕ ЗАДАНИЯ":"ЗАКАЗЫ ПРОИЗВОДСТВА",205,137,GOLD);
            text(g,tab.equals("Задания")?"3 новых задания каждые реальные сутки":"Сдавайте ресурсы и получайте мастерки",205,153,0xFFD3BCA3);
            List<MasterkiNetwork.Task> rows=tasksForTab();
            int start=page*8;
            for(int i=0;i<8 && start+i<rows.size();i++){
                MasterkiNetwork.Task c=rows.get(start+i);
                int yy=177+i*30;
                g.fill(202,yy,623,yy+27,0xFF493020);
                ItemStack icon=stack(c.item());if(!icon.isEmpty())g.renderItem(icon,207,yy+5);
                text(g,shorten(icon.getHoverName().getString(),23)+" ×"+c.amount(),230,yy+9,LIGHT);
                text(g,"+"+format(c.reward())+" М",450,yy+9,GREEN);
                button(g,544,yy+2,72,24,"Сдать",false,GOLD);
            }
            button(g,340,427,38,22,"<",false,GOLD);
            centered(g,"Стр. "+(page+1),421,434,LIGHT);
            button(g,468,427,38,22,">",false,GOLD);
        } else if(tab.equals("История")){
            text(g,"ИСТОРИЯ ОПЕРАЦИЙ",205,137,GOLD);
            for(int i=0;i<Math.min(16,data.history().size());i++)
                text(g,shorten(data.history().get(i),60),203,163+i*17,0xFFEAD2AE);
            if(data.history().isEmpty())text(g,"Операций пока не было",210,170,0xFFBFA58C);
        } else {
            text(g,"НАСТРОЙКИ ОТОБРАЖЕНИЯ",205,137,GOLD);
            button(g,207,174,391,38,"Показывать мини-HUD: "+(ClientEconomy.showHud?"ДА":"НЕТ"),true,LIGHT);
            text(g,"Баланс в инвентаре отображается отдельно.",210,224,0xFFBFA58C);
            text(g,"Основной HUD не загромождается.",210,241,0xFFBFA58C);
        }

        panel(g,647,123,243,332,false);
        if(selected!=null){
            ItemStack item=stack(selected.id());
            if(!item.isEmpty())g.renderItem(item,667,147);
            text(g,shorten(item.getHoverName().getString(),24),691,147,GOLD);
            text(g,shorten(selected.id(),29),661,167,0xFFBEA990);
            button(g,853,138,28,28,ClientEconomy.favorites.contains(selected.id())?"★":"☆",false,GOLD);
            g.fill(662,188,878,189,0xFF86572A);
            labelRow(g,"Купить:",selected.buy()+" М / шт.",198);
            labelRow(g,"Продать:",selected.sell()+" М / шт.",220);
            labelRow(g,"На складе:",selected.stock()+" шт.",242);
            labelRow(g,"Поставка в сутки:", "+"+selected.daily(),264);
            labelRow(g,"Макс. запас:",selected.cap()+" шт.",286);
            text(g,"Количество:",661,314,LIGHT);
            button(g,659,332,27,29,"−",false,GOLD);
            button(g,691,332,65,29,""+qty,false,LIGHT);
            button(g,761,332,27,29,"+",false,GOLD);
            int[] counts={16,32,64,256};
            for(int i=0;i<4;i++)button(g,660+i*54,367,51,23,""+counts[i],qty==counts[i],GOLD);
            long subtotal=selected.buy()*(long)qty,pc=qty>=256?85:qty>=64?90:qty>=16?95:100;
            long cost=(subtotal/100)*pc+(subtotal%100)*pc/100;
            text(g,"Итого:",661,402,LIGHT);text(g,format(cost)+" М",723,402,GOLD);
            button(g,661,421,102,27,"Купить",false,GREEN);
            button(g,768,421,110,27,"Продать",false,GOLD);
        }else{text(g,"Выберите товар",661,153,LIGHT);}

        if(ClientLayout.visible("shop_delivery")){
            ClientLayout.start(g,"shop_delivery",10,462);
            panel(g,10,462,264,43,false);coin(g,18,472,25);
            text(g,"Следующая поставка",50,471,LIGHT);
            text(g,timeLeft(),50,485,GOLD);
            ClientLayout.end(g);
        }
        if(ClientLayout.visible("shop_tasks")){
            ClientLayout.start(g,"shop_tasks",283,462);
            panel(g,283,462,327,43,false);
            text(g,"ЕЖЕДНЕВНЫЕ ЗАКАЗЫ",299,470,GOLD);
            text(g,"Контракты на ресурсы и Create",299,486,LIGHT);
            ClientLayout.end(g);
        }
        button(g,618,462,127,43,"Топ игроков",false,GOLD);
        button(g,753,462,137,43,"Помощь",false,GOLD);
        g.pose().popPose();
    }
    private void labelRow(GuiGraphics g,String left,String right,int y){
        text(g,left,661,y,LIGHT);
        text(g,right,872-font.width(right),y,GOLD);
    }
    private static String format(long amount){return String.format(Locale.ROOT,"%,d",amount).replace(',',' ');}
    private String shorten(String s,int length){return s.length()<=length?s:s.substring(0,length-1)+"…";}
    private static void frame(GuiGraphics g,int x,int y,int w,int h,int col){
        g.fill(x,y,x+w,y+1,col);g.fill(x,y+h-1,x+w,y+h,col);
        g.fill(x,y,x+1,y+h,col);g.fill(x+w-1,y,x+w,y+h,col);
    }
    private void gear(GuiGraphics g,int cx,int cy,int radius,int color){
        ItemStack wheel=stack("create:cogwheel");
        if(wheel.isEmpty())wheel=new ItemStack(Items.CLOCK);
        g.pose().pushPose();
        g.pose().translate(cx,cy,40);
        g.pose().mulPose(Axis.ZP.rotationDegrees((System.currentTimeMillis()%8000L)*0.045f));
        g.pose().scale(1.8f,1.8f,1);
        g.renderItem(wheel,-8,-8);
        g.pose().popPose();
    }
    private float atX(double x){return (float)((x-px)/uiScale);}
    private float atY(double y){return (float)((y-py)/uiScale);}
    @Override public boolean mouseClicked(double mx,double my,int button){
        float x=atX(mx),y=atY(my);
        if(button==0&&x>=0&&x<VW&&y>=0&&y<VH)
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK,1.0f));
        if(x>=856&&y>=11&&x<890&&y<63){onClose();return true;}
        if(y>=77&&y<117){
            int[] starts={10,160,310,460,610,760};
            for(int i=0;i<6;i++)if(x>=starts[i]&&x<starts[i]+142){tab=TABS[i];page=0;return true;}
        }
        if(x>=18&&x<171&&y>=157&&y<157+CATEGORIES.length*36){
            int index=(int)((y-157)/36);
            if(index<CATEGORIES.length){category=CATEGORIES[index];page=0;selected=null;filter();return true;}
        }
        if(tab.equals("Настройки")&&x>=207&&x<598&&y>=174&&y<212){ClientEconomy.showHud=!ClientEconomy.showHud;return true;}
        if((tab.equals("Контракты")||tab.equals("Задания"))&&x>=544&&x<616&&y>=179&&y<=179+8*30){
            int index=page*8+(int)((y-179)/30);
            List<MasterkiNetwork.Task> rows=tasksForTab();
            if(index>=0&&index<rows.size()){
                PacketDistributor.sendToServer(new MasterkiNetwork.Action(tab.equals("Задания")?"daily":"contract",rows.get(index).id(),1));
                return true;
            }
        }
        if(tab.equals("Магазин")||tab.equals("Продажа")){
            if(x>=196&&x<422&&y>=132&&y<160){searching=true;return true;}
            if(x>=429&&x<523&&y>=132&&y<160){onlyStock=!onlyStock;page=0;filter();return true;}
            if(x>=530&&x<630&&y>=132&&y<160){sortMode=(sortMode+1)%3;page=0;filter();return true;}
            if(x>=197&&x<=634&&y>=168&&y<423){
                int c=(int)((x-197)/88),r=(int)((y-168)/85);
                if(c>=0&&c<5&&r>=0&&r<3){
                    int index=page*15+r*5+c;
                    if(index<visible.size())selected=visible.get(index);
                    return true;
                }
            }
        }
        if(selected!=null&&x>=853&&x<881&&y>=138&&y<167){
            if(!ClientEconomy.favorites.add(selected.id()))ClientEconomy.favorites.remove(selected.id());
            if("Избранное".equals(category))filter();
            return true;
        }
        if(x>=340&&x<505&&y>=427&&y<450){
            if(x<393)page=Math.max(0,page-1);else if(x>452)page++;
            int count=tab.equals("Контракты")||tab.equals("Задания")?tasksForTab().size():visible.size();
            page=Math.min(page,Math.max(0,(count-1)/((tab.equals("Контракты")||tab.equals("Задания"))?8:15)));
            return true;
        }
        if(x>=659&&x<789&&y>=332&&y<361){
            if(x<687)qty=Math.max(1,qty-1);
            else if(x>760)qty=Math.min(4096,qty+1);
            return true;
        }
        if(x>=660&&x<880&&y>=367&&y<391){
            int index=(int)((x-660)/54); int[] values={16,32,64,256};
            if(index>=0&&index<4)qty=values[index];
            return true;
        }
        if(selected!=null&&x>=661&&x<878&&y>=421&&y<448){
            PacketDistributor.sendToServer(new MasterkiNetwork.Action(x<765?"buy":"sell",selected.id(),qty));
            return true;
        }
        if(x>=283&&x<610&&y>=462&&y<505){tab="Контракты";page=0;return true;}
        if(x>=618&&x<745&&y>=462&&y<505){
            PacketDistributor.sendToServer(new MasterkiNetwork.Action("top","",1));return true;
        }
        if(x>=753&&x<890&&y>=462&&y<505){
            PacketDistributor.sendToServer(new MasterkiNetwork.Action("help","",1));return true;
        }
        searching=false;
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}
        if(searching){
            if(key==GLFW.GLFW_KEY_BACKSPACE&&!search.isEmpty()){search=search.substring(0,search.length()-1);page=0;filter();return true;}
            if(key==GLFW.GLFW_KEY_ENTER){searching=false;return true;}
        }
        return super.keyPressed(key,scan,mods);
    }
    @Override public boolean charTyped(char ch,int mods){
        if(searching&&search.length()<45&&!Character.isISOControl(ch)){
            search+=ch;page=0;selected=null;filter();return true;
        }
        return super.charTyped(ch,mods);
    }
    @Override public boolean isPauseScreen(){return false;}
}
