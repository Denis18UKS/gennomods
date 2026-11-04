#version 150
uniform float Time;
in vec2 texCoord;
in vec4 tint;
out vec4 fragColor;

float hash(vec2 p){return fract(sin(dot(p,vec2(37.719,91.347)))*43758.5453);}
float noise(vec2 p){
    vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);
    return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),
               mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);
}
float fbm(vec2 p){
    float sum=0.,amp=.52;
    for(int j=0;j<5;j++){sum+=amp*noise(p);p=p*2.1+vec2(13.,1.3);amp*=.52;}
    return sum;
}
void main(){
    float t=Time;
    vec2 p=texCoord;
    vec3 col;
    if(tint.r>.6){
        float cloud=fbm(p*7.+vec2(t*.04,-t*.03));
        float detail=fbm(p*19.+vec2(-t*.07,t*.015));
        vec3 high=mix(vec3(.018,.025,.095),vec3(.21,.07,.43),clamp(p.y+.22*cloud,0.,1.));
        high+=cloud*vec3(.03,.07,.19);
        // A readable OTHER-REALITY scene: skyline, distant mountains,
        // glowing moon and floating islands. This is procedural, not a
        // live world view; that requires the separate timeline engine.
        float mountains=.27+.12*sin(p.x*11.)+.065*sin(p.x*28.+.3)+.04*noise(vec2(p.x*35.,0.));
        col=high;
        if(p.y<mountains){
            float ground=clamp((mountains-p.y)*1.3+detail*.2,0.,1.);
            col=mix(vec3(.018,.02,.075),vec3(.13,.05,.21),ground);
        }
        float moon=1.-smoothstep(.026,.032,length(p-vec2(.74,.72)));
        col+=moon*vec3(.73,.78,1.0);
        float landNoise=fbm(vec2(p.x*10.7,p.y*6.));
        float island=1.-smoothstep(.02,.043,abs(p.y-(.62+.035*sin(p.x*20.))));
        island*=smoothstep(.05,.17,p.x)* (1.-smoothstep(.42,.54,p.x));
        col=mix(col,vec3(.075,.065,.20)+vec3(.14,.11,.32)*landNoise,island*.8);
        float stars=step(.994,hash(floor(p*vec2(168.,146.)+vec2(t*.008,0.))));
        col+=stars*vec3(.9,.95,1.)*step(mountains,p.y);
        float veil=fbm(p*13.+vec2(t*.07,0.));
        col+=veil*vec3(.025,.02,.06);
    }else{
        // Narrow animated violet/cyan crack edge, not a giant purple wall.
        float energy=pow(.5+.5*sin(texCoord.x*58.-t*3.1+sin(texCoord.y*13.)*2.),7.);
        float spark=pow(.5+.5*sin(texCoord.x*119.+t*6.),23.);
        col=mix(vec3(.25,.10,.54),vec3(.21,.85,1.0),energy);
        col+=vec3(.55,.25,.6)*spark;
    }
    fragColor=vec4(clamp(col*max(.65,tint.g),0.,1.),tint.a);
}
