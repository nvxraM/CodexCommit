# Verification record

Date: 15 October 2026.
Version: 1.1.5.

This record separates software tests from language review.
It does not prove that the plugin has no risk.

## Current candidate

Version 1.1.5 accepts selected `.idea/workspace.xml` as workspace metadata.
The file stays checked for the user's Git commit. Generation never reads its XML body or Git baseline.
It does not capture unsaved workspace text, hash workspace content, or count it toward the source byte budget.
A mixed selection sends the source patch and one fixed generic note about workspace changes.
A workspace-only selection uses a fixed local draft without Codex, model detection, or account calls.
Workspace autosaves do not invalidate a source draft. Source and message freshness checks remain active.
Other sensitive paths, partial selections, links, invalid locations, and source/workspace renames remain blocked.
The README keeps the user's short installation prompt and repository link.

The Linux Gradle check, plugin build, plugin structure check, and project configuration check passed.
The supported-build warning remains. Future IDE versions have no acceptance result.
The checked ZIP has SHA-256:

`9d2f774ab6d05925c2e5c74d29070cec064f51f61d5f32550878bc1381a2fc29`

| Suite | Passed | Failed | Skipped |
| --- | --- | --- | --- |
| Core and credential boundaries | 32 | 0 | 0 |
| Git, patch, path, and selection boundaries | 23 | 0 | 0 |
| Source archive guard | 20 | 0 | 0 |
| Plugin archive guard | 9 | 0 | 0 |
| Total | 84 | 0 | 0 |

The regressions cover the fixed prompt note, safe nested workspace paths, sensitive ancestors,
workspace status and rename boundaries, autosaves, atomic replacement, malformed and oversized omitted content,
missing files, directories, project escapes, and symbolic links.
The ordinary JUnit and searchable settings tasks remain disabled by design.
They are not counted as passed tests.
The official Plugin Verifier 1.410 reports Compatible with `PY-262.10968.92`, offline with no dependency downloads.
The native Codex CLI 0.160.1 loopback audit passed for `gpt-5.4` with the current prompt.
It used synthetic data and a fake credential. The tool array was empty. A real account remains untested.
The current button and draft screenshots remain accurate and retain twice the original pixel density.

### Native workspace checks

An isolated PyCharm 2026.2.3 profile loaded the exact candidate JAR from the checked ZIP.
These tests used real project Git changes and a controlled Commit selection.
A test backend supplied the model and draft. No real account or external model request was used.

| Check | Result |
| --- | --- |
| Workspace-only with a nonexistent Codex path | Applied the fixed local draft. No model detection or generation call. |
| Source plus root and nested workspace files | Sent the exact source diff and fixed note. Workspace paths and canaries were absent. |
| Open unsaved workspace document | Its contents stayed outside the prompt. |
| Workspace autosave and atomic file replacement | Accepted the source draft and preserved the checked selection. |
| Source edit during generation | Refused the stale draft. |
| Message typed during generation | Preserved the typed message. |

The four grouped checks took 406–599 milliseconds with the supplied backend and control polling.
These times do not measure a real model response or guarantee performance.
Each recorded final state had no visible dialog. A later deliberate document/disk conflict opened an IDE-owned cache dialog.
This does not prove that PyCharm never opens its own dialogs.
The candidate toolbar invocation was not completed; the earlier 1.1.4 action result below remains historical.
Gitleaks 8.30.1 found no leaks in the reviewed source snapshot, release directory, or reachable history, with full redaction.

