# Verification record

## 1.1.10 — public support contact and privacy notice

The publisher remains `nvxraM`. The approved public contact is now `nvxram.support@gmail.com`.
Both the plugin Overview and Additional Info link to the public profile-repository privacy notice.
An unauthenticated HTTP read confirmed that the page is accessible and contains the new support email.
The source repository remains private. The license, account ownership and CLI integration are unchanged.
Marketplace submission remains paused; this update does not claim submission or approval.

The local build, structure check and 147 regression/archive cases passed (33 core, 35 IDE boundary,
36 source guard, 43 release guard). Plugin Verifier 1.410 reports Compatible against `PY-262.10968.92`.
The existing bounded-build configuration warning remains intentional.
Packaged-byte inspection confirmed version 1.1.10, the unchanged publisher name, the new support email,
and the new URL in both the descriptor and compiled Additional Info code. The old email and private privacy URL
are absent from the packaged JAR. This is a byte-level check, not a native GUI interaction test.
Local candidate SHA-256: `70c35809ecbe2ccf5f83edb508608751340a1de3cd9c4b11f6555cfc2d6953ec`. The release workflow records its independently built artifact hash.
Native GUI acceptance, native Windows Codex execution and real-account responses remain untested.


## Publisher metadata and GitHub release preparation — 18 October 2026

The unpublished 1.1.9 candidate now uses the approved publisher `nvxraM` and the public contact approved for that version.
The privacy notice retains its data-processing disclosure and links to its repository-hosted page.
The repository is private and has no GitHub Pages site. That URL is not yet a publicly readable Marketplace privacy page.
Marketplace submission and approval are separate; neither is claimed here.

The runtime CLI integration is unchanged from the compatibility check below.
The source archive guard now includes the manual GitHub release workflow. Local checks pass 147 cases:
33 core, 35 IDE boundary, 36 source guard and 43 release guard cases. Build and structure checks pass.
Plugin Verifier 1.410 reports Compatible for this updated ZIP against `PY-262.10968.92`, offline.
The metadata-updated local ZIP has SHA-256 `22c59e2cec258d8583de5b1b958c714bed08d2d81eabf6ff9dfa7e3e6ba27bdf`.
The release workflow independently builds, tests and verifies its exact source commit, and records its own ZIP hash.
The local ZIP hash does not substitute for the hash of an independently built release artifact.

The workflow passed actionlint 1.7.12 and shell/Python syntax checks. Actual local validation scripts rejected
version mismatch, shell text, symbolic refs and missing commits. Mocked API tests rejected existing releases/tags,
authentication/server failures and artifact tampering. These mocked checks are not successful publication evidence.
The workflow uses read-only build permissions and grants `contents: write` only to its separate release job.
No PAT, persistent credential or repository permission setting was added or changed.

## Compatibility check — 18 October 2026

Repository: https://github.com/nvxraM/CodexCommit.
Base revision: `6e39dbe7e356c772be313bd3200a6466b479547b`.
Candidate: source version 1.1.9. The checked 1.1.8 release ZIP is unchanged.

Official stable releases checked today:

