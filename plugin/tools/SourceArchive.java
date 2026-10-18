import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;

/** Creates a source-only snapshot from an explicit allowlist; never includes caches or local settings. */
public final class SourceArchive {
    private static final Set<String> REPOSITORY_FILES = Set.of("README.md", "LICENSE", ".gitignore",
            ".github/SECURITY.md", ".github/workflows/check.yml", ".github/workflows/release.yml");
    private static final Set<String> PROJECT_FILES = Set.of("plugin/AGENTS.md", "plugin/.gitattributes",
            "plugin/gradle.properties", "plugin/settings.gradle.kts", "plugin/build.gradle.kts", "plugin/gradlew", "plugin/gradlew.bat");
    private static final Set<String> DOCUMENT_FILES = Set.of("plugin/docs/README.md", "plugin/docs/USAGE.md", "plugin/docs/BUILD.md",
            "plugin/docs/CHANGELOG.md", "plugin/docs/PRIVACY.md", "plugin/docs/PUBLISHING.md", "plugin/docs/VERIFICATION.md", "plugin/docs/SECURITY_AUDIT.md",
            "plugin/docs/DESCRIPTION.md", "plugin/docs/WRITING_GUIDE.md", "plugin/docs/THIRD_PARTY_NOTICES.md", "plugin/docs/release.properties.example",
            "plugin/docs/images/README.md", "plugin/docs/images/generate-button.png", "plugin/docs/images/commit-message.png",
            "plugin/docs/images/how-to.webp", "plugin/docs/images/generate-button.webp",
            "plugin/docs/images/brand/horizontal-light.webp", "plugin/docs/images/brand/horizontal-dark.webp",
            "plugin/docs/images/brand/horizontal-light-outlined.svg",
            "plugin/docs/images/brand/horizontal-dark-outlined.svg", "plugin/docs/images/brand/FONT_LICENSE.txt");
    private static final Set<String> SUPPORT_FILES = Set.of("plugin/tools/LocalBuild.java", "plugin/tools/ReleaseAudit.java",
            "plugin/tools/SourceArchive.java", "plugin/tools/build-local.sh", "plugin/tools/build-local.ps1",
            "plugin/gradle/wrapper/gradle-wrapper.jar", "plugin/gradle/wrapper/gradle-wrapper.properties",
            "plugin/src/main/resources/META-INF/plugin.xml", "plugin/src/main/resources/META-INF/pluginIcon.svg",
            "plugin/src/main/resources/META-INF/pluginIcon_dark.svg", "plugin/src/main/resources/icons/codexcommit.svg",
            "plugin/src/main/resources/icons/codexcommit_dark.svg");
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
                    ".github/auth.json", "plugin/docs/images/brand/private.png",
                    "plugin/src/main/resources/icons/loading/frame-000.svg", "plugin/src/main/resources/icons/loading/private.svg");
            for (String path : unsafe) {
                if (allowed(path)) throw new AssertionError("Unsafe source path accepted.");
            }
            List<String> expected = List.of("README.md", ".github/SECURITY.md", ".github/workflows/release.yml",
                    "plugin/src/main/java/dev/codexcommit/core/CodexRunner.java", "plugin/gradle/wrapper/gradle-wrapper.jar",
                    "plugin/docs/USAGE.md", "plugin/docs/images/README.md", "plugin/docs/images/generate-button.png",
                    "plugin/tools/build-local.sh", "plugin/tools/build-local.ps1", "plugin/docs/images/how-to.webp",
                    "plugin/docs/images/brand/horizontal-dark-outlined.svg", "plugin/src/main/resources/META-INF/pluginIcon_dark.svg",
                    "plugin/docs/images/brand/horizontal-dark.webp", "plugin/docs/images/brand/horizontal-light.webp",
                    "plugin/docs/images/generate-button.webp", "plugin/src/main/java/dev/codexcommit/ide/CubeMotion.java");
            for (String path : expected) {
                if (!allowed(path)) throw new AssertionError("Expected source path rejected.");
            }
            System.out.println("Source allowlist tests: " + (unsafe.size() + expected.size()) + " passed, 0 failed.");
            return;
        }
        boolean listOnly = args.length == 2 && args[0].equals("--list");
        if (args.length != 1 && !listOnly) throw new IllegalArgumentException("Usage: java plugin/tools/SourceArchive.java [--list] <repository-root>");
        Path root = Path.of(args[listOnly ? 1 : 0]).toRealPath();
        Path versionFile = root.resolve("plugin/gradle.properties");
        if (!Files.isRegularFile(versionFile, LinkOption.NOFOLLOW_LINKS) || Files.size(versionFile) > 64 * 1024)
            throw new IOException("Missing or oversized version metadata.");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(versionFile, LinkOption.NOFOLLOW_LINKS)) {
            byte[] content = input.readNBytes(64 * 1024 + 1);
            if (content.length > 64 * 1024) throw new IOException("Oversized version metadata.");
            properties.load(new StringReader(new String(content, StandardCharsets.UTF_8)));
        }
        String version = properties.getProperty("pluginVersion", "");
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?")) throw new IOException("Invalid plugin version.");
        Path destination = root.resolve("plugin/dist/CodexCommit-source-" + version + ".zip");
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
            if (Files.size(file) > maxBytes(name)) throw new IOException("Unexpectedly large source file.");
        }
        if (listOnly) {
            for (Path file : files) System.out.println(root.relativize(file).toString().replace('\\', '/'));
            return;
        }
        Files.createDirectories(destination.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW))) {
            for (Path file : files) {
                if (Files.isSymbolicLink(file) || !file.toRealPath().startsWith(root)) throw new IOException("Source link outside release.");
                String name = root.relativize(file).toString().replace('\\', '/');
                if (Files.size(file) > maxBytes(name)) throw new IOException("Unexpectedly large source file.");
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
    private static long maxBytes(String name) {
        return switch (name) {
            case "plugin/docs/images/how-to.webp" -> 24L * 1024 * 1024;
            case "plugin/docs/images/brand/horizontal-dark.webp", "plugin/docs/images/brand/horizontal-light.webp"
                    -> 8L * 1024 * 1024;
            default -> 2L * 1024 * 1024;
        };
    }
}
