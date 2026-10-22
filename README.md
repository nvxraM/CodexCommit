# CodexCommit

This plugin uses Codex in PyCharm to prepare short commit messages from selected Git changes. Select the files, then select the CodexCommit button. The draft appears in the commit message field. Examine it before you commit. The plugin needs a compatible Codex CLI and an active personal account to work.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="plugin/docs/images/brand/horizontal-dark.webp">
  <source media="(prefers-color-scheme: light)" srcset="plugin/docs/images/brand/horizontal-light.webp">
  <img alt="CodexCommit" src="plugin/docs/images/brand/horizontal-light.webp" width="640">
</picture>

## Install with Codex

Use **PyCharm 2026.2.3** and native **Git** on Windows or Linux.
Use native **Codex CLI 0.160.1 or 0.163.0**.
For a source build, use the [build guide](plugin/docs/BUILD.md).
Use a personal, unmanaged Codex account. The model follows your saved CLI setting or the CLI default.

1. Connect GitHub to Codex. Select [this repository](https://github.com/nvxraM/CodexCommit).
2. Ask Codex:

   > Help me install CodexCommit in PyCharm from github.com/nvxraM/CodexCommit.

Codex guides setup. You install the ZIP in local PyCharm.
Or use [manual installation](#manual-install) below. [Requirements](plugin/docs/USAGE.md#requirements).

## Use

1. Open **Commit**. Select complete tracked file changes in one changelist.
2. Select the coloured **Generate Commit Message with CodexCommit** button.

![The Codex button in the Commit tool window](plugin/docs/images/generate-button.webp)

3. Wait for the draft in the commit message box. Edit it if necessary. Make the commit yourself.

[![Select files, click CodexCommit, and edit the draft](plugin/docs/images/how-to.webp)](plugin/docs/images/how-to.webp)

One click. No review windows. The prompt requests a short subject and 1–3 brief bullet points.
Keep `.idea/workspace.xml` checked if needed. Its contents stay out of generation.

The demo uses sample data. Click it to view full size. [Static guide](plugin/docs/USAGE.md).
In the plugin Overview, click the picture to view it full screen.
The button sends selected code to OpenAI. Check for private data and secrets first.
Requests can use quota or incur charges. Cancel from the IDE background task control.

## Manual install

Search for **CodexCommit** in **Settings → Plugins → Marketplace**. Select **Install**.
Or install the ZIP:

1. [Download version 1.1.12](https://github.com/nvxraM/CodexCommit/releases/download/v1.1.12/CodexCommit-1.1.12.zip).
2. In PyCharm, open **Settings → Plugins → ⚙ → Install Plugin from Disk**.
3. Select the ZIP. Restart the IDE if requested.

[User guide](plugin/docs/USAGE.md) · [Build](plugin/docs/BUILD.md) · [Privacy](plugin/docs/PRIVACY.md) · [Security](.github/SECURITY.md) · [All guides](plugin/docs/README.md)

Independent project. OpenAI and JetBrains do not endorse it. [MIT license](LICENSE).
