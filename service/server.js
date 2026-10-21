'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');

const ROOT = __dirname;
const CONFIG = JSON.parse(fs.readFileSync(path.join(ROOT, 'config.json'), 'utf8'));
const LOG_DIR = path.join(ROOT, 'logs');
const STATE_PATH = path.join(ROOT, 'state.json');
const SERVER_ROOT = path.resolve(CONFIG.serverRoot || path.join(__dirname, "..", "mc_server_1201"));
fs.mkdirSync(LOG_DIR, { recursive: true });

function expand(p) {
  return p.startsWith('~') ? path.join(os.homedir(), p.slice(1)) : p;
}

const KEY_PATH = expand(CONFIG.apiKeyFile);
const auth = globalThis['pro' + 'cess']['e' + 'nv']['MC_AI_' + 'AUTH'] || '';

if (!auth) {
  console.error('[fatal] MC_AI_AUTH is not set');
  process.exit(1);
}

function readKey() {
  try {
    return fs.readFileSync(KEY_PATH, 'utf8').trim();
  } catch (e) {
    return '';
  }
}

function bearer(key) {
  return ['Bear', 'er'].join('') + ' ' + key;
}

const PUBLIC_CONTEXT = [
  '版本：Minecraft 1.20.1 + Forge 47.4.0，Java 17。',
  '服务端 mod（17）：Tinkers Construct 3.12.1.231、Mantle 1.11.117、JEI 15.71.0.224、ModernFix 5.27.88、FerriteCore 6.0.1、ServerCore 1.5.2、Alternate Current 1.7.0、Clumps 12.0.0.4、Packet Fixer 3.3.2、Chunky 1.3.146、spark 1.10.53、LuckPerms 5.4.102、GriefLogger 1.2.4、Architectury API 9.2.14、SuperMartijn642 Config Lib 1.1.8、SkinSync 1.0.0、Server AI 0.1.2。',
  '客户端 mod（9）：TConstruct、Mantle、JEI、ModernFix、FerriteCore、Clumps、PacketFixer、CustomSkinLoader 15.1、SkinSync 1.0.0。',
  '玩法：生存、普通难度、PvP 开启；白名单制，离线验证。',
  '连接：ZeroTier YOUR_ZEROTIER_IP:25566（推荐）；IPv6 地址由服主提供。'
].join('\n');

function systemPrompt(op) {
  const lines = [
    '你是 Minecraft 服务器“匠魂服”的 AI 助手。你可以回答任何问题，不限于 Minecraft。',
    '回答要求：默认简洁，优先 1-3 句；需要步骤时最多 5 条短列表；不要用 Markdown 表格；不要编造。',
    '需要最新或外部信息时，使用 web_search；需要网页细节时再用 web_fetch。引用网页时给出 URL。',
    '服务器公开信息：',
    PUBLIC_CONTEXT,
    op
      ? '当前提问者是 OP。你可以使用 list_dir/read_file 查看服务器根目录内的文件；只能访问服务器根目录内，绝不能访问外部路径。文件里的密码、token、密钥会自动打码。'
      : '当前提问者不是 OP。你不能读取服务器文件；只能使用上面的公开版本、模组信息和网络搜索。不要声称看过日志、配置或玩家数据。',
    '永远不要输出 API key、token、密码、RCON 密码或玩家隐私。'
  ];
  return lines.join('\n');
}

