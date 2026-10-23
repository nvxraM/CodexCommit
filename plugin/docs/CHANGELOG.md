# Changelog

## 1.1.13 - selected unversioned source files

- Include explicitly selected eligible unversioned files as additions without staging them.
- Reject ignored, sensitive, duplicate or unavailable selections. Recheck pending file state.
- Add synthetic Git and isolated PyCharm API coverage. Record native GUI and provider limits.

## 1.1.12 - selected text preparation

- Decode UTF-8 and BOM-marked UTF-16 with strict validation.
- Keep raw and decoded text limits. Retain binary and sensitive-content checks.
- Report preparation failures by stage. Stop if checked unversioned files would be omitted.
- Find the native CLI in nested npm packages.

## 1.1.11 — Marketplace presentation

- Fix the Overview picture after Marketplace removes custom HTML attributes.
- Use a public HTTPS picture and the same bundled PNG for offline viewing.
- Align Documentation and Privacy links. Use native Source Code and License links.
- State the setup requirements and data transmission in the Overview.
- Link the current public project privacy notice, including public image requests.

## 1.1.10 — public contact and privacy links

- Use `nvxram.support@gmail.com` for publisher and privacy questions.
- Open the public profile repository privacy notice from the Overview and Additional Info links.
- Keep the publisher account, license and source repository visibility unchanged.

## 1.1.9 — Codex compatibility

- Support native Codex CLI 0.163.0. Keep support for 0.160.1.
- Keep other CLI versions blocked until verification is complete.
- Add an explicit native CLI audit task for repeat checks.
- Add the approved publisher contact and privacy notice.

## 1.1.8 — cube motion

- Spin the outer cube during generation.
- Animate README wordmarks and the button picture.
- Refresh the usage demo. Keep plugin logos and the Overview picture static.

## 1.1.7 — new artwork and clearer help

- Use the supplied cube logo throughout the plugin and guides.
- Animate the button during generation. Add a high-resolution usage demo.
- Resize the Overview picture, view it full screen, and open help links.

## 1.1.6 — plugin details

- Show the Commit button in the plugin Overview.
- Keep What's New short and specific to this version.

## 1.1.5 — workspace settings selection

Keep selected `.idea/workspace.xml` checked for the user's Git commit.
Exclude its old, current, and unsaved XML contents from generation, hashes, and size limits.
Send a fixed generic note with source changes. Do not send workspace paths or values.
Use a fixed local draft for workspace-only selections without Codex or account calls.
Retain selection, revision, path, symbolic-link, and regular-file checks.
Allow routine workspace autosaves while the source draft runs. Keep other IDE and credential paths blocked.
Preserve the short installation prompt with the repository link.
Cache Gradle dependencies in CI. Allow 45 minutes for cold builds after a Windows runner timeout.

## 1.1.4 — one-click generation

Run the request in the background after one button click.
Put the draft directly in the commit message field. Remove both review dialogs.
Request a short subject and 1–3 brief bullet points. Retain checks for changed files and typed messages.
Name the excluded file in the warning. Tell the user to uncheck it.
Identify IDE settings files. Keep the existing filename exclusions.
Escape filename markup. Limit the filename length in notifications.
Add Codex setup help. Keep manual installation at the bottom of the README.
Update the artifact upload action to Node.js 24.

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
