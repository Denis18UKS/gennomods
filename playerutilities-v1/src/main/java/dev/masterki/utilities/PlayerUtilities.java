package dev.masterki.utilities;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@Mod("playerutilities")
public final class PlayerUtilities {
    private static final Map<UUID, Request> REQUESTS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private record Request(UUID sender, long expiresAt) {}

    public PlayerUtilities(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onCommands);
        NeoForge.EVENT_BUS.addListener(this::onDeath);
    }

    private void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Saved deaths = Saved.of(player.serverLevel());
        Death entry = new Death(Instant.now().toEpochMilli(),
            player.serverLevel().dimension().location().toString(), player.blockPosition(),
            event.getSource().getLocalizedDeathMessage(player).getString());
        deaths.append(player.getUUID(), entry);
    }

    private void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> root = event.getDispatcher();
        root.register(Commands.literal("tpa").then(Commands.argument("player", EntityArgument.player())
            .executes(c -> request(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player")))));
        root.register(Commands.literal("tpaccept").executes(c -> accept(c.getSource().getPlayerOrException())));
        root.register(Commands.literal("tpdeny").executes(c -> deny(c.getSource().getPlayerOrException())));
        root.register(Commands.literal("death")
            .then(Commands.literal("list")
                .executes(c -> list(c.getSource().getPlayerOrException(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(c -> list(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "page")))))
            .then(Commands.literal("last").executes(c -> last(c.getSource().getPlayerOrException())))
            .then(Commands.literal("remove")
                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                    .executes(c -> remove(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c,"number")))))
            .then(Commands.literal("clear")
                .executes(c -> {c.getSource().getPlayerOrException().sendSystemMessage(
                    Component.literal("Для удаления всей истории введи /death clear confirm").withStyle(ChatFormatting.YELLOW));return 1;})
                .then(Commands.literal("confirm").executes(c -> clear(c.getSource().getPlayerOrException())))));
    }

    private static int request(ServerPlayer from, ServerPlayer to) {
        if (from.getUUID().equals(to.getUUID())) { send(from,"Нельзя отправить запрос самому себе.",ChatFormatting.RED);return 0; }
        long now=System.currentTimeMillis();
        if (COOLDOWNS.getOrDefault(from.getUUID(),0L)>now){send(from,"Подожди 5 секунд перед следующим запросом.",ChatFormatting.YELLOW);return 0;}
        COOLDOWNS.put(from.getUUID(),now+5000);
        Request existing=REQUESTS.get(to.getUUID());
        if(existing!=null&&existing.expiresAt>now){send(from,"У этого игрока уже есть ожидающий запрос.",ChatFormatting.YELLOW);return 0;}
        REQUESTS.put(to.getUUID(), new Request(from.getUUID(), System.currentTimeMillis()+60_000));
        send(from,"Запрос телепортации отправлен игроку "+to.getGameProfile().getName()+".",ChatFormatting.YELLOW);
        to.sendSystemMessage(Component.literal(from.getGameProfile().getName()+" хочет телепортироваться к вам. ")
            .withStyle(ChatFormatting.GOLD)
            .append(button("[Принять]", "/tpaccept", ChatFormatting.GREEN))
            .append(Component.literal(" "))
            .append(button("[Отклонить]", "/tpdeny", ChatFormatting.RED)));
        return 1;
    }
    private static MutableComponent button(String title, String command, ChatFormatting color) {
        return Component.literal(title).withStyle(style -> style.withColor(color)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,command))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal(command))));
    }
    private static int accept(ServerPlayer to) {
        Request req = REQUESTS.remove(to.getUUID());
        if (req == null || req.expiresAt < System.currentTimeMillis()) {send(to,"Нет активного запроса.",ChatFormatting.RED);return 0;}
        ServerPlayer from = to.server.getPlayerList().getPlayer(req.sender);
        if (from == null) {send(to,"Отправитель вышел из игры.",ChatFormatting.RED);return 0;}
        from.teleportTo(to.serverLevel(),to.getX(),to.getY(),to.getZ(), Set.of(),to.getYRot(),to.getXRot());
        send(to,"Игрок "+from.getGameProfile().getName()+" телепортирован к вам.",ChatFormatting.GREEN);
        send(from,"Телепортация принята.",ChatFormatting.GREEN);
        return 1;
    }
    private static int deny(ServerPlayer to) {
        Request req=REQUESTS.remove(to.getUUID());
        if(req==null) {send(to,"Нет активного запроса.",ChatFormatting.RED);return 0;}
        ServerPlayer from=to.server.getPlayerList().getPlayer(req.sender);
        if(from!=null)send(from,to.getGameProfile().getName()+" отклонил запрос.",ChatFormatting.RED);
        send(to,"Запрос отклонён.",ChatFormatting.YELLOW);
        return 1;
    }
    private static int last(ServerPlayer p) {
        List<Death> deaths=Saved.of(p.serverLevel()).get(p.getUUID());
        if(deaths.isEmpty()) {send(p,"История смертей пуста.",ChatFormatting.GRAY);return 0;}
        show(p,deaths.size(),deaths.get(deaths.size()-1));
        return 1;
    }
    private static int list(ServerPlayer p,int page) {
        List<Death> deaths=Saved.of(p.serverLevel()).get(p.getUUID());
        int count=deaths.size(), pages=Math.max(1,(count+6)/7);
        if(page>pages)page=pages;
        p.sendSystemMessage(Component.literal("☠ История смертей | "+count+" записей | страница "+page+"/"+pages).withStyle(ChatFormatting.GOLD));
        if(count==0){send(p,"Пока нет смертей.",ChatFormatting.GRAY);return 1;}
        for(int i=count-1-(page-1)*7;i>=0 && i>=count-page*7;i--)show(p,i+1,deaths.get(i));
        if(page>1||page<pages){
            MutableComponent nav=Component.empty();
            if(page>1)nav.append(button("[← Назад]", "/death list "+(page-1),ChatFormatting.YELLOW));
            if(page>1&&page<pages)nav.append(Component.literal("    "));
            if(page<pages)nav.append(button("[Далее →]", "/death list "+(page+1),ChatFormatting.YELLOW));
            p.sendSystemMessage(nav);
        }
        return 1;
    }
    private static void show(ServerPlayer p,int number,Death death) {
        String date=Instant.ofEpochMilli(death.epoch).atZone(ZoneId.systemDefault()).format(DATE);
        String loc=death.pos.getX()+", "+death.pos.getY()+", "+death.pos.getZ();
        p.sendSystemMessage(Component.literal("#"+number+"  "+date+"  "+death.dimension+"  ")
            .withStyle(ChatFormatting.WHITE)
            .append(Component.literal("["+loc+"]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD,loc))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal("Скопировать координаты")))))
            .append(Component.literal(" | "+death.reason).withStyle(ChatFormatting.GRAY)));
    }
    private static int remove(ServerPlayer p,int n) {
        if(Saved.of(p.serverLevel()).remove(p.getUUID(),n-1)){send(p,"Метка #"+n+" удалена.",ChatFormatting.GREEN);return 1;}
        send(p,"Нет метки с таким номером.",ChatFormatting.RED);return 0;
    }
    private static int clear(ServerPlayer p) {
        int count=Saved.of(p.serverLevel()).clear(p.getUUID());
        send(p,"Удалено записей: "+count,ChatFormatting.YELLOW);return 1;
    }
    private static void send(Player p,String s,ChatFormatting style){p.sendSystemMessage(Component.literal(s).withStyle(style));}

    private record Death(long epoch,String dimension,BlockPos pos,String reason){}
    private static final class Saved extends SavedData {
        private final Map<UUID,List<Death>> history = new HashMap<>();
        static Saved of(ServerLevel level){
            return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(Saved::new,Saved::load), "playerutilities_deaths");
        }
        void append(UUID id,Death death){history.computeIfAbsent(id,k->new ArrayList<>()).add(death);setDirty();}
        List<Death> get(UUID id){return history.getOrDefault(id,List.of());}
        boolean remove(UUID id,int index){
            List<Death> list=history.get(id);
            if(list==null||index<0||index>=list.size())return false;
            list.remove(index);setDirty();return true;
        }
        int clear(UUID id){List<Death> list=history.remove(id);setDirty();return list==null?0:list.size();}
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
            ListTag entries=new ListTag();
            history.forEach((uuid,list)->{
                CompoundTag person=new CompoundTag();
                person.putUUID("Owner",uuid);
                ListTag deaths=new ListTag();
                for(Death death:list){
                    CompoundTag d=new CompoundTag();
                    d.putLong("Time",death.epoch);
                    d.putString("Dimension",death.dimension);
                    d.putInt("X",death.pos.getX()); d.putInt("Y",death.pos.getY()); d.putInt("Z",death.pos.getZ());
                    d.putString("Reason",death.reason);
                    deaths.add(d);
                }
                person.put("Deaths",deaths); entries.add(person);
            });
            tag.put("Players",entries);return tag;
        }
        static Saved load(CompoundTag tag,HolderLookup.Provider registries){
            Saved saved=new Saved();
            ListTag entries=tag.getList("Players",Tag.TAG_COMPOUND);
            for(int i=0;i<entries.size();i++){
                CompoundTag person=entries.getCompound(i);
                if(!person.hasUUID("Owner"))continue;
                List<Death> list=new ArrayList<>();
                ListTag deaths=person.getList("Deaths",Tag.TAG_COMPOUND);
                for(int j=0;j<deaths.size();j++){
                    CompoundTag d=deaths.getCompound(j);
                    list.add(new Death(d.getLong("Time"),d.getString("Dimension"),
                        new BlockPos(d.getInt("X"),d.getInt("Y"),d.getInt("Z")),d.getString("Reason")));
                }
                saved.history.put(person.getUUID("Owner"),list);
            }
            return saved;
        }
    }
}
