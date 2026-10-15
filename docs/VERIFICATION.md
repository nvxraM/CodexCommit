# Verification record

Date: 15 October 2026.
Version: 1.1.2.

This record separates software tests from language review.
It does not prove that the plugin has no risk.

## Current candidate

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
java tools/ReleaseAudit.java build/distributions/CodexCommit-1.1.2.zip
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
- Active generation and dialog removal during a native IDE update.
- Classloader garbage collection after removal.
- Complete native Linux generation with a real account. macOS execution remains blocked.
- Hosted GitHub Actions, until the workflow runs after the push.
- Organization policies and managed accounts.

The existing HiClick repository and installed plugin are separate from this source project.
This work does not change the HiClick source, environment, or Git settings.
