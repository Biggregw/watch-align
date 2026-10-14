"""Exploratory seeded luminance segmentation of lume fill on two user supplied watch crops.
No model fitting, Rolex thresholds, or manufacturing classifications.
"""
import cv2, numpy as np
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
O=Path('/mnt/data')
CASES=[('Genuine reference','tri_watch.jpg',(145,127,335,354),[(110,132,153)]),('QC watch','qc_triangle_crop_work.png',(135,151,214,249),[(115,145,175)])]

def extract(img,box,th):
 x0,y0,x1,y1=box
 r=cv2.cvtColor(img[y0:y1,x0:x1],cv2.COLOR_BGR2GRAY)
 r=cv2.GaussianBlur(r,(3,3),0.6)
 b=(r>th).astype(np.uint8)*255
 n,labels,stats,cents=cv2.connectedComponentsWithStats(b,connectivity=8)
 hh,ww=r.shape
 valid=[i for i in range(1,n) if stats[i,cv2.CC_STAT_AREA]>100 and (stats[i,cv2.CC_STAT_WIDTH]>12)]
 if not valid:return None
 # choose dominant luminous body; constrained by known triangle location
 idx=max(valid,key=lambda i:stats[i,cv2.CC_STAT_AREA]);mask=(labels==idx).astype(np.uint8)
 k=cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(3,3))
 # slight interior hole filling optional, never change outer boundary geometrically
 cont,_=cv2.findContours(mask,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_NONE)
 c=max(cont,key=cv2.contourArea).reshape(-1,2)
 c[:,0]+=x0;c[:,1]+=y0
 full=np.zeros(img.shape[:2],np.uint8);full[y0:y1,x0:x1]=mask
 return full,c

def boundary_dist(a,b):
 """Symmetric median and 90th point-to-other-edge dist in native px."""
 ea=cv2.Canny(a*255,50,150)>0;eb=cv2.Canny(b*255,50,150)>0
 db=cv2.distanceTransform(np.uint8(~eb),cv2.DIST_L2,cv2.DIST_MASK_PRECISE)
 da=cv2.distanceTransform(np.uint8(~ea),cv2.DIST_L2,cv2.DIST_MASK_PRECISE)
 arr=np.r_[db[ea],da[eb]]
 return float(np.median(arr)),float(np.percentile(arr,90))

out=[]
for name,fn,box,items in CASES:
 image=cv2.imread(str(O/fn));ths=items[0]
 masks=[extract(image,box,t) for t in ths]
 if any(x is None for x in masks):print(name,'FAILED');continue
 med=masks[1][0];w=box[2]-box[0]
 print(name,'ROI',box,'width',w)
 for i,(t,(m,c)) in enumerate(zip(ths,masks)):
  iou=np.logical_and(m,med).sum()/np.logical_or(m,med).sum()
  dist=boundary_dist(m,med)
  x=c[:,0];y=c[:,1]
  print('  threshold',t,'area',int(m.sum()),'relative area',round(m.sum()/med.sum(),3),'IoU',round(iou,3),'med/p90 boundary shift',np.round(dist,2),'bbox',[x.min(),y.min(),x.max(),y.max()])
 # enlarge each original with three contours
 raw=cv2.cvtColor(image,cv2.COLOR_BGR2RGB)
 d=raw.copy()
 for m,c in masks:
  pass
 for i,(t,(m,c)) in enumerate(zip(ths,masks)):
  color=[(246,194,72),(77,228,248),(228,103,232)][i]
  cv2.polylines(d,[c.reshape(-1,1,2)],True,color,2,cv2.LINE_AA)
 x0,y0,x1,y1=box
 pad=20;bb=(max(0,x0-pad),max(0,y0-pad),min(d.shape[1],x1+pad),min(d.shape[0],y1+pad))
 cr=Image.fromarray(raw).crop(bb);ol=Image.fromarray(d).crop(bb)
 zoom=2 if name.startswith('Genuine') else 4
 cr=cr.resize((cr.width*zoom,cr.height*zoom),Image.Resampling.NEAREST)
 ol=ol.resize((ol.width*zoom,ol.height*zoom),Image.Resampling.NEAREST)
 top=Image.new('RGB',(cr.width*2,cr.height+74),(14,18,29));dr=ImageDraw.Draw(top)
 top.paste(cr,(0,74));top.paste(ol,(cr.width,74))
 dr.text((10,9),name+' original',fill='white');dr.text((cr.width+10,9),'Lume contour at three thresholds',fill='white')
 dr.text((cr.width+10,35),f'Amber {ths[0]} / Cyan {ths[1]} / Magenta {ths[2]}',fill='white')
 p=O/('lume_genuine_trace.png' if name.startswith('Genuine') else 'lume_qc_trace.png');top.save(p)
 out.append((name,p))
for k,v in out:print(v)
