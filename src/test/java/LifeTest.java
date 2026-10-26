import ru.stalkerzone.sim.*;
import java.io.*;
import java.util.*;
/** v1.14: живая Зона — лента КПК, тайники погибших (через учёт), слухи, сохранение. */
public class LifeTest {
  static int f=0; static void ck(boolean ok,String m){System.out.println((ok?"OK   ":"FAIL ")+m); if(!ok)f++;}
  public static void main(String[] x) throws Exception {
    Simulation s=ZoneScenario.create(4); ZoneLife life=new ZoneLife(); Random r=new Random(9);
    int msgs=0, deaths=0, sem=0;
    for(int d=0;d<300;d++){ s.step(); for(ZoneLife.Msg m:life.daily(s,r)){ msgs++; if(m.text.startsWith("Погиб")) deaths++; if(m.text.contains("Семецк")) sem++; }
      if(!s.audit().isEmpty()){ System.out.println(s.audit()); break; } }
    System.out.println("     сообщений "+msgs+", гибелей "+deaths+", Семецкий "+sem+", тайников "+life.stashes.size()+", слухов "+life.rumors.size());
    ck(s.audit().isEmpty(),"аудит сходится: хабар тайников снят со складов через учёт");
    ck(msgs>200,"лента живая: больше 200 сообщений за 300 дней");
    ck(deaths>10,"в ленте есть гибели сталкеров");
    ck(sem>0,"Семецкий погибает (как положено)");
    ck(!life.stashes.isEmpty() && life.stashes.size()<=ZoneLife.MAX_STASHES,"тайники погибших есть и не копятся сверх меры");
    boolean loot=true; for(ZoneLife.Stash st:life.stashes){ int sum=0; for(int v:st.loot) sum+=v; if(sum==0) loot=false; }
    ck(loot,"в каждом тайнике есть хабар");
    long t=life.rumors.stream().filter(q->q.truth).count(), l=life.rumors.size()-t;
    ck(t>0 && l>0,"слухи и правдивые ("+t+"), и враньё ("+l+")");
    ZoneLife.Rumor tr=life.rumors.stream().filter(q->q.truth).findFirst().orElse(null);
    ZoneLife.Stash st=life.hear(tr);
    ck(st!=null && st.known && Math.hypot(st.x-tr.x, st.z-tr.z)<=22,"правдивый слух открывает тайник, место указано с точностью до ~20 м");
    ZoneLife.Rumor lie=life.rumors.stream().filter(q->!q.truth).findFirst().orElse(null);
    ck(life.hear(lie)==null,"враньё ничего не открывает");
    ByteArrayOutputStream bo=new ByteArrayOutputStream(); life.write(new DataOutputStream(bo));
    ZoneLife back=ZoneLife.read(new DataInputStream(new ByteArrayInputStream(bo.toByteArray())));
    ByteArrayOutputStream bo2=new ByteArrayOutputStream(); back.write(new DataOutputStream(bo2));
    ck(Arrays.equals(bo.toByteArray(),bo2.toByteArray()),"сохранение и загрузка без потерь ("+bo.size()+" байт)");
    for(int i=0;i<3;i++) System.out.println("     пример: "+life.feed.get(life.feed.size()-1-i).text);
    if(f>0){System.out.println("ПРОВАЛОВ: "+f); System.exit(1);} System.out.println("Все проверки пройдены");
  }
}
