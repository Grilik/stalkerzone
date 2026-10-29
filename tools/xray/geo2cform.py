"""Рендер-геометрия уровня X-Ray (level + level.geom) -> псевдо-cform для vox: id «материала» = категория по имени текстуры
(кирпич, штукатурка, бетон, доски, шифер, ржавый металл, стекло, двери, техника...). Деревья — отдельным списком
экземпляров (положение, высота, порода). Использование: geo2cform.py level level.geom level.cform out.cform trees.json"""
import sys,struct,json,re,numpy as np
from lvl import chunks,shaders,vbs,ibs
SKIP=re.compile(r'^(levels\\|effects\\|flora\\|selflight\\)|\\(act_(?!lenin)|corp_|fx_|glow_|wm_|item_|grnd_(?!rock)|terrain_|vine_|water_|unitaz|umivalnik|fake_nosun|trees_forestwall|trees_vetka|trees_sosna|trees_elka|trees_leaf)|(rope|provod|wire|cable|sigaret|kitchen_stuff|book|document|number|map_flag|agit|sheet|blind|lock|switch|priluki|prima)')
RULES=[  # (регексп по текстуре, категория)
 (r'ston_rocks|ston_rock|rock|skala|kamen', 100),
 (r'veh_btr|veh_bmp|veh_tank|t72|veh_uaz|veh_uazik', 215),
 (r'wheel|shina|koleso|tire', 216),
 (r'^veh\\|\\veh_', 214),
 (r'^wind\\|\\wind_|glas|glass|steklo|okno|window', 212),
 (r'act_lenin|statue|pamyat', 203),
 (r'tention|tower|antena|antenna|vishka_m', 211),
 (r'wall_yellow|yellow', 223),
 (r'wall_orange|orange|ohra', 224),
 (r'wall_roses|wall_pink|pink|rose', 225),
 (r'^wall\\|\\wall_', 201),
 (r'roadborder|bordur|stupeni|steps', 202),
 (r'lep\d|_lep|pylon|opora', 211),
 (r'mus_kont|bochenok', 209),
 (r'door|dver|vorota', 213),
 (r'fence|setka|set_iov|resh|grid|rabica|barbwire|mask_setka|gate|zabor_m|perila|railing|lestn|ladder', 211),
 (r'schiffer|shifer|shiffer|roof1|roof2|crete_roof|roof_g', 207),
 (r'roof_red|roof', 208),
 (r'bricks?_?w|silikat|white_br', 201),
 (r'brick|briks|ston_br|redwall|kirpich|stuccobrick', 200),
 (r'stucco|shtukat|kraska|paint_w|walls_g|tualgr|stena_kraska', 201),
 (r'plitka|kafel|tile|floor4', 219),
 (r'linol|pol_smola', 220),
 (r'lampa|lamp|svet|light|galogen', 221),
 (r'trees_spil|spil|stump|bark|log_', 222),
 (r'stolb|post_w|brevno|balka', 206),
 (r'wood_paint|wood_kraska|chipboard|walls7|walls8|walls10|walls11', 205),
 (r'^.*\\wood_|doski|plank|wood|table|chair|shkaf|box|crate|yashik|palitra|jasik|veranda', 204),
 (r'bag|meshok|sack|mattress|matras|bed|cloth|tkan|brezent|tent(?!ion)|palatka', 217),
 (r'rust|rja|ржав|bochka|bo4ka|barrel|konteyner|vagon|trainwheel|railroad|truba|tube|trub', 209),
 (r'beton_lom|old_beton|beton_iov_dirt|old_wall|osnova|bordur|podval|pod_0', 203),
 (r'beton|crete|concrete|ston_|walls|stena|kazarma|potolok|floor|srarkon|plita', 202),
 (r'mtl_|metal|metall|radiator|transformer|electr|pult|pribor|shitok|safe|vanna|vac_|lamppost|dangerbox|governmentbox|doorbox|vishka|engine', 210),
]
RULES=[(re.compile(a),b) for a,b in RULES]
def category(tex):
    t=tex.lower()
    if SKIP.search(t): return None
    for rx,c in RULES:
        if rx.search(t): return c
    return 210 if t.startswith(('def_shaders','default')) else None
