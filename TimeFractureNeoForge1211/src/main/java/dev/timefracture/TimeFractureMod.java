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
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import dev.timefracture.create.ChronalCoreBlock;
import dev.timefracture.create.ChronalCoreBlockEntity;
import dev.timefracture.layer.LayerCatalog;
import dev.timefracture.layer.LayerWorlds;
import java.nio.file.*;
import java.util.*;

@Mod("timefracture")
public class TimeFractureMod {
  static Ledger L=new Ledger(Path.of("config","timefracture","branches.tsv"));
  static PocketWorld P; // LEGACY: read-only recovery for previously generated pocket areas
  static LayerCatalog LAYERS;
  static LayerWorlds TRAVEL;
  static RiftGates GATES;
  static AnomalyManager A;
  public static final DeferredRegister<EntityType<?>> ENTITY_TYPES=
      DeferredRegister.create(Registries.ENTITY_TYPE,"timefracture");
  public static final DeferredHolder<EntityType<?>,EntityType<EchoPlayerEntity>> ECHO_PLAYER=
      ENTITY_TYPES.register("echo_player",()->
          EntityType.Builder.of(EchoPlayerEntity::new,MobCategory.CREATURE)
            .sized(.6f,1.8f).clientTrackingRange(10).updateInterval(3)
            .build("timefracture:echo_player"));
  public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("timefracture");
  public static final DeferredBlock<ChronalCoreBlock> CHRONAL_CORE =
      BLOCKS.register("chronal_core",()->new ChronalCoreBlock(
          BlockBehaviour.Properties.of().strength(5f,8f).requiresCorrectToolForDrops()
              .sound(SoundType.METAL).lightLevel(s->7)));
  public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE,"timefracture");
  public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ChronalCoreBlockEntity>> CHRONAL_CORE_BE =
      BLOCK_ENTITIES.register("chronal_core",()->
          BlockEntityType.Builder.of(ChronalCoreBlockEntity::new,CHRONAL_CORE.get()).build(null));
  public static final DeferredBlock<RiftGateBlock> RIFT_GATE =
      BLOCKS.register("rift_gate",()->new RiftGateBlock(
          BlockBehaviour.Properties.of().strength(2f).noCollission()
              .lightLevel(state->14).sound(SoundType.AMETHYST)));
  static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("timefracture");
  static {
    ITEMS.registerSimpleBlockItem(CHRONAL_CORE);
    ITEMS.registerSimpleBlockItem(RIFT_GATE);
    ITEMS.register("time_artifact",()->new Item(new Item.Properties().stacksTo(1)){
      @Override public InteractionResultHolder<ItemStack> use(Level world,Player user,InteractionHand hand){
        if(world.isClientSide) TimeArtifactClient.open();
        return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand),world.isClientSide);
      }
    });
  }
  public TimeFractureMod(IEventBus bus){
    ENTITY_TYPES.register(bus);
    bus.addListener(TimeFractureMod::entityAttributes);
    BLOCKS.register(bus);
    BLOCK_ENTITIES.register(bus);
    ITEMS.register(bus);
    NeoForge.EVENT_BUS.addListener(TimeFractureMod::commands);
    NeoForge.EVENT_BUS.addListener(TimeFractureMod::death);
    NeoForge.EVENT_BUS.addListener(TimeFractureMod::serverStarted);
    NeoForge.EVENT_BUS.addListener(TimeFractureMod::dimensionChanged);
    NeoForge.EVENT_BUS.addListener(TimeFractureMod::importantBlock);
    // NEVER instantiate or tick villager-based fake player echoes in v0.8.
    // NEVER auto-teleport players into displaced legacy pocket islands.
    NeoForge.EVENT_BUS.addListener(ConvergenceService::tick);
    NeoForge.EVENT_BUS.addListener(TimeFractureMod::layerTicks);
    NeoForge.EVENT_BUS.addListener(TimeFractureMod::gateTicks);
  }
  private static void entityAttributes(EntityAttributeCreationEvent event){
    event.put(ECHO_PLAYER.get(),EchoPlayerEntity.attributes().build());
  }

  public static boolean canConverge() { return P!=null && P.count()>=2 && !L.frozen; }
  public static int activePocketCount() { return P==null?0:P.count(); }
  public static boolean isFrozen() {return L.frozen;}
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
        if(LAYERS==null)throw new IllegalStateException("Индекс временных слоёв ещё не загружен");
        if(L.frozen)throw new IllegalStateException("Временное ветвление заморожено");
        if(L.next>=LayerWorlds.MAX_SLOTS)
          throw new IllegalStateException("Доступно только 8 игровых слотов в этой версии. Старые ветви сохраняются.");
        if(!"-".equals(parent) && LAYERS.find(parent)==null)
            throw new IllegalArgumentException("У родительской линии отсутствует индекс слоёв: "+parent);
        ServerPlayer source=c.getSource().getServer().getPlayerList().getPlayer(f.id);
        if(source==null)throw new IllegalStateException("Игрок больше не онлайн");
        var created=L.add(f.id,f.name,parent,"manual");
        registerLayer(source,created.id,parent);
        if(TRAVEL!=null)TRAVEL.enqueue(source,created.id,parent,source.blockPosition());
        return msg(c.getSource(),"§aВетвь "+created.id+
            " создана на тех же X/Y/Z. Снимок исходного чанка подготавливается без блокировки сервера. "+
            "Вход: /timefracture enter "+created.id);
      }catch(IllegalStateException|IllegalArgumentException ex){return msg(c.getSource(),"§c"+ex.getMessage());}
    })));
    root.then(Commands.literal("enter").then(Commands.argument("line",StringArgumentType.word()).executes(c->{
      if(!(c.getSource().getEntity() instanceof ServerPlayer p))return msg(c.getSource(),"§cТолько для игрока");
      String id=StringArgumentType.getString(c,"line");
      if(LAYERS==null || LAYERS.find(id)==null)return msg(c.getSource(),"§cТакого временного слоя нет: "+id);
      if(TRAVEL==null)return msg(c.getSource(),"§cМодуль переходов ещё не загрузился.");
      return msg(c.getSource(),TRAVEL.enter(p,id,LAYERS));
    })));
    root.then(Commands.literal("return").executes(c->{
      if(!(c.getSource().getEntity() instanceof ServerPlayer p))return msg(c.getSource(),"§cТолько для игрока");
      if(TRAVEL==null)return msg(c.getSource(),"§cМодуль переходов ещё не загрузился.");
      return msg(c.getSource(),TRAVEL.leave(p));
    }));
    var gates=Commands.literal("gate");
    gates.then(Commands.literal("open").then(Commands.argument("line",StringArgumentType.word()).executes(c->{
      if(!(c.getSource().getEntity() instanceof ServerPlayer player))
        return msg(c.getSource(),"§cКоманда требует присутствия игрока");
      if(GATES==null)return msg(c.getSource(),"§cРеестр разломов ещё не загружен");
      return msg(c.getSource(),GATES.open(player,StringArgumentType.getString(c,"line")));
    })));
    gates.then(Commands.literal("status").executes(c->
        msg(c.getSource(),GATES==null?"§cРеестр разломов не загружен":GATES.status())));
    root.then(gates);
    var anomalies=Commands.literal("anomaly");
    anomalies.then(Commands.literal("on").executes(c->{
      if(!(c.getSource().getEntity() instanceof ServerPlayer p)||A==null)
        return msg(c.getSource(),"§cТолько игрок после загрузки сервера");
      A.set(p.getUUID(),true);
      return msg(c.getSource(),"§dСлучайные временные аномалии включены для тебя (можно выключить /timefracture anomaly off)");
    }));
    anomalies.then(Commands.literal("off").executes(c->{
      if(!(c.getSource().getEntity() instanceof ServerPlayer p)||A==null)
        return msg(c.getSource(),"§cТолько игрок после загрузки сервера");
      A.set(p.getUUID(),false);
      return msg(c.getSource(),"§aСлучайные переходы выключены");
    }));
    anomalies.then(Commands.literal("test").executes(c->{
      if(!(c.getSource().getEntity() instanceof ServerPlayer p)||A==null)
        return msg(c.getSource(),"§cТолько игрок после загрузки сервера");
      return msg(c.getSource(),A.travel(p,true)?"§dАномалия запущена":"§eПока нет доступных альтернативных слоёв");
    }));
    root.then(anomalies);
    root.then(Commands.literal("converge")
        .then(Commands.literal("status").executes(c->msg(c.getSource(),
            "§bОбъединение запускается ТОЛЬКО от механизма Create, а не командой. "+
            ConvergenceService.status()))));
    root.then(Commands.literal("layers").executes(c->msg(c.getSource(),
      "§dЗарегистрированных слоёв: "+(LAYERS==null?0:LAYERS.size())+
      " · независимых слотов: 8 × 3 измерения. "+(TRAVEL==null?"Нет данных":TRAVEL.status()))));
    root.then(Commands.literal("inspect").then(Commands.argument("line",StringArgumentType.word()).executes(c->
      msg(c.getSource(),LAYERS==null?"Индекс не загружен":
          LAYERS.describe(StringArgumentType.getString(c,"line"))))));
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
    admin.then(Commands.literal("echo").then(Commands.literal("summon")
        .then(Commands.argument("player",EntityArgument.player()).executes(c->{
        if(!(c.getSource().getEntity() instanceof ServerPlayer invoking))
            return msg(c.getSource(),"§cКоманда требует присутствия оператора в мире");
        ServerPlayer original=EntityArgument.getPlayer(c,"player");
        EchoPlayerEntity echo=ECHO_PLAYER.get().create(invoking.serverLevel());
        if(echo==null)return msg(c.getSource(),"§cНе удалось создать альтернативную личность");
        echo.initializeFrom(original,"T-DEV");
        echo.setPos(invoking.getX()+5,invoking.getY(),invoking.getZ()+5);
        invoking.serverLevel().addFreshEntity(echo);
        return msg(c.getSource(),"§dСоздана отдельная сущность-двойник игрока "+
           original.getName().getString()+" (скин сохранён, ИИ базовый)");
    }))));
    admin.then(Commands.literal("cleanup-legacy-echoes").executes(c->{
      int removed=0;
      for(var level:c.getSource().getServer().getAllLevels()){
        for(var entity:level.getAllEntities()){
          if(entity instanceof net.minecraft.world.entity.npc.Villager villager &&
              (villager.getPersistentData().contains("tf_echo_subject") ||
               villager.getPersistentData().getBoolean("tf_keeper"))){
            entity.discard();
            removed++;
          }
        }
      }
      return msg(c.getSource(),"§aУдалено старых жителей-заглушек: "+removed+
           ". Настоящие жители не затронуты.");
    }));
    admin.then(Commands.literal("revoke").then(Commands.argument("player",EntityArgument.player()).executes(c->{
      var p=EntityArgument.getPlayer(c,"player");L.devs.remove(p.getUUID());L.save();return msg(c.getSource(),"DEV-: "+p.getName().getString());
    })));
    var rollback=Commands.literal("rollback");
    rollback.then(Commands.literal("preview").executes(c->msg(c.getSource(),
        "§eПолный откат всех событий требует полного снимка сервера ДО первого раскола. "+
        "В этой экспериментальной сборке сохранены локальные области, а не полные снимки миров. "+
        "Нельзя безопасно восстановить умерших существ, инвентари и механизмы без такого снимка.")));
    rollback.then(Commands.literal("origin").executes(c->msg(c.getSource(),
        "§cDEV-откат заблокирован: отсутствует проверенный полный доснимочный backup мира. "+
        "Ручное восстановление — только на остановленном сервере из резервной копии.")));
    rollback.then(Commands.literal("unarchive").executes(c->{
        if(P==null)return msg(c.getSource(),"§cНет реестра временных областей.");
        if(ConvergenceService.running())return msg(c.getSource(),"§cДождитесь завершения текущего объединения.");
        P.restoreArchived();
        return msg(c.getSource(),"§aАрхивные ветви снова доступны. Незавершённые области остаются заблокированными.");
    }));
    rollback.then(Commands.literal("archive-pockets").then(Commands.literal("confirm").executes(c->{
        if(P==null)return msg(c.getSource(),"§cНет реестра временных областей.");
        if(ConvergenceService.running())return msg(c.getSource(),"§cСначала завершите объединение.");
        for(ServerPlayer p:c.getSource().getServer().getPlayerList().getPlayers()){
            try{P.leave(p);}catch(RuntimeException ignored){}
        }
        P.archiveAll();
        L.frozen=true;
        L.save();
        return msg(c.getSource(),"§eВсе карманные ветви архивированы. "+
            "Оригинальный мир не удалён, но события до раскола НЕ восстановлены.");
    })));
    admin.then(rollback);
    root.then(admin);
    e.getDispatcher().register(root);
  }
  static void death(LivingDeathEvent e){
    if(L.frozen||LAYERS==null)return;
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
    P=new PocketWorld(dir.resolve("pockets.tsv")); // legacy stored pockets, not created anymore
    LAYERS=new LayerCatalog(dir.resolve("layers.tsv"));
    A=new AnomalyManager(dir.resolve("anomalies.txt"));
    TRAVEL=new LayerWorlds(dir.resolve("copied_chunks.txt"));
    GATES=new RiftGates(dir.resolve("gates.tsv"));
  }
  static void gateTicks(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
    if(GATES!=null)GATES.tick(e);
  }
  static void layerTicks(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
    if(TRAVEL!=null)TRAVEL.tick(e);
  }
  static void randomTravelTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
    if(A!=null)A.tick(e);
  }
  static void dimensionChanged(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e){
    if(e.getFrom().location().getNamespace().equals("timefracture")
       || e.getTo().location().getNamespace().equals("timefracture"))return;
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
  static void registerLayer(ServerPlayer p,String id,String parent){
    if(LAYERS==null)throw new IllegalStateException("Не загружен индекс слоёв");
    var dim=java.util.stream.StreamSupport.stream(
        p.getServer().getAllLevels().spliterator(),false)
        .map(level->level.dimension().location().toString()).toList();
    var anchor=p.blockPosition();
    LAYERS.register(id,parent,p.getUUID(),p.getGameProfile().getName(),
        p.level().dimension().location().toString(),anchor.getX(),anchor.getY(),anchor.getZ(),dim);
  }

  static void eventBranch(ServerPlayer subject,String event){
    if(LAYERS==null||L.frozen)return;
    if(L.next>=LayerWorlds.MAX_SLOTS)return;
    if(L.focus.values().stream().noneMatch(f->f.id.equals(subject.getUUID())))return;
    try{
      String prev=L.latest(subject.getUUID());
      String parent=LAYERS.find(prev)==null?"-":prev;
      var record=L.add(subject.getUUID(),subject.getGameProfile().getName(),parent,event);
      registerLayer(subject,record.id,parent);
      if(TRAVEL!=null)TRAVEL.enqueue(subject,record.id,parent,subject.blockPosition());
      subject.sendSystemMessage(Component.literal("§5[Разветвление времени] §f"+
          event+" → "+record.id+" · начинается постепенное сохранение исходного чанка"));
    }catch(RuntimeException ex){
      subject.sendSystemMessage(Component.literal("§c[TimeFracture] Не удалось зарегистрировать событие: "+ex.getMessage()));
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