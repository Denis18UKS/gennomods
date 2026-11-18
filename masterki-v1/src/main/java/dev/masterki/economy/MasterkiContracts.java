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
    private static final long TWO_HOURS = 2 * HOUR;
    private static final int MAX_LOG = 64;
    private static final int MAX_ARCHIVE = 160;
    private static final int CONTRACTS_PER_DAY = 40;
    // 15% of the whole batch minus 13% shop commission = 13.05% net.
    private static final long BONUS_NUMERATOR = 1305L;
    private static final long BONUS_DENOMINATOR = 10000L;
    private final Map<UUID, Board> boards = new HashMap<>();
    private final Map<UUID, List<Fine>> fineHistory = new HashMap<>();
    private int latePercent = 10;
    private int contractPriceVersion = 0;
    private int cancelPercent = 5;
    private boolean finesEnabled = true;
    private transient ServerLevel serverLevel;
    // Three daily tasks are generated once for the 05:00 cycle and serialized.
    // In-place repair never resets players' completed task IDs.
    private long dailyTaskCycle=Long.MIN_VALUE;
    private final List<Masterki.Contract> dailyOrders=new ArrayList<>();

    public static final class ContractEntry {
        public String key, poolId, item, state;
        public int amount;
        public long reward, dueAt, rotation;
        // Frozen at acceptance; old world contracts keep their original payout.
        public long acceptedPayout;
        public long closedAt;
        public boolean difficult;
        ContractEntry(String key,String poolId,String item,int amount,long reward,long rotation){
            this.key=key;this.poolId=poolId;this.item=item;this.amount=amount;
            this.reward=reward;this.rotation=rotation;this.state="available";
        }
        MasterkiNetwork.ContractStatus view(boolean carriedOver,long preview) {
            long shown="available".equals(state)?preview:
                acceptedPayout>0?acceptedPayout:reward;
            return new MasterkiNetwork.ContractStatus(key,item,amount,shown,state,dueAt,
                difficult,carriedOver);
        }
    }
    private static final class Board {
        long nextRefresh;
        long rotation;
        long cycleDay=Long.MIN_VALUE;
        List<String> previous = new ArrayList<>();
        List<ContractEntry> entries = new ArrayList<>();
        final Map<Long,BonusBatch> batches=new HashMap<>();
        final List<Archived> archived=new ArrayList<>();
    }
    /** Progress belongs to the original contract rotation, even after new daily offers. */
    private static final class BonusBatch {
        final long rotation;
        final long cycleDay;
        final int size;
        int completed;
        int failed;
        long totalReward;
        boolean paid;
        BonusBatch(long rotation,long cycleDay,int size,long totalReward){
            this.rotation=rotation;this.cycleDay=cycleDay;this.size=size;
            this.totalReward=totalReward;
        }
        long bonus(){return Math.max(0,totalReward)*BONUS_NUMERATOR/BONUS_DENOMINATOR;}
    }
    private record Archived(String item,int amount,long reward,String state,long finishedAt,long rotation) {}
    private record Fine(long epoch,String cause,long amount) {}

    private MasterkiContracts() {}
    public static MasterkiContracts of(ServerLevel level){
        MasterkiContracts data=level.getServer().overworld().getDataStorage()
            .computeIfAbsent(new SavedData.Factory<>(MasterkiContracts::new,MasterkiContracts::load),"masterki_contracts");
        data.serverLevel=level.getServer().overworld();
        return data;
    }

    public long nextRotation(UUID player,long now){
        return board(player,now).nextRefresh;
    }
    public List<MasterkiNetwork.ContractStatus> visible(ServerPlayer player,long now){
        Board board=board(player.getUUID(),now);
        ArrayList<MasterkiNetwork.ContractStatus> result=new ArrayList<>();
        for(ContractEntry entry:board.entries)
            if(!"overdue".equals(entry.state) && !"expired".equals(entry.state))
                result.add(entry.view(entry.rotation!=board.rotation,
                    MasterkiDifficulty.adjusted(entry.reward,player)));
        return result;
    }
    public List<MasterkiNetwork.BonusStatus> bonuses(ServerPlayer player,long now){
        Board board=board(player.getUUID(),now);
        ArrayList<MasterkiNetwork.BonusStatus> result=new ArrayList<>();
        board.batches.values().stream()
            .sorted((a,b)->Long.compare(b.rotation,a.rotation))
            .limit(3).forEach(batch->result.add(new MasterkiNetwork.BonusStatus(
                batch.rotation,batch.size,batch.completed,batch.failed>0,batch.paid,
                batch.bonus())));
        return result;
    }
    public List<MasterkiNetwork.ContractArchive> archive(UUID uuid,long now){
        Board board=board(uuid,now);
        return board.archived.stream().limit(80)
            .map(c->new MasterkiNetwork.ContractArchive(c.item,c.amount,c.reward,
                c.state,c.finishedAt)).toList();
    }
    private Board board(UUID uuid,long now){
        // One-time 1.0.10 migration: reprice only UNACCEPTED offers.
        // Accepted payouts, completed payments and penalties are immutable.
        if(contractPriceVersion<2){
            for(Board old:boards.values())for(ContractEntry entry:old.entries){
                if("available".equals(entry.state))
                    entry.reward=Masterki.contractReward(entry.item,entry.amount);
            }
            contractPriceVersion=2;
            setDirty();
        }
        Board b=boards.computeIfAbsent(uuid,k->new Board());
        // Existing worlds may contain contracts created by 1.0.6 without a cycle key.
        // Migrate those in place, without wiping accepted/completed entries or fines.
        // Never silently delete an invalid contract: that would make the full
        // 40/40 bonus impossible. Replace the slot without changing rotation.
        long current=MasterkiSchedule.cycleDay(now);
        if(b.cycleDay==Long.MIN_VALUE && b.nextRefresh>0 && !b.entries.isEmpty())
            migrateOldBoard(b,now,current);
        if(b.cycleDay!=current){
            repairImpossible(b,now);
            // Expire BEFORE removing an old rotation. Offline players are charged once.
            expire(uuid,b,now);
            rotate(uuid,b,current,now);
        }
        repairImpossible(b,now);
        expire(uuid,b,now);
        // v1.0.11 worlds may have received today's batch before the upgrade.
        recoverCurrentBatch(b);
        return b;
    }
    private void recoverCurrentBatch(Board b){
        if(b.batches.containsKey(b.rotation))return;
        List<ContractEntry> current=b.entries.stream()
            .filter(e->e.rotation==b.rotation).toList();
        if(current.size()!=CONTRACTS_PER_DAY)return;
        long total=0;
        for(ContractEntry e:current)total+=e.acceptedPayout>0?e.acceptedPayout:e.reward;
        BonusBatch batch=new BonusBatch(b.rotation,b.cycleDay,current.size(),total);
        for(ContractEntry e:current){
            if("completed".equals(e.state))batch.completed++;
            else if("cancelled".equals(e.state)||"overdue".equals(e.state)
                ||"expired".equals(e.state))batch.failed++;
        }
        // Do not give a retroactive second payment for an already finished board.
        if(batch.completed==batch.size)batch.paid=true;
        b.batches.put(batch.rotation,batch);
        setDirty();
    }
    /**
     * Replace only unfinished, demonstrably uncraftable or explicitly denied
     * mod items. The original contract key/rotation and the 40-slot batch size
     * stay unchanged. An accepted bad order is replaced WITHOUT any fine.
     */
    private void repairImpossible(Board board,long now){
        if(serverLevel==null || board.entries.isEmpty())return;
        List<Masterki.Contract> pool=Masterki.contracts().stream()
            .filter(c->MasterkiObtainability.obtainable(serverLevel,c.item())).toList();
        java.util.Set<String> used=new java.util.HashSet<>();
        for(ContractEntry entry:board.entries)used.add(entry.item);
        for(ContractEntry entry:board.entries){
            if(!("available".equals(entry.state)||"accepted".equals(entry.state)))continue;
            if(MasterkiObtainability.obtainable(serverLevel,entry.item))continue;
            Masterki.Contract replacement=null;
            if(!pool.isEmpty()){
                int start=ThreadLocalRandom.current().nextInt(pool.size());
                for(int n=0;n<pool.size();n++){
                    Masterki.Contract c=pool.get((start+n)%pool.size());
                    if(!used.contains(c.item())){
                        replacement=c;break;
                    }
                }
                if(replacement==null)for(Masterki.Contract c:pool)
                    if(!c.item().equals(entry.item)){replacement=c;break;}
            }
            if(replacement==null){
                System.err.println("[Masterki] No safe contract substitute for "+entry.item);
                continue;
            }
            long oldPay=entry.acceptedPayout>0?entry.acceptedPayout:entry.reward;
            String oldItem=entry.item;
            used.remove(oldItem);
            used.add(replacement.item());
            int[] variants={75,100,125,150};
            int multiplier=variants[ThreadLocalRandom.current().nextInt(variants.length)];
            int amount=Math.max(1,(int)Math.min(4096,
                Math.round(replacement.amount()*multiplier/100.0)));
            long payout=Masterki.contractReward(replacement.item(),amount);
            entry.poolId=replacement.id();
            entry.item=replacement.item();
            entry.amount=amount;
            entry.reward=payout;
            entry.acceptedPayout=0L;
            entry.state="available";
            entry.difficult=classify(entry.item,entry.amount);
            // Preserve a valid longer old deadline; never make a repair overdue.
            long freshCutoff=MasterkiSchedule.cutoff(
                MasterkiSchedule.cycleDay(now),entry.difficult?3:1);
            entry.dueAt=Math.max(entry.dueAt,freshCutoff);
            BonusBatch batch=board.batches.get(entry.rotation);
            if(batch!=null)batch.totalReward=Math.max(0L,
                batch.totalReward-oldPay+payout);
            setDirty();
        }
    }

    /** Persistent server-wide three daily orders (same IDs across all players).
     * Replaces only invalid entries; cannot be farmed by repeated GUI openings.
     */
    public List<Masterki.Contract> dailyTasks(long now){
        long cycle=MasterkiSchedule.cycleDay(now);
        if(dailyTaskCycle!=cycle){
            dailyTaskCycle=cycle;
            dailyOrders.clear();
            // Seed from legacy deterministic daily IDs for migration compatibility.
            dailyOrders.addAll(Masterki.dailyTasks());
            setDirty();
        }
        if(serverLevel==null)return List.copyOf(dailyOrders);
        List<Masterki.Contract> pool=Masterki.contracts().stream()
            .filter(c->MasterkiObtainability.obtainable(serverLevel,c.item())).toList();
        java.util.Set<String> used=new java.util.HashSet<>();
        for(Masterki.Contract c:dailyOrders)used.add(c.item());
        for(int index=0;index<dailyOrders.size();index++){
            Masterki.Contract old=dailyOrders.get(index);
            if(MasterkiObtainability.obtainable(serverLevel,old.item()))continue;
            Masterki.Contract replacement=null;
            for(Masterki.Contract candidate:pool)
                if(!used.contains(candidate.item())){replacement=candidate;break;}
            if(replacement==null)for(Masterki.Contract candidate:pool)
                if(!candidate.item().equals(old.item())){replacement=candidate;break;}
            if(replacement==null)continue;
            used.remove(old.item());
            used.add(replacement.item());
            dailyOrders.set(index,new Masterki.Contract(
                "daily:"+replacement.id(),replacement.item(),
                Math.max(4,replacement.amount()/4),Math.max(30,replacement.reward()/4)));
            setDirty();
        }
        return List.copyOf(dailyOrders);
    }

    private void failBatch(Board board,ContractEntry entry){
        BonusBatch batch=board.batches.get(entry.rotation);
        if(batch!=null)batch.failed++;
        setDirty();
    }
    private void retire(Board board,ContractEntry entry,long now){
        String finalState="available".equals(entry.state)?"expired":entry.state;
        if("available".equals(entry.state))failBatch(board,entry);
        long paid=entry.acceptedPayout>0?entry.acceptedPayout:entry.reward;
        board.archived.add(0,new Archived(entry.item,entry.amount,paid,
            finalState,entry.closedAt>0?entry.closedAt:now,entry.rotation));
        if(board.archived.size()>MAX_ARCHIVE)
            board.archived.subList(MAX_ARCHIVE,board.archived.size()).clear();
        setDirty();
    }
    private void pruneOldBatches(Board board,long cycle){
        board.batches.entrySet().removeIf(entry->
            entry.getValue().cycleDay<cycle-10
            && board.entries.stream().noneMatch(e->e.rotation==entry.getKey()));
        setDirty();
    }
    private boolean classify(String item,int amount){
        // Complexity uses the real configured market price and quantity,
        // not merely the "create" namespace or the reward's capped amount.
        long price=0;
        for(Masterki.Offer offer:Masterki.offers())
            if(offer.id().equals(item)){price=offer.buy();break;}
        long workload=price*(long)amount;
        return workload>=6_000L || (amount>=128 && price>=24)
            || (price>=1_000 && amount>=4);
    }
    private void migrateOldBoard(Board board,long now,long cycle){
        long cutoff1=MasterkiSchedule.cutoff(cycle,1);
        long cutoff2=MasterkiSchedule.cutoff(cycle,2);
        for(ContractEntry entry:board.entries){
            boolean hard=classify(entry.item,entry.amount);
            if("accepted".equals(entry.state) && entry.dueAt>now){
                // Never shorten an already accepted old 24-hour deadline.
                // Round it UP to the first 05:00 cutoff at or after the old deadline.
                entry.dueAt=entry.dueAt<=cutoff1?cutoff1:cutoff2;
                entry.difficult=hard || entry.dueAt>cutoff1;
            }else if(!"accepted".equals(entry.state)){
                entry.difficult=hard;
                if("available".equals(entry.state))
                    entry.dueAt=hard?cutoff2:cutoff1;
            }
            // An already expired v1.0.6 acceptance remains expired; no resurrection.
        }
        board.cycleDay=cycle;
        board.nextRefresh=cutoff1;
        setDirty();
    }
    private void expire(UUID uuid,Board board,long now){
        for(ContractEntry entry:board.entries){
            if("accepted".equals(entry.state) && entry.dueAt>0 && now>=entry.dueAt){
                entry.state="overdue";
                entry.closedAt=now;
                failBatch(board,entry);
                fine(uuid,entry,"Просрочка контракта "+entry.poolId,latePercent);
                setDirty();
            }
        }
    }
    private void rotate(UUID uuid,Board board,long cycle,long now){
        List<ContractEntry> retained=new ArrayList<>();
        for(ContractEntry entry:board.entries){
            if("accepted".equals(entry.state)&&entry.dueAt>now)
                retained.add(entry);
            else retire(board,entry,now);
        }
        List<Masterki.Contract> pool=new ArrayList<>(Masterki.contracts());
        pool.removeIf(c->!MasterkiObtainability.obtainable(serverLevel,c.item()));
        Collections.shuffle(pool,ThreadLocalRandom.current());
        Map<String,Integer> previousVariants=new HashMap<>();
        for(String value:board.previous){
            int sep=value.lastIndexOf(':');
            if(sep>0)try{previousVariants.put(value.substring(0,sep),
                Integer.parseInt(value.substring(sep+1)));}catch(NumberFormatException ignored){}
        }
        List<Masterki.Contract> picked=new ArrayList<>();
        for(Masterki.Contract c:pool){
            if(picked.size()>=CONTRACTS_PER_DAY)break;
            if(!previousVariants.containsKey(c.id()))picked.add(c);
        }
        for(Masterki.Contract c:pool){
            if(picked.size()>=CONTRACTS_PER_DAY)break;
            if(picked.stream().noneMatch(p->p.id().equals(c.id())))picked.add(c);
        }
        board.rotation++;
        board.cycleDay=cycle;
        board.previous.clear();
        board.entries.clear();
        int[] variants={75,100,125,150};
        long first=MasterkiSchedule.cutoff(cycle,1),third=MasterkiSchedule.cutoff(cycle,3);
        long batchTotal=0;
        for(Masterki.Contract c:picked){
            int prior=previousVariants.getOrDefault(c.id(),-1);
            int variant=variants[ThreadLocalRandom.current().nextInt(variants.length)];
            if(variant==prior)
                variant=variants[(java.util.Arrays.binarySearch(variants,variant)+1)%variants.length];
            int amount=Math.max(1,(int)Math.min(4096,
                Math.round((double)c.amount()*variant/100.0)));
            // Recompute by FINAL randomized amount, never cap all rare items at 1250.
            long reward=Masterki.contractReward(c.item(),amount);
            ContractEntry entry=new ContractEntry("r"+board.rotation+"_"+c.id(),
                c.id(),c.item(),amount,reward,board.rotation);
            entry.difficult=classify(c.item(),amount);
            entry.dueAt=entry.difficult?third:first;
            board.previous.add(c.id()+":"+variant);
            board.entries.add(entry);
            batchTotal+=reward;
        }
        // Today's 40 appear first; still-active older three-day jobs come after them.
        board.entries.addAll(retained);
        board.batches.put(board.rotation,
            new BonusBatch(board.rotation,cycle,picked.size(),batchTotal));
        board.nextRefresh=first;
        pruneOldBatches(board,cycle);
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
            if(e.dueAt<=now){
                Masterki.tell(player,"Срок контракта уже истёк.",ChatFormatting.RED);return false;
            }
            e.acceptedPayout=MasterkiDifficulty.adjusted(e.reward,player);
            BonusBatch batch=b.batches.get(e.rotation);
            if(batch!=null)batch.totalReward+=e.acceptedPayout-e.reward;
            e.state="accepted";setDirty();
            long remaining=(e.dueAt-now+HOUR-1)/HOUR;
            Masterki.tell(player,"Контракт принят: "+(e.difficult?"сложный":"обычный")+
                ". До 05:00 по расписанию осталось около "+remaining+" ч.",ChatFormatting.GREEN);
            if(e.dueAt-now<TWO_HOURS)
                Masterki.tell(player,"Внимание! До срока менее двух часов.",ChatFormatting.YELLOW);
            return true;
        }
        if("cancel".equals(verb)){
            if(!"accepted".equals(e.state)){Masterki.tell(player,"Этот контракт нельзя отменить.",ChatFormatting.RED);return false;}
            e.state="cancelled";
            e.closedAt=now;
            failBatch(b,e);
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
        long pay=e.acceptedPayout>0?e.acceptedPayout:e.reward;
        BonusBatch batch=b.batches.get(e.rotation);
        boolean finishesBatch=batch!=null&&!batch.paid&&batch.failed==0
            &&batch.completed==batch.size-1&&batch.size>0;
        long premium=finishesBatch?batch.bonus():0L;
        // Check total before taking the items: no partial payment on overflow.
        if(pay<0||premium<0||pay>Long.MAX_VALUE-premium
            ||economy.balance(uuid)>Long.MAX_VALUE-pay-premium){
            Masterki.tell(player,"Баланс слишком велик для начисления награды.",ChatFormatting.RED);
            return false;
        }
        take(player,sample,e.amount);
        e.state="completed"; // commit state before any payment
        e.closedAt=now;
        if(batch!=null){
            batch.completed++;
            if(finishesBatch)batch.paid=true;
        }
        economy.setBalance(uuid,economy.balance(uuid)+pay+premium);
        economy.log(uuid,"Контракт "+e.poolId+": +"+pay+" М");
        if(finishesBatch)
            economy.log(uuid,"Премия за партию "+e.rotation+": +"+premium+
                " М (15% минус 13% комиссии)");
        setDirty();
        Masterki.tell(player,"Выполнено: +"+pay+" М",ChatFormatting.GREEN);
        if(finishesBatch)
            Masterki.tell(player,"Вся партия "+e.rotation+" закрыта! Премия: +"+
                premium+" М (15% от суммы, комиссия 13%).",ChatFormatting.GOLD);
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
        long payout=entry.acceptedPayout>0?entry.acceptedPayout:entry.reward;
        long fine=Math.max(1,(payout*percentage+99)/100);
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
        for(UUID player:new ArrayList<>(boards.keySet()))board(player,now);
    }
    public int reroll(UUID uuid,long now) {
        Board b=board(uuid,now);
        expire(uuid,b,now);
        rotate(uuid,b,MasterkiSchedule.cycleDay(now),now);
        return b.entries.size();
    }
    /**
     * ADMIN: cleanup only. No reroll, no new batch, no changes to today's
     * still-valid offers or accepted carry-over.
     */
    public int refreshExpired(long now){
        int count=0;
        for(var pair:boards.entrySet()){
            UUID uuid=pair.getKey();
            Board b=pair.getValue();
            expire(uuid,b,now);
            for(var it=b.entries.iterator();it.hasNext();){
                ContractEntry e=it.next();
                if("overdue".equals(e.state) ||
                    ("available".equals(e.state)&&e.dueAt>0&&e.dueAt<=now)){
                    retire(b,e,now);
                    it.remove();
                    count++;
                }
            }
        }
        if(count>0)setDirty();
        return count;
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
        dispatcher.register(Commands.literal("masterki")
            .then(Commands.literal("admin").requires(s->s.hasPermission(2))
                .then(Commands.literal("contracts")
                    .then(Commands.literal("refresh").executes(c->{
                        MasterkiContracts data=of(c.getSource().getLevel());
                        int count=data.refreshExpired(System.currentTimeMillis());
                        for(ServerPlayer player:c.getSource().getServer().getPlayerList().getPlayers())
                            Masterki.sync(player,false,"");
                        info(c.getSource(),"Очистка завершена: убрано просроченных контрактов "+
                            count+". Сегодняшняя ротация и действующие сложные заказы сохранены.");
                        return count>0?count:1;
                    })))));
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
                )
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
        tag.putInt("ContractPriceVersion",contractPriceVersion);
        tag.putInt("LatePercent",latePercent);
        tag.putInt("CancelPercent",cancelPercent);
        tag.putBoolean("FinesEnabled",finesEnabled);
        tag.putLong("DailyTaskCycle",dailyTaskCycle);
        ListTag daily=new ListTag();
        for(Masterki.Contract task:dailyOrders){
            CompoundTag row=new CompoundTag();
            row.putString("Id",task.id());row.putString("Item",task.item());
            row.putInt("Amount",task.amount());row.putLong("Reward",task.reward());
            daily.add(row);
        }
        tag.put("DailyOrders",daily);
        ListTag players=new ListTag();
        for(var pair:boards.entrySet()){
            CompoundTag data=new CompoundTag();
            data.putUUID("UUID",pair.getKey());
            Board board=pair.getValue();
            data.putLong("Next",board.nextRefresh);data.putLong("Rotation",board.rotation);
            data.putLong("CycleDay",board.cycleDay);
            ListTag previous=new ListTag();
            for(String id:board.previous){CompoundTag e=new CompoundTag();e.putString("ID",id);previous.add(e);}
            data.put("Previous",previous);
            ListTag tasks=new ListTag();
            for(ContractEntry c:board.entries){
                CompoundTag n=new CompoundTag();
                n.putString("Key",c.key);n.putString("Pool",c.poolId);n.putString("Item",c.item);
                n.putString("State",c.state);n.putInt("Amount",c.amount);n.putLong("Reward",c.reward);
                n.putLong("Due",c.dueAt);n.putLong("Rotation",c.rotation);
                n.putLong("AcceptedPayout",c.acceptedPayout);
                n.putLong("ClosedAt",c.closedAt);
                n.putBoolean("Difficult",c.difficult);tasks.add(n);
            }
            data.put("Entries",tasks);
            ListTag bonuses=new ListTag();
            for(BonusBatch batch:board.batches.values()){
                CompoundTag n=new CompoundTag();
                n.putLong("Rotation",batch.rotation);n.putLong("CycleDay",batch.cycleDay);
                n.putInt("Size",batch.size);n.putInt("Completed",batch.completed);
                n.putInt("Failed",batch.failed);n.putLong("Total",batch.totalReward);
                n.putBoolean("Paid",batch.paid);
                bonuses.add(n);
            }
            data.put("BonusBatches",bonuses);
            ListTag archived=new ListTag();
            for(Archived entry:board.archived){
                CompoundTag n=new CompoundTag();
                n.putString("Item",entry.item);n.putInt("Amount",entry.amount);
                n.putLong("Reward",entry.reward);n.putString("State",entry.state);
                n.putLong("FinishedAt",entry.finishedAt);n.putLong("Rotation",entry.rotation);
                archived.add(n);
            }
            data.put("ContractArchive",archived);
            players.add(data);
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
        s.contractPriceVersion=tag.contains("ContractPriceVersion",Tag.TAG_INT)
            ?tag.getInt("ContractPriceVersion"):0;
        s.latePercent=tag.contains("LatePercent")?Math.clamp(tag.getInt("LatePercent"),0,100):10;
        s.cancelPercent=tag.contains("CancelPercent")?Math.clamp(tag.getInt("CancelPercent"),0,100):5;
        s.finesEnabled=!tag.contains("FinesEnabled")||tag.getBoolean("FinesEnabled");
        s.dailyTaskCycle=tag.contains("DailyTaskCycle",Tag.TAG_LONG)
            ?tag.getLong("DailyTaskCycle"):Long.MIN_VALUE;
        ListTag daily=tag.getList("DailyOrders",Tag.TAG_COMPOUND);
        for(int i=0;i<daily.size()&&i<3;i++){
            CompoundTag task=daily.getCompound(i);
            if(!task.getString("Id").startsWith("daily:")||
                task.getString("Item").length()>128)continue;
            s.dailyOrders.add(new Masterki.Contract(task.getString("Id"),
                task.getString("Item"),Math.clamp(task.getInt("Amount"),1,4096),
                Math.max(1,task.getLong("Reward"))));
        }
        ListTag players=tag.getList("Boards",Tag.TAG_COMPOUND);
        for(int i=0;i<players.size();i++){
            CompoundTag data=players.getCompound(i);
            if(!data.hasUUID("UUID"))continue;
            Board b=new Board();b.nextRefresh=data.getLong("Next");b.rotation=data.getLong("Rotation");
            b.cycleDay=data.contains("CycleDay")?data.getLong("CycleDay"):Long.MIN_VALUE;
            ListTag old=data.getList("Previous",Tag.TAG_COMPOUND);
            for(int j=0;j<old.size();j++)b.previous.add(old.getCompound(j).getString("ID"));
            ListTag tasks=data.getList("Entries",Tag.TAG_COMPOUND);
            for(int j=0;j<tasks.size()&&j<256;j++){
                CompoundTag t=tasks.getCompound(j);
                String key=t.getString("Key"),item=t.getString("Item");
                if(key.length()>48||item.length()>128||key.isBlank()||item.isBlank())continue;
                ContractEntry c=new ContractEntry(key,t.getString("Pool"),item,
                    Math.clamp(t.getInt("Amount"),1,4096),Math.clamp(t.getLong("Reward"),1L,10_000_000_000_000L),
                    t.getLong("Rotation"));
                c.state=t.getString("State");c.dueAt=t.getLong("Due");
                c.acceptedPayout=t.contains("AcceptedPayout",Tag.TAG_LONG)
                    ?Math.max(0,t.getLong("AcceptedPayout")):0L;
                c.closedAt=t.contains("ClosedAt",Tag.TAG_LONG)?t.getLong("ClosedAt"):0L;
                c.difficult=t.getBoolean("Difficult");b.entries.add(c);
            }
            ListTag bonuses=data.getList("BonusBatches",Tag.TAG_COMPOUND);
            for(int j=0;j<bonuses.size()&&j<64;j++){
                CompoundTag n=bonuses.getCompound(j);
                int size=Math.clamp(n.getInt("Size"),1,CONTRACTS_PER_DAY);
                BonusBatch batch=new BonusBatch(n.getLong("Rotation"),
                    n.getLong("CycleDay"),size,Math.max(0L,n.getLong("Total")));
                batch.completed=Math.clamp(n.getInt("Completed"),0,size);
                batch.failed=Math.clamp(n.getInt("Failed"),0,size);
                batch.paid=n.getBoolean("Paid");
                b.batches.put(batch.rotation,batch);
            }
            ListTag archive=data.getList("ContractArchive",Tag.TAG_COMPOUND);
            for(int j=0;j<archive.size()&&j<MAX_ARCHIVE;j++){
                CompoundTag n=archive.getCompound(j);
                if(n.getString("Item").length()>128)continue;
                b.archived.add(new Archived(n.getString("Item"),n.getInt("Amount"),
                    n.getLong("Reward"),n.getString("State"),
                    n.getLong("FinishedAt"),n.getLong("Rotation")));
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
