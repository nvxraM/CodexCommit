# Security audit

Date: 15 October 2026.

This audit covers CodexCommit 1.1.0. It covers the source, build tools, release files, and the Codex CLI 0.160.1 interface. It does not certify that the software has no risk. It does not certify compliance with the ASD-STE100 dictionary.


Version 1.1.6 embeds the button screenshot in the plugin Overview. The image loads without a network request.
Release checks reject stale version notes, oversized notes, and changed screenshot bytes.
Generation retains one-click operation. The button sends the selected patch without another confirmation.
It puts the draft directly in the commit message field. It retains content checks and stale-result protection.
Examine selected changes before using the button. Examine the message before committing.
Selected workspace XML stays out of generation. Mixed selections use a fixed generic note; workspace-only selections use a local draft.
Read the [current verification record](VERIFICATION.md#current-candidate) for current results and limits.
The audit below records the earlier 1.1.0 candidate.
## Audit limits

No real Codex/OpenAI credential file was opened. No real Codex/OpenAI key or account token was used in the plugin tests. No private patch was used. No request was sent to a real model service. Tests use synthetic text and local test servers.

The supported execution scope is an unmanaged Windows or Linux computer with a trusted native Codex CLI and the default saved login. Windows was tested. Linux execution still needs acceptance tests. macOS execution is disabled. Managed account policies are outside the supported scope.

## Credential and data paths

| Data | Source | Destination | Control |
| --- | --- | --- | --- |
| Selected source text | Included, tracked local Git changes | Review dialog, then CLI standard input | Exact request review is required before each send. |
| API keys and account tokens | The CLI's own saved login | The CLI's authentication headers and normal login service | The plugin does not open or copy the credential store. |
| Inherited API keys and endpoint overrides | IDE process environment | Excluded from the CLI process | A short environment allowlist removes key, proxy, tracing, and Codex override variables. |
| Generated draft | Bounded CLI standard output | Plain-text review dialog, then the commit message field | A user must choose Apply Draft. Freshness checks protect an existing message. |
| Executable preference | User setting | IDE application settings | Only the executable path is saved. Settings roaming is disabled. |
| Temporary runtime files | Fixed model metadata and CLI diagnostics | Private temporary directory | Ephemeral mode and bounded cleanup apply. Prompts are not placed in command arguments. |

The CLI must use an account credential to authenticate to OpenAI. This normal authentication is separate from the text sent to the model. The plugin cannot promise that credentials never leave the computer. It can avoid reading, displaying, logging, or packaging them itself.

## Source review

- `CodexRunner.java` creates a child process without a command shell. The request uses standard input. The command contains fixed settings and temporary paths. It contains no API key or account token.
- The child environment excludes `OPENAI_API_KEY`, `CODEX_API_KEY`, `CODEX_ACCESS_TOKEN`, `CODEX_HOME`, refresh URL overrides, custom CA overrides, tracing variables, and proxy variables.
- The CLI keeps its own default saved login. It can refresh that login through its normal authentication code. The plugin does not manage login, logout, token refresh, or key storage.
- The model catalog disables shell access and `apply_patch`. The profile also disables image access, browsers, apps, plugins, hooks, skills, memories, and multiple agents.
- CLI version 0.160.1 is required. A different version causes a hard stop. A new version needs a new source and request audit.
- Environment context and project instructions are disabled. The CLI works in a temporary directory outside the repository.
- CLI standard error is not displayed or logged by the plugin. Errors shown in the IDE use fixed text. Model output is plain text. It is not HTML.
- `LocalGitReader.java` uses bounded `ls-tree` and `cat-file` reads. It does not invoke a shell, diff driver, hook, index update, or Git transport. It removes Git environment overrides.
- `ChangeSnapshot.java` checks file size, encoding, path location, link state, partial selection, and stale content. File reads use `NOFOLLOW_LINKS`. Credentials and selected sensitive file types are excluded.
- The plugin has no HTTP client, server, developer telemetry endpoint, automatic download, commit, staging, push, or source-write function.
- Project service disposal cancels the job and interrupts its worker. Child process cleanup has a shared time limit. A volatile worker reference is copied before use to avoid a disposal race.

## Issues found during development

The audit found the following issues. These issues are closed in the reviewed source and tests. The final package must use this source.

| Issue | Risk | Result |
| --- | --- | --- |
| Disabling shell tools did not disable the default `apply_patch` tool. | A model could still receive a file-change tool. | Closed. The fixed catalog has a null patch tool. The actual CLI request has an empty tools array. |
| The CLI added environment context to a request. | The request could contain the temporary path or home path. | Closed. Environment context is disabled. The local request audit found no home-path marker in the body or headers. |
| Repeated reads of the volatile worker reference could race with disposal. | IDE plugin disposal could fail. | Closed. The code copies the worker reference once before interrupting it. |
| A selected file could become a link between capture and reading. | An unintended local file could be read. | Closed for the tested file boundary. The code rechecks its location and rejects a leaf link when opening it. Windows link fixtures are skipped where the OS denies their creation. |
| Lower configuration layers can set service URLs or proxy behavior. | An unsupported configuration could change the credential route. | Closed for the supported configuration. The profile restores the official authentication-aware endpoint defaults. It disables system proxy selection and fallback. Managed policies remain outside the supported scope. |
| Source and binary archive checks accepted broad resource paths. | A later misplaced credential file could enter a release archive. | Closed. Archive tools use explicit path rules. Synthetic credential-path and resource tests pass. |

The final verification record must state which checks passed for the exact package hash. Do not use results from an older package.

## Local security test results

The software test total is 52 passed, 0 failed, and 2 skipped. The core suite has 24 passing cases and 1 skipped link fixture. It includes tests for process limits, cancellation, child termination, strict text decoding, secret rejection, environment filtering, command arguments, and error output. The local Git and patch suite has 8 passing cases and 1 skipped link fixture. Windows denied creation of the skipped fixtures. The archive path tests have 11 passing source cases and 9 passing binary-release cases.

The native CLI test uses a local HTTP server, an empty temporary credential home, and one fake test key. It verifies the following results:

- The CLI sends the selected synthetic text and exposes an empty tool set.
- Unrelated key, account, refresh, revoke, provider, proxy, tracing, and environment markers are absent from the body and headers.
- The fake test key occurs only in the expected Authorization header. It does not occur in the model body or another header.
- Unselected files, ignored configuration, AGENTS text, skill text, and the home path are absent from the model body.
- The CLI returns the synthetic draft from the test server.
- The inspected temporary files contain no selected prompt marker, returned draft, or fake test key before cleanup. The temporary test directory is removed.

These tests do not use a real model. They do not establish that a model will resist prompt injection. They establish the tested process, request, tool, and file boundaries. They do not test real account sign-in or provider-side retention.

The isolated native IDE test passed idle plugin load and unload. Active generation and open-dialog unload still need native acceptance tests.

The final installable ZIP has SHA-256:

`98e332fa2331dfea5bed480e1061051142419ce2aab95d0652ce1b963ea3c7e3`

Its archive scan passes. It contains one project JAR with 26 entries. The official Plugin Verifier 1.410 reports Compatible with PyCharm build `PY-262.10968.92`. It reports probable dynamic plugin eligibility. It reports no plugin compatibility or internal API problem. It runs offline and downloads no dependencies. The local SDK has missing optional layout modules; these produce SDK layout warnings, not plugin findings.

Gitleaks 8.30.1 found no leaks in the clean publication snapshot of 48 explicitly allowed source files. The scan covered approximately 192,408 bytes. Its output uses full redaction. A clean scan does not prove the absence of every possible secret. The publication procedure also requires a staged-file scan and a history scan before a push.

## Build and publication review

The Gradle distribution has a pinned checksum. The IntelliJ build plugin and target SDK have fixed versions. GitHub Actions use commit hashes. The workflow has read-only repository permission and disables stored checkout credentials. It has no signing key, Marketplace token, model credential, or publication job. `publishPlugin` stops with an error.

The installable ZIP must contain one project JAR. The JAR must contain only production classes, approved resources, and the license. Tests, runtimes, authentication files, private paths, and build caches must be excluded. The source ZIP needs a separate audit. `.gitignore` alone does not control ZIP contents.

The source includes the standard Gradle wrapper. Its third-party license notice is separate from the proposed project license. The CLI, Git, JDK, SDK, and build dependencies are separate tools. Their code is not bundled in the installable plugin.

Build tools still require trust. Direct version pins do not prove that every transitive dependency or future build is safe. Keep signing and publication credentials out of source and out of untrusted pull-request builds.

## Remaining risks and release gates

1. Do not use this release with managed Codex accounts or policies. The Send review requires an unmanaged personal-account acknowledgement. This is a user acknowledgement, not a technical account-type check. Known local policy files and a cloud policy cache cause a hard stop. Their absence does not prove that a new cloud policy cannot load. Windows policy paths can also use a relocated system folder. These cases need a native policy probe or an effective-policy API.
2. Use a trusted Codex and Git binary. A native binary header and a version string do not prove publisher identity. Another IDE plugin or a compromised computer has the IDE user's access.
3. Review every request. Pattern checks cannot find all secrets, personal data, or restricted source. Cancellation cannot recall data that the service has received.
4. Review every draft. A source diff can contain prompt injection. An empty tool set prevents the tested tool actions, but it does not make generated text correct.
5. Do not promise secure erasure. OS paging, crash dumps, forced termination, backups, and provider retention are outside the plugin's controls.
6. Test open dialogs, active generation, cancellation, project close, and plugin update or unload in an isolated IDE. An idle unload test does not cover all active states.
7. Review the final publisher identity, contact details, privacy URL, source URL, license, and name before publication. Do not put private contact details or credentials into release metadata by accident.

## Primary source evidence

- [JetBrains plugin access model](https://plugins.jetbrains.com/docs/marketplace/understanding-plugin-security.html)
- [JetBrains dynamic plugin rules](https://plugins.jetbrains.com/docs/intellij/dynamic-plugins.html)
- [JetBrains Marketplace approval rules](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html)
- [Codex 0.160.1 tool registration](https://github.com/openai/codex/blob/rust-v0.160.1/codex-rs/core/src/tools/spec_plan.rs)
- [Codex 0.160.1 configuration merge](https://github.com/openai/codex/blob/rust-v0.160.1/codex-rs/config/src/merge.rs)
- [Codex 0.160.1 configuration layers](https://github.com/openai/codex/blob/rust-v0.160.1/codex-rs/config/src/loader/mod.rs)
- [Codex 0.160.1 provider defaults](https://github.com/openai/codex/blob/rust-v0.160.1/codex-rs/model-provider-info/src/lib.rs)
- [Codex 0.160.1 credential storage schema](https://github.com/openai/codex/blob/rust-v0.160.1/codex-rs/login/src/auth/storage.rs)
- [Codex 0.160.1 token refresh route](https://github.com/openai/codex/blob/rust-v0.160.1/codex-rs/login/src/auth/manager.rs)
