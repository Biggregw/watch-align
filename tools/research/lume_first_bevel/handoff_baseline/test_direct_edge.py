from PIL import Image
import numpy as np,cv2
from scipy.ndimage import map_coordinates, gaussian_filter1d
case={
 'QC original':('1791959776897.jpeg',242,[(146,99),(513,99),(328,557)]),
 'Genuine A (older)':('1791961152875.jpeg',319,[(143,99),(510,99),(326,557)]),
 'Genuine B (older)':('38121.jpg',319,[(141,102),(507,102),(325,560)]),
}

def getim(name, ya):
 im=np.array(Image.open('/mnt/data/'+name).convert('RGB'))[ya:ya+650,29:679]
 hsv=cv2.cvtColor(im,cv2.COLOR_RGB2HSV)
 yellow=(hsv[:,:,0]>12)&(hsv[:,:,0]<40)&(hsv[:,:,1]>60)&(hsv[:,:,2]>70)&(im[:,:,0]>110)&(im[:,:,1]>85)
 yellow=np.uint8(yellow)*255
 # expand yellow line 1 px
 yellow=cv2.dilate(yellow,np.ones((3,3),np.uint8))
 im=cv2.inpaint(im,yellow,2,cv2.INPAINT_TELEA)
 return im,yellow

def measure(im,poly,edge,frac=0.3):
 poly=np.array(poly,float);center=poly.mean(0)
 if edge=='top': a,b=poly[0],poly[1];u=np.linspace(.10,.90,42)
 if edge=='left': a,b=poly[0],poly[2];u=np.linspace(.13,.77,50)
 if edge=='right':a,b=poly[1],poly[2];u=np.linspace(.13,.77,50)
 tangent=(b-a)/np.linalg.norm(b-a);nn=np.array([tangent[1],-tangent[0]])
 if np.dot(nn,(a+b)/2-center)<0: nn=-nn
 white=np.min(im.astype(float),axis=2)
 offsets=np.arange(-40,41,.5)
 vals=[]; allpts=[]
 for v in u:
  p=a*(1-v)+b*v
  pts=p[None,:]+offsets[:,None]*nn[None,:]
  intensity=map_coordinates(white,[pts[:,1],pts[:,0]],order=1,mode='nearest')
  smooth=gaussian_filter1d(intensity,1.6) # 0.8px
  inside=np.median(smooth[(offsets>-32)&(offsets<-20)])
  outside=np.median(smooth[(offsets>24)&(offsets<38)])
  if inside-outside<35:continue
  thr=outside+frac*(inside-outside)
  # image bright inside/outside dark -- crossings, sustained dark outer > 5 px
  arr=smooth<thr
  poss=[]
  for j in range(1,len(offsets)-12):
   if offsets[j]<-32 or offsets[j]>32:continue
   if not arr[j-1] and arr[j] and np.mean(arr[j:j+10])>.85:
    # interpolate
    alpha=(thr-smooth[j-1])/(smooth[j]-smooth[j-1]+1e-8)
    pos=offsets[j-1]+(offsets[j]-offsets[j-1])*alpha
    poss.append(pos)
  if not poss:continue
  # favor sustained outer dark crossing closest to zero; distant if legitimate
  pos=min(poss,key=lambda x:abs(x))
  vals.append(pos);allpts.append(p)
 return np.array(vals),allpts

if __name__=='__main__':
 for case_name,(file,ya,poly) in case.items():
  im,mask=getim(file,ya)
  print('\n'+case_name)
  for edge in ['top','left','right']:
   print(edge,' '.join(f'{frac}: n{len(x)} median{np.median(x):+.1f}px IQR[{np.percentile(x,25):+.1f},{np.percentile(x,75):+.1f}]' if len(x)>2 else f'{frac}: n{len(x)}' for frac in [.25,.35,.45,.55,.65] for x,_ in [measure(im,poly,edge,frac)]))
