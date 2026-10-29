import sys; import os; sys.path.insert(0,os.path.dirname(os.path.abspath(__file__))); import mca
from PIL import Image, ImageDraw
def color(n):
    T=[('grass_block',(96,128,58)),('dirt',(120,85,58)),('coarse',(110,80,55)),('spruce_planks',(115,85,52)),('spruce_door',(110,80,50)),('stripped_spruce',(150,115,75)),('spruce_fence',(100,75,45)),('spruce_log',(80,60,40)),('dark_oak_stairs',(66,43,20)),('dark_oak_slab',(66,43,20)),('dark_oak_fence',(66,43,20)),('dark_oak',(66,43,20)),('stripped_dark_oak',(90,70,45)),
       ('andesite',(135,135,135)),('stone_bricks',(122,122,122)),('cobble',(110,110,110)),('stone',(125,125,125)),('bricks',(150,90,75)),('brick',(150,90,75)),('calcite',(220,220,215)),('diorite',(200,200,200)),('glass',(170,210,230)),('granite',(150,105,85)),
       ('leaves',(60,95,40)),('birch_log',(215,215,205)),('oak_log',(100,80,50)),('_log',(90,70,45)),('terracotta',(150,95,70)),('concrete',(170,170,170)),('gravel',(130,125,120)),('water',(60,90,170)),('mud',(70,60,55)),('podzol',(90,65,40)),('lantern',(250,220,120)),('wool',(130,100,70)),('iron',(200,200,200))]
    for k,c in T:
        if k in n: return c
    return (255,0,255)
def render(world,x0,x1,y0,y1,z0,z1,out,S=4):
    B=mca.blocks(world,x0,x1,y0,y1,z0,z1)
    X=x1-x0+1; Z=z1-z0+1; H=y1-y0+1
    W=(X+Z)*2*S+8; Hh=(X+Z)*S+H*2*S+8
    img=Image.new('RGB',(W,Hh),(30,32,40)); d=ImageDraw.Draw(img)
    items=sorted(B.items(),key=lambda kv:((kv[0][0]-x0)+(kv[0][2]-z0), kv[0][1]))
    for (x,y,z),(n,props) in items:
        if 'short_grass' in n or n in ('grass','tall_grass','fern'): continue
        c=color(n); xs=x-x0; zs=z-z0
        sx=(xs-zs+Z)*2*S; sy=(xs+zs)*S+(y1-y)*2*S
        half = ('slab' in n and props.get('type')=='bottom') or 'stairs' in n
        thin = 'fence' in n or 'pane' in n or 'door' in n
        top=[(sx+2*S,sy),(sx+4*S,sy+S),(sx+2*S,sy+2*S),(sx,sy+S)]
        off=S if half else 0
        if thin: c=tuple(int(v*0.9) for v in c)
        top=[(a,b+off) for a,b in top]
        d.polygon(top,fill=c)
        d.polygon([(sx,sy+S+off),(sx+2*S,sy+2*S+off),(sx+2*S,sy+4*S),(sx,sy+3*S)],fill=tuple(int(v*0.72) for v in c))
        d.polygon([(sx+2*S,sy+2*S+off),(sx+4*S,sy+S+off),(sx+4*S,sy+3*S),(sx+2*S,sy+4*S)],fill=tuple(int(v*0.5) for v in c))
    img.save(out); print(img.size,len(B))
if __name__=='__main__':
    a=sys.argv; render(a[1],*map(int,a[2:8]),a[8])
