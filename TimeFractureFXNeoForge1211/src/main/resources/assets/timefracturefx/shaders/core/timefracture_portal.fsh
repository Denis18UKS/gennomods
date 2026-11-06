#version 150
uniform float Time;
uniform vec3 PortalCenter;
uniform float PortalRadius;
uniform float Samples;
in vec3 cameraRelative;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

// All samples live in world-relative coordinates, not fixed 2D screen UVs.
// Cost capped to 8 volume steps to reduce GPU load.
float h(vec3 p){return fract(sin(dot(p,vec3(73.37,117.41,249.17)))*43758.5453);}
float n3(vec3 p){
 vec3 a=floor(p), f=fract(p);
 f=f*f*(3.0-2.0*f);
 return mix(
   mix(mix(h(a),h(a+vec3(1,0,0)),f.x),
       mix(h(a+vec3(0,1,0)),h(a+vec3(1,1,0)),f.x),f.y),
   mix(mix(h(a+vec3(0,0,1)),h(a+vec3(1,0,1)),f.x),
       mix(h(a+vec3(0,1,1)),h(a+vec3(1,1,1)),f.x),f.y),f.z);
}
void main(){
 float R=max(1.0,PortalRadius);
 vec3 eye=-PortalCenter;
 vec3 ray=normalize(cameraRelative);
 float reach=min(length(cameraRelative),R*2.0);
 vec3 sum=vec3(.003,.002,.018);
 for(int i=0;i<4;i++){
   if(float(i)>=Samples)break;
   float t=(float(i)+.5)/max(1.0,Samples);
   vec3 p=(eye+ray*(t*reach))/R;
   float angle=atan(p.z,p.x);
   float r=length(p.xz);
   float twist=angle+3.8*log(r+.21)+p.y*2.0-Time*.21;
   vec3 q=vec3(cos(twist)*r,p.y*.85,sin(twist)*r)*5.2;
   float neb=n3(q+vec3(Time*.045,-Time*.025,0.0));
   // Skip a second expensive 3D noise field in low-FPS modes.
   float fine=Samples>2.5 ? n3(q*2.1+vec3(9.4,3.1,12.7))
                          : (.5+.5*sin(q.x*2.2+q.y*4.0+q.z*2.5));
   float bands=pow(.5+.5*sin(twist*5.4+fine*2.6-Time*.37),6.0);
   float cloud=smoothstep(.25,.77,neb*.66+fine*.34+bands*.18);
   float core=1.0-smoothstep(.04,.23,r);
   vec3 c=mix(vec3(.49,.06,.74),vec3(.08,.39,.90),
              .5+.5*sin(twist*1.7+p.y*7.0));
   sum+=c*cloud*(.20+.52*bands)*(1.0-.8*core)/max(1.0,Samples);
 }
 vec3 onShell=(cameraRelative-PortalCenter)/R;
 float stars=step(.9973,h(floor(onShell*290.0)));
 sum+=vec3(.90,.91,1.0)*stars*.72;
 float central=length((eye+ray*reach).xz)/R;
 sum=mix(sum,vec3(.002,.001,.016),(.65*(1.0-smoothstep(.02,.16,central))));
 sum+=vec3(.11,.035,.26)*exp(-abs(central-.21)*18.0)*.36;
 fragColor=vec4(clamp(sum,vec3(.002),vec3(1.0)),tint.a);
}
