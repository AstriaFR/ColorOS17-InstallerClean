"""Verify reference DEX declarations and emit immutable, version-independent feature templates."""
import hashlib
import json
import re
import zipfile
from pathlib import Path
from loguru import logger
from androguard.core.dex import DEX

logger.remove()
PRIMITIVES = {'void': 'V', 'boolean': 'Z', 'int': 'I', 'long': 'J', 'byte': 'B', 'short': 'S', 'char': 'C', 'float': 'F', 'double': 'D'}

def descriptor(name):
    if name.endswith('[]'): return '[' + descriptor(name[:-2])
    return PRIMITIVES.get(name, 'L' + name.replace('.', '/') + ';')

def signature(owner, name, result, *params):
    return descriptor(owner), name, '(' + ''.join(map(descriptor, params)) + ')' + descriptor(result)

def declarations(apk, expected_hash):
    assert hashlib.sha256(apk.read_bytes()).hexdigest() == expected_hash, 'Wrong development reference APK'
    methods, fields, parents = {}, {}, {}
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not re.fullmatch(r'classes\d*\.dex', name): continue
            for cls in DEX(archive.read(name)).get_classes():
                parents[cls.get_name()] = [cls.get_superclassname(), *cls.get_interfaces()]
                for method in cls.get_methods():
                    methods[(cls.get_name(), method.get_name(), method.get_descriptor().replace(' ', ''))] = method.get_access_flags()
                for field in cls.get_fields():
                    fields[(cls.get_name(), field.get_name(), field.get_descriptor())] = field.get_access_flags()
    return methods, fields, parents

def assignable(expected, actual, parents):
    pending, visited = [descriptor(actual)], set()
    target = descriptor(expected)
    while pending:
        item = pending.pop()
        if item == target: return True
        if item in visited: continue
        visited.add(item)
        pending.extend(parents.get(item, []))
    return False

def java_type(text, position):
    code = text[position]
    if code == '[':
        name, end = java_type(text, position + 1)
        return name + '[]', end
    if code == 'L':
        end = text.index(';', position)
        return text[position + 1:end].replace('/', '.'), end + 1
    return {value: key for key, value in PRIMITIVES.items()}[code], position + 1

def caller_spec(key):
    owner, name, proto = key
    params, position = [], 1
    while proto[position] != ')':
        value, position = java_type(proto, position)
        params.append(value)
    result, end = java_type(proto, position + 1)
    assert end == len(proto)
    return [owner[1:-1].replace('/', '.'), name, result, *params]

