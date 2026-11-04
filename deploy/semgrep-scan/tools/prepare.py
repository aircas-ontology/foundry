"""Generate or verify the manifest for the bundled runtime rules (offline)."""
import hashlib
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from app.versioning import fingerprint


def main():
    checked = (ROOT / 'version-manifest.json').read_bytes() if '--check' in sys.argv else None
    blocking = json.loads((ROOT / 'rules' / 'blocking.json').read_text(encoding='utf-8'))
    languages = ('java', 'python', 'typescript')
    if set(blocking) != set(languages):
        raise ValueError('Blocking policy must cover all supported languages')
    selection = {}
    for language in languages:
        rules = json.loads((ROOT / 'rules' / 'active' / (language + '.yaml')).read_text(encoding='utf-8'))['rules']
        ids = [rule['id'] for rule in rules]
        if not ids or len(ids) != len(set(ids)):
            raise ValueError('Empty or duplicate rules: ' + language)
        if any(rule['languages'] != [language] for rule in rules):
            raise ValueError('Unexpected parser language: ' + language)
        if not blocking[language] or not set(blocking[language]) <= set(ids):
            raise ValueError('Invalid blocking policy: ' + language)
        selection[language] = {'applicableIds': ids}
    base = json.loads((ROOT / 'base-image.json').read_text(encoding='utf-8'))
    inputs = fingerprint(ROOT)
    version = 'sha256:' + hashlib.sha256(json.dumps(inputs, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    manifest = {
        'schemaVersion': 1, 'baseImage': base, 'rulesetVersion': version,
        'inputs': inputs, 'selection': selection,
        'limits': {'slots': 2, 'jobs': 1, 'engineTimeoutSeconds': 15,
                   'totalTimeoutSeconds': 20, 'codeBytes': 262144,
                   'reportBytes': 4194304, 'diagnosticBytes': 1048576,
                   'diagnosticLines': 200, 'findings': 200},
        'buildId': version.removeprefix('sha256:')[:16],
    }
    serialized = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    if checked is not None and checked != serialized:
        raise ValueError('Manifest is stale: regenerate with tools/prepare.py in the locked base before building')
    (ROOT / 'version-manifest.json').write_bytes(serialized)
    print(json.dumps({'rulesetVersion': version, 'rules': {key: len(value['applicableIds']) for key, value in selection.items()}}))


if __name__ == '__main__':
    main()
