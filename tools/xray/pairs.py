"""Пары переходов (level_changer) из all.spawn Anomaly для новых уровней → newpairs.json."""
import re,struct,json,sys
d=open(sys.argv[1],'rb').read()
pref={'mar':'k00_marsh','ds':'k01_darkscape','tc':'k02_trucks_cemetery','cit':'l09_deadcity','lim':'l10_limansk','red':'l10_red_forest','katacomb':'l11_hospital',
      'esc':'l01_escape','val':'l04_darkvalley','bar':'l05_bar','mil':'l07_military','yan':'l08_yantar','rad':'l10_radar','agr':'l03_agroprom','aes2':'l12_stancia'}
dest={'marsh':'k00_marsh','darkscape':'k01_darkscape','tc':'k02_trucks_cemetery','dead_city':'l09_deadcity','limansk':'l10_limansk','red_forest':'l10_red_forest','red':'l10_red_forest','hospital':'l11_hospital',
      'escape':'l01_escape','darkvalley':'l04_darkvalley','bar':'l05_bar','military':'l07_military','yantar':'l08_yantar','radar':'l10_radar','agroprom':'l03_agroprom','aes':'l12_stancia'}
new=set(list(pref.values())[:7])
pairs={}
for m in re.finditer(rb'\x01\x00level_changer\x00([A-Za-z_0-9]*)\x00',d):
    n=m.group(1).decode(); x,y,z=struct.unpack_from('<3f',d,m.end()+2)
    p=n.split('_')[0]; t=re.search(r'to_([a-z_]+?)(_\d+)?$',n)
    if p not in pref or not t or t.group(1) not in dest: continue
    a,b=pref[p],dest[t.group(1)]
    if a in new or b in new: pairs.setdefault('|'.join(sorted((a,b))),{}).setdefault(a,[x,z])
json.dump(pairs,open('newpairs.json','w'),indent=1)
print(len(pairs),'пар')
