import ru.stalkerzone.sim.*;
import ru.stalkerzone.mutant.MutantType;
/** Э0-10: enum-ы, сохраняемые по номеру (ординалу): текущий список имён — префикс values(). Дописывание в конец можно, вставка/перестановка — провал. */
public class EnumOrderTest {
  static int f=0; static void ck(boolean ok,String m){System.out.println((ok?"OK   ":"FAIL ")+m); if(!ok)f++;}
  static <T extends Enum<T>> void chk(Class<T> c,String label,String... expected){
    T[] v=c.getEnumConstants(); boolean ok=v.length>=expected.length; String bad="";
    if(v.length<expected.length) bad=" значений "+v.length+", было не меньше "+expected.length+" (удалено?)";
    for(int i=0;i<expected.length&&ok;i++) if(!v[i].name().equals(expected[i])){ok=false;bad=" на месте "+i+": ожидали "+expected[i]+", сейчас "+v[i].name();}
    ck(ok,"порядок "+label+(ok?" ("+expected.length+" зафиксировано, всего "+v.length+")":bad+" — менять порядок нельзя, новые значения только в конец"));
  }
  public static void main(String[] a){
    chk(Phase.class,"Phase","ACCUMULATE","WAR","RECOVER");
    chk(Goal.class,"Goal","FORTIFY","TRADE","EXPAND","REVENGE","CLEANSE","RESIST","CRUSADE");
    chk(LairKind.class,"LairKind","DOGS","FLESH","BLOODSUCKERS","CHIMERA","CONTROLLERS","RODENTS","BURERS","GIANTS","POLTERGEISTS");
    chk(BuildingType.class,"BuildingType","HQ","BARRACKS","WORKSHOP","CLINIC","TOWER");
    chk(AnomalyType.class,"AnomalyType","SPRINGBOARD","WHIRLIGIG","GRINDER","MOSQUITO","FRY","FLUFF","ELECTRA","FOG","COMET","JELLY","RUST","LIFT","BRAZIER","WEB");
    chk(Contract.Type.class,"Contract.Type","FETCH_ARTIFACT","SUPPLY","CLEAR_LAIR","LAB_DATA","GUARD_MEETING","AMBUSH_MEETING","ESCORT_COURIER","CAPTURE_NODE","AMBUSH_COURIER","RAID_NODE","SCOUT");
    chk(Contract.Status.class,"Contract.Status","OPEN","TAKEN","READY","DONE","FAILED","EXPIRED");
    chk(Convoy.Kind.class,"Convoy.Kind","SUPPLY","RETURN","CONTRACT","REINFORCE","TRIBUTE","SMUGGLE");
    chk(Person.Cls.class,"Person.Cls","ASSAULT","SNIPER","MEDIC","DOCTOR","TECH","SCOUT");
    chk(Person.Role.class,"Person.Role","MEMBER","SPECIALIST","SQUAD_LEADER","DEPUTY","LEADER");
    chk(MutantType.class,"MutantType","DOG","FLESH","BOAR","SNORK","BLOODSUCKER","CHIMERA","CONTROLLER","PSEUDODOG","PSYDOG","TUSHKAN","BURER","POLTERGEIST","PSEUDOGIANT");
    chk(Good.class,"Good","FOOD","PARTS","AMMO","MEDS","GEAR","ARTIFACT","MATERIALS","PROTO","AMMO_PRO","GEAR_PRO");
    boolean idx=Good.ALL.length==Good.values().length;
    for(int i=0;idx&&i<Good.ALL.length;i++) if(Good.ALL[i]!=Good.values()[i]||Good.ALL[i].ordinal()!=i) idx=false;
    ck(idx,"Good.ALL совпадает с values() и ординалами");
    System.out.println(f==0?"EnumOrderTest: OK":"EnumOrderTest: FAIL "+f);
    if(f>0) System.exit(1);
  }
}