Read the [hosted workflow results](https://github.com/nvxraM/CodexCommit/actions/workflows/check.yml) for each exact commit.
The Ubuntu, Windows, and macOS jobs test source boundaries without account credentials.
They do not prove native macOS generation support; macOS execution remains blocked.

## Previous 1.1.4 candidate

Version 1.1.4 names an excluded file and tells the user to uncheck it in the Commit window.
The warning identifies IDE settings files. The filename exclusions remain unchanged.
One button click prepares and sends the request in background tasks.
The draft appears directly in the commit message field. No request or draft dialog opens.
The prompt requests a subject of at most 50 characters and 1–3 bullet points.
It requests at most 12 words per bullet and 50 words for the complete message.
The README gives Codex setup help first and manual installation last.
Connecting the repository to Codex does not install the plugin in local PyCharm.

The Linux Gradle check, plugin build, plugin structure check, and project configuration check passed.
The supported-build warning remains. Future IDE versions have no acceptance result.
The checked ZIP has SHA-256:

`ee49d82a91fc5dd0510cf4c8dbe6f3eb22cba6074994c8b9c62a0b3d0935ca53`

| Suite | Passed | Failed | Skipped |
| --- | --- | --- | --- |
| Core and credential boundaries | 31 | 0 | 0 |
| Git, patch, path, and selection boundaries | 18 | 0 | 0 |
| Source archive guard | 20 | 0 | 0 |
| Plugin archive guard | 9 | 0 | 0 |
| Total | 78 | 0 | 0 |

The new regressions cover `.idea/workspace.xml`, nested IDE settings, credential filenames,
ordinary Python source, escaped markup, direction controls, bounded notices, and absolute-path refusal.
The ordinary JUnit and searchable settings tasks remain disabled by design.
They are not counted as passed tests.
The native file and CLI checks under the 1.1.3 heading are historical results.
A real account and model response remain untested.

### Native one-click checks

An isolated PyCharm 2026.2.3 profile loaded the exact checked JAR.
A test backend supplied a sample model and draft. No real account or external model request was used.
The actual action, project service, background tasks, Git reads, freshness checks, and message field ran in the IDE.

| Check | Result |
| --- | --- |
| One button click | Filled the commit message field. No dialogs opened. |
| Repeated start during generation | Started one generation only. |
| Message typed during generation | Preserved the typed message. |
| Background task cancellation | Left the message unchanged. |
| Same-size source change with restored timestamp | Refused the stale result. |
| Backend failure | Left the message unchanged. |
| New request after these cases | Filled the message field successfully. |

Both README images were captured at twice the original pixel density from this isolated IDE.
They show the button and the short draft in the message field. They use sample data.
The final ZIP contains no review-dialog class.
The native Codex CLI 0.160.1 loopback audit also passed with `gpt-5.4` and the current prompt.
It used a fake test credential, local server, and synthetic patch. It exposed no tools.
This does not establish real-model output length or real-account generation.

### Hosted checks

The previous main revision, `7c7caab`, passed the Ubuntu, Windows, and macOS jobs.
It includes the symbolic-link cleanup fix. The older failed run used `fc06eec`.
This update pins `actions/upload-artifact` 7.0.2, which uses Node.js 24.
Read the [workflow results](https://github.com/nvxraM/CodexCommit/actions/workflows/check.yml) for each exact commit.

## Previous 1.1.3 candidate

Version 1.1.3 accepts up to 500 tracked files and 50 MiB of combined before and after text.
The patch limit is 512 KiB. The complete request limit is 520 KiB.
The draft limit remains 16 KiB and 100 lines.
The plugin detects the saved local Codex CLI model or uses the CLI default.
It prepares files and verifies content in background tasks with progress and cancellation.

The Linux Gradle check, plugin build, plugin structure check, and project configuration check passed.
The supported-build warning remains. Future IDE versions have no acceptance result.
The checked ZIP has SHA-256:

`a401503b0a5e0d5550fa223ff2e1735c5010c93c82b1b50e869be80d130b7fdc`

| Suite | Passed | Failed | Skipped |
| --- | --- | --- | --- |
| Core and credential boundaries | 31 | 0 | 0 |
| Git, patch, path, and selection boundaries | 15 | 0 | 0 |
| Source archive guard | 20 | 0 | 0 |
| Plugin archive guard | 9 | 0 | 0 |
| Total | 75 | 0 | 0 |

The ordinary JUnit and searchable settings tasks remain disabled by design.
They are not counted as passed tests.
The tests cover exact UTF-8 byte caps, oversized historical files, streamed hashes, cancellation,
model response bounds, invalid model IDs, default selection, and malformed local protocol replies.

### Native file and UI checks

An isolated PyCharm 2026.2.3 profile loaded the candidate on Linux.
These checks used tracked synthetic files and the actual snapshot and patch code.

| Check | Result |
| --- | --- |
| 500 selected files | Patch of 76,280 bytes and both freshness checks passed. |
| 501 selected files | Rejected at selection capture. |
| 25 MiB baseline plus 25 MiB current file | Exact 50 MiB budget passed. Patch of 8,313 bytes passed. |
| 50 MiB plus additional selected content | Rejected before file body preparation. |
| Same-size content change with the original timestamp restored | Background hash verification rejected the old snapshot. |
| Commit message edited after capture | UI freshness verification rejected the old snapshot. |

The 500-file preparation took about 6.3 seconds in this isolated cloud session.
Selection capture took about 6 milliseconds. An EDT test event returned in about 0.15 seconds during preparation.
The exact 50 MiB preparation took about 1.2 seconds. Capture took less than 1 millisecond.
These observations are not performance guarantees for other computers or storage.

The cloud host has system Codex configuration. The existing managed-configuration guard blocked full generation on this host.
No real account or external model request was used. Native real-account generation and active-job unload remain untested.

### Native model and request checks

Native Codex CLI 0.160.1 passed local loopback audits for `gpt-5.4` and `gpt-5.3-codex`.
Each audit detected the saved CLI model and sent a synthetic patch larger than 400 KiB to a local test server.
The received user text matched the reviewed prompt exactly. No truncation occurred.
The request used the detected model and an empty tool array.
Unselected file, instruction, configuration, environment, home-path, and authentication markers were absent from the model body.
The fake test credential appeared only in its expected authentication header.
CLI default model discovery also passed with an isolated local catalog.
These checks do not establish access to every model or test a real model response.

The large request test found an old 256 KiB diagnostic stream cap.
Native CLI prompt echo exceeded that cap. The new cap follows the bounded request size.
Diagnostic text remains discarded. The oversized-output, timeout, and cancellation tests still pass.

### Documentation images

The three examples were recaptured at twice the original pixel density.
Each review image appears below its instruction and links to the full image.
Both review dialogs use supplied sample text. Read [screenshot details](images/README.md).

## Previous 1.1.2 candidate

The 1.1.2 candidate uses CodexCommit for the plugin, packages, UI, and download.
It retains the 1.1.1 fix for whole-file selection when an editor tab is closed.
It retains the coloured icon and ASD-STE100 prompt rules.
Its SHA-256 is:

`5994416a3d81ba73f275d3d35d004d7fe80ea434ceca62fec3384c0f28f49d7c`

The full Gradle check, plugin build, and plugin structure check passed on Linux.

| Suite | Passed | Failed | Skipped |
| --- | --- | --- | --- |
| Core and credential boundaries | 24 | 0 | 0 |
| Git, patch, path, and selection boundaries | 12 | 0 | 0 |
| Source archive guard | 20 | 0 | 0 |
| Plugin archive guard | 9 | 0 | 0 |
| Total | 65 | 0 | 0 |

The IDE boundary suite now includes the SDK classes in its runtime classpath.
The Linux fixture cleanup removes symbolic links without changing their DOS file attributes.
The regular JUnit task and searchable settings tasks remain disabled by design.
They are not counted as passed tests.

### Native selection checks

The previous candidate rejected a checked whole-file change when its editor was closed.
A native PyCharm 2026.2.3 test reproduced that failure.
The same isolated Linux profile then loaded the built 1.1.2 candidate.

| Check | Result |
| --- | --- |
| Checked file with no editor or line tracker | Capture, patch construction, freshness check, and request review passed. |
| Checked file with an open editor and line tracker | Capture, patch construction, freshness check, and request review passed. |
| One of two hunks excluded | Selection rejected. The notification gave the specific cause. No request was sent. |
| Excluded hunk, then editor closed | Tracker retained the exclusion. Selection remained rejected. |
| Cancel request review | The dialog closed. No model request was made. |

These checks used a tracked sample Python file with two separate changes.
A local observer recorded the real toolbar action and tracker state.
The test did not bypass selection checks or open the request dialog directly.
No real account or live model response was tested.
The broader acceptance limits below still apply.

The settings page displays CodexCommit. The plugin ID is unchanged.
The documentation screenshots now show the renamed plugin and use sample data. See [screenshot details](images/README.md).
The following Windows results describe the earlier 1.1.0 candidate.

## Test system

| Item | Version |
| --- | --- |
| Host | Windows, x86-64 |
| PyCharm | 2026.2.3, build PY-262.10968.92 |
| JDK | JetBrains Runtime 25.0.4 |
| Gradle wrapper | 9.1.0, with a distribution checksum |
| IntelliJ build plugin | 2.19.0 |
| Codex CLI | 0.160.1 |
| Official Plugin Verifier | 1.410 |

## Tests

The final Gradle check completed successfully.

| Suite | Passed | Failed | Skipped |
| --- | --- | --- | --- |
| Core and credential boundaries | 24 | 0 | 1 |
| Git, patch, and path boundaries | 8 | 0 | 1 |
| Source archive guard | 11 | 0 | 0 |
| Plugin archive guard | 9 | 0 | 0 |
| Total | 52 | 0 | 2 |

Windows did not permit the two symbolic link test branches.
The ordinary cleanup and file boundary tests passed.
The Gradle structure check and archive scan also passed.

The actual Codex CLI also passed a local request probe.
The probe used an empty temporary account directory and a false test key.
The model request contained an empty tools array.
It did not contain the user home path or the unrelated credential markers.
Only the expected false credential was present in the test authorization header.
The request did not include unselected files, user instructions, or unrelated account markers.
The temporary files did not contain the test prompt, draft, or false credential.
The probe did not use a real account or model service.

The actual PyCharm platform loaded and removed the plugin in a separate test profile.
The action, settings, and idle project service were created successfully.
The action and settings were absent after removal.
This result does not cover an open dialog, an active request, or classloader garbage collection.

The official Plugin Verifier reported compatibility with `PY-262.10968.92`.
It found no plugin compatibility or internal API problem.
It ran offline.

Gitleaks 8.30.1 found no secrets in the reviewed source snapshot.
The scan used full redaction and inspected archives to a depth of two.
The staged source and commit history will receive the same check before the push.

The earlier Windows-tested plugin ZIP contains one project JAR with 26 entries.
Its SHA-256 is:

`98e332fa2331dfea5bed480e1061051142419ce2aab95d0652ce1b963ea3c7e3`

The tests used false keys and temporary test accounts.
No real credential file was read by the audit.
The audit did not send private code or use a real model account.

## Repeat the checks

Set `CODEXCOMMIT_IDE_HOME` to the installed SDK path.
Use JDK 25 for the build.

```powershell
.\gradlew.bat check buildPlugin verifyPluginStructure verifyPluginProjectConfiguration --no-daemon
.\tools\build-local.ps1 -IdeHome 'PATH_TO_YOUR_IDE'
java tools/SourceArchive.java --self-test
java tools/ReleaseAudit.java --self-test
java tools/ReleaseAudit.java build/distributions/CodexCommit-1.1.5.zip
```

The `check` task uses the `coreTest` and `ideBoundaryTest` programs.
It also checks the release archive and the archive guard rules.
There is no JUnit test suite.
The ordinary Gradle `test` task is disabled for this reason.
The searchable settings index tasks are also disabled.
These skipped tasks are not passed tests.

The official verifier uses the installed PyCharm SDK and offline mode.
The separate native lifecycle harness uses temporary IDE directories.
Its generated files are excluded from the public source.

## Compatibility limit

The supported build range starts at `262.10968.92` and ends at `262.*`.
The build tool recommends removal of the upper limit.
This project keeps the limit because a future platform has no verification result.
This is a recorded configuration warning.

## Language review

The description file, XML description, and first README paragraph contain the same 50 words.
The README uses the project rules based on ASD-STE100.
The full official dictionary was not available for this review.
Formal STE conformity is not certified.

## Tests that remain incomplete

- A real account and real model response.
- Active generation during a native IDE update.
- Classloader garbage collection after removal.
- Complete native Linux generation with a real account. macOS execution remains blocked.
- Organization policies and managed accounts.

The existing HiClick repository and installed plugin are separate from this source project.
This work does not change the HiClick source, environment, or Git settings.
