package dev.timefracturefx;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/** GPU-drawn WORLD-SPACE 3D wireframes. GLSL experiments are NOT wired to runtime. */
@Mod.EventBusSubscriber(modid="timefracturefx",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public class FxRenderer {
  record Fx(String kind,double x,double y,double z,long created,long closing,String state){}
  static final List<Fx> ACTIVE=new CopyOnWriteArrayList<>();
  public static void receive(TimeFractureFXMod.Signal s){
    long now=System.currentTimeMillis();
    switch(s.type()){
      case "portal","rift","window"->ACTIVE.add(new Fx(s.type(),s.x(),s.y(),s.z(),now,0,"open"));
      case "close","collapse"->{
        for(int i=0;i<ACTIVE.size();i++){
          var old=ACTIVE.get(i);
          ACTIVE.set(i,new Fx(old.kind,old.x,old.y,old.z,old.created,now,s.type()));
        }
      }
      case "stop"->ACTIVE.clear();
    }
  }
  @SubscribeEvent public static void render(RenderLevelStageEvent event){
    if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||ACTIVE.isEmpty())return;
    var camera=event.getCamera().getPosition();
    PoseStack pose=event.getPoseStack();pose.pushPose();
    pose.translate(-camera.x,-camera.y,-camera.z);
    Matrix4f m=pose.last().pose();
    RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
    RenderSystem.disableCull();RenderSystem.lineWidth(2.8f);
    RenderSystem.setShader(GameRenderer::getPositionColorShader);
    BufferBuilder b=Tesselator.getInstance().getBuilder();
    b.begin(VertexFormat.Mode.LINES,DefaultVertexFormat.POSITION_COLOR);
    long time=System.currentTimeMillis();
    ACTIVE.removeIf(v->v.closing>0&&time-v.closing>2000);
    for(Fx f:ACTIVE){
      float v=f.closing>0?Math.max(0,1-(time-f.closing)/(f.state.equals("collapse")?400f:2000f)):
          Math.min(1,(time-f.created)/1400f);
      if(v<=0)continue;
      if(f.kind.equals("portal"))portal(b,m,f,v,time);
      else crack(b,m,f,v,f.kind.equals("window"));
    }
    BufferUploader.drawWithShader(b.end());
    RenderSystem.lineWidth(1f);RenderSystem.enableCull();RenderSystem.disableBlend();
    pose.popPose();
  }
  private static void line(BufferBuilder b,Matrix4f m,double x,double y,double z,double X,double Y,double Z,float r,float g,float blue){
    b.vertex(m,(float)x,(float)y,(float)z).color(r,g,blue,0.95f).endVertex();
    b.vertex(m,(float)X,(float)Y,(float)Z).color(r,g,blue,0.95f).endVertex();
  }
  private static void portal(BufferBuilder b,Matrix4f m,Fx f,float v,long time){
    // Many rings at different world-space Y, creating a fly-through tunnel.
    for(int layer=0;layer<12;layer++){
      double radius=(17-layer*.9)*v, y=f.y-layer*2.5;
      double spin=time/3300.0*(layer%2==0?1:-1);
      for(int j=0;j<96;j++){
        double t=j*2*Math.PI/96+spin,a=(j+1)*2*Math.PI/96+spin;
        line(b,m,f.x+radius*Math.cos(t),y,f.z+radius*Math.sin(t),f.x+radius*Math.cos(a),y,f.z+radius*Math.sin(a),.57f,.35f+layer*.02f,1f);
      }
    }
  }
  private static void crack(BufferBuilder b,Matrix4f m,Fx f,float v,boolean window){
    int count=window?11:21;double rx=(window?5:12)*v,ry=(window?4:17)*v;
    for(int depth=0;depth<5;depth++){
      for(int j=0;j<count;j++){
        double a=2*Math.PI*j/count,c=2*Math.PI*(j+1)/count;
        double r=1+.12*Math.sin(j*3.7+depth),r2=1+.12*Math.sin((j+1)*3.7+depth);
        line(b,m,f.x+rx*r*Math.cos(a),f.y+ry*r*Math.sin(a),f.z+depth*.55,
            f.x+rx*r2*Math.cos(c),f.y+ry*r2*Math.sin(c),f.z+depth*.55,
            .7f,.35f+depth*.1f,1f);
      }
    }
  }
}