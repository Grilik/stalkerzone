"""Экспорт уровня X-Ray в регион мода (.szl): рельеф, покрытие, вода, постройки вокселями, вырезы под землёй, деревья."""
import numpy as np, struct, gzip, sys, json
from scipy import ndimage
import json as _j; _L=_j.load(open("levels.json"))[sys.argv[2]]; CF_DX, CF_DZ, YOFF = _L["dx"], _L["dz"], _L.get("yoff",100); _B=_L["bound"]; CROP=(_B[0]-40,_B[1]-40,_B[2]+40,_B[3]+40)
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
# --- скалы: связные (по следу сверху) массивы «бетона», где заметная доля граней наклонная, — камень целиком
rk=[v for v in vox if v[3] in (3,100)]
if rk:
    a=np.array([(v[0],v[2],v[3]) for v in rk]); x0_,z0_=a[:,0].min(),a[:,1].min()
    fp=np.zeros((a[:,1].max()-z0_+1,a[:,0].max()-x0_+1),bool); fp[a[:,1]-z0_,a[:,0]-x0_]=True
    lab,_n=ndimage.label(fp,structure=np.ones((3,3)))
    li=lab[a[:,1]-z0_,a[:,0]-x0_]
    tot=np.bincount(li,minlength=_n+1); rcnt=np.bincount(li,weights=(a[:,2]==100),minlength=_n+1)
    rock=(rcnt/np.maximum(tot,1))>0.25
    vox=[(v[0],v[1],v[2],100 if rock[lab[v[2]-z0_,v[0]-x0_]] else 3) if v[3] in (3,100) else v for v in vox]
    print('rock voxels',sum(1 for v in vox if v[3]==100))
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
import os
if os.path.exists('trees.json'):   # деревья по экземплярам из рендер-геометрии (geo2cform.py): точные места и породы
    inst={}
    for nm,x,z,y0,y1,r in json.load(open('trees.json')):
        k=(int(round(x)),int(round(z))); e=inst.setdefault(k,{'n':set(),'y1':-1e9,'r':1})
        e['n'].add(nm); e['y1']=max(e['y1'],y1); e['r']=max(e['r'],r)
    trees=[]
    for (x,z),e in inst.items():
        n=' '.join(e['n'])
        kind=5 if 'sux' in n else 2 if 'elka' in n else 1 if 'sosna' in n else 3 if 'bark_a_01' in n else 4
        zi=int(z-bb[2]); xi=int(x-bb[0])
        if not (0<=zi<H and 0<=xi<W): continue
        gy=ter[zi,xi]
        trees.append((x+CF_DX, int(round(-z))+CF_DZ, int(round(gy))+YOFF, int(min(24,max(4,e['y1']-gy))), int(min(5,max(1,round(e['r'])))), kind))
print('trees',len(trees))
# --- сетка в координатах MC: x = xr_x+CF_DX, z = -xr_z+CF_DZ  → строки по возрастанию MC z = убыванию xr z
# обрезка по игровой области уровня (+40 м): дальние декорационные холмы не нужны
xi0=max(0,int(np.floor(CROP[0]-bb[0]))); xi1=min(W,int(np.ceil(CROP[2]-bb[0])))
zi0=max(0,int(np.floor(CROP[1]-bb[2]))); zi1=min(H,int(np.ceil(CROP[3]-bb[2])))
ter=ter[zi0:zi1,xi0:xi1]; tmat=tmat[zi0:zi1,xi0:xi1]; water=water[zi0:zi1,xi0:xi1]; wat=wat[zi0:zi1,xi0:xi1]
bb=list(bb); bb[0]+=xi0; bb[2]+=zi0; H,W=ter.shape
mcx0=int(round(bb[0]+0.5))+CF_DX; mcz0=int(round(-(bb[2]+H-0.5)))+CF_DZ
inr=lambda x,z: mcx0<=x<mcx0+W and mcz0<=z<mcz0+H
vox=[v for v in vox if inr(v[0],v[2])]; air=[v for v in air if inr(v[0],v[2])]; trees=[t for t in trees if inr(t[0],t[1])]
if _L.get('clip'):   # v1.18: постройки и деревья только внутри игровой области — фоновые «декорации за забором» не ставим
    bx0,bz0,bx1,bz1=_B[0]+CF_DX,-_B[3]+CF_DZ,_B[2]+CF_DX,-_B[1]+CF_DZ
    inb=lambda x,z: bx0<=x<=bx1 and bz0<=z<=bz1
    vox=[v for v in vox if inb(v[0],v[2])]; air=[v for v in air if inb(v[0],v[2])]; trees=[t for t in trees if inb(t[0],t[1])]
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