def main(root, generated_relative='src/java/cn/lzy/gt7p/installerclean', report_relative='verification'):
    config = json.loads((root / 'profile.json').read_text(encoding='utf8'))
    jstr = lambda value: json.dumps(value, ensure_ascii=True)
    array = lambda rows: 'new String[][] {' + ','.join('{' + ','.join(map(jstr, row)) + '}' for row in rows) + '}'
    bools = lambda values: 'new boolean[] {' + ','.join(str(value).lower() for value in values) + '}'
    reports, emitted, compiled, samples = [], [], [], []
    for profile in config['profiles']:
        methods, fields, parents = declarations(root / profile['referenceApk'], profile['apkSha256'])
        samples.append((profile, methods, fields, parents))
        groups, counts, all_hooks = [], {}, []
        for feature, group in profile['features'].items():
            hooks, dependencies = group['hooks'], group['dependencies']
            contracts = group.get('assignable', [])
            for expected, actual in contracts:
                assert assignable(expected, actual, parents), f'Invalid binding {actual} -> {expected}'
            hook_static, dependency_static = [], []
            for hook in hooks:
                key = signature(*hook[1:])
                assert key in methods, f'{profile["id"]}/{feature}: missing hook {key}'
                hook_static.append(bool(methods[key] & 8))
            for dependency in dependencies:
                key = signature(*dependency)
                assert key in methods, f'{profile["id"]}/{feature}: missing dependency {key}'
                dependency_static.append(bool(methods[key] & 8))
            for owner, name, field_type, static in group['fields']:
                key = descriptor(owner), name, descriptor(field_type)
                assert key in fields and bool(fields[key] & 8) == (static == 'true'), f'Missing field {key}'
            callers = []
            for owner, name in group['deopt']:
                found = [key for key in methods if key[0] == descriptor(owner) and key[1] == name]
                assert found, f'Missing caller {owner}#{name}'
                callers += [caller_spec(key) for key in sorted(found)]
            groups.append('new Group(Feature.' + feature + ',' + ','.join([
                array(hooks), bools(hook_static), array(dependencies), bools(dependency_static), array(group['fields']), array(callers), array(contracts)]) + ')')
            fingerprint = frozenset(
                [('H', static, *hook) for hook, static in zip(hooks, hook_static)]
                + [('D', static, *dep) for dep, static in zip(dependencies, dependency_static)]
                + [('F', *field) for field in group['fields']]
                + [('C', *caller) for caller in callers] + [('B', *pair) for pair in contracts])
            compiled.append({'template': profile['id'], 'feature': feature, 'hooks': hooks,
                             'hookStatic': hook_static, 'dependencies': dependencies,
                             'dependencyStatic': dependency_static, 'fields': group['fields'],
                             'callers': callers, 'assignable': contracts, 'fingerprint': fingerprint})
            counts[feature] = {'hooks': len(hooks), 'dependencies': len(dependencies), 'fields': len(group['fields']), 'callers': len(callers)}
            all_hooks += hooks
        assert all_hooks and len({signature(*hook[1:]) for hook in all_hooks}) == len(all_hooks), 'Duplicate hook targets across feature groups'
        bindings = profile['bindings']
        args = [jstr(profile['id']), str(profile['legacy']).lower(), jstr(profile['versionName'])]
        args += [jstr(bindings[key]) for key in ['state', 'stateBase', 'fragment', 'cardsGetter', 'marketGetter', 'detectGetter']]
        args.append('new Group[]{' + ','.join(groups) + '}')
        emitted.append('new Rule(' + ','.join(args) + ')')
        reports.append({'template': profile['id'], 'referenceVersion': profile['versionName'], 'hooks': len(all_hooks), 'features': counts, 'result': 'PASS'})
    native = config['nativeRouter']
    methods, fields, parents = declarations(root / native['referenceApk'], native['referenceApkSha256'])
    entry, caller = native['targets']
    for target in native['targets']: assert signature(*target) in methods
    native_group = 'new Group(Feature.FLOW,' + ','.join([
        array([['ROUTE', *entry]]), bools([bool(methods[signature(*entry)] & 8)]),
        array([caller]), bools([bool(methods[signature(*caller)] & 8)]), array([]), array([caller])]) + ')'
    matrix = []
    for sample, sample_methods, sample_fields, sample_parents in samples:
        selected, rejected = {}, []
        for feature in ['FLOW', 'PERMISSIONS', 'RECOMMENDATIONS', 'RESULT', 'RESULT_DIALOGS', 'UPDATE_REMINDERS', 'CANCEL_PROMPTS', 'FINISH_NOTE']:
            matches = []
            for group in compiled:
                if group['feature'] != feature: continue
                if feature == 'PERMISSIONS' and (not selected.get('FLOW') or group['template'] != selected['FLOW']['template']): continue
                valid = all(signature(*hook[1:]) in sample_methods and bool(sample_methods[signature(*hook[1:])] & 8) == static
                            for hook, static in zip(group['hooks'], group['hookStatic']))
                valid &= all(signature(*dep) in sample_methods and bool(sample_methods[signature(*dep)] & 8) == static
                             for dep, static in zip(group['dependencies'], group['dependencyStatic']))
                valid &= all((descriptor(owner), name, descriptor(kind)) in sample_fields
                             and bool(sample_fields[(descriptor(owner), name, descriptor(kind))] & 8) == (static == 'true')
                             for owner, name, kind, static in group['fields'])
                valid &= all(signature(*caller) in sample_methods for caller in group['callers'])
                valid &= all(assignable(expected, actual, sample_parents) for expected, actual in group['assignable'])
                if valid: matches.append(group)
            maximal = []
            for match in matches:
                if any(match['fingerprint'] < other['fingerprint'] for other in matches): continue
                if not any(match['fingerprint'] == other['fingerprint'] for other in maximal): maximal.append(match)
            if len(maximal) == 1: selected[feature] = maximal[0]
            else: rejected.append({'feature': feature, 'reason': 'ambiguous' if maximal else 'no match'})
        assert selected.get('FLOW', {}).get('template') == sample['id']
        assert selected.get('PERMISSIONS', {}).get('template') == sample['id']
        matrix.append({'sample': sample['versionName'], 'matched': {key: {'template': value['template'], 'hooks': len(value['hooks'])}
                       for key, value in selected.items()}, 'skipped': rejected,
                       'totalHooks': sum(len(value['hooks']) for value in selected.values()), 'result': 'PASS'})
    source = '''package cn.lzy.gt7p.installerclean;
/** Generated structural templates. Reference version names are diagnostics, never gates. */
final class Profile {
    static final String PACKAGE = %s;
    static final String NATIVE_PACKAGE = %s;
    static final class Group {
        final Feature feature;
        final String[][] hooks, dependencies, fields, deopt, assignable;
        final boolean[] hookStatic, dependencyStatic;
        Group(Feature feature, String[][] hooks, boolean[] hookStatic, String[][] dependencies,
                boolean[] dependencyStatic, String[][] fields, String[][] deopt) {
            this(feature,hooks,hookStatic,dependencies,dependencyStatic,fields,deopt,new String[0][]);
        }
        Group(Feature feature, String[][] hooks, boolean[] hookStatic, String[][] dependencies,
                boolean[] dependencyStatic, String[][] fields, String[][] deopt, String[][] assignable) {
            this.feature=feature; this.hooks=hooks; this.hookStatic=hookStatic;
            this.dependencies=dependencies; this.dependencyStatic=dependencyStatic;
            this.fields=fields; this.deopt=deopt; this.assignable=assignable;
        }
    }
    static final class Rule {
        final String id, referenceVersion, state, stateBase, fragment, cardsGetter, marketGetter, detectGetter;
        final boolean legacy;
        final Group[] groups;
        Rule(String id, boolean legacy, String referenceVersion, String state, String stateBase,
                String fragment, String cards, String market, String detect, Group[] groups) {
            this.id=id; this.legacy=legacy; this.referenceVersion=referenceVersion;
            this.state=state; this.stateBase=stateBase; this.fragment=fragment;
            cardsGetter=cards; marketGetter=market; detectGetter=detect; this.groups=groups;
        }
        Group group(Feature feature) {
            for (Group group : groups) if (group.feature == feature) return group;
            return null;
        }
    }
    static final Rule[] ALL = {%s};
    static final Group NATIVE_GROUP = %s;
}
''' % (jstr(config['package']), jstr(native['package']), ',\n'.join(emitted), native_group)
    assert all(p['apkSha256'] not in source for p in config['profiles'])
    assert 'select(long' not in source and 'NATIVE_VERSION' not in source
    generated = root / generated_relative
    generated.mkdir(parents=True, exist_ok=True)
    (generated / 'Profile.java').write_text(source, encoding='utf8')
    report = {'selection': config['selection'], 'profiles': reports, 'structuralMatrix': matrix,
              'nativeRouter': {'structuralTargets': len(native['targets']), 'result': 'PASS'},
              'runtimeVersionGate': False, 'runtimeDeviceGate': False, 'runtimeOsGate': False,
              'runtimeFirmwareGate': False, 'runtimeApkHashGate': False, 'runtimeSplitGate': False,
              'unknownVersionBehaviorValidated': False, 'endToEndInstallTested': False}
    folder = root / report_relative
    folder.mkdir(exist_ok=True)
    (folder / 'static-verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report))
