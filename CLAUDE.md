@AGENTS.md

Claude-Code-only notes (everything shared lives in `AGENTS.md`):

- Keep replies short and factual; cite `path:line` for code claims.
- Use a todo list for multi-step work, one item in progress at a time.
- Prefer parallel independent tool calls (reads, `git status` + `git log`).
- Never `git commit -a`, amend, push, or rewrite history unless asked.
- On upstream merges, reconcile branding (README `# Excl`, `Excl` log tags,
  Balancer/AWG/VkTurn) before committing — see “Upstream merges”.
