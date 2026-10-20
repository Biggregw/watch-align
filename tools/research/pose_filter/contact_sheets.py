#!/usr/bin/env python3
"""Contact sheets solely for owner-requested visual crown/case pose review.
Sha256-checked images are read transiently; only resized contact sheets are artifacts.
No reference data or scores are changed.
"""
import csv
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps

root=Path("g/img"); dest=Path("out/contact");dest.mkdir(parents=True,exist_ok=True)
rows=list(csv.DictReader(open("g/manifest.csv",newline="")))
font=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",19)
small=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",16)
TILE_W=690;TILE_H=700;PHOTO_H=602;COLS=3;COUNT=9
for fam in ("NoDate","GMT","Date"):
 subset=[r for r in rows if r["family"]==fam]
 for batch in range(0,len(subset),COUNT):
  picked=subset[batch:batch+COUNT]
  sheet=Image.new("RGB",(TILE_W*COLS,TILE_H*3),"#dadada")
  painter=ImageDraw.Draw(sheet)
  for idx,rec in enumerate(picked):
   x=idx%COLS*TILE_W;y=idx//COLS*TILE_H
   painter.rectangle((x+2,y+2,x+TILE_W-3,y+TILE_H-3),fill="#f8f8f8",outline="#555555",width=2)
   path=root/rec["local_path"]
   status="unavailable"
   if path.exists():
    try:
     with Image.open(path) as src:
      im=ImageOps.exif_transpose(src).convert("RGB")
      w,h=im.size
      # Crop only the image borders for normalisation; retain complete watch and crown
      thumb=ImageOps.contain(im,(TILE_W-26,PHOTO_H-12),Image.Resampling.LANCZOS)
      xx=x+(TILE_W-thumb.width)//2;yy=y+6+(PHOTO_H-thumb.height)//2
      sheet.paste(thumb,(xx,yy))
      status=f"original {w}x{h}"
    except Exception as ex:
     status="decode failed: "+str(ex)[:35]
   painter.text((x+14,y+PHOTO_H+7),f"{fam} | {rec['photo_id']}",font=font,fill="#101010")
   painter.text((x+14,y+PHOTO_H+36),rec["physical_watch_id"][:52],font=small,fill="#101010")
   painter.text((x+14,y+PHOTO_H+61),status,font=small,fill="#303030")
  out=dest/f"{fam}_sheet_{batch//COUNT+1}.jpg"
  sheet.save(out,format="JPEG",quality=89,subsampling=0)
  print(out, len(picked))
