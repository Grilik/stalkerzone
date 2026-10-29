"""Здания уровня из вокселей .szl: следы построек (прямоугольник по осям), высоты стен и конька, материалы,
двери и окна; заборы — ломаными. Выход: <id>.bld.json (координаты мира MC)."""
import sys, json, numpy as np
from scipy import ndimage
import os; sys.path.insert(0,os.path.dirname(os.path.abspath(__file__))); import szl
import geo2cform as _g
CAT={128+i:_g.base_category(m[5].replace('/','\\')) for i,m in enumerate(_g._ZM)}
SKIP={100,214,215,216,217,221,222}
WALLM=(200,201,202,203,204,205,209,210,223,224,225)
def run(path,out):
    L=szl.load(path); v=L['v']; x0,z0,W,D=L['x0'],L['z0'],L['w'],L['d']; G=L['g']
    cm=np.array([CAT.get(int(m),int(m)) or 0 for m in v['m']]) if len(v) else np.zeros(0,int)
    v=v.copy(); v['m']=np.where(cm>0,cm,v['m']).astype(v['m'].dtype)
    v=v[~np.isin(v['m'],list(SKIP))]
    xi=v['x']-x0; zi=v['z']-z0; ok=(xi>=0)&(xi<W)&(zi>=0)&(zi<D); v=v[ok]; xi=xi[ok]; zi=zi[ok]
    gy=np.floor(G[zi,xi]).astype(int); h=v['y']-gy            # высота над землёй
    v=v[h>=1]; xi=xi[h>=1]; zi=zi[h>=1]; h=h[h>=1]
    top=np.zeros((D,W),int); np.maximum.at(top,(zi,xi),h)
    cnt=np.zeros((D,W),int); np.add.at(cnt,(zi,xi),1)
    tall=top>=3
    tall=ndimage.binary_closing(tall,np.ones((3,3)))
    tall=ndimage.binary_opening(tall,np.ones((3,3)))   # без заборов и столбов
    lab,n=ndimage.label(tall,np.ones((3,3)))
    blds=[]
    vox={}
    for a,b,c,m in zip(v['x'],v['y'],v['z'],v['m']): vox[(int(a),int(b),int(c))]=int(m)
    for i,sl in enumerate(ndimage.find_objects(lab)):
        zz,xx=sl; msk=lab[sl]==i+1; area=int(msk.sum())
        if area<12: continue
        ys,xs=np.nonzero(msk)
        X0,X1=xx.start+xs.min()+x0, xx.start+xs.max()+x0; Z0,Z1=zz.start+ys.min()+z0, zz.start+ys.max()+z0
        if (X1-X0+1)*(Z1-Z0+1)>40000: continue
        fill=area/((X1-X0+1)*(Z1-Z0+1))
        base=int(np.floor(np.median(G[zz,xx][msk])))
        # воксели здания
        sel=(v['x']>=X0)&(v['x']<=X1)&(v['z']>=Z0)&(v['z']<=Z1)
        bv=v[sel]; hh=bv['y']-base
        ridge=int(hh.max())
        # высота стен: по краевой полосе (1 блок от границы) — медиана верхов колонок
        edge=(bv['x']<=X0+1)|(bv['x']>=X1-1)|(bv['z']<=Z0+1)|(bv['z']>=Z1-1)
        cols={}
        for x,z,y in zip(bv['x'][edge],bv['z'][edge],hh[edge]): cols[(x,z)]=max(cols.get((x,z),0),y)
        eave=int(np.median(list(cols.values()))) if cols else ridge
        eave=max(2,min(eave,ridge))
        # конёк вдоль длинной стороны, если крыша выше стен
        wx,wz=X1-X0+1,Z1-Z0+1
        axis='x' if wx>=wz else 'z'
        # материалы
        lower=bv['m'][(hh<=eave)&edge]; upper=bv['m'][hh>eave] if ridge>eave else bv['m'][hh>=eave]
        def mode(a,pref=None):
            a=[int(t) for t in a if pref is None or t in pref]
            return max(set(a),key=a.count) if a else None
        wall=mode(lower,WALLM) or 204; roof=mode(upper) or 207
        up=[int(t) for t in upper]
        if up and up.count(207)>=0.2*len(up): roof=207
        # целость крыши: доля колонок с вокселем выше стен
        cover=0
        if ridge>eave:
            tops={}
            for x,z,y in zip(bv['x'],bv['z'],hh): tops[(x,z)]=max(tops.get((x,z),0),y)
            cover=sum(1 for k,t in tops.items() if t>eave)/max(1,wx*wz)
        doors=[[int(x),int(y),int(z)] for (x,y,z),m in vox.items() if m==213 and X0<=x<=X1 and Z0<=z<=Z1 and y-base<=2]
        small=(wx*wz)<=360 and ridge<=14 and wall in (200,201,204,205,223,224,225)
        if not small: continue
        blds.append(dict(x0=int(X0),z0=int(Z0),x1=int(X1),z1=int(Z1),y=base,eave=eave,ridge=ridge,axis=axis,wall=wall,roof=roof,
                         roofCover=round(cover,2),fill=round(fill,2),doors=doors))
    # заборы: невысокие (1..2) тонкие колонки
    low=(top>=1)&(top<=2)&(cnt>=1)&~ndimage.binary_dilation(lab>0,np.ones((3,3)))
    fl,fn=ndimage.label(low,np.ones((3,3)))
    fences=[]
    for i,sl in enumerate(ndimage.find_objects(fl)):
        msk=fl[sl]==i+1
        if msk.sum()<4: continue
        ys,xs=np.nonzero(msk); zz,xx=sl
        pts=[[int(xx.start+x+x0),int(zz.start+z+z0)] for z,x in zip(ys,xs)]
        # только деревянные заборы (бордюры, трубы, обломки — нет)
        cs=set(map(tuple,pts)); mm=[int(m) for x_,z_,m in zip(v['x'],v['z'],v['m']) if (int(x_),int(z_)) in cs]
        if not mm or sum(1 for m in mm if m in (204,205,206))<0.6*len(mm): continue
        fences.append(dict(cells=pts,h=int(top[sl][msk].max())))
    json.dump(dict(buildings=blds,fences=fences),open(out,'w'))
    print(len(blds),'зданий',len(fences),'заборов')
if __name__=='__main__': run(sys.argv[1],sys.argv[2])
