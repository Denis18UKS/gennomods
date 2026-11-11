package dev.masterki.economy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent, per-player contracts. Authoritative on the server; no client-supplied rewards. */
public final class MasterkiContracts extends SavedData {
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24L*HOUR;
    private static final int MAX_LOG = 64;
    private final Map<UUID, Board> boards = new HashMap<>();
    private final Map<UUID, List<Fine>> fineHistory = new HashMap<>();
    private int rotationHours = 24;
    private int deadlineHours = 24;
    private int dailyLimit = 5;
    private int latePercent = 10;
    private int cancelPercent = 5;
    private boolean finesEnabled = true;
    private transient ServerLevel serverLevel;

    public static final class ContractEntry {
        public String key, poolId, item, state;
        public int amount;
        public long reward, dueAt, rotation;
        ContractEntry(String key,String poolId,String item,int amount,long reward,long rotation){
            this.key=key;this.poolId=poolId;this.item=item;this.amount=amount;
            this.reward=reward;this.rotation=rotation;this.state="available";
        }
        MasterkiNetwork.ContractStatus view() {
            return new MasterkiNetwork.ContractStatus(key,item,amount,reward,state,dueAt);
        }
    }
    private static final class Board {
        long nextRefresh;
        long rotation;
        List<String> previous = new ArrayList<>();
        List<ContractEntry> entries = new ArrayList<>();
    }
    private record Fine(long epoch,String cause,long amount) {}

    private MasterkiContracts() {}
    public static MasterkiContracts of(ServerLevel level){
        MasterkiContracts data=level.getServer().overworld().getDataStorage()
            .computeIfAbsent(new SavedData.Factory<>(MasterkiContracts::new,MasterkiContracts::load),"masterki_contracts");
        data.serverLevel=level.getServer().overworld();
        return data;
    }

    private long rotationDuration(){return rotationHours*HOUR;}
    private long deadlineDuration(){return deadlineHours*HOUR;}
    public long nextRotation(UUID player,long now){
        return board(player,now).nextRefresh;
    }
    public List<MasterkiNetwork.ContractStatus> visible(UUID player,long now) {
        Board b=board(player,now);
        ArrayList<MasterkiNetwork.ContractStatus> result=new ArrayList<>();
        for(ContractEntry entry:b.entries)result.add(entry.view());
        return result;
    }
    private Board board(UUID uuid,long now){
        Board b=boards.computeIfAbsent(uuid,k->new Board());
        if(b.nextRefresh==0 || now>=b.nextRefresh){
            rotate(uuid,b,now);
        }
        expire(uuid,b,now);
        return b;
    }
    private void expire(UUID uuid,Board b,long now){
        for(ContractEntry entry:b.entries){
            if("accepted".equals(entry.state)&&entry.dueAt>0&&now>=entry.dueAt){
                entry.state="overdue";
                fine(uuid,entry,"Просрочка контракта "+entry.poolId,latePercent);
                setDirty();
            }
        }
    }
    private void rotate(UUID uuid,Board board,long now){
        // Keep accepted contracts until their own deadlines, and expired contracts
        // from the last rotation so the player's status can be inspected.
        List<ContractEntry> retained=new ArrayList<>();
        for(ContractEntry entry:board.entries){
            if("accepted".equals(entry.state) ||
                ("overdue".equals(entry.state) && entry.rotation==board.rotation))retained.add(entry);
        }
        List<Masterki.Contract> pool=new ArrayList<>(Masterki.contracts());
        pool.removeIf(c->{
            ResourceLocation id=ResourceLocation.tryParse(c.item());
            return id==null || !BuiltInRegistries.ITEM.containsKey(id)
                || BuiltInRegistries.ITEM.get(id)==Items.AIR;
        });
        Collections.shuffle(pool,ThreadLocalRandom.current());
        // Keep 40 visible contracts, as in v1.0.2, but rotate them from an
        // expanded 120-contract pool. Avoid repeating previous items when possible.
        Map<String,Integer> previousVariants=new HashMap<>();
        for(String value:board.previous){
            int sep=value.lastIndexOf(':');
            if(sep>0)try {previousVariants.put(value.substring(0,sep),
                Integer.parseInt(value.substring(sep+1)));}catch(NumberFormatException ignored){}
        }
        List<Masterki.Contract> picked=new ArrayList<>();
        for(Masterki.Contract c:pool){
            if(picked.size()>=40)break;
            if(!previousVariants.containsKey(c.id()))picked.add(c);
        }
        for(Masterki.Contract c:pool){
            if(picked.size()>=40)break;
            if(picked.stream().noneMatch(p->p.id().equals(c.id())))picked.add(c);
        }
        board.rotation++;
        board.previous.clear();
        board.entries.clear();
        board.entries.addAll(retained);
        int[] variants={75,100,125,150};
        for(Masterki.Contract c:picked){
            int prior=previousVariants.getOrDefault(c.id(),-1);
            int variant=variants[ThreadLocalRandom.current().nextInt(variants.length)];
            if(variant==prior)variant=variants[(java.util.Arrays.binarySearch(variants,variant)+1)%variants.length];
            int amount=Math.max(1,(int)Math.min(4096,Math.round((double)c.amount()*variant/100.0)));
            long reward=Math.max(40,Math.min(1250,(c.reward()*variant)/100L));
            board.previous.add(c.id()+":"+variant);
            board.entries.add(new ContractEntry("r"+board.rotation+"_"+c.id(),c.id(),c.item(),
                amount,reward,board.rotation));
        }
        board.nextRefresh=now+rotationDuration();
        setDirty();
    }

