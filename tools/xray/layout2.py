"""Добавляет уровни Anomaly к готовой раскладке ТЧ: центр по глобальной карте Anomaly (линейная подгонка по 11 уровням ТЧ),
размер по level.cform, раздвижка до промежутка GAP; уровни ТЧ стоят на месте."""
import json,re,struct,sys,numpy as np
OLD=json.load(open('levels_soc.json')); GAP=170; MARGIN=40
t=open('game_maps_single.ltx',encoding='cp1251',errors='replace').read()
G={}; sec=None
for line in t.splitlines():
    m=re.match(r'\s*\[([^\]]+)\]',line)
    if m: sec=m.group(1); continue
    m=re.match(r'\s*global_rect\s*=\s*([^;]+)',line)
    if sec and m: G[sec]=[float(v.strip().rstrip('f')) for v in m.group(1).split(',')]
NEW=sys.argv[1:]
L={k:dict(v) for k,v in OLD.items()}
def ctr(r): return ((r[0]+r[2])/2,(r[1]+r[3])/2)
A=[];BX=[];BZ=[]
for k,v in OLD.items():
    g=ctr(G[k]); r=ctr(v['rect']); A.append([g[0],1]); BX.append(r[0]); BZ.append([g[1],1]) or None
A=np.array(A); ax=np.linalg.lstsq(A,np.array(BX),rcond=None)[0]
Az=np.array([[ctr(G[k])[1],1] for k in OLD]); az=np.linalg.lstsq(Az,np.array([ctr(v['rect'])[1] for v in OLD.values()]),rcond=None)[0]
print('масштаб x %.2f z %.2f'%(ax[0],az[0]))
pos={}
for k in NEW:
    cf=open('cut/%s/level.cform'%k,'rb').read(36); bb=struct.unpack_from('<6f',cf,12)
    bound=[bb[0],bb[2],bb[3],bb[5]]
    g=ctr(G[k]); mx=ax[0]*g[0]+ax[1]; mz=az[0]*g[1]+az[1]
    L[k]={'bbox':[round(v) for v in bb],'bound':bound,'global':G[k],'anomaly':True}
    pos[k]=[mx-(bound[0]+bound[2])/2, mz+(bound[1]+bound[3])/2]
for k in OLD: pos[k]=[OLD[k]['dx'],OLD[k]['dz']]
def rect(k):
    bx0,bz0,bx1,bz1=L[k]['bound']; dx,dz=pos[k]
    return [bx0+dx-MARGIN,-bz1+dz-MARGIN,bx1+dx+MARGIN,-bz0+dz+MARGIN]
keys=list(L)
for it in range(2000):
    moved=False
    for i,a in enumerate(keys):
        for b in keys[i+1:]:
            wa=a in NEW; wb=b in NEW
            if not (wa or wb): continue
            P,Q=rect(a),rect(b)
            ox=min(P[2],Q[2])-max(P[0],Q[0])+GAP; oz=min(P[3],Q[3])-max(P[1],Q[1])+GAP
            if ox<=0 or oz<=0: continue
            moved=True
            ca,cb=ctr(P),ctr(Q)
            if ox<oz: axi,d=0,ox*(1 if cb[0]>=ca[0] else -1)
            else: axi,d=1,oz*(1 if cb[1]>=ca[1] else -1)
            if wa and wb: pos[b][axi]+=d/2; pos[a][axi]-=d/2
            elif wb: pos[b][axi]+=d
            else: pos[a][axi]-=d
    if not moved: break
print('итераций',it)
for k in NEW:
    pos[k]=[round(pos[k][0]),round(pos[k][1])]; L[k]['dx'],L[k]['dz']=pos[k]; L[k]['rect']=[round(v) for v in rect(k)]
    print('%-22s DX %6d DZ %6d rect'%(k,*pos[k]),L[k]['rect'])
json.dump(L,open('levels.json','w'),indent=1)