TREE=re.compile(r'flora\\|\\trees_')
def main(lp,gp,cfp,outp,treep):
    lv=open(lp,'rb').read(); ge=open(gp,'rb').read(); cfh=open(cfp,'rb').read(36); bb=struct.unpack_from('<6f',cfh,12)
    sh=shaders(lv); V=vbs(ge); I=ibs(ge)
    tris=[]; mats=[]; pts=[]; trees=[]; stat={}
    for cid,o,sz in chunks(lv):
        if cid!=3: continue
        for vid,vo,vsz in chunks(lv,o,o+sz):
            h=g=xf=None
            for c,co,cs in chunks(lv,vo,vo+vsz):
                if c==1: h=struct.unpack_from('<BBH',lv,co)
                elif c==21: g=struct.unpack_from('<6I',lv,co)
                elif c==12: xf=np.array(struct.unpack_from('<16f',lv,co)).reshape(4,4)
            if not (h and g): continue
            typ,sid=h[1],h[2]; tex=sh[sid] if sid<len(sh) else ''
            tname=tex.split('/')[-1].split(',')[0]
            vb,vo_,vsz_,ib,io,isz=g
            idx=I[ib][io:io+isz].astype(np.int64)+vo_; idx=idx[:len(idx)//3*3].reshape(-1,3)
            P=V[vb][idx]
            if xf is not None: P=(P@xf[:3,:3].astype(np.float32)+xf[3,:3].astype(np.float32))
            if TREE.search(tex):
                if xf is not None and 'trunk' in tex.split('/')[0]+tname or (xf is not None and 'bark' in tname):
                    q=P.reshape(-1,3); trees.append((tname,float(xf[3,0]),float(xf[3,2]),float(q[:,1].min()),float(q[:,1].max()),float(np.ptp(q[:,0])+np.ptp(q[:,2]))/4))
                elif xf is not None and 'leaf' in tex: trees.append((tname,float(xf[3,0]),float(xf[3,2]),None,float(P.reshape(-1,3)[:,1].max()),float(np.ptp(P.reshape(-1,3)[:,0])+np.ptp(P.reshape(-1,3)[:,2]))/4))
                continue
            cat=category(tname if '\\' in tname else tex)
            stat[tname]=(cat,stat.get(tname,(0,0))[1]+len(P))
            if cat is None: continue
            c_=P.mean(1); ok=(c_[:,0]>=bb[0])&(c_[:,0]<=bb[3])&(c_[:,2]>=bb[2])&(c_[:,2]<=bb[5])
            P=P[ok]
            if len(P):
                # мелкие грани (меньше вокселя) — сразу в воксель по центру: экземпляры техники и мебели дают десятки млн граней
                e=np.maximum(np.abs(P[:,0]-P[:,1]).max(1),np.abs(P[:,0]-P[:,2]).max(1))
                big=e>0.8
                if big.any(): tris.append(P[big].astype(np.float32)); mats.append(np.full(int(big.sum()),cat,np.uint32))
                if (~big).any():
                    k=np.floor(P[~big].mean(1)).astype(np.int32)
                    pts.append(np.unique(np.c_[k,np.full(len(k),cat,np.int32)],axis=0))
                    if len(pts)>200: pts[:]=[np.unique(np.concatenate(pts),axis=0)]
    # скалы из коллизии (remap.py: id 100) — у части уровней обрывы нарисованы рельефом, в рендере их нет
    cf=open(cfp,'rb').read(); nv,nf0=struct.unpack_from('<2I',cf,4)
    CV=np.frombuffer(cf,'<f4',nv*3,36).reshape(nv,3); CF=np.frombuffer(cf,'<u4',nf0*4,36+12*nv).reshape(nf0,4)
    rk=(CF[:,3]&0x3fff)==100
    if rk.any(): tris.append(CV[CF[rk,:3].astype(np.int64)]); mats.append(np.full(int(rk.sum()),100,np.uint32))
    del cf
    if pts:   # точки -> вырожденные грани в центре вокселя (vox ставит их в свой воксель)
        U=np.unique(np.concatenate(pts),axis=0); C=U[:,:3].astype(np.float32)+0.5
        tris.append(np.repeat(C[:,None,:],3,axis=1)); mats.append(U[:,3].astype(np.uint32))
    T=np.concatenate(tris); tris.clear(); M=np.concatenate(mats); nf=len(T)
    Vt=T.reshape(-1,3); F=np.zeros((nf,4),np.uint32); F[:,0]=np.arange(nf)*3; F[:,1]=F[:,0]+1; F[:,2]=F[:,0]+2; F[:,3]=M
    with open(outp,'wb') as o:
        o.write(struct.pack('<3I',4,len(Vt),nf)); o.write(struct.pack('<6f',*bb)); o.write(Vt.astype('<f4').tobytes()); o.write(F.tobytes())
    json.dump(trees,open(treep,'w'))
    from collections import Counter
    cc=Counter(); [cc.update({v[0]:v[1]}) for v in stat.values()]
    print('треугольников',nf,'деревьев',len(trees),'по категориям',dict(sorted(cc.items(),key=lambda x:-x[1])))
    unk=sorted(((v[1],k) for k,v in stat.items() if v[0]==210),reverse=True)[:25]; print('металл по умолчанию:',unk)
if __name__=='__main__': main(*sys.argv[1:6])
