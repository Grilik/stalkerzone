"""Чтение блоков из мира Minecraft 1.20 (регионы .mca) для превью: blocks(world, x0,x1, y0,y1, z0,z1) -> dict (x,y,z)->name."""
import zlib,struct,os,math
def nbt(b,o,t):
    if t==1: return struct.unpack_from('>b',b,o)[0],o+1
    if t==2: return struct.unpack_from('>h',b,o)[0],o+2
    if t==3: return struct.unpack_from('>i',b,o)[0],o+4
    if t==4: return struct.unpack_from('>q',b,o)[0],o+8
    if t==5: return struct.unpack_from('>f',b,o)[0],o+4
    if t==6: return struct.unpack_from('>d',b,o)[0],o+8
    if t==7:
        n=struct.unpack_from('>i',b,o)[0]; return b[o+4:o+4+n],o+4+n
    if t==8:
        n=struct.unpack_from('>H',b,o)[0]; return b[o+2:o+2+n].decode('utf-8','replace'),o+2+n
    if t==9:
        et=b[o]; n=struct.unpack_from('>i',b,o+1)[0]; o+=5; out=[]
        for _ in range(n): v,o=nbt(b,o,et); out.append(v)
        return out,o
    if t==10:
        d={}
        while True:
            tt=b[o]; o+=1
            if tt==0: return d,o
            n=struct.unpack_from('>H',b,o)[0]; name=b[o+2:o+2+n].decode('utf-8','replace'); o+=2+n
            v,o=nbt(b,o,tt); d[name]=v
    if t==11:
        n=struct.unpack_from('>i',b,o)[0]; return list(struct.unpack_from('>%di'%n,b,o+4)),o+4+4*n
    if t==12:
        n=struct.unpack_from('>i',b,o)[0]; return list(struct.unpack_from('>%dq'%n,b,o+4)),o+4+8*n
def chunk(world,cx,cz):
    p=os.path.join(world,'region','r.%d.%d.mca'%(cx>>5,cz>>5))
    if not os.path.exists(p): return None
    with open(p,'rb') as f:
        f.seek(4*((cx&31)+(cz&31)*32)); e=struct.unpack('>I',f.read(4))[0]
        if e==0: return None
        f.seek((e>>8)*4096); ln,ct=struct.unpack('>IB',f.read(5)); data=f.read(ln-1)
    b=zlib.decompress(data); t=b[0]; n=struct.unpack_from('>H',b,1)[0]
    return nbt(b,3+n,t)[0]
def blocks(world,x0,x1,y0,y1,z0,z1):
    out={}
    for cx in range(x0>>4,(x1>>4)+1):
        for cz in range(z0>>4,(z1>>4)+1):
            c=chunk(world,cx,cz)
            if not c: continue
            for s in c.get('sections',[]):
                sy=s['Y']
                if sy*16>y1 or sy*16+15<y0: continue
                bs=s.get('block_states'); pal=bs['palette']
                if len(pal)==1 and 'data' not in bs:
                    nm=pal[0]['Name']
                    if nm=='minecraft:air': continue
                    idxs=None
                bits=max(4,math.ceil(math.log2(len(pal)))) if len(pal)>1 else 0
                data=bs.get('data')
                per=64//bits if bits else 0
                for i in range(4096):
                    if bits:
                        lng=data[i//per]&0xFFFFFFFFFFFFFFFF; v=(lng>>((i%per)*bits))&((1<<bits)-1)
                    else: v=0
                    p=pal[v]; nm=p['Name']
                    if nm=='minecraft:air': continue
                    x=cx*16+(i&15); z=cz*16+((i>>4)&15); y=sy*16+(i>>8)
                    if x0<=x<=x1 and y0<=y<=y1 and z0<=z<=z1: out[(x,y,z)]=(nm[10:] if nm.startswith('minecraft:') else nm, p.get('Properties',{}))
    return out
