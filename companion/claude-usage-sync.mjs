#!/usr/bin/env node
/**
 * Token Pulse — desktop sync for Claude Code usage.
 *
 * Reads Claude Code's local session logs (~/.claude/projects/**\/*.jsonl),
 * optionally your plan limits (5-hour / weekly) using the login Claude Code
 * already has, and publishes a compact JSON snapshot to a *secret* GitHub Gist
 * that the Token Pulse Android app and widgets read.
 *
 * No dependencies. Node 18+.
 *
 *   node claude-usage-sync.mjs setup          # one-time: save GitHub token, create gist
 *   node claude-usage-sync.mjs                # build snapshot + push once
 *   node claude-usage-sync.mjs --watch        # push every N minutes (default 5)
 *   node claude-usage-sync.mjs --print        # print snapshot, don't push
 *   node claude-usage-sync.mjs --out f.json   # write snapshot to a file too
 *   node claude-usage-sync.mjs --no-limits    # skip the plan-limit lookup
 */

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import readline from "node:readline";
import { execFileSync } from "node:child_process";

const VERSION = "1.0.0";
const DEVICE_SLUG = (process.env.TOKEN_PULSE_DEVICE || os.hostname() || "device")
  .toLowerCase()
  .replace(/[^a-z0-9]+/g, "-")
  .replace(/^-+|-+$/g, "") || "device";
// One file per computer, all in the same gist. The app adds them together.
const GIST_FILE = `claude-usage-${DEVICE_SLUG}.json`;
const CONFIG_PATH = path.join(os.homedir(), ".claude-usage-sync.json");
const FIVE_HOURS = 5 * 60 * 60 * 1000;
const DAY = 24 * 60 * 60 * 1000;

// ---------------------------------------------------------------------------
// Pricing (USD per million tokens). Used to show API-equivalent cost.
// First match wins, so keep specific ids above general ones.
// Override or extend in ~/.claude-usage-sync.json → "pricing".
// ---------------------------------------------------------------------------
const DEFAULT_PRICING = [
  { match: ["fable-5-2", "mythos-5-2"], input: 8, output: 40, cacheWrite: 10, cacheRead: 0.2 },
  { match: ["fable", "mythos"], input: 10, output: 50, cacheWrite: 12.5, cacheRead: 0.25 },
  { match: ["opus-5-5"], input: 4, output: 20, cacheWrite: 5, cacheRead: 0.2 },
  { match: ["opus-5", "opus-4-5", "opus-4-6", "opus-4-7", "opus-4-8"], input: 5, output: 25, cacheWrite: 6.25, cacheRead: 0.5 },
  { match: ["opus"], input: 15, output: 75, cacheWrite: 18.75, cacheRead: 1.5 },
  { match: ["sonnet-5-5"], input: 2, output: 10, cacheWrite: 2.5, cacheRead: 0.1 },
  { match: ["sonnet-5"], input: 2, output: 10, cacheWrite: 2.5, cacheRead: 0.2 },
  { match: ["sonnet"], input: 3, output: 15, cacheWrite: 3.75, cacheRead: 0.3 },
  { match: ["haiku-5-5"], input: 0.1, output: 0.5, cacheWrite: 0.125, cacheRead: 0.01 },
  { match: ["haiku-4-5"], input: 1, output: 5, cacheWrite: 1.25, cacheRead: 0.1 },
  { match: ["haiku"], input: 0.8, output: 4, cacheWrite: 1, cacheRead: 0.08 },
];

// ---------------------------------------------------------------------------
// Config
// ---------------------------------------------------------------------------
function loadConfig() {
  try {
    return JSON.parse(fs.readFileSync(CONFIG_PATH, "utf8"));
  } catch {
    return {};
  }
}

function saveConfig(cfg) {
  fs.writeFileSync(CONFIG_PATH, JSON.stringify(cfg, null, 2), { mode: 0o600 });
}