    public String status(UUID uuid,String key,long now){
        for(ContractEntry e:board(uuid,now).entries)
            if(e.key.equals(key))return e.state;
        return "unknown";
    }

    public boolean action(ServerPlayer player,String verb,String key){
        UUID uuid=player.getUUID();
        long now=System.currentTimeMillis();
        Board b=board(uuid,now);
        ContractEntry e=b.entries.stream().filter(c->c.key.equals(key)).findFirst().orElse(null);
        if(e==null){Masterki.tell(player,"Контракт недоступен или уже сменился.",ChatFormatting.RED);return false;}
        Masterki.Saved economy=Masterki.Saved.of(player.serverLevel());
        if("accept".equals(verb)){
            if(!"available".equals(e.state)){Masterki.tell(player,"Контракт уже принят или закрыт.",ChatFormatting.RED);return false;}
            e.state="accepted";e.dueAt=now+deadlineDuration();setDirty();
            Masterki.tell(player,"Контракт принят. У тебя "+deadlineHours+" ч. на выполнение.",ChatFormatting.GREEN);
            return true;
        }
        if("cancel".equals(verb)){
            if(!"accepted".equals(e.state)){Masterki.tell(player,"Этот контракт нельзя отменить.",ChatFormatting.RED);return false;}
            e.state="cancelled";
            fine(uuid,e,"Отмена контракта "+e.poolId,cancelPercent);
            setDirty();
            Masterki.tell(player,"Контракт отменён.",ChatFormatting.YELLOW);return true;
        }
        if(!"complete".equals(verb))return false;
        if(!"accepted".equals(e.state)){
            Masterki.tell(player,"Сначала прими контракт, затем сдай ресурсы.",ChatFormatting.RED);return false;
        }
        if(e.dueAt<=now){
            expire(uuid,b,now);
            Masterki.tell(player,"Время выполнения контракта закончилось.",ChatFormatting.RED);return false;
        }
        long day=now/DAY;
        if(economy.contractsCompleted(uuid,day)>=dailyLimit){
            Masterki.tell(player,"Дневной лимит: "+dailyLimit+" оплачиваемых контрактов.",ChatFormatting.RED);
            return false;
        }
        if(player.getAbilities().instabuild){
            Masterki.tell(player,"В творческом режиме нельзя получать награды за контракты.",ChatFormatting.RED);
            return false;
        }
        ResourceLocation id=ResourceLocation.tryParse(e.item);
        if(id==null || !BuiltInRegistries.ITEM.containsKey(id))return false;
        Item item=BuiltInRegistries.ITEM.get(id);
        if(item==Items.AIR)return false;
        ItemStack sample=new ItemStack(item);
        if(itemCount(player,sample)<e.amount){
            Masterki.tell(player,"Недостаточно ресурсов для контракта.",ChatFormatting.RED);return false;
        }
        if(economy.balance(uuid)>Long.MAX_VALUE-e.reward)return false;
        take(player,sample,e.amount);
        e.state="completed"; // commit the state before the reward can be paid again
        economy.completeContract(uuid,day);
        economy.setBalance(uuid,economy.balance(uuid)+e.reward);
        economy.log(uuid,"Контракт "+e.poolId+": +"+e.reward+" М");
        setDirty();
        Masterki.tell(player,"Выполнено: +"+e.reward+" М",ChatFormatting.GREEN);
        return true;
    }

