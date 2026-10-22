"""Single-process asynchronous orchestration of isolated, bounded CE subprocesses."""
import asyncio
import hashlib
import importlib.metadata
import json
import logging
import os
import platform
from pathlib import Path
import re
import shutil
import signal
import time
import uuid

from app.versioning import fingerprint

ROOT = Path(__file__).resolve().parents[1]
TMP = Path('/tmp/scan')
LANGUAGES = {'GROOVY': ('java', 'script.groovy'), 'PYTHON': ('python', 'script.py'),
             'TYPESCRIPT': ('typescript', 'script.ts')}
FLAGS = ['--oss-only', '--metrics', 'off', '--disable-version-check', '--disable-nosem',
         '--jobs', '1', '--scan-unknown-extensions', '--no-rewrite-rule-ids', '--time',
         '--timeout', '5', '--timeout-threshold', '1', '--max-memory', '768', '--no-git-ignore']
RETRYABLE = {'SCAN_TIMEOUT', 'ENGINE_CRASHED', 'RESOURCE_UNAVAILABLE'}
LOGGER = logging.getLogger('scan')


class ScanError(Exception):
    def __init__(self, code):
        self.code = code
        super().__init__(code)


def error_result(code, scan_id=None, duration=0):
    return {'scanId': scan_id, 'durationMs': duration, 'cached': False,
            'findings': [], 'findingsTruncated': False,
            'error': {'code': code, 'message': code, 'retryable': code in RETRYABLE or code in {'SERVICE_BUSY', 'SERVICE_NOT_READY', 'SERVICE_FAILURE'}}}


def validate(payload):
    if not isinstance(payload, dict) or set(payload) != {'requestId', 'scriptType', 'code', 'codeHash'}:
        raise ScanError('INVALID_REQUEST')
    request_id, language, code, digest = (payload[k] for k in ('requestId', 'scriptType', 'code', 'codeHash'))
    if not isinstance(request_id, str) or not re.fullmatch(r'[A-Za-z0-9_.:-]{1,128}', request_id):
        raise ScanError('INVALID_REQUEST')
    if not isinstance(language, str) or language not in LANGUAGES:
        raise ScanError('UNSUPPORTED_LANGUAGE')
    if not isinstance(code, str) or not code.strip() or '\x00' in code:
        raise ScanError('INVALID_REQUEST')
    try:
        raw = code.encode('utf-8')
    except UnicodeError:
        raise ScanError('INVALID_REQUEST') from None
    if len(raw) > 262144 or not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest):
        raise ScanError('INVALID_REQUEST')
    if hashlib.sha256(raw).hexdigest() != digest:
        raise ScanError('CODE_HASH_MISMATCH')
    return language, raw


def position(raw, value):
    """Map Semgrep UTF-8 byte offset to Unicode code-point column, 1-based."""
    if not isinstance(value, dict):
        return None, None
    offset = value.get('offset')
    if type(offset) is not int or not 0 <= offset <= len(raw):
        return None, None
    try:
        prefix = raw[:offset].decode('utf-8')
    except UnicodeError:
        return None, None
    line = prefix.count('\n') + 1
    column = len(prefix.rsplit('\n', 1)[-1]) + 1
    return line, column


