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
            MasterkiCatalogPolicy.reload();
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
            // Unlimited catalogue: automatically include allowed items from ALL loaded mods.
            // Deterministic ordering prevents previous item IDs from changing between restarts.
            List<ResourceLocation> registered=new ArrayList<>(BuiltInRegistries.ITEM.keySet());
            registered.sort(Comparator.comparing(ResourceLocation::toString));
            for(ResourceLocation id:registered) {
                if(out.containsKey(id.toString())||!MasterkiCatalogPolicy.allowed(id))continue;
                Item item=BuiltInRegistries.ITEM.get(id);
                int price=MasterkiCatalogPolicy.approximateBuy(id,item);
                int stock=price>=1500?8:price>=500?24:price>=150?64:price>=50?128:512;
                int daily=Math.max(1,stock/2);
                catalog(out,id.toString(),price,stock,daily);
            }
            offers=MasterkiConfig.loadOrCreate(List.copyOf(out.values()));
        }
        return offers;
    }
    private static void catalog(Map<String,Offer> out,String id,int price,int cap,int refill){
        ResourceLocation key=ResourceLocation.tryParse(id);
        if(!MasterkiCatalogPolicy.allowed(key))return;
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
        List<Offer> selected=new ArrayList<>();
        for(Offer offer:offers()){
            if(offer.category().equals("Create")&&selected.size()<16)selected.add(offer);
        }
        for(Offer offer:offers()){
            if(selected.size()>=Math.min(120,offers().size()))break;
            if(!selected.contains(offer))selected.add(offer);
        }
        int n=0;
        for(Offer offer:selected){
            int quantity=offer.buy()<40?64:offer.buy()<300?32:8;
            long reward=Math.max(50,offer.sell()*quantity*120/100);
            result.add(new Contract("c"+(++n),offer.id(),quantity,reward));
        }
        return result;
    }

    /** Three small daily tasks, rotated against the actual date rather than Minecraft ticks. */
    public static List<Contract> dailyTasks() {
        List<Contract> available=contracts();
        if(available.isEmpty())return List.of();
        int offset=(int)Math.floorMod(MasterkiSchedule.cycleDay(System.currentTimeMillis()),available.size());
        List<Contract> daily=new ArrayList<>();
        for(int i=0;i<Math.min(3,available.size());i++){
            Contract contract=available.get((offset+i*7)%available.size());
            daily.add(new Contract("daily:"+contract.id(),contract.item(),
                Math.max(4,contract.amount()/4),Math.max(30,contract.reward()/4)));
        }
        return daily;
    }

    private void tick(ServerTickEvent.Post e){
        if(++tickCounter%1200!=0)return;
        Saved s=Saved.of(e.getServer().overworld());
        s.restockIfDue();
        MasterkiContracts.of(e.getServer().overworld()).tick(System.currentTimeMillis(),e.getServer().overworld());
        MasterkiOpRecovery.backupIfSafe(e.getServer());
    }
    private void joined(PlayerEvent.PlayerLoggedInEvent e){
        if(e.getEntity() instanceof ServerPlayer p) sync(p,false,"");
    }
    private void commands(RegisterCommandsEvent event){
        var d=event.getDispatcher();
        d.register(Commands.literal("shop")
            .executes(c -> {sync(c.getSource().getPlayerOrException(),true,"");return 1;})
            .then(Commands.literal("history")
                .executes(c -> {sync(c.getSource().getPlayerOrException(),true,"history");return 1;})));
        d.register(Commands.literal("contracts").executes(c -> { sync(c.getSource().getPlayerOrException(),true,"contracts");return 1;}));
        d.register(Commands.literal("hunt").executes(c -> {
            ServerPlayer player=c.getSource().getPlayerOrException();
            Saved data=Saved.of(player.serverLevel());
            long now=System.currentTimeMillis(),day=MasterkiSchedule.cycleDay(now);
            long seconds=(MasterkiSchedule.nextCutoff(now)-now)/1000;
            tell(player,"Охота: обычные мобы "+data.huntCount(player.getUUID(),day)+"/12, "+
                "боссы "+data.bossHuntCount(player.getUUID(),day)+"/2. "+
                "Обновление через "+String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",
                    seconds/3600,seconds/60%60,seconds%60)+".",ChatFormatting.GOLD);
            return 1;
        }));
        d.register(Commands.literal("money")
            .executes(c -> {ServerPlayer p=c.getSource().getPlayerOrException();tell(p,"Баланс: "+Saved.of(p.serverLevel()).balance(p.getUUID())+" М",ChatFormatting.GOLD);return 1;})
            .then(Commands.literal("top").executes(c -> top(c.getSource().getPlayerOrException()))));
        d.register(Commands.literal("pay")
            .then(Commands.argument("player",EntityArgument.player())
                .then(Commands.argument("amount",LongArgumentType.longArg(1))
                    .executes(c -> pay(c.getSource().getPlayerOrException(),
                        EntityArgument.getPlayer(c,"player"),LongArgumentType.getLong(c,"amount"))))));
        d.register(Commands.literal("masterki")
          .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
            .then(Commands.literal("restock").executes(c->{
                Saved.of(c.getSource().getLevel()).restockNow();
                c.getSource().sendSuccess(()->Component.literal("Masterki: поставка выполнена"),false);return 1;
            }))
            .then(Commands.literal("reload").executes(c->{
                MasterkiSchedule.reload();
                offers=null;
                int size=offers().size();
                c.getSource().sendSuccess(()->Component.literal("Masterki: загружено "+size+" товаров"),false);
                return 1;
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
        MasterkiLayout.register(d);
        MasterkiContracts.register(d);
        MasterkiOpRecovery.register(d);
    }
    private static int adminMoney(ServerLevel level,ServerPlayer p,long value,boolean set){
        Saved s=Saved.of(level);s.setBalance(p.getUUID(),set?value:Math.addExact(s.balance(p.getUUID()),value));
        sync(p,false,"");return 1;
    }
    private static int pay(ServerPlayer from,ServerPlayer to,long amount){
        if(from.getUUID().equals(to.getUUID())){tell(from,"Нельзя переводить мастерки самому себе.",ChatFormatting.RED);return 0;}
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
        List<MasterkiNetwork.Task> tasks=new ArrayList<>();
        long now=System.currentTimeMillis();
        List<String> dailyDone=new ArrayList<>();
        for(Contract c:dailyTasks()){
            tasks.add(new MasterkiNetwork.Task(c.id,c.item,c.amount,c.reward));
            if(!data.canCompleteDaily(p.getUUID(),MasterkiSchedule.cycleDay(now),c.id()))dailyDone.add(c.id());
        }
        MasterkiContracts rotation=MasterkiContracts.of(p.serverLevel());
        PacketDistributor.sendToPlayer(p,new MasterkiNetwork.Snapshot(open,page,data.balance(p.getUUID()),data.nextRestock,
            List.of(),tasks,List.copyOf(data.logs(p.getUUID())),MasterkiLayout.of(p.serverLevel()).entries(),
            rotation.nextRotation(p.getUUID(),now),MasterkiSchedule.nextCutoff(now),
            rotation.visible(p.getUUID(),now),dailyDone,MasterkiSchedule.zone().getId()));
        if(open)sendCatalog(p,data);
    }

    private static void sendCatalog(ServerPlayer player,Saved data){
        // 96 entries per packet, with the final packet committing the rebuilt list.
        List<Offer> all=offers();
        if(all.isEmpty()){
            PacketDistributor.sendToPlayer(player,new MasterkiNetwork.CatalogChunk(true,true,List.of()));
            return;
        }
        for(int start=0;start<all.size();start+=96){
            int end=Math.min(start+96,all.size());
            List<MasterkiNetwork.Product> slice=new ArrayList<>(end-start);
            for(int i=start;i<end;i++){
                Offer o=all.get(i);
                slice.add(new MasterkiNetwork.Product(o.id,o.category,o.buy,o.sell,
                    data.stock(o),o.daily,o.cap));
            }
            PacketDistributor.sendToPlayer(player,
                new MasterkiNetwork.CatalogChunk(start==0,end==all.size(),slice));
        }
    }
    public static void action(ServerPlayer player,MasterkiNetwork.Action req){
        Saved data=Saved.of(player.serverLevel());
        data.restockIfDue();
        int qty=req.count();
        if(MasterkiLayout.editorAction(player,req.kind(),req.id(),req.count()))return;
        if("open".equals(req.kind())){sync(player,true,"");return;}
        if(req.kind().startsWith("contract_")){
            MasterkiContracts.of(player.serverLevel()).action(player,req.kind().substring(9),req.id());
            sync(player,true,"contracts");return;
        }
        if("top".equals(req.kind())){top(player);return;}
        if("help".equals(req.kind())){
            tell(player,"Команды: /shop, /money, /pay, /money top, /contracts",ChatFormatting.YELLOW);return;
        }
        if(qty<=0||qty>4096){tell(player,"Недопустимое количество.",ChatFormatting.RED);return;}
        if(player.getAbilities().instabuild &&
           (req.kind().equals("sell")||req.kind().equals("daily"))){
            tell(player,"В творческом режиме получать мастерки через магазин нельзя.",ChatFormatting.RED);
            return;
        }
        Offer offer=offers().stream().filter(o->o.id.equals(req.id())).findFirst().orElse(null);
        if("daily".equals(req.kind())){
            Contract task=dailyTasks().stream().filter(c->c.id().equals(req.id())).findFirst().orElse(null);
            if(task==null){tell(player,"Это задание уже недоступно.",ChatFormatting.RED);sync(player,true,"daily");return;}
            long day=MasterkiSchedule.cycleDay(System.currentTimeMillis());
            if(!data.canCompleteDaily(player.getUUID(),day,task.id())){
                tell(player,"Сегодня это задание уже выполнено.",ChatFormatting.YELLOW);
                sync(player,true,"daily");return;
            }
            Item item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(task.item()));
            if(item==Items.AIR||itemCount(player,new ItemStack(item))<task.amount()){
                tell(player,"Недостаточно предметов для задания.",ChatFormatting.RED);
                sync(player,true,"daily");return;
            }
            if(data.balance(player.getUUID())>Long.MAX_VALUE-task.reward()){sync(player,true,"daily");return;}
            take(player,new ItemStack(item),task.amount());
            data.completeDaily(player.getUUID(),day,task.id());
            data.setBalance(player.getUUID(),data.balance(player.getUUID())+task.reward());
            data.log(player.getUUID(),"Ежедневное задание: +"+task.reward()+" М");
            tell(player,"Задание выполнено: +"+task.reward()+" М",ChatFormatting.GREEN);
            sync(player,true,"daily");return;
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
            if(!data.canSell(player.getUUID(),offer,qty,player.serverLevel())){tell(player,"Личный лимит выкупа этого товара на игровой день исчерпан.",ChatFormatting.RED);sync(player,true,"");return;}
            long earned=safeMultiply(offer.sell,qty);
            if(data.balance(player.getUUID())>Long.MAX_VALUE-earned){sync(player,true,"");return;}
            take(player,template,qty);
            data.countSell(player.getUUID(),offer,qty,player.serverLevel());
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
        for(int i=0;i<36;i++){
            ItemStack stack=player.getInventory().getItem(i);
            if(ItemStack.isSameItemSameComponents(stack,template))count+=stack.getCount();
        }
        return count;
    }
    private static void take(ServerPlayer player,ItemStack template,int qty){
        int left=qty;
        for(int i=0;i<36&&left>0;i++){
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
        final Map<UUID,java.util.Set<String>> discoveries=new HashMap<>();
        final Map<UUID,Integer> huntedToday=new HashMap<>();
        final Map<UUID,Long> huntDay=new HashMap<>();
        final Map<UUID,Integer> bossesToday=new HashMap<>();
        final Map<UUID,Long> bossDay=new HashMap<>();
        final Map<UUID,Long> dailyDay=new HashMap<>();
        final Map<UUID,java.util.Set<String>> dailyCompleted=new HashMap<>();
        // Per-player, per-item buyback counts for the current Overworld calendar day.
        // The older shared real-world-day counters cannot be attributed to individual
        // players, so they are intentionally not migrated from v1.0.7.
        final Map<UUID,Map<String,Integer>> soldByPlayer=new HashMap<>();
        long buybackGameDay=Long.MIN_VALUE;
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
        boolean firstDiscovery(UUID uuid,String identity){
            boolean first=discoveries.computeIfAbsent(uuid,k->new java.util.HashSet<>()).add(identity);
            if(first)setDirty();
            return first;
        }
        int huntCount(UUID uuid,long today){
            return huntDay.getOrDefault(uuid,-1L)==today?huntedToday.getOrDefault(uuid,0):0;
        }
        int bossHuntCount(UUID uuid,long today){
            return bossDay.getOrDefault(uuid,-1L)==today?bossesToday.getOrDefault(uuid,0):0;
        }
        boolean recordHunt(UUID uuid,long today){
            if(huntCount(uuid,today)>=12)return false;
            if(huntDay.getOrDefault(uuid,-1L)!=today){
                huntDay.put(uuid,today);
                huntedToday.put(uuid,0);
            }
            huntedToday.merge(uuid,1,Integer::sum);
            setDirty();
            return true;
        }
        boolean recordBossHunt(UUID uuid,long today){
            if(bossHuntCount(uuid,today)>=2)return false;
            if(bossDay.getOrDefault(uuid,-1L)!=today){
                bossDay.put(uuid,today);
                bossesToday.put(uuid,0);
            }
            bossesToday.merge(uuid,1,Integer::sum);
            setDirty();
            return true;
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
        private void updateBuybackDay(ServerLevel level){
            // getDayTime() advances with the Overworld day/night cycle (24000 ticks).
            // A bed skipping the night also advances this calendar-day counter.
            // Use the Overworld even when the selling player is in another dimension.
            long currentDay=Math.floorDiv(level.getServer().overworld().getDayTime(),24000L);
            if(buybackGameDay!=currentDay){
                buybackGameDay=currentDay;
                soldByPlayer.clear();
                setDirty();
            }
        }
        boolean canSell(UUID player,Offer offer,int qty,ServerLevel level){
            updateBuybackDay(level);
            long cap=offer.daily()>0?2L*offer.daily():8L;
            int sold=soldByPlayer.getOrDefault(player,Map.of()).getOrDefault(offer.id(),0);
            return qty>0 && (long)sold+qty<=cap;
        }
        void countSell(UUID player,Offer offer,int qty,ServerLevel level){
            updateBuybackDay(level);
            soldByPlayer.computeIfAbsent(player,k->new HashMap<>())
                .merge(offer.id(),qty,(before,added)->
                    (int)Math.min(Integer.MAX_VALUE,(long)before+added));
            setDirty();
        }
        boolean canCompleteDaily(UUID id,long day,String task){
            return dailyDay.getOrDefault(id,-1L)!=day ||
                !dailyCompleted.getOrDefault(id,java.util.Set.of()).contains(task);
        }
        void completeDaily(UUID id,long day,String task){
            if(dailyDay.getOrDefault(id,-1L)!=day){
                dailyDay.put(id,day);
                dailyCompleted.put(id,new java.util.HashSet<>());
            }
            dailyCompleted.computeIfAbsent(id,k->new java.util.HashSet<>()).add(task);
            setDirty();
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
            tag.putLong("BuybackGameDay",buybackGameDay);
            ListTag buybacks=new ListTag();
            for(var entry:soldByPlayer.entrySet()){
                CompoundTag user=new CompoundTag();
                user.putUUID("UUID",entry.getKey());
                CompoundTag items=new CompoundTag();
                entry.getValue().forEach(items::putInt);
                user.put("Items",items);
                buybacks.add(user);
            }
            tag.put("BuybackUsers",buybacks);
            ListTag users=new ListTag();
            for(var e:balances.entrySet()){
                CompoundTag u=new CompoundTag();u.putUUID("UUID",e.getKey());u.putLong("Balance",e.getValue());
                u.putInt("Tasks",tasksCount.getOrDefault(e.getKey(),0));
                u.putLong("TaskDay",taskDay.getOrDefault(e.getKey(),-1L));
                u.putInt("Hunts",huntedToday.getOrDefault(e.getKey(),0));
                u.putLong("HuntDay",huntDay.getOrDefault(e.getKey(),-1L));
                u.putInt("BossHunts",bossesToday.getOrDefault(e.getKey(),0));
                u.putLong("BossHuntDay",bossDay.getOrDefault(e.getKey(),-1L));
                u.putLong("DailyDay",dailyDay.getOrDefault(e.getKey(),-1L));
                ListTag finished=new ListTag();
                for(String task:dailyCompleted.getOrDefault(e.getKey(),java.util.Set.of())){
                    CompoundTag mark=new CompoundTag();mark.putString("ID",task);finished.add(mark);
                }
                u.put("DailyDone",finished);
                ListTag found=new ListTag();
                for(String entry:discoveries.getOrDefault(e.getKey(),java.util.Set.of())){
                    CompoundTag item=new CompoundTag();item.putString("ID",entry);found.add(item);
                }
                u.put("Discoveries",found);
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
            // v1.0.7 used a single global Buybacks map and a real-world BuybackDay.
            // Those keys are ignored: a global count cannot be safely assigned to a
            // player, so every player begins the first new game-day cycle at 0.
            saved.buybackGameDay=tag.contains("BuybackGameDay",Tag.TAG_LONG)
                ?tag.getLong("BuybackGameDay"):Long.MIN_VALUE;
            ListTag savedBuybacks=tag.getList("BuybackUsers",Tag.TAG_COMPOUND);
            for(int i=0;i<savedBuybacks.size();i++){
                CompoundTag user=savedBuybacks.getCompound(i);
                if(!user.hasUUID("UUID"))continue;
                CompoundTag items=user.getCompound("Items");
                Map<String,Integer> counts=new HashMap<>();
                for(String id:items.getAllKeys()){
                    int amount=items.getInt(id);
                    if(amount>0)counts.put(id,amount);
                }
                if(!counts.isEmpty())saved.soldByPlayer.put(user.getUUID("UUID"),counts);
            }
            if(saved.nextRestock==0)saved.nextRestock=System.currentTimeMillis()+DAY_MS;
            ListTag users=tag.getList("Users",Tag.TAG_COMPOUND);
            for(int i=0;i<users.size();i++){
                CompoundTag u=users.getCompound(i);
                if(!u.hasUUID("UUID"))continue;
                UUID uuid=u.getUUID("UUID");
                saved.balances.put(uuid,u.getLong("Balance"));
                saved.tasksCount.put(uuid,u.getInt("Tasks"));
                saved.taskDay.put(uuid,u.getLong("TaskDay"));
                saved.huntedToday.put(uuid,u.getInt("Hunts"));
                saved.huntDay.put(uuid,u.getLong("HuntDay"));
                saved.bossesToday.put(uuid,u.getInt("BossHunts"));
                saved.bossDay.put(uuid,u.contains("BossHuntDay")?u.getLong("BossHuntDay"):-1L);
                saved.dailyDay.put(uuid,u.getLong("DailyDay"));
                ListTag dailyDone=u.getList("DailyDone",Tag.TAG_COMPOUND);
                java.util.Set<String> done=new java.util.HashSet<>();
                for(int k=0;k<dailyDone.size();k++)done.add(dailyDone.getCompound(k).getString("ID"));
                saved.dailyCompleted.put(uuid,done);
                ListTag found=u.getList("Discoveries",Tag.TAG_COMPOUND);
                java.util.Set<String> discovered=new java.util.HashSet<>();
                for(int k=0;k<found.size();k++)discovered.add(found.getCompound(k).getString("ID"));
                saved.discoveries.put(uuid,discovered);
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
