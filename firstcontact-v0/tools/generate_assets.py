#!/usr/bin/env python3
"""Deterministically generate our OWN pixel-art radio textures, 3D block model and Morse OGGs.
No borrowed copyrighted textures, no dependence on the user's Minecraft installation.
Invoked by Gradle before processResources. Requires python3 and ffmpeg (for OGG)."""
from __future__ import annotations
import json
import math
import random
import shutil
import struct
import subprocess
import tempfile
import wave
import zlib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]/"src"/"main"/"resources"/"assets"/"firstcontact"
TEXTURES=ROOT/"textures"/"block"
MODELS=ROOT/"models"/"block"
SOUNDS=ROOT/"sounds"
for d in (TEXTURES,MODELS,SOUNDS):
    d.mkdir(parents=True,exist_ok=True)

def png(path:str, image:list[list[tuple[int,int,int]]]):
    height=len(image);width=len(image[0])
    rows=b"".join(b"\0"+b"".join(bytes(px) for px in row) for row in image)
    def chunk(kind,content):
        return struct.pack(">I",len(content))+kind+content+struct.pack(">I",zlib.crc32(kind+content)&0xffffffff)
    data=b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",width,height,8,2,0,0,0))
    data+=chunk(b"IDAT",zlib.compress(rows,9))+chunk(b"IEND",b"")
    (TEXTURES/(path+".png")).write_bytes(data)

def mix(a,b,t):
    return tuple(max(0,min(255,round(x*(1-t)+y*t))) for x,y in zip(a,b))

rng=random.Random(271107)
N=32
# warm stained walnut with vertical wood grain (not vanilla spruce planks).
wood=[]
for y in range(N):
    row=[]
    for x in range(N):
        ring=math.sin(x*.48+math.sin(y*.18)*1.2)*0.11+math.cos(x*1.9+y*.07)*.055
        grain=(rng.random()-.5)*.07
        row.append(mix((50,31,23),(112,68,43),max(0,min(1,.48+ring+grain))))
    wood.append(row)
png("walnut",wood)
# Aged satin brass, dark desaturated bronze rather than gold-block texture.
brass=[]
for y in range(N):
    row=[]
    for x in range(N):
        streak=math.sin(x*.31+y*.09)*.032+(rng.random()-.5)*.04
        spec=.05 if y%16 in (0,1) else 0
        row.append(mix((97,65,36),(186,139,69),.42+streak+spec))
    brass.append(row)
png("aged_brass",brass)
bronze=[]
for y in range(N):
    row=[]
    for x in range(N):
        noise=(rng.random()-.5)*.07
        row.append(mix((49,39,32),(130,90,54),.37+noise+math.cos(x*.45)*.04))
    bronze.append(row)
png("burnished_copper",bronze)
bakelite=[]
for y in range(N):
    row=[]
    for x in range(N):
        streak=(rng.random()-.5)*.045
        row.append(mix((16,23,21),(47,48,42),.36+streak))
    bakelite.append(row)
png("bakelite",bakelite)
glass=[]
for y in range(N):
    row=[]
    for x in range(N):
        bright=.35+math.sin(x*.36)*.13+(rng.random()-.5)*.05
        row.append(mix((15,38,32),(64,117,85),bright))
    glass.append(row)
png("phosphor",glass)
speaker=[]
for y in range(N):
    row=[]
    for x in range(N):
        cross=(x%4==0 or y%4==0)
        row.append((98,74,48) if cross else (18,20,18))
    speaker.append(row)
png("speaker_mesh",speaker)
dial=[]
for y in range(N):
    row=[]
    for x in range(N):
        d=math.hypot(x-15.5,y-15.5)
        if d<8:color=(36,34,29)
        elif d<10:color=(203,152,75)
        elif d<13:color=(78,54,36)
        else:color=(30,30,26)
        row.append(color)
    dial.append(row)
png("knob",dial)
# Textures in separate files permit independent adjustment and crisp per-pixel UVs.
textures={
    "wood":"firstcontact:block/walnut",
    "brass":"firstcontact:block/aged_brass",
    "copper":"firstcontact:block/burnished_copper",
    "bakelite":"firstcontact:block/bakelite",
    "screen":"firstcontact:block/phosphor",
    "mesh":"firstcontact:block/speaker_mesh",
    "dial":"firstcontact:block/knob",
    "particle":"firstcontact:block/walnut",
}
parts=[]
def cuboid(frm,to,face,allfaces=True):
    # Unique exposed surfaces. Do not draw internal coincident faces.
    sides=("north","south","east","west","up","down") if allfaces else ("north",)
    parts.append({"from":list(frm),"to":list(to),
                  "faces":{side:{"texture":"#"+face} for side in sides}})
# Keep the silhouette of the original radio, replace everything that was gold-block.
cuboid((1,0,1),(15,2,15),"bakelite")
cuboid((1.4,2,2.5),(14.6,13.6,15),"wood")
cuboid((1,13.6,1.8),(15,15,15.2),"wood")
cuboid((1.0,2,1.1),(15,3.1,15),"copper")
cuboid((1.5,3.1,1.45),(14.5,13.6,2.5),"brass")
cuboid((2,3.6,1.2),(14,13.1,1.55),"bakelite")
# Horizontal trims, distinct depth in front of main fascia to prevent z-fighting.
for y in [3.35,12.85]:
    cuboid((1.7,y,0.95),(14.3,y+.30,1.22),"brass")
