# pycharm-codex-commit

This plugin uses Codex in PyCharm to prepare commit messages from selected Git changes. You examine the code before you send it. You examine the message before you use it. The plugin does not make commits or push changes. Each request needs a compatible Codex CLI and an active account.

## Install

Use **PyCharm 2026.2.3**, native **Codex CLI 0.160.1**, and native **Git** on Windows or Linux.
Sign in to Codex with a personal, unmanaged account that can use `gpt-6.1-sol`.

1. [Download the plugin ZIP](releases/DiffQuill-1.1.0.zip?raw=true).
2. In PyCharm, open **Settings → Plugins → ⚙ → Install Plugin from Disk**.
3. Select the ZIP. Restart the IDE if requested.

The plugin appears as **DiffQuill** in PyCharm. [Full requirements](docs/USAGE.md#requirements).

## Use

1. Open **Commit**. Select complete tracked file changes in one changelist.
2. Select the coloured **Generate Commit Message with Codex** button.

![The Codex button in the Commit tool window](docs/images/generate-button.png)

3. Examine the request. Confirm your personal account. Select **Send to Codex**.
4. Examine or edit the draft. Select **Apply Draft**. Make the commit yourself.

| Examine the request | Examine the draft |
| --- | --- |
| [![Request review and Send to Codex button](docs/images/review-request.png)](docs/images/review-request.png) | [![Draft review and Apply Draft button](docs/images/review-draft.png)](docs/images/review-draft.png) |

Select an image to enlarge it. Screens use sample data.
Only send code that you can disclose. Check for secrets. Requests can use quota or incur charges.

[User guide](docs/USAGE.md) · [Build](docs/BUILD.md) · [Privacy](docs/PRIVACY.md) · [Security](SECURITY.md) · [All guides](docs/README.md)

Independent project. OpenAI and JetBrains do not endorse it. [MIT license](LICENSE).