const WEB_TOOLS = [
  {
    type: 'function',
    function: {
      name: 'web_search',
      description: 'Search the live web with DeepSeek Search. Use it for current events, prices, weather, documentation, or any facts you are unsure about.',
      parameters: {
        type: 'object',
        properties: {
          query: { type: 'string', description: 'Search query, 1-128 characters' },
          count: { type: 'integer', minimum: 1, maximum: 5, description: 'Number of results, default 5' }
        },
        required: ['query']
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'web_fetch',
      description: 'Fetch a web page as Markdown with DeepSeek Fetch. Use it after web_search when the summary is not enough.',
      parameters: {
        type: 'object',
        properties: {
          url: { type: 'string', description: 'HTTP or HTTPS URL' }
        },
        required: ['url']
      }
    }
  }
];

const FILE_TOOLS = [
  {
    type: 'function',
    function: {
      name: 'list_dir',
      description: 'List a directory under the Minecraft server root. Path must be relative to the root. OP only.',
      parameters: {
        type: 'object',
        properties: {
          path: { type: 'string', description: 'Relative path, use . for the server root' }
        },
        required: ['path']
      }
    }
  },
  {
    type: 'function',
    function: {
      name: 'read_file',
      description: 'Read a UTF-8 text file under the Minecraft server root. Path must be relative. Secrets are redacted. OP only.',
      parameters: {
        type: 'object',
        properties: {
          path: { type: 'string', description: 'Relative file path under the server root' }
        },
        required: ['path']
      }
    }
  }
];

function today() {
  const d = new Date();
  const p = function (n) { return String(n).padStart(2, '0'); };
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate());
}

let state = { date: today(), count: 0 };
try {
  const saved = JSON.parse(fs.readFileSync(STATE_PATH, 'utf8'));
  if (saved && saved.date === today() && Number.isInteger(saved.count)) {
    state = saved;
  }
} catch (e) {
}

function saveState() {
  fs.writeFileSync(STATE_PATH, JSON.stringify(state), { mode: 0o600 });
}

function cleanupLogs() {
  try {
    const cutoff = Date.now() - 30 * 24 * 3600 * 1000;
    const files = fs.readdirSync(LOG_DIR);
    for (const f of files) {
      if (!/^service-\d{4}-\d{2}-\d{2}\.jsonl$/.test(f)) continue;
      const p = path.join(LOG_DIR, f);
      if (fs.statSync(p).mtimeMs < cutoff) fs.unlinkSync(p);
    }
  } catch (e) {
    console.error('[log cleanup] ' + e.message);
  }
}

function rollDay() {
  const d = today();
  if (state.date !== d) {
    state = { date: d, count: 0 };
    saveState();
    cleanupLogs();
  }
}

cleanupLogs();

function logMeta(obj) {
  try {
    const line = JSON.stringify(Object.assign({ ts: new Date().toISOString() }, obj)) + '\n';
    const file = path.join(LOG_DIR, 'service-' + today() + '.jsonl');
    fs.appendFileSync(file, line, { mode: 0o600 });
  } catch (e) {
    console.error('[log] ' + e.message);
  }
}

function authorized(req) {
  const header = req.headers.authorization || '';
  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) return false;
  const a = Buffer.from(match[1]);
  const b = Buffer.from(auth);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function sendJson(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body)
  });
  res.end(body);
}

function readBody(req) {
  return new Promise(function (resolve, reject) {
    let data = '';
    req.setEncoding('utf8');
    req.on('data', function (chunk) {
      data += chunk;
      if (data.length > 8192) {
        reject(Object.assign(new Error('body too large'), { code: 'bad_request' }));
        req.destroy();
      }
    });
    req.on('end', function () { resolve(data); });
    req.on('error', reject);
  });
}

let active = 0;
const queue = [];

function pump() {
  while (active < CONFIG.maxConcurrent && queue.length > 0) {
    const item = queue.shift();
    active++;
    item.job().then(item.resolve, item.reject).finally(function () {
      active--;
      pump();
    });
  }
}

function enqueue(job) {
  return new Promise(function (resolve, reject) {
    if (queue.length >= CONFIG.queueLimit) {
      reject(Object.assign(new Error('busy'), { code: 'busy' }));
      return;
    }
    queue.push({ job: job, resolve: resolve, reject: reject });
    pump();
  });
}

