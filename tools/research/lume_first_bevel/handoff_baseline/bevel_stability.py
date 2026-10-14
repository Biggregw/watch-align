from reconstruct_bevel import CASES,sample
from PIL import Image
import cv2,numpy as np
from pathlib import Path
for name,case in CASES.items():
 im=np.array(Image.open(Path('/mnt/data')/case['file']).convert('RGB'));gray=cv2.cvtColor(im,cv2.COLOR_RGB2GRAY).astype(float)
 v=np.array(case['vertices'],float)
 groups={}
 print('\n'+name)
 for side,(i,j) in [('top',(0,1)),('left',(0,2)),('right',(1,2))]:
  def trace(points,width):
   a,b=points[i],points[j];us,n,chosen,_,_=sample(gray,a,b,points.mean(0),case['scan']+4,width)
   o=np.array([a+u*(b-a)+n*x[1] if x else [np.nan,np.nan] for u,x in zip(us,chosen)])
   return o
  base=trace(v,case['width']);out=[]
  for dx,dy,wd in [(1,0,1),(-1,0,1),(0,1,1),(0,-1,1),(2,2,1),(-2,-2,1),(0,0,.8),(0,0,1.2)]:
   shift=v+np.array([dx,dy]);other=trace(shift,case['width']*wd);valid=np.isfinite(base[:,0])&np.isfinite(other[:,0]);dist=np.linalg.norm(base[valid]-other[valid],axis=1)
   out.append({'shift':(dx,dy),'widthScale':wd,'med_px':round(float(np.median(dist)),2),'p90_px':round(float(np.percentile(dist,90)),2),'n':int(valid.sum())})
  groups[side]=out
  print(side,out)
