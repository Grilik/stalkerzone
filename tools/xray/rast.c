// Растеризация level.cform в сетку 1 м: верхняя поверхность, нижняя «земля» (обращённые вверх грани), материал верха.
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <math.h>
#include <float.h>
int main(int c, char**v){
  FILE*f=fopen(v[1],"rb"); fseek(f,0,2); long n=ftell(f); fseek(f,0,0); uint8_t*d=malloc(n); fread(d,1,n,f); fclose(f);
  uint32_t nv=*(uint32_t*)(d+4), nf=*(uint32_t*)(d+8); float*bb=(float*)(d+12);
  float*V=(float*)(d+36); uint32_t*F=(uint32_t*)(d+36+nv*12);
  double cell=atof(v[3]);
  int W=(int)ceil((bb[3]-bb[0])/cell)+1, H=(int)ceil((bb[5]-bb[2])/cell)+1;
  float*top=malloc(4*W*H),*low=malloc(4*W*H),*ter=malloc(4*W*H),*wat=malloc(4*W*H); uint16_t*tmat=calloc(W*H,2); uint16_t*mat=calloc(W*H,2);
  for(int i=0;i<W*H;i++){top[i]=-1e9;low[i]=1e9;ter[i]=-1e9;wat[i]=-1e9;}
  for(uint32_t t=0;t<nf;t++){
    float*a=V+3*F[4*t],*b=V+3*F[4*t+1],*cc=V+3*F[4*t+2]; uint16_t m=F[4*t+3]&0x3fff;
    float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],wx=cc[0]-a[0],wy=cc[1]-a[1],wz=cc[2]-a[2];
    float nx=uy*wz-uz*wy, ny=uz*wx-ux*wz, nz=ux*wy-uy*wx; float nl=sqrtf(nx*nx+ny*ny+nz*nz); if(nl<1e-9) continue;
    float up=fabsf(ny)/nl;
    float minx=fminf(a[0],fminf(b[0],cc[0])),maxx=fmaxf(a[0],fmaxf(b[0],cc[0]));
    float minz=fminf(a[2],fminf(b[2],cc[2])),maxz=fmaxf(a[2],fmaxf(b[2],cc[2]));
    int x0=(int)floor((minx-bb[0])/cell),x1=(int)floor((maxx-bb[0])/cell),z0=(int)floor((minz-bb[2])/cell),z1=(int)floor((maxz-bb[2])/cell);
    for(int zi=z0;zi<=z1;zi++) for(int xi=x0;xi<=x1;xi++){
      if(xi<0||zi<0||xi>=W||zi>=H) continue;
      float px=bb[0]+(xi+0.5)*cell, pz=bb[2]+(zi+0.5)*cell;
      // барицентрика в плоскости XZ
      float d0=(b[2]-cc[2])*(a[0]-cc[0])+(cc[0]-b[0])*(a[2]-cc[2]); 
      float y;
      if(fabsf(d0)<1e-9){ // вертикальная грань: учитываем как стену по краю
        int inside = (px>=minx-cell*0.5 && px<=maxx+cell*0.5 && pz>=minz-cell*0.5 && pz<=maxz+cell*0.5);
        if(!inside) continue; y=fmaxf(a[1],fmaxf(b[1],cc[1]));
      } else {
        float l1=((b[2]-cc[2])*(px-cc[0])+(cc[0]-b[0])*(pz-cc[2]))/d0, l2=((cc[2]-a[2])*(px-cc[0])+(a[0]-cc[0])*(pz-cc[2]))/d0, l3=1-l1-l2;
        float e=-0.02f; if(l1<e||l2<e||l3<e) continue;
        y=l1*a[1]+l2*b[1]+l3*cc[1];
      }
      int k=zi*W+xi;
      if(y>top[k]){top[k]=y;mat[k]=m;}
      if(up>0.5f && ny/nl>0 && y<low[k]) low[k]=y;
      int terr = (m==7||m==10||m==15||m==8||m==16||m==4||m==93);
      if(terr && ny/nl>0.2f && y>ter[k]){ter[k]=y; tmat[k]=m;}
      if(m==11 && y>wat[k]) wat[k]=y;
    }
  }
  FILE*o=fopen(v[2],"wb"); int32_t hdr[2]={W,H}; fwrite(hdr,4,2,o); fwrite(bb,4,6,o); fwrite(top,4,W*H,o); fwrite(low,4,W*H,o); fwrite(mat,2,W*H,o); fwrite(ter,4,W*H,o); fwrite(tmat,2,W*H,o); fwrite(wat,4,W*H,o); fclose(o);
  printf("%d x %d\n",W,H); return 0; }