function parseArgs(argv) {
  const args = { cmd: "push", watch: false, print: false, out: null, limits: true, interval: null };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "setup") args.cmd = "setup";
    else if (a === "--watch" || a === "-w") args.watch = true;
    else if (a === "--print" || a === "-p") args.print = true;
    else if (a === "--out") args.out = argv[++i];
    else if (a === "--no-limits") args.limits = false;
    else if (a === "--interval") args.interval = Number(argv[++i]);
    else if (a === "--help" || a === "-h") args.cmd = "help";
    else if (a === "--version") args.cmd = "version";
  }
  return args;
}

// ---------------------------------------------------------------------------
// Locating logs
// ---------------------------------------------------------------------------
function claudeDirs() {
  const dirs = [];
  const env = process.env.CLAUDE_CONFIG_DIR;
  if (env) for (const p of env.split(/[,;]/)) if (p.trim()) dirs.push(p.trim());
  dirs.push(path.join(os.homedir(), ".claude"));
  dirs.push(path.join(os.homedir(), ".config", "claude"));
  return [...new Set(dirs)].filter((d) => fs.existsSync(d));
}

function* walkJsonl(dir) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const e of entries) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) yield* walkJsonl(p);
    else if (e.isFile() && e.name.endsWith(".jsonl")) yield p;
  }
}

function projectName(file, projectsRoot) {
  const rel = path.relative(projectsRoot, file).split(path.sep)[0] || "unknown";
  // Claude Code encodes the cwd as a folder name like "-Users-me-src-my-app"
  // or "C--Users-me-src-my-app". Show the last segment.
  const parts = rel.split("-").filter(Boolean);
  return parts.length ? parts.slice(-2).join("-") : rel;
}

// ---------------------------------------------------------------------------
// Parsing + cost
// ---------------------------------------------------------------------------
function priceFor(model, pricing) {
  const m = (model || "").toLowerCase();
  for (const p of pricing) if (p.match.some((s) => m.includes(s))) return p;
  return null;
}

function costOf(u, model, pricing) {
  const p = priceFor(model, pricing);
  if (!p) return 0;
  return (
    (u.input * p.input + u.output * p.output + u.cacheWrite * p.cacheWrite + u.cacheRead * p.cacheRead) / 1e6
  );
}

function readEntries(pricing, sinceMs) {
  const seen = new Set();
  const out = [];
  for (const root of claudeDirs()) {
    const projectsRoot = path.join(root, "projects");
    if (!fs.existsSync(projectsRoot)) continue;
    for (const file of walkJsonl(projectsRoot)) {
      let stat;
      try {
        stat = fs.statSync(file);
      } catch {
        continue;
      }
      if (stat.mtimeMs < sinceMs) continue; // untouched since the window started
      const project = projectName(file, projectsRoot);
      let text;
      try {
        text = fs.readFileSync(file, "utf8");
      } catch {
        continue;
      }
      for (const line of text.split("\n")) {
        if (!line || line.indexOf('"usage"') === -1) continue;
        let j;
        try {
          j = JSON.parse(line);
        } catch {
          continue;
        }
        const msg = j.message;
        const usage = msg && msg.usage;
        if (!usage || !j.timestamp) continue;
        const ts = Date.parse(j.timestamp);
        if (!Number.isFinite(ts) || ts < sinceMs) continue;
        const key = `${msg.id || ""}:${j.requestId || ""}`;
        if (key !== ":" && seen.has(key)) continue; // same response logged twice
        seen.add(key);
        const model = msg.model || "unknown";
        if (model === "<synthetic>") continue;
        const u = {
          input: usage.input_tokens || 0,
          output: usage.output_tokens || 0,
          cacheWrite: usage.cache_creation_input_tokens || 0,
          cacheRead: usage.cache_read_input_tokens || 0,
        };
        const cost = typeof j.costUSD === "number" ? j.costUSD : costOf(u, model, pricing);
        out.push({ ts, model, project, session: j.sessionId || file, cost, ...u });
      }
    }
  }
  out.sort((a, b) => a.ts - b.ts);
  return out;
}

