// Распаковщик архивов X-Ray .db (формат 2947/EE): список файлов и извлечение по маске.
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <string>
#include <vector>
#include <sys/stat.h>
typedef uint8_t u8; typedef uint32_t u32;
bool _decompressLZ(u8** dest, size_t* dest_sz, void* src, size_t src_sz, size_t total_size);
extern "C" int lzo1x_dec(const uint8_t *in, size_t in_len, uint8_t *out, size_t *out_len);
static void mkdirs(std::string p){ for(size_t i=1;i<p.size();i++) if(p[i]=='/'){ std::string q=p.substr(0,i); mkdir(q.c_str(),0755);} }
int main(int argc,char**argv){
  if(argc<3){fprintf(stderr,"xdb <file.db> list|extract <outdir> [substr]\n");return 1;}
  FILE*f=fopen(argv[1],"rb"); if(!f){perror("open");return 1;}
  fseeko(f,0,SEEK_END); off_t total=ftello(f); fseeko(f,0,SEEK_SET);
  std::vector<u8> table;
  while(ftello(f)<total){ u32 id,sz; if(fread(&id,4,1,f)!=1||fread(&sz,4,1,f)!=1)break;
    if((id&0x7fffffff)==1){ std::vector<u8> raw(sz); fread(raw.data(),1,sz,f);
      if(id&0x80000000){ u8*d; size_t ds; if(!_decompressLZ(&d,&ds,raw.data(),sz,(size_t)-1)){fprintf(stderr,"lzhuf fail\n");return 2;} table.assign(d,d+ds);} else table=raw; break; }
    fseeko(f,sz,SEEK_CUR); }
  size_t p=0; bool list=!strcmp(argv[2],"list"); const char*out=list?0:argv[3]; const char*mask=argc>(list?3:4)?argv[list?3:4]:"";
  int n=0;
  while(p+18<=table.size()){
    uint16_t hs; memcpy(&hs,&table[p],2); u32 real,comp,crc,ptr; memcpy(&real,&table[p+2],4); memcpy(&comp,&table[p+6],4); memcpy(&crc,&table[p+10],4);
    size_t nl=hs-16; std::string name((char*)&table[p+14],nl); memcpy(&ptr,&table[p+14+nl],4); p+=14+nl+4;
    for(auto&c:name) if(c=='\\') c='/';
    if(*mask && name.find(mask)==std::string::npos) continue;
    if(list){ printf("%10u %10u %10u %s\n",real,comp,ptr,name.c_str()); n++; continue; }
    if(real==0 && comp==0){ continue; }
    std::vector<u8> c(comp); fseeko(f,ptr,SEEK_SET); fread(c.data(),1,comp,f);
    std::vector<u8> o(real);
    if(real!=comp){ size_t ol=real; int r=lzo1x_dec(c.data(),comp,o.data(),&ol); if(r||ol!=real){fprintf(stderr,"lzo fail %d %s\n",r,name.c_str()); continue;} }
    else o=c;
    std::string path=std::string(out)+"/"+name; mkdirs(path); FILE*w=fopen(path.c_str(),"wb"); fwrite(o.data(),1,o.size(),w); fclose(w); n++;
  }
  fprintf(stderr,"%d files\n",n); return 0; }
