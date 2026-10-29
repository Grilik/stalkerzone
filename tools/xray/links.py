import json
L=json.load(open('levels.json'))
alias={'escape':'l01_escape','garbage':'l02_garbage','agroprom':'l03_agroprom','darkvalley':'l04_darkvalley','bar':'l05_bar','rostok':'l06_rostok','military':'l07_military','yantar':'l08_yantar','radar':'l10_radar','pripyat':'l11_pripyat','stancia':'l12_stancia'}
def mc(k,p): v=L[k]; return (p[0]+v['dx'], -p[2]+v['dz'])
def edge_out(k,x,z):
    x0,z0,x1,z1=L[k]['rect']
    d={'w':x-x0,'e':x1-x,'n':z-z0,'s':z1-z}; s=min(d,key=d.get)
    return {'w':(x0-2,z),'e':(x1+2,z),'n':(x,z0-2),'s':(x,z1+2)}[s]
def edge_toward(k,tx,tz):
    x0,z0,x1,z1=L[k]['rect']
    cx,cz=min(max(tx,x0),x1),min(max(tz,z0),z1)
    return edge_out(k,cx,cz)
ends={}
for k,v in L.items():
    for c in v['changers']:
        t=c['name'].replace('exit_to_','').rsplit('_',1)[0]
        if t not in alias: continue
        x,z=mc(k,c['pos']); ends.setdefault(tuple(sorted((k,alias[t]))),{})[k]=edge_out(k,x,z)
links=[]
for (a,b),e in ends.items():
    if a not in e: e[a]=edge_toward(a,*e[b])
    if b not in e: e[b]=edge_toward(b,*e[a])
    links.append({'a':a,'b':b,'ax':round(e[a][0]),'az':round(e[a][1]),'bx':round(e[b][0]),'bz':round(e[b][1])})
for l in links: print(l)
lay={'levels':[{'id':k,'rect':v['rect']} for k,v in L.items()],'links':links}
json.dump(lay,open('out/layout.json','w'),indent=1,ensure_ascii=False)
