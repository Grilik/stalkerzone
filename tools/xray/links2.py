"""Переходы между уровнями: конец дороги — на той стороне прямоугольника уровня, что смотрит на соседа,
напротив места перехода в игре (level_changer). Так дорога идёт через промежуток, а не через сам уровень."""
import json
L=json.load(open('levels.json')); NP=json.load(open('newpairs.json'))
alias={'escape':'l01_escape','garbage':'l02_garbage','agroprom':'l03_agroprom','darkvalley':'l04_darkvalley','bar':'l05_bar','rostok':'l06_rostok','military':'l07_military','yantar':'l08_yantar','radar':'l10_radar','pripyat':'l11_pripyat','stancia':'l12_stancia'}
def mc(k,p): v=L[k]; return (p[0]+v['dx'], -p[1]+v['dz'])
pairs={}
for k,v in L.items():
    for c in v.get('changers',[]):
        t=c['name'].replace('exit_to_','').rsplit('_',1)[0]
        if t in alias: pairs.setdefault(tuple(sorted((k,alias[t]))),{})[k]=mc(k,(c['pos'][0],c['pos'][2]))
for key,v in NP.items():
    a,b=key.split('|'); e=pairs.setdefault((a,b),{})
    for k,p in v.items():
        if L[k].get('anomaly'): e[k]=mc(k,p)
def side(k,o):
    A,B=L[k]['rect'],L[o]['rect']
    gaps={'e':B[0]-A[2],'w':A[0]-B[2],'s':B[1]-A[3],'n':A[1]-B[3]}
    return max(gaps,key=gaps.get)
def end(k,o,p):
    x0,z0,x1,z1=L[k]['rect']; B=L[o]['rect']; s=side(k,o)
    if p is None: p=((B[0]+B[2])/2,(B[1]+B[3])/2)
    cx=min(max(p[0],x0+20),x1-20); cz=min(max(p[1],z0+20),z1-20)
    return {'w':(x0-2,cz),'e':(x1+2,cz),'n':(cx,z0-2),'s':(cx,z1+2)}[s]
links=[]
for (a,b),e in pairs.items():
    pa=end(a,b,e.get(a, e.get(b))); pb=end(b,a,e.get(b, pa))
    links.append({'a':a,'b':b,'ax':round(pa[0]),'az':round(pa[1]),'bx':round(pb[0]),'bz':round(pb[1])})
for l in links: print(l)
lay={'levels':[{'id':k,'rect':v['rect']} for k,v in L.items()],'links':links}
json.dump(lay,open('layout.json','w'),indent=1,ensure_ascii=False)
