import ru.stalkerzone.sim.*;
import ru.stalkerzone.world.ZoneBounds;
import ru.stalkerzone.zonemap.RoadNet;
import ru.stalkerzone.zonemap.RoadNet.Leg;
import java.util.*;
/** v1.14: дороги по графу — связность, границы, ЛЭП и газопровод, участки сходятся в узлах. */
public class RoadNetTest {
  static int f=0; static void ck(boolean ok,String m){System.out.println((ok?"OK   ":"FAIL ")+m); if(!ok)f++;}
  static int[] start(Leg l){ return l.alongX()? new int[]{l.from(),l.fixed()} : new int[]{l.fixed(),l.from()}; }
  static int[] end(Leg l){ return l.alongX()? new int[]{l.to(),l.fixed()} : new int[]{l.fixed(),l.to()}; }
  public static void main(String[] a){
    Simulation s=ZoneScenario.create(0); RoadNet net=new RoadNet(s); ZoneBounds b=ZoneBounds.fromNodes(s.nodes);
    int n=s.nodes.size(); long total=0;
    ck(!net.legs.isEmpty(),"дороги построены: "+net.legs.size()+" участков");
    boolean inside=true; for(Leg l:net.legs){ total+=l.length();
      for(int t=l.lo();t<=l.hi();t+=8){ int x=l.alongX()?t:l.fixed(), z=l.alongX()?l.fixed():t;
        if(!b.inside(x,z)||b.distanceToFence(x,z)<30) inside=false; } }
    ck(inside,"все дороги внутри Зоны, не ближе 30 блоков к забору");
    System.out.println("     общая длина: "+total+" блоков");
    // каждый участок начинается или кончается в центре узла, а пара участков ребра стыкуется в углу
    boolean joined=true;
    Map<String,List<Leg>> byEdge=new HashMap<>();
    for(Leg l:net.legs) byEdge.computeIfAbsent(l.a()+"-"+l.b(),k->new ArrayList<>()).add(l);
    for(List<Leg> e:byEdge.values()){
      Set<String> pts=new HashSet<>(); int ends=0;
      for(Leg l:e){ for(int[] p:new int[][]{start(l),end(l)}){ String k=p[0]+","+p[1]; if(!pts.add(k)) ends++; } }
      if(e.size()==2&&ends!=1) joined=false;
    }
    ck(joined,"Г-образные участки ребра стыкуются");
    // связность по рёбрам дорог
    int[] comp=new int[n]; Arrays.fill(comp,-1); Deque<Integer> q=new ArrayDeque<>(); q.add(0); comp[0]=0;
    while(!q.isEmpty()){int u=q.poll(); for(Leg l:net.legs){ int v=l.a()==u?l.b():l.b()==u?l.a():-1; if(v>=0&&comp[v]<0){comp[v]=0;q.add(v);} }}
    boolean all=true; for(int i=0;i<n;i++) if(comp[i]<0){all=false; System.out.println("     без дороги: "+s.nodes.get(i).name);}
    ck(all,"по дорогам можно доехать до любой из "+n+" локаций");
    long lep=net.legs.stream().filter(Leg::lep).count(), gas=net.legs.stream().filter(Leg::gas).count();
    ck(lep>=2,"ЛЭП от ЧАЭС к Кордону: "+lep+" участков");
    ck(gas>=2,"газопровод у Агропрома: "+gas+" участков");
    ck(net.roadDist(net.centers[0][0],net.centers[0][1])==0,"центр узла лежит на дороге");
    ck(net.nearCenter(net.centers[1][0]+10,net.centers[1][1],RoadNet.CAMP_R),"лагерь узнаётся по радиусу");
    if(f>0){System.out.println("ПРОВАЛОВ: "+f); System.exit(1);} System.out.println("Все проверки пройдены");
  }
}
