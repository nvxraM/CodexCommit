# Security boundaries

No plugin can be guaranteed risk-free. JetBrains plugins run with the IDE user's access; this plugin is not protected by a separate IDE permission sandbox. A malicious binary, compromised update, compromised Codex/Git executable, or compromised build tool can bypass source-level controls. Install trusted programs, inspect the source, and review release provenance.

The Generate button sends selected tracked Git changes through Codex and fills the commit message field. No additional confirmation dialog opens. Check the selected changes before using the button. Controls include limits before loading Git objects, rejection of unsupported partial selections, obvious-secret checks, fixed arguments without a command shell, an empty private CLI working directory, a restricted tool profile, bounded time/output, owned-process cancellation, stale-result checks, and disposal cleanup. Actual verified controls and gaps are listed in [the verification record](docs/VERIFICATION.md). A read-only CLI sandbox alone is not a prohibition on reading unrelated files; CLI tool exposure is separately constrained and audited.

The patch is untrusted task data and may contain prompt injection. Fixed instructions and tool restrictions reduce its influence, but generated text can still be wrong or manipulated. Read the draft in the commit message field before committing it. The action cannot establish whether a change is safe, legally publishable, or accurately described.

No release workflow has a Marketplace token, signing key, account credential, or automatic upload step. Keep future credentials outside source and outside untrusted pull-request jobs. Use protected release procedures and review every dependency/tool update. The Gradle distribution is pinned with a checksum. SDK/build dependencies are not redistributed in the plugin ZIP; only project classes/resources are packaged.

The initial execution profile is restricted to native Codex 0.160.1 and a personal unmanaged account on Windows/Linux. The runner rejects detected local system/managed Codex configuration and rejects macOS. Absence of local policy markers does not prove that an account has no cloud-managed configuration. Higher-priority organizational policy can change CLI behavior; organizational accounts and undetected managed configurations are unsupported, and a universal no-tools guarantee is not made. Automatic model detection uses the local CLI configuration protocol without a conversation or source text. Generation uses a model-specific restricted catalog. The profile/catalog and process environment are tested locally; the selected trusted CLI remains responsible for authentication, backend policy, and its own enforcement.

Before public release, add a private vulnerability-report contact controlled by the publisher. Do not attach real patches, credentials, or private repositories to public issues. A license disclaimer does not guarantee immunity from legal or other responsibilities.

Primary references:

- [JetBrains plugin security](https://plugins.jetbrains.com/docs/marketplace/understanding-plugin-security.html)
- [Dynamic plugin requirements](https://plugins.jetbrains.com/docs/intellij/dynamic-plugins.html)
- [Codex non-interactive execution](https://developers.openai.com/codex/noninteractive)
