package cn.lzy.gt7p.installerclean;
import com.oplus.appdetail.model.install.viewmodel.*;
import com.heytap.cdo.card.domain.dto.installer.InstallerResultCardDto;
public class FinishButtonsTest {
 static int count;
 static void check(boolean ok) {count++; if(!ok)throw new AssertionError("finish actions case "+count);}
 public static void main(String[] args) throws Exception {
  for(boolean legacy:new boolean[]{true,false}) {
   var helper=new FinishButtons(FinishButtonsTest.class.getClassLoader(),legacy);
   var vm=new PkgInstallViewModel();
   for(Object state:new Object[]{null,new b(),new Object()}) {
    vm.state.value=state; check(helper.restore(vm,null)==null);
   }
   vm.state.value=new c();
   Object original=new Object(); check(helper.restore(vm,original)==original);
   var card=(InstallerResultCardDto)helper.restore(vm,null);
   check(card.first.behavior.equals("open")); check(card.second.behavior.equals("complete"));
   check(card.first.title!=null && card.second.title!=null); check(card.third==null);
   var another=(InstallerResultCardDto)helper.restore(vm,null);
   check(card!=another && card.first!=another.first);
   vm.state.value=new b(); check(helper.restore(vm,null)==null);
  }
  System.out.println("FINISH_BUTTONS_PASS assertions="+count);
 }
}
