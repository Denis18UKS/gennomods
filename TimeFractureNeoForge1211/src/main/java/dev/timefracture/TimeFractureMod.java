package dev.timefracture;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.nio.file.*;
import java.util.*;

@Mod("timefracture")
public class TimeFractureMod {
  static Ledger L=new Ledger(Path.of("config","timefracture","branches.tsv"));
  static PocketWorld P;
  static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("timefracture");
  static {
    ITEMS.register("time_artifact",()->new Item(new Item.Properties().stacksTo(1)){
      @Override public InteractionResultHolder<ItemStack> use(Level world,Player user,InteractionHand hand){
        if(world.isClientSide) TimeArtifactClient.open();
        return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand),world.isClientSide);
      }
    });
  }
  public TimeFractureMod(IEventBus bus){
    ITEMS.register(bus);
    NeoForge.EVENT_BUS.addListener(TimeFractureMod::commands);
    NeoForge.EVENT_BUS.addListener(TimeFractureMod::death);
    NeoForge.EVENT_BUS.addListener(TimeFractureMod::serverStarted);
    NeoForge.EVENT_BUS.addListener(TimeFractureMod::dimensionChanged);
    NeoForge.EVENT_BUS.addListener(TimeFractureMod::importantBlock);
    NeoForge.EVENT_BUS.addListener(TimeEchoes::tick);
  }
  static UUID actor(net.minecraft.commands.CommandSourceStack s){
    return s.getEntity() instanceof ServerPlayer p ? p.getUUID() : new UUID(0,0);
  }
  static boolean dev(net.minecraft.commands.CommandSourceStack s){return s.hasPermission(4)||L.devs.contains(actor(s));}
  static int msg(net.minecraft.commands.CommandSourceStack s,String m){s.sendSuccess(()->Component.literal(m),false);return 1;}
  static void commands(RegisterCommandsEvent e){
    var root=Commands.literal("timefracture");
    root.then(Commands.literal("players").executes(c->msg(c.getSource(),"§dOnline: "+String.join(", ",c.getSource().getServer().getPlayerList().getPlayers().stream().map(p->p.getGameProfile().getName()).toList()))));
    root.then(Commands.literal("focus").then(Commands.argument("player",EntityArgument.player()).executes(c->{
      ServerPlayer p=EntityArgument.getPlayer(c,"player");
      L.select(actor(c.getSource()),p.getUUID(),p.getGameProfile().getName());
      return msg(c.getSource(),"§aИсточник ветвлений: "+p.getName().getString());
    })));
    root.then(Commands.literal("branch").then(Commands.argument("parent",StringArgumentType.word()).executes(c->{
      var f=L.focus.get(actor(c.getSource()));
      if(f==null)return msg(c.getSource(),"§cВыбери реального онлайн-игрока через focus.");
      if(c.getSource().getServer().getPlayerList().getPlayer(f.id)==null)return msg(c.getSource(),"§cЦелевой игрок офлайн.");
      try {
        String parent=StringArgumentType.getString(c,"parent");
        if(P==null)throw new IllegalStateException("Менеджер ветвей ещё не загрузился");
        if(L.frozen)throw new IllegalStateException("Ветвления заморожены");
        if(parent!=null&&!parent.equals("-")&&P.get(parent)==null)
            throw new IllegalArgumentException("Родительская линия не имеет снимка: "+parent);
        var b=L.add(f.id,f.name,parent,"manual");
        var pocket=P.create(c.getSource().getServer(),b.id, c.getSource().getServer().getPlayerList().getPlayer(f.id),parent);
        var world=c.getSource().getServer().getLevel(net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            net.minecraft.resources.ResourceLocation.parse(pocket.dimension())));
        if(world!=null)TimeEchoes.create(world,pocket,P.count());
        return msg(c.getSource(),"§aВетвь "+b.id+" готова: отдельная область 21×17×21. Вход: /timefracture enter "+b.id);
      }catch(IllegalStateException|IllegalArgumentException ex){return msg(c.getSource(),"§c"+ex.getMessage());}
    })));
    root.then(Commands.literal("enter").then(Commands.argument("line",StringArgumentType.word()).executes(c->{
      if(!(c.getSource().getEntity() instanceof ServerPlayer p))return msg(c.getSource(),"§cТолько для игрока");
      try {P.enter(p,StringArgumentType.getString(c,"line"));return 1;}
      catch(Exception ex){return msg(c.getSource(),"§c"+ex.getMessage());}
    })));
    root.then(Commands.literal("return").executes(c->{
      if(!(c.getSource().getEntity() instanceof ServerPlayer p))return msg(c.getSource(),"§cТолько для игрока");
      try {P.leave(p);return 1;}
      catch(Exception ex){return msg(c.getSource(),"§c"+ex.getMessage());}
    }));
    root.then(Commands.literal("pockets").executes(c->msg(c.getSource(),
      "§dЛокальных временных миров: "+(P==null?0:P.count())+" / "+PocketWorld.MAX)));
    root.then(Commands.literal("tree").executes(c->{
      var list=L.list();
      msg(c.getSource(),"§dЗаписей: "+list.size()+"; локальных миров: "+(P==null?0:P.count()));
      list.stream().skip(Math.max(0,list.size()-10)).forEach(b->msg(c.getSource(),b.id+" <- "+b.parent+" "+b.name+" "+b.event));
      return 1;
    }));
    var bal=Commands.literal("balance");
    bal.then(Commands.literal("status").executes(c->msg(c.getSource(),"Линий "+L.list().size()+"; заморозка="+L.frozen)));
    bal.then(Commands.literal("freeze").requires(TimeFractureMod::dev).executes(c->{L.frozen=true;L.save();return msg(c.getSource(),"§eВетвления заморожены.");}));
    bal.then(Commands.literal("unfreeze").requires(TimeFractureMod::dev).executes(c->{L.frozen=false;L.save();return msg(c.getSource(),"§aВетвления разблокированы.");}));
    bal.then(Commands.literal("restore").requires(TimeFractureMod::dev).then(Commands.literal("all").executes(c->{
      L.restore();return msg(c.getSource(),"§aМетаданные стабилизированы; игровые миры не изменялись.");
    })));
    root.then(bal);
    var admin=Commands.literal("dev").requires(s->s.hasPermission(4));
    admin.then(Commands.literal("grant").then(Commands.argument("player",EntityArgument.player()).executes(c->{
      var p=EntityArgument.getPlayer(c,"player");L.devs.add(p.getUUID());L.save();return msg(c.getSource(),"DEV+: "+p.getName().getString());
    })));
    admin.then(Commands.literal("revoke").then(Commands.argument("player",EntityArgument.player()).executes(c->{
      var p=EntityArgument.getPlayer(c,"player");L.devs.remove(p.getUUID());L.save();return msg(c.getSource(),"DEV-: "+p.getName().getString());
    })));
    root.then(admin);
    e.getDispatcher().register(root);
  }
  static void death(LivingDeathEvent e){
    if(L.frozen||P==null)return;
    if(e.getEntity() instanceof ServerPlayer p){
      eventBranch(p,"player-death");
      return;
    }
    if(e.getEntity() instanceof net.minecraft.world.entity.TamableAnimal pet){
      UUID owner=pet.getOwnerUUID();
      if(owner!=null && e.getEntity().level() instanceof net.minecraft.server.level.ServerLevel level){
        var p=level.getServer().getPlayerList().getPlayer(owner);
        if(p!=null)eventBranch(p,"pet-death:"+pet.getName().getString());
      }
    }
    if(e.getSource().getEntity() instanceof ServerPlayer killer){
      var type=e.getEntity().getType();
      if(type==net.minecraft.world.entity.EntityType.ENDER_DRAGON ||
         type==net.minecraft.world.entity.EntityType.WITHER ||
         type==net.minecraft.world.entity.EntityType.WARDEN)
        eventBranch(killer,"boss-defeated:"+type.toString());
    }
  }
  static void serverStarted(net.neoforged.neoforge.event.server.ServerStartedEvent e){
    Path dir=e.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
      .resolve("data").resolve("timefracture");
    L=new Ledger(dir.resolve("branches.tsv"));
    P=new PocketWorld(dir.resolve("pockets.tsv"));
  }
  static void dimensionChanged(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e){
    if(!L.frozen&&e.getEntity() instanceof ServerPlayer p)eventBranch(p,"dimension-change");
  }
  static void importantBlock(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent e){
    if(L.frozen||!(e.getPlayer() instanceof ServerPlayer p))return;
    var s=e.getState();
    if(s.is(net.minecraft.world.level.block.Blocks.DRAGON_EGG)
       || s.is(net.minecraft.world.level.block.Blocks.BEACON)
       || s.is(net.minecraft.world.level.block.Blocks.END_PORTAL_FRAME))
        eventBranch(p,"significant-block:"+s.getBlock().getName().getString());
  }
  static void eventBranch(ServerPlayer subject,String event){
    if(P==null||L.frozen)return;
    if(L.focus.values().stream().noneMatch(f->f.id.equals(subject.getUUID())))return;
    try{
      // Historical bookkeeping and world snapshot are distinct: a failed
      // physical snapshot must be reported and not misrepresented as playable.
      String last=P.closest(subject);
      String parent=last.equals("-")?"-":last;
      var record=L.add(subject.getUUID(),subject.getGameProfile().getName(),parent,event);
      var pocket=P.create(subject.getServer(),record.id,subject,parent);
      var level=subject.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
        net.minecraft.core.registries.Registries.DIMENSION,
        net.minecraft.resources.ResourceLocation.parse(pocket.dimension())));
      if(level!=null)TimeEchoes.create(level,pocket,P.count());
      subject.sendSystemMessage(Component.literal("§5[Раскол времени] §f"+event+" → "+record.id));
    }catch(RuntimeException ex){
      subject.sendSystemMessage(Component.literal("§c[TimeFracture] Не удалось создать ветвь: "+ex.getMessage()));
    }
  }
  static final class Ledger {
    record Focus(UUID id,String name){}
    record Branch(String id,String parent,UUID player,String name,String event,long millis,int stability){}
    final Path file;
    final Map<String,Branch> nodes=new LinkedHashMap<>();
    final Map<UUID,Focus> focus=new HashMap<>();
    final Set<UUID> devs=new HashSet<>();
    boolean frozen;
    int next;
    Ledger(Path path){file=path;load();}
    synchronized void select(UUID by,UUID target,String name){focus.put(by,new Focus(target,name));save();}
    synchronized String latest(UUID id){
      String last="-";for(var b:nodes.values())if(b.player.equals(id))last=b.id;return last;
    }
    synchronized Branch add(UUID who,String name,String parent,String event){
      if(frozen)throw new IllegalStateException("Ветвление отключено.");
      if(parent==null||parent.isBlank())parent="-";
      if(!parent.equals("-")&&!nodes.containsKey(parent))throw new IllegalArgumentException("Нет линии "+parent);
      if(!parent.equals("-")&&!nodes.get(parent).player.equals(who))throw new IllegalArgumentException("Линия принадлежит другой личности.");
      int depth=0;String p=parent;
      while(!p.equals("-")&&depth<1000){depth++;p=nodes.get(p).parent;}
      var b=new Branch("T-"+(++next),parent,who,name,event,System.currentTimeMillis(),Math.max(0,100-depth*4));
      nodes.put(b.id,b);save();return b;
    }
    synchronized List<Branch> list(){return List.copyOf(nodes.values());}
    synchronized void restore(){nodes.replaceAll((k,b)->new Branch(b.id,b.parent,b.player,b.name,b.event,b.millis,100));frozen=false;save();}
    static String enc(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static String dec(String s){return new String(Base64.getUrlDecoder().decode(s),java.nio.charset.StandardCharsets.UTF_8);}
    synchronized void save(){
      try{
        Files.createDirectories(file.getParent());
        var all=new ArrayList<String>();
        all.add("H\t"+next+"\t"+frozen);
        nodes.values().forEach(b->all.add("B\t"+b.id+"\t"+b.parent+"\t"+b.player+"\t"+enc(b.name)+"\t"+enc(b.event)+"\t"+b.millis+"\t"+b.stability));
        focus.forEach((id,f)->all.add("F\t"+id+"\t"+f.id+"\t"+enc(f.name)));
        devs.forEach(d->all.add("D\t"+d));
        var tmp=file.resolveSibling(file.getFileName()+".tmp");Files.write(tmp,all);
        Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
      }catch(Exception ex){System.err.println("[TimeFracture] saving: "+ex);}
    }
    synchronized void load(){
      if(!Files.exists(file))return;
      try{
        for(String row:Files.readAllLines(file)){
          String[] s=row.split("\t");
          switch(s[0]){
            case "H"->{next=Integer.parseInt(s[1]);frozen=Boolean.parseBoolean(s[2]);}
            case "B"->{var b=new Branch(s[1],s[2],UUID.fromString(s[3]),dec(s[4]),dec(s[5]),Long.parseLong(s[6]),Integer.parseInt(s[7]));nodes.put(b.id,b);}
            case "F"->focus.put(UUID.fromString(s[1]),new Focus(UUID.fromString(s[2]),dec(s[3])));
            case "D"->devs.add(UUID.fromString(s[1]));
          }
        }
      }catch(Exception ex){System.err.println("[TimeFracture] load: "+ex);}
    }
  }
}