# Use CodexCommit

## Requirements

- Use PyCharm 2026.2.3, build `262.10968.92`, or a compatible build in the `262` branch. Other IDE releases and products have no acceptance result.
- Install native Codex CLI `0.160.1`. This release blocks other CLI versions.
- Use the CLI's default saved login for a personal, unmanaged account. Enterprise and managed account configurations are not supported.
- Configure an absolute path to native Git in the IDE. WSL and script wrappers are not supported.

The plugin requests the `gpt-6.1-sol` model. Your account must have access to this model.
The plugin does not include a CLI, a model, credentials, or an account.

The audited execution profile is for Windows and Linux. Linux execution still needs native acceptance tests.
This release blocks macOS execution. Its managed device policy controls have not been audited.
The plugin rejects known system or managed Codex configurations. This detection cannot prove that every account or computer is unmanaged.

Open Settings > Tools > CodexCommit to set the Codex executable path.
Leave the field empty to use automatic discovery. Use trusted native Codex and Git executables.

The plugin does not pass these settings to the CLI process:

- A custom `CODEX_HOME` directory.
- API keys from environment variables.
- Custom model providers.
- Proxy settings from environment variables.
- Remote execution settings.

Use a different integration if your account needs these settings.

## Prepare a commit message

1. Open the Commit tool window.
2. Select the tracked Git files that you want to describe.
3. Select each complete file change in one changelist.
4. Include all hunks for those files.
5. Select Generate Commit Message with CodexCommit.
6. Examine the complete request in the review dialog.
7. Confirm that you use a personal account and a computer without organization policy.
8. Select Send to Codex to send that request.
9. Examine the draft message.
10. Edit the draft if necessary.
11. Select Apply Draft to put the draft in the commit message field.

The request includes the displayed filenames, code, and comments. It can include secrets that the automatic checks do not find.
Send only data that you have permission to disclose to OpenAI.
Select Cancel in the request review dialog to send nothing.

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
| Selected tracked files | 32 |
| Each file version | 64 KiB |
| Combined before and after text | 256 KiB |
| Selected patch | 120 KiB |
| Complete request | 128 KiB |
| Draft message | 16 KiB |
| CLI generation time | 120 seconds |

These limits reduce resource use and data exposure. They do not remove all risk.
Filename and content checks cannot find every secret or private record.
Examine the complete request before each transmission.

The plugin uses a private temporary directory for CLI execution.
It requests a restricted, ephemeral CLI run. It removes temporary files after the run.
It does not store credentials or send data to a developer server. It has no developer telemetry service.
These controls cannot make an untrusted executable safe.
Read [PRIVACY.md](PRIVACY.md) and [security boundaries](../SECURITY.md) for the remaining limits.


## Install

After the update, check the Codex path in **Settings > Tools > CodexCommit**.
This release uses a new settings file. Set a custom executable path again if needed.

The checked [candidate ZIP](../releases/CodexCommit-1.1.2.zip?raw=true) is in the source repository.
The same directory contains its SHA-256 value.
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
Unload during an active job or an open review dialog remains untested.
Plugin classloader garbage collection also remains untested.
Read [VERIFICATION.md](VERIFICATION.md) for the exact commands, results, and remaining acceptance tests.

Read [PUBLISHING.md](PUBLISHING.md) before a public release.
The source uses the [MIT license](../LICENSE). Read the license before you distribute the plugin.
Automatic plugin publication is disabled.

The descriptions and this README use project rules based on ASD-STE100 Simplified Technical English.
No formal conformance assessment has been made. See [WRITING_GUIDE.md](WRITING_GUIDE.md) for the writing rules and project terms.
