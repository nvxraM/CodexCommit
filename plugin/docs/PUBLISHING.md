# Publication checklist

Publish this project as **CodexCommit** under the `nvxraM` GitHub account.
The repository contains the source code and a checked candidate ZIP.
The source uses the MIT license.
No JetBrains Marketplace listing or release account has been created by this work.
This checklist describes the additional Marketplace release steps.
It does not promise that publication has no risk.
Run build and publication commands from `plugin/`.

1. Review the source, MIT license, CodexCommit name, icon attribution, privacy notice, security boundaries, and verification results. Confirm the name and retained `local.codex.commitmessage` plugin ID are available and you have the rights to distribute the final content. Rename/reserve the ID before a first public release if Marketplace reports a conflict.
2. Choose your public publisher identity, valid contact email, website, source URL, and hosted privacy-policy URL. Fill the ignored `release.properties` from `docs/release.properties.example` and place the approved vendor/contact metadata into the final descriptor. The current generic contributor vendor is local-build metadata, not a completed Marketplace identity.
3. Decide the publisher's trader/non-trader status and review the Marketplace developer agreement yourself. Public publisher/contact information is a user choice; the build must not invent or expose personal contact details.
4. Run the full core tests, `verifyPluginProjectConfiguration`, `verifyPluginStructure`, and official Plugin Verifier against every claimed IDE target. Keep the supported build range bounded; do not declare compatibility with unreleased platforms merely to avoid future update work.
5. In an isolated IDE sandbox, test install, one-click generation using a harmless synthetic diff, cancellation, timeout, partial-selection refusal, stale changes, typed-message preservation, project close, and dynamic unload/reload. Test Windows, macOS, and Linux before claiming those platforms work. A descriptor/verifier pass is not a live dynamic-unload test.
6. Run a release archive/source scan. Confirm there are no private source files, absolute personal paths, credentials, authentication files, local IDE data, compiled tests, CLI/runtime binaries, logs, or build caches in the release. Inspect both the ZIP and its inner JAR. Record the SHA-256 and the exact source revision separately.
7. Keep the reviewed source revisions in the GitHub repository. The build tools do not make commits or push changes automatically. Publication commits use the GitHub no-reply address.
8. Create the initial Marketplace listing manually using your approved publisher identity and the reviewed ZIP. Read and accept the vendor agreement yourself. Marketplace approval and any security scan are separate checks; approval does not eliminate risk.

The current candidate's execution boundary is personal unmanaged accounts with native Codex 0.160.1 on Windows/Linux; macOS is blocked. Resolve or clearly disclose those restrictions in the listing. A successful fake-provider safety probe does not prove cloud-managed accounts retain the same tool profile. The CI file defines account-free Windows/macOS/Linux build/test jobs, but those hosted jobs have not run merely because the workflow exists. They do not test real model access or native dynamic UI behavior.

The Gradle `publishPlugin` task is deliberately blocked to prevent accidental uploads. Signing and update automation can be added in a later reviewed change after a publisher identity and protected credential handling exist.

Official requirements: [Marketplace approval guidelines](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html), [build compatibility ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html), [Plugin Verifier](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html).
