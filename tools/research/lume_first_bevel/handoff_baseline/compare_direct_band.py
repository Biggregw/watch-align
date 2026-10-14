import sys
sys.path.insert(0,'/mnt/data')
from test_direct_edge import case,getim
from scipy.ndimage import map_coordinates,gaussian_filter1d
import numpy as np

for label,(fn,y,poly) in case.items():
 im,_=getim(fn,y);white=np.min(im.astype(float),axis=2);lum=np.mean(im.astype(float),axis=2)
 center=np.array(poly,float).mean(axis=0)
 print('\n'+label)
 for edge in ['top','left','right']:
  p=np.array(poly,float)
  if edge=='top':a,b=p[0],p[1];uu=np.linspace(.16,.84,30)
  if edge=='left':a,b=p[0],p[2];uu=np.linspace(.17,.75,30)
  if edge=='right':a,b=p[1],p[2];uu=np.linspace(.17,.75,30)
  vv=b-a;nn=np.array([vv[1],-vv[0]])/np.linalg.norm(vv)
  if np.dot(nn,(a+b)/2-center)<0:nn=-nn
  for ii,img in [('min RGB',white),('mean RGB',lum)]:
   profiles=[]
   for t in uu:
    q=a+t*(b-a); offsets=np.arange(-35,35.51,.5)
    xy=q[None,:]+offsets[:,None]*nn[None,:]
    profile=gaussian_filter1d(map_coordinates(img,[xy[:,1],xy[:,0]],order=1,mode='nearest'),2)
    profiles.append(profile)
   pp=np.stack(profiles)
   def sm(z,dist=1):return np.median(pp[:,(offsets>=z-dist)&(offsets<=z+dist)],axis=1)
   total=sm(-23)-sm(23)
   print(edge,ii,'total contrast median',round(np.median(total),0),'partial contrasts [-d,+d]',[
    (d,round(np.median(sm(-d)-sm(d)),0),round(np.median((sm(-d)-sm(d))/(np.maximum(30,total))),2)) for d in [4,7,10,15]
   ])
