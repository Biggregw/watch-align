"""Reproducible demo: double-edge detection in a user-specified triangle corridor, with stability checks.
Output: blue/cyan supported outer edge, purple inner edge; amber sections unstable to thickness/seed choices.
No CAD triangle / factory reference is fitted or altered.
"""
from PIL import Image,ImageDraw
import cv2,numpy as np
from pathlib import Path
from reconstruct_bevel import CASES,sample
O=Path('/mnt/data')

def reconstruct(case):
 im=np.asarray(Image.open(O/case['file']).convert('RGB'));gray=cv2.cvtColor(im,cv2.COLOR_RGB2GRAY).astype(float)
 verts=np.array(case['vertices'],float)
 disp=im.copy();overlay=im.copy();res={}
 colors={'outer':(65,239,255),'inner':(230,95,249),'unknown':(250,177,35)}
 for side,(i,j) in [('top',(0,1)),('left',(0,2)),('right',(1,2))]:
  def tr(V,w):
   a,b=V[i],V[j];us,n,ch,pro,off=sample(gray,a,b,V.mean(0),case['scan']+4,w)
   inn=np.array([a+u*(b-a)+n*x[0] if x else [np.nan,np.nan] for u,x in zip(us,ch)])
   out=np.array([a+u*(b-a)+n*x[1] if x else [np.nan,np.nan] for u,x in zip(us,ch)])
   return inn,out
  inner,outer=tr(verts,case['width']);
  test=[tr(verts,case['width']*.8)[1],tr(verts,case['width']*1.2)[1]]
  for dx,dy in [(1,0),(-1,0),(0,1),(0,-1)]:test.append(tr(verts+np.array([dx,dy]),case['width'])[1])
  diffs=np.stack([np.linalg.norm(a-outer,axis=1) for a in test]);maxdev=np.nanmax(diffs,axis=0)
  # a region supported only when multiple priors/starts yield comparable location
  stable=np.isfinite(maxdev)&(maxdev<=2)
  res[side]={'stable_samples':int(stable.sum()),'total_samples':len(stable),'max_median_shift_px':round(float(np.nanmedian(maxdev)),2),'width_prior_px':case['width']}
  if len(outer)>0:
   for k in range(len(outer)-1):
    if not np.isfinite(outer[k]).all() or not np.isfinite(outer[k+1]).all():continue
    ok=stable[k] and stable[k+1]
    if ok:
     poly=np.int32([inner[k],inner[k+1],outer[k+1],outer[k]])
     cv2.fillPoly(overlay,[poly],(20,178,255))
    if ok or k%3==0:
     cv2.line(disp,tuple(outer[k].astype(int)),tuple(outer[k+1].astype(int)),colors['outer'] if ok else colors['unknown'],2,cv2.LINE_AA)
     if ok:cv2.line(disp,tuple(inner[k].astype(int)),tuple(inner[k+1].astype(int)),colors['inner'],1,cv2.LINE_AA)
 # blend fill
 overlay=cv2.addWeighted(im,.68,overlay,.32,0)
 # ROI expand triangle by 32 px
 x0=max(0,int(verts[:,0].min()-38));y0=max(0,int(verts[:,1].min()-48));x1=min(im.shape[1],int(verts[:,0].max()+38));y1=min(im.shape[0],int(verts[:,1].max()+45))
 region=lambda ar:Image.fromarray(ar[y0:y1,x0:x1]);return [region(im),region(overlay),region(disp)],res

if __name__=='__main__':
 imgs=[];results={}
 for name,case in CASES.items():
  ps,r=reconstruct(case);imgs.append((name,ps));results[name]=r
  stem='genuine' if name.startswith('Genuine') else 'qc'
  upscale=2.1 if stem=='genuine' else 4.0
  # selected raw and evidence overlay saved separately
  ps[-1].resize((int(ps[-1].width*upscale),int(ps[-1].height*upscale)),Image.Resampling.LANCZOS).save(O/f'bevel_{stem}_evidence.png')
  ps[1].resize((int(ps[1].width*upscale),int(ps[1].height*upscale)),Image.Resampling.LANCZOS).save(O/f'bevel_{stem}_filled.png')
 width=1170;w=width//3;h=540
 canvas=Image.new('RGB',(width,2*(h+53)),(9,15,24));dr=ImageDraw.Draw(canvas)
 for row,(name,ps) in enumerate(imgs):
  for col,p in enumerate(ps):
   scale=min((w-15)/p.width,(h-25)/p.height);r=p.resize((int(p.width*scale),int(p.height*scale)),Image.Resampling.LANCZOS)
   canvas.paste(r,(col*w+(w-r.width)//2,row*(h+53)+45))
   dr.text((col*w+12,row*(h+53)+12),name+[' / Original',' / Silver strip',' / Edges + uncertainty'][col] if col==0 else ['Original','Silver bevel trace','Edges + uncertainty'][col],fill=(240,248,253))
 canvas.save(O/'silver_bevel_comparison.png')
 print(results)
