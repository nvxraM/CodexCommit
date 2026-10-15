# Privacy notice — release candidate

CodexCommit processes the tracked changes you include in the Commit tool window to prepare a commit-message draft. It shows the exact patch for review before every generation request. If you choose to send it, that patch and fixed task instructions are passed to the Codex CLI executable selected on your computer. The CLI communicates using its own authentication. This may send filenames, source code, comments, and any personal or secret information present in the patch to the service used by Codex.

The plugin developer does not receive your patch, result, credentials, account details, or usage data through a developer-operated server. There is no developer telemetry endpoint. The plugin does not read or copy authentication files. Executable-path settings are stored in the IDE's normal application settings, independently of your repository. The IDE's own settings sync, local history, diagnostics, and other plugins are outside this plugin's control.

Before request review, the plugin asks the local Codex CLI for its saved model selection or default model.
This local query does not include source text or start a Codex conversation.
The plugin uses only the reported model for generation. It does not forward other saved CLI instructions or settings.
The CLI may use its normal model metadata service when it resolves a default.

The reviewed patch is held in memory. Generation uses a private temporary working directory and bounded output. Non-sensitive CLI safety configuration may be written there. The plugin requests ephemeral Codex execution and removes its temporary files after completion. OS paging, crash dumps, a forced termination, malware, and CLI/provider-side storage are outside these controls; secure erasure is not promised. A generated message you apply may subsequently be stored in IDE history or in a Git commit when you choose to commit it.

Filename and content checks reject some obvious credentials and unsupported inputs. They cannot detect all secrets, private data, licenses, or organizational restrictions. Review the complete patch and do not send data you lack permission to disclose.

The CLI may use account quota or incur charges under your chosen service. Provider processing and retention depend on your account, settings, and applicable terms. The plugin cannot promise that data never leaves your computer or that provider-side data is deleted by cancelling a request.

Before public release, the publisher must add a real public contact and hosted privacy-policy URL. This draft describes the intended behavior; compare it with the final audited code and verification report before publishing.