// ---------------------------------------------------------------------------
// Aggregation
// ---------------------------------------------------------------------------
const localDate = (ms) => {
  const d = new Date(ms);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

function emptyTotals() {
  return { cost: 0, input: 0, output: 0, cacheWrite: 0, cacheRead: 0, tokens: 0, messages: 0 };
}

function add(t, e) {
  t.cost += e.cost;
  t.input += e.input;
  t.output += e.output;
  t.cacheWrite += e.cacheWrite;
  t.cacheRead += e.cacheRead;
  t.tokens += e.input + e.output + e.cacheWrite + e.cacheRead;
  t.messages += 1;
}

function round(t) {
  return { ...t, cost: Math.round(t.cost * 10000) / 10000 };
}

/** Claude Code style 5-hour blocks: start at the hour of the first message. */
function currentBlock(entries, now) {
  let block = null;
  for (const e of entries) {
    if (!block || e.ts >= block.end || e.ts - block.last > FIVE_HOURS) {
      const start = new Date(e.ts);
      start.setMinutes(0, 0, 0);
      block = { start: start.getTime(), end: start.getTime() + FIVE_HOURS, first: e.ts, last: e.ts, totals: emptyTotals() };
    }
    block.last = e.ts;
    add(block.totals, e);
  }
  if (!block || now >= block.end) return null;
  const elapsedH = Math.max((now - block.first) / 3600000, 1 / 6);
  const burn = block.totals.cost / elapsedH;
  const remainingH = (block.end - now) / 3600000;
  return {
    start: new Date(block.start).toISOString(),
    end: new Date(block.end).toISOString(),
    ...round(block.totals),
    burnRatePerHour: Math.round(burn * 100) / 100,
    tokensPerMinute: Math.round(block.totals.tokens / (elapsedH * 60)),
    projectedCost: Math.round((block.totals.cost + burn * remainingH) * 100) / 100,
  };
}

function buildSnapshot(entries, now) {
  const today = localDate(now);
  const monthPrefix = today.slice(0, 7);
  const d30 = now - 30 * DAY;

  const totalsToday = emptyTotals();
  const totalsMonth = emptyTotals();
  const totals30 = emptyTotals();
  const daily = new Map();
  const models = new Map();
  const projects = new Map();
  const sessionsToday = new Set();

  for (let i = 13; i >= 0; i--) daily.set(localDate(now - i * DAY), { cost: 0, tokens: 0 });

  for (const e of entries) {
    const day = localDate(e.ts);
    const tok = e.input + e.output + e.cacheWrite + e.cacheRead;
    if (day === today) {
      add(totalsToday, e);
      sessionsToday.add(e.session);
    }
    if (day.startsWith(monthPrefix)) add(totalsMonth, e);
    if (daily.has(day)) {
      const d = daily.get(day);
      d.cost += e.cost;
      d.tokens += tok;
    }
    if (e.ts >= d30) {
      add(totals30, e);
      const m = models.get(e.model) || { cost: 0, tokens: 0 };
      m.cost += e.cost;
      m.tokens += tok;
      models.set(e.model, m);
      const p = projects.get(e.project) || { cost: 0, tokens: 0 };
      p.cost += e.cost;
      p.tokens += tok;
      projects.set(e.project, p);
    }
  }

  const ranked = (map, n) =>
    [...map.entries()]
      .map(([name, v]) => ({
        name,
        cost: Math.round(v.cost * 100) / 100,
        tokens: v.tokens,
        share: totals30.cost > 0 ? Math.round((v.cost / totals30.cost) * 1000) / 1000 : 0,
      }))
      .sort((a, b) => b.cost - a.cost || b.tokens - a.tokens)
      .slice(0, n);

  return {
    schema: 1,
    source: `token-pulse-sync/${VERSION}`,
    generatedAt: new Date(now).toISOString(),
    host: process.env.TOKEN_PULSE_DEVICE || os.hostname(),
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    plan: null,
    planError: null,
    block: currentBlock(entries, now),
    today: { date: today, sessions: sessionsToday.size, ...round(totalsToday) },
    month: { label: monthPrefix, ...round(totalsMonth) },
    last30: round(totals30),
    daily: [...daily.entries()].map(([date, v]) => ({ date, cost: Math.round(v.cost * 100) / 100, tokens: v.tokens })),
    models: ranked(models, 6),
    projects: ranked(projects, 6),
  };
}

// ---------------------------------------------------------------------------
// Plan limits (5-hour / weekly) — the same numbers Claude Code's /usage shows.
// Uses the access token Claude Code already stored. It never refreshes the
// token itself, so it can't interfere with your Claude Code login.
// ---------------------------------------------------------------------------
function readOauth() {
  for (const dir of claudeDirs()) {
    const f = path.join(dir, ".credentials.json");
    if (fs.existsSync(f)) {
      try {
        const j = JSON.parse(fs.readFileSync(f, "utf8"));
        if (j.claudeAiOauth) return j.claudeAiOauth;
      } catch {}
    }
  }
  if (process.platform === "darwin") {
    try {
      const raw = execFileSync("security", ["find-generic-password", "-s", "Claude Code-credentials", "-w"], {
        encoding: "utf8",
        stdio: ["ignore", "pipe", "ignore"],
      });
      const j = JSON.parse(raw.trim());
      if (j.claudeAiOauth) return j.claudeAiOauth;
    } catch {}
  }
  return null;
}

async function fetchPlan() {
  const oauth = readOauth();
  if (!oauth || !oauth.accessToken) return { error: "No Claude subscription login found (API-key users have no plan limits)." };
  if (oauth.expiresAt && oauth.expiresAt < Date.now())
    return { error: "Claude Code login token expired. Run any `claude` command to refresh it.", tier: oauth.subscriptionType };
  try {
    const res = await fetch("https://api.anthropic.com/api/oauth/usage", {
      headers: {
        Authorization: `Bearer ${oauth.accessToken}`,
        "anthropic-beta": "oauth-2025-04-20",
        "Content-Type": "application/json",
        "User-Agent": `token-pulse-sync/${VERSION}`,
      },
      signal: AbortSignal.timeout(10000),
    });
    if (!res.ok) return { error: `Plan usage request failed (HTTP ${res.status}).`, tier: oauth.subscriptionType };
    const j = await res.json();
    const win = (w) => (w && typeof w.utilization === "number" ? { pct: w.utilization, resetsAt: w.resets_at || null } : null);
    return {
      plan: {
        tier: oauth.subscriptionType || null,
        fiveHour: win(j.five_hour),
        sevenDay: win(j.seven_day),
        sevenDayOpus: win(j.seven_day_opus),
        sevenDaySonnet: win(j.seven_day_sonnet),
        extra:
          j.extra_usage && j.extra_usage.is_enabled
            ? {
                usedCredits: j.extra_usage.used_credits ?? null,
                monthlyLimit: j.extra_usage.monthly_limit ?? null,
                pct: j.extra_usage.utilization ?? null,
              }
            : null,
      },
    };
  } catch (e) {
    return { error: `Plan usage request failed: ${e.message}`, tier: oauth.subscriptionType };
  }
}

// ---------------------------------------------------------------------------
// GitHub Gist
// ---------------------------------------------------------------------------
async function gh(method, url, token, body) {
  const res = await fetch(`https://api.github.com${url}`, {
    method,
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${token}`,
      "X-GitHub-Api-Version": "2022-11-28",
      "User-Agent": `token-pulse-sync/${VERSION}`,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`GitHub ${method} ${url} → HTTP ${res.status}: ${(await res.text()).slice(0, 200)}`);
  return res.json();
}

async function pushGist(cfg, snapshot) {
  const token = process.env.GITHUB_TOKEN || cfg.githubToken;
  if (!token || !cfg.gistId) throw new Error("Not set up yet. Run: node claude-usage-sync.mjs setup");
  await gh("PATCH", `/gists/${cfg.gistId}`, token, {
    files: { [GIST_FILE]: { content: JSON.stringify(snapshot, null, 1) } },
  });
}

function ask(q) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((r) => rl.question(q, (a) => (rl.close(), r(a.trim()))));
}

async function setup() {
  const cfg = loadConfig();
  console.log("\nToken Pulse setup\n");
  console.log("1. Create a GitHub token with only the 'gist' scope:");
  console.log("   https://github.com/settings/tokens/new?scopes=gist&description=Token%20Pulse\n");
  const token = (await ask(`GitHub token${cfg.githubToken ? " (Enter to keep current)" : ""}: `)) || cfg.githubToken;
  if (!token) throw new Error("A token is required.");
  cfg.githubToken = token;
  if (!cfg.gistId) {
    const existing = await ask("Gist ID from your other computer (press Enter if this is your first computer): ");
    if (existing) cfg.gistId = existing.split("/").pop();
  }
  if (!cfg.gistId) {
    const snap = buildSnapshot(readEntries(DEFAULT_PRICING, Date.now() - 31 * DAY), Date.now());
    const gist = await gh("POST", "/gists", token, {
      description: "Token Pulse — Claude Code usage snapshot",
      public: false,
      files: { [GIST_FILE]: { content: JSON.stringify(snap, null, 1) } },
    });
    cfg.gistId = gist.id;
  }
  cfg.intervalMinutes = cfg.intervalMinutes || 5;
  saveConfig(cfg);
  console.log(`\n✔ Saved ${CONFIG_PATH}`);
  console.log(`\n   Gist ID →  ${cfg.gistId}   (this computer: ${DEVICE_SLUG})\n`);
  console.log("   More computers? Run setup on each one and paste this same Gist ID when asked.");
  console.log("2. In the app: Settings → GitHub Gist → paste the Gist ID.");
  console.log("   (Add the same token in the app too if you want faster, unthrottled refreshes.)");
  console.log("3. Keep it fresh: node claude-usage-sync.mjs --watch\n");
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------
async function runOnce(args, cfg) {
  const pricing = [...(cfg.pricing || []), ...DEFAULT_PRICING];
  const now = Date.now();
  const entries = readEntries(pricing, now - 31 * DAY);
  const snap = buildSnapshot(entries, now);
  if (args.limits && cfg.planLimits !== false) {
    const r = await fetchPlan();
    snap.plan = r.plan || null;
    snap.planError = r.error || null;
    if (!snap.plan && r.tier) snap.plan = { tier: r.tier, fiveHour: null, sevenDay: null, sevenDayOpus: null, sevenDaySonnet: null, extra: null };
  }
  if (args.out) fs.writeFileSync(args.out, JSON.stringify(snap, null, 2));
  if (args.print) {
    console.log(JSON.stringify(snap, null, 2));
    return;
  }
  await pushGist(cfg, snap);
  const fh = snap.plan && snap.plan.fiveHour ? ` · 5h ${Math.round(snap.plan.fiveHour.pct)}%` : "";
  console.log(
    `[${new Date().toLocaleTimeString()}] synced · today $${snap.today.cost.toFixed(2)} · ${(snap.today.tokens / 1e6).toFixed(2)}M tok${fh}`
  );
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.cmd === "help") {
    console.log(fs.readFileSync(new URL(import.meta.url), "utf8").split("*/")[0].replace(/^#!.*\n\/\*\*?/, ""));
    return;
  }
  if (args.cmd === "version") return console.log(VERSION);
  if (args.cmd === "setup") return setup();

  const cfg = loadConfig();
  if (!args.watch) return runOnce(args, cfg);
  const minutes = args.interval || cfg.intervalMinutes || 5;
  console.log(`Token Pulse sync: every ${minutes} min. Ctrl+C to stop.`);
  for (;;) {
    try {
      await runOnce(args, cfg);
    } catch (e) {
      console.error(`[${new Date().toLocaleTimeString()}] ${e.message}`);
    }
    await new Promise((r) => setTimeout(r, minutes * 60 * 1000));
  }
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
