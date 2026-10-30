import ru.stalkerzone.sim.*;
import java.util.*;
public class LairTest {
  public static void main(String[] a){
    int fail=0;
    for(long seed=1;seed<=6;seed++){
      Simulation s=ZoneScenario.create(seed);
      int minL=99,maxL=0,minMem=999; long overrun=0,raids=0,spawned=0;
      for(int d=0;d<1440;d++){
        s.step();
        if(!s.audit().isEmpty()){System.out.println("AUDIT FAIL seed "+seed+" day "+s.day+"\n"+s.audit());System.exit(1);}
        minL=Math.min(minL,s.lairs.size());maxL=Math.max(maxL,s.lairs.size());
        for(Faction f:s.factions) if(!f.isService()) minMem=Math.min(minMem,f.members);
      }
      long raid=s.events.stream().filter(e->e.contains("напало на")).count();
      long take=s.events.stream().filter(e->e.contains("Мутанты захватили")).count();
      long base=s.events.stream().filter(e->e.contains("прорвались на базу")).count();
      System.out.printf("seed %d: логов %d..%d, уничтожено %d, новых %d | в журнале (последние %d событий): налётов %d, захватов %d, баз %d | мин. людей %d%n",seed,minL,maxL,s.lairsDestroyed,s.lairsSpawned,s.events.size(),raid,take,base,minMem);
      StringBuilder sb=new StringBuilder("   ");
      for(Faction f:s.factions)sb.append(f.name+" "+f.members+", ");
      System.out.println(sb);
      if(minMem<1)fail++;
    }
    // save/load
    Simulation s1=ZoneScenario.create(5); s1.step(300);
    Simulation s2=Simulation.load(s1.save());
    s1.step(200);s2.step(200);
    boolean same=s1.save().equals(s2.save());
    System.out.println((same?"OK   ":"FAIL ")+"сохранение с логовами идентично");
    if(!same)fail++;
    Simulation s=ZoneScenario.create(2); s.step(400);
    System.out.println(Report.lairs(s));
    for(String e:s.events) if(e.contains("логов")||e.contains("Мутанты")||e.contains("мутант")||e.contains("Логово")) System.out.println(e);
    System.exit(fail);
  }}
