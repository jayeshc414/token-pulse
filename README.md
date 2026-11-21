# Token Pulse

A native Android app with home-screen widgets for Claude Code usage: session (5-hour) and weekly plan limits with a live reset countdown, today's and this month's spend, burn rate, a 14-day chart, token mix, and top models and projects.

```
 computer (Claude Code)                     phone
 ┌─────────────────────────┐   secret gist   ┌───────────────────────┐
 │ claude-usage-sync.mjs    │ ─────────────▶ │ Token Pulse app        │
 │  • ~/.claude/projects/*  │  claude-usage  │  • dashboard           │
 │  • plan limits (/usage)  │     .json      │  • 4×2 + 2×2 widgets   │
 └─────────────────────────┘                 │  • limit alerts        │
                                             └───────────────────────┘
```

Claude Code keeps its usage on your computer, so a small script there sums it up and posts the summary to a **secret GitHub Gist** that the phone reads. Only totals are uploaded. No prompts, code or file contents leave the computer.

## 1. Build the APK (no Android Studio)

1. Push this folder to a **private** GitHub repo. It includes a fixed signing key (`app/tokenpulse.keystore`) so new builds install over old ones.
2. Go to **Actions → Build APK → Run workflow**, or just push to `main`.
3. Download the `TokenPulse-apk` artifact, unzip it, and install `app-release.apk` on your phone.

The app opens in **Demo** mode, so the dashboard and widgets have data right away.

## 2. Run the sync on your computer

Requires Node 18 or newer, which Claude Code users already have.

```bash
# one-time: paste a GitHub token that has only the "gist" scope
#   https://github.com/settings/tokens/new?scopes=gist&description=Token%20Pulse
node companion/claude-usage-sync.mjs setup
#   → prints your Gist ID

# keep it fresh (every 5 min)
node companion/claude-usage-sync.mjs --watch
```

In the app, go to **Settings → GitHub Gist**, paste the Gist ID, and tap **Save & sync**. Adding the same token in the app is optional; it raises GitHub's limit for unauthenticated reads (60 per hour).

### Run it automatically

**Windows (Task Scheduler), every 5 minutes:**
```powershell
schtasks /Create /SC MINUTE /MO 5 /TN "TokenPulseSync" /TR "node C:\path\to\claude-usage-sync.mjs"
```

**macOS / Linux (cron):**
```
*/5 * * * * /usr/local/bin/node /path/to/claude-usage-sync.mjs >/dev/null 2>&1
```

Other flags: `--print` (show the JSON and don't upload), `--out file.json`, `--no-limits`, `--interval 10`.

## Where the numbers come from

| Shown | Source |
|---|---|
| Session % / Weekly % / Opus & Sonnet weekly | Claude Code's stored sign-in calls the same usage endpoint that `/usage` uses. The script never refreshes the token, so it can't sign you out. If the token has expired, run any `claude` command and the next sync will pick it up. This endpoint is undocumented and may change. |
| Spend, tokens, burn rate, 5h block, models, projects | Your local `~/.claude/projects/**/*.jsonl` logs (deduplicated per response). |
| $ amounts | API list-price equivalents (Opus 5.5 $4/$20, Sonnet 5.5 $2/$10, Haiku 5.5 $0.10/$0.50 per MTok, plus cache rates). On a Pro/Max plan you aren't billed these. Add or override prices under `"pricing"` in `~/.claude-usage-sync.json`. |

If you sign in with an API key instead of a plan, the limit rings are replaced by the local 5-hour block (time elapsed, cost, forecast).

## Widgets

- **Dashboard (4×2):** dual ring (session outside, weekly inside), live ticking reset countdown, weekly reset day, today's spend and tokens, a 14-day sparkline, and a refresh button.
- **Ring (2×2):** session % with a live countdown.

Rings turn red at 90%. Background refresh runs every 15, 30 or 60 minutes (15 is Android's minimum). The countdown keeps ticking between refreshes.

## Alerts

You get a notification when the session limit passes 75% and 90%, and when the weekly limit passes 80% and 95%. Each one fires once per window. You can turn them off in Settings.

## Self-hosting instead of a gist

Choose **URL** in Settings and point it at any HTTPS URL that serves the JSON from `--out`, such as your own server or a Cloudflare Worker.
