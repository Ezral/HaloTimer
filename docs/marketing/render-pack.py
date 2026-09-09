#!/usr/bin/env python3
"""Compose original marketing layouts around unchanged native screenshots/video.
Requires Pillow, numpy, ffmpeg. Usage: render-pack.py RAW_DIR OUTPUT_DIR
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import numpy as np
import subprocess, sys, json
ROOT=Path(__file__).resolve().parents[2]
RAW=Path(sys.argv[1]); OUT=Path(sys.argv[2]); OUT.mkdir(parents=True,exist_ok=True)
WORK=OUT.parent/'work'; WORK.mkdir(exist_ok=True)
FONTS=ROOT/'app/src/main/res/font'
W,H=1080,1920
# Native Pixel 6 capture: 1080 x 2400; preserve full screen and its real border.
SW,SH=594,1320; X,Y=(W-SW)//2,414

def font(size,bold=False): return ImageFont.truetype(str(FONTS/('poppins_semibold.ttf' if bold else 'poppins_regular.ttf')),size)
def text(d,xy,t,size,color,bold=False): d.text(xy,t,font=font(size,bold),fill=color,anchor='lt')
def base(title,sub,accent=(166,146,255),tag='ANDROID • HALOTIMER',light=False):
    yy,xx=np.mgrid[0:H,0:W]
    glow=np.exp(-(((xx-810)/650)**2+((yy-1210)/950)**2))*.16
    bg=np.array((242,241,236) if light else (14,16,22),dtype=float)
    pixels=bg[None,None,:]+glow[:,:,None]*(np.array(accent)-bg)
    im=Image.fromarray(np.uint8(np.clip(pixels,0,255)),'RGB').convert('RGBA')
    d=ImageDraw.Draw(im); fg=(25,29,35) if light else (246,246,241); muted=(85,91,98) if light else (158,164,177)
    text(d,(72,54),'halo',62,fg,True)
    d.rounded_rectangle((803,67,1008,113),radius=23,outline=muted,width=1)
    text(d,(837,78),'HALOTIMER',18,fg,True)
    for i,line in enumerate(title.split('\n')): text(d,(72,163+i*83),line,74,fg,True)
    text(d,(76,346),sub,25,muted)
    # Soft physical-frame shadow outside screenshot; never alters app contents.
    shadow=Image.new('RGBA',(W,H)); sd=ImageDraw.Draw(shadow)
    sd.rounded_rectangle((X-13,Y-1,X+SW+13,Y+SH+25),radius=30,fill=(0,0,0,140 if not light else 65))
    im=Image.alpha_composite(im,shadow.filter(ImageFilter.GaussianBlur(28)))
    d=ImageDraw.Draw(im); d.rounded_rectangle((X-8,Y-8,X+SW+8,Y+SH+8),radius=24,fill=(33,36,44),outline=(71,75,84),width=2)
    for i,c in enumerate([(170,156,255),(87,221,180),(255,186,119)]): d.rounded_rectangle((72+i*37,1791,96+i*37,1797),radius=3,fill=c)
    text(d,(72,1830),tag,18,muted)
    text(d,(769,1830),'TIME, IN VIEW.',18,muted,True)
    return im.convert('RGB')

def poster(name,src,title,sub,light=False):
    im=base(title,sub,light=light); shot=Image.open(RAW/src).convert('RGB'); shot=shot.resize((SW,SH),Image.Resampling.LANCZOS)
    im.paste(shot,(X,Y)); im.save(OUT/name,optimize=True)

def video(name,src,title,sub,duration,start=0,tag='ANDROID • HALOTIMER'):
    template=WORK/(Path(name).stem+'-layout.png'); base(title,sub,tag=tag).save(template)
    cmd=['ffmpeg','-v','error','-y','-loop','1','-framerate','30','-i',str(template),'-ss',str(start),'-i',str(RAW/src),
         '-filter_complex_threads','1','-filter_complex',f'[1:v]setpts=PTS-STARTPTS,scale={SW}:{SH}:flags=lanczos,setsar=1,fps=30,tpad=stop_mode=clone:stop_duration=1[screen];[0:v][screen]overlay={X}:{Y}:shortest=1,format=yuv420p[v]',
         '-map','[v]','-t',str(duration),'-an','-c:v','libx264','-threads','2','-preset','fast','-crf','19','-movflags','+faststart',str(OUT/name)]
    subprocess.run(cmd,check=True)

if __name__=='__main__':
    poster('HaloTimer-01-Parallel.png','04-parallel-overlay.png','Three timers.\nOne glance.','Independent countdowns. Your own colors.')
    poster('HaloTimer-02-Dock.png','06-half-circle-dock.png','A little space.\nA lot of focus.','Pull to the edge. Keep time in view.')
    poster('HaloTimer-03-Controls.png','07-dock-actions.png','Hold. Slide.\nKeep going.','Playback controls, right at your fingertips.')
    poster('HaloTimer-04-Sequences.png','03-sequence-menu.png','One ritual.\nEvery step.','Named sequences that flow from one timer to the next.',True)
    poster('HaloTimer-05-Dark.png','01-dark-menu.png','Make time\nyour own.','Three tracks. Custom colors. Light or dark.')
    poster('HaloTimer-06-Glass.png','08-compact-glass.png','Small control.\nFull focus.','Pause, play and reset without opening the menu.')
    def concat(parts,name):
     listing=WORK/f'{name}.txt';listing.write_text(''.join("file '"+str(OUT/p)+"'\n" for p in parts))
     subprocess.run(['ffmpeg','-v','error','-y','-f','concat','-safe','0','-i',str(listing),'-c','copy','-movflags','+faststart',str(OUT/name)],check=True)
     for p in parts:(OUT/p).unlink()
    video('parallel-a.mp4','01-parallel-timers.mp4','Three timers.\nOne glance.','Independent countdowns. Your own colors.',3,0)
    video('parallel-b.mp4','01-parallel-timers.mp4','Three timers.\nOne glance.','Independent countdowns. Your own colors.',3,8.3)
    concat(['parallel-a.mp4','parallel-b.mp4'],'HaloTimer-Parallel-6s.mp4')
    video('HaloTimer-Dock-14s.mp4','02-fluid-dock.mp4','Time, tucked\ninto the edge.','Drag to dock. Hold for controls. Pull to expand.',14)
    parts=[]
    for n,(src,label) in enumerate([('03-alert-breathe.mp4','BREATHE'),('04-alert-orbit.mp4','ORBIT'),('05-alert-ping_pong.mp4','PING-PONG'),('06-alert-double_pong.mp4','DOUBLE PONG')]):
     p=f'alert-{n}.mp4';video(p,src,'Time is up.\nLet it glow.',label+'  /  Choose your completion animation.',5,.1);parts.append(p)
    concat(parts,'HaloTimer-Alerts-20s.mp4')
    parts=[]
    for n,(src,start,duration,title,sub) in enumerate([
     ('01-parallel-timers.mp4',0,3,'Time, in view.\nLife, in motion.','Meet HaloTimer for Android.'),
     ('02-fluid-dock.mp4',1,9,'A timer that\nfinds its place.','Fluid docking. Controls within reach.'),
     ('04-alert-orbit.mp4',.1,6,'Your next timer\nhas a halo.','Custom colors. Sequences. Edge-light alerts.')]):
     p=f'hero-{n}.mp4';video(p,src,title,sub,duration,start);parts.append(p)
    concat(parts,'HaloTimer-Hero-18s.mp4')
    thumb=Image.new('RGB',(1080,1280),(14,16,22))
    for i,p in enumerate(sorted(OUT.glob('HaloTimer-0*.png'))):
     im=Image.open(p);im.load();thumb.paste(im.resize((360,640),Image.Resampling.LANCZOS),((i%3)*360,(i//3)*640))
    thumb.save(OUT/'HaloTimer-Screenshot-Preview.jpg',quality=92)
    print('FINAL EXPORT COMPLETE',flush=True)
