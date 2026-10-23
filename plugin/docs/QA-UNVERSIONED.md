# Selected unversioned file QA

## Candidate status — 23 October 2026

This record covers the 1.1.13 candidate after the published 1.1.12 baseline.
The initial tests used temporary 1.1.12 metadata. That temporary ZIP must not be published.
Version 1.1.13 was unused when its metadata was prepared. Final checks follow the five rounds below.

Selected eligible unversioned files are represented as additions in the generated patch.
The plugin does not stage them. It does not silently omit an ineligible selected file.
Tracked and unversioned selections share the 500-file limit and existing source, encoding,
binary, secret, project-boundary, symlink, combined-content and patch-size checks.
The unversioned path also rejects IDE settings, IDE-ignored files, and files excluded by
repository Git ignore rules. Native Git runs with the existing sanitized configuration;
personal global Git configuration is not loaded. The IDE ignore check supplements this.
Files that become tracked or ignored while a result is pending invalidate that result.

## Automated checks

The full Gradle `check buildPlugin` completed successfully:

| Suite | Passed | Failed | Optional skips |
| --- | ---: | ---: | ---: |
| Core | 34 | 0 | 1 |
| IDE boundary | 46 | 0 | 1 |
| Source archive guard | 39 | 0 | 0 |
| Release guard | 62 | 0 | 0 |

Both skips concern symbolic links that this Windows host could not create.
The boundary tests include exact unversioned additions, duplicate and directory refusal,
the selection-count limit, literal pathspec handling, ignored/tracked-file refusal and
unchanged Git index content. Existing tests retain the mixed UTF-16 baseline/UTF-8
working-copy regression and source, subprocess, privacy and size protections.

`verifyPluginStructure` passed. `verifyPluginProjectConfiguration` completed with the
existing warning about the bounded upper build. The supported range remains bounded.
Official Plugin Verifier 1.411 reported **Compatible** against **PY-262.10968.92**.
These checks cover the candidate, not a new published release.

## Isolated PyCharm API exercise

A test-only ApplicationStarter ran in the actual PyCharm application with separate
config, system, plugin, log and home directories. It opened a disposable synthetic Git
project. A proxy supplied CommitWorkflowUi selections and an actual IDE Document held
the commit message. A deterministic backend supplied responses without authentication
or a provider request. The test extension was added only to the sandbox plugin copy.
It is not part of the distribution.

Two completed corrected runs each passed **53 assertions, zero failures**: **106/106**.
Each run repeated the six service-response scenarios five times. Coverage included:

- Service startup, empty selection, modified/added/deleted/renamed files and the original
  mixed-encoding baseline.
- Mixed tracked/unversioned selection, unversioned-only selection and empty `.gitkeep`.
- Unchanged index and untracked status; selection removal, disk edits and staging while pending.
- Ignored, sensitive, binary, malformed, secret-containing and oversized unversioned content.
- Selected-only prompt content, valid response application, empty/control-containing/oversized
  response refusal, backend error, missing executable and commit-message preservation.
- A reentrant start while the service is busy, plus a user edit before the backend returns.
- Service disposal during generation, cancellation signalling and subsequent project closure.
- A real local Git commit and push to a disposable bare remote, with unselected files still untracked.

The original baseline harness and raw result files are kept outside the repository in
the task QA workspace. No research repository or installed plugin was changed.

## Five additional rounds

These rounds are separate from the two baseline runs. Each round reran four shared
regressions: exact selected addition with unchanged index, selection freshness, binary
refusal and empty `.gitkeep`. The remaining assertions focused on a distinct risk.

| Round | Focus | Passed | Failed |
| --- | --- | ---: | ---: |
| 1 | Spaces, Unicode, UTF-8 BOM and UTF-16LE/BE BOM | 9 | 0 |
| 2 | Project escape, missing file, workspace, Git private exclude, duplicate | 9 | 0 |
| 3 | Discovery failure/cancellation, request cancellation/timeout/protocol error | 9 | 0 |
| 4 | Five reentrant starts and selection changes during discovery | 9 | 0 |
| 5 | Ten disk-freshness iterations plus shared regressions | 14 | 0 |

There were 50 valid assertions across these five rounds. No new plugin defect was
confirmed. Round 2 first recorded eight passes and one fixture setup failure: the empty
Git template had not created `.git/info`. The harness now creates that directory before
writing its synthetic exclude file. The corrected full round passed. A source-write
encoding failure also prevented an attempted round extension; the surviving baseline
class ran instead. That run is excluded from the additional-round denominators.

The maintained round source is `src/test/java/dev/codexcommit/ide/IdeApiRounds.java`.
It is an opt-in ApplicationStarter, not a normal test-suite entry point. To reproduce:

1. Build the production plugin and compile test sources against the same PyCharm SDK.
2. In a disposable plugin copy only, include this class and its nested classes. Register
   an `appStarter` extension with implementation `dev.codexcommit.ide.IdeApiRounds`.
   Do not distribute that copy.
3. Launch the SDK using its `product-info.json` boot classpath and VM arguments, with
   headless and command-line modes enabled. Supply a fresh writable `codexcommit.qa.root`.
   Set `idea.config.path`, `idea.system.path`, `idea.plugins.path`, `idea.log.path` and
   `user.home` to separate directories under that root. The harness enforces this boundary.
4. Supply the absolute native Git path as `codexcommit.qa.git`, and choose
   `codexcommit.qa.round=1` through `5`. Invoke the registered starter ID.
5. Inspect `result-round-N.txt`, process exit code and IDE logs. Keep each result.
   Run the full Gradle suite and official verifier against the final production ZIP.

The harness uses reflection to inject an account-free backend in its isolated service.
Its synthetic timeout exception checks service error handling. Actual elapsed subprocess
timeouts are covered separately by the core suite.

## Invalid trials and remaining limits

An earlier harness run recorded 47 passes and five invalid timing failures. Its final
closure test stalled and only its isolated JVM was stopped. Headless PyCharm executes
the Backgroundable tasks synchronously: two sequential completed starts are not two
concurrent clicks. Those five trials do not measure plugin failure rate and are excluded
from the corrected results. Earlier setup attempts also needed an opened project and
an actual progress indicator before the assertions were valid.

The corrected reentrant test checks the busy guard deterministically. It does not prove
native background-thread interleavings. Disposal is exercised in-flight, followed by
project closure; a native user closing the window while a background worker runs remains
untested. A corrected run logged IDE Git-ignore coroutine errors during immediate JVM
shutdown after successful assertions. A normal interactive IDE shutdown is not represented.
The stack names the IDE's `GitIgnoreInStoreDirGenerator` coroutine and contains no
CodexCommit frame. Three additional control runs opened and closed synthetic Git
projects without invoking the CodexCommit service; all exited successfully and did not
reproduce the error. The second baseline and five additional round logs also did not
reproduce it. This evidence does not establish the root cause or prove that the plugin
cannot contribute. The intermittent shutdown error remains unresolved; it is not counted
as a failed plugin assertion or declared harmless. No plugin resource defect was confirmed.

Native click-through acceptance, GUI layout/theme/scaling/accessibility, dynamic unload,
an authenticated provider round-trip, other IDE builds and live macOS/Linux IDE operation
remain untested. Subprocess timeout/cancellation checks in the core suite use real local
child processes, but do not replace native GUI timing tests. No universal reliability or
zero-failure claim follows from these finite synthetic trials.