- [PyCharm 2026.2.3](https://www.jetbrains.com/pycharm/download/), build `PY-262.10968.92`, released 18 September 2026.
- [Codex CLI 0.163.0](https://github.com/openai/codex/releases/tag/rust-v0.163.0), released 15 October 2026.

The original code rejects 0.163.0 before generation and automatic model selection.
The candidate accepts exactly 0.160.1 and 0.163.0. Unknown versions and prereleases remain blocked.
The IDE range remains `262.10968.92` through `262.*`. Older IDEs and the 263 EAP are not claimed.

Linux x86-64 checks used OpenJDK 25+36, Gradle 9.1.0, IntelliJ Platform Gradle plugin 2.19.0,
and Plugin Verifier 1.410. The native Linux musl CLI archives matched GitHub release asset SHA-256 metadata.

| Check | Result | Scope |
| --- | --- | --- |
| `check buildPlugin verifyPluginStructure verifyPluginProjectConfiguration` | Pass | 33 core, 35 IDE boundary, 35 source guard, 43 release guard cases: 146 total |
| Official Plugin Verifier | Compatible | Exact candidate against `PY-262.10968.92`, offline |
| `auditCli` with 0.160.1 and 0.163.0 | Pass | Real native CLI, fake key, loopback response server, synthetic diff; model ID `gpt-5.4` |
| 0.163.0 audit with `gpt-6.1` | Pass | Same loopback test with another model ID; no claim of real model availability |
| Tagged 0.163.0 source review | No required profile change found | ModelInfo schema, tool registration, configuration schema, official provider defaults |

The native CLI tests verify automatic model discovery, exact large-prompt delivery, empty tools,
synthetic draft return, excluded file/configuration/skill/environment/home markers, fake-key header placement,
and no prompt/draft/fake-key persistence. They do not call OpenAI or test model behavior.
The IDE boundary suite uses SDK classes and synthetic local Git fixtures. It does not click the IDE toolbar.
JUnit and searchable-options tasks are disabled and are not counted as passing tests.

The first ordinary Gradle run failed the descendant-termination assertion because this container's PID 1
leaves killed children as zombies. The unchanged assertion passed with Gradle under an owned Linux child subreaper.
The Gradle verifier task could not create its default read-only home cache. Direct invocation with a writable
`-Duser.home` passed. The verifier reported missing optional SDK layout modules, with no plugin compatibility finding.
The configuration task warns about `until-build`; the bounded range is retained deliberately.

Commands, from `plugin/` (set `JAVA_HOME` to JDK 25 and use a writable `GRADLE_USER_HOME`):

```sh
sh gradlew check buildPlugin verifyPluginStructure verifyPluginProjectConfiguration --no-daemon
sh gradlew auditCli -PcodexExecutable=/absolute/path/to/native/codex --no-daemon
sh gradlew auditCli -PcodexExecutable=/absolute/path/to/native/codex -PauditModel=gpt-6.1 --no-daemon
java -Duser.home=/writable/verifier-home -jar /path/to/verifier-cli-1.410-all.jar check-plugin build/distributions/CodexCommit-1.1.9.zip /path/to/pycharm-2026.2.3 -offline -verification-reports-dir /writable/reports
```

Candidate ZIP SHA-256: `aec10dc074f217c1fd6e7794014290ce97bec2b1de9f3df0b77ea13cbdb2a222`.

No display server was available for a fresh native UI acceptance run. Real account authentication, real model responses,
Windows execution of CLI 0.163.0, native UI generation/cancellation/unload, and future IDE builds remain untested in this run.
macOS execution remains disabled. No credentials, installed plugin, release ZIP, or schedule were changed.
See [the repeat check procedure](BUILD.md#repeat-compatibility-check). Build/verifier success alone is not an end-to-end guarantee.

## Earlier verification records

Date: 17 October 2026.
Version: 1.1.8.

This record separates software tests from language review.
It does not prove that the plugin has no risk.

## Current candidate

Version 1.1.8 uses the approved outer-cube turn, corner tilt, acceleration, and return.
The inner cubes keep their opposite X rotations and 3.2-second period during generation.
The busy outer curve repeats every 9.6 seconds. Completion uses a 720 millisecond return curve.
Painting and timer callbacks follow EDT scheduling.
Draft insertion and the job's busy state do not wait for that visual return.
A new request during the return joins the next busy curve without a pose jump.

The toolbar draws local Java2D vector paths at the display scale. It bundles no loading-frame files or browser code.
The coalesced 40 millisecond timer stops when hidden, disposed, or motion is disabled.
Power save and screen reader modes use the static home pose. The policy listener is removed on disposal.
The renderer caches projected paths for unchanged poses and reuses its bounded 216-face storage.

| Suite | Passed | Failed | Skipped |
| --- | --- | --- | --- |
| Core and credential boundaries | 32 | 0 | 0 |
| Git, selection, screenshot, and motion boundaries | 35 | 0 | 0 |
| Source archive guard | 35 | 0 | 0 |
| Plugin archive and metadata guard | 43 | 0 | 0 |
| Total | 145 | 0 | 0 |

The Linux Gradle check, build, structure check, and project configuration check passed.
Plugin Verifier 1.410 reports Compatible with `PY-262.10968.92`, offline.
The existing supported-build warning remains. Future IDE versions have no acceptance result.
Disabled JUnit and searchable settings tasks are not counted as passed tests.
The standalone IDE checks also passed at the default and explicit twice-sized user scales.
The first Windows CI run found missing SDK display-scale initialization in the standalone test harness.
The harness now precomputes the SDK's system-scale and font data from UI defaults before the tests.
It retains the detected DPI and user scale. The production code and checked installable ZIP are unchanged.
The offline helper passed all 67 core and IDE checks and audited its separate package.
An initial offline run hit the descendant-liveness assertion in this container, whose PID 1 does not reap children.
A controlled probe confirmed that a killed zombie reports alive until reaped.
The exact POSIX build entrypoint then passed under an owned subreaper, with the assertions and production code unchanged.
The bundled-JBR and full-JDK helper builds produced identical ZIPs.

Native checks loaded the exact checked ZIP in an isolated PyCharm 2026.2.3 profile at four-times pixel density.
A real toolbar click produced the short sample draft with no dialogs.
The full 9.6-second outer curve and unchanged opposing inner rotations were observed.
Draft insertion occurred 20.7–146.7 milliseconds after the sample backend returned in the recorded runs.
These measurements describe a local test backend, not real model response time or a performance guarantee.

A quiet 20 millisecond observer bracketed the timer's finish between 703.8 and 724.2 milliseconds.
Under 4K capture load, the observed interval was 659.7–768.7 milliseconds.
These samples bracket the transition; they do not prove an exact timer stop by 720 milliseconds.
The return pose clamps to home at the configured duration. A repeat request resumed without an idle gap.
Cancellation, hide/show, power save, and screen reader stop/resume passed.
The static Overview resized correctly. Its four help links stayed scoped to CodexCommit.
A real click opened the full-screen picture; Esc closed it.
SDK removal during active generation with the viewer open stopped the timer, closed the viewer, and removed both listeners.
All owned GUI processes stopped after testing. No real account request was made.

Independent source review found no actionable defect in the motion, lifecycle, or archive changes.
The packaged JAR has no loading frames, JavaScript, machine paths, or screenshot harness markers.
Gitleaks 8.30.1 found no leaks in the unpacked plugin JAR, with full redaction.
The source snapshot uses 68 approved files. The reviewed source and reachable Git history also passed the secret scan.
Reachable commit identities use GitHub no-reply addresses.

Both README wordmarks render at 2400 × 768 pixels, without upscaling.
Their lossless, transparent WebP loops last exactly 12 seconds. The original outlined text pixels stay unchanged.
Decoded keyframes match the rendered source. The first and last visible home frames match.
Only the two named wordmark WebPs receive an 8 MiB source-file limit.
The usage demo retains its 24 MiB limit. Other source files retain the 2 MiB limit.

New native lossless WebP captures show sample files and the exact candidate:

- Usage demo: 3840 × 2160 pixels, 544,342 bytes, 17.893 seconds, with a 2.768-second final draft pause.
- Button example: 2200 × 464 pixels, 768,136 bytes, 17.800 seconds, with a 2.640-second final pause.

No capture was upscaled. The original static button PNG remains unchanged in the Overview and bundled viewer.

Real account responses, native IDE updates during generation, and classloader garbage collection remain untested.
These results do not certify zero risk or formal STE conformity.

The checked Gradle ZIP has SHA-256:

`d03066ac4020ca449f45694b3c270fed4ad0119da185b46fb3940c02860e9939`

## Previous 1.1.7 candidate

Version 1.1.7 uses the supplied cube artwork in the plugin, toolbar, README, and new screenshots.
Static icons and wordmarks are SVGs. The toolbar uses 40 local vector frames at 16 logical pixels.
Only the active toolbar button and the GitHub usage demo animate. The Overview picture stays static.
The source frame geometry and colours match the supplied pack. Only logical width and height change.
All 245 supplied checksum entries matched. Static SVGs contain no scripts, external links, or active animation nodes.
The original font notice is retained with the outlined wordmarks.

| Suite | Passed | Failed | Skipped |
| --- | --- | --- | --- |
| Core and credential boundaries | 32 | 0 | 0 |
| Git, selection, screenshot, and animation boundaries | 33 | 0 | 0 |
| Source archive guard | 33 | 0 | 0 |
| Plugin archive and metadata guard | 44 | 0 | 0 |
| Total | 142 | 0 | 0 |

The Linux Gradle check, build, structure check, and project configuration check passed.
The official Plugin Verifier 1.410 reports Compatible with `PY-262.10968.92`, offline.
The offline helper compiled the same source, passed all 65 core and IDE tests, and audited its package.
The existing supported-build warning remains. Future IDE versions have no acceptance result.
The disabled ordinary JUnit and searchable settings tasks are not counted as passed tests.

Native checks used an isolated PyCharm 2026.2.3 profile, synthetic files, and a local test backend.
The exact final ZIP loaded with the normal default motion settings.
A real mouse click selected three tracked files and started generation. The disabled icon stayed coloured and animated.
The draft contained a short subject and two bullets. No request or draft dialog opened. Completion stopped the timer.
Cancellation stopped it too. Hiding the Commit window stopped it; showing the window restored it during the same job.
Power save and screen reader modes kept the logo static.
Turning either mode off resumed the same active job without a forced repaint or a direct helper refresh.

The exact final ZIP also passed responsive Overview checks: 351 × 74 narrow, 409 × 86 default, and 670 × 141 wide.
All four Additional Info links appeared. Another plugin retained its native renderer and had no owned links.
Returning to CodexCommit restored the links. A real mouse click opened the full-screen viewer; Esc closed it.
SDK removal passed with both generation and the viewer active. It stopped the timer, closed the viewer,
removed the Overview observer and screen reader listener, and restored the original editor kit.
Both owned listener counts changed from one to zero.

Native testing caught an incorrect tool-window animation preference gate and two policy-resume gaps before release.
The final code uses the actual screen reader change event and normal busy-action updates for power save.
The new public-API regression tests screen reader stop, resume, and cleanup.
The coalesced 80 ms timer does no idle polling. Disposal removes the owned policy listener.

The new captures use native four-times pixel density, without upscaling:

- Button and tooltip: 2200 × 464 PNG.
- Sample draft: 1824 × 1412 PNG.
- Usage demo: 3840 × 2160 lossless WebP, 229,364 bytes, 10.001 seconds.

The demo contains 30 optimized frames and 15 distinct toolbar states. Its final draft pause lasts 2.001 seconds.
The Overview embeds the exact static button PNG and bundles the same bytes for its full-screen viewer.
Archive guards require all 40 approved frames and compare packaged SVG and PNG bytes with source.
Only the named usage demo gets a 24 MiB source-file limit. Other source files retain the 2 MiB limit.

Native Markdown preview created one empty JCEF directory in the cached SDK.
All 12,567 SDK regular files matched the original archive by SHA-256.
The empty generated directory was removed after shutdown. The final Gradle check passed with cache integrity restored.
Later native checks use a separate runtime SDK copy.

The source snapshot contains 104 approved files and byte-matches the reviewed tree.
Gitleaks 8.30.1 found no leaks in that snapshot, the unpacked final plugin JAR, or reachable Git history.

These checks do not test real account responses or prove classloader garbage collection.
They do not certify zero risk or formal STE conformity.

The final ZIP has SHA-256:

`ab104a32a755e9b296d9534ff14336844b6d40c2c565a87943c7dcc407693b56`

## Initial 1.1.7 UI candidate (superseded)

Version 1.1.7 uses three short Overview bullets and a responsive button picture.
Clicking the preview opens the bundled picture in an owned, full-screen window. Esc closes it.
Additional Info links to GitHub, the user guide, the MIT license, and privacy information.
The image bytes are the original GitHub screenshot.

| Suite | Passed | Failed | Skipped |
| --- | --- | --- | --- |
| Core and credential boundaries | 32 | 0 | 0 |
| Git, selection, and screenshot boundaries | 28 | 0 | 0 |
| Source archive guard | 25 | 0 | 0 |
| Plugin archive and metadata guard | 39 | 0 | 0 |
| Total | 124 | 0 | 0 |

The Linux Gradle check, build, structure check, and project configuration check passed.
The official Plugin Verifier 1.410 reports Compatible with `PY-262.10968.92`, offline.
The existing supported-build warning remains. Future IDE versions have no acceptance result.
The ordinary JUnit and searchable settings tasks remain disabled and are not counted as passed tests.

Five new headless screenshot checks cover landscape and portrait fits, invalid dimensions, actual grow/shrink painting,
and PNG decoding with exact pixel checks and malformed input rejection.
Native testing found a double-close error in the first candidate's image loader. The final decoder uses an explicit image reader.
The decoder regression now exercises that code path.
The bundle includes the PNG for the viewer. The release audit compares it to the original documentation image.

An isolated PyCharm 2026.2.3 profile loaded the exact final candidate through its normal startup listeners.
The preview measured 407 × 86 at the default width, 669 × 141 when wide, and 350 × 74 when narrow.
A real mouse click opened the native full-screen viewer. A real Esc key closed it and preserved the Plugins dialog.
Additional Info showed four help links. Another installed plugin showed no owned links and retained the SDK renderer.
Returning directly to CodexCommit's Additional Info restored the four links. Returning to Overview restored the responsive preview.
No test UrlOpener or browser preference override was active in these final checks.

The SDK accepted dynamic removal while the full-screen viewer was open.
Removal closed the viewer, restored the SDK editor kit, removed all four help links and preview components,
and left zero owned AWT observers. This check does not cover active Codex generation or prove classloader garbage collection.

The offline helper also compiled the project, passed its 60 core and IDE tests, and audited its package.
The source archive contains 57 approved files.
Gitleaks 8.30.1 found no leaks in the reviewed source snapshot, unpacked plugin JAR, or reachable history.
Real account responses and active-generation unload remain outside this release's UI checks.

The final ZIP has SHA-256:

`86eb99fba76da345f292e7c3deda63e89c0eb83f92b88cfb7eacaf065addde26`

## Previous 1.1.6 candidate

Version 1.1.6 adds the existing button screenshot to the plugin Overview.
The descriptor embeds the original PNG bytes. The image needs no image-host request or GitHub login.
What's New contains the current version and two short bullets.

| Suite | Passed | Failed | Skipped |
| --- | --- | --- | --- |
| Core and credential boundaries | 32 | 0 | 0 |
| Git, patch, path, and selection boundaries | 23 | 0 | 0 |
| Source archive guard | 25 | 0 | 0 |
| Plugin archive and metadata guard | 39 | 0 | 0 |
| Total | 119 | 0 | 0 |

The Linux Gradle check, build, structure check, and project configuration check passed.
The official Plugin Verifier 1.410 reports Compatible with `PY-262.10968.92` for the final ZIP, offline.
The supported-build warning remains. Future IDE versions have no acceptance result.
The ordinary JUnit and searchable settings tasks remain disabled and are not counted as passed tests.
The offline helper compiled the project, passed its 55 core and IDE/Git checks, and audited its package.
Both version-file readers rejected linked, oversized, and FIFO metadata in six additional manual fixture checks.

Release checks reject stale source or packaged versions, stale or excessive release notes, and missing or changed inline images.
Each version must have one to three current bullets within 60 words. The full history stays in `CHANGELOG.md`.
Package filenames now use `pluginVersion` from `gradle.properties`.
All production classes, icons, and license bytes match 1.1.5. Only the descriptor and build manifest differ in the JAR.
Real account responses and active-request unload remain untested.

An isolated PyCharm 2026.2.3 profile loaded the final ZIP.
The Overview image and all text fit the default Plugins pane without horizontal or vertical scrolling.
The original 1100 × 232 image renders at 400 × 84. Its decoded bytes match the GitHub screenshot.
The native What's New tab shows the current heading and two brief bullets.
These display checks used a temporary profile and no real account.
Gitleaks 8.30.1 found no leaks in the reviewed source snapshot, unpacked plugin JAR, or reachable history.

The final ZIP has SHA-256:

`f2a6cdccc0ac4d5cda3f9e3f6d69db95d3203567fc1e32a080f71d60df092c64`

## Repository layout — 3ace223, 17 October 2026

The repository root has five entries: `.github`, `.gitignore`, `LICENSE`, `README.md`, and `plugin/`.
The project, guides, build tools, and release copies are under `plugin/`.
GitHub reads the security policy from `.github/SECURITY.md`.

The Linux Gradle check, plugin build, structure check, and project configuration check passed from `plugin/`.
All 89 local checks passed: 32 core, 23 IDE/Git, 25 source archive, and 9 plugin archive checks.
The offline helper also compiled the project and passed its 55 core and IDE/Git checks.
The rebuilt Gradle ZIP is byte-identical to the released 1.1.5 ZIP identified below.
Production source, license text, screenshots, and the released ZIP are unchanged.

## Previous 1.1.5 candidate

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
The first 1.1.5 run passed Ubuntu and macOS. Windows exceeded its 30-minute job limit.
GitHub did not provide an archived Windows build log. No specific Gradle task failure could be established.
The workflow now caches Gradle dependencies and allows 45 minutes for a cold build.

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
From the repository root, enter `plugin/` before running the commands.

```powershell
cd plugin
.\gradlew.bat check buildPlugin verifyPluginStructure verifyPluginProjectConfiguration --no-daemon
.\tools\build-local.ps1 -IdeHome 'PATH_TO_YOUR_IDE'
java tools/SourceArchive.java --self-test
java tools/ReleaseAudit.java --self-test
java tools/ReleaseAudit.java build/distributions/CodexCommit-1.1.8.zip
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

The description file and first README paragraph contain the same 50 words.
The Overview uses three short action bullets.
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
