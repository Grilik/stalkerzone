import ru.stalkerzone.sim.*;
import ru.stalkerzone.world.ZoneBounds;
import ru.stalkerzone.world.ZoneBounds.Kind;
public class BoundsTest {
  static int f=0; static void ck(boolean ok,String m){System.out.println((ok?"OK   ":"FAIL ")+m); if(!ok)f++;}
  public static void main(String[] a){
    Simulation s=ZoneScenario.create(0); ZoneBounds b=ZoneBounds.fromNodes(s.nodes);
    System.out.println(b);
    for(Node n:s.nodes) ck(b.inside(n.x*10,n.z*10)&&b.distanceToFence(n.x*10,n.z*10)>=ZoneBounds.MARGIN,"узел "+n.name+" внутри с запасом");
    // замкнутость: любая точка вне внутренней области, ближайшая к ней по пути наружу, проходит через FENCE и BARRIER
    boolean sealed=true;
    for(int z=b.minZ-5;z<=b.maxZ+5&&sealed;z+=1){
      for(int dir=-1;dir<=1;dir+=2){
        int x=dir<0?b.minX:b.maxX; boolean seenF=false,seenB1=false,seenB2=false;
        for(int i=0;i<=ZoneBounds.BARRIER_THICKNESS;i++){ Kind k=b.kind(x+dir*i,z);
          if(z>=b.minZ&&z<=b.maxZ){ if(i==0&&k!=Kind.FENCE)sealed=false; if(i>0&&k!=Kind.BARRIER)sealed=false;} }
      }}
    ck(sealed,"боковые стороны: забор + барьер без дыр");
    boolean corners=true;
    for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
      int x=b.minX+dx,z=b.minZ+dz; Kind k=b.kind(x,z);
      boolean in=b.inside(x,z); int out=Math.max(Math.max(b.minX-x,x-b.maxX),Math.max(b.minZ-z,z-b.maxZ));
      if(!in&&out<=2&&k!=Kind.BARRIER)corners=false;
      if(!in&&out>2&&k!=Kind.NONE)corners=false;}
    ck(corners,"углы: барьер на 2 слоя, дальше пусто");
    // touchesChunk согласован с kind
    boolean consist=true;
    for(int cx=(b.minX>>4)-3;cx<=(b.maxX>>4)+3;cx++)for(int cz=(b.minZ>>4)-3;cz<=(b.maxZ>>4)+3;cz++){
      boolean any=false; for(int x=0;x<16;x++)for(int z=0;z<16;z++) if(b.kind((cx<<4)+x,(cz<<4)+z)!=Kind.NONE)any=true;
      if(any!=b.touchesChunk(cx,cz))consist=false;}
    ck(consist,"touchesChunk совпадает с kind для всех чанков");
    ck(b.inside(b.clampX(-99999),b.clampZ(99999)),"clamp возвращает внутрь");
    System.out.println(f==0?"ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ":"ПРОВАЛЕНО "+f); System.exit(f);
  }}
