import ru.stalkerzone.sim.*;
/** v1.14: выброс заряжает поля артефактами, кривая активности мутантов, аудит не ломается. */
public class EmissionTest {
  static int f=0; static void ck(boolean ok,String m){System.out.println((ok?"OK   ":"FAIL ")+m); if(!ok)f++;}
  public static void main(String[] x){
    Simulation s=ZoneScenario.create(3);
    for(int d=0;d<10;d++) s.step();
    for(Anomaly a:s.anomalies) a.pending=0;
    ck(s.mutantActivity()==1.0,"до первого выброса активность обычная");
    s.triggerEmission();
    boolean all=true; for(Anomaly a:s.anomalies) if(a.pending<1) all=false;
    ck(all,"после выброса в каждом поле созрел артефакт ("+s.anomalies.size()+" полей)");
    double[] curve=new double[8];
    for(int d=0;d<8;d++){ curve[d]=s.mutantActivity(); s.step(); }
    System.out.println("     активность по суткам: "+java.util.Arrays.toString(curve));
    ck(curve[0]<1 && curve[1]<1,"сразу после выброса мутанты затихают");
    ck(curve[3]>1.4 && curve[4]>1.4,"пик на 3–4-е сутки");
    ck(curve[7]==1.0,"к неделе всё как обычно");
    ck(s.audit().isEmpty(),"аудит сходится после выброса");
    for(int d=0;d<60;d++) s.step();
    ck(s.audit().isEmpty(),"аудит сходится через два цикла выбросов");
    if(f>0){System.out.println("ПРОВАЛОВ: "+f); System.exit(1);} System.out.println("Все проверки пройдены");
  }
}
