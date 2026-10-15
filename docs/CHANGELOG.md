# Changelog

## 1.1.2 — CodexCommit

Use CodexCommit for the plugin, repository, packages, and download.
Update the settings page, notifications, and review dialog titles.
Retain the plugin ID so this release replaces the existing installation.

## 1.1.1 — selection fix

Accept whole-file changes when the IDE has no active line tracker.
Keep the checks for excluded hunks and split changelists.
Show the specific validation failure when a selection is rejected.
Fix Linux test cleanup for symbolic links.

## 1.1.0 — release candidate

Replaces the local binary-only plugin with portable source, build scripts, neutral artwork, explicit patch review, isolated bounded generation, cancellation, draft review, and offline security regressions. The original plugin ID and commit toolbar action ID are retained for upgrade continuity. Compatibility is bounded to the tested 2026.2 platform branch. Actual verification results and remaining release gates are recorded in VERIFICATION.md.
