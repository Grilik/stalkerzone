import re,struct,sys
def mats(p):
    d=open(p,'rb').read(); out={}
    for m in re.finditer(rb'(materials\\[a-z0-9_\\]+)\x00',d):
        i=struct.unpack_from('<I',d,m.start()-4)[0]
        if i<2000: out[i]=m.group(1).decode()
    return out
if __name__=='__main__':
    a=mats(sys.argv[1]); b=mats(sys.argv[2])
    print(len(a),len(b))
    rb_={v:k for k,v in b.items()}
    for k in sorted(a): print(k,a[k],'->',rb_.get(a[k]))
