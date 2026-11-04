#version 150
uniform float Time;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

float hash(vec2 p){return fract(sin(dot(p,vec2(37.719,91.347)))*43758.5453);}
float noise(vec2 p){
 vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
 return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);
}
float hills(float x){return .28+.12*sin(x*12.0)+.045*sin(x*43.0)+.06*noise(vec2(x*34.0,2.0));}
void main(){
    vec2 p=texCoord;
    float t=Time;
    float storm=noise(p*7.0+vec2(t*.065,-t*.02));
    float mist=noise(p*18.0-vec2(t*.13,0));
    vec3 sky=mix(vec3(.035,.014,.12),vec3(.26,.08,.45),p.y);
    sky+=vec3(.08,.10,.28)*storm;
    // An artificial alternate-reality landscape, NOT a second Minecraft world.
    float horizon=hills(p.x);
    if(p.y<horizon){
      float d=(horizon-p.y);
      sky=mix(vec3(.02,.03,.085),vec3(.15,.09,.29),clamp(d*.8+mist*.2,0.0,1.0));
    }
    float stars=step(.994,hash(floor(p*vec2(148.0,128.0)+vec2(t*.005,0.0))));
    sky+=vec3(.8,.9,1.0)*stars*step(horizon,p.y);
    float moon=1.0-smoothstep(.022,.026,distance(p,vec2(.74,.73)));
    sky+=moon*vec3(.55,.58,1.0);
    float vein=pow(.5+.5*sin(p.y*40.0+p.x*21.0-t*2.5+mist*7.0),14.0);
    // Edge surface: tint.r=.25, interior tint.r=1.
    if(tint.r<.6){
      sky=vec3(.19,.045,.36)+vec3(.42,.18,.58)*vein;
      sky+=vec3(.16,.5,.8)*pow(.5+.5*sin(t*3.0+p.x*31.0),10.0);
    }
    float glow=.1+.15*sin(t*2.0+p.x*20.0);
    fragColor=vec4(clamp((sky+vec3(glow*.12,glow*.07,glow*.22))*max(.6,tint.g),0.0,1.0),tint.a);
}
