# Changelog

## 1.1.3 — larger selections and automatic model detection

Accept up to 500 tracked files and 50 MiB of combined before and after text.
Allow a 512 KiB patch. Read files, prepare patches, and verify content in background tasks.
Show file progress. Check cancellation during file reads and patch formatting.
Detect the model saved in Codex CLI settings. Use the CLI default if no model is saved.
Show the detected model before transmission. Retain the restricted tool profile for each model.
Increase the resolution of the example screenshots.
Allow 520 KiB for the complete request. Keep the draft limit at 16 KiB.
Scale the discarded diagnostic stream cap to support larger requests.
Keep the existing content checks and complete request review.

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
