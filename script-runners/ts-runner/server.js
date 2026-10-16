/**
 * ts-runner：TypeScript 语言脚本 Runner（Node 内置 http + typescript 转译 + vm 沙箱）。
 *
 * 对外契约与 groovy/java/python runner 完全一致：
 *   GET  /health           → {status, language}
 *   POST /validate  {code} → {ok} | {ok:false, error}
 *   POST /parse     {code} → {ok, params:[{name,type,category,source}]} | {ok:false, error}
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

// ============================ 参数静态解析（TypeScript Compiler API） ============================

/** 把一个类型节点归一为标量类型文本（string/number/boolean/array/object），或 ref:类型名。 */
function normalizeTsType(node, src) {
  if (!node) return 'object';
  if (ts.isParenthesizedTypeNode(node)) return normalizeTsType(node.type, src);
  if (node.kind === ts.SyntaxKind.StringKeyword) return 'string';
  if (node.kind === ts.SyntaxKind.NumberKeyword) return 'number';
  if (node.kind === ts.SyntaxKind.BooleanKeyword) return 'boolean';
  if (
    node.kind === ts.SyntaxKind.AnyKeyword ||
    node.kind === ts.SyntaxKind.UnknownKeyword ||
    node.kind === ts.SyntaxKind.ObjectKeyword ||
    node.kind === ts.SyntaxKind.VoidKeyword ||
    node.kind === ts.SyntaxKind.NeverKeyword
  ) {
    return 'object';
  }
  if (ts.isArrayTypeNode(node) || ts.isTupleTypeNode(node)) return 'array';
  if (ts.isUnionTypeNode(node)) {
    const parts = node.types.map((t) => normalizeTsType(t, src));
    if (parts.includes('array')) return 'array';
    if (parts.includes('string')) return 'string';
    if (parts.includes('number')) return 'number';
    if (parts.includes('boolean')) return 'boolean';
    return 'object';
  }
  if (ts.isTypeReferenceNode(node)) {
    const name = node.typeName.getText(src);
    if (/^(Array|ReadonlyArray|Set|Iterable)$/i.test(name)) return 'array';
    if (/^Promise$/i.test(name) && node.typeArguments && node.typeArguments.length) {
      return normalizeTsType(node.typeArguments[0], src);
    }
    return 'ref:' + name;
  }
  if (ts.isLiteralTypeNode(node)) {
    const lit = node.literal;
    if (ts.isStringLiteral(lit)) return 'string';
    if (ts.isNumericLiteral(lit)) return 'number';
    if (lit.kind === ts.SyntaxKind.TrueKeyword || lit.kind === ts.SyntaxKind.FalseKeyword) return 'boolean';
    return 'object';
  }
  return 'object';
}

/** 收集 interface / type=对象字面量 定义的成员（name → 字段列表）。 */
function collectNamedTypes(src) {
  const map = new Map();
  const visit = (node) => {
    let members = null;
    let typeName = null;
    if (ts.isInterfaceDeclaration(node)) {
      members = node.members;
      typeName = node.name.getText(src);
    } else if (ts.isTypeAliasDeclaration(node) && ts.isTypeLiteralNode(node.type)) {
      members = node.type.members;
      typeName = node.name.getText(src);
    }
    if (members) {
      const fields = [];
      members.forEach((m) => {
        if (ts.isPropertySignature(m) && m.name && m.type) {
          fields.push({ name: m.name.getText(src), type: normalizeTsType(m.type, src) });
        }
      });
      map.set(typeName, fields);
    }
    ts.forEachChild(node, visit);
  };
  src.statements.forEach(visit);
  return map;
}

/** ref 命名字段降级为 object，其余保留标量文本。 */
function memberToParam(name, typeText) {
  if (typeText && typeText.startsWith('ref:')) {
    return { name, type: 'object' };
  }
  return { name, type: typeText || 'object' };
}

/** 定位名为 handle 的入口（函数声明 或 const/let handle = 箭头/函数表达式），返回统一描述符。 */
function findHandle(src) {
  for (const st of src.statements) {
    if (ts.isFunctionDeclaration(st) && st.name && st.name.text === 'handle') {
      return { parameters: st.parameters, type: st.type, body: st.body };
    }
    if (ts.isVariableStatement(st)) {
      for (const decl of st.declarationList.declarations) {
        if (ts.isIdentifier(decl.name) && decl.name.text === 'handle'
            && decl.initializer && (ts.isArrowFunction(decl.initializer) || ts.isFunctionExpression(decl.initializer))) {
          const fn = decl.initializer;
          return { parameters: fn.parameters, type: fn.type, body: fn.body };
        }
      }
    }
  }
  return null;
}

/** 展开一个类型节点为字段列表（ref 命中命名类型或内联对象字面量），不能展开返回 null。 */
function expandFields(node, named, src) {
  if (!node) return null;
  const target = ts.isParenthesizedTypeNode(node) ? node.type : node;
  if (ts.isTypeLiteralNode(target)) {
    return target.members
      .filter(ts.isPropertySignature)
      .map((m) => memberToParam(m.name.getText(src), normalizeTsType(m.type, src)));
  }
  if (ts.isTypeReferenceNode(target)) {
    const refName = target.typeName.getText(src);
    if (named.has(refName)) return named.get(refName).map((f) => ({ name: f.name, type: f.type }));
    if (/^Promise$/i.test(refName) && target.typeArguments && target.typeArguments.length) {
      return expandFields(target.typeArguments[0], named, src);
    }
  }
  return null;
}

