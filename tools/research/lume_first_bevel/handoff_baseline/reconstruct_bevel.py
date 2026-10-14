"""Research-only analytical bevel reconstruction: input photographs and manually seeded side corridors.
The algorithm uses two independent local negative gradients along each normal (lume-to-bevel and bevel-to-dial),
a soft bezel-thickness prior, and a continuity penalty. No reference outline is used to move any observed edge.
This is a visual prototype, not a validated detector or Watch Align code.
"""
from PIL import Image, ImageDraw, ImageFont
from scipy.ndimage import map_coordinates, gaussian_filter1d, gaussian_filter
import cv2, numpy as np
from pathlib import Path
OUT=Path('/mnt/data')
CASES={
 'Genuine, photographed silver surround': {'file':'tri_watch.jpg','vertices':[(149,130),(329,130),(244,350)],'width':7,'scan':17,'pr':3.0},
 'QC, photographed silver surround': {'file':'qc_triangle_crop_work.png','vertices':[(137,155),(213,155),(179,247)],'width':3.5,'scan':9,'pr':1.5},
}

def sample(gray,a,b,ctr,window,width,noise=0.7):
    v=(b-a);t=v/np.linalg.norm(v);n=np.array([t[1],-t[0]])
    if np.dot(n,(a+b)/2-ctr)<0:n=-n
    us=np.linspace(.13,.85,66);offs=np.arange(-window,window+.01,.5)
    grey=gaussian_filter(gray,noise)
    profiles=[]
    for u in us:
       p=a+(b-a)*u
       xy=p[None,:]+offs[:,None]*n[None,:]
       vec=map_coordinates(grey,[xy[:,1],xy[:,0]],order=1,mode='nearest')
       profiles.append(gaussian_filter1d(vec,.9/.5))
    prof=np.array(profiles)
    # Test pairs of falling transitions; g[i] is approximate contrast over ~1 px.
    grad=-np.gradient(prof,.5,axis=1)
    locs=np.where((offs>-window+2)&(offs<window-2))[0]
    states=[]
    for k in range(len(us)):
      p=prof[k];g=grad[k]
      lum=np.median(p[offs< -window*.67]);dial=np.median(p[offs>window*.7]);total=lum-dial
      poss=[]
      if total>40:
        for i in locs:
          if g[i]<2.0:continue
          for j in locs:
            dist=offs[j]-offs[i]
            if dist<width*.4 or dist>width*2.0:continue
            if g[j]<2.0:continue
            # Pair quality: inward fall then outward fall plus the low-contrast silver rebound.
            metal=p[i+1:j]
            if len(metal)<2:continue
            hi=float(np.max(metal))
            trough=float(np.min(metal))
            contrast=(max(0,g[i])+max(0,g[j]))
            thickness_penalty=2.1*((dist-width)/max(width,2))**2
            loc_penalty=.18*(abs(offs[i])+abs(offs[j]))
            rebound=max(0,hi-trough)
            quality=np.log1p(max(0,contrast))*5 + .015*rebound-thickness_penalty-loc_penalty
            poss.append((quality,i,j))
      # maintain a handful of best candidates plus missing state
      poss.sort(reverse=True)
      states.append(poss[:50])
    # dynamic programming with continuity for both edges; independent pair search but coherent along each side.
    paths=[]; prev=None
    for k,ss in enumerate(states):
      if not ss:
       paths.append(None);prev=None;continue
      scores=np.array([x[0] for x in ss]);bestidx=None
      if prev is not None:
        prevss,prevscores=prev
        bm=[]
        for score,i,j in ss:
           pospen=np.array([.75*(abs(offs[i]-offs[ii])+abs(offs[j]-offs[jj])) for _,ii,jj in prevss])
           sc=prevscores-pospen
           ip=int(np.argmax(sc));bm.append((score+sc[ip],ip))
        scores=np.array([z[0] for z in bm]);bestidx=np.array([z[1] for z in bm])
      paths.append({'cand':ss,'score':scores,'back':bestidx})
      prev=(ss,scores)
    # Backtracking runs
    chosen=[None]*len(us)
    k=len(us)-1
    while k>=0:
      if paths[k] is None:k-=1;continue
      end=k
      while k>=0 and paths[k] is not None:k-=1
      start=k+1
      idx=int(np.argmax(paths[end]['score']))
      for h in range(end,start-1,-1):
        v=paths[h]['cand'][idx]
        score,i,j=v
        local=float(grad[h,i]+grad[h,j])
        lum=np.median(prof[h,offs< -window*.67]);dial=np.median(prof[h,offs>window*.7]);difference=lum-dial
        # Good evidence: both distinct drops and a bright interior vs dark dial.
        strong=(grad[h,i]>max(2.5,.035*difference) and grad[h,j]>max(3.2,.055*difference))
        chosen[h]=(offs[i],offs[j],strong,local,difference)
        idx=int(paths[h]['back'][idx]) if paths[h]['back'] is not None else 0
    return us,n,chosen,prof,offs

