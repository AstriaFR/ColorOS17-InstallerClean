"""Validate reference APKs at build time; embed only version numbers and rule tables."""
import hashlib, json, re, sys, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parent
from loguru import logger
logger.remove()
from androguard.core.dex import DEX

def descriptor(name):
    if name.endswith('[]'): return '['+descriptor(name[:-2])
    return {'void':'V', 'boolean':'Z', 'int':'I', 'long':'J'}.get(name, 'L'+name.replace('.', '/')+';')

config = json.loads((ROOT/'profile.json').read_text(encoding='utf8'))
reports, emitted = [], []
jstr = lambda value: json.dumps(value, ensure_ascii=True)
def array(rows):
    return 'new String[][] {\n'+',\n'.join('            {'+', '.join(map(jstr,row))+'}' for row in rows)+'\n        }'

for profile in config['profiles']:
    apk = ROOT/profile['referenceApk']
    # Identify the development sample. No hash is embedded in the module.
    assert hashlib.sha256(apk.read_bytes()).hexdigest()==profile['apkSha256'], 'Wrong reference APK'
    methods, fields = set(), set()
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not re.fullmatch(r'classes\d*\.dex',name): continue
            for cls in DEX(archive.read(name)).get_classes():
                for method in cls.get_methods():
                    methods.add((cls.get_name(),method.get_name(),method.get_descriptor().replace(' ','')))
                for field in cls.get_fields(): fields.add((cls.get_name(),field.get_name(),field.get_descriptor()))
    def check(owner,name,result,*params):
        sig=(descriptor(owner),name,'('+''.join(map(descriptor,params))+')'+descriptor(result))
        assert sig in methods, f'{profile["versionCode"]}: missing {sig}'
    for action,owner,name,result,*params in profile['hooks']: check(owner,name,result,*params)
    for owner,name in profile['deopt']:
        assert any(x[0]==descriptor(owner) and x[1]==name for x in methods), f'Missing caller {owner}#{name}'
    b=profile['bindings']
    domain='com.heytap.cdo.security.domain.safeguide.'
    model='com.oplus.appdetail.model.install.viewmodel.PkgInstallViewModel'
    deps=[
        [domain+'GuideResult','<init>','void'],
        [b['state'],'<init>','void',domain+'GuideResult'],
        ['com.oplus.appdetail.modelv2.parsing.reository.c','<init>','void',b['stateBase'],'com.heytap.cdo.security.domain.expgray.ConfigResult','java.lang.String'],
        ['com.oplus.appdetail.modelv2.parsing.b$j','<init>','void','boolean'],
        [b['fragment'],'<init>','void'],
        [domain+'GuideResult','getGuideConfig',domain+'GuideConfig'],
        [domain+'GuideResult','setRiskDtoList','void','java.util.List'],
        *[[domain+'GuideConfig',name,'void',param] for name,param in [('setGuideMode','java.lang.String'),('setGuidePageType','int'),('setScanTime','java.lang.String'),('setShowWarning','java.lang.String'),('setVerifySwitch','boolean'),('setNewGuideSwitch','boolean')]],
        *[[model,b[key],'androidx.lifecycle.MutableLiveData'] for key in ['cardsGetter','marketGetter','detectGetter']],
        ['androidx.lifecycle.MutableLiveData','postValue','void','java.lang.Object']
    ]
    deps += profile.get('dependencies', [])
    for dep in deps: check(*dep)
    assert (descriptor('com.oplus.appdetail.model.guide.repository.riskScan.k'),'a',descriptor('com.oplus.appdetail.model.guide.repository.riskScan.k')) in fields
    assert (descriptor('kotlin.s'),'a',descriptor('kotlin.s')) in fields
    assert (descriptor('com.oplus.appdetail.model.install.util.ResultPageDialogType'),'DEFAULT',descriptor('com.oplus.appdetail.model.install.util.ResultPageDialogType')) in fields
    arguments=[str(profile['versionCode'])+'L',jstr(profile['versionName'])]+[jstr(b[key]) for key in ['state','stateBase','fragment','cardsGetter','marketGetter','detectGetter']]+[array(profile['hooks']),array(profile['deopt'])]
    emitted.append('        new Rule('+', '.join(arguments)+')')
    reports.append({'versionCode':profile['versionCode'],'versionName':profile['versionName'],'referenceApkSha256':profile['apkSha256'],'hooks':len(profile['hooks']),'dependencies':len(deps),'fields':3,'deoptCallers':len(profile['deopt']),'result':'PASS'})

native=config['nativeRouter']
native_apk=ROOT/native['referenceApk']
assert hashlib.sha256(native_apk.read_bytes()).hexdigest()==native['referenceApkSha256']
native_methods=set()
with zipfile.ZipFile(native_apk) as archive:
    for name in archive.namelist():
        if not re.fullmatch(r'classes\d*\.dex',name): continue
        for cls in DEX(archive.read(name)).get_classes():
            for method in cls.get_methods(): native_methods.add((cls.get_name(),method.get_name(),method.get_descriptor().replace(' ','')))
for owner,name,result,*params in native['targets']:
    signature=(descriptor(owner),name,'('+''.join(map(descriptor,params))+')'+descriptor(result))
    assert signature in native_methods, f'Missing native router dependency: {signature}'

source='''package cn.lzy.gt7p.installerclean;
final class Profile {
    static final String PACKAGE = %s;
    static final String NATIVE_PACKAGE = %s;
    static final long NATIVE_VERSION = %dL;
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
%s
    };
    static Rule select(long versionCode) {
        for (Rule rule : ALL) if (rule.versionCode == versionCode) return rule;
        return null;
    }
}
''' % (jstr(config['package']),jstr(native['package']),native['versionCode'],',\n'.join(emitted))
assert all(p['apkSha256'] not in source for p in config['profiles'])
generated=ROOT/'src/java/cn/lzy/gt7p/installerclean'
generated.mkdir(parents=True,exist_ok=True)
(generated/'Profile.java').write_text(source,encoding='utf8')
report={'selection':'versionCode only','profiles':reports,'nativeRouter':{'versionCode':native['versionCode'],'hooks':1,'dependencies':len(native['targets']),'result':'PASS'},'runtimeDeviceGate':False,'runtimeOsGate':False,'runtimeFirmwareGate':False,'runtimeApkHashGate':False,'runtimeSplitGate':False,'vectorInjectionTested':False,'endToEndInstallTested':False}
(ROOT/'verification').mkdir(exist_ok=True)
(ROOT/'verification/static-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(json.dumps(report))
