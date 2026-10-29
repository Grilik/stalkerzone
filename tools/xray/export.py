"""Экспорт уровня X-Ray в регион мода (.szl): рельеф, покрытие, вода, постройки вокселями, вырезы под землёй, деревья."""
import numpy as np, struct, gzip, sys
from scipy import ndimage
CF_DX, CF_DZ, YOFF = 209, -142, 100     # деревня новичков (xr -209,-142) -> MC (0,0); MC y = xr_y + YOFF
d=open('esc.bin','rb').read()
W,H=struct.unpack_from('<2i',d,0); bb=struct.unpack_from('<6f',d,8); o=32
def arr(dt,sz):
    global o
    a=np.frombuffer(d,dt,W*H,o).reshape(H,W).copy(); o+=sz*W*H; return a
top=arr(np.float32,4); low=arr(np.float32,4); mat=arr(np.uint16,2); ter=arr(np.float32,4); tmat=arr(np.uint16,2); wat=arr(np.float32,4)
TERR={7,10,15,8,16,4,93}
# дыры в рельефе (под домами, вне сетки) — ближайшее значение
hole=ter<-1e8
idx=ndimage.distance_transform_edt(hole,return_distances=False,return_indices=True)
ter=ter[idx[0],idx[1]]; tmat=tmat[idx[0],idx[1]]
ter=ndimage.gaussian_filter(ter,0.6)   # лёгкое сглаживание ступенек сетки
water=(wat>ter+0.15)
# --- воксели
f=open('vox.bin','rb'); VW,VH,VD=struct.unpack('<3i',f.read(12)); vbb=struct.unpack('<6f',f.read(24))
g=np.memmap('vox.bin',np.uint16,'r',36,(VD,VW,VH))
SKIP={80,82,37,39,88,93,11}|TERR
vox=[];air=[]
Y0=int(np.floor(vbb[1]))
for zi in range(VD):
    sl=np.array(g[zi])            # [x,y]
    if not sl.any(): continue
    m=(sl&0x3fff).astype(np.int32)-1
    bmask=(sl>0)&~np.isin(m,list(SKIP))
    xs,ys=np.nonzero(bmask)
    if len(xs)==0: continue
    xr_z=vbb[2]+zi+0.5
    for x,y in zip(xs,ys):
        vox.append((int(round(vbb[0]+x+0.5))+CF_DX, Y0+y+YOFF, int(round(-xr_z))+CF_DZ, m[x,y]))
    # подземные помещения: между полом и потолком ниже земли
    for x in np.unique(xs):
        col=ys[xs==x]; gy=ter[min(H-1,max(0,int(xr_z-bb[2]))), min(W-1,max(0,int(vbb[0]+x+0.5-bb[0])))]
        under=sorted(yy for yy in col if Y0+yy < gy-0.5)
        for a,b in zip(under,under[1:]):
            if b-a>=3:
                for yy in range(a+1,b): air.append((int(round(vbb[0]+x+0.5))+CF_DX, Y0+yy+YOFF, int(round(-xr_z))+CF_DZ))
print('voxels',len(vox),'air',len(air))
# --- деревья: компоненты крон (bush) по карте сверху
bush=(mat==37)&((top-ter)>2.0)
lab,n=ndimage.label(bush)
trees=[]
for i,sl in enumerate(ndimage.find_objects(lab)):
    zz,xx=sl; m_=lab[sl]==i+1; area=m_.sum()
    if area<2: continue
    cz=(zz.start+zz.stop-1)/2; cx=(xx.start+xx.stop-1)/2
    hgt=float(np.nanmax(top[sl][m_]))-float(ter[int(cz),int(cx)])
    r=max(1,int(round(np.sqrt(area/np.pi))))
    big = area>60
    # крупные «облака» — это опушки: рассыпать несколько деревьев
    k=1 if not big else int(area/40)
    ys_,xs_=np.nonzero(m_)
    rng=np.random.default_rng(i)
    for t in range(k):
        j=rng.integers(len(xs_)) if big else None
        px=xx.start+(xs_[j] if big else cx-xx.start); pz=zz.start+(ys_[j] if big else cz-zz.start)
        xr_x=bb[0]+px+0.5; xr_z=bb[2]+pz+0.5
        gy=ter[int(pz),int(px)]
        trees.append((int(round(xr_x))+CF_DX, int(round(-xr_z))+CF_DZ, int(round(gy))+YOFF, int(min(24,max(4,hgt))), min(6,r if not big else 3), 0))
print('trees',len(trees))
# --- сетка в координатах MC: x = xr_x+CF_DX, z = -xr_z+CF_DZ  → строки по возрастанию MC z = убыванию xr z
mcx0=int(round(bb[0]+0.5))+CF_DX; mcz0=int(round(-(bb[2]+H-0.5)))+CF_DZ
G=ter[::-1]; S=tmat[::-1]; Wt=np.where(water,wat,-1000)[::-1]
SURF={7:1,10:2,15:3,8:4,16:5,4:6,93:7}
surf=np.vectorize(lambda v: SURF.get(int(v),1))(S).astype(np.uint8)
out=gzip.open(sys.argv[1],'wb')
out.write(b'SZL1'); out.write(struct.pack('<5i',mcx0,mcz0,W,H,YOFF))
out.write(np.round((G+YOFF)*8).astype('<i2').tobytes())
out.write(surf.tobytes())
out.write(np.round((Wt+YOFF)*8).astype('<i2').tobytes())
vox.sort(key=lambda v:((v[0]>>4),(v[2]>>4)))
out.write(struct.pack('<i',len(vox)))
out.write(b''.join(struct.pack('<hhhB',*v) for v in vox))
air.sort(key=lambda v:((v[0]>>4),(v[2]>>4)))
out.write(struct.pack('<i',len(air)))
out.write(b''.join(struct.pack('<hhh',*v) for v in air))
out.write(struct.pack('<i',len(trees)))
out.write(b''.join(struct.pack('<hhhBBB',*t) for t in trees))
out.close()
import os; print('size',os.path.getsize(sys.argv[1]), 'origin',mcx0,mcz0, 'ground MC y', float(np.min(G))+YOFF, float(np.max(G))+YOFF)
