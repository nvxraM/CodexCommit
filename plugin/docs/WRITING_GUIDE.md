# Writing guide

Use this guide for the root README, product descriptions, and release descriptions.
Use the same terms in the plugin UI when you change that UI.

These project rules use ASD-STE100 Simplified Technical English as a reference.
The [official introduction](https://www.asd-ste100.org/about_STE.html) describes writing rules, a controlled dictionary, and technical terms.
This project has not completed a formal conformance assessment.
The full official dictionary was not available for this review.
Do not describe this project as certified or fully conformant.

## Writing rules

- Use short sentences.
- Use the active voice.
- Put one instruction in each sentence.
- Use a direct instruction for a user action.
- Use one term for one meaning.
- Use the terms in the glossary below.
- Keep technical facts and safety limits correct.
- State which tests passed.
- State which tests remain incomplete.
- Do not promise zero risk.
- Do not change code, commands, or identifiers to make them look like ordinary words.

Use the official dictionary when it is available.
Use a word only with its approved meaning and part of speech.
Use a technical noun or technical verb only when the subject requires it.
This glossary records project terms. It does not approve arbitrary words under the official standard.

## Project terms

| Term | Use in this project |
| --- | --- |
| account | The user's account for the Codex service. |
| changelist | The IDE group that contains selected Git changes. |
| CLI | Command-line interface. Use `Codex CLI` for the separately installed program. |
| Codex | The OpenAI product that prepares the draft. |
| commit | A Git record. Use `make a commit` for the user action. |
| commit message | The text that describes a Git commit. |
| draft | A proposed commit message that the user must examine. |
| executable | The native program file that the plugin starts. |
| Git | The version control system. Preserve its capitalization. |
| hunk | A group of changed lines in a patch. |
| IDE | Integrated development environment. Use `PyCharm` when the product name is necessary. |
| JDK | Java development kit. The build needs JDK 25. |
| model | The service model that produces the draft. The plugin detects the saved Codex CLI model or uses the CLI default. |
| native | A program that runs directly on the host operating system. |
| patch | The text that describes the selected file changes. |
| plugin | The CodexCommit extension for the IDE. |
| quota | The account allowance that a request can use. |
| repository | The directory and Git history for a project. |
| request | The patch and fixed task instructions sent through the Codex CLI. |
| commit message field | The IDE text box where the draft appears. The user examines it before committing. |
| SDK | Software development kit. The build uses an IDE SDK. |
| stage | The Git action that adds a change to the index. The plugin does not do this action. |
| symbolic link | A filesystem entry that points to another path. |
| tracked file | A file represented by a selected Git change. Unversioned files are excluded. |
| unmanaged account | A personal account without enterprise or administrator configuration. The plugin cannot prove this condition in every case. |
| UTF-8 | The supported text encoding. |

Keep UI labels exact when you quote them.
An example is `Generate Commit Message with CodexCommit`.
Keep code identifiers exact. Examples are `CODEX_HOME`, `CODEXCOMMIT_IDE_HOME`, and `local.codex.commitmessage`.

## Description length

Keep `docs/DESCRIPTION.md` at exactly 50 words.
Use the same text in the first XML product description paragraph and the first description paragraph of the root `README.md`.
Count words as groups of non-whitespace characters.
Remove HTML tags and normalize whitespace before you compare the XML paragraph.
Do not include a heading in `docs/DESCRIPTION.md`.

## Before a release

1. Compare each technical claim with the code and verification report.
2. Examine the sentences against this guide.
3. Examine the terms against the glossary.
4. Count the words in the product description.
5. Confirm that all three description copies match.
6. Record any formal STE review separately from software tests.
