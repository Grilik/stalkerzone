"""Разбор X-Ray `level` + `level.geom`: шейдеры/текстуры, визуалы (OGF), треугольники по текстуре."""
import struct,sys,numpy as np
def chunks(d,o=0,end=None):
    end=len(d) if end is None else end
    while o+8<=end:
        cid,sz=struct.unpack_from('<II',d,o); yield cid&0x7fffffff,o+8,sz; o+=8+sz
def shaders(level):
    for cid,o,sz in chunks(level):
        if cid==2:
            n=struct.unpack_from('<I',level,o)[0]; p=o+4; out=[]
            for i in range(n):
                e=level.index(b'\0',p); out.append(level[p:e].decode('cp1251','replace')); p=e+1
            return out
DT={0:4,1:8,2:12,3:16,4:4,5:4,6:4,7:8,8:4,9:4,10:8,11:4,12:8,13:4,14:4,15:4,16:8}
def vbs(geom):
    out=[]
    for cid,o,sz in chunks(geom):
        if cid==9:
            n=struct.unpack_from('<I',geom,o)[0]; p=o+4
            for i in range(n):
                vs=0; pos=None
                while True:
                    st,off,typ,meth,use,ui=struct.unpack_from('<HHBBBB',geom,p); p+=8
                    if st==0xff: break
                    vs=max(vs,off+DT.get(typ,4))
                    if use==0 and pos is None: pos=off
                cnt=struct.unpack_from('<I',geom,p)[0]; p+=4
                a=np.frombuffer(geom,np.uint8,cnt*vs,p).reshape(cnt,vs); p+=cnt*vs
                out.append(np.ascontiguousarray(a[:,pos:pos+12]).view('<f4').reshape(cnt,3))
    return out
def ibs(geom):
    out=[]
    for cid,o,sz in chunks(geom):
        if cid==10:
            n=struct.unpack_from('<I',geom,o)[0]; p=o+4
            for i in range(n):
                cnt=struct.unpack_from('<I',geom,p)[0]; p+=4
                out.append(np.frombuffer(geom,'<u2',cnt,p)); p+=cnt*2
    return out
def visuals(level):
    for cid,o,sz in chunks(level):
        if cid==3:
            for vid,vo,vsz in chunks(level,o,o+sz):
                h=None;g=None
                for c,co,cs in chunks(level,vo,vo+vsz):
                    if c==1: h=struct.unpack_from('<BBH',level,co)
                    elif c==21: g=struct.unpack_from('<6I',level,co)
                if h and g: yield h,g
def triangles(level,geom):
    sh=shaders(level); V=vbs(geom); I=ibs(geom)
    for (ver,typ,sid),(vb,vo,vsz,ib,io,isz) in visuals(level):
        if typ in (12,14,6): continue          # деревья, детали
        idx=I[ib][io:io+isz].astype(np.int64)+vo
        idx=idx[:len(idx)//3*3].reshape(-1,3)
        yield sh[sid] if sid<len(sh) else '?', V[vb][idx]
if __name__=='__main__':
    lv=open(sys.argv[1],'rb').read(); ge=open(sys.argv[2],'rb').read()
    from collections import Counter
    c=Counter(); types=Counter()
    for (ver,typ,sid),g in visuals(lv): types[typ]+=1
    print('типы визуалов',dict(types))
    for name,t in triangles(lv,ge): c[name]+=len(t)
    for k,v in c.most_common(400): print(v,k)
