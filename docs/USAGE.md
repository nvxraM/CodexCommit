# Use CodexCommit

## Requirements

- Use PyCharm 2026.2.3, build `262.10968.92`, or a compatible build in the `262` branch. Other IDE releases and products have no acceptance result.
- Install native Codex CLI `0.160.1`. This release blocks other CLI versions.
- Use the CLI's default saved login for a personal, unmanaged account. Enterprise and managed account configurations are not supported.
- Configure an absolute path to native Git in the IDE. WSL and script wrappers are not supported.

The plugin detects the model saved in your normal Codex CLI home settings before each request.
Project-specific CLI settings and another process's `--profile` selection are excluded.
If no model is saved, it uses the CLI default. Use `/model` in Codex to change your selection.
The model must be available to your account and supported by the installed CLI.
No separate plugin model setting is necessary.
The plugin cannot detect an unsaved model selection in another running session.
The plugin does not include a CLI, a model, credentials, or an account.

The audited execution profile is for Windows and Linux. Linux generation with a real account remains untested.
This release blocks macOS execution. Its managed device policy controls have not been audited.
The plugin rejects known system or managed Codex configurations. This detection cannot prove that every account or computer is unmanaged.

Open Settings > Tools > CodexCommit to set the Codex executable path.
Leave the field empty to use automatic discovery. Use trusted native Codex and Git executables.

During generation, the plugin does not pass these settings to the CLI process:

- A custom `CODEX_HOME` directory. Model detection uses the CLI's normal saved settings.
- API keys from environment variables.
- Custom model providers.
- Proxy settings from environment variables.
- Remote execution settings.

Use a different integration if your account needs these settings.

## Prepare a commit message

1. Open the Commit tool window.
2. Select complete tracked file changes in one changelist. Include all hunks.
3. Examine the selected changes for private data and secrets.
4. Select Generate Commit Message with CodexCommit once.
5. Wait for the draft in the commit message field.
6. Examine or edit the draft. Make the commit yourself.

The request runs in the background. No request or draft dialog opens.
The button sends the selected patch and fixed instructions to OpenAI through your Codex CLI account.
The request includes filenames, code, and comments. It can include secrets that the automatic checks do not find.
Send only data that you have permission to disclose to OpenAI.
Cancel generation from the IDE background task control.
Cancellation after transmission cannot retract the request.

The prompt requests a subject of at most 50 characters and 1–3 bullet points.
Each bullet has at most 12 words. The complete message has at most 50 words.
These are model instructions. A model can return a different format. Edit the message if necessary.

The CLI sends each approved request to OpenAI through your Codex account.
A request can use account quota or incur charges. The service controls data processing and retention under your account terms.
This plugin does not provide an offline model. Cancellation cannot retract data that the service has received.

The plugin stops if it cannot represent the selected changes safely.
It excludes unversioned files, staged or custom revisions, partial file selections, sensitive filenames, binary files, and symbolic links.
The files and their Git root must be inside the project directory.
Changes with no text difference are not supported.

The plugin preserves your message if you type during generation.
It also prevents draft application if the selected changes or file content change.
It does not stage files, make commits, push changes, or edit source files.
You decide when to make a commit.

## Limits

| Item | Limit |
| --- | --- |
| Selected tracked files | 500 |
| Each file version | 50 MiB, within the combined limit |
| Combined before and after text | 50 MiB |
| Selected patch | 512 KiB |
| Complete request | 520 KiB |
| Draft message | 16 KiB and 100 lines |
| CLI generation time | 120 seconds |

The file size limit applies to each complete file version, even when you change only one line.
Count both the Git baseline and the current file when you calculate the combined limit.
For example, a 20 MiB baseline and a 20 MiB current file use 40 MiB of that limit.
Only the patch and fixed instructions go into the request. The plugin does not truncate an oversized patch.
Select fewer changes if the patch exceeds its limit.
The model can impose a smaller context limit. A 50 MiB file allowance does not permit a 50 MiB request.
Content verification runs in the background. It remains subject to the combined limit.

Add unversioned source files to Git before generation. Leave `.idea/workspace.xml` unchecked.
The plugin excludes IDE settings files. One excluded file stops the complete request.
The warning names the file and tells you to uncheck it in the Commit window.
Uncheck that file. Select Generate Commit Message with CodexCommit again.
The plugin does not change your checked files.

These limits reduce resource use and data exposure. They do not remove all risk.
Filename and content checks cannot find every secret or private record.
Examine the selected changes before you use the button.

The plugin uses a private temporary directory for CLI execution.
It requests a restricted, ephemeral CLI run. It removes temporary files after the run.
It does not store credentials or send data to a developer server. It has no developer telemetry service.
These controls cannot make an untrusted executable safe.
Read [PRIVACY.md](PRIVACY.md) and [security boundaries](../SECURITY.md) for the remaining limits.


## Install with Codex

1. Connect GitHub to Codex.
2. Select [CodexCommit](https://github.com/nvxraM/CodexCommit).
3. Ask: "Help me install CodexCommit in PyCharm. Check README.md and guide me through setup on my computer."
4. Complete the local PyCharm steps that Codex gives you.

Connecting the repository gives Codex access to the setup instructions.
It does not install the local plugin or sign in to your local Codex CLI.

## Manual install

After the update, check the Codex path in **Settings > Tools > CodexCommit**.
Version 1.1.2 introduced a new settings file. Set a custom executable path again if you update from an older version.

Download the [version 1.1.4 ZIP](https://github.com/nvxraM/CodexCommit/blob/v1.1.4/releases/CodexCommit-1.1.4.zip?raw=true).
The release and the source repository contain its [SHA-256 value](../releases/SHA256SUMS).
You can also build the ZIP from the source code.

1. Obtain the plugin ZIP.
2. Open Settings > Plugins.
3. Open the gear menu.
4. Select Install Plugin from Disk.
5. Select the ZIP.
6. Follow the IDE instructions.

The plugin retains the previous local plugin ID, `local.codex.commitmessage`.
It replaces that plugin instead of adding a second action.
The IDE decides whether a restart is necessary.

## Verification and release

Native plugin load and unload passed in an idle PyCharm instance.
Unload during an active job remains untested.
Plugin classloader garbage collection also remains untested.
Read [VERIFICATION.md](VERIFICATION.md) for the exact commands, results, and remaining acceptance tests.

Read [PUBLISHING.md](PUBLISHING.md) before a public release.
The source uses the [MIT license](../LICENSE). Read the license before you distribute the plugin.
Automatic plugin publication is disabled.

The descriptions and this README use project rules based on ASD-STE100 Simplified Technical English.
No formal conformance assessment has been made. See [WRITING_GUIDE.md](WRITING_GUIDE.md) for the writing rules and project terms.
