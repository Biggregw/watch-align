"""User photo preview: luminous threshold mask and outer boundary offset from right-side anchor.
Exploratory human-seeded crop + threshold; NOT an independently validated outer-metal detector.
"""
from pathlib import Path
import cv2, numpy as np
from PIL import Image,ImageDraw,ImageFont
O=Path('/mnt/data')
CASES=[
 dict(name='QC PHOTO',info='GMT QC photo (your rejected 12)',fname='qc_triangle_crop_work.png',roi=(135,151,214,249),th=145,outer_limit=16,anchor_y=(174,222),crop=(115,132,235,278),scale=4.0),
 dict(name='GENUINE CONTROL',info='Genuine older GMT (116710 generation)',fname='tri_watch.jpg',roi=(145,127,335,354),th=132,outer_limit=18,anchor_y=(174,289),crop=(115,99,370,383),scale=2.05),
]
FONT='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
BOLD='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
def font(sz=16,bold=False):return ImageFont.truetype(BOLD if bold else FONT,sz)
def contour(mask):
    contours,_=cv2.findContours(np.uint8(mask)*255,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_NONE)
    return max(contours,key=cv2.contourArea)
def make_mask(im,cfg):
    g=cv2.GaussianBlur(cv2.cvtColor(im,cv2.COLOR_BGR2GRAY),(3,3),.6)
    x0,y0,x1,y1=cfg['roi'];bw=np.zeros(g.shape,np.uint8)
    bw[y0:y1,x0:x1]=np.uint8(g[y0:y1,x0:x1]>cfg['th'])
    n,l,s,_=cv2.connectedComponentsWithStats(bw,8)
    i=max(range(1,n),key=lambda k:s[k,cv2.CC_STAT_AREA]);m=np.uint8(l==i)
    return g,m

def sample_anchor(g,m,cfg):
    pts=[]; widths=[]
    for y in range(*cfg['anchor_y']):
        xs=np.where(m[y]>0)[0]
        if len(xs)<5:continue
        xi=int(xs.max())
        outer=next((x for x in range(xi+2,xi+36) if x<g.shape[1] and g[y,x]<=cfg['outer_limit']),None)
        if outer is None:continue
        pts.append((outer,y,xi));widths.append(outer-xi)
    # Width is normal-direction, not measured straight across, adjust for slope of right edge
    if not pts:raise ValueError('No anchor found')
    xi=np.array([p[2] for p in pts],float); y=np.array([p[1] for p in pts],float)
    dxdy=np.polyfit(y,xi,1)[0]; normal_x=1/np.sqrt(1+dxdy*dxdy)
    robust=np.percentile(widths,85)*normal_x
    return pts,round(float(robust),1),round(float(np.percentile(widths,50)*normal_x),1),round(float(normal_x),3)

