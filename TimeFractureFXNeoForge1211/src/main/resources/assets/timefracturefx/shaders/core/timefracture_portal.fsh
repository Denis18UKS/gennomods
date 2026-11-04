#version 150
uniform float Time;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453123);
}
float noise(vec2 p) {
    vec2 i=floor(p),f=fract(p);
    f=f*f*(3.0-2.0*f);
    return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),
               mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);
}
float fractal(vec2 p) {
    float f=0.0,weight=0.55;
    for(int i=0;i<5;i++){f+=noise(p)*weight;p=p*2.05+vec2(17.3,4.7);weight*=.52;}
    return f;
}
void main(){
    vec2 p=texCoord*2.0-1.0;
    float radius=length(p);
    float angle=atan(p.y,p.x);
    float t=Time;
    // A volumetric perspective illusion inside a real world-space multi-layer tunnel.
    float depth=1.0/(radius+.12);
    vec2 swirl=vec2(cos(angle+depth*.55-t*.6),sin(angle+depth*.55-t*.6));
    float gas=fractal(p*4.8+swirl*1.7+t*.07);
    float spiral=pow(max(0.0,sin(angle*9.0+depth*9.5-t*2.0)),5.0);
    float bands=pow(.5+.5*sin(depth*15.0-t*3.4+gas*4.0),4.0);
    vec3 base=mix(vec3(.008,.004,.055),vec3(.11,.018,.30),gas);
    base+=vec3(.18,.07,.46)*spiral*.52;
    base+=vec3(.12,.44,.84)*bands*.65;
    base+=vec3(.37,.15,.63)*exp(-radius*4.0);
    float stars=step(.998,hash(floor((p*1.8+vec2(t*.009,0.0))*115.0)));
    base+=stars*vec3(.8,.91,1.0)*(0.6+0.4*sin(t*2.0+angle*7.0));
    float rim=exp(-pow((radius-.91)*13.0,2.0));
    base+=rim*vec3(.44,.31,.88);
    // tint.r: interior=1, tunnel shell=.28. Coordinates stay tied to real geometry.
    if(tint.r<.6) {
        base=mix(vec3(.09,.02,.22),vec3(.43,.22,.86),.5+.5*sin(texCoord.x*29.0-t*2.0));
        base+=vec3(.13,.38,.8)*pow(.5+.5*sin(texCoord.y*36.0-t*4.0),5.0);
    }
    float alpha=tint.a;
    if(tint.r>=.6)alpha*=1.0-smoothstep(.97,1.0,radius);
    fragColor=vec4(min(base*max(.4,tint.g),vec3(1.0)),alpha);
}