def adapt(report, exit_code, target, raw, expected, blocking):
    if not isinstance(report, dict) or not isinstance(report.get('errors'), list):
        raise ScanError('REPORT_INVALID')
    if report['errors']:
        types = {str(e.get('type', '')) for e in report['errors'] if isinstance(e, dict)}
        if any('Timeout' in t for t in types):
            raise ScanError('SCAN_TIMEOUT')
        if any('PartialParsing' in t for t in types):
            raise ScanError('PARTIAL_PARSE')
        if any('Rule' in t or 'Pattern' in t for t in types):
            raise ScanError('RULESET_INVALID')
        if any('Parse' in t or 'Syntax' in t or 'Lexical' in t for t in types):
            raise ScanError('PARSE_FAILED')
        if any('Memory' in t for t in types):
            raise ScanError('RESOURCE_UNAVAILABLE')
        raise ScanError('REPORT_INVALID')
    if exit_code != 0:
        raise ScanError('ENGINE_CRASHED')
    if report.get('engine_requested') != 'OSS' or report.get('skipped_rules') != []:
        raise ScanError('RULESET_INVALID')
    timing = report.get('time')
    if not isinstance(timing, dict) or set(timing.get('rules', [])) != set(expected):
        raise ScanError('RULESET_INVALID')
    if timing.get('fixpoint_timeouts'):
        raise ScanError('SCAN_TIMEOUT')
    paths = report.get('paths')
    if not isinstance(paths, dict) or paths.get('scanned') != [str(target)]:
        raise ScanError('TARGET_SKIPPED')
    targets = timing.get('targets')
    if not isinstance(targets, list) or not targets or any(t.get('path') != str(target) for t in targets):
        raise ScanError('TARGET_SKIPPED')
    results = report.get('results')
    if not isinstance(results, list):
        raise ScanError('REPORT_INVALID')
    findings = []
    for hit in results:
        if not isinstance(hit, dict) or hit.get('path') != str(target) or hit.get('check_id') not in expected:
            raise ScanError('REPORT_INVALID')
        rule_id = hit['check_id']
        block = blocking.get(rule_id)
        start_line, start_column = position(raw, hit.get('start'))
        end_line, end_column = position(raw, hit.get('end'))
        severity = hit.get('extra', {}).get('severity')
        if severity not in {'INFO', 'WARNING', 'ERROR'}:
            raise ScanError('REPORT_INVALID')
        # Use bundled static descriptions, never interpolated engine messages/code.
        findings.append({'ruleId': rule_id, 'category': block['category'] if block else 'security',
                         'severity': {'ERROR': 'HIGH', 'WARNING': 'MEDIUM', 'INFO': 'LOW'}[severity],
                         'reason': block['reason'] if block else '静态规则匹配到潜在安全风险，需结合调用上下文审核；不代表漏洞已被证实。',
                         'message': block['message'] if block else '命中安全检查规则，请结合规则 ID 审核。',
                         'startLine': start_line, 'startColumn': start_column,
                         'endLine': end_line, 'endColumn': end_column})
    findings.sort(key=lambda f: ({'HIGH': 0, 'MEDIUM': 1, 'LOW': 2}[f['severity']],
                                 f['startLine'] or 0, f['startColumn'] or 0, f['ruleId']))
    return {'findings': findings[:200], 'findingsTruncated': len(findings) > 200}


