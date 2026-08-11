# AGENTS.md

Guidance for AI coding agents working in `OlympusLedgerOrg/tauri`. See also
[FORK_NOTES.md](./FORK_NOTES.md) for this fork's relationship to upstream.

## What this repo is

This is `OlympusLedgerOrg`'s fork of [`tauri-apps/tauri`](https://github.com/tauri-apps/tauri),
vendored as a dependency of the Olympus desktop app. It carries real,
org-specific commits on top of upstream `dev` — treat it as a maintained
fork, not a passive mirror. Do not discard local patches when syncing with
upstream; see FORK_NOTES.md for the sync procedure.

## Commands

```bash
# Install JS deps + build (run once before anything else)
pnpm install
pnpm build

# Format / lint
pnpm run format:check       # prettier --check .
pnpm run eslint:check       # pnpm run -r eslint:check
pnpm run ts:check           # pnpm run -r ts:check
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings

# Tests
pnpm run test                # JS/TS package tests
cargo test --workspace

# Rust sanity check (fast, no full build)
cargo check --workspace
```

Root `cargo test --workspace` covers the Rust crates and packages/cli's Rust
project; JS/TS packages (`packages/api`, `packages/cli`) are tested via
`pnpm run test`, not Cargo.

## Before committing

- Run `cargo fmt --all` and `cargo clippy --workspace --all-targets -- -D warnings`
  for any Rust change.
- Run `pnpm run format:check` and `pnpm run eslint:check` for any JS/TS change.
- If the change is release-relevant, add a changeset per `.changes/readme.md`
  (this is upstream's own convention, already used throughout the repo).

## Upstream's AI Tool Policy (`.github/CONTRIBUTING.md`)

Upstream explicitly asks that a human review and test all LLM-generated
content before it is submitted, and not use AI to draft review-comment
replies. This applies here too: any AI-assisted change in this fork should
be reviewed by a human before merging, and PR/review replies should not be
auto-generated.

## Fork-specific rules

- **Never force-sync this fork with upstream.** Merge upstream in; see
  FORK_NOTES.md.
- `dev` is a protected branch requiring status checks — changes land via PR,
  not a direct push.
