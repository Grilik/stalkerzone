import ru.stalkerzone.sim.*;
/** v1.14: наёмники, Чистое небо, Грех; главная вражда Долга и Свободы; новые локации связаны. */
public class FactionTest {
  static int f=0; static void ck(boolean ok,String m){System.out.println((ok?"OK   ":"FAIL ")+m); if(!ok)f++;}
  static Faction by(Simulation s,String n){ for(Faction x:s.factions) if(x.name.equals(n)) return x; return null; }
  static Node node(Simulation s,String n){ for(Node x:s.nodes) if(x.name.equals(n)) return x; return null; }
  public static void main(String[] a){
    Simulation s=ZoneScenario.create(5);
    Faction merc=by(s,"Наёмники"), cs=by(s,"Чистое небо"), sin=by(s,"Грех"), duty=by(s,"Долг"), free=by(s,"Свобода");
    ck(merc!=null&&cs!=null&&sin!=null,"три новые группировки в Зоне");
    ck(s.nodes.get(merc.baseNode).name.equals("Дикая территория") && s.nodes.get(cs.baseNode).name.equals("Болота") && s.nodes.get(sin.baseNode).name.equals("Мёртвый город"),"базы: Дикая территория, Болота, Мёртвый город");
    ck(s.hostilityTo(duty,free.id)>=1.0,"Долг и Свобода — главная вражда");
    boolean sinAll=true; for(Faction x:s.factions) if(x!=sin && !x.fanatic && s.hostilityTo(sin,x.id)<1.0) sinAll=false;
    ck(sinAll && sin.raider,"«Грех» враждует со всеми");
    for(String n:new String[]{"Болота","Мёртвый город","Собачья деревня","Кордон Лесника","Хутор Болотного Доктора"})
      { Node x=node(s,n); boolean edge=false; if(x!=null) for(Node o:s.nodes) if(s.graph.edgeDays(x.id,o.id)>0) edge=true; ck(x!=null&&edge,"локация «"+n+"» на карте и связана дорогой"); }
    Lair dogs=s.lairAt(node(s,"Собачья деревня").id);
    ck(dogs!=null && dogs.kind==LairKind.DOGS,"в Собачьей деревне логово слепых псов");
    ck(s.lairAt(node(s,"Кордон Лесника").id)==null && s.lairAt(node(s,"Хутор Болотного Доктора").id)==null,"Лесника и Доктора звери не трогают");
    boolean audit=true; for(int d=0;d<600;d++){ s.step(); if(!s.audit().isEmpty()){audit=false; System.out.println(s.audit()); break;} }
    ck(audit,"аудит сходится 600 дней с десятью группировками");
    System.out.printf("     через 600 дней: наёмники %d, Чистое небо %d, Грех %d, войн у наёмников %d%n", merc.members, cs.members, sin.members, merc.warsStarted);
    ck(merc.members>0 && cs.members>0,"наёмники и Чистое небо живы");
    if(f>0){System.out.println("ПРОВАЛОВ: "+f); System.exit(1);} System.out.println("Все проверки пройдены");
  }
}