    private static int itemCount(ServerPlayer player,ItemStack sample){
        int count=0;
        for(int i=0;i<36;i++){
            ItemStack stack=player.getInventory().getItem(i);
            if(ItemStack.isSameItemSameComponents(stack,sample))count+=stack.getCount();
        }
        return count;
    }
    private static void take(ServerPlayer player,ItemStack sample,int amount){
        for(int i=0;i<36&&amount>0;i++){
            ItemStack stack=player.getInventory().getItem(i);
            if(!ItemStack.isSameItemSameComponents(stack,sample))continue;
            int remove=Math.min(amount,stack.getCount());
            stack.shrink(remove);amount-=remove;
        }
        player.getInventory().setChanged();
    }
    private void fine(UUID uuid,ContractEntry entry,String reason,int percentage){
        if(!finesEnabled||percentage<=0)return;
        Masterki.Saved economy=Masterki.Saved.of(serverLevel);
        // This hook is supplied by Masterki; it resolves the owning overworld storage.
        long fine=Math.max(1,(entry.reward*percentage+99)/100);
        long paid=Math.min(fine,economy.balance(uuid));
        if(paid<=0)return;
        economy.setBalance(uuid,economy.balance(uuid)-paid);
        economy.log(uuid,"Штраф: -"+paid+" М ("+reason+")");
        List<Fine> lines=fineHistory.computeIfAbsent(uuid,k->new ArrayList<>());
        lines.add(0,new Fine(System.currentTimeMillis(),reason,paid));
        if(lines.size()>MAX_LOG)lines.remove(lines.size()-1);
        setDirty();
    }
    public void tick(long now,ServerLevel overworld){
        // Resolve fines for OFFLINE players as well; the balance is persistent.
        for(var entry:boards.entrySet())expire(entry.getKey(),entry.getValue(),now);
    }
    public int reroll(UUID uuid,long now) {
        Board b=board(uuid,now);
        rotate(uuid,b,now);
        return b.entries.size();
    }
    public void reset(UUID uuid){
        boards.remove(uuid);setDirty();
    }
    private static void info(CommandSourceStack sender,String msg){
        sender.sendSuccess(()->Component.literal("[Masterki] "+msg),false);
    }
    private int list(CommandSourceStack sender,UUID uuid){
        var b=board(uuid,System.currentTimeMillis());
        info(sender,"Контрактов: "+b.entries.size()+", до обновления: "+Math.max(0,(b.nextRefresh-System.currentTimeMillis())/1000)+" с");
        for(ContractEntry c:b.entries) info(sender,c.key+" / "+c.item+" x"+c.amount+" / "+c.state+" / "+c.reward+" М");
        return 1;
    }
    private int fineList(CommandSourceStack sender,UUID uuid){
        List<Fine> f=fineHistory.getOrDefault(uuid,List.of());
        info(sender,"Штрафов: "+f.size());
        for(Fine line:f)info(sender,line.cause()+" -"+line.amount()+" М");
        return 1;
    }
    private int adminPlayer(CommandSourceStack source,UUID uuid,String cmd){
        long now=System.currentTimeMillis();
        switch(cmd){
            case "list" -> {return list(source,uuid);}
            case "reroll" -> {reroll(uuid,now);info(source,"Контракты обновлены");}
            case "reset" -> {reset(uuid);info(source,"Подборка сброшена");}
            case "timer" -> {info(source,"До обновления: "+Math.max(0,(nextRotation(uuid,now)-now)/1000)+" секунд");}
            case "fines_list" -> {return fineList(source,uuid);}
            case "fines_clear" -> {fineHistory.remove(uuid);setDirty();info(source,"История штрафов очищена без возврата средств");}
        }
        return 1;
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        var root=Commands.literal("masterki").then(Commands.literal("dev").requires(s->s.hasPermission(2))
            .then(Commands.literal("contracts")
                .then(Commands.literal("list")
                    .executes(c->of(c.getSource().getLevel()).adminPlayer(c.getSource(),c.getSource().getPlayerOrException().getUUID(),"list"))
                    .then(Commands.argument("player",EntityArgument.player())
                        .executes(c->of(c.getSource().getLevel()).adminPlayer(c.getSource(),EntityArgument.getPlayer(c,"player").getUUID(),"list"))))
                .then(Commands.literal("reroll")
                    .executes(c->of(c.getSource().getLevel()).adminPlayer(c.getSource(),c.getSource().getPlayerOrException().getUUID(),"reroll"))
                    .then(Commands.argument("player",EntityArgument.player())
                        .executes(c->of(c.getSource().getLevel()).adminPlayer(c.getSource(),EntityArgument.getPlayer(c,"player").getUUID(),"reroll"))))
                .then(Commands.literal("reset")
                    .executes(c->of(c.getSource().getLevel()).adminPlayer(c.getSource(),c.getSource().getPlayerOrException().getUUID(),"reset"))
                    .then(Commands.argument("player",EntityArgument.player())
                        .executes(c->of(c.getSource().getLevel()).adminPlayer(c.getSource(),EntityArgument.getPlayer(c,"player").getUUID(),"reset"))))
                .then(Commands.literal("timer")
                    .executes(c->of(c.getSource().getLevel()).adminPlayer(c.getSource(),c.getSource().getPlayerOrException().getUUID(),"timer"))
                    .then(Commands.argument("player",EntityArgument.player())
                        .executes(c->of(c.getSource().getLevel()).adminPlayer(c.getSource(),EntityArgument.getPlayer(c,"player").getUUID(),"timer"))))
                .then(Commands.literal("set")
                    .then(Commands.literal("rotation_hours")
                        .then(Commands.argument("hours",IntegerArgumentType.integer(1,168)).executes(c->{
                            MasterkiContracts s=of(c.getSource().getLevel());s.rotationHours=IntegerArgumentType.getInteger(c,"hours");s.setDirty();info(c.getSource(),"Интервал обновления: "+s.rotationHours+" ч.");return 1;})))
                    .then(Commands.literal("deadline_hours")
                        .then(Commands.argument("hours",IntegerArgumentType.integer(1,168)).executes(c->{
                            MasterkiContracts s=of(c.getSource().getLevel());s.deadlineHours=IntegerArgumentType.getInteger(c,"hours");s.setDirty();info(c.getSource(),"Срок выполнения: "+s.deadlineHours+" ч.");return 1;})))
                    .then(Commands.literal("daily_limit")
                        .then(Commands.argument("count",IntegerArgumentType.integer(1,20)).executes(c->{
                            MasterkiContracts s=of(c.getSource().getLevel());s.dailyLimit=IntegerArgumentType.getInteger(c,"count");s.setDirty();info(c.getSource(),"Лимит: "+s.dailyLimit);return 1;})))))
            .then(Commands.literal("fines")
                .then(Commands.literal("list")
                    .executes(c->of(c.getSource().getLevel()).adminPlayer(c.getSource(),c.getSource().getPlayerOrException().getUUID(),"fines_list"))
                    .then(Commands.argument("player",EntityArgument.player())
                        .executes(c->of(c.getSource().getLevel()).adminPlayer(c.getSource(),EntityArgument.getPlayer(c,"player").getUUID(),"fines_list"))))
                .then(Commands.literal("clear")
                    .then(Commands.argument("player",EntityArgument.player())
                        .executes(c->of(c.getSource().getLevel()).adminPlayer(c.getSource(),EntityArgument.getPlayer(c,"player").getUUID(),"fines_clear"))))
                .then(Commands.literal("enable").executes(c->{
                    MasterkiContracts s=of(c.getSource().getLevel());s.finesEnabled=true;s.setDirty();info(c.getSource(),"Штрафы включены");return 1;}))
                .then(Commands.literal("disable").executes(c->{
                    MasterkiContracts s=of(c.getSource().getLevel());s.finesEnabled=false;s.setDirty();info(c.getSource(),"Штрафы отключены");return 1;}))
                .then(Commands.literal("late_percent")
                    .then(Commands.argument("percent",IntegerArgumentType.integer(0,100)).executes(c->{
                        MasterkiContracts s=of(c.getSource().getLevel());s.latePercent=IntegerArgumentType.getInteger(c,"percent");s.setDirty();return 1;})))
                .then(Commands.literal("cancel_percent")
                    .then(Commands.argument("percent",IntegerArgumentType.integer(0,100)).executes(c->{
                        MasterkiContracts s=of(c.getSource().getLevel());s.cancelPercent=IntegerArgumentType.getInteger(c,"percent");s.setDirty();return 1;})))));
        dispatcher.register(root);
    }

    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        tag.putInt("RotationHours",rotationHours);
        tag.putInt("DeadlineHours",deadlineHours);
        tag.putInt("DailyLimit",dailyLimit);
        tag.putInt("LatePercent",latePercent);
        tag.putInt("CancelPercent",cancelPercent);
        tag.putBoolean("FinesEnabled",finesEnabled);
        ListTag players=new ListTag();
        for(var pair:boards.entrySet()){
            CompoundTag data=new CompoundTag();
            data.putUUID("UUID",pair.getKey());
            Board board=pair.getValue();
            data.putLong("Next",board.nextRefresh);data.putLong("Rotation",board.rotation);
            ListTag previous=new ListTag();
            for(String id:board.previous){CompoundTag e=new CompoundTag();e.putString("ID",id);previous.add(e);}
            data.put("Previous",previous);
            ListTag tasks=new ListTag();
            for(ContractEntry c:board.entries){
                CompoundTag n=new CompoundTag();
                n.putString("Key",c.key);n.putString("Pool",c.poolId);n.putString("Item",c.item);
                n.putString("State",c.state);n.putInt("Amount",c.amount);n.putLong("Reward",c.reward);
                n.putLong("Due",c.dueAt);n.putLong("Rotation",c.rotation);tasks.add(n);
            }
            data.put("Entries",tasks);players.add(data);
        }
        tag.put("Boards",players);
        ListTag fines=new ListTag();
        for(var pair:fineHistory.entrySet()){
            CompoundTag entry=new CompoundTag();entry.putUUID("UUID",pair.getKey());
            ListTag lines=new ListTag();
            for(Fine fine:pair.getValue()){
                CompoundTag line=new CompoundTag();
                line.putLong("Time",fine.epoch());line.putString("Cause",fine.cause());
                line.putLong("Amount",fine.amount());lines.add(line);
            }
            entry.put("Fines",lines);fines.add(entry);
        }
        tag.put("Fines",fines);
        return tag;
    }
    private static MasterkiContracts load(CompoundTag tag,HolderLookup.Provider registries){
        MasterkiContracts s=new MasterkiContracts();
        s.rotationHours=tag.contains("RotationHours")?Math.clamp(tag.getInt("RotationHours"),1,168):24;
        s.deadlineHours=tag.contains("DeadlineHours")?Math.clamp(tag.getInt("DeadlineHours"),1,168):24;
        s.dailyLimit=tag.contains("DailyLimit")?Math.clamp(tag.getInt("DailyLimit"),1,20):5;
        s.latePercent=tag.contains("LatePercent")?Math.clamp(tag.getInt("LatePercent"),0,100):10;
        s.cancelPercent=tag.contains("CancelPercent")?Math.clamp(tag.getInt("CancelPercent"),0,100):5;
        s.finesEnabled=!tag.contains("FinesEnabled")||tag.getBoolean("FinesEnabled");
        ListTag players=tag.getList("Boards",Tag.TAG_COMPOUND);
        for(int i=0;i<players.size();i++){
            CompoundTag data=players.getCompound(i);
            if(!data.hasUUID("UUID"))continue;
            Board b=new Board();b.nextRefresh=data.getLong("Next");b.rotation=data.getLong("Rotation");
            ListTag old=data.getList("Previous",Tag.TAG_COMPOUND);
            for(int j=0;j<old.size();j++)b.previous.add(old.getCompound(j).getString("ID"));
            ListTag tasks=data.getList("Entries",Tag.TAG_COMPOUND);
            for(int j=0;j<tasks.size()&&j<128;j++){
                CompoundTag t=tasks.getCompound(j);
                String key=t.getString("Key"),item=t.getString("Item");
                if(key.length()>48||item.length()>128||key.isBlank()||item.isBlank())continue;
                ContractEntry c=new ContractEntry(key,t.getString("Pool"),item,
                    Math.clamp(t.getInt("Amount"),1,4096),Math.clamp(t.getLong("Reward"),1,1000000),
                    t.getLong("Rotation"));
                c.state=t.getString("State");c.dueAt=t.getLong("Due");b.entries.add(c);
            }
            s.boards.put(data.getUUID("UUID"),b);
        }
        ListTag fines=tag.getList("Fines",Tag.TAG_COMPOUND);
        for(int i=0;i<fines.size();i++){
            CompoundTag e=fines.getCompound(i);
            if(!e.hasUUID("UUID"))continue;
            List<Fine> lines=new ArrayList<>();
            ListTag list=e.getList("Fines",Tag.TAG_COMPOUND);
            for(int j=0;j<list.size()&&j<MAX_LOG;j++){
                CompoundTag f=list.getCompound(j);
                lines.add(new Fine(f.getLong("Time"),f.getString("Cause"),f.getLong("Amount")));
            }
            s.fineHistory.put(e.getUUID("UUID"),lines);
        }
        return s;
    }
}
