"""Exploratory human-seeded luminous border extrapolation. No production validity."""
import cv2, numpy as np
from PIL import Image,ImageDraw
import importlib.util
from pathlib import Path
O=Path('/mnt/data')
spec=importlib.util.spec_from_file_location('b',str(O/'reconstruct_bevel.py'));b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
CASES=[
 dict(label='QC replica',file='qc_triangle_crop_work.png',box=(135,151,214,249),threshold=145,width=2,vertices=[(137,155),(213,155),(179,247)],scan=9,calibrate=3.5,crop=(111,125,240,286),scale=3.2),
 dict(label='Genuine control',file='tri_watch.jpg',box=(145,127,335,354),threshold=132,width=3,vertices=[(149,130),(329,130),(244,350)],scan=17,calibrate=4,crop=(120,100,360,381),scale=2)
]
cvfont=cv2.FONT_HERSHEY_SIMPLEX
panels=[]
for ca in CASES:
 src=cv2.imread(str(O/ca['file']));gray=cv2.cvtColor(src,cv2.COLOR_BGR2GRAY)
 blurred=cv2.GaussianBlur(gray,(3,3),0.6)
 x0,y0,x1,y1=ca['box']
 rr=np.zeros_like(gray,dtype=np.uint8)
 rr[y0:y1,x0:x1]=np.uint8(blurred[y0:y1,x0:x1]>ca['threshold'])*255
 # retain largest component
 n,lab,stats,_=cv2.connectedComponentsWithStats(rr,connectivity=8)
 idx=max(range(1,n),key=lambda k:stats[k,cv2.CC_STAT_AREA]);m=np.uint8(lab==idx)*255
 k=2*ca['width']+1
 element=cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(k,k))
 outer=cv2.dilate(m,element)
 c,_=cv2.findContours(m,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_NONE)
 co,_=cv2.findContours(outer,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_NONE)
 inn=max(c,key=cv2.contourArea)
 out=max(co,key=cv2.contourArea)
 p=src.copy()
 # contour (the high-constrast luminous fill edge) and hypothesised outer contour in BGR.
 cv2.drawContours(p,[inn],-1,(220,93,211),1,cv2.LINE_AA) # pink inner
 cv2.drawContours(p,[out],-1,(242,225,44),1,cv2.LINE_AA) # cyan outer
 # independently fit right visible anchor from paired local transitions (prior-fit, still not ground truth)
 vs=np.array(ca['vertices'],dtype=float);center=vs.mean(0)
 us,normal,cho,profiles,offs=b.sample(gray.astype(float),vs[1],vs[2],center,ca['scan'],ca['calibrate'])
 observed=[]; dists=[]
 for u,a in zip(us,cho):
  if a is None or not a[2] or u<.23 or u>.70:continue
  centerPt=vs[1]+u*(vs[2]-vs[1]); q=centerPt+normal*a[1]
  observed.append(q)
  # closest predicted contour distance
  dists.append(float(cv2.pointPolygonTest(out,(float(q[0]),float(q[1])),True)))
 for qa,qb in zip(observed,observed[1:]):
  cv2.line(p,tuple(np.round(qa).astype(int)),tuple(np.round(qb).astype(int)),(47,220,74),2,cv2.LINE_AA)
 print(ca['label'],'right-edge paired-contrast anchor samples',len(observed),'width',ca['width'],'anchor vs inferred contour median absolute deviation',round(float(np.median(np.abs(dists))),2),'p90',round(float(np.percentile(np.abs(dists),90)),2),'pixels')
 X0,Y0,X1,Y1=ca['crop']
 orig=src[Y0:Y1,X0:X1];overlay=p[Y0:Y1,X0:X1]
 factor=ca['scale']
 sz=(round(orig.shape[1]*factor),round(orig.shape[0]*factor))
 orig=Image.fromarray(cv2.cvtColor(orig,cv2.COLOR_BGR2RGB)).resize(sz,Image.Resampling.NEAREST)
 overlay=Image.fromarray(cv2.cvtColor(overlay,cv2.COLOR_BGR2RGB)).resize(sz,Image.Resampling.NEAREST)
 hh=max(orig.height,overlay.height)
 panel=Image.new('RGB',(orig.width*2,hh+82),(13,19,30));draw=ImageDraw.Draw(panel)
 panel.paste(orig,(0,82));panel.paste(overlay,(orig.width,82))
 draw.text((14,14),ca['label']+' original',fill='white')
 draw.text((orig.width+14,14),'Lume-calibrated bevel hypothesis',fill='white')
 draw.text((orig.width+14,39),'Pink = lume; cyan = estimated outer; green = visible-edge anchor',fill=(190,205,218))
 pth=O/('anchored_lume_qc.png' if ca['label'].startswith('QC') else 'anchored_lume_genuine.png');panel.save(pth)
 print(pth)
 panels.append((ca['label'],panel))
# A combined portrait-ish output with QC above genuine at same image native sizes.
w=max(x.width for _,x in panels)
h=sum(x.height for _,x in panels)+16
combined=Image.new('RGB',(w,h),(13,19,30));y=0
for _,img in panels:combined.paste(img,((w-img.width)//2,y));y+=img.height+16
combined.save(O/'anchored_lume_bevel_comparison.png')
