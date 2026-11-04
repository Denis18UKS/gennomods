package dev.timefracturefx;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;

@Mod("timefracturefx")
public class TimeFractureFXMod {
  static final String VERSION="1";
  static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("timefracturefx","visuals"),
        ()->VERSION,VERSION::equals,VERSION::equals);
  public TimeFractureFXMod(){
    CHANNEL.registerMessage(0,Signal.class,Signal::write,Signal::read,Signal::handle);
    MinecraftForge.EVENT_BUS.addListener(TimeFractureFXMod::commands);
  }
  static void commands(RegisterCommandsEvent e){
    var root=Commands.literal("tfx").requires(s->s.hasPermission(2));
    var preview=Commands.literal("preview");
    for(String type:new String[]{"portal","rift","window","close","collapse","stop"}){
      preview.then(Commands.literal(type).executes(c->{
        var source=c.getSource();var p=source.getPosition();
        double y=p.y+(type.equals("portal")?45:type.equals("rift")?25:3);
        CHANNEL.send(PacketDistributor.ALL.noArg(),new Signal(type,p.x,y,p.z));
        source.sendSuccess(()->Component.literal("§d[TimeFractureFX] §f"+type+" preview"),false);return 1;
      }));
    }
    e.getDispatcher().register(root.then(preview));
  }
  public record Signal(String type,double x,double y,double z){
    public static void write(Signal s,FriendlyByteBuf b){b.writeUtf(s.type);b.writeDouble(s.x);b.writeDouble(s.y);b.writeDouble(s.z);}
    public static Signal read(FriendlyByteBuf b){return new Signal(b.readUtf(),b.readDouble(),b.readDouble(),b.readDouble());}
    public static void handle(Signal s,Supplier<NetworkEvent.Context> context){
      var ctx=context.get();
      ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->FxRenderer.receive(s)));
      ctx.setPacketHandled(true);
    }
  }
}