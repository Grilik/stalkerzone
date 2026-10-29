"""Anomaly level.cform -> те же id материалов, что у ТЧ (новые материалы Anomaly сводим к ближайшим)."""
import numpy as np,struct,sys
MAP={94:7,95:37,96:11,97:80,98:82,99:80}
d=bytearray(open(sys.argv[1],'rb').read())
ver,nv,nf=struct.unpack_from('<3I',d,0); bb=struct.unpack_from('<6f',d,12)
print(sys.argv[1],'ver',ver,'nv',nv,'nf',nf,'bbox',[round(v) for v in bb])
o=36+12*nv
f=np.frombuffer(bytes(d[o:o+16*nf]),'<u4').reshape(nf,4).copy()
m=f[:,3]&0x3fff
u,c=np.unique(m,return_counts=True); print(dict(zip(u.tolist(),c.tolist())))
for a,b in MAP.items(): f[m==a,3]=(f[m==a,3]&~np.uint32(0x3fff))|b
# материал default (0): у части уровней Anomaly так помечен рельеф — пологие грани считаем землёй
V=np.frombuffer(bytes(d[36:36+12*nv]),'<f4').reshape(nv,3)
i=f[:,:3].astype(np.int64); a,b,c=V[i[:,0]],V[i[:,1]],V[i[:,2]]
nrm=np.cross(b-a,c-a); ln=np.linalg.norm(nrm,axis=1)+1e-12; ny=np.abs(nrm[:,1])/ln
m=f[:,3]&0x3fff; sel=(m==0)&(ny>0.6); print('default->earth',int(sel.sum()))
f[sel,3]=(f[sel,3]&~np.uint32(0x3fff))|7
# скалы: в X-Ray камень помечен «бетоном»; наклонные грани бетона — признак скалы (id 100), дома так не строят
m=f[:,3]&0x3fff; rk=(m==3)&(ny>0.15)&(ny<0.9); print('rock faces',int(rk.sum()))
f[rk,3]=(f[rk,3]&~np.uint32(0x3fff))|100
d[o:o+16*nf]=f.tobytes(); open(sys.argv[2],'wb').write(d)
