# OlympusLedgerOrg/tauri fork notes

This fork carries real, org-specific commits on top of `tauri-apps/tauri` —
it is not a passive mirror. As of 2026-08-11 it is ~82 commits ahead of
upstream `dev` with local CI/build patches (e.g. Linux dependency setup and
pinned corepack version in `.github/workflows/test-android.yml`).

## Syncing with upstream

- **Never force-sync this fork** (`gh repo sync --force` or equivalent).
  A force-sync overwrites the branch with upstream's history and silently
  discards every local commit.
- To pull in new upstream commits, fetch `upstream/dev` and **merge** it into
  our `dev` (or open a PR doing the same). Resolve conflicts by preferring
  our local patch unless the upstream side is an unrelated bugfix with no
  overlap with our changes.
- `dev` is a protected branch requiring status checks, so a sync lands via PR,
  not a direct push.

See PR #17 for the most recent sync as a worked example.
