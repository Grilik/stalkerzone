all: xdb rast vox
LzHuf.cpp:
	curl -sfL https://raw.githubusercontent.com/OpenXRay/xray-16/dev/src/xrCore/LzHuf.cpp -o LzHuf.cpp
lzhuf.cpp: LzHuf.cpp
	python3 -c "s=open('LzHuf.cpp').read().replace('#include \"stdafx.h\"','').replace('#pragma hdrstop','').replace('IC ','inline '); c=s.index('size_t _writeLZ'); s=s[:c]+'bool _decompressLZ(u8**d,size_t*ds,void*src,size_t ss,size_t t){fs.Init_Input((u8*)src,(u8*)src+ss);if(!Decode((int)t))return false;*d=fs.OutPointer();*ds=fs.OutSize();return true;}\n'; h='#include <cstdio>\n#include <cstdlib>\n#include <cstring>\n#include <cstdint>\ntypedef uint8_t u8;typedef uint16_t u16;typedef uint32_t u32;\ntemplate<class T>T*xr_alloc(size_t n){return(T*)malloc(n*sizeof(T));}\ntemplate<class T>T*xr_realloc(T*p,size_t n){return(T*)realloc(p,n);}\n#define xr_free(p) free(p)\n#define xr_malloc(n) malloc(n)\n#define VERIFY(x)\n#define R_ASSERT(x)\n#define R_ASSERT2(x,y)\n'; open('lzhuf.cpp','w').write(h+s)"
xdb: xdb.cpp lzhuf.cpp lzo.c
	gcc -O2 -c lzo.c -o lzo.o && g++ -O2 -w xdb.cpp lzhuf.cpp lzo.o -o xdb
rast: rast.c
	gcc -O2 rast.c -o rast -lm
vox: vox.c
	gcc -O2 vox.c -o vox -lm
