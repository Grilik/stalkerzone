import numpy as np, struct, sys
from PIL import Image, ImageDraw
f=open('vox.bin','rb'); W,H,D=struct.unpack('<3i',f.read(12)); bb=struct.unpack('<6f',f.read(24))
g=np.memmap('vox.bin',np.uint16,'r',36,(D,W,H))
cx,cz,r,name=float(sys.argv[1]),float(sys.argv[2]),int(sys.argv[3]),sys.argv[4]
x0=int(cx-bb[0]-r); z0=int(cz-bb[2]-r)
R=np.array(g[z0:z0+2*r, x0:x0+2*r, :])        # [z(север+),x,y]
R=R[::-1]                                       # теперь индекс 0 = север, растёт к югу (Zs)
pal={8:(108,138,68),38:(52,100,44),81:(200,60,60)}
rng=np.random.default_rng(3)
cache={}
S=3
def cube(m):
    if m in cache: return cache[m]
    if m not in pal: pal[m]=tuple(int(v) for v in rng.integers(90,235,3))
    c=pal[m]; im=Image.new('RGBA',(4*S,4*S),(0,0,0,0)); d=ImageDraw.Draw(im)
    t=[(2*S,0),(4*S,S),(2*S,2*S),(0,S)]
    l=[(0,S),(2*S,2*S),(2*S,4*S),(0,3*S)]
    rr=[(2*S,2*S),(4*S,S),(4*S,3*S),(2*S,4*S)]
    d.polygon(t,fill=c); d.polygon(l,fill=tuple(int(v*0.72) for v in c)+(255,)); d.polygon(rr,fill=tuple(int(v*0.5) for v in c)+(255,))
    cache[m]=im; return im
Zs,X,Y=R.shape
ys=np.nonzero(R.any(axis=(0,1)))[0]; y0,y1=ys.min(),ys.max()
Wimg=(X+Zs)*2*S+8*S; Himg=(X+Zs)*S+(y1-y0+2)*2*S+8*S
img=Image.new('RGBA',(Wimg,Himg),(24,24,34,255))
pts=np.argwhere(R>0)  # zs,x,y
key=(pts[:,0]+pts[:,1])*1000+pts[:,2]
pts=pts[np.argsort(key,kind='stable')]
for zs,x,y in pts:
    sx=(x-zs+Zs)*2*S; sy=(x+zs)*S+(y1-y)*2*S
    img.alpha_composite(cube(int(R[zs,x,y])),(int(sx),int(sy)))
img=img.convert('RGB'); img=img.crop(img.getbbox()); img.save(name); print(img.size, len(pts))