def drawpoly(img,pts,color,width=2,step=None):
    if len(pts)>=2:cv2.polylines(img,[np.array(pts,dtype=np.int32)],False,color,width,cv2.LINE_AA)

def reconstruct(case):
    raw=np.asarray(Image.open(OUT/case['file']).convert('RGB'))
    H,W=raw.shape[:2];verts=np.array(case['vertices'],float);ctr=verts.mean(0)
    gray=cv2.cvtColor(raw,cv2.COLOR_RGB2GRAY).astype(float)
    disp=raw.copy();blue=np.zeros_like(raw);mask=np.zeros((H,W),np.uint8)
    report={};edges=[]
    for side,(i,j) in [('top',(0,1)),('left',(0,2)),('right',(1,2))]:
      a,b=verts[i],verts[j]
      us,n,chosen,prof,offs=sample(gray,a,b,ctr,case['scan'],case['width'])
      inner=[];outer=[];valid=[];regions=[]
      for u,ch in zip(us,chosen):
        if ch is None: continue
        inn,out,good,contrast,total=ch
        p=a+u*(b-a)
        inner.append(p+n*inn);outer.append(p+n*out);valid.append(good)
        regions.append((p+n*inn,p+n*out))
      inner=np.array(inner);outer=np.array(outer);valid=np.array(valid)
      if not len(inner):report[side]={'seen':0};continue
      edges.append((inner,outer,valid,regions))
      thickness=np.linalg.norm(outer-inner,axis=1)
      report[side]={'seen':int(np.sum(valid)),'samples':len(valid),'median_thickness_px':round(float(np.median(thickness)),1),'range_px':(round(float(np.percentile(thickness,10)),1),round(float(np.percentile(thickness,90)),1))}
      # Blue actual thickness stripe; segment only confident observations.
      for k in range(len(inner)-1):
         if valid[k] and valid[k+1]:
            quad=np.array([inner[k],inner[k+1],outer[k+1],outer[k]],np.int32)
            cv2.fillPoly(blue,[quad],(25,160,240))
            cv2.fillPoly(mask,[quad],255)
      # observed segments are cyan outer / purple inner. Weak evidence dotted amber.
      for k in range(len(inner)-1):
        colO=(55,225,245) if valid[k] and valid[k+1] else (250,185,75)
        colI=(242,100,250) if valid[k] and valid[k+1] else (250,185,75)
        if valid[k] and valid[k+1] or k%3==0:
          cv2.line(disp,tuple(outer[k].astype(int)),tuple(outer[k+1].astype(int)),colO,2,cv2.LINE_AA)
          cv2.line(disp,tuple(inner[k].astype(int)),tuple(inner[k+1].astype(int)),colI,1,cv2.LINE_AA)
    # prepare montage: raw, reconstructed bevel rendering alone, overlay
    recolored=np.round(raw*.83 + blue*.17).clip(0,255).astype(np.uint8)
    panel1=Image.fromarray(raw);panel2=Image.fromarray(recolored);panel3=Image.fromarray(disp)
    # fit within 400 high etc
    factor=min(1.7,640/H);resize=lambda x:x.resize((round(W*factor),round(H*factor)),Image.Resampling.LANCZOS)
    ims=[resize(panel1),resize(panel2),resize(panel3)]
    canv=Image.new('RGB',(ims[0].width*3,ims[0].height+56),(13,17,25));d=ImageDraw.Draw(canv)
    for idx,im in enumerate(ims):
      canv.paste(im,(idx*im.width,55))
      d.text((idx*im.width+10,14),['Original','Reconstructed silver-band pixels','Inner / outer border'][idx],fill=(240,245,252))
    return canv,report

if __name__=='__main__':
 for name,case in CASES.items():
  panel,res=reconstruct(case)
  fn='silver_bevel_genuine_prototype.png' if name.startswith('Genuine') else 'silver_bevel_qc_prototype.png'
  panel.save(OUT/fn)
  print(fn,res)
