/**
 * ts-runner：TypeScript 语言脚本 Runner（Node 内置 http + typescript 转译 + vm 沙箱）。
 *
 * 对外契约与 groovy/java/python runner 完全一致：
 *   GET  /health           → {status, language}
 *   POST /validate  {code} → {ok} | {ok:false, error}
 *   POST /functions/handle {functionApi,version,code,input}
 *                          → {data,description,taskId,..., _meta:{durationMs,status,language}}
 *
 * 主应用（ontology-server）不执行任何用户代码，统一 HTTP 调本服务。
 * 安全：vm 独立上下文 + 同步硬超时；仅允许 require Node 内置模块；容器层再兜底隔离。
 */
'use strict';

const http = require('http');
const vm = require('vm');
const crypto = require('crypto');
const { createRequire, builtinModules } = require('module');
const ts = require('typescript');

const PORT = Number(process.env.SERVER_PORT || 39102);
const TIMEOUT_MS = Number(process.env.RUNNER_TIMEOUT_MS || 5000);

const nodeRequire = createRequire(__filename);
const BUILTINS = new Set(builtinModules);

function safeRequire(id) {
  if (BUILTINS.has(id)) {
    return nodeRequire(id);
  }
  throw new Error('禁止 require 非内置模块：' + id);
}

/** 转译产物缓存：codeHash → JS 源码。 */
const transpileCache = new Map();

function sha256(code) {
  return crypto.createHash('sha256').update(code, 'utf8').digest('hex');
}

function transpile(code) {
  const hash = sha256(code);
  const cached = transpileCache.get(hash);
  if (cached) {
    return cached;
  }
  const out = ts.transpileModule(code, {
    reportDiagnostics: true,
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
    },
  });
  const syntaxErrors = (out.diagnostics || [])
    .filter((d) => d.category === ts.DiagnosticCategory.Error)
    .map((d) => ts.flattenDiagnosticMessageText(d.messageText, ' '));
  if (syntaxErrors.length > 0) {
    throw new Error('TypeScript 语法错误：' + syntaxErrors.join('; '));
  }
  transpileCache.set(hash, out.outputText);
  return out.outputText;
}

function hasHandle(code) {
  // 轻量静态检查：允许 export function handle / function handle / const handle =
  return /(?:export\s+)?(?:async\s+)?function\s+handle\b|(?:export\s+)?(?:const|let|var)\s+handle\b/.test(code);
}

function validate(code) {
  transpile(code); // 语法/转译校验，失败抛错
  if (!hasHandle(code)) {
    throw new Error('函数 handle 方法不存在');
  }
}

function runInSandbox(js, input) {
  const sandbox = {
    console,
    Buffer,
    setTimeout,
    clearTimeout,
    require: safeRequire,
    module: { exports: {} },
  };
  sandbox.exports = sandbox.module.exports;
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(js, sandbox, { timeout: TIMEOUT_MS, filename: 'user-script.ts' });
  const handle = sandbox.module.exports.handle || sandbox.handle;
  if (typeof handle !== 'function') {
    throw new Error('函数 handle 方法不存在');
  }
  return handle(input);
}

async function execute(code, input) {
  validate(code);
  const js = transpile(code);
  let result;
  try {
    result = runInSandbox(js, input || {});
  } catch (e) {
    if (e && e.code === 'ERR_SCRIPT_EXECUTION_TIMEOUT') {
      throw new Error(`脚本执行超时（>${TIMEOUT_MS}ms）`);
    }
    throw e;
  }
  // 支持 async handle：thenable 用 Promise.race 加超时
  if (result && typeof result.then === 'function') {
    result = await Promise.race([
      Promise.resolve(result),
      new Promise((_, reject) =>
        setTimeout(() => reject(new Error(`脚本执行超时（>${TIMEOUT_MS}ms）`)), TIMEOUT_MS)
      ),
    ]);
  }
  return result;
}

function normalize(result) {
  if (result && typeof result === 'object' && !Array.isArray(result)) {
    return Object.assign({}, result);
  }
  return { data: result };
}

function sendJson(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      try {
        const raw = Buffer.concat(chunks).toString('utf8');
        resolve(raw ? JSON.parse(raw) : {});
      } catch (e) {
        reject(e);
      }
    });
    req.on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  const url = (req.url || '').split('?')[0];
  try {
    if (req.method === 'GET' && url === '/health') {
      return sendJson(res, 200, { status: 'UP', language: 'typescript' });
    }
    if (req.method === 'POST' && url === '/validate') {
      const body = await readBody(req);
      try {
        validate(String(body.code || ''));
        return sendJson(res, 200, { ok: true });
      } catch (e) {
        return sendJson(res, 200, { ok: false, error: e.message });
      }
    }
    if (req.method === 'POST' && url === '/functions/handle') {
      const body = await readBody(req);
      const start = Date.now();
      let result;
      try {
        result = await execute(String(body.code || ''), body.input || {});
      } catch (e) {
        return sendJson(res, 400, { ok: false, error: e.message });
      }
      const resp = normalize(result);
      resp._meta = { durationMs: Date.now() - start, status: 'ok', language: 'typescript' };
      return sendJson(res, 200, resp);
    }
    return sendJson(res, 404, { ok: false, error: 'not found' });
  } catch (e) {
    return sendJson(res, 500, { ok: false, error: e.message });
  }
});

server.listen(PORT, () => {
  // eslint-disable-next-line no-console
  console.log(`ts-runner listening on :${PORT}`);
});
