package dev.timefracturefx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * World-space, shader-filled openings, with physically separated 3D energy layers.
 *
 * v0.5 geometry invariants:
 * - No continuous opaque cone. The sky is covered by the circular GLSL surface,
 *   while separated annular rings and spiral traces provide parallax below it.
 * - Rift outline is deterministic and IDENTICAL on both sides of a thin extrusion.
 *   No per-layer random radius and no enormous radial border offsets.
 * - Background surfaces are drawn first with depth writes; luminous accents are
 *   translucent, with depth testing on and depth writes off.
 */
public final class FxMeshRenderer {
    private static final int SEG = 96;
    private static final long START_NANOS=System.nanoTime();
    private FxMeshRenderer() {}

    /**
     * The portal is painted during NeoForge's AFTER_SKY stage, before
     * world chunks are rendered. It is world-anchored and not a screen overlay.
     * Rift objects remain in AFTER_PARTICLES. This avoids drawing the old
     * solid cone into the ordinary world render stage.
     */
    public static void render(RenderLevelStageEvent e) {
        boolean sky=e.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY;
        boolean world=e.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        if(!sky&&!world) return;
        if(FxClient.effects().isEmpty()) return;
        ShaderInstance shader=sky?FxShaders.PORTAL:FxShaders.RIFT;
        if(shader==null) return;

        PoseStack pose=e.getPoseStack();
        if(pose==null) return;
        Vec3 camera=e.getCamera().getPosition();
        long now=System.currentTimeMillis();
        float seconds=(System.nanoTime()-START_NANOS)*1.0e-9f;
        pose.pushPose();
        pose.translate(-camera.x,-camera.y,-camera.z);
        Matrix4f matrix=pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(!sky);
        try {
            shader(shader,seconds);
            for(FxClient.Fx fx:FxClient.effects()) {
                boolean isPortal=fx.type().equals("portal");
                if(isPortal!=sky)continue;
                if(camera.distanceToSqr(fx.center())>1200.0*1200.0)continue;
                float amount=phase(fx,now);
                if(amount<=0f)continue;
                if(sky){
                    Vec3 relative=fx.center().subtract(camera);
                    var centerUniform=shader.getUniform("PortalCenter");
                    if(centerUniform!=null)centerUniform.set((float)relative.x,(float)relative.y,(float)relative.z);
                    var radiusUniform=shader.getUniform("PortalRadius");
                    if(radiusUniform!=null)radiusUniform.set(112.0f*amount);
                    portal(matrix,fx.center(),amount,seconds);
                }
                else rift(matrix,fx.center(),amount,fx.type().equals("window"),seconds);
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            pose.popPose();
        }
    }

    private static float phase(FxClient.Fx fx,long now) {
        if(fx.ending()>0) {
            float t=(now-fx.ending())/(fx.collapse()?520f:1950f);
            return fx.collapse() ? (float)Math.max(0,1-t*t*t) : Math.max(0,1-t);
        }
        float t=Math.min(1f,(now-fx.born())/1700f);
        return t*t*(3-2*t);
    }

    private static void shader(ShaderInstance s,float seconds){
        RenderSystem.setShader(()->s);
        var u=s.getUniform("Time");
        if(u!=null)u.set(seconds);
    }
    private static BufferBuilder begin() {
        return Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX_COLOR);
    }
    private static void draw(BufferBuilder b){BufferUploader.drawWithShader(b.buildOrThrow());}
    private static void vertex(BufferBuilder b,Matrix4f m,double x,double y,double z,
                               float u,float v,float material,float brightness,float alpha) {
        b.addVertex(m,(float)x,(float)y,(float)z).setUv(u,v)
         .setColor(material,brightness,1f,alpha);
    }
    private static void tri(BufferBuilder b,Matrix4f m,
                            double x0,double y0,double z0,float u0,float v0,
                            double x1,double y1,double z1,float u1,float v1,
                            double x2,double y2,double z2,float u2,float v2,
                            float material,float brightness,float alpha){
        vertex(b,m,x0,y0,z0,u0,v0,material,brightness,alpha);
        vertex(b,m,x1,y1,z1,u1,v1,material,brightness,alpha);
        vertex(b,m,x2,y2,z2,u2,v2,material,brightness,alpha);
    }

    /** Shaded disc. It is the world-space portion of sky that the portal covers. */
    private static void disc(BufferBuilder b,Matrix4f m,Vec3 center,
                             double y,double radius,float alpha){
        for(int k=0;k<SEG;k++){
            double a=2*Math.PI*k/SEG,beta=2*Math.PI*(k+1)/SEG;
            tri(b,m,
                center.x,y,center.z,.5f,.5f,
                center.x+radius*Math.cos(a),y,center.z+radius*Math.sin(a),
                    (float)(.5+.5*Math.cos(a)),(float)(.5+.5*Math.sin(a)),
                center.x+radius*Math.cos(beta),y,center.z+radius*Math.sin(beta),
                    (float)(.5+.5*Math.cos(beta)),(float)(.5+.5*Math.sin(beta)),
                1f,1f,alpha);
        }
    }

