#!/usr/bin/env python3
"""Arrange unmodified Android captures into feature and transition sheets.
Usage: python render-alpha07.py RAW_DIRECTORY OUTPUT_DIRECTORY
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import sys, io, os
raw,out=map(Path,sys.argv[1:3]);out.mkdir(parents=True,exist_ok=True)
fonts=Path(__file__).resolve().parents[2]/'app/src/main/res/font'
def font(size,bold=False):return ImageFont.truetype(str(fonts/('poppins_bold.ttf' if bold else 'poppins_regular.ttf')),size)
def save(im,name):
 b=io.BytesIO();im.save(b,format='PNG',optimize=True)
 with open(out/name,'wb') as f:f.write(b.getvalue());f.flush();os.fsync(f.fileno())
def header(im,title,sub):
 d=ImageDraw.Draw(im);d.text((90,65),'HALOTIMER  /  ANDROID ALPHA 07',font=font(24,True),fill='#A695F8')
 d.text((90,115),title,font=font(66,True),fill='#F7F6FC');d.text((90,215),sub,font=font(25),fill='#BBB7C8')
scene=[('21-rounded-bar-with-name.png','01  Compact & personal','Rounded bar · optional name'),
 ('23-contour-label-left.png','02  Along the contour','One label follows the dock'),
 ('27-bar-perimeter-text.png','03  Text in motion','Optional travel around the bar'),
 ('29-completion-full-page.png','04  A colorful finish','Timer + sequence completion')]
im=Image.new('RGB',(2400,1800),'#111019');header(im,'Time, in its own shape.','Actual Android emulator captures over a separate sample app.')
d=ImageDraw.Draw(im)
for i,(src,title,desc) in enumerate(scene):
 x=90+i*580;shot=Image.open(raw/src).convert('RGB');shot.thumbnail((480,1170),Image.Resampling.LANCZOS)
 d.rounded_rectangle((x-8,324,x+488,1510),radius=30,fill='#292534')
 im.paste(shot,(x+(480-shot.width)//2,338))
 d.text((x,1540),title,font=font(24,True),fill='#F7F6FC');d.text((x,1585),desc,font=font(20),fill='#BBB7C8')
d.text((90,1720),'Native captures · No simulated app screens · Physical-device feel remains subject to testing',font=font(20),fill='#888192')
save(im,'HaloTimer-alpha07-features.png')
# Tight crops retain the entire display width and the same vertical region in every frame.
items=[('21-rounded-bar-with-name.png','Floating bar'),('22-bar-merges-while-held.png','Touch edge · merge'),('23-contour-label-left.png','Release · dock'),('24-held-circle.png','Pull · circle'),('25-circle-merges-right-while-held.png','Touch opposite edge'),('26-contour-label-right.png','Release · dock again')]
im=Image.new('RGB',(2400,1540),'#111019');header(im,'A single gesture. Either edge.','Real captures: contact starts the merge before the finger lifts.')
d=ImageDraw.Draw(im)
for i,(src,title) in enumerate(items):
 shot=Image.open(raw/src).convert('RGB');w,h=shot.size
 # Mid-screen band contains the timer throughout the gesture; preserve aspect ratio.
 shot=shot.crop((0,int(h*.23),w,int(h*.62)));shot.thumbnail((680,465),Image.Resampling.LANCZOS)
 x=90+(i%3)*770;y=350+(i//3)*560
 d.rounded_rectangle((x-8,y-8,x+688,y+473),radius=20,fill='#292534');im.paste(shot,(x,y))
 d.text((x,y+486),f'{i+1:02}  {title}',font=font(23,True),fill='#F7F6FC')
save(im,'HaloTimer-alpha07-transitions.png')
