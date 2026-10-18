package cn.lzy.gt7p.installerclean;
final class Profile {
    static final String PACKAGE = "com.oplus.appdetail";
    static final String NATIVE_PACKAGE = "com.android.packageinstaller";
    static final long NATIVE_VERSION = 17000001L;
    static final class Rule {
        final long versionCode;
        final String versionName, state, stateBase, fragment, cardsGetter, marketGetter, detectGetter;
        final String[][] hooks, deopt;
        Rule(long code, String name, String state, String base, String fragment,
                String cards, String market, String detect, String[][] hooks, String[][] deopt) {
            this.versionCode=code; this.versionName=name; this.state=state; this.stateBase=base;
            this.fragment=fragment; cardsGetter=cards; marketGetter=market; detectGetter=detect;
            this.hooks=hooks; this.deopt=deopt;
        }
    }
    static final Rule[] ALL = {
        new Rule(51762L, "5.17.62_os17", "de.i", "de.f", "com.oplus.appdetail.modelv2.guide.fragment.EnhanceGuardFragment", "x", "G", "F", new String[][] {
            {"PRELOAD", "com.oplus.appdetail.modelv2.parsing.reository.PreloadGuideRepository", "a", "java.lang.Object", "qd.a", "qd.c", "com.oplus.appdetail.modelv2.parsing.c", "kotlin.coroutines.c"},
            {"PARSED", "com.oplus.appdetail.modelv2.parsing.reository.a", "a", "com.oplus.appdetail.modelv2.parsing.b", "qd.k", "com.heytap.cdo.security.domain.safeguide.GuideResult", "java.util.HashMap", "com.oplus.appdetail.modelv2.parsing.c", "boolean"},
            {"FRAGMENT", "com.oplus.appdetail.modelv2.helper.c", "a", "androidx.fragment.app.Fragment", "com.oplus.appdetail.model.guide.repository.ExtJumpParam"},
            {"STATE", "com.oplus.appdetail.model.guide.repository.netrequest.GuideRepository", "f", "java.lang.Object", "com.oplus.appdetail.model.guide.repository.netrequest.f", "boolean", "boolean", "kotlin.coroutines.c"},
            {"GUIDE", "com.oplus.appdetail.model.guide.repository.netrequest.g", "b", "com.heytap.cdo.security.domain.safeguide.GuideResult", "java.lang.String"},
            {"STATE", "com.oplus.appdetail.model.guide.repository.netrequest.g", "c", "de.f", "java.lang.String", "boolean"},
            {"NULL", "com.oplus.appdetail.model.finish.manager.RecommendManager", "i", "java.lang.Object", "com.oplus.appdetail.model.finish.b", "kotlin.coroutines.c"},
            {"NOOP", "com.oplus.appdetail.model.finish.manager.RecommendManager", "l", "void", "com.oplus.appdetail.model.guide.repository.ExtJumpParam"},
            {"RESULT", "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "a0", "void", "com.heytap.cdo.card.domain.dto.ViewLayerWrapDto"},
            {"UNIT", "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "I", "java.lang.Object", "kotlin.coroutines.c"},
            {"UNIT", "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "J", "java.lang.Object", "int", "boolean", "kotlin.coroutines.c"},
            {"NOOP", "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "N", "void", "boolean"},
            {"NOOP", "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "O", "void"},
            {"NOOP", "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "P", "void"},
            {"DEFAULT", "com.oplus.appdetail.model.install.util.ResultPageDialogHelper", "f", "com.oplus.appdetail.model.install.util.ResultPageDialogType", "com.heytap.cdo.card.domain.dto.installer.ResultPageInfo"},
            {"DEFAULT", "com.oplus.appdetail.model.install.util.ResultPageDialogHelper", "g", "com.oplus.appdetail.model.install.util.ResultPageDialogType", "com.heytap.cdo.card.domain.dto.installer.SelfUpdateInfoDto"},
            {"FALSE", "com.oplus.appdetail.model.cancel.c", "a", "boolean"},
            {"FALSE", "com.oplus.appdetail.model.cancel.c", "b", "boolean"},
            {"FALSE", "com.oplus.appdetail.model.cancel.c", "c", "boolean"},
            {"NOOP", "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "Q", "void"},
            {"HIDE_SCAN_NOTE", "com.oplus.appdetail.modelv2.guide.view.ScanContentFrameLayout", "g", "void"},
            {"NATIVE_SCAN_START", "com.oplus.appdetail.modelv2.guide.fragment.EnhanceGuardFragment", "G", "void"},
            {"NATIVE_READY", "com.oplus.appdetail.modelv2.guide.viewmodel.RiskScanViewModel", "r", "void", "com.oplus.appdetail.model.guide.repository.ExtJumpParam", "com.heytap.cdo.security.domain.safeguide.GuideResult", "long"},
            {"PERMISSIONS", "com.oplus.appdetail.modelv2.guide.view.ScanContentFrameLayout", "i", "void", "boolean", "com.oplus.appdetail.modelv2.guide.helper.h"},
            {"NATIVE_BOTTOM", "com.oplus.appdetail.modelv2.guide.presenter.GuideBottomViewPresenter", "l", "void", "com.oplus.appdetail.modelv2.guide.view.GuideBottomView"},
            {"NATIVE_INSTALL", "com.oplus.appdetail.modelv2.riskDialog.DialogHelper", "i", "boolean", "android.view.View", "com.oplus.appdetail.modelv2.guide.helper.h"},
            {"NOOP", "com.oplus.appdetail.modelv2.guide.viewmodel.RiskScanViewModel", "k", "void", "com.heytap.cdo.security.domain.safeguide.GuideResult", "java.lang.String"},
            {"NOOP", "com.oplus.appdetail.modelv2.guide.presenter.d", "f", "void", "boolean", "java.lang.String", "android.content.DialogInterface$OnDismissListener", "boolean"},
            {"NOOP", "com.oplus.appdetail.modelv2.guide.presenter.d", "g", "void", "boolean"},
            {"NOOP", "com.oplus.appdetail.modelv2.guide.helper.EnhanceUiBindHelper", "q", "void", "com.oplus.appdetail.modelv2.guide.view.GuideBottomView"},
            {"FINISH_NOTE", "com.oplus.appdetail.model.install.view.ProcessSuccessLayout", "f", "void", "int", "java.lang.String"},
            {"PERMISSION_DETAILS", "ke.a", "d", "java.util.List", "com.oplus.appdetail.model.permission.ui.PermissionActivity"}
        }, new String[][] {
            {"com.oplus.appdetail.modelv2.parsing.ParsingViewModel", "n"},
            {"com.oplus.appdetail.model.guide.ui.InstallGuideActivity", "A0"},
            {"com.oplus.appdetail.model.guide.viewModel.GuideViewModel", "g"},
            {"com.oplus.appdetail.model.guide.repository.netrequest.GuideRepository$loadDataFlow$1", "invokeSuspend"},
            {"com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "a0"},
            {"com.oplus.appdetail.model.cancel.CancelInstallViewModel", "e"},
            {"com.oplus.appdetail.modelv2.guide.fragment.EnhanceGuardFragment", "onViewCreated"},
            {"com.oplus.appdetail.modelv2.guide.fragment.EnhanceGuardFragment", "I"},
            {"com.oplus.appdetail.modelv2.guide.fragment.EnhanceGuardFragment", "J"},
            {"com.oplus.appdetail.modelv2.guide.fragment.EnhanceGuardFragment", "K"},
            {"com.oplus.appdetail.modelv2.guide.listener.InstallBtnClickPresenter$a", "a"},
            {"com.oplus.appdetail.model.install.util.InstallPageUIHelper", "s"},
            {"ke.a", "<init>"}
        }),
        new Rule(52302L, "5.23.0", "yd.k", "yd.h", "com.oplus.appdetail.modelv2.guide.fragment.EnhanceGuardFragment", "x", "I", "H", new String[][] {
            {"PRELOAD", "com.oplus.appdetail.modelv2.parsing.reository.PreloadGuideRepository", "a", "java.lang.Object", "ld.a", "ld.c", "com.oplus.appdetail.modelv2.parsing.c", "kotlin.coroutines.c"},
            {"PARSED", "com.oplus.appdetail.modelv2.parsing.reository.a", "a", "com.oplus.appdetail.modelv2.parsing.b", "ld.k", "com.heytap.cdo.security.domain.safeguide.GuideResult", "java.util.HashMap", "com.oplus.appdetail.modelv2.parsing.c", "boolean"},
            {"FRAGMENT", "com.oplus.appdetail.modelv2.helper.c", "a", "androidx.fragment.app.Fragment", "com.oplus.appdetail.model.guide.repository.ExtJumpParam"},
            {"STATE", "com.oplus.appdetail.model.guide.repository.netrequest.GuideRepository", "f", "java.lang.Object", "com.oplus.appdetail.model.guide.repository.netrequest.f", "boolean", "boolean", "boolean", "kotlin.coroutines.c"},
            {"GUIDE", "com.oplus.appdetail.model.guide.repository.netrequest.g", "b", "com.heytap.cdo.security.domain.safeguide.GuideResult", "java.lang.String"},
            {"STATE", "com.oplus.appdetail.model.guide.repository.netrequest.g", "c", "yd.h", "java.lang.String", "boolean"},
            {"NULL", "com.oplus.appdetail.model.finish.manager.RecommendManager", "i", "java.lang.Object", "com.oplus.appdetail.model.finish.b", "kotlin.coroutines.c"},
            {"NOOP", "com.oplus.appdetail.model.finish.manager.RecommendManager", "l", "void", "com.oplus.appdetail.model.guide.repository.ExtJumpParam"},
            {"RESULT", "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "b0", "void", "com.heytap.cdo.card.domain.dto.ViewLayerWrapDto"},
            {"UNIT", "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "K", "java.lang.Object", "kotlin.coroutines.c"},
            {"UNIT", "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "L", "java.lang.Object", "int", "kotlin.coroutines.c"},
            {"NOOP", "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "P", "void"},
            {"NOOP", "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "Q", "void"},
            {"NOOP", "com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "R", "void"},
            {"DEFAULT", "com.oplus.appdetail.model.install.util.ResultPageDialogHelper", "h", "com.oplus.appdetail.model.install.util.ResultPageDialogType", "com.heytap.cdo.card.domain.dto.installer.ResultPageInfo"},
            {"DEFAULT", "com.oplus.appdetail.model.install.util.ResultPageDialogHelper", "i", "com.oplus.appdetail.model.install.util.ResultPageDialogType", "com.heytap.cdo.card.domain.dto.installer.SelfUpdateInfoDto"},
            {"FALSE", "com.oplus.appdetail.model.guide.helper.GameUpdateReminderHelper", "canShowCard", "boolean", "java.util.Map"},
            {"FALSE", "com.oplus.appdetail.model.guide.helper.GameUpdateReminderHelper", "isSwitchEnabled", "boolean", "java.util.Map"},
            {"NOOP", "com.oplus.appdetail.modelv2.guide.presenter.EnhanceRecommendPresenter", "u", "void", "android.content.Context"},
            {"FALSE", "com.oplus.appdetail.model.cancel.c", "a", "boolean"},
            {"FALSE", "com.oplus.appdetail.model.cancel.c", "b", "boolean"},
            {"FALSE", "com.oplus.appdetail.model.cancel.c", "c", "boolean"},
            {"HIDE_SCAN_NOTE", "com.oplus.appdetail.modelv2.guide.view.ScanContentFrameLayout", "h", "void"},
            {"NATIVE_SCAN_START", "com.oplus.appdetail.modelv2.guide.fragment.EnhanceGuardFragment", "H", "void"},
            {"NATIVE_READY", "com.oplus.appdetail.modelv2.guide.viewmodel.RiskScanViewModel", "r", "void", "com.oplus.appdetail.model.guide.repository.ExtJumpParam", "com.heytap.cdo.security.domain.safeguide.GuideResult", "long"},
            {"PERMISSIONS", "com.oplus.appdetail.modelv2.guide.view.ScanContentFrameLayout", "j", "void", "boolean", "com.oplus.appdetail.modelv2.guide.helper.i"},
            {"NATIVE_BOTTOM", "com.oplus.appdetail.modelv2.guide.presenter.GuideBottomViewPresenter", "m", "void", "com.oplus.appdetail.modelv2.guide.view.GuideBottomView", "boolean"},
            {"NATIVE_INSTALL", "com.oplus.appdetail.modelv2.riskDialog.DialogHelper", "i", "boolean", "android.view.View", "com.oplus.appdetail.modelv2.guide.helper.i"},
            {"NOOP", "com.oplus.appdetail.modelv2.guide.viewmodel.RiskScanViewModel", "k", "void", "com.heytap.cdo.security.domain.safeguide.GuideResult", "java.lang.String"},
            {"NOOP", "com.oplus.appdetail.modelv2.guide.helper.EnhanceUiBindHelper", "t", "void", "com.oplus.appdetail.modelv2.guide.view.GuideBottomView"},
            {"FINISH_NOTE", "com.oplus.appdetail.model.install.view.ProcessSuccessLayout", "f", "void", "int", "java.lang.String"},
            {"PERMISSION_DETAILS", "fe.a", "d", "java.util.List", "com.oplus.appdetail.model.permission.ui.PermissionActivity"}
        }, new String[][] {
            {"com.oplus.appdetail.modelv2.parsing.ParsingViewModel", "n"},
            {"com.oplus.appdetail.model.guide.ui.InstallGuideActivity", "E0"},
            {"com.oplus.appdetail.model.guide.viewModel.GuideViewModel", "g"},
            {"com.oplus.appdetail.model.guide.repository.netrequest.GuideRepository$loadDataFlow$1", "invokeSuspend"},
            {"com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel", "b0"},
            {"com.oplus.appdetail.model.cancel.CancelInstallViewModel", "e"},
            {"com.oplus.appdetail.modelv2.guide.fragment.EnhanceGuardFragment", "onViewCreated"},
            {"com.oplus.appdetail.modelv2.guide.fragment.EnhanceGuardFragment", "J"},
            {"com.oplus.appdetail.modelv2.guide.fragment.EnhanceGuardFragment", "K"},
            {"com.oplus.appdetail.modelv2.guide.fragment.EnhanceGuardFragment", "L"},
            {"com.oplus.appdetail.modelv2.guide.listener.InstallBtnClickPresenter$a", "a"},
            {"com.oplus.appdetail.model.install.util.InstallPageUIHelper", "u"},
            {"fe.a", "<init>"}
        })
    };
    static Rule select(long versionCode) {
        for (Rule rule : ALL) if (rule.versionCode == versionCode) return rule;
        return null;
    }
}
