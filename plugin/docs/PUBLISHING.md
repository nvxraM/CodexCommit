# Publication checklist

Publish this project as **CodexCommit** under the `nvxraM` GitHub account.
The repository contains the source code and a checked candidate ZIP.
The source uses the MIT license.
The Marketplace listing is [CodexCommit](https://plugins.jetbrains.com/plugin/35249-codexcommit).
Use the [listing guide](MARKETPLACE.md) for its Overview, screenshots, and links.
This checklist describes the release steps.
It does not promise that publication has no risk.
Run build and publication commands from `plugin/`.

1. Review the source, MIT license, CodexCommit name, icon attribution, privacy notice, security boundaries, and verification results. Confirm the name and retained `local.codex.commitmessage` plugin ID are available and you have the rights to distribute the final content. Rename/reserve the ID before a first public release if Marketplace reports a conflict.
2. Choose your public publisher identity, valid contact email, website, source URL, and hosted privacy-policy URL. Fill the ignored `release.properties` from `docs/release.properties.example` and place the approved vendor/contact metadata into the final descriptor. The descriptor uses the approved publisher name `nvxraM` and public contact `nvxram.support@gmail.com`. The privacy notice uses the [public project page](https://github.com/nvxraM/CodexCommit/blob/main/plugin/docs/PRIVACY.md). Check the current repository visibility before publication. Marketplace submission remains separate from GitHub publication.
3. Decide the publisher's trader/non-trader status and review the Marketplace developer agreement yourself. Public publisher/contact information is a user choice; the build must not invent or expose personal contact details.
4. Update What's New for every version. Match its version to `gradle.properties` and the plugin descriptor. Include only changes for that version. Use at most three bullets and 60 words. Keep older changes in `docs/CHANGELOG.md`. Check the public Overview image and the bundled offline copy in the final JAR.
   Check the preview at narrow and wide widths. Click it to open full screen, then close it with Esc.
   Check the aligned Documentation and Privacy links in Additional Info.
   Use the Marketplace native URL fields for Source Code, License, and other standard links. Switch to another plugin and back. Check listener cleanup during removal.
   Check the new static logo in light and dark themes. Use the unchanged default IDE settings to check button animation.
   Check its coloured loading state, completion, cancellation, hidden toolbar, power save, screen reader mode, and removal.
   Turn power save and screen reader modes off during the same active job. Check that motion resumes without a forced repaint.
   Keep the Overview image static. Update the high-resolution GitHub demo with only sample data.
5. Run the full core tests, `verifyPluginProjectConfiguration`, `verifyPluginStructure`, and official Plugin Verifier against every claimed IDE target. Keep the supported build range bounded; do not declare compatibility with unreleased platforms merely to avoid future update work.
6. In an isolated IDE sandbox, test install, one-click generation using a harmless synthetic diff, cancellation, timeout, partial-selection refusal, stale changes, typed-message preservation, project close, and dynamic unload/reload. Test Windows, macOS, and Linux before claiming those platforms work. A descriptor/verifier pass is not a live dynamic-unload test.
7. Run a release archive/source scan. Confirm there are no private source files, absolute personal paths, credentials, authentication files, local IDE data, compiled tests, CLI/runtime binaries, logs, or build caches in the release. Inspect both the ZIP and its inner JAR. Record the SHA-256 and the exact source revision separately.
8. Keep the reviewed source revisions in the GitHub repository. The build tools do not make commits or push changes automatically. Publication commits use the GitHub no-reply address.
9. Upload the checked ZIP to the existing Marketplace listing. Use its owner page for gallery pictures and general information links. A configured Marketplace token can upload updates through the official API. Read any new vendor agreement yourself. Marketplace approval and any security scan are separate checks; approval does not eliminate risk.

The current candidate's execution boundary is personal unmanaged accounts with native Codex 0.160.1 or 0.163.0 on Windows/Linux; macOS is blocked. Resolve or clearly disclose those restrictions in the listing. A successful fake-provider safety probe does not prove cloud-managed accounts retain the same tool profile. The CI file defines account-free Windows/macOS/Linux build/test jobs, but those hosted jobs have not run merely because the workflow exists. They do not test real model access or native dynamic UI behavior.

The Gradle `publishPlugin` task is deliberately blocked to prevent accidental uploads. Signing and update automation can be added in a later reviewed change after a publisher identity and protected credential handling exist.

Official requirements: [Marketplace approval guidelines](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html), [build compatibility ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html), [Plugin Verifier](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html).

## GitHub release workflow

Use the manual **Publish GitHub release** workflow on `main` after the compatibility check and normal CI pass.
Supply the stable plugin version without `v` and its exact 40-character source commit SHA.
The commit must already be on `main`. The descriptor and Gradle version must match the input.

The read-only build job checks, builds and verifies that exact source. It prepares the ZIP, checksum and notes.
A separate release job uses only the built-in temporary GitHub token with `contents: write`.
It does not execute the source code. It verifies the artifact hash, refuses existing tags/releases,
creates the release, and downloads its assets again to verify their bytes and the tag's source SHA.
Only this release job has write permission. Repository defaults and other workflow permissions stay unchanged.
There is no PAT, stored publisher credential, PR release trigger or Marketplace upload in this workflow.

If a run fails after publication, inspect the existing tag, release and assets before recovery.
The workflow does not overwrite or delete existing releases or tags.
GitHub release access follows the current repository visibility.
Marketplace publication still requires the user's configured account, listing and accepted publisher terms.
