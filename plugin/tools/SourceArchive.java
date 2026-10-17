import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;

/** Creates a source-only snapshot from an explicit allowlist; never includes caches or local settings. */
public final class SourceArchive {
    private static final Set<String> REPOSITORY_FILES = Set.of("README.md", "LICENSE", ".gitignore",
            ".github/SECURITY.md", ".github/workflows/check.yml");
    private static final Set<String> PROJECT_FILES = Set.of("plugin/AGENTS.md", "plugin/.gitattributes",
            "plugin/gradle.properties", "plugin/settings.gradle.kts", "plugin/build.gradle.kts", "plugin/gradlew", "plugin/gradlew.bat");
    private static final Set<String> DOCUMENT_FILES = Set.of("plugin/docs/README.md", "plugin/docs/USAGE.md", "plugin/docs/BUILD.md",
            "plugin/docs/CHANGELOG.md", "plugin/docs/PRIVACY.md", "plugin/docs/PUBLISHING.md", "plugin/docs/VERIFICATION.md", "plugin/docs/SECURITY_AUDIT.md",
            "plugin/docs/DESCRIPTION.md", "plugin/docs/WRITING_GUIDE.md", "plugin/docs/THIRD_PARTY_NOTICES.md", "plugin/docs/release.properties.example",
            "plugin/docs/images/README.md", "plugin/docs/images/generate-button.png", "plugin/docs/images/commit-message.png");
    private static final Set<String> SUPPORT_FILES = Set.of("plugin/tools/LocalBuild.java", "plugin/tools/ReleaseAudit.java",
            "plugin/tools/SourceArchive.java", "plugin/tools/build-local.sh", "plugin/tools/build-local.ps1",
            "plugin/gradle/wrapper/gradle-wrapper.jar", "plugin/gradle/wrapper/gradle-wrapper.properties",
            "plugin/src/main/resources/META-INF/plugin.xml", "plugin/src/main/resources/META-INF/pluginIcon.svg",
            "plugin/src/main/resources/icons/codexcommit.svg");
    static boolean allowed(String name) {
        if (name.contains("..") || name.contains("\\") || name.startsWith("/")) return false;
        if (REPOSITORY_FILES.contains(name) || PROJECT_FILES.contains(name) || DOCUMENT_FILES.contains(name)
                || SUPPORT_FILES.contains(name)) return true;
        return name.matches("plugin/src/(main|test)/java/dev/codexcommit/[A-Za-z0-9_/]+\\.java");
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-test")) {
            List<String> unsafe = List.of("plugin/tools/.env", "plugin/src/main/resources/META-INF/private.pem", "plugin/src/main/resources/META-INF/credentials.yml",
                    "plugin/src/main/java/auth.json", "plugin/tools/account.key", "plugin/gradle/wrapper/credentials.properties", "plugin/release.properties", "../auth.json",
                    "plugin/docs/auth.json", "plugin/docs/.env", "plugin/docs/images/private.png", "plugin/docs/../auth.json",
                    "plugin/build/classes/private.class", "plugin/dist/private.zip", "plugin/.gradle/credentials.properties",
                    ".github/auth.json");
            for (String path : unsafe) {
                if (allowed(path)) throw new AssertionError("Unsafe source path accepted.");
            }
            List<String> expected = List.of("README.md", ".github/SECURITY.md",
                    "plugin/src/main/java/dev/codexcommit/core/CodexRunner.java", "plugin/gradle/wrapper/gradle-wrapper.jar",
                    "plugin/docs/USAGE.md", "plugin/docs/images/README.md", "plugin/docs/images/generate-button.png",
                    "plugin/tools/build-local.sh", "plugin/tools/build-local.ps1");
            for (String path : expected) {
                if (!allowed(path)) throw new AssertionError("Expected source path rejected.");
            }
            System.out.println("Source allowlist tests: " + (unsafe.size() + expected.size()) + " passed, 0 failed.");
            return;
        }
        boolean listOnly = args.length == 2 && args[0].equals("--list");
        if (args.length != 1 && !listOnly) throw new IllegalArgumentException("Usage: java plugin/tools/SourceArchive.java [--list] <repository-root>");
        Path root = Path.of(args[listOnly ? 1 : 0]).toRealPath();
        Path destination = root.resolve("plugin/dist/CodexCommit-source-1.1.5.zip");
        Set<Path> files = new TreeSet<>(Comparator.comparing(Path::toString));
        for (Set<String> names : List.of(REPOSITORY_FILES, PROJECT_FILES, DOCUMENT_FILES, SUPPORT_FILES)) {
            for (String name : names) {
                Path file = root.resolve(name);
                if (!Files.isRegularFile(file)) throw new IOException("Missing release source: " + name);
                files.add(file);
            }
        }
        for (String name : List.of("plugin/src", "plugin/tools", "plugin/gradle", "plugin/docs", ".github")) {
            try (var stream = Files.walk(root.resolve(name))) {
                files.addAll(stream.filter(Files::isRegularFile).toList());
            }
        }
        // Validate the complete list before creating an archive. Unknown files stop the release.
        for (Path file : files) {
            String name = root.relativize(file).toString().replace('\\', '/');
            if (!allowed(name) || Files.isSymbolicLink(file) || !file.toRealPath().startsWith(root))
                throw new IOException("Unexpected or unsafe source path: " + name);
            if (Files.size(file) > 2 * 1024 * 1024) throw new IOException("Unexpectedly large source file.");
        }
        if (listOnly) {
            for (Path file : files) System.out.println(root.relativize(file).toString().replace('\\', '/'));
            return;
        }
        Files.createDirectories(destination.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW))) {
            for (Path file : files) {
                if (Files.isSymbolicLink(file) || !file.toRealPath().startsWith(root)) throw new IOException("Source link outside release.");
                if (Files.size(file) > 2 * 1024 * 1024) throw new IOException("Unexpectedly large source file.");
                String name = root.relativize(file).toString().replace('\\', '/');
                if (name.endsWith(".class") || name.endsWith(".exe") || name.endsWith(".zip") || name.endsWith("auth.json")
                        || (name.endsWith(".jar") && !name.equals("plugin/gradle/wrapper/gradle-wrapper.jar"))) throw new IOException("Unexpected source artifact.");
                ZipEntry entry = new ZipEntry("CodexCommit/" + name);
                entry.setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0));
                zip.putNextEntry(entry);
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        System.out.println("Source files: " + files.size());
        System.out.println("Source archive: " + destination);
        System.out.println("SHA256: " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(destination))));
    }
}
