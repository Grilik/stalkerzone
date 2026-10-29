import sys,zipfile,io; import os; sys.path.insert(0,os.path.dirname(os.path.abspath(__file__))); import mca, render
from PIL import Image
pack=zipfile.ZipFile(os.environ.get('SZ_PACK','original_textures.zip'))
TEX={}
def tex(n):
    if n in TEX: return TEX[n]
    t=None
    if n.startswith('stalkerzone:zm_'):
        k=n[len('stalkerzone:'):]
        for suf in ('_stairs','_slab','_fence','_trapdoor','_pane','_door'):
            if k.endswith(suf): k=k[:-len(suf)]; break
        try: t=Image.open(io.BytesIO(pack.read(f'assets/stalkerzone/textures/block/{k}.png'))).convert('RGBA').resize((16,16))
        except KeyError: t=None
    if t is None:
        c=render.color(n); t=Image.new('RGBA',(16,16),c+(255,))
    TEX[n]=t; return t
def shade(im,k):
    r,g,b,a=im.split(); return Image.merge('RGBA',[x.point(lambda v:int(v*k)) for x in (r,g,b)]+[a])
def go(world,x0,x1,y0,y1,z0,z1,out,S=6):
    B=mca.blocks(world,x0,x1,y0,y1,z0,z1)
    X=x1-x0+1; Z=z1-z0+1; H=y1-y0+1
    W=(X+Z)*2*S+8; Hh=(X+Z)*S+H*2*S+8
    img=Image.new('RGBA',(W,Hh),(30,32,40,255))
    cache={}
    def face(n,kind):
        key=(n,kind)
        if key in cache: return cache[key]
        t=tex(n); w=4*S
        if kind=='top':
            # ромб: исходный квадрат на ромб
            f=t.transform((w,2*S),Image.QUAD,(0,0, 0,16, 16,16, 16,0),Image.NEAREST)  # заглушка
            f=t.rotate(45,expand=True).resize((w,2*S),Image.NEAREST)
        elif kind=='left':
            f=shade(t.resize((2*S,2*S)),0.75).transform((2*S,3*S),Image.AFFINE,(1,0,0,-0.5,1,0),Image.NEAREST)
        else:
            f=shade(t.resize((2*S,2*S)),0.55).transform((2*S,3*S),Image.AFFINE,(1,0,0,0.5,1,-S),Image.NEAREST)
        cache[key]=f; return f
    items=sorted(B.items(),key=lambda kv:((kv[0][0]-x0)+(kv[0][2]-z0), kv[0][1]))
    for (x,y,z),(n,props) in items:
        if 'short_grass' in n or n in ('grass','tall_grass','fern'): continue
        nn=n if ':' in n else 'minecraft:'+n
        xs=x-x0; zs=z-z0; sx=(xs-zs+Z)*2*S; sy=(xs+zs)*S+(y1-y)*2*S
        img.alpha_composite(face(nn if nn.startswith('stalkerzone') else n,'left'),(sx,sy+S))
        img.alpha_composite(face(nn if nn.startswith('stalkerzone') else n,'right'),(sx+2*S,sy+S))
        img.alpha_composite(face(nn if nn.startswith('stalkerzone') else n,'top'),(sx,sy))
    img.convert('RGB').save(out); print(img.size)
if __name__=='__main__':
    a=sys.argv; go(a[1],*map(int,a[2:8]),a[8])
