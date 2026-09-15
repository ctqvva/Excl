# CLAUDE.md — Excl

This file exists for Claude Code / Claude agents. **The canonical agent
instructions are `AGENTS.md` — read it first and follow it exactly**
(progressive reading contract, thinking protocol, commit-at-every-step,
never-commit list, submodule discipline, upstream-merge procedure).

Claude-specific notes:

- Keep responses short and factual; cite `path:line` for code claims.
- Use `TodoWrite` for multi-step work; exactly one `in_progress` item.
- Prefer parallel independent tool calls (reads, `git status` + `git log`).
- Never commit secrets or build outputs; never `git commit -a`, amend,
  push, or rewrite history unless explicitly asked.
- When an upstream merge touches Excl-branded files, stop and reconcile
  branding (README `# Excl`, `Excl` log tags, Balancer/AWG/VkTurn) before
  committing — see “Merging upstream” in `AGENTS.md`.
