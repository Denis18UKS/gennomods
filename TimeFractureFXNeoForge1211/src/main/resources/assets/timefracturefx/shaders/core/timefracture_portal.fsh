#version 150
uniform float Time;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453123);}
float noise(vec2 p){
    vec2 i=floor(p),f=fract(p);
    f=f*f*(3.-2.*f);
    return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),
               mix(hash(i+vec2(0.,1.)),hash(i+vec2(1.,1.)),f.x),f.y);
}
float fbm(vec2 p){
    float f=0.,a=.52;
    for(int i=0;i<5;i++){f+=a*noise(p);p=mat2(1.6,-1.2,1.2,1.6)*p+vec2(14.,3.);a*=.5;}
    return f;
}
void main(){
    float t=Time;
    vec2 p=texCoord*2.-1.;
    float r=length(p);
    float a=atan(p.y,p.x);
    if(tint.r>.6){
        // The true world-space opening covers the sky. The shader supplies
        // an apparent endless, moving interior without any opaque cone.
        float d=1.0/(r+.075);
        vec2 q=vec2(cos(a + d*.48 - t*.24),sin(a+d*.48-t*.24));
        float neb=fbm(p*5.1+q*(.7+1.4*fbm(p*3.+t*.04))+vec2(t*.07,-t*.045));
        float wisps=pow(.5+.5*cos(a*7.-d*5.+t*1.3+neb*7.),5.);
        float rings=pow(.5+.5*sin(d*10.8+t*2.8-neb*5.),6.);
        float haze=fbm(p*15.+vec2(t*.2,t*.08));
        vec3 col=mix(vec3(.009,.006,.052),vec3(.085,.018,.22),neb);
        col+=wisps*vec3(.27,.08,.61);
        col+=rings*vec3(.10,.48,.95)*.67;
        col+=vec3(.07,.025,.16)*haze;
        float core=exp(-r*r*19.);
        col+=core*vec3(.24,.09,.55);
        float halo=exp(-pow((r-.86)*15.,2.));
        col+=halo*vec3(.33,.28,.88);
        float stars=step(.995,hash(floor(p*155.+vec2(t*.019,0.))));
        col+=stars*vec3(.8,.9,1.0)*(1.-smoothstep(.7,1.,r));
        // Proper dark edges but clear colorful detail at interior.
        col=clamp(col,0.,1.);
        fragColor=vec4(col, tint.a*(1.-smoothstep(.985,1.,r)));
    } else {
        // Thin physical bands only. No solid colored side walls.
        float stripe=pow(.5+.5*sin(texCoord.x*40.-t*1.8+texCoord.y*9.),5.);
        float spark=pow(.5+.5*sin(texCoord.x*120.+t*4.4),20.);
        vec3 glow=mix(vec3(.11,.07,.46),vec3(.27,.73,1.0),stripe);
        glow+=spark*vec3(.32,.21,.52);
        fragColor=vec4(clamp(glow*max(.7,tint.g),0.,1.),tint.a*(.55+.45*stripe));
    }
}