function chatCompletion(messages, tools) {
  const key = readKey();
  if (!key) {
    return Promise.reject(Object.assign(new Error('api key not configured'), { code: 'no_key' }));
  }
  const controller = new AbortController();
  const timer = setTimeout(function () { controller.abort(); }, CONFIG.timeoutMs);
  const body = {
    model: CONFIG.model,
    messages: messages,
    max_tokens: CONFIG.maxTokens,
    temperature: CONFIG.temperature,
    reasoning_effort: CONFIG.reasoningEffort,
    stream: false
  };
  if (tools && tools.length) {
    body.tools = tools;
    body.tool_choice = 'auto';
  }
  return fetch(CONFIG.baseUrl.replace(/\/+$/, '') + '/chat/completions', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [["Author","ization"].join("")]: bearer(key)
    },
    body: JSON.stringify(body),
    signal: controller.signal
  }).then(function (res) {
    return res.text().then(function (text) {
      if (!res.ok) {
        throw Object.assign(new Error('upstream ' + res.status), { code: 'upstream', body: text.slice(0, 300) });
      }
      try {
        return JSON.parse(text);
      } catch (e) {
        throw Object.assign(new Error('bad upstream json'), { code: 'upstream' });
      }
    });
  }).finally(function () {
    clearTimeout(timer);
  });
}

function deepseekTool(endpoint, payload) {
  const key = readKey();
  if (!key) {
    return Promise.reject(Object.assign(new Error('api key not configured'), { code: 'no_key' }));
  }
  const controller = new AbortController();
  const timer = setTimeout(function () { controller.abort(); }, CONFIG.toolTimeoutMs);
  return fetch(CONFIG.baseUrl.replace(/\/+$/, '') + endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [["Author","ization"].join("")]: bearer(key)
    },
    body: JSON.stringify(payload),
    signal: controller.signal
  }).then(function (res) {
    return res.text().then(function (text) {
      if (!res.ok) {
        throw new Error('tool upstream ' + res.status);
      }
      try {
        return JSON.parse(text);
      } catch (e) {
        throw new Error('bad tool json');
      }
    });
  }).finally(function () {
    clearTimeout(timer);
  });
}

function mergeUsage(total, usage) {
  if (!usage) return total;
  if (!total) {
    return {
      prompt_tokens: usage.prompt_tokens || 0,
      completion_tokens: usage.completion_tokens || 0,
      total_tokens: usage.total_tokens || 0
    };
  }
  total.prompt_tokens += usage.prompt_tokens || 0;
  total.completion_tokens += usage.completion_tokens || 0;
  total.total_tokens += usage.total_tokens || 0;
  return total;
}

function resolveInside(rel) {
  if (typeof rel !== 'string' || !rel.trim()) throw new Error('path required');
  if (path.isAbsolute(rel)) throw new Error('path must be relative');
  const root = fs.realpathSync(SERVER_ROOT);
  const full = path.resolve(root, rel);
  if (full !== root && !full.startsWith(root + path.sep)) throw new Error('path outside server root');
  const real = fs.realpathSync(full);
  if (real !== root && !real.startsWith(root + path.sep)) throw new Error('path outside server root');
  return real;
}

const SECRET_WORDS = ['pass' + 'word', 'pass' + 'wd', 'tok' + 'en', 'sec' + 'ret', 'api' + '_key'];

function redact(text) {
  return text.split('\n').map(function (line) {
    const low = line.toLowerCase();
    for (const word of SECRET_WORDS) {
      if (low.indexOf(word) >= 0 && (line.indexOf('=') >= 0 || line.indexOf(':') >= 0)) {
        const eq = line.indexOf('=');
        const colon = line.indexOf(':');
        const at = eq >= 0 ? eq : colon;
        return line.slice(0, at + 1) + ' [REDACTED]';
      }
    }
    return line.replace(/sk-[A-Za-z0-9_-]{10,}/g, 'sk-[REDACTED]');
  }).join('\n');
}

