import re,struct,sys,glob,os,json
B=(sys.argv[1] if len(sys.argv)>1 else 'stalker_dump').rstrip('/')+'/'   # папка с извлечёнными level.cform/level.spawn
lv=['l01_escape','l02_garbage','l03_agroprom','l04_darkvalley','l05_bar','l06_rostok','l07_military','l08_yantar','l10_radar','l11_pripyat','l12_stancia']
out={}
for L in lv:
    p=B+('l01/l01_escape_level.spawn' if L=='l01_escape' else f'cut/{L}/level.spawn')
    d=open(p,'rb').read()
    cf=B+('l01/l01_escape_level.cform' if L=='l01_escape' else f'cut/{L}/level.cform')
    c=open(cf,'rb').read(36); bb=struct.unpack_from('<6f',c,12)
    ch=[]
    for m in re.finditer(rb'\x01\x00level_changer\x00([A-Za-z_0-9]*)\x00',d):
        q=m.end()+2; x,y,z=struct.unpack_from('<3f',d,q)
        tail=d[q:q+600]
        dest=re.findall(rb'([lL]\d\d[a-z_0-9]*)\x00',tail)
        ch.append(dict(name=m.group(1).decode(),pos=[round(x),round(y),round(z)],dest=[t.decode() for t in dest][:2]))
    out[L]=dict(bbox=[round(v) for v in bb],changers=ch)
    print(L,[round(v) for v in bb]); [print('   ',c_['name'],c_['pos'],c_['dest']) for c_ in ch]
json.dump(out,open('levels.json','w'),indent=1)