    /** Fixed 3D world-space shell. GLSL raymarches through volume, no flat billboard. */
    private static void portal(Matrix4f m,Vec3 center,float scale,float seconds) {
        int latitude=18,longitude=36;
        double radius=112.0*scale;
        RenderSystem.depthMask(false);
        BufferBuilder mesh=begin();
        for(int j=0;j<latitude;j++){
            double a0=Math.PI*j/latitude,a1=Math.PI*(j+1)/latitude;
            for(int k=0;k<longitude;k++){
                double b0=2*Math.PI*k/longitude,b1=2*Math.PI*(k+1)/longitude;
                double x00=center.x+radius*Math.sin(a0)*Math.cos(b0);
                double y00=center.y+radius*Math.cos(a0);
                double z00=center.z+radius*Math.sin(a0)*Math.sin(b0);
                double x01=center.x+radius*Math.sin(a0)*Math.cos(b1);
                double z01=center.z+radius*Math.sin(a0)*Math.sin(b1);
                double x10=center.x+radius*Math.sin(a1)*Math.cos(b0);
                double y10=center.y+radius*Math.cos(a1);
                double z10=center.z+radius*Math.sin(a1)*Math.sin(b0);
                double x11=center.x+radius*Math.sin(a1)*Math.cos(b1);
                double z11=center.z+radius*Math.sin(a1)*Math.sin(b1);
                float u0=(float)k/longitude,u1=(float)(k+1)/longitude;
                float v0=(float)j/latitude,v1=(float)(j+1)/latitude;
                tri(mesh,m,x00,y00,z00,u0,v0,x10,y10,z10,u0,v1,x01,y00,z01,u1,v0,1f,1f,1f);
                tri(mesh,m,x01,y00,z01,u1,v0,x10,y10,z10,u0,v1,x11,y10,z11,u1,v1,1f,1f,1f);
            }
        }
        draw(mesh);
    }

    /** Shared, deterministic XY outline for all rift layers (never self-intersecting). */
    private static double shape(int k,int count){
        double a=2*Math.PI*k/count;
        return .91 + .050*Math.sin(5*a+.7)
                   + .033*Math.sin(11*a+1.8)
                   + .022*Math.cos(17*a+2.1);
    }
    private static double px(Vec3 c,double rx,int k,int count,double offset){
        double a=2*Math.PI*k/count;
        return c.x+(rx*shape(k,count)+offset)*Math.cos(a);
    }
    private static double py(Vec3 c,double ry,int k,int count,double offset){
        double a=2*Math.PI*k/count;
        return c.y+(ry*shape(k,count)+offset)*Math.sin(a);
    }
    private static float u(Vec3 c,double rx,int k,int n){
        return (float)(.5+.5*(px(c,rx,k,n,0)-c.x)/rx);
    }
    private static float v(Vec3 c,double ry,int k,int n){
        return (float)(.5+.5*(py(c,ry,k,n,0)-c.y)/ry);
    }

    private static void rift(Matrix4f m,Vec3 c,float scale,boolean window,float seconds) {
        int count=window?40:56;
        double rx=(window?5.5:12.0)*scale,ry=(window?4.3:17.0)*scale;
        double zFront=c.z-.2,zRear=c.z+1.25*scale;
        double width=(window?.31:.58)*scale;
        // Draw the COMPLETE opening once, with one shared non-crossing outline.
        RenderSystem.depthMask(true);
        BufferBuilder scene=begin();
        for(int k=0;k<count;k++){
            int next=(k+1)%count;
            tri(scene,m,c.x,c.y,c.z,.5f,.5f,
                px(c,rx,k,count,0),py(c,ry,k,count,0),c.z,u(c,rx,k,count),v(c,ry,k,count),
                px(c,rx,next,count,0),py(c,ry,next,count,0),c.z,
                    u(c,rx,next,count),v(c,ry,next,count),
                1f,1f,1f);
        }
        draw(scene);

        // SAME outline at both depths; only a 0.3-0.6-block border strip.
        // No per-depth noise, no large radial multipliers, no spiky triangles.
        RenderSystem.depthMask(false);
        BufferBuilder rim=begin();
        for(int k=0;k<count;k++){
            int next=(k+1)%count;
            double ix0=px(c,rx,k,count,0),iy0=py(c,ry,k,count,0);
            double ix1=px(c,rx,next,count,0),iy1=py(c,ry,next,count,0);
            double ox0=px(c,rx,k,count,width),oy0=py(c,ry,k,count,width);
            double ox1=px(c,rx,next,count,width),oy1=py(c,ry,next,count,width);
            float a0=(float)k/count,a1=(float)(k+1)/count;

            // Front illuminated narrow rim
            tri(rim,m,ix0,iy0,zFront,a0,0,ox0,oy0,zFront,a0,1,
                ix1,iy1,zFront,a1,0,.24f,1f,.92f);
            tri(rim,m,ix1,iy1,zFront,a1,0,ox0,oy0,zFront,a0,1,
                ox1,oy1,zFront,a1,1,.24f,1f,.92f);
            // Rear narrow rim (same contour, no independent rotations)
            tri(rim,m,ix0,iy0,zRear,a0,0,ix1,iy1,zRear,a1,0,
                ox0,oy0,zRear,a0,1,.24f,.75f,.68f);
            tri(rim,m,ix1,iy1,zRear,a1,0,ox1,oy1,zRear,a1,1,
                ox0,oy0,zRear,a0,1,.24f,.75f,.68f);
            // A slender side wall to provide honest 3D depth from oblique angles.
            tri(rim,m,ox0,oy0,zFront,a0,0,ox1,oy1,zFront,a1,0,
                ox0,oy0,zRear,a0,1,.24f,.75f,.54f);
            tri(rim,m,ox1,oy1,zFront,a1,0,ox1,oy1,zRear,a1,1,
                ox0,oy0,zRear,a0,1,.24f,.75f,.54f);
        }
        draw(rim);
    }
}
