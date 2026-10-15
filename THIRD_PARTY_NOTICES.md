# Third-party build tooling

The source distribution includes the standard Gradle 9.1.0 wrapper scripts and wrapper JAR, generated with the official Gradle distribution. They are licensed under the Apache License 2.0; retain their original headers. The complete applicable notice/license is embedded at `META-INF/LICENSE` inside `gradle/wrapper/gradle-wrapper.jar`. See [Gradle licensing](https://github.com/gradle/gradle/blob/v9.1.0/LICENSE).

The wrapper JAR SHA-256 is `76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3`. The wrapper distribution checksum is pinned in `gradle-wrapper.properties`. Source provenance: [Gradle 9.1.0](https://github.com/gradle/gradle/tree/v9.1.0).

The IntelliJ Platform Gradle Plugin, IDE SDK, Plugin Verifier, JDK, Git, and Codex CLI are separate tools governed by their own licenses/terms. They are not bundled in the installable DiffQuill ZIP. The SDK's platform/Git libraries are compile-time dependencies and are provided by the target IDE at runtime. No upstream executable, framework, authentication file, or third-party model is redistributed as part of the plugin.

The original DiffQuill code and docs use the proposed project license. The toolbar and plugin icons adapt the OpenAI icon from [Simple Icons 15.0.0](https://github.com/simple-icons/simple-icons/blob/15.0.0/icons/openai.svg), distributed under [CC0 1.0](https://github.com/simple-icons/simple-icons/blob/15.0.0/LICENSE.md), with a pink-to-blue gradient. The OpenAI and ChatGPT marks belong to OpenAI. Third-party product names and marks do not imply endorsement. The release publisher must review the final license and notices before distribution.
