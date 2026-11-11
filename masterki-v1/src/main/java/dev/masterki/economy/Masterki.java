package dev.masterki.economy;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod("masterki")
public final class Masterki {
    public static final String MODID="masterki";
    private static final long DAY_MS=86_400_000L;
    private static long tickCounter;
    private static List<Offer> offers;

    public Masterki(IEventBus bus) {
        bus.addListener(MasterkiNetwork::register);
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::joined);
    }

    public record Offer(String id,String category,long buy,long sell,int cap,int daily) {}
    public record Contract(String id,String item,int amount,long reward) {}

    public static List<Offer> offers() {
        if(offers==null) {
            LinkedHashMap<String,Offer> out=new LinkedHashMap<>();
            // Curated staples, with server-side default prices and stock.
            catalog(out,"minecraft:iron_ingot",18,512,256);
            catalog(out,"minecraft:copper_ingot",12,512,256);
            catalog(out,"minecraft:gold_ingot",35,256,128);
            catalog(out,"minecraft:diamond",250,32,8);
            catalog(out,"minecraft:netherite_ingot",3500,4,1);
            catalog(out,"minecraft:coal",9,512,256);
            catalog(out,"minecraft:redstone",12,512,256);
            catalog(out,"minecraft:lapis_lazuli",12,384,192);
            catalog(out,"minecraft:quartz",18,256,128);
            catalog(out,"minecraft:emerald",160,64,16);
            catalog(out,"minecraft:iron_block",160,128,64);
            catalog(out,"minecraft:copper_block",100,128,64);
            catalog(out,"minecraft:gold_block",310,64,32);
            catalog(out,"minecraft:diamond_block",2250,8,2);
            catalog(out,"minecraft:amethyst_shard",16,256,128);
            catalog(out,"minecraft:obsidian",24,256,128);
            catalog(out,"minecraft:ender_pearl",85,128,64);
            catalog(out,"minecraft:slime_ball",20,256,128);
            catalog(out,"minecraft:blaze_rod",55,128,64);
            catalog(out,"minecraft:glowstone_dust",8,384,192);
            catalog(out,"minecraft:oak_log",8,512,256);
            catalog(out,"minecraft:spruce_log",8,512,256);
            catalog(out,"minecraft:birch_log",8,512,256);
            catalog(out,"minecraft:cobblestone",2,1024,512);
            catalog(out,"minecraft:stone",3,1024,512);
            catalog(out,"minecraft:deepslate",3,1024,512);
            catalog(out,"minecraft:glass",7,512,256);
            catalog(out,"minecraft:sand",5,512,256);
            catalog(out,"minecraft:gravel",4,512,256);
            catalog(out,"minecraft:clay_ball",7,512,256);
            catalog(out,"minecraft:bricks",12,512,256);
            catalog(out,"minecraft:torch",3,512,256);
            catalog(out,"minecraft:lantern",32,128,64);
            catalog(out,"minecraft:chain",20,256,128);
            catalog(out,"minecraft:rail",14,512,256);
            catalog(out,"minecraft:powered_rail",80,128,64);
            catalog(out,"minecraft:minecart",150,64,32);
            catalog(out,"minecraft:hopper",125,64,32);
            catalog(out,"minecraft:chest",20,256,128);
            catalog(out,"minecraft:barrel",25,256,128);
            catalog(out,"minecraft:furnace",25,128,64);
            catalog(out,"minecraft:anvil",750,16,8);
            catalog(out,"minecraft:diamond_pickaxe",1200,16,8);
            catalog(out,"minecraft:iron_pickaxe",110,64,32);
            catalog(out,"minecraft:bow",50,64,32);
            catalog(out,"minecraft:crossbow",110,64,32);
            catalog(out,"minecraft:arrow",4,512,256);
            catalog(out,"minecraft:shield",90,64,32);
            catalog(out,"minecraft:bread",10,512,256);
            catalog(out,"minecraft:cooked_beef",22,512,256);
            catalog(out,"minecraft:golden_carrot",35,256,128);
            catalog(out,"minecraft:apple",8,256,128);
            catalog(out,"minecraft:potato",6,384,192);
            catalog(out,"minecraft:wheat",4,512,256);
            catalog(out,"minecraft:carrot",6,384,192);
            catalog(out,"minecraft:bone_meal",5,512,256);
            catalog(out,"minecraft:oak_sapling",12,128,64);
            catalog(out,"minecraft:elytra",12000,2,0);
            catalog(out,"minecraft:shulker_shell",450,16,4);
            catalog(out,"minecraft:nether_star",8000,1,0);
            catalog(out,"create:andesite_alloy",30,256,128);
            catalog(out,"create:brass_ingot",65,256,128);
            catalog(out,"create:brass_sheet",110,256,128);
            catalog(out,"create:iron_sheet",36,256,128);
            catalog(out,"create:precision_mechanism",650,32,16);
            catalog(out,"create:mechanical_press",900,16,8);
            catalog(out,"create:mechanical_mixer",1200,16,8);
            catalog(out,"create:mechanical_drill",750,24,12);
            catalog(out,"create:mechanical_saw",700,24,12);
            catalog(out,"create:mechanical_harvester",600,24,12);
            catalog(out,"create:mechanical_bearing",550,24,12);
            catalog(out,"create:shaft",15,512,256);
            catalog(out,"create:cogwheel",15,512,256);
            catalog(out,"create:large_cogwheel",35,256,128);
            catalog(out,"create:gearbox",70,256,128);
            catalog(out,"create:encased_fan",450,48,24);
            catalog(out,"create:fluid_pipe",48,512,256);
            catalog(out,"create:mechanical_pump",500,64,32);
            catalog(out,"create:fluid_tank",220,128,64);
            catalog(out,"create:belt_connector",40,128,64);
            catalog(out,"create:depot",65,128,64);
            catalog(out,"create:basin",160,64,32);
            catalog(out,"create:brass_funnel",420,48,24);
            catalog(out,"create:brass_tunnel",650,48,24);
            catalog(out,"create:track",150,256,128);
            // Fill the remaining catalogue from actual loaded registry items. Never assume
            // that Create or another mod is installed.
            for(ResourceLocation id:BuiltInRegistries.ITEM.keySet()){
                if(out.size()>=130)break;
                String namespace=id.getNamespace();
                if(!namespace.equals("minecraft"))continue;
                String path=id.getPath();
                if(banned(path))continue;
                int price=namespace.equals("create")?70:Math.max(5,Math.abs(path.hashCode()%95)+8);
                catalog(out,id.toString(),price,128,32);
            }
            for(ResourceLocation id:BuiltInRegistries.ITEM.keySet()){
                if(out.size()>=165)break;
                if(!id.getNamespace().equals("create")||banned(id.getPath()))continue;
                catalog(out,id.toString(),70,128,32);
            }
            for(ResourceLocation id:BuiltInRegistries.ITEM.keySet()){
                if(out.size()>=200)break;
                String namespace=id.getNamespace();
                if(namespace.equals("minecraft")||namespace.equals("create")||namespace.equals("masterki")||namespace.equals("neoforge")||banned(id.getPath()))continue;
                catalog(out,id.toString(),120,64,16);
            }
            for(ResourceLocation id:BuiltInRegistries.ITEM.keySet()){
                if(out.size()>=200)break;
                if(id.getNamespace().equals("minecraft")&&!banned(id.getPath()))catalog(out,id.toString(),28,128,32);
            }
            offers=List.copyOf(out.values());
        }
        return offers;
    }
    private static boolean banned(String p){
        return p.contains("spawn_egg")||p.contains("command_block")||p.contains("barrier")
            ||p.contains("structure")||p.contains("jigsaw")||p.contains("debug")
            ||p.contains("air")||p.contains("light")||p.contains("bedrock")
            ||p.contains("knowledge_book")||p.contains("potion")
            ||p.contains("shulker_box")||p.contains("creative")||p.contains("operator")||p.contains("debug_stick");
    }
    private static void catalog(Map<String,Offer> out,String id,int price,int cap,int refill){
        ResourceLocation key=ResourceLocation.tryParse(id);
        if(key==null||!BuiltInRegistries.ITEM.containsKey(key))return;
        Item item=BuiltInRegistries.ITEM.get(key);
        if(item==Items.AIR||new ItemStack(item).isEmpty())return;
        String type;
        String p=key.getPath();
        if(key.getNamespace().equals("create"))type="Create";
        else if(!key.getNamespace().equals("minecraft"))type="Другие моды";
        else if(p.contains("sword")||p.contains("pickaxe")||p.contains("axe")||p.contains("bow")||p.contains("shield"))type="Инструменты";
        else if(p.contains("cooked")||p.contains("bread")||p.contains("seed")||p.contains("wheat")||p.contains("carrot")||p.contains("apple")||p.contains("potato"))type="Еда и фермы";
        else if(p.contains("log")||p.contains("plank")||p.contains("stone")||p.contains("glass")||p.contains("brick")||p.contains("door"))type="Строительство";
        else if(price>=2000||p.contains("netherite")||p.contains("diamond"))type="Редкости";
        else type="Ресурсы";
        out.putIfAbsent(id,new Offer(id,type,price,Math.max(1,price/3),cap,refill));
    }
    public static List<Contract> contracts(){
        ArrayList<Contract> result=new ArrayList<>();
        int n=0;
        for(Offer offer:offers()){
            if(n++>=40)break;
            int quantity=offer.buy()<40?64:offer.buy()<300?32:8;
            long reward=Math.max(50,offer.sell()*quantity*120/100);
            result.add(new Contract("c"+n,offer.id(),quantity,reward));
        }
        return result;
    }

    private void tick(ServerTickEvent.Post e){
        if(++tickCounter%1200!=0)return;
        Saved s=Saved.of(e.getServer().overworld());
        s.restockIfDue();
    }
    private void joined(PlayerEvent.PlayerLoggedInEvent e){
        if(e.getEntity() instanceof ServerPlayer p) sync(p,false,"");
    }
    private void commands(RegisterCommandsEvent event){
        var d=event.getDispatcher();
        d.register(Commands.literal("shop").executes(c -> { sync(c.getSource().getPlayerOrException(),true,"");return 1;}));
        d.register(Commands.literal("contracts").executes(c -> { sync(c.getSource().getPlayerOrException(),true,"contracts");return 1;}));
        d.register(Commands.literal("money")
            .executes(c -> {ServerPlayer p=c.getSource().getPlayerOrException();tell(p,"Баланс: "+Saved.of(p.serverLevel()).balance(p.getUUID())+" М",ChatFormatting.GOLD);return 1;})
            .then(Commands.literal("pay")
                .then(Commands.argument("player",EntityArgument.player())
                    .then(Commands.argument("amount",LongArgumentType.longArg(1))
                        .executes(c -> pay(c.getSource().getPlayerOrException(),
                                EntityArgument.getPlayer(c,"player"),LongArgumentType.getLong(c,"amount"))))))
            .then(Commands.literal("top").executes(c -> top(c.getSource().getPlayerOrException()))));
        d.register(Commands.literal("masterki")
          .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
            .then(Commands.literal("restock").executes(c->{
                Saved.of(c.getSource().getLevel()).restockNow();
                c.getSource().sendSuccess(()->Component.literal("Masterki: поставка выполнена"),false);return 1;
            }))
            .then(Commands.literal("money")
                .then(Commands.literal("add")
                    .then(Commands.argument("player",EntityArgument.player())
                      .then(Commands.argument("amount",LongArgumentType.longArg(1))
                        .executes(c->adminMoney(c.getSource().getLevel(),EntityArgument.getPlayer(c,"player"),LongArgumentType.getLong(c,"amount"),false)))))
                .then(Commands.literal("set")
                    .then(Commands.argument("player",EntityArgument.player())
                      .then(Commands.argument("amount",LongArgumentType.longArg(0))
                        .executes(c->adminMoney(c.getSource().getLevel(),EntityArgument.getPlayer(c,"player"),LongArgumentType.getLong(c,"amount"),true))))))));
    }
    private static int adminMoney(ServerLevel level,ServerPlayer p,long value,boolean set){
        Saved s=Saved.of(level);s.setBalance(p.getUUID(),set?value:Math.addExact(s.balance(p.getUUID()),value));
        sync(p,false,"");return 1;
    }
    private static int pay(ServerPlayer from,ServerPlayer to,long amount){
        if(from.getUUID().equals(to.getUUID()))return 0;
        Saved s=Saved.of(from.serverLevel());
        if(amount<=0||s.balance(from.getUUID())<amount){tell(from,"Недостаточно мастерков.",ChatFormatting.RED);return 0;}
        if(s.balance(to.getUUID())>Long.MAX_VALUE-amount){tell(from,"Баланс получателя переполнен.",ChatFormatting.RED);return 0;}
        s.setBalance(from.getUUID(),s.balance(from.getUUID())-amount);
        s.setBalance(to.getUUID(),s.balance(to.getUUID())+amount);
        s.log(from.getUUID(),"-"+amount+" М → "+to.getGameProfile().getName());
        s.log(to.getUUID(),"+"+amount+" М ← "+from.getGameProfile().getName());
        tell(from,"Переведено "+amount+" М",ChatFormatting.GREEN);tell(to,"Получено "+amount+" М",ChatFormatting.GREEN);
        sync(from,false,"");sync(to,false,"");return 1;
    }
    private static int top(ServerPlayer player){
        Saved s=Saved.of(player.serverLevel());
        player.sendSystemMessage(Component.literal("Топ игроков по мастеркам:").withStyle(ChatFormatting.GOLD));
        s.balances.entrySet().stream().sorted(Map.Entry.<UUID,Long>comparingByValue().reversed()).limit(10)
            .forEach(e -> {
                ServerPlayer online=player.server.getPlayerList().getPlayer(e.getKey());
                String name=online==null?e.getKey().toString().substring(0,8):online.getGameProfile().getName();
                player.sendSystemMessage(Component.literal(name+" — "+e.getValue()+" М"));
            });return 1;
    }
    public static void sync(ServerPlayer p,boolean open,String page){
        Saved data=Saved.of(p.serverLevel());
        data.restockIfDue();
        List<MasterkiNetwork.Product> products=new ArrayList<>();
        for(Offer offer:offers())products.add(new MasterkiNetwork.Product(offer.id,offer.category,offer.buy,offer.sell,data.stock(offer),offer.daily,offer.cap));
        List<MasterkiNetwork.Task> tasks=new ArrayList<>();
        for(Contract c:contracts())tasks.add(new MasterkiNetwork.Task(c.id,c.item,c.amount,c.reward));
        PacketDistributor.sendToPlayer(p,new MasterkiNetwork.Snapshot(open,page,data.balance(p.getUUID()),data.nextRestock,
            products,tasks,List.copyOf(data.logs(p.getUUID()))));
    }
    public static void action(ServerPlayer player,MasterkiNetwork.Action req){
        Saved data=Saved.of(player.serverLevel());
        data.restockIfDue();
        int qty=req.count();
        if("top".equals(req.kind())){top(player);return;}
        if("help".equals(req.kind())){
            tell(player,"Команды: /shop, /money, /money pay, /money top, /contracts",ChatFormatting.YELLOW);return;
        }
        if(qty<=0||qty>4096){tell(player,"Недопустимое количество.",ChatFormatting.RED);return;}
        Offer offer=offers().stream().filter(o->o.id.equals(req.id())).findFirst().orElse(null);
        if("contract".equals(req.kind())){
            Contract contract=contracts().stream().filter(c->c.id.equals(req.id())).findFirst().orElse(null);
            if(contract==null){sync(player,true,"contracts");return;}
            long day=System.currentTimeMillis()/DAY_MS;
            if(data.contractsCompleted(player.getUUID(),day)>=5){tell(player,"Лимит контрактов на сегодня: 5.",ChatFormatting.RED);sync(player,true,"contracts");return;}
            Item item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(contract.item()));
            if(item==Items.AIR||itemCount(player,new ItemStack(item))<contract.amount()){tell(player,"Не хватает ресурсов для контракта.",ChatFormatting.RED);sync(player,true,"contracts");return;}
            if(data.balance(player.getUUID())>Long.MAX_VALUE-contract.reward()){sync(player,true,"contracts");return;}
            take(player,new ItemStack(item),contract.amount());
            data.setBalance(player.getUUID(),data.balance(player.getUUID())+contract.reward());
            data.completeContract(player.getUUID(),day);
            data.log(player.getUUID(),"Контракт: +"+contract.reward()+" М");
            tell(player,"Контракт выполнен: +"+contract.reward()+" М",ChatFormatting.GREEN);
            sync(player,true,"contracts");return;
        }
        if(offer==null){tell(player,"Товар отсутствует.",ChatFormatting.RED);sync(player,true,"");return;}
        Item item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(offer.id));
        if(item==Items.AIR){sync(player,true,"");return;}
        ItemStack template=new ItemStack(item);
        if("buy".equals(req.kind())){
            long price=total(offer.buy,qty);
            if(price<0||data.balance(player.getUUID())<price){tell(player,"Недостаточно мастерков.",ChatFormatting.RED);sync(player,true,"");return;}
            if(data.stock(offer)<qty){tell(player,"Товара недостаточно на складе.",ChatFormatting.RED);sync(player,true,"");return;}
            if(room(player,template)<qty){tell(player,"Недостаточно места в инвентаре.",ChatFormatting.RED);sync(player,true,"");return;}
            data.setBalance(player.getUUID(),data.balance(player.getUUID())-price);
            data.adjustStock(offer,-qty);
            for(int left=qty;left>0;){
                int count=Math.min(left,template.getMaxStackSize());
                ItemStack stack=template.copy();stack.setCount(count);
                if(!player.getInventory().add(stack))player.drop(stack,false);
                left-=count;
            }
            data.log(player.getUUID(),"Покупка "+offer.id+" x"+qty+" (-"+price+" М)");
            tell(player,"Покупка выполнена: -"+price+" М",ChatFormatting.GREEN);
        } else if("sell".equals(req.kind())){
            if(player.getAbilities().instabuild){tell(player,"Продажа в творческом режиме отключена.",ChatFormatting.RED);sync(player,true,"");return;}
            if(itemCount(player,template)<qty){tell(player,"Недостаточно предметов без дополнительных компонентов.",ChatFormatting.RED);sync(player,true,"");return;}
            if(!data.canSell(offer,qty)){tell(player,"Дневной лимит выкупа этого товара исчерпан.",ChatFormatting.RED);sync(player,true,"");return;}
            long earned=safeMultiply(offer.sell,qty);
            if(data.balance(player.getUUID())>Long.MAX_VALUE-earned){sync(player,true,"");return;}
            take(player,template,qty);
            data.countSell(offer,qty);
            data.setBalance(player.getUUID(),data.balance(player.getUUID())+earned);
            data.adjustStock(offer,qty);
            data.log(player.getUUID(),"Продажа "+offer.id+" x"+qty+" (+"+earned+" М)");
            tell(player,"Продано: +"+earned+" М",ChatFormatting.GREEN);
        }
        sync(player,true,"");
    }
    private static long safeMultiply(long a,int count){
        if(a>Long.MAX_VALUE/count)return -1;return a*count;
    }
    private static long total(long unit,int qty){
        long raw=safeMultiply(unit,qty);if(raw<0)return -1;
        int percent=qty>=256?85:qty>=64?90:qty>=16?95:100;
        return (raw/100)*percent+(raw%100)*percent/100;
    }
    private static int room(ServerPlayer player,ItemStack template){
        int capacity=0,max=template.getMaxStackSize();
        for(int i=0;i<36;i++){
            ItemStack stack=player.getInventory().getItem(i);
            if(stack.isEmpty())capacity+=max;
            else if(ItemStack.isSameItemSameComponents(stack,template))
                capacity+=Math.max(0,Math.min(max,stack.getMaxStackSize())-stack.getCount());
        }
        return capacity;
    }
    private static int itemCount(ServerPlayer player,ItemStack template){
        int count=0;
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            ItemStack stack=player.getInventory().getItem(i);
            if(ItemStack.isSameItemSameComponents(stack,template))count+=stack.getCount();
        }
        return count;
    }
    private static void take(ServerPlayer player,ItemStack template,int qty){
        int left=qty;
        for(int i=0;i<player.getInventory().getContainerSize()&&left>0;i++){
            ItemStack stack=player.getInventory().getItem(i);
            if(!ItemStack.isSameItemSameComponents(stack,template))continue;
            int sub=Math.min(stack.getCount(),left);
            stack.shrink(sub);left-=sub;
        }
        player.getInventory().setChanged();
    }
    public static void tell(ServerPlayer p,String msg,ChatFormatting col){
        p.sendSystemMessage(Component.literal("[Мастерки] "+msg).withStyle(col));
    }

    public static final class Saved extends SavedData {
        final Map<UUID,Long> balances=new HashMap<>();
        final Map<String,Integer> stock=new HashMap<>();
        final Map<UUID,List<String>> history=new HashMap<>();
        final Map<UUID,Integer> tasksCount=new HashMap<>();
        final Map<UUID,Long> taskDay=new HashMap<>();
        final Map<String,Integer> soldToday=new HashMap<>();
        long buybackDay=System.currentTimeMillis()/DAY_MS;
        long nextRestock=System.currentTimeMillis()+DAY_MS;
        static Saved of(ServerLevel level){
            return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(Saved::new,Saved::load),"masterki_economy");
        }
        long balance(UUID id){
            if(!balances.containsKey(id)){balances.put(id,300L);setDirty();}
            return balances.get(id);
        }
        void setBalance(UUID id,long money){balances.put(id,Math.max(0,money));setDirty();}
        int stock(Offer offer){return stock.getOrDefault(offer.id,offer.cap);}
        void adjustStock(Offer offer,int change){
            stock.put(offer.id,Math.max(0,Math.min(offer.cap,stock(offer)+change)));setDirty();
        }
        List<String> logs(UUID uuid){return history.getOrDefault(uuid,List.of());}
        void log(UUID uuid,String line){
            List<String> logs=history.computeIfAbsent(uuid,k->new ArrayList<>());
            logs.add(0,line);
            if(logs.size()>50)logs.remove(logs.size()-1);
            setDirty();
        }
        void restockIfDue(){
            if(System.currentTimeMillis()<nextRestock)return;
            restockNow();
        }
        void restockNow(){
            for(Offer o:offers()){
                int old=stock(o), count=Math.min(o.cap,Math.max(0,old+o.daily));
                stock.put(o.id,count);
            }
            nextRestock=System.currentTimeMillis()+DAY_MS;
            setDirty();
        }
        private void updateBuybackDay(){
            long today=System.currentTimeMillis()/DAY_MS;
            if(buybackDay!=today){buybackDay=today;soldToday.clear();setDirty();}
        }
        boolean canSell(Offer offer,int qty){
            updateBuybackDay();
            int cap=offer.daily()>0?offer.daily()*2:8;
            return (long)soldToday.getOrDefault(offer.id(),0)+qty<=cap;
        }
        void countSell(Offer offer,int qty){
            updateBuybackDay();
            soldToday.merge(offer.id(),qty,Integer::sum);setDirty();
        }
        int contractsCompleted(UUID id,long today){
            return taskDay.getOrDefault(id,-1L)==today?tasksCount.getOrDefault(id,0):0;
        }
        void completeContract(UUID id,long today){
            if(taskDay.getOrDefault(id,-1L)!=today){taskDay.put(id,today);tasksCount.put(id,0);}
            tasksCount.merge(id,1,Integer::sum);setDirty();
        }
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
            tag.putLong("NextRestock",nextRestock);
            tag.putLong("BuybackDay",buybackDay);
            CompoundTag buybacks=new CompoundTag();
            soldToday.forEach(buybacks::putInt);
            tag.put("Buybacks",buybacks);
            ListTag users=new ListTag();
            for(var e:balances.entrySet()){
                CompoundTag u=new CompoundTag();u.putUUID("UUID",e.getKey());u.putLong("Balance",e.getValue());
                u.putInt("Tasks",tasksCount.getOrDefault(e.getKey(),0));
                u.putLong("TaskDay",taskDay.getOrDefault(e.getKey(),-1L));
                ListTag lines=new ListTag();
                for(String line:logs(e.getKey())){CompoundTag l=new CompoundTag();l.putString("Text",line);lines.add(l);}
                u.put("History",lines);users.add(u);
            }
            tag.put("Users",users);
            CompoundTag stocks=new CompoundTag();stock.forEach(stocks::putInt);tag.put("Stocks",stocks);
            return tag;
        }
        static Saved load(CompoundTag tag,HolderLookup.Provider registries){
            Saved saved=new Saved();
            saved.nextRestock=tag.getLong("NextRestock");
            saved.buybackDay=tag.getLong("BuybackDay");
            CompoundTag savedBuybacks=tag.getCompound("Buybacks");
            for(String id:savedBuybacks.getAllKeys())saved.soldToday.put(id,savedBuybacks.getInt(id));
            if(saved.nextRestock==0)saved.nextRestock=System.currentTimeMillis()+DAY_MS;
            ListTag users=tag.getList("Users",Tag.TAG_COMPOUND);
            for(int i=0;i<users.size();i++){
                CompoundTag u=users.getCompound(i);
                if(!u.hasUUID("UUID"))continue;
                UUID uuid=u.getUUID("UUID");
                saved.balances.put(uuid,u.getLong("Balance"));
                saved.tasksCount.put(uuid,u.getInt("Tasks"));
                saved.taskDay.put(uuid,u.getLong("TaskDay"));
                List<String> logs=new ArrayList<>();
                ListTag lines=u.getList("History",Tag.TAG_COMPOUND);
                for(int n=0;n<lines.size();n++)logs.add(lines.getCompound(n).getString("Text"));
                saved.history.put(uuid,logs);
            }
            CompoundTag stocks=tag.getCompound("Stocks");
            for(String id:stocks.getAllKeys())saved.stock.put(id,stocks.getInt(id));
            return saved;
        }
    }
}
