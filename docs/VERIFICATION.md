# Verification record

Date: 15 October 2026.
Version: 1.1.0.

This record separates software tests from language review.
It does not prove that the plugin has no risk.

## Current candidate

The repository candidate includes the coloured ChatGPT icon and ASD-STE100 prompt rules.
Its SHA-256 is:

`0304d78d54962866a4f8ecf022650e00dde4a31fd2215b70469a66b2e738d653`

Linux packaging, the release archive audit, and the plugin structure check passed.
The updated source archive guard passed 20 checks.
The plugin archive guard passed 9 checks.
The core suite passed 24 checks.
The IDE boundary suite passed 9 assertions, then failed during fixture cleanup.
Its cleanup tries to change DOS file attributes on a Linux symbolic link.
The complete `check` task therefore remains failed on this system.

An isolated PyCharm 2026.2.3 profile loaded the plugin for documentation screenshots.
A local harness opened the request and draft dialogs with sample data.
No live Codex request was made.
These screenshots do not complete native Linux workflow acceptance.
See [screenshot details](images/README.md).

The following Windows results describe the earlier candidate.

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

Set `DIFFQUILL_IDE_HOME` to the installed SDK path.
Use JDK 25 for the build.

```powershell
.\gradlew.bat check buildPlugin verifyPluginStructure verifyPluginProjectConfiguration --no-daemon
.\tools\build-local.ps1 -IdeHome 'PATH_TO_YOUR_IDE'
java tools/SourceArchive.java --self-test
java tools/ReleaseAudit.java --self-test
java tools/ReleaseAudit.java build/distributions/DiffQuill-1.1.0.zip
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
- Native Linux or macOS execution.
- Hosted GitHub Actions, until the workflow runs after the push.
- Organization policies and managed accounts.

The existing HiClick repository and installed plugin are separate from this source project.
This work does not change the HiClick source, environment, or Git settings.
