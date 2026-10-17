# Third-party build tooling

The source distribution includes the standard Gradle 9.1.0 wrapper scripts and wrapper JAR, generated with the official Gradle distribution. They are licensed under the Apache License 2.0; retain their original headers. The complete applicable notice/license is embedded at `META-INF/LICENSE` inside `gradle/wrapper/gradle-wrapper.jar`. See [Gradle licensing](https://github.com/gradle/gradle/blob/v9.1.0/LICENSE).

The wrapper JAR SHA-256 is `76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3`. The wrapper distribution checksum is pinned in `gradle-wrapper.properties`. Source provenance: [Gradle 9.1.0](https://github.com/gradle/gradle/tree/v9.1.0).

The IntelliJ Platform Gradle Plugin, IDE SDK, Plugin Verifier, JDK, Git, and Codex CLI are separate tools governed by their own licenses/terms. They are not bundled in the installable CodexCommit ZIP. The SDK's platform/Git libraries are compile-time dependencies and are provided by the target IDE at runtime. No upstream executable, framework, authentication file, or third-party model is redistributed as part of the plugin.

The original CodexCommit code and docs use the [MIT license](../../LICENSE).
Third-party product names and marks do not imply endorsement.

## Supplied artwork

The maintainer supplied `CODEXCommit_Asset_Pack` version 1.0.
The following static files are copied unchanged from that pack:

| Use | Pack source | Project path |
| --- | --- | --- |
| Plugin icons | `05_Platform/JetBrains/META-INF/pluginIcon.svg` and `pluginIcon_dark.svg` | `src/main/resources/META-INF/` |
| Toolbar fallback icons | `05_Platform/JetBrains/icons/action.svg` and `action_dark.svg` | `src/main/resources/icons/codexcommit.svg` and `codexcommit_dark.svg` |
| Wordmark masters | `04_Wordmarks/horizontal-light-outlined.svg` and `horizontal-dark-outlined.svg` | `docs/images/brand/` |

The outlined wordmarks use DejaVu Sans Bold glyph outlines. They do not require an installed font.
The pack's [font copyright and license notice](images/brand/FONT_LICENSE.txt) is retained unchanged from `09_Source/FONT_LICENSE.txt`.
It includes the Bitstream Vera permission notice, the public-domain DejaVu changes, and Debian packaging notices.
No font program or pack build script is included.
The supplied cube artwork replaces the earlier adapted OpenAI toolbar and plugin icons.
The toolbar renderer uses the supplied cube dimensions and colours with the maintainer's approved outer-cube motion.
It draws local vector geometry. It does not bundle loading-frame files or run the pack's browser code or build scripts.
The animated README wordmarks retain the original outlined text and use the approved cube motion.
The static SVG masters remain available beside them.
The release publisher must review the final artwork rights and notices before distribution.
