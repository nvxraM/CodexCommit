plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.codexcommit"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val localIde = providers.environmentVariable("CODEXCOMMIT_IDE_HOME").orNull
        if (localIde != null) local(localIde) else pycharm(providers.gradleProperty("platformVersion").get())
        bundledPlugin("Git4Idea")
        bundledModule("intellij.platform.vcs.impl")
        bundledModule("intellij.platform.vcs.impl.shared")
        pluginVerifier()
    }
}

java { sourceCompatibility = JavaVersion.VERSION_25; targetCompatibility = JavaVersion.VERSION_25 }
tasks.withType<JavaCompile>().configureEach { options.release.set(25); options.encoding = "UTF-8" }
tasks.jar { from("../LICENSE") { into("META-INF") } }
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        name = "CodexCommit"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }
    pluginVerification { ides { recommended() } }
}

val coreTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs bounded subprocess, privacy, and executable discovery regression tests without an account."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.codexcommit.core.CoreTests")
}
tasks.check { dependsOn(coreTest) }
val ideBoundaryTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs local Git-object, path, binary, and unified-patch boundary regressions on synthetic fixtures."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + sourceSets.test.get().compileClasspath
    mainClass.set("dev.codexcommit.ide.IdeBoundaryTests")
}
tasks.check { dependsOn(ideBoundaryTest) }
// Tests deliberately use account-free standard-library main harnesses instead of JUnit.
tasks.test { enabled = false; description = "Use coreTest and ideBoundaryTest; no JUnit suite is configured." }

val javaExecutable = File(System.getProperty("java.home"), "bin/java" + if (System.getProperty("os.name").startsWith("Windows")) ".exe" else "")
val archiveGuardTest by tasks.registering(Exec::class) {
    group = "verification"
    commandLine(javaExecutable, "tools/SourceArchive.java", "--self-test")
}
val releaseGuardTest by tasks.registering(Exec::class) {
    group = "verification"
    commandLine(javaExecutable, "tools/ReleaseAudit.java", "--self-test")
}
val auditRelease by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn("buildPlugin", archiveGuardTest, releaseGuardTest)
    commandLine(javaExecutable, "tools/ReleaseAudit.java", "build/distributions/CodexCommit-${project.version}.zip")
}
tasks.check { dependsOn(auditRelease) }

// There is intentionally no automatic upload, signing-key handling, or publication workflow.
// Publishing requires a separate, reviewed user action after the release checklist.
tasks.matching { it.name == "publishPlugin" }.configureEach {
    doFirst { error("Automatic publishing is disabled. Review docs/PUBLISHING.md and upload the reviewed ZIP manually.") }
}