class Engine:
    def __init__(self):
        self.manifest = None
        self.blocking = None
        self.ready = False
        self.active = 0
        self.processes = set()

    def initialize(self):
        self.ready = False
        TMP.mkdir(parents=True, exist_ok=True)
        for child in TMP.iterdir():
            if child.is_dir() and not child.is_symlink():
                shutil.rmtree(child)
            else:
                child.unlink()
        Path('/tmp/home').mkdir(exist_ok=True)
        manifest = json.loads((ROOT / 'version-manifest.json').read_text())
        actual = fingerprint(ROOT)
        if actual != manifest['inputs']:
            raise ScanError('RULESET_INVALID')
        version = 'sha256:' + hashlib.sha256(json.dumps(actual, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
        if version != manifest['rulesetVersion'] or importlib.metadata.version('semgrep') != manifest['baseImage']['version']:
            raise ScanError('RULESET_INVALID')
        for line in (ROOT / 'requirements.txt').read_text().splitlines():
            if line and not line.startswith('#'):
                name, pinned = line.split('==')
                if importlib.metadata.version(name) != pinned:
                    raise ScanError('RULESET_INVALID')
        blocking = json.loads((ROOT / 'rules' / 'blocking.json').read_text())
        for language, info in manifest['selection'].items():
            rules = json.loads((ROOT / 'rules' / 'active' / (language + '.yaml')).read_text())['rules']
            ids = [r['id'] for r in rules]
            if not ids or set(ids) != set(info['applicableIds']) or not blocking[language] or not set(blocking[language]) <= set(ids):
                raise ScanError('RULESET_INVALID')
            # blocking.json is now a description override, not an admission policy.
            for rule in rules:
                metadata = rule.get('metadata', {})
                cwe = metadata.get('cwe', [])
                if isinstance(cwe, str):
                    cwe = [cwe]
                blocking[language].setdefault(rule['id'], {
                    'category': metadata.get('category', 'security'),
                    'message': rule['message'],
                    'reason': '; '.join(cwe) if cwe else '规则识别到潜在不安全用法，需结合调用上下文审核。',
                })
        self.manifest, self.blocking = manifest, blocking

    def capabilities(self):
        m = self.manifest
        return {'ready': self.ready, 'languages': {k: v[0] for k, v in LANGUAGES.items()},
                'engine': 'Semgrep CE', 'engineVersion': m['baseImage']['version'],
                'baseImageDigest': m['baseImage']['reference'].split('@')[1], 'platform': 'linux/' + {'x86_64': 'amd64', 'aarch64': 'arm64'}.get(platform.machine(), platform.machine()),
                'supportedPlatforms': m['baseImage']['platforms'],
                'serviceBuildId': m['buildId'], 'rulesetVersion': m['rulesetVersion'], 'limits': m['limits'],
                'cached': False}

    async def warmup(self):
        # Check actual parser, rule validity, CLI/report compatibility before readiness.
        samples = {'GROOVY': b'class Smoke { void handle() { Runtime.getRuntime().exec("id"); } }',
                   'PYTHON': b'import os\nos.system("id")\n',
                   'TYPESCRIPT': b'require("child_process").execSync("id");\n'}
        for language, raw in samples.items():
            result = await self.scan(language, raw)
            if 'error' in result or not result['findings']:
                raise ScanError('RULESET_INVALID')
        self.ready = True

    async def scan(self, script_type, raw, budget=None):
        started = time.monotonic()
        scan_id = uuid.uuid4().hex
        directory = TMP / scan_id
        language, filename = LANGUAGES[script_type]
        target, report_file = directory / filename, directory / 'report.json'
        process = None
        drain_task = None
        diagnostic_bytes = diagnostic_lines = 0

        async def drain(stream):
            nonlocal diagnostic_bytes, diagnostic_lines
            # Keep counts only. Content is deliberately discarded, never logged.
            while data := await stream.read(8192):
                diagnostic_bytes = min(1048576, diagnostic_bytes + len(data))
                diagnostic_lines = min(200, diagnostic_lines + data.count(b'\n'))

        async def stop():
            if process is not None:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                await process.wait()

        try:
            directory.mkdir(mode=0o700)
            target.write_bytes(raw)
            env = {k: v for k, v in os.environ.items() if k not in {'SEMGREP_IN_DOCKER', 'SEMGREP_APP_TOKEN', 'SEMGREP_RULES', 'SEMGREP_URL', 'SEMGREP_APP_URL'}}
            env['HOME'] = str(directory)
            env['SEMGREP_SETTINGS_FILE'] = str(directory / 'settings.yml')
            command = ['semgrep', 'scan', *FLAGS, '--config', str(ROOT / 'rules' / 'active' / (language + '.yaml')),
                       '--json', '--output', str(report_file), str(target)]
            deadline = started + min(self.manifest['limits']['totalTimeoutSeconds'], budget if budget is not None else 20)
            engine_deadline = min(deadline, time.monotonic() + self.manifest['limits']['engineTimeoutSeconds'])
            process = await asyncio.create_subprocess_exec(*command, cwd=directory, env=env,
                                                         stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.PIPE,
                                                         start_new_session=True)
            self.processes.add(process)
            drain_task = asyncio.create_task(drain(process.stderr))
            while process.returncode is None:
                if report_file.exists() and report_file.stat().st_size > 4194304:
                    raise ScanError('REPORT_TOO_LARGE')
                if time.monotonic() >= engine_deadline:
                    raise ScanError('SCAN_TIMEOUT')
                await asyncio.sleep(0.02)
            # Kill any descendants even if the CLI parent has already exited.
            await stop()
            await asyncio.wait_for(drain_task, timeout=max(0.01, deadline - time.monotonic()))
            if time.monotonic() >= deadline:
                raise ScanError('SCAN_TIMEOUT')
            if not report_file.exists():
                raise ScanError('ENGINE_CRASHED' if process.returncode else 'REPORT_MISSING')
            if report_file.stat().st_size > 4194304:
                raise ScanError('REPORT_TOO_LARGE')
            try:
                report = json.loads(report_file.read_bytes())
            except (ValueError, UnicodeError):
                raise ScanError('REPORT_INVALID') from None
            if not isinstance(report, dict) or report.get('version') != self.manifest['baseImage']['version']:
                raise ScanError('REPORT_INVALID')
            result = adapt(report, process.returncode, target, raw,
                           self.manifest['selection'][language]['applicableIds'], self.blocking[language])
            if time.monotonic() >= deadline:
                raise ScanError('SCAN_TIMEOUT')
            result.update(scanId=scan_id, durationMs=int((time.monotonic() - started) * 1000), cached=False)
            return result
        except ScanError as exc:
            return error_result(exc.code, scan_id, int((time.monotonic() - started) * 1000))
        except TimeoutError:
            return error_result('SCAN_TIMEOUT', scan_id, int((time.monotonic() - started) * 1000))
        except OSError:
            return error_result('RESOURCE_UNAVAILABLE', scan_id, int((time.monotonic() - started) * 1000))
        except (KeyError, TypeError, ValueError):
            return error_result('REPORT_INVALID', scan_id, int((time.monotonic() - started) * 1000))
        finally:
            await stop()
            if drain_task is not None and not drain_task.done():
                try:
                    await asyncio.wait_for(drain_task, timeout=0.5)
                except TimeoutError:
                    pass
            if process is not None:
                self.processes.discard(process)
            shutil.rmtree(directory, ignore_errors=True)
            LOGGER.info('scanId=%s codeHash=%s rulesetVersion=%s durationMs=%s',
                        scan_id, hashlib.sha256(raw).hexdigest(), self.manifest['rulesetVersion'],
                        int((time.monotonic() - started) * 1000))
