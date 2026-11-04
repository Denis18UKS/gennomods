#version 150
// EXPERIMENTAL only: not currently bound to a runtime shader program.
uniform float Time;
in vec2 uv;
out vec4 fragColor;
void main(){
  float f=abs(uv.x-.5+.04*sin(22.*uv.y+Time));
  float e=exp(-f*48.);
  fragColor=vec4(.24+.65*e,.1+.3*e,.5+.5*e,e);
}