/** 启发层：扫描函数体里 input.xxx 成员访问与 input["xxx"] 下标访问。 */
function collectBodyProps(fn, paramName) {
  const seen = [];
  const push = (k) => {
    if (k && !seen.includes(k)) seen.push(k);
  };
  const visit = (node) => {
    if (ts.isPropertyAccessExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === paramName) {
      push(node.name.text);
    } else if (ts.isElementAccessExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === paramName) {
      const arg = node.argumentExpression;
      if (arg && ts.isStringLiteral(arg)) push(arg.text);
    }
    ts.forEachChild(node, visit);
  };
  if (fn.body) ts.forEachChild(fn.body, visit);
  return seen;
}

/** 启发层：收集函数体内所有 return 对象字面量的键并集（含守卫分支/多 return），推断类型。 */
function collectReturnKeys(fn) {
  const out = [];
  const seen = new Set();
  const addObjectLiteral = (objLit) => {
    objLit.properties.filter(ts.isPropertyAssignment).forEach((p) => {
      const name = p.name ? p.name.getText() : '';
      if (!name || seen.has(name)) return;
      const init = p.initializer;
      let type = 'object';
      if (init && ts.isStringLiteral(init)) type = 'string';
      else if (init && ts.isNumericLiteral(init)) type = 'number';
      else if (init && (init.kind === ts.SyntaxKind.TrueKeyword || init.kind === ts.SyntaxKind.FalseKeyword)) type = 'boolean';
      else if (init && ts.isArrayLiteralExpression(init)) type = 'array';
      seen.add(name);
      out.push({ name, type });
    });
  };
  const unwrap = (e) => (e && ts.isParenthesizedExpression(e) ? unwrap(e.expression) : e);
  const body = fn.body;
  if (!body) return out;
  if (!ts.isBlock(body)) {
    // 箭头函数表达式体：=> ({ ... })，无显式 return
    const expr = unwrap(body);
    if (ts.isObjectLiteralExpression(expr)) addObjectLiteral(expr);
    return out;
  }
  const visit = (node) => {
    if (ts.isReturnStatement(node) && node.expression) {
      const expr = unwrap(node.expression);
      if (ts.isObjectLiteralExpression(expr)) addObjectLiteral(expr);
    }
    ts.forEachChild(node, visit);
  };
  ts.forEachChild(body, visit);
  return out;
}

/** 静态解析脚本参数，返回 [{name,type,category,source}]。 */
function parse(code) {
  const src = ts.createSourceFile('parse.ts', String(code || ''), ts.ScriptTarget.ES2020, true, ts.ScriptKind.TS);
  const handle = findHandle(src);
  if (!handle) return [];
  const named = collectNamedTypes(src);
  const params = [];

  const first = handle.parameters[0];
  if (first) {
    const fields = first.type ? expandFields(first.type, named, src) : null;
    const paramName = first.name.getText(src);
    if (fields && fields.length > 0) {
      fields.forEach((f) => params.push({ name: f.name, type: f.type, category: 'INPUT', source: 'ANNOTATION' }));
    } else if (first.type) {
      // 有类型但无法展开（any/object/未知 ref）→ 先试标量，否则启发扫 body
      const scalar = normalizeTsType(first.type, src);
      if (scalar && scalar !== 'object' && !scalar.startsWith('ref:')) {
        params.push({ name: paramName, type: scalar, category: 'INPUT', source: 'ANNOTATION' });
      } else {
        collectBodyProps(handle, paramName).forEach((k) =>
          params.push({ name: k, type: 'object', category: 'INPUT', source: 'INFERRED' }));
      }
    } else {
      // 无注解→启发扫描 input.xxx
      collectBodyProps(handle, paramName).forEach((k) =>
        params.push({ name: k, type: 'object', category: 'INPUT', source: 'INFERRED' }));
    }
  }

  if (handle.type) {
    const fields = expandFields(handle.type, named, src);
    if (fields && fields.length > 0) {
      fields.forEach((f) => params.push({ name: f.name, type: f.type, category: 'OUTPUT', source: 'ANNOTATION' }));
    } else {
      const scalar = normalizeTsType(handle.type, src);
      if (scalar && scalar !== 'object' && !scalar.startsWith('ref:')) {
        params.push({ name: 'result', type: scalar, category: 'OUTPUT', source: 'ANNOTATION' });
      } else {
        collectReturnKeys(handle).forEach((k) =>
          params.push({ name: k.name, type: k.type, category: 'OUTPUT', source: 'INFERRED' }));
      }
    }
  } else {
    collectReturnKeys(handle).forEach((k) =>
      params.push({ name: k.name, type: k.type, category: 'OUTPUT', source: 'INFERRED' }));
  }
  return params;
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
    if (req.method === 'POST' && url === '/parse') {
      const body = await readBody(req);
      try {
        const params = parse(String(body.code || ''));
        return sendJson(res, 200, { ok: true, params: params });
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

if (require.main === module) {
  server.listen(PORT, () => {
    // eslint-disable-next-line no-console
    console.log(`ts-runner listening on :${PORT}`);
  });
}

module.exports = { parse, validate, execute };