def alpha_outline(im,m,width,anchor=None,fill=True):
    # Uniform normal-distance offset from identified fill edge using distance transform, NOT a fitted shape.
    layer=im.copy()
    yy,xx=np.nonzero(m)
    if fill:
        dist=cv2.distanceTransform(np.uint8(1-m),cv2.DIST_L2,cv2.DIST_MASK_PRECISE)
        band=(m==0)&(dist<=width)
        blue=np.zeros_like(layer);blue[:]=(235,205,25)  # BGR cyan tint
        layer[band]=np.uint8(.76*layer[band]+.24*blue[band])
    cont=contour(m)
    cv2.drawContours(layer,[cont],-1,(185,72,229),1,cv2.LINE_AA) # magenta (BGR)
    outer=np.uint8(dist<=width)|(m>0) if fill else None
    if outer is not None:
        co=contour(outer)
        # cyan dashed outline (polyline segments every 7 original px)
        po=co.reshape(-1,2)
        for i in range(0,len(po)-1,13):
            seg=po[i:min(i+7,len(po))]
            if len(seg)>=2:cv2.polylines(layer,[seg.reshape(-1,1,2)],False,(255,229,50),1,cv2.LINE_AA)
    if anchor:
        for x,y,xi in anchor[::max(1,len(anchor)//18)]:
            cv2.circle(layer,(x,y),1,(75,230,94),-1,cv2.LINE_AA)
    return layer

def frame(im,crop,scale,title,subtitle):
    x0,y0,x1,y1=crop
    thumb=Image.fromarray(cv2.cvtColor(im[y0:y1,x0:x1],cv2.COLOR_BGR2RGB))
    thumb=thumb.resize((round(thumb.width*scale),round(thumb.height*scale)),Image.Resampling.NEAREST)
    panel=Image.new('RGB',(thumb.width,thumb.height+93),(13,20,32));p=ImageDraw.Draw(panel)
    p.text((13,9),title,font=font(20,True),fill=(252,252,255))
    p.text((13,42),subtitle,font=font(12),fill=(188,198,211))
    panel.paste(thumb,(0,93))
    return panel

rows=[]
for ca in CASES:
    im=cv2.imread(str(O/ca['fname']))
    g,m=make_mask(im,ca)
    pts,w,med,nx=sample_anchor(g,m,ca)
    original=frame(im,ca['crop'],ca['scale'],'1. ORIGINAL', 'Original supplied photograph')
    inner=im.copy();cv2.drawContours(inner,[contour(m)],-1,(185,72,229),1,cv2.LINE_AA)
    p2=frame(inner,ca['crop'],ca['scale'],'2. LUMINOUS EDGE','Pink = detected bright-fill boundary')
    predicted=alpha_outline(im,m,w,anchor=pts,fill=True)
    p3=frame(predicted,ca['crop'],ca['scale'],'3. OUTER EDGE ESTIMATE',f'Width {w:.1f} px; green = anchor evidence')
    W=max(q.width for q in (original,p2,p3));H=max(q.height for q in (original,p2,p3))
    out=Image.new('RGB',(3*W,H+67),(13,20,32));d=ImageDraw.Draw(out)
    for j,p in enumerate((original,p2,p3)):out.paste(p,(j*W+(W-p.width)//2,67))
    d.text((16,8),ca['name']+'  |  '+ca['info'],font=font(23,True),fill=(255,255,255))
    d.text((16,37),f'Right-side candidate: median {med:.1f} px; robust wide {w:.1f} px | image-level exploratory values only',font=font(15),fill=(183,202,220))
    pth=O/('example_bevel_qc.png' if ca['name']=='QC PHOTO' else 'example_bevel_genuine.png')
    out.save(pth,optimize=True)
    print(pth, out.size,'right anchor count',len(pts),'median',med,'width',w, 'normalX',nx)
    rows.append(out)

# Variant width comparison, based on same measured anchor (not moving inner trace)
ca=CASES[0];im=cv2.imread(str(O/ca['fname']));g,m=make_mask(im,ca);pts,w,med,nx=sample_anchor(g,m,ca)
variants=[('NARROWER',max(1,w*.75)),('WIDEST CONSISTENT',w),('LARGER TEST',w*1.25)]
panels=[]
for txt,ww in variants:
  out=alpha_outline(im,m,ww,pts if txt=='WIDEST CONSISTENT' else None)
  panels.append(frame(out,ca['crop'],ca['scale'],txt,f'{ww:.1f} px offset; same pink luminous edge'))
W=max(p.width for p in panels);H=max(p.height for p in panels)
img=Image.new('RGB',(W*3,H+80),(13,20,32));d=ImageDraw.Draw(img)
d.text((16,8),'QC PHOTO: OUTER-BORDER THICKNESS OPTIONS',font=font(22,True),fill='white')
d.text((16,40),'Pink: luminous edge     Cyan: inferred outer edge     Green: right-edge evidence (middle only)',font=font(15),fill=(199,212,230))
for i,p in enumerate(panels):img.paste(p,(W*i,H+80-p.height))
img.save(O/'example_bevel_qc_width_options.png')
print('width options',img.size)

# Cover contact sheet resized for mobile: two full case strips one below another
mW=1480
small=[]
for row in rows:
 h=round(row.height*mW/row.width)
 small.append(row.resize((mW,h),Image.Resampling.LANCZOS))
cover=Image.new('RGB',(mW,sum(i.height for i in small)+20),(13,20,32));y=0
for i in small:cover.paste(i,(0,y));y+=i.height+20
cover.save(O/'example_bevel_side_by_side.png')
print('combined',cover.size)
