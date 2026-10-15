"""Minimal ASGI HTTP adapter. One uvicorn worker owns the two engine slots."""
import asyncio
import json
import logging
import time

from app.engine import Engine, ScanError, error_result, validate

logging.basicConfig(level=logging.INFO, format='%(levelname)s %(message)s')
engine = Engine()
warmup_task = None
MAX_BODY = 2 * 1024 * 1024


async def warmup():
    try:
        await asyncio.to_thread(engine.initialize)
        await engine.warmup()
    except Exception:
        engine.ready = False
        logging.error('startup validation failed: RULESET_INVALID')


async def respond(send, status, payload):
    data = json.dumps(payload, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    await send({'type': 'http.response.start', 'status': status,
                'headers': [(b'content-type', b'application/json; charset=utf-8'),
                            (b'content-length', str(len(data)).encode()), (b'cache-control', b'no-store')]})
    await send({'type': 'http.response.body', 'body': data})


async def body(receive):
    content = bytearray()
    async with asyncio.timeout(5):
        while True:
            message = await receive()
            if message['type'] == 'http.disconnect':
                raise ScanError('INVALID_REQUEST')
            content.extend(message.get('body', b''))
            if len(content) > MAX_BODY:
                raise ScanError('INVALID_REQUEST')
            if not message.get('more_body', False):
                break
    try:
        # Reject ambiguous duplicate JSON keys, including duplicated codeHash.
        def unique(pairs):
            value = {}
            for key, item in pairs:
                if key in value:
                    raise ValueError('duplicate key')
                value[key] = item
            return value
        return json.loads(content, object_pairs_hook=unique)
    except (ValueError, UnicodeError, RecursionError):
        raise ScanError('INVALID_REQUEST') from None


async def application(scope, receive, send):
    global warmup_task
    if scope['type'] == 'lifespan':
        while True:
            event = await receive()
            if event['type'] == 'lifespan.startup':
                warmup_task = asyncio.create_task(warmup())
                await send({'type': 'lifespan.startup.complete'})
            elif event['type'] == 'lifespan.shutdown':
                if warmup_task is not None and not warmup_task.done():
                    warmup_task.cancel()
                    await asyncio.gather(warmup_task, return_exceptions=True)
                await send({'type': 'lifespan.shutdown.complete'})
                return
        return
    if scope['type'] != 'http':
        return
    path, method = scope['path'], scope['method']
    if (path, method) == ('/api/v1/health', 'GET'):
        await respond(send, 200, {'status': 'alive'})
        return
    if (path, method) == ('/api/v1/capabilities', 'GET'):
        await respond(send, 200 if engine.ready else 503,
                      engine.capabilities() if engine.manifest else error_result('SERVICE_NOT_READY'))
        return
    if (path, method) != ('/api/v1/scan', 'POST'):
        await respond(send, 404, error_result('INVALID_REQUEST'))
        return
    if not engine.ready:
        await respond(send, 503, error_result('SERVICE_NOT_READY'))
        return
    if engine.active >= 2:
        await respond(send, 429, error_result('SERVICE_BUSY'))
        return
    # No await between checking capacity and taking ownership.
    engine.active += 1
    started = time.monotonic()
    try:
        headers = dict(scope.get('headers', []))
        if headers.get(b'content-type', b'').split(b';')[0].strip().lower() != b'application/json':
            raise ScanError('INVALID_REQUEST')
        language, raw = validate(await body(receive))
        result = await engine.scan(language, raw, budget=max(0.01, 20 - (time.monotonic() - started)))
        logging.info('scanId=%s verdict=%s', result['scanId'], result['verdict'])
        await respond(send, 200, result)
    except (ScanError, TimeoutError) as exc:
        await respond(send, 400, error_result(exc.code if isinstance(exc, ScanError) else 'INVALID_REQUEST'))
    except Exception:
        logging.error('request failed: SERVICE_FAILURE')
        await respond(send, 500, error_result('SERVICE_FAILURE', duration=int((time.monotonic() - started) * 1000)))
    finally:
        engine.active -= 1
