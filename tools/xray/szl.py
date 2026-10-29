import gzip,struct,numpy as np
def load(p):
    d=gzip.open(p).read(); o=4
    x0,z0,w,dd,yoff=struct.unpack_from('<5i',d,o); o+=20; n=w*dd
    g=np.frombuffer(d,'<i2',n,o).reshape(dd,w)/8.0; o+=2*n
    surf=np.frombuffer(d,'u1',n,o).reshape(dd,w); o+=n
    wat=np.frombuffer(d,'<i2',n,o).reshape(dd,w); o+=2*n
    nv=struct.unpack_from('<i',d,o)[0]; o+=4
    v=np.frombuffer(d,np.dtype([('x','<i2'),('y','<i2'),('z','<i2'),('m','u1')]),nv,o).copy(); o+=7*nv
    return dict(x0=x0,z0=z0,w=w,d=dd,g=g,v=v)
