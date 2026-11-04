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
 * Render real world-space triangular geometry with TWO actual custom GLSL programs.
 *
 * Portal: filled sky-replacing tunnel cross-section (shader) + deep tapered 3D wall
 *         and layered physical rim; approach/fly through and observe parallax.
 * Rift/window: filled, jagged vertical aperture and extruded 3D illuminated border.
 *
 * "Window" shows PROCEDURAL alternate landscape until TimeFracture has genuine
 * separate world snapshots to provide. It does not pretend to render another world.
 */
public final class FxMeshRenderer {
    private static final int SEG = 72;
    private FxMeshRenderer() {}

    public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var effects = FxClient.effects();
        if(effects.isEmpty()) return;
        ShaderInstance portalShader=FxShaders.PORTAL;
        ShaderInstance riftShader=FxShaders.RIFT;
        if(portalShader==null || riftShader==null) return; // shader not yet registered

        PoseStack pose=e.getPoseStack();
        if(pose==null) return;
        Vec3 camera=e.getCamera().getPosition();
        long now=System.currentTimeMillis();
        float seconds=now/1000.0f;
        pose.pushPose();
        pose.translate(-camera.x,-camera.y,-camera.z);
        Matrix4f mat=pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        try {
            for(FxClient.Fx fx:effects) {
                if(camera.distanceToSqr(fx.center())>650.0*650.0) continue;
                float size=phase(fx,now);
                if(size<=0) continue;
                if(fx.type().equals("portal")) {
                    setupShader(portalShader,seconds);
                    drawPortal(mat,fx.center(),size,seconds);
                } else {
                    setupShader(riftShader,seconds);
                    drawRift(mat,fx.center(),size,fx.type().equals("window"),seconds);
                }
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            pose.popPose();
        }
    }

    private static float phase(FxClient.Fx f,long now){
        if(f.ending()>0){
            float t=(now-f.ending())/(f.collapse()?550f:2100f);
            if(f.collapse()) return (float)Math.max(0,1.0-t*t*t);
            return Math.max(0,1-t);
        }
        float t=(now-f.born())/1700f;
        // overshoot and settle on spawn
        return (float)Math.min(1.0,Math.max(0.0,1.0-Math.pow(Math.max(0,1-t),2.4)));
    }

    private static void setupShader(ShaderInstance shader,float seconds) {
        RenderSystem.setShader(()->shader);
        var u=shader.getUniform("Time");
        if(u!=null)u.set(seconds);
    }

