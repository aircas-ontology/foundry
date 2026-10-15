import hashlib


def fingerprint(root):
    # This list is part of the fingerprint because app/versioning.py is included.
    # Do not include generated manifest or final service image digest (no circular hash).
    paths = [root / 'base-image.json', root / 'requirements.txt', root / 'Dockerfile', root / 'tools' / 'prepare.py']
    paths += list((root / 'app').glob('*.py'))
    paths += [p for p in (root / 'rules').rglob('*') if p.is_file()]
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(set(paths))}
