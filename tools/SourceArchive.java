import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;

/** Creates a source-only snapshot from an explicit allowlist; never includes caches or local settings. */
public final class SourceArchive {
    private static final Set<String> ROOT_FILES = Set.of("README.md", "LICENSE", "SECURITY.md", "AGENTS.md",
            "gradle.properties", "settings.gradle.kts", "build.gradle.kts", "gradlew", "gradlew.bat", ".gitignore", ".gitattributes");
    private static final Set<String> DOCUMENT_FILES = Set.of("docs/README.md", "docs/USAGE.md", "docs/BUILD.md",
            "docs/CHANGELOG.md", "docs/PRIVACY.md", "docs/PUBLISHING.md", "docs/VERIFICATION.md", "docs/SECURITY_AUDIT.md",
            "docs/DESCRIPTION.md", "docs/WRITING_GUIDE.md", "docs/THIRD_PARTY_NOTICES.md", "docs/release.properties.example",
            "docs/images/README.md", "docs/images/generate-button.png", "docs/images/review-request.png", "docs/images/review-draft.png");
    static boolean allowed(String name) {
        if (name.contains("..") || name.contains("\\") || name.startsWith("/")) return false;
        if (ROOT_FILES.contains(name) || DOCUMENT_FILES.contains(name)) return true;
        if (name.matches("src/(main|test)/java/dev/diffquill/[A-Za-z0-9_/]+\\.java")) return true;
        return Set.of("tools/LocalBuild.java", "tools/ReleaseAudit.java", "tools/SourceArchive.java", "tools/build-local.sh", "tools/build-local.ps1",
                "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties", ".github/workflows/check.yml",
                "src/main/resources/META-INF/plugin.xml", "src/main/resources/META-INF/pluginIcon.svg", "src/main/resources/icons/diffquill.svg").contains(name);
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-test")) {
            List<String> unsafe = List.of("tools/.env", "src/main/resources/META-INF/private.pem", "src/main/resources/META-INF/credentials.yml",
                    "src/main/java/auth.json", "tools/account.key", "gradle/wrapper/credentials.properties", "release.properties", "../auth.json",
                    "docs/auth.json", "docs/.env", "docs/images/private.png", "docs/../auth.json");
            for (String path : unsafe) {
                if (allowed(path)) throw new AssertionError("Unsafe source path accepted.");
            }
            List<String> expected = List.of("README.md", "src/main/java/dev/diffquill/core/CodexRunner.java", "gradle/wrapper/gradle-wrapper.jar",
                    "docs/USAGE.md", "docs/images/README.md", "docs/images/generate-button.png", "tools/build-local.sh", "tools/build-local.ps1");
            for (String path : expected) {
                if (!allowed(path)) throw new AssertionError("Expected source path rejected.");
            }
            System.out.println("Source allowlist tests: " + (unsafe.size() + expected.size()) + " passed, 0 failed.");
            return;
        }
        boolean listOnly = args.length == 2 && args[0].equals("--list");
        if (args.length != 1 && !listOnly) throw new IllegalArgumentException("Usage: java tools/SourceArchive.java [--list] <project-root>");
        Path root = Path.of(args[listOnly ? 1 : 0]).toRealPath();
        Path destination = root.resolve("dist/DiffQuill-source-1.1.1.zip");
        List<String> fileNames = ROOT_FILES.stream().sorted().toList();
        List<Path> files = new ArrayList<>();
        for (String name : fileNames) {
            Path file = root.resolve(name);
            if (!Files.isRegularFile(file)) throw new IOException("Missing release source: " + name);
            files.add(file);
        }
        for (String name : DOCUMENT_FILES) {
            if (!Files.isRegularFile(root.resolve(name))) throw new IOException("Missing release document: " + name);
        }
        for (String name : List.of("src", "tools", "gradle", ".github", "docs")) {
            try (var stream = Files.walk(root.resolve(name))) {
                files.addAll(stream.filter(Files::isRegularFile).toList());
            }
        }
        files.sort(Comparator.comparing(Path::toString));
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
                        || (name.endsWith(".jar") && !name.equals("gradle/wrapper/gradle-wrapper.jar"))) throw new IOException("Unexpected source artifact.");
                ZipEntry entry = new ZipEntry("DiffQuill/" + name);
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