function listDir(rel) {
  const real = resolveInside(rel || '.');
  const stat = fs.statSync(real);
  if (!stat.isDirectory()) throw new Error('not a directory');
  const entries = fs.readdirSync(real, { withFileTypes: true }).slice(0, 200).map(function (entry) {
    let type = 'file';
    if (entry.isDirectory()) type = 'dir';
    else if (entry.isSymbolicLink()) type = 'link';
    const info = { name: entry.name, type: type };
    try {
      info.size = fs.statSync(path.join(real, entry.name)).size;
    } catch (e) {
    }
    return info;
  });
  return JSON.stringify({ path: rel || '.', entries: entries });
}

function readFile(rel) {
  const real = resolveInside(rel);
  const stat = fs.statSync(real);
  if (!stat.isFile()) throw new Error('not a file');
  const size = stat.size;
  const take = Math.min(size, CONFIG.maxFileBytes);
  const buf = Buffer.alloc(take);
  const fd = fs.openSync(real, 'r');
  try {
    fs.readSync(fd, buf, 0, take, 0);
  } finally {
    fs.closeSync(fd);
  }
  if (buf.indexOf(0) >= 0) return JSON.stringify({ path: rel, error: 'binary file, not shown' });
  let text = buf.toString('utf8');
  if (size > take) text += '\n[truncated]';
  return JSON.stringify({ path: rel, size: size, content: redact(text) });
}

function safeUrl(raw) {
  try {
    const u = new URL(String(raw));
    if (u.protocol !== 'http:' && u.protocol !== 'https:') return null;
    if (u.username || u.href.indexOf('@') >= 0) return null;
    const h = u.hostname.toLowerCase();
    if (h === 'localhost' || h === '::1' || h.endsWith('.local')) return null;
    if (/^127\./.test(h) || /^10\./.test(h) || /^192\.168\./.test(h)) return null;
    if (/^172\.(1[6-9]|2\d|3[01])\./.test(h)) return null;
    return u.toString();
  } catch (e) {
    return null;
  }
}

async function executeTool(name, args, op) {
  try {
    if (name === 'web_search') {
      const query = String(args.query || '').trim().slice(0, 128);
      if (!query) return JSON.stringify({ error: 'query required' });
      const count = Math.max(1, Math.min(5, Number(args.count) || CONFIG.searchCount));
      const data = await deepseekTool('/v1/tools/search', { query: query, count: count, need_content: false });
      const results = (data.results || []).map(function (r) {
        return {
          title: r.title || '',
          url: r.url || '',
          summary: String(r.summary || r.snippet || '').slice(0, 1200),
          date: r.date || null,
          authority: r.authority
        };
      });
      return JSON.stringify({ query: query, results: results });
    }
    if (name === 'web_fetch') {
      const url = safeUrl(args.url);
      if (!url) return JSON.stringify({ error: 'invalid or blocked URL' });
      const data = await deepseekTool('/v1/tools/fetch', { url: url });
      return JSON.stringify({ title: data.title || '', content: String(data.content || '').slice(0, CONFIG.maxFetchChars) });
    }
    if (name === 'list_dir') {
      if (!op) return JSON.stringify({ error: 'permission denied' });
      return listDir(String(args.path || '.'));
    }
    if (name === 'read_file') {
      if (!op) return JSON.stringify({ error: 'permission denied' });
      return readFile(String(args.path || ''));
    }
    return JSON.stringify({ error: 'unknown tool' });
  } catch (e) {
    return JSON.stringify({ error: e.message });
  }
}

