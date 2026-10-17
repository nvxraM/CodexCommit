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
Use the same text in the first XML product description paragraph and the first description paragraph of `../README.md`.
Count words as groups of non-whitespace characters.
Remove HTML tags and normalize whitespace before you compare the XML paragraph.
Keep the Overview button image embedded in the descriptor so it works offline.
Use the exact bytes from `docs/images/generate-button.png`.

Update What's New for every version. Include only the current version and one to three short HTML bullets.
Keep its heading and bullets within 60 words. The release audit rejects stale versions or longer notes.
Keep older changes in `docs/CHANGELOG.md`.

Preserve all technical requirements, privacy limits, and untested boundaries.
Do not claim zero risk or formal STE certification.
Do not change `../LICENSE` as part of a style edit.

## Scope

Keep machine paths, credentials, account data, and private source files out of published artifacts.
Do not change the installed plugin or another project as part of repository maintenance.
Keep software verification results separate from writing assessments.
