package dev.masterki.economy;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class MasterkiNetwork {
    private MasterkiNetwork(){}
    public record Product(String id,String category,long buy,long sell,int stock,int daily,int cap){}
    public record Task(String id,String item,int amount,long reward){}
    public record ContractStatus(String id,String item,int amount,long reward,String status,long deadline,
                                 boolean difficult,boolean carriedOver){}
    public record LayoutEntry(String id,int x,int y,int scale,boolean visible){}

    public record Action(String kind,String id,int count) implements CustomPacketPayload{
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("masterki","action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeUtf(p.kind,24);buf.writeUtf(p.id,128);buf.writeVarInt(p.count);},
            buf->new Action(buf.readUtf(24),buf.readUtf(128),buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    /** Chunked full-catalog transport: avoids oversized payloads on large modpacks. */
    public record CatalogChunk(boolean reset,boolean last,List<Product> products)
        implements CustomPacketPayload {
        public static final Type<CatalogChunk> TYPE=new Type<>(
            ResourceLocation.fromNamespaceAndPath("masterki","catalog_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf,CatalogChunk> CODEC=StreamCodec.of(
            (buf,p)->{
                buf.writeBoolean(p.reset());buf.writeBoolean(p.last());
                buf.writeVarInt(p.products().size());
                for(Product o:p.products()){
                    buf.writeUtf(o.id(),128);buf.writeUtf(o.category(),48);
                    buf.writeLong(o.buy());buf.writeLong(o.sell());
                    buf.writeVarInt(o.stock());buf.writeVarInt(o.daily());buf.writeVarInt(o.cap());
                }
            },
            buf->{
                boolean reset=buf.readBoolean(),last=buf.readBoolean();
                int n=buf.readVarInt();
                if(n<0||n>128)throw new IllegalArgumentException("Bad Masterki catalog chunk size "+n);
                List<Product> products=new ArrayList<>(n);
                for(int i=0;i<n;i++)products.add(new Product(buf.readUtf(128),buf.readUtf(48),
                    buf.readLong(),buf.readLong(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt()));
                return new CatalogChunk(reset,last,products);
            });
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }

    public record Snapshot(boolean open,String tab,long balance,long nextRestock,
                           List<Product> products,List<Task> tasks,List<String> history,List<LayoutEntry> layout,
                           long nextContracts,long dailyReset,List<ContractStatus> contracts,List<String> dailyDone,String scheduleZone)
            implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("masterki","state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of(
            (buf,p)->{
                buf.writeBoolean(p.open);buf.writeUtf(p.tab,32);buf.writeLong(p.balance);
                buf.writeLong(p.nextRestock);
                buf.writeVarInt(p.products.size());
                for(Product o:p.products){
                    buf.writeUtf(o.id,128);buf.writeUtf(o.category,48);
                    buf.writeLong(o.buy);buf.writeLong(o.sell);
                    buf.writeVarInt(o.stock);buf.writeVarInt(o.daily);buf.writeVarInt(o.cap);
                }
                buf.writeVarInt(p.tasks.size());
                for(Task t:p.tasks){
                    buf.writeUtf(t.id,48);buf.writeUtf(t.item,128);
                    buf.writeVarInt(t.amount);buf.writeLong(t.reward);
                }
                buf.writeVarInt(p.history.size());
                for(String line:p.history)buf.writeUtf(line,255);
                buf.writeVarInt(p.layout.size());
                for(LayoutEntry e:p.layout){buf.writeUtf(e.id(),64);buf.writeInt(e.x());buf.writeInt(e.y());buf.writeVarInt(e.scale());buf.writeBoolean(e.visible());}
                buf.writeLong(p.nextContracts);buf.writeLong(p.dailyReset);
                buf.writeVarInt(p.contracts.size());
                for(ContractStatus entry:p.contracts){buf.writeUtf(entry.id(),48);buf.writeUtf(entry.item(),128);buf.writeVarInt(entry.amount());buf.writeLong(entry.reward());buf.writeUtf(entry.status(),24);buf.writeLong(entry.deadline());buf.writeBoolean(entry.difficult());buf.writeBoolean(entry.carriedOver());}
                buf.writeVarInt(p.dailyDone.size());
                for(String key:p.dailyDone)buf.writeUtf(key,48);
                buf.writeUtf(p.scheduleZone(),64);
            },
            buf->{
                boolean open=buf.readBoolean();String tab=buf.readUtf(32);
                long balance=buf.readLong(),next=buf.readLong();
                int size=Math.min(512,buf.readVarInt());List<Product> rows=new ArrayList<>(size);
                for(int i=0;i<size;i++)rows.add(new Product(buf.readUtf(128),buf.readUtf(48),
                    buf.readLong(),buf.readLong(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt()));
                int taskCount=Math.min(128,buf.readVarInt());List<Task> tasks=new ArrayList<>(taskCount);
                for(int i=0;i<taskCount;i++)tasks.add(new Task(buf.readUtf(48),buf.readUtf(128),
                    buf.readVarInt(),buf.readLong()));
                int historyCount=Math.min(100,buf.readVarInt());List<String> history=new ArrayList<>();
                for(int i=0;i<historyCount;i++)history.add(buf.readUtf(255));
                int layoutCount=Math.min(64,buf.readVarInt());
                List<LayoutEntry> layout=new ArrayList<>();
                for(int i=0;i<layoutCount;i++)layout.add(new LayoutEntry(
                    buf.readUtf(64),buf.readInt(),buf.readInt(),buf.readVarInt(),buf.readBoolean()));
                long nextContracts=buf.readLong(), dailyReset=buf.readLong();
                int number=Math.min(128,buf.readVarInt());
                List<ContractStatus> contracts=new ArrayList<>(number);
                for(int i=0;i<number;i++)contracts.add(new ContractStatus(buf.readUtf(48),
                    buf.readUtf(128),buf.readVarInt(),buf.readLong(),buf.readUtf(24),buf.readLong(),
                    buf.readBoolean(),buf.readBoolean()));
                int count=Math.min(64,buf.readVarInt());
                List<String> dailyDone=new ArrayList<>(count);
                for(int i=0;i<count;i++)dailyDone.add(buf.readUtf(48));
                return new Snapshot(open,tab,balance,next,rows,tasks,history,layout,nextContracts,dailyReset,contracts,dailyDone,buf.readUtf(64));
            });
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }

    public static void register(RegisterPayloadHandlersEvent event){
        PayloadRegistrar registrar=event.registrar("1");
        registrar.playToServer(Action.TYPE,Action.CODEC,(packet,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer player)Masterki.action(player,packet);
        }));
        registrar.playToClient(Snapshot.TYPE,Snapshot.CODEC,(packet,context)->
            context.enqueueWork(()->dev.masterki.economy.client.ClientEconomy.receive(packet)));
        registrar.playToClient(CatalogChunk.TYPE,CatalogChunk.CODEC,(packet,context)->
            context.enqueueWork(()->dev.masterki.economy.client.ClientEconomy.receiveCatalog(packet)));
    }
}
