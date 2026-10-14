from PIL import Image
import numpy as np,cv2,matplotlib.pyplot as plt
from scipy.ndimage import map_coordinates,gaussian_filter1d
src={
'Genuine crop':('/mnt/data/tri_watch.jpg',[(150,132),(330,132),(244,352)]),
'QC original':('/mnt/data/qc_triangle_crop_work.png',[(138,154),(213,154),(181,246)]),
}
fig,axs=plt.subplots(2,3,figsize=(14,8))
for (name,(fn,pts)),axrow in zip(src.items(),axs):
 im=np.array(Image.open(fn).convert('RGB'));gray=cv2.cvtColor(im,cv2.COLOR_RGB2GRAY).astype(float);arr=np.array(pts,float);cen=arr.mean(0)
 for edge,ax in zip(['top','left','right'],axrow):
  a,b={'top':(arr[0],arr[1]),'left':(arr[0],arr[2]),'right':(arr[1],arr[2])}[edge];v=b-a;t=v/np.linalg.norm(v);n=np.array([t[1],-t[0]])
  if np.dot(n,(a+b)/2-cen)<0:n=-n
  offsets=np.arange(-18,19,.25)
  for u in [.25,.5,.7]:
   p=a*(1-u)+b*u;pts=p+offsets[:,None]*n
   vals=gaussian_filter1d(map_coordinates(gray,[pts[:,1],pts[:,0]],order=1,mode='nearest'),2)
   ax.plot(offsets,vals,label=str(u))
  ax.axvline(0,ls='--',c='k',alpha=.3);ax.set_title(name+' '+edge);ax.grid(alpha=.2);ax.legend();ax.set_xlabel('outward px')
plt.tight_layout();plt.savefig('/mnt/data/bevel_profiles.png',dpi=130)
