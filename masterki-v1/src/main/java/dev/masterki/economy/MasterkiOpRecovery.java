package dev.masterki.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.level.storage.LevelResource;
import com.mojang.brigadier.CommandDispatcher;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * World-scoped emergency OP recovery. Intentionally rejects offline-mode and
 * untrusted usernames. Only authenticated, pre-bound Zunserz can invoke /resetop.
 * The backup is NOT overwritten when the trusted OP disappears from the list.
 */
public final class MasterkiOpRecovery {
    private static final Logger LOG=LogUtils.getLogger();
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final String OWNER="Zunserz";
    private static long lastBackupMs;
    private MasterkiOpRecovery(){}

    private record Op(UUID uuid,String name,int level,boolean bypass){}
    private record Backup(UUID trusted,List<Op> operators){}

    private static Path file(MinecraftServer server){
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("masterki_op_recovery.json");
    }

    private static Backup read(MinecraftServer server){
        Path path=file(server);
        if(!Files.isRegularFile(path))return null;
        try {
            JsonObject obj=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
            UUID trusted=UUID.fromString(obj.get("trustedUuid").getAsString());
            JsonArray entries=obj.getAsJsonArray("operators");
            if(entries==null || entries.isEmpty())return null;
            List<Op> ops=new ArrayList<>();
            for(JsonElement elem:entries){
                JsonObject o=elem.getAsJsonObject();
                UUID id=UUID.fromString(o.get("uuid").getAsString());
                String name=o.get("name").getAsString();
                int level=Math.clamp(o.get("level").getAsInt(),1,4);
                boolean bypass=o.get("bypass").getAsBoolean();
                if(!name.isBlank() && name.length()<=16)ops.add(new Op(id,name,level,bypass));
            }
            return ops.isEmpty()?null:new Backup(trusted,ops);
        }catch(Exception e){
            LOG.warn("Masterki: OP backup cannot be read",e);
            return null;
        }
    }
    public static void backupIfSafe(MinecraftServer server){
        if(!server.isDedicatedServer()||!server.usesAuthentication())return;
        long now=System.currentTimeMillis();
        if(now-lastBackupMs<30_000)return;
        lastBackupMs=now;
        List<Op> operators=new ArrayList<>();
        UUID trustedFromOps=null;
        for(ServerOpListEntry entry:server.getPlayerList().getOps().getEntries()){
            GameProfile profile=entry.getUser();
            if(profile==null||profile.getId()==null||profile.getName()==null)continue;
            operators.add(new Op(profile.getId(),profile.getName(),
                entry.getLevel(),entry.getBypassesPlayerLimit()));
            if(OWNER.equals(profile.getName()))trustedFromOps=profile.getId();
        }
        if(trustedFromOps==null||operators.isEmpty())return;
        Backup previous=read(server);
        if(previous!=null && !previous.trusted().equals(trustedFromOps)){
            LOG.warn("Masterki: refusing to re-bind trusted /resetop UUID to another account");
            return;
        }
        JsonObject root=new JsonObject();
        root.addProperty("trustedName",OWNER);
        root.addProperty("trustedUuid",trustedFromOps.toString());
        root.addProperty("savedAt",now);
        JsonArray list=new JsonArray();
        for(Op op:operators){
            JsonObject e=new JsonObject();
            e.addProperty("uuid",op.uuid().toString());
            e.addProperty("name",op.name());
            e.addProperty("level",op.level());
            e.addProperty("bypass",op.bypass());
            list.add(e);
        }
        root.add("operators",list);
        Path path=file(server),temporary=path.resolveSibling(path.getFileName()+".tmp");
        try{
            Files.createDirectories(path.getParent());
            Files.writeString(temporary,JSON.toJson(root),StandardCharsets.UTF_8);
            Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception ex){LOG.error("Masterki: unable to preserve OP backup",ex);}
    }

    private static boolean trusted(CommandSourceStack sender){
        if(!(sender.getEntity() instanceof ServerPlayer player))return false;
        MinecraftServer server=player.server;
        if(!server.isDedicatedServer()||!server.usesAuthentication())return false;
        if(!OWNER.equals(player.getGameProfile().getName()))return false;
        Backup backup=read(server);
        return backup!=null && backup.trusted().equals(player.getUUID());
    }

    private static int restore(CommandSourceStack sender){
        if(!trusted(sender)){
            sender.sendFailure(Component.literal("Нет доступа к /resetop. Требуется подтверждённый UUID Zunserz и действующая резервная копия OP."));
            return 0;
        }
        MinecraftServer server=sender.getServer();
        Backup backup=read(server);
        if(backup==null)return 0;
        int restored=0;
        for(Op op:backup.operators()){
            GameProfile profile=new GameProfile(op.uuid(),op.name());
            var original=server.getPlayerList().getOps().get(profile);
            if(original!=null && original.getLevel()==op.level() &&
                original.getBypassesPlayerLimit()==op.bypass())continue;
            server.getPlayerList().getOps().add(new ServerOpListEntry(profile,op.level(),op.bypass()));
            ServerPlayer online=server.getPlayerList().getPlayer(op.uuid());
            if(online!=null)server.getPlayerList().sendPlayerPermissionLevel(online);
            restored++;
        }
        LOG.warn("Masterki: /resetop executed by Zunserz ({}) - restored {} operator entries",
            backup.trusted(),restored);
        final int count=restored;
        sender.sendSuccess(()->Component.literal("Восстановлены записи OP: "+count),true);
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(Commands.literal("resetop")
            .requires(src->src.getEntity() instanceof ServerPlayer p &&
                OWNER.equals(p.getGameProfile().getName()))
            .executes(c->restore(c.getSource())));
    }
}
