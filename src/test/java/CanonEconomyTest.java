import ru.stalkerzone.sim.*;
/** v1.14: экономика по канону — касса торговцев течёт наружу (крыша, взятки, вывоз), скупщик берёт артефакты дёшево. */
public class CanonEconomyTest {
  static int f=0; static void ck(boolean ok,String m){System.out.println((ok?"OK   ":"FAIL ")+m); if(!ok)f++;}
  public static void main(String[] x){
    long maxTrader=0, sumFaction=0; int samples=0; boolean audit=true; long dues=0, bribes=0, export=0;
    for(long seed=1;seed<=4;seed++){
      Simulation s=ZoneScenario.create(seed);
      for(int d=0;d<1440;d++){ s.step(); if(!s.audit().isEmpty()){ audit=false; System.out.println(s.audit()); break; }
        if(d>=360 && d%60==0){ for(Node n:s.nodes) if(n.trader) maxTrader=Math.max(maxTrader,n.market.money);
          for(Faction fa:s.factions) if(!fa.fanatic) sumFaction+=fa.stock.money; samples++; } }
      dues+=s.traderDues; bribes+=s.traderBribes; export+=s.traderExport;
      Node hub=null; for(Node n:s.nodes) if(n.trader){hub=n;break;}
      if(seed==1) System.out.printf("     артефакт у %s: скупка %d, продажа %d руб.%n", hub.name, s.buyPrice(hub,Good.ARTIFACT), s.sellPrice(hub,Good.ARTIFACT));
      if(seed==1) ck(s.sellPrice(hub,Good.ARTIFACT) >= s.buyPrice(hub,Good.ARTIFACT)*1.4,"маржа скупщика на артефактах не меньше 40% между скупкой и продажей");
    }
    System.out.printf("     за 4×1440 дней: крыша %d, взятки военным %d, вывезено за Периметр %d руб.; касса торговца max %d; у группировок в среднем %d%n",
        dues,bribes,export,maxTrader,sumFaction/Math.max(1,samples));
    ck(audit,"аудит сходится");
    ck(dues>0 && bribes>0 && export>0,"касса торговцев уходит крыше, военным и за Периметр");
    ck(maxTrader<75000,"торговцы не копят сверх меры (было до 85 тыс.)");
    ck(sumFaction/Math.max(1,samples)>1500,"у группировок появились деньги (крыша платит)");
    Simulation s=ZoneScenario.create(2); Faction mil=s.military(); long m0=mil.stock.money; int a0=mil.stock.get(Good.ARTIFACT);
    s.playerBribe(mil,500); s.playerConfiscated(mil,Good.ARTIFACT,3);
    ck(mil.stock.money==m0+500 && mil.stock.get(Good.ARTIFACT)==a0+3 && s.audit().isEmpty(),"взятка и конфискация на посту идут военным через учёт");
    if(f>0){System.out.println("ПРОВАЛОВ: "+f); System.exit(1);} System.out.println("Все проверки пройдены");
  }
}