for x in [1.8,13.9]:
    cuboid((x,3.65,.95),(x+.30,12.80,1.22),"brass")
# Mesh speaker, left at exposed z=0.7, surrounded by bronze trimmings.
cuboid((2.45,5.35,.72),(8.65,11.65,1.1),"mesh")
for xx in [2.25,8.5]:
    cuboid((xx,5.2,.45),(xx+.31,11.8,.76),"brass")
for yy in [5.2,11.5]:
    cuboid((2.25,yy,.45),(8.75,yy+.27,.76),"brass")
# Small vertical rounded grille ribs in front of the mesh.
for xx in [3.0,4.1,5.2,6.3,7.4,8.3]:
    cuboid((xx,5.6,.34),(xx+.16,11.3,.52),"copper")
# Receiver illuminated analogue scale and inset screen.
cuboid((9.2,8.15,.75),(13.65,11.6,1.15),"brass")
cuboid((9.54,8.5,.42),(13.3,11.2,.78),"screen")
for x in [9.8,10.5,11.2,11.9,12.6]:
    cuboid((x,8.8,.28),(x+.10,9.3,.45),"brass")
cuboid((11.8,9.3,.25),(11.94,10.8,.45),"copper")
# Two sculpted brass/bakelite knobs with protruding hub and circular textured face.
for xx in [9.85,12.55]:
    cuboid((xx,5.25,.85),(xx+1.7,7.1,1.25),"copper")
    cuboid((xx+.15,5.42,.46),(xx+1.55,6.92,.86),"bakelite")
    cuboid((xx+.32,5.6,.25),(xx+1.35,6.72,.47),"dial",False)
# Horizontal inset bars and four metal corner bolts.
cuboid((9.2,4.35,.70),(13.9,4.62,.88),"brass")
for xx in [2.0,13.2]:
    for yy in [4.0,12.15]:
        cuboid((xx,yy,.48),(xx+.45,yy+.45,.78),"copper",False)
# Brass lid seams and side wood strips.
for xx in [1.1,14.55]:
    cuboid((xx,2.3,2.1),(xx+.32,13.8,14.7),"copper")
# Low-profile square feet / lamps.
for xx in [1.55,13.4]:
    for zz in [2.0,13.5]:
        cuboid((xx,.15,zz),(xx+1,.9,zz+1),"copper")
# Face normals only on exterior, no shared-depth co-planar quads.
model={"parent":"minecraft:block/block","ambientocclusion":False,"textures":textures,
       "elements":parts,"display":{
         "gui":{"rotation":[30,225,0],"translation":[0,0,0],"scale":[0.77,0.77,0.77]},
         "ground":{"rotation":[0,0,0],"translation":[0,3,0],"scale":[0.50,0.50,0.50]},
         "fixed":{"rotation":[0,180,0],"translation":[0,0,0],"scale":[0.72,0.72,0.72]}}}
(MODELS/"radio_receiver.json").write_text(json.dumps(model,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print(f"[First Contact] generated {len(parts)} cuboids and 7 original textures")
# Sound design: single continuous original sine-wave CW tones, short vs long,
# with attack/release fades (no click/pops), plus analogue radio static.
def wavfile(path, seconds, tone):
    sr=22050;n=int(seconds*sr)
    rng2=random.Random(1641+(1 if tone=="dot" else 2 if tone=="dash" else 3))
    with wave.open(str(path),"wb") as wf:
        wf.setnchannels(1);wf.setsampwidth(2);wf.setframerate(sr)
        buf=bytearray()
        for i in range(n):
            t=i/sr
            if tone=="static":
                amplitude=.16*(rng2.random()*2-1)+.045*math.sin(2*math.pi*145*t)
            else:
                fade=min(1.0,t/.014,max(0.0,(seconds-t)/.024))
                # two slight detuned harmonics make the radio audio less piercing
                amplitude=fade*(.35*math.sin(2*math.pi*690*t)
                     +.045*math.sin(2*math.pi*1380*t)
                     +.012*(rng2.random()*2-1))
            amplitude=max(-.95,min(.95,amplitude))
            buf.extend(struct.pack("<h",int(amplitude*32767)))
        wf.writeframes(buf)
ffmpeg=shutil.which("ffmpeg")
if ffmpeg is None:
    raise SystemExit("ffmpeg is required to generate Morse OGGs; install ffmpeg or use GitHub Actions")
with tempfile.TemporaryDirectory() as temp:
    for name,secs,tone in [("morse_dot",.15,"dot"),
                           ("morse_dash",.45,"dash"),
                           ("radio_static",.40,"static")]:
        wav=Path(temp)/(name+".wav")
        wavfile(wav,secs,tone)
        subprocess.run([ffmpeg,"-hide_banner","-loglevel","error","-y",
                        "-i",str(wav),"-c:a","libvorbis","-qscale:a","3",
                        str(SOUNDS/(name+".ogg"))],check=True)
sounds={key:{"sounds":[{"name":f"firstcontact:{key}","volume":0.85,
                         "attenuation_distance":16}]} for key in
        ("morse_dot","morse_dash","radio_static")}
(ROOT/"sounds.json").write_text(json.dumps(sounds,indent=2)+"\n",encoding="utf-8")
print("[First Contact] generated 3 original OGG sounds and sounds.json")
