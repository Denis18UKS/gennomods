package dev.masterki.economy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MasterkiLayout extends SavedData {
    public static final String[] IDS = {
        "inventory_wallet", "inventory_coin", "inventory_balance",
        "hud_wallet", "hud_coin", "hud_balance",
        "shop_balance", "shop_gear", "shop_delivery", "shop_tasks"
    };
    private final Map<String, MasterkiNetwork.LayoutEntry> elements = new LinkedHashMap<>();

    private MasterkiLayout() {}

    public static MasterkiLayout of(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
            .computeIfAbsent(new SavedData.Factory<>(MasterkiLayout::new, MasterkiLayout::load), "masterki_layout");
    }

    public static boolean known(String id) {
        for (String key : IDS) if (key.equals(id)) return true;
        return false;
    }

    private static MasterkiNetwork.LayoutEntry original(String id) {
        // Slight leftward shift from the previous version; other elements are centered on their anchors.
        int dx = "inventory_wallet".equals(id) ? -32 : 0;
        return new MasterkiNetwork.LayoutEntry(id, dx, 0, 100, true);
    }

    public MasterkiNetwork.LayoutEntry get(String id) {
        return elements.getOrDefault(id, original(id));
    }

    public java.util.List<MasterkiNetwork.LayoutEntry> entries() {
        java.util.List<MasterkiNetwork.LayoutEntry> list = new java.util.ArrayList<>();
        for (String id : IDS) list.add(get(id));
        return list;
    }

    public void apply(String id, int x, int y, int scale, boolean visible) {
        if (!known(id)) return;
        elements.put(id, new MasterkiNetwork.LayoutEntry(id,
            Math.clamp(x,-4096,4096), Math.clamp(y,-4096,4096),
            Math.clamp(scale,50,250), visible));
        setDirty();
    }

    public void reset(String id) {
        if ("all".equals(id)) elements.clear();
        else if (known(id)) elements.remove(id);
        setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (var entry : elements.values()) {
            CompoundTag part = new CompoundTag();
            part.putString("Id",entry.id());
            part.putInt("X",entry.x());
            part.putInt("Y",entry.y());
            part.putInt("Scale",entry.scale());
            part.putBoolean("Visible",entry.visible());
            list.add(part);
        }
        tag.put("Elements",list);
        return tag;
    }

    private static MasterkiLayout load(CompoundTag tag, HolderLookup.Provider lookup) {
        MasterkiLayout result = new MasterkiLayout();
        ListTag list = tag.getList("Elements",Tag.TAG_COMPOUND);
        for (int n=0;n<list.size();n++) {
            CompoundTag part=list.getCompound(n);
            String id=part.getString("Id");
            if (!known(id)) continue;
            result.elements.put(id,new MasterkiNetwork.LayoutEntry(id,
                Math.clamp(part.getInt("X"),-4096,4096),
                Math.clamp(part.getInt("Y"),-4096,4096),
                Math.clamp(part.getInt("Scale"),50,250),part.getBoolean("Visible")));
        }
        return result;
    }

    private static void inform(CommandSourceStack sender, String text) {
        sender.sendSuccess(()->Component.literal(text),false);
    }

    private static int modify(CommandSourceStack sender,String operation,String id,int a,int b) {
        if (!known(id)) {
            sender.sendFailure(Component.literal("Неизвестный индикатор: "+id));
            return 0;
        }
        MasterkiLayout layout=of(sender.getLevel());
        var old=layout.get(id);
        switch (operation) {
            case "move" -> layout.apply(id,old.x()+a,old.y()+b,old.scale(),old.visible());
            case "setpos" -> layout.apply(id,a,b,old.scale(),old.visible());
            case "scale" -> layout.apply(id,old.x(),old.y(),a,old.visible());
            case "show" -> layout.apply(id,old.x(),old.y(),old.scale(),true);
            case "hide" -> layout.apply(id,old.x(),old.y(),old.scale(),false);
            case "reset" -> layout.reset(id);
            default -> {return 0;}
        }
        inform(sender, "Masterki layout "+id+": "+operation+" (автосохранение в мире)");
        syncAll(sender.getLevel());
        return 1;
    }

    private static void syncAll(ServerLevel level) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers())
            Masterki.sync(player,false,"");
    }

    public static boolean editorAction(ServerPlayer player, String kind, String id, int value) {
        if (!kind.startsWith("layout_")) return false;
        if (!player.createCommandSourceStack().hasPermission(2)) return true;
        if (!known(id)) return true;
        var layout=of(player.serverLevel());
        var old=layout.get(id);
        switch (kind) {
            case "layout_x" -> layout.apply(id,value,old.y(),old.scale(),old.visible());
            case "layout_y" -> layout.apply(id,old.x(),value,old.scale(),old.visible());
            case "layout_scale" -> layout.apply(id,old.x(),old.y(),value,old.visible());
            case "layout_visible" -> layout.apply(id,old.x(),old.y(),old.scale(),value!=0);
            case "layout_reset" -> layout.reset(id);
            default -> {return true;}
        }
        syncAll(player.serverLevel());
        return true;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root=Commands.literal("masterki")
            .then(Commands.literal("dev").requires(s->s.hasPermission(2))
                .then(Commands.literal("layout").executes(c->{
                    var lay=of(c.getSource().getLevel());
                    for (var row:lay.entries()) inform(c.getSource(),
                        row.id()+": x="+row.x()+" y="+row.y()+" scale="+row.scale()+"% visible="+row.visible());
                    return 1;
                }))
                .then(Commands.literal("edit").executes(c->{
                    Masterki.sync(c.getSource().getPlayerOrException(),true,"layout_editor");
                    return 1;
                }))
                .then(Commands.literal("save").executes(c->{
                    of(c.getSource().getLevel()).setDirty();
                    inform(c.getSource(),"Положения индикаторов сохранены в мире");
                    return 1;
                }))
                .then(Commands.literal("reset")
                    .then(Commands.argument("id",StringArgumentType.word()).executes(c->{
                        String id=StringArgumentType.getString(c,"id");
                        if ("all".equals(id)) {
                            of(c.getSource().getLevel()).reset(id);syncAll(c.getSource().getLevel());
                            return 1;
                        }
                        return modify(c.getSource(),"reset",id,0,0);
                    })))
                .then(Commands.literal("show")
                    .then(Commands.argument("id",StringArgumentType.word()).executes(c->
                        modify(c.getSource(),"show",StringArgumentType.getString(c,"id"),0,0))))
                .then(Commands.literal("hide")
                    .then(Commands.argument("id",StringArgumentType.word()).executes(c->
                        modify(c.getSource(),"hide",StringArgumentType.getString(c,"id"),0,0))))
                .then(Commands.literal("scale")
                    .then(Commands.argument("id",StringArgumentType.word())
                        .then(Commands.argument("value",DoubleArgumentType.doubleArg(0.5,2.5))
                            .executes(c->modify(c.getSource(),"scale",StringArgumentType.getString(c,"id"),
                                (int)Math.round(DoubleArgumentType.getDouble(c,"value")*100),0)))))
                .then(Commands.literal("move")
                    .then(Commands.argument("id",StringArgumentType.word())
                        .then(Commands.argument("x",IntegerArgumentType.integer(-4096,4096))
                            .then(Commands.argument("y",IntegerArgumentType.integer(-4096,4096))
                                .executes(c->modify(c.getSource(),"move",
                                    StringArgumentType.getString(c,"id"),
                                    IntegerArgumentType.getInteger(c,"x"),
                                    IntegerArgumentType.getInteger(c,"y")))))))
                .then(Commands.literal("setpos")
                    .then(Commands.argument("id",StringArgumentType.word())
                        .then(Commands.argument("x",IntegerArgumentType.integer(-4096,4096))
                            .then(Commands.argument("y",IntegerArgumentType.integer(-4096,4096))
                                .executes(c->modify(c.getSource(),"setpos",
                                    StringArgumentType.getString(c,"id"),
                                    IntegerArgumentType.getInteger(c,"x"),
                                    IntegerArgumentType.getInteger(c,"y"))))))));
        dispatcher.register(root);
    }
}
