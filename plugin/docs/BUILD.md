# Build and test

Use a full JDK 25 for the current IDE SDK. This release targets Java 25.
It does not establish compatibility with older IDEs.

Open `plugin/` as the Gradle project in your IDE.
Run build commands from that directory.
The build can download pinned build tools and the selected IDE SDK.

From the repository root, on Windows:

```powershell
cd plugin
.\gradlew.bat check buildPlugin
```

From the repository root, on Linux or macOS:

```sh
cd plugin
./gradlew check buildPlugin
```

To use an installed IDE SDK, set `CODEXCOMMIT_IDE_HOME` to its installation directory before the build.
On macOS, use the application bundle's `Contents` directory.
Do not put a personal installation path in a tracked file.

The offline helper uses an installed IDE SDK and its JDK. It downloads nothing.
Keep `plugin/` as your working directory for these commands.

Windows:

```powershell
.\tools\build-local.ps1 -IdeHome 'PATH_TO_YOUR_IDE'
```

Linux or macOS:

```sh
sh tools/build-local.sh /path/to/your/IDE
```

The offline helper compiles the source and does the local regression tests.
It puts a new plugin ZIP in `build/local/`.
It does not install the plugin or launch the IDE.
Build tests do not replace native UI acceptance tests.

Both builds check the release version, brief What's New notes, and embedded Overview image.
Update the notes whenever you change `pluginVersion`. Keep one to three bullets within 60 words.

## Repeat compatibility check

Run this procedure for each weekly check. It creates no schedule and publishes nothing.

1. Record `git remote get-url origin`, `git rev-parse HEAD`, and `git status --short`.
2. Check the stable [PyCharm download](https://www.jetbrains.com/pycharm/download/) and
   [Codex release](https://github.com/openai/codex/releases/latest). Exclude EAP and alpha releases.
3. Compare those versions with `gradle.properties` and `CodexRunner`'s exact CLI allowlist.
   Download native CLI binaries from the official release. Check the asset SHA-256 from its release metadata.
4. Use JDK 25. Run `sh gradlew check buildPlugin verifyPluginStructure verifyPluginProjectConfiguration --no-daemon`.
5. Run `sh gradlew verifyPlugin --no-daemon`. Record the exact IDE build and verifier findings.
   For a restricted container, run the verifier JAR directly with a writable `-Duser.home`,
   `check-plugin`, the candidate ZIP, the exact installed SDK path, `-offline`, and `-verification-reports-dir`.
6. For each accepted CLI, run `sh gradlew auditCli -PcodexExecutable=/absolute/path/to/native/codex --no-daemon`.
   Repeat with `-PauditModel=another-valid-model-id` to check model selection independence.
   This runs the real CLI with fake authentication and a loopback response server. It is not a real model test.
7. Before adding a CLI version, review its model schema, configuration and tool registration against the restricted profile.
   Audit the candidate version in an isolated source copy first. Do not accept a version range or bypass strict settings.
8. Test the candidate in an isolated native IDE with synthetic tracked changes: generation, cancellation,
   stale results, typed-message preservation, project close, and unload. Record any missing GUI or account access.
9. Record commands, versions, source revision, ZIP hash, failures, skips, and untested boundaries.
   A build or verifier pass alone does not establish end-to-end compatibility.

The main regression harnesses are `coreTest` and `ideBoundaryTest`. JUnit is disabled.
The CLI audit is opt-in and is not part of `check` or the account-free hosted workflow.
On Linux containers whose PID 1 does not reap children, run Gradle under a child subreaper.
Keep the descendant-termination assertion enabled. A zombie-process failure is not a passing test.
