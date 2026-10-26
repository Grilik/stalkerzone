import ru.stalkerzone.sim.*;
import ru.stalkerzone.artifact.ArtifactDef;
import java.util.*;
/** v1.14: каталог артефактов — прежние номера на месте, у каждой аномалии свои артефакты, редкие реже. */
public class ArtifactTest {
  static int f=0; static void ck(boolean ok,String m){System.out.println((ok?"OK   ":"FAIL ")+m); if(!ok)f++;}
  public static void main(String[] x){
    String[] old={"kolobok","stone_flower","stone_blood","soul","fireball","drop","flash","thorn"};
    boolean same=true; for(int i=0;i<old.length;i++) if(!ArtifactDef.ALL[i].id.equals(old[i])) same=false;
    ck(same,"прежние 8 артефактов на своих номерах (сохранения не ломаются)");
    ck(ArtifactDef.ALL.length>=20,"артефактов в каталоге: "+ArtifactDef.ALL.length);
    Set<String> ids=new HashSet<>(); boolean uniq=true; for(ArtifactDef d:ArtifactDef.ALL) if(!ids.add(d.id)) uniq=false;
    ck(uniq,"идентификаторы не повторяются");
    int withMinus=0, useless=0; for(ArtifactDef d:ArtifactDef.ALL){
      boolean minus=d.rad>0||d.heal<0||d.therm<0||d.elec<0||d.armor<0;
      boolean plus=d.rad<0||d.heal>0||d.carry>0||d.speed>0||d.stamina>0||d.therm>0||d.elec>0||d.chem>0||d.grav>0||d.psi>0||d.armor>0;
      if(minus) withMinus++; if(!plus) useless++; }
    ck(withMinus>=ArtifactDef.ALL.length*0.7,"у большинства есть минус: "+withMinus);
    ck(useless<=2,"бесполезных (только на продажу) не больше двух: "+useless);
    Random r=new Random(5); boolean every=true;
    for(AnomalyType t:AnomalyType.ALL) for(int tier=0;tier<=3;tier++){
      int i=ArtifactDef.pick(t,tier,r); ArtifactDef d=ArtifactDef.ALL[i];
      boolean ok=false; for(AnomalyType a:d.from) if(a==t) ok=true;
      if(!ok){ every=false; System.out.println("     "+t+" ур."+tier+" -> "+d.id+" (не из этой аномалии)"); } }
    ck(every,"каждая из "+AnomalyType.ALL.length+" аномалий рождает свои артефакты на любом уровне");
    int[] cnt=new int[4]; int caustic0=0;
    for(int k=0;k<20000;k++){ AnomalyType t=AnomalyType.ALL[r.nextInt(AnomalyType.ALL.length)];
      ArtifactDef d=ArtifactDef.ALL[ArtifactDef.pick(t,0,r)]; cnt[d.tier]++; if(d.caustic) caustic0++; }
    System.out.println("     слабые поля: ур.1="+cnt[1]+" ур.2="+cnt[2]+" ур.3="+cnt[3]);
    ck(cnt[3]==0&&cnt[1]>cnt[2],"в слабых полях редких нет, простые чаще");
    ck(caustic0==0,"ведьмин студень не родится в слабом поле");
    int[] c3=new int[4]; for(int k=0;k<20000;k++){ AnomalyType t=AnomalyType.ALL[r.nextInt(AnomalyType.ALL.length)]; c3[ArtifactDef.ALL[ArtifactDef.pick(t,3,r)].tier]++; }
    System.out.println("     смертельные поля: ур.1="+c3[1]+" ур.2="+c3[2]+" ур.3="+c3[3]);
    ck(c3[3]>c3[1]*0.5,"в смертельных полях редкие встречаются часто");
    if(f>0){System.out.println("ПРОВАЛОВ: "+f); System.exit(1);} System.out.println("Все проверки пройдены");
  }
}
