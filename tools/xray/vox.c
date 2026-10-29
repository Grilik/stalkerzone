// Поверхностная вокселизация level.cform: шаг 1 м. Выход: для каждого вокселя (x,y,z) id материала (u16), 0 — пусто.
// Плюс карта высоты рельефа (материалы-грунты передаются списком) — под ней всё заполняется.
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
int main(int c,char**v){
  FILE*f=fopen(v[1],"rb"); fseek(f,0,2); long n=ftell(f); fseek(f,0,0); uint8_t*d=malloc(n); if(fread(d,1,n,f)!=n) return 1; fclose(f);
  uint32_t nv=*(uint32_t*)(d+4), nf=*(uint32_t*)(d+8); float*bb=(float*)(d+12);
  float*V=(float*)(d+36); uint32_t*F=(uint32_t*)(d+36+nv*12);
  int W=(int)ceil(bb[3]-bb[0])+1, H=(int)ceil(bb[4]-bb[1])+1, D=(int)ceil(bb[5]-bb[2])+1;
  size_t N=(size_t)W*H*D; uint16_t*g=calloc(N,2);
  for(uint32_t t=0;t<nf;t++){
    float*a=V+3*F[4*t],*b=V+3*F[4*t+1],*q=V+3*F[4*t+2]; uint16_t m=(F[4*t+3]&0x3fff)+1;
    { float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],wx=q[0]-a[0],wy=q[1]-a[1],wz=q[2]-a[2];
      float nx=uy*wz-uz*wy, ny=uz*wx-ux*wz, nz=ux*wy-uy*wx, nl=sqrtf(nx*nx+ny*ny+nz*nz);
      if(nl>1e-9){ float c=ny/nl; if(c>0.4f) m|=0x4000; else if(c<-0.4f) m|=0x8000; } }
    float la=sqrtf((b[0]-a[0])*(b[0]-a[0])+(b[1]-a[1])*(b[1]-a[1])+(b[2]-a[2])*(b[2]-a[2]));
    float lb=sqrtf((q[0]-a[0])*(q[0]-a[0])+(q[1]-a[1])*(q[1]-a[1])+(q[2]-a[2])*(q[2]-a[2]));
    int s=(int)ceil(fmaxf(la,lb)/0.35f)+1;
    for(int i=0;i<=s;i++) for(int j=0;j<=s-i;j++){
      float u=(float)i/s, w=(float)j/s;
      float x=a[0]+u*(b[0]-a[0])+w*(q[0]-a[0]), y=a[1]+u*(b[1]-a[1])+w*(q[1]-a[1]), z=a[2]+u*(b[2]-a[2])+w*(q[2]-a[2]);
      int xi=(int)floorf(x-bb[0]), yi=(int)floorf(y-bb[1]), zi=(int)floorf(z-bb[2]);
      if(xi<0||yi<0||zi<0||xi>=W||yi>=H||zi>=D) continue;
      size_t k=((size_t)zi*W+xi)*H+yi; if(!g[k]) g[k]=m; else g[k]|=(m&0xC000);
    }
  }
  FILE*o=fopen(v[2],"wb"); int32_t hd[3]={W,H,D}; fwrite(hd,4,3,o); fwrite(bb,4,6,o); fwrite(g,2,N,o); fclose(o);
  size_t cnt=0; for(size_t i=0;i<N;i++) if(g[i]) cnt++; printf("%d %d %d, %zu voxels\n",W,H,D,cnt); return 0; }