    private static BufferBuilder begin(){
        return Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    private static void draw(BufferBuilder b) {
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void vertex(BufferBuilder b,Matrix4f m,double x,double y,double z,
                               float u,float v,float material,float brightness,float alpha){
        b.addVertex(m,(float)x,(float)y,(float)z)
            .setUv(u,v)
            .setColor(material,brightness,1.0f,alpha);
    }

    private static void tri(BufferBuilder b,Matrix4f m,
                            double ax,double ay,double az,float au,float av,
                            double bx,double by,double bz,float bu,float bv,
                            double cx,double cy,double cz,float cu,float cv,
                            float mat,float brightness,float alpha) {
        vertex(b,m,ax,ay,az,au,av,mat,brightness,alpha);
        vertex(b,m,bx,by,bz,bu,bv,mat,brightness,alpha);
        vertex(b,m,cx,cy,cz,cu,cv,mat,brightness,alpha);
    }

    private static void portalCap(BufferBuilder b,Matrix4f m,Vec3 c,
                                  double y,double radius,float brightness,float alpha) {
        for(int i=0;i<SEG;i++){
            double a=i*2*Math.PI/SEG, next=(i+1)*2*Math.PI/SEG;
            tri(b,m,c.x,y,c.z,.5f,.5f,
                c.x+radius*Math.cos(a),y,c.z+radius*Math.sin(a),
                    (float)(.5+.5*Math.cos(a)),(float)(.5+.5*Math.sin(a)),
                c.x+radius*Math.cos(next),y,c.z+radius*Math.sin(next),
                    (float)(.5+.5*Math.cos(next)),(float)(.5+.5*Math.sin(next)),
                1.0f,brightness,alpha);
        }
    }

    private static void drawPortal(Matrix4f m,Vec3 center,float scale,float t) {
        BufferBuilder b=begin();
        double base=center.y;
        double mouthRadius=30.0*scale;
        // The filled disc replaces the visible sky INSIDE the world-space mouth.
        // The GLSL code itself draws a perspective vortex with its own "depth".
        portalCap(b,m,center,base+1.0,mouthRadius,1f,.97f);

        // Tapered 3D tunnel: continuous physical wall that can be approached and
        // viewed from underneath or from the side, rather than a 2D overlay.
        int layers=14;
        for(int layer=0;layer<layers;layer++){
            double f0=(double)layer/layers,f1=(double)(layer+1)/layers;
            double y0=base-5+f0*42.0,y1=base-5+f1*42.0;
            double r0=mouthRadius*(1-.71*f0);
            double r1=mouthRadius*(1-.71*f1);
            float alpha=(float)(.92-.27*f0);
            for(int k=0;k<SEG;k++){
                double a=2*Math.PI*k/SEG + t*.04,na=2*Math.PI*(k+1)/SEG+t*.04;
                float u0=(float)k/SEG,u1=(float)(k+1)/SEG;
                double ax=center.x+r0*Math.cos(a),az=center.z+r0*Math.sin(a);
                double bx=center.x+r0*Math.cos(na),bz=center.z+r0*Math.sin(na);
                double cx=center.x+r1*Math.cos(a),cz=center.z+r1*Math.sin(a);
                double dx=center.x+r1*Math.cos(na),dz=center.z+r1*Math.sin(na);
                tri(b,m,ax,y0,az,u0,(float)f0,bx,y0,bz,u1,(float)f0,
                    cx,y1,cz,u0,(float)f1,.25f,.95f,alpha);
                tri(b,m,bx,y0,bz,u1,(float)f0,dx,y1,dz,u1,(float)f1,
                    cx,y1,cz,u0,(float)f1,.25f,.95f,alpha);
            }
        }

        // A physical halo torus around the mouth, not a particle outline.
        for(int layer=0;layer<5;layer++){
            double top=(layer/4.0)*Math.PI*2;
            double next=((layer+1)/4.0)*Math.PI*2;
            for(int k=0;k<SEG;k++){
                double a=2*Math.PI*k/SEG,na=2*Math.PI*(k+1)/SEG;
                double rr0=mouthRadius+1.3*Math.cos(top),rr1=mouthRadius+1.3*Math.cos(next);
                double yy0=base-5+1.3*Math.sin(top),yy1=base-5+1.3*Math.sin(next);
                float v0=(float)layer/5,v1=(float)(layer+1)/5;
                tri(b,m,center.x+rr0*Math.cos(a),yy0,center.z+rr0*Math.sin(a),(float)k/SEG,v0,
                    center.x+rr0*Math.cos(na),yy0,center.z+rr0*Math.sin(na),(float)(k+1)/SEG,v0,
                    center.x+rr1*Math.cos(a),yy1,center.z+rr1*Math.sin(a),(float)k/SEG,v1,
                    .25f,1f,1f);
                tri(b,m,center.x+rr0*Math.cos(na),yy0,center.z+rr0*Math.sin(na),(float)(k+1)/SEG,v0,
                    center.x+rr1*Math.cos(na),yy1,center.z+rr1*Math.sin(na),(float)(k+1)/SEG,v1,
                    center.x+rr1*Math.cos(a),yy1,center.z+rr1*Math.sin(a),(float)k/SEG,v1,
                    .25f,1f,1f);
            }
        }
        draw(b);
    }

    private static double riftNoise(int i,int layer){
        return .82+.17*Math.sin(i*3.51+layer*.32)+.09*Math.cos(i*8.31+layer*.13);
    }

    private static void drawRift(Matrix4f m,Vec3 c,float scale,boolean window,float t){
        BufferBuilder b=begin();
        int count=window?22:34;
        double rx=scale*(window?5.7:15.0),ry=scale*(window?4.3:22.0);
        // Solid scene inside the aperture; procedural "other reality" is rendered
        // by custom shader, not a second actually simulated Minecraft dimension.
        for(int i=0;i<count;i++){
            double a=2*Math.PI*i/count,next=2*Math.PI*(i+1)/count;
            double f=riftNoise(i,0),f2=riftNoise(i+1,0);
            tri(b,m,c.x,c.y,c.z,.5f,.5f,
                c.x+rx*f*Math.cos(a),c.y+ry*f*Math.sin(a),c.z,
                    (float)(.5+.5*f*Math.cos(a)),(float)(.5+.5*f*Math.sin(a)),
                c.x+rx*f2*Math.cos(next),c.y+ry*f2*Math.sin(next),c.z,
                    (float)(.5+.5*f2*Math.cos(next)),(float)(.5+.5*f2*Math.sin(next)),
                1f,1f,.99f);
        }
        // Extruded jagged rim; this generates actual silhouette/thickness/parallax.
        for(int layer=0;layer<6;layer++){
            float l0=layer/6f,l1=(layer+1)/6f;
            double z0=c.z+layer*.75,z1=c.z+(layer+1)*.75;
            for(int i=0;i<count;i++){
                double a=2*Math.PI*i/count,next=2*Math.PI*(i+1)/count;
                double f=riftNoise(i,layer),fn=riftNoise(i+1,layer);
                double w0=1.0+.14*Math.sin(t*1.7+i*.5+layer),w1=1.0+.14*Math.sin(t*1.7+i*.5+layer+1);
                double ra=rx*f*w0,rb=ry*f*w0,rc=rx*fn*w1,rd=ry*fn*w1;
                // Border ribbons: four triangles around the aperture edge
                double x0=c.x+ra*Math.cos(a),y0=c.y+rb*Math.sin(a);
                double x1=c.x+rc*Math.cos(next),y1=c.y+rd*Math.sin(next);
                double thick=window?.18:.3;
                double X0=c.x+(ra+thick*rx)*Math.cos(a),Y0=c.y+(rb+thick*ry)*Math.sin(a);
                double X1=c.x+(rc+thick*rx)*Math.cos(next),Y1=c.y+(rd+thick*ry)*Math.sin(next);
                float u=(float)i/count,U=(float)(i+1)/count;
                tri(b,m,x0,y0,z0,u,l0,x1,y1,z0,U,l0,X0,Y0,z0,u,l1,
                    .25f,1f,.85f);
                tri(b,m,x1,y1,z0,U,l0,X1,Y1,z0,U,l1,X0,Y0,z0,u,l1,
                    .25f,1f,.85f);
                tri(b,m,x0,y0,z0,u,l0,x1,y1,z0,U,l0,x0,y0,z1,u,l1,
                    .25f,.91f,.7f);
                tri(b,m,x1,y1,z0,U,l0,x1,y1,z1,U,l1,x0,y0,z1,u,l1,
                    .25f,.91f,.7f);
            }
        }
        draw(b);
    }
}
