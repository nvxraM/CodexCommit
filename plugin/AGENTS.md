# CodexCommit project instructions

## Commit messages

Use ASD-STE100 Simplified Technical English for Git commit messages.
Use short sentences, the active voice, and one main idea per sentence.
Use consistent technical terms and preserve code identifiers.
Use an imperative subject of 72 characters or fewer.
Separate an optional factual body from the subject with a blank line.

## Documentation

Paths in these instructions are relative to `plugin/`.
Read `docs/WRITING_GUIDE.md` before you change `../README.md`, `docs/DESCRIPTION.md`, or the descriptions in `plugin.xml`.
Use the project rules based on ASD-STE100 Simplified Technical English for these files.
Use short sentences, the active voice, and one instruction per sentence.
Use the glossary terms consistently.

Keep `docs/DESCRIPTION.md` at exactly 50 words.
Use the same text in the first description paragraph of `../README.md`.
Count words as groups of non-whitespace characters.
Keep the plugin Overview separate. Use a brief introduction, three action bullets, the responsive picture, requirements, and help links.
Use a pinned public HTTPS PNG in the descriptor. Marketplace removes inline data and custom HTML IDs.
Bundle the exact bytes from `docs/images/generate-button.png` for offline viewing.
Recognize the exact picture and its link after Marketplace removes IDs. Keep other plugin views unchanged.

Update What's New for every version. Include only the current version and one to three short HTML bullets.
Keep its heading and bullets within 60 words. The release audit rejects stale versions or longer notes.
Keep older changes in `docs/CHANGELOG.md`.

Use the supplied scalable cube artwork for plugin and toolbar icons.
Keep plugin logos and the Overview picture static.
Animate README wordmarks, the button illustration, and the usage demo with the approved cube motion.
In the IDE, animate only the toolbar button during an active job.
Keep the button static in power save and screen reader modes. Stop its timer when hidden or disposed.
Do not execute or bundle the supplied pack's browser code or build scripts.

Preserve all technical requirements, privacy limits, and untested boundaries.
Do not claim zero risk or formal STE certification.
Do not change `../LICENSE` as part of a style edit.

## Scope

Keep machine paths, credentials, account data, and private source files out of published artifacts.
Do not change the installed plugin or another project as part of repository maintenance.
Keep software verification results separate from writing assessments.
