#version 150
// EXPERIMENTAL only: not currently bound to a runtime shader program.
uniform float Time;
in vec2 uv;
out vec4 fragColor;
void main(){
  vec2 p=uv*2.0-1.0;
  float r=length(p),a=atan(p.y,p.x);
  float rings=sin(20.*a-Time*2.-40.*r)*.5+.5;
  fragColor=vec4(.3+.5*rings,.12,.65+.3*rings,1.0);
}