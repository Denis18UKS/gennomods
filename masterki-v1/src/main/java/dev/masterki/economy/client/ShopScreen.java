package dev.masterki.economy.client;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import com.mojang.math.Axis;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
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
    private int page=0,qty=64,chosen=0,focus=0,sortMode=0,viewedBatch=0;
    private boolean onlyStock=false;
    private EditBox searchBox;
    private boolean syncingSearchBox=false;
    private final ArrayDeque<String> undoSearch=new ArrayDeque<>();
    private String pendingExpiringContract="";
    private long pendingExpiringUntil=0L;
    private String pendingCancelContract="";
    private long pendingCancelUntil=0L;
    private float uiScale;private int px,py;
    private final List<MasterkiNetwork.Product> visible=new ArrayList<>();
    private MasterkiNetwork.Product selected;
    private int clickedCard=-1;
    public ShopScreen(MasterkiNetwork.Snapshot snapshot,String initialTab){
        super(Component.literal("Магазин мастерков"));
        data=snapshot;
        var player=Minecraft.getInstance().player;
        if(player!=null)ClientShopState.load(player.getUUID());
        tab=ClientShopState.lastTab();
        if(initialTab.equals("contracts"))tab="Контракты";
        else if(initialTab.equals("history"))tab="История";
        if(java.util.Arrays.stream(TABS).noneMatch(t->t.equals(tab)))tab="Магазин";
        page=ClientShopState.page(tab);
        category=ClientShopState.category(tab);
        search=ClientShopState.query(tab);
    }
    public void snapshot(MasterkiNetwork.Snapshot next){this.data=next;filter();}
    @Override protected void init(){
        super.init();
        uiScale=Math.min((width-8f)/VW,(height-8f)/VH);
        px=(int)((width-VW*uiScale)/2f);py=(int)((height-VH*uiScale)/2f);
        searchBox=this.addRenderableWidget(new EditBox(font,
            px+Math.round(204*uiScale),py+Math.round(140*uiScale),
            Math.max(40,Math.round(210*uiScale)),Math.max(12,Math.round(17*uiScale)),
            Component.literal("Поиск товаров")));
        searchBox.setMaxLength(128);
        searchBox.setBordered(false);
        searchBox.setTextColor(LIGHT);
        searchBox.setCanLoseFocus(true);
        searchBox.setResponder(this::onSearchChanged);
        syncingSearchBox=true;
        searchBox.setValue(search);
        syncingSearchBox=false;
        searchBox.setFocused(false);
        searchBox.visible=isMarketTab();
        filter();
    }

    private boolean isMarketTab(){
        return tab.equals("Магазин")||tab.equals("Продажа");
    }
    private void setPage(int number){
        page=Math.max(0,number);
        ClientShopState.setPage(tab,page);
    }
    private void onSearchChanged(String updated){
        if(syncingSearchBox||!isMarketTab()||updated.equals(search))return;
        undoSearch.push(search);
        if(undoSearch.size()>40)undoSearch.removeLast();
        search=updated;
        ClientShopState.setQuery(tab,search);
        setPage(0);
        selected=null;
        filter();
    }
    private void switchTab(String newTab){
        if(newTab.equals(tab))return;
        ClientShopState.setPage(tab,page);
        if(searchBox!=null)searchBox.setFocused(false);
        this.setFocused(null);
        tab=newTab;
        ClientShopState.setTab(tab);
        page=ClientShopState.page(tab);
        category=ClientShopState.category(tab);
        search=ClientShopState.query(tab);
        undoSearch.clear();
        viewedBatch=0;
        if(searchBox!=null){
            syncingSearchBox=true;
            searchBox.setValue(search);
            syncingSearchBox=false;
            searchBox.visible=isMarketTab();
        }
        selected=null;
        filter();
    }
    private List<MasterkiNetwork.Task> tasksForTab(){
        if(tab.equals("Задания"))return data.tasks().stream()
            .filter(t->t.id().startsWith("daily:")).toList();
        return data.contracts().stream().map(c->
            new MasterkiNetwork.Task(c.id(),c.item(),c.amount(),c.reward())).toList();
    }
    private MasterkiNetwork.ContractStatus contractStatus(String id){
        return data.contracts().stream().filter(c->c.id().equals(id)).findFirst().orElse(null);
    }
    private static String remaining(long deadline){
        long seconds=Math.max(0,(deadline-System.currentTimeMillis())/1000);
        return String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);
    }
    private String deadlineLabel(long date){
        return java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm",Locale.ROOT)
            .withZone(java.time.ZoneId.of(data.scheduleZone()))
            .format(java.time.Instant.ofEpochMilli(date));
    }
    private static String statusLabel(String status){
        return switch(status){
            case "accepted" -> "Выполняется";
            case "completed" -> "Выполнено";
            case "overdue" -> "Просрочен";
            case "cancelled" -> "Отменён";
            case "expired" -> "Истёк";
            default -> "Доступен";
        };
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
        // Never clamp contract/daily pages against the filtered shop catalogue.
        // The server sends periodic snapshots even while browsing contracts.
        // The catalogue arrives after the opening state packet. Preserve
        // restored shop/sell positions until its chunks have arrived.
        if(!isMarketTab()||!data.products().isEmpty()){
            int clamped=Math.max(0,Math.min(page,maxPageForCurrentTab()));
            if(clamped!=page)setPage(clamped);
        }
        if(selected==null&&!visible.isEmpty())selected=visible.get(0);
        else if(selected!=null){
            String id=selected.id();
            selected=data.products().stream().filter(p->p.id().equals(id)).findFirst().orElse(null);
        }
    }
    private int maxPageForCurrentTab(){
        int count;
        int pageSize;
        if(tab.equals("Контракты")){
            count=data.contracts().size();
            pageSize=5;
        }else if(tab.equals("Задания")){
            count=(int)data.tasks().stream().filter(t->t.id().startsWith("daily:")).count();
            pageSize=8;
        }else if(tab.equals("Магазин")||tab.equals("Продажа")){
            count=visible.size();
            pageSize=15;
        }else return 0;
        return Math.max(0,(count-1)/pageSize);
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
        int textWidth=Minecraft.getInstance().font.width(label);
        float size=Math.min(1.16f,(w-10f)/Math.max(1,textWidth));
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

        boolean marketTab=tab.equals("Магазин")||tab.equals("Продажа");
        if(marketTab){
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
        }
        panel(g,marketTab?186:10,123,marketTab?454:880,332,false);
        if(marketTab){
            g.fill(196,132,422,160,0xFF211611);frame(g,196,132,226,28,0xFF7A4C25);
            // Native EditBox renders after the scaled custom UI. It handles
            // selection, clipboard, arrows, Home/End and paste internally.
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
            boolean daily=tab.equals("Задания");
            text(g,daily?"ЕЖЕДНЕВНЫЕ ЗАДАНИЯ":"ЛИЧНЫЕ КОНТРАКТЫ",28,134,GOLD);
            long reset=daily?data.dailyReset():data.nextContracts();
            text(g,"Обновление: "+deadlineLabel(reset)+" · "+remaining(reset),
                28,151,0xFFD3BCA3);
            if(!daily&&!data.bonuses().isEmpty()){
                int index=Math.floorMod(viewedBatch,data.bonuses().size());
                var batch=data.bonuses().get(index);
                String line="Партия #"+batch.rotation()+" · "+batch.completed()+"/"+batch.total()+
                    "   ["+(index+1)+"/"+data.bonuses().size()+"]  ▶";
                text(g,line,470,134,GOLD);
                String rewardStatus=batch.paid()?"ПРЕМИЯ ВЫПЛАЧЕНА":
                    batch.failed()?"Премия утрачена":
                    "Премия за все: +"+format(batch.reward())+" М";
                text(g,rewardStatus,470,151,
                    batch.failed()?RED:batch.paid()?GREEN:LIGHT);
                int done=Math.max(0,Math.min(390,
                    (int)(390L*batch.completed()/Math.max(1,batch.total()))));
                g.fill(470,164,860,168,0xFF4B3022);
                g.fill(470,164,470+done,168,batch.failed()?RED:GREEN);
            }
            if(!daily && pendingCancelUntil>System.currentTimeMillis())
                text(g,"Отмена уничтожит уже внесённые предметы! Нажмите «Точно?» ещё раз.",28,162,RED);
            else if(!daily && pendingExpiringUntil>System.currentTimeMillis())
                text(g,"До истечения контракта мало времени! Нажмите «Принять» ещё раз.",28,162,RED);
            List<MasterkiNetwork.Task> rows=tasksForTab();
            int perPage=daily?8:5;
            int start=page*perPage;
            for(int i=0;i<perPage && start+i<rows.size();i++){
                MasterkiNetwork.Task c=rows.get(start+i);
                int yy=daily?177+i*30:173+i*50;
                g.fill(26,yy,874,yy+(daily?27:47),0xFF493020);
                ItemStack icon=stack(c.item());
                if(!icon.isEmpty())g.renderItem(icon,33,yy+5);
                String itemName=icon.isEmpty()?c.item():icon.getHoverName().getString();
                text(g,shorten(itemName,daily?62:59)+" ×"+c.amount(),56,yy+5,LIGHT);
                if(daily){
                    boolean done=data.dailyDone().contains(c.id());
                    text(g,"+"+format(c.reward())+" М",631,yy+9,done?0xFFB0A18C:GREEN);
                    button(g,772,yy+2,91,24,done?"Выполнено":"Сдать",done,done?0xFF9AC58F:GOLD);
                }else{
                    MasterkiNetwork.ContractStatus state=contractStatus(c.id());
                    String code=state==null?"expired":state.status();
                    text(g,"+"+format(c.reward())+" М",612,yy+6,GREEN);
                    text(g,"≈"+format(c.reward()/Math.max(1,c.amount()))+" М/шт.",
                        612,yy+22,0xFFC2A58C);
                    if(state!=null){
                        String type=state.difficult()?"Сложный":"Обычный";
                        String descriptor=type+" · "+statusLabel(code)+(state.carriedOver()?" (из прошлой партии)":"");
                        text(g,shorten(descriptor,55),56,yy+20,
                            "completed".equals(code)?GREEN:0xFFD7B990);
                        if(state.partialAllowed()&&"accepted".equals(code)){
                            int contributed=Math.min(state.amount(),state.deposited());
                            text(g,"Внесено: "+contributed+"/"+state.amount(),445,yy+34,GOLD);
                            g.fill(56,yy+44,570,yy+46,0xFF533522);
                            int complete=(int)(514L*contributed/Math.max(1,state.amount()));
                            g.fill(56,yy+44,56+complete,yy+46,GREEN);
                        }
                        boolean nearDeadline=state.deadline()-System.currentTimeMillis()<2L*3600*1000
                            && ("available".equals(code)||"accepted".equals(code));
                        text(g,"До "+deadlineLabel(state.deadline())+" · "+remaining(state.deadline()),
                            56,yy+34,nearDeadline?RED:0xFFC2A58C);
                    }
                    if("available".equals(code)){
                        boolean pending=c.id().equals(pendingExpiringContract)&&pendingExpiringUntil>System.currentTimeMillis();
                        button(g,777,yy+21,86,24,pending?"Точно?":"Принять",pending,GOLD);
                    }else if("accepted".equals(code)){
                        if(state.partialAllowed()){
                            boolean confirm=state.deposited()>0&&c.id().equals(pendingCancelContract)
                                &&pendingCancelUntil>System.currentTimeMillis();
                            button(g,655,yy+21,80,24,confirm?"Точно?":"Отмена",confirm,RED);
                            button(g,741,yy+21,122,24,"Внести имеющиеся",false,GREEN);
                        }else{
                            button(g,689,yy+21,82,24,"Отмена",false,RED);
                            button(g,777,yy+21,86,24,"Сдать",false,GREEN);
                        }
                    }else button(g,777,yy+21,86,24,statusLabel(code),true,0xFF9AC58F);
                }
            }
            if(!daily || rows.size()>8){
                button(g,340,427,38,22,"<",false,GOLD);
                centered(g,"Стр. "+(page+1)+"/"+Math.max(1,(rows.size()+perPage-1)/perPage),421,434,LIGHT);
                button(g,468,427,38,22,">",false,GOLD);
            }
        } else if(tab.equals("История")){
            text(g,"ИСТОРИЯ ОПЕРАЦИЙ",28,137,GOLD);
            for(int i=0;i<Math.min(13,data.history().size());i++)
                text(g,shorten(data.history().get(i),66),28,165+i*20,0xFFEAD2AE);
            if(data.history().isEmpty())text(g,"Операций пока не было",28,170,0xFFBFA58C);
            g.fill(453,133,454,440,0xFF86572A);
            text(g,"АРХИВ КОНТРАКТОВ",470,137,GOLD);
            for(int i=0;i<Math.min(13,data.archived().size());i++){
                var c=data.archived().get(i);
                ItemStack item=stack(c.item());
                String name=item.isEmpty()?c.item():item.getHoverName().getString();
                String state=statusLabel(c.state());
                text(g,shorten(name,30)+" ×"+c.amount(),471,165+i*20,LIGHT);
                text(g,shorten(state+" · "+deadlineLabel(c.finishedAt()),37),
                    683,165+i*20,
                    "completed".equals(c.state())?GREEN:
                    "expired".equals(c.state())?0xFFC3AE98:RED);
            }
            if(data.archived().isEmpty())
                text(g,"Архив появится после первой ротации",470,170,0xFFBFA58C);
        } else {
            text(g,"НАСТРОЙКИ ОТОБРАЖЕНИЯ",28,137,GOLD);
            button(g,28,174,475,38,"Мини-HUD баланса: "+(ClientEconomy.showHud?"ВКЛ":"ВЫКЛ"),true,LIGHT);
            button(g,28,222,475,38,"HUD контрактов: "+(ClientContractHud.enabled()?"ВКЛ":"ВЫКЛ"),true,LIGHT);
            button(g,28,270,475,38,"Режим HUD: "+(ClientContractHud.compact()?"Компактный":"Расширенный"),true,LIGHT);
            text(g,"Положение и масштаб: /masterki dev edit",28,327,0xFFBFA58C);
            text(g,"Настройки HUD сохраняются на клиенте.",28,345,0xFFBFA58C);
        }

        if(marketTab){
        panel(g,647,123,243,332,false);
        if(selected!=null&&(tab.equals("Магазин")||tab.equals("Продажа"))){
            ItemStack item=stack(selected.id());
            if(!item.isEmpty())g.renderItem(item,667,147);
            text(g,shorten(item.getHoverName().getString(),24),691,147,GOLD);
            text(g,shorten(selected.id(),29),661,167,0xFFBEA990);
            button(g,853,138,28,28,ClientEconomy.favorites.contains(selected.id())?"★":"☆",false,GOLD);
            g.fill(662,188,878,189,0xFF86572A);
            boolean selling=tab.equals("Продажа");
            labelRow(g,selling?"Цена выкупа:":"Цена покупки:",
                format(selling?selected.sell():selected.buy())+" М / шт.",198);
            labelRow(g,"На складе:",selected.stock()+" шт.",242);
            labelRow(g,"Поставка в сутки:", "+"+selected.daily(),264);
            labelRow(g,"Макс. запас:",selected.cap()+" шт.",286);
            text(g,"Количество:",661,314,LIGHT);
            button(g,659,332,27,29,"−",false,GOLD);
            button(g,691,332,65,29,""+qty,false,LIGHT);
            button(g,761,332,27,29,"+",false,GOLD);
            int[] counts={16,32,64,256};
            for(int i=0;i<4;i++)button(g,660+i*54,367,51,23,""+counts[i],qty==counts[i],GOLD);
            long subtotal=(selling?selected.sell():selected.buy())*(long)qty;
            long pc=qty>=256?85:qty>=64?90:qty>=16?95:100;
            long total=selling?subtotal:(subtotal/100)*pc+(subtotal%100)*pc/100;
            text(g,selling?"Ты получишь:":"Стоимость:",661,392,LIGHT);
            text(g,(selling?"+":"")+format(total)+" М",661,407,selling?GREEN:GOLD);
            if(selling){
                button(g,661,421,104,27,"Продать 1",false,GREEN);
                button(g,770,421,108,27,"Продать "+qty,false,GOLD);
            }else{
                button(g,661,421,132,27,"Купить 1 предмет",false,GREEN);
                button(g,798,421,80,27,"Купить "+qty,false,GOLD);
            }
        }else{text(g,"Выберите товар",661,153,LIGHT);}
        }

        if(marketTab && ClientLayout.visible("shop_delivery")){
            ClientLayout.start(g,"shop_delivery",10,462);
            panel(g,10,462,264,43,false);coin(g,18,472,25);
            text(g,"Следующая поставка",50,471,LIGHT);
            text(g,timeLeft(),50,485,GOLD);
            ClientLayout.end(g);
        }
        if(marketTab && ClientLayout.visible("shop_tasks")){
            ClientLayout.start(g,"shop_tasks",283,462);
            panel(g,283,462,327,43,false);
            text(g,"ЕЖЕДНЕВНЫЕ ЗАКАЗЫ",299,470,GOLD);
            text(g,"Контракты на ресурсы и Create",299,486,LIGHT);
            ClientLayout.end(g);
        }
        button(g,618,462,127,43,"Топ игроков",false,GOLD);
        button(g,753,462,137,43,"Помощь",false,GOLD);
        g.pose().popPose();
        if(isMarketTab()&&searchBox!=null){
            searchBox.visible=true;
            searchBox.render(g,mx,my,partial);
        }
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
        boolean searchClick=isMarketTab()&&x>=196&&x<422&&y>=132&&y<160;
        if(searchBox!=null && searchBox.isFocused() && !searchClick){
            searchBox.setFocused(false);
            this.setFocused(null);
        }
        if(button==0&&x>=0&&x<VW&&y>=0&&y<VH)
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK,1.0f));
        if(x>=856&&y>=11&&x<890&&y<63){onClose();return true;}
        if(y>=77&&y<117){
            int[] starts={10,160,310,460,610,760};
            for(int i=0;i<6;i++)if(x>=starts[i]&&x<starts[i]+142){
                switchTab(TABS[i]);return true;
            }
        }
        if(tab.equals("Контракты")&&x>=470&&x<860&&y>=130&&y<170
            &&!data.bonuses().isEmpty()){
            viewedBatch=(viewedBatch+1)%data.bonuses().size();
            return true;
        }
        if((tab.equals("Магазин")||tab.equals("Продажа"))
            &&x>=18&&x<171&&y>=157&&y<157+CATEGORIES.length*36){
            int index=(int)((y-157)/36);
            if(index<CATEGORIES.length){
                category=CATEGORIES[index];
                ClientShopState.setCategory(tab,category);
                setPage(0);selected=null;filter();return true;
            }
        }
        if(tab.equals("Настройки")&&x>=28&&x<503){
            if(y>=174&&y<212){ClientEconomy.showHud=!ClientEconomy.showHud;return true;}
            if(y>=222&&y<260){ClientContractHud.toggleEnabled();return true;}
            if(y>=270&&y<308){ClientContractHud.toggleMode();return true;}
        }
        if(tab.equals("Задания")&&x>=772&&x<863&&y>=179&&y<179+8*30){
            int index=page*8+(int)((y-179)/30);
            List<MasterkiNetwork.Task> rows=tasksForTab();
            if(index>=0&&index<rows.size()&&!data.dailyDone().contains(rows.get(index).id())){
                PacketDistributor.sendToServer(new MasterkiNetwork.Action("daily",rows.get(index).id(),1));
                return true;
            }
        }
        if(tab.equals("Контракты")&&x>=655&&x<863&&y>=173&&y<423){
            int index=page*5+(int)((y-173)/50);
            List<MasterkiNetwork.Task> rows=tasksForTab();
            if(index>=0&&index<rows.size()&&y>=173+(index-page*5)*50+21){
                var contract=contractStatus(rows.get(index).id());
                if(contract!=null){
                    String action="";
                    if("available".equals(contract.status())&&x>=777)
                        action="contract_accept";
                    else if("accepted".equals(contract.status())){
                        if(contract.partialAllowed()){
                            if(x>=655&&x<735)action="contract_cancel";
                            else if(x>=741)action="contract_complete";
                        }else{
                            if(x>=689&&x<771)action="contract_cancel";
                            else if(x>=777)action="contract_complete";
                        }
                    }
                    if(!action.isEmpty()){
                        long now=System.currentTimeMillis();
                        if("contract_cancel".equals(action)&&contract.deposited()>0){
                            if(!contract.id().equals(pendingCancelContract)||now>pendingCancelUntil){
                                pendingCancelContract=contract.id();
                                pendingCancelUntil=now+6000L;
                                return true;
                            }
                        }
                        if("contract_accept".equals(action)&&contract.deadline()-now<2L*3600*1000){
                            if(!contract.id().equals(pendingExpiringContract)||now>pendingExpiringUntil){
                                pendingExpiringContract=contract.id();
                                pendingExpiringUntil=now+6000L;
                                return true;
                            }
                        }
                        pendingExpiringContract="";
                        pendingExpiringUntil=0;
                        pendingCancelContract="";
                        pendingCancelUntil=0;
                        PacketDistributor.sendToServer(new MasterkiNetwork.Action(action,contract.id(),1));
                        return true;
                    }
                }
            }
        }
        if(tab.equals("Магазин")||tab.equals("Продажа")){
            if(searchClick && searchBox!=null){
                this.setFocused(searchBox);
                searchBox.setFocused(true);
                return true;
            }
            if(x>=429&&x<523&&y>=132&&y<160){onlyStock=!onlyStock;setPage(0);filter();return true;}
            if(x>=530&&x<630&&y>=132&&y<160){sortMode=(sortMode+1)%3;setPage(0);filter();return true;}
            if(x>=197&&x<=634&&y>=168&&y<423){
                int c=(int)((x-197)/88),r=(int)((y-168)/85);
                if(c>=0&&c<5&&r>=0&&r<3){
                    int index=page*15+r*5+c;
                    if(index<visible.size())selected=visible.get(index);
                    return true;
                }
            }
        }
        if(selected!=null&&(tab.equals("Магазин")||tab.equals("Продажа"))
            &&x>=853&&x<881&&y>=138&&y<167){
            if(!ClientEconomy.favorites.add(selected.id()))ClientEconomy.favorites.remove(selected.id());
            if("Избранное".equals(category))filter();
            return true;
        }
        if((tab.equals("Магазин")||tab.equals("Продажа")||
            tab.equals("Контракты")||tab.equals("Задания"))
            &&x>=340&&x<505&&y>=427&&y<450){
            int newPage=page;
            if(x<393)newPage=Math.max(0,page-1);
            else if(x>452)newPage=page+1;
            setPage(Math.min(newPage,maxPageForCurrentTab()));
            return true;
        }
        if((tab.equals("Магазин")||tab.equals("Продажа"))&&selected!=null
            &&x>=659&&x<789&&y>=332&&y<361){
            if(x<687)qty=Math.max(1,qty-1);
            else if(x>760)qty=Math.min(4096,qty+1);
            return true;
        }
        if((tab.equals("Магазин")||tab.equals("Продажа"))&&selected!=null
            &&x>=660&&x<880&&y>=367&&y<391){
            int index=(int)((x-660)/54); int[] values={16,32,64,256};
            if(index>=0&&index<4)qty=values[index];
            return true;
        }
        if(selected!=null&&x>=661&&x<878&&y>=421&&y<448){
            if(tab.equals("Магазин")){
                PacketDistributor.sendToServer(new MasterkiNetwork.Action("buy",selected.id(),
                    x<795?1:qty));
                return true;
            }
            if(tab.equals("Продажа")){
                int amount=x<766?1:qty;
                PacketDistributor.sendToServer(new MasterkiNetwork.Action("sell",selected.id(),amount));
                return true;
            }
        }
        if(x>=283&&x<610&&y>=462&&y<505){switchTab("Контракты");return true;}
        if(x>=618&&x<745&&y>=462&&y<505){
            PacketDistributor.sendToServer(new MasterkiNetwork.Action("top","",1));return true;
        }
        if(x>=753&&x<890&&y>=462&&y<505){
            PacketDistributor.sendToServer(new MasterkiNetwork.Action("help","",1));return true;
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(searchBox!=null&&searchBox.isFocused()){
            if(key==GLFW.GLFW_KEY_ESCAPE||key==GLFW.GLFW_KEY_ENTER||
                key==GLFW.GLFW_KEY_KP_ENTER){
                searchBox.setFocused(false);
                this.setFocused(null);
                return true;
            }
            // Vanilla EditBox supports Ctrl+A/C/V/X, selections, navigation.
            // A local undo stack additionally supplies Ctrl+Z.
            if(key==GLFW.GLFW_KEY_Z&&(mods&GLFW.GLFW_MOD_CONTROL)!=0
                &&!undoSearch.isEmpty()){
                String previous=undoSearch.pop();
                syncingSearchBox=true;
                searchBox.setValue(previous);
                syncingSearchBox=false;
                search=previous;
                ClientShopState.setQuery(tab,search);
                setPage(0);selected=null;filter();
                return true;
            }
            if(searchBox.keyPressed(key,scan,mods))return true;
        }
        if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}
        return super.keyPressed(key,scan,mods);
    }
    @Override public boolean charTyped(char ch,int mods){
        if(searchBox!=null&&searchBox.isFocused())
            return searchBox.charTyped(ch,mods);
        return super.charTyped(ch,mods);
    }
    @Override public void onClose(){
        ClientShopState.setPage(tab,page);
        super.onClose();
    }
    @Override public boolean isPauseScreen(){return false;}
}