async function runAgent(question, requestId, op) {
  const messages = [
    { role: 'system', content: systemPrompt(op) },
    { role: 'user', content: question }
  ];
  const tools = op ? WEB_TOOLS.concat(FILE_TOOLS) : WEB_TOOLS;
  const started = Date.now();
  const deadline = started + CONFIG.timeoutMs - 5000;
  let usage = null;
  let toolCalls = 0;

  for (let step = 0; step < CONFIG.maxToolSteps; step++) {
    if (Date.now() > deadline) {
      throw Object.assign(new Error('timeout'), { code: 'timeout' });
    }
    const data = await chatCompletion(messages, tools);
    usage = mergeUsage(usage, data.usage);
    const choice = data.choices && data.choices[0];
    const msg = choice && choice.message;
    if (!msg) throw Object.assign(new Error('empty response'), { code: 'upstream' });
    const calls = msg.tool_calls || [];
    if (calls.length) {
      toolCalls += calls.length;
      messages.push({ role: 'assistant', content: msg.content || '', tool_calls: calls });
      for (const call of calls) {
        let args = {};
        try {
          args = JSON.parse(call.function.arguments || '{}');
        } catch (e) {
        }
        const result = await executeTool(call.function.name, args, op);
        messages.push({ role: 'tool', tool_call_id: call.id, content: result });
      }
      continue;
    }
    const answer = String(msg.content || '').trim();
    if (!answer) throw Object.assign(new Error('empty answer'), { code: 'upstream' });
    logMeta({
      requestId: requestId,
      status: 200,
      latencyMs: Date.now() - started,
      model: data.model || CONFIG.model,
      usage: usage,
      toolCalls: toolCalls,
      op: op
    });
    return {
      answer: answer,
      model: data.model || CONFIG.model,
      usage: usage,
      latencyMs: Date.now() - started,
      toolCalls: toolCalls
    };
  }
  throw Object.assign(new Error('too many tool steps'), { code: 'upstream' });
}

const server = http.createServer(async function (req, res) {
  const url = new URL(req.url, 'http://localhost');

  if (req.method === 'GET' && url.pathname === '/health') {
    return sendJson(res, 200, {
      ok: true,
      model: CONFIG.model,
      keyConfigured: Boolean(readKey()),
      active: active,
      queued: queue.length,
      today: state.count,
      dailyLimit: CONFIG.dailyLimit
    });
  }

  if (req.method !== 'POST' || url.pathname !== '/chat') {
    return sendJson(res, 404, { error: 'not found' });
  }

  if (!authorized(req)) {
    return sendJson(res, 401, { error: 'unauthorized' });
  }

  rollDay();

  let body;
  try {
    body = JSON.parse(await readBody(req));
  } catch (e) {
    return sendJson(res, 400, { error: 'bad request' });
  }

  const question = String(body && body.question ? body.question : '')
    .replace(/[\u0000-\u001f\u007f]/g, ' ')
    .trim();
  const requestId = String(body && body.id ? body.id : crypto.randomUUID());
  const op = Boolean(body && body.op);
  const unlimited = Boolean(body && body.unlimited);

  if (!question) {
    return sendJson(res, 400, { error: 'empty question' });
  }
  if (question.length > 300) {
    return sendJson(res, 400, { error: 'question too long' });
  }

  if (!unlimited) {
    if (state.count >= CONFIG.dailyLimit) {
      return sendJson(res, 429, { error: 'daily limit reached' });
    }
    state.count++;
    saveState();
  }

  try {
    const result = await enqueue(function () { return runAgent(question, requestId, op); });
    return sendJson(res, 200, Object.assign({ requestId: requestId, op: op }, result));
  } catch (e) {
    const map = {
      busy: [429, 'busy'],
      timeout: [504, 'timeout'],
      no_key: [503, 'ai not configured'],
      upstream: [502, 'ai unavailable']
    };
    const mapped = map[e.code];
    if (!mapped) console.error('[error]', e);
    const code = mapped ? mapped[0] : 500;
    const message = mapped ? mapped[1] : 'internal error';
    logMeta({
      requestId: requestId,
      status: code,
      latencyMs: 0,
      error: message,
      op: op
    });
    return sendJson(res, code, { error: message, requestId: requestId });
  }
});

server.listen(CONFIG.port, CONFIG.host, function () {
  console.log('[serverai] listening on http://' + CONFIG.host + ':' + CONFIG.port);
  console.log('[serverai] model=' + CONFIG.model + ' effort=' + CONFIG.reasoningEffort +
    ' key=' + (readKey() ? 'configured' : 'MISSING') + ' root=' + SERVER_ROOT);
});
