import json
L=json.load(open('levels.json')); S=3.35; GAP=170; MARGIN=40
pos={}
for k,v in L.items():
    bx0,bz0,bx1,bz1=v['bound']; g=v['global']
    gcx=(g[0]+g[2])/2*S; gcz=(g[1]+g[3])/2*S
    cx=(bx0+bx1)/2; cz=(bz0+bz1)/2
    pos[k]=[gcx-cx, gcz+cz]      # DX, DZ
# Кордон: деревня новичков (-209,-142) -> (0,0)
dx0,dz0=pos['l01_escape']; sx,sz=209-dx0,-142-dz0
for k in pos: pos[k]=[pos[k][0]+sx,pos[k][1]+sz]
def rect(k):
    bx0,bz0,bx1,bz1=L[k]['bound']; dx,dz=pos[k]
    return [bx0+dx-MARGIN, -bz1+dz-MARGIN, bx1+dx+MARGIN, -bz0+dz+MARGIN]
keys=list(L)
for it in range(400):
    moved=False
    for i,a in enumerate(keys):
        for b in keys[i+1:]:
            A,B=rect(a),rect(b)
            ox=min(A[2],B[2])-max(A[0],B[0])+GAP; oz=min(A[3],B[3])-max(A[1],B[1])+GAP
            if ox<=0 or oz<=0: continue
            moved=True
            ca=((A[0]+A[2])/2,(A[1]+A[3])/2); cb=((B[0]+B[2])/2,(B[1]+B[3])/2)
            wa=0 if a=='l01_escape' else 1; wb=0 if b=='l01_escape' else 1
            if wa+wb==0: continue
            if ox<oz: ax,d=0,ox*(1 if cb[0]>=ca[0] else -1)
            else: ax,d=1,oz*(1 if cb[1]>=ca[1] else -1)
            step=d*0.5 if wa and wb else d
            if wb: pos[b][ax]+=step if wa else d
            if wa: pos[a][ax]-=step if wb else d
    if not moved: break
print('итераций',it)
for k in keys:
    pos[k]=[round(pos[k][0]),round(pos[k][1])]; r=rect(k); L[k]['dx'],L[k]['dz']=pos[k]; L[k]['rect']=[round(v) for v in r]
    print('%-16s DX %6d DZ %6d  rect x %6d..%6d z %6d..%6d'%(k,*pos[k],*[round(v) for v in r]))
json.dump(L,open('levels.json','w'),indent=1)
