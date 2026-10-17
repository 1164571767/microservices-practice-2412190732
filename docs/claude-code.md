# Claude Code Project Workflow

## Default
- Use normal `.claude/settings.json`.
- Run `/verify` before declaring a meaningful feature complete.
- Use `/security-review` for auth/permission/sensitive-data changes.
- Use `/concurrency-review` for appointment/inventory/payment/idempotency changes.
- Use `/adr` for significant architecture decisions.
- Use `/extract-service` only when assessing a future microservice boundary.

## Strict mode
`settings.strict.example.json` adds a `Stop` hook that runs tests before Claude can stop.

Use strict mode selectively. A Stop hook can continue the agent loop when it blocks, and repeated blocks are capped by Claude Code; it is therefore not a replacement for CI. Keep CI as the final quality gate.
