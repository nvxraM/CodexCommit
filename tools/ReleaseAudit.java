import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import java.util.regex.Pattern;

/** Account-free check of what will be distributed. Not a vulnerability scanner or legal clearance. */
public final class ReleaseAudit {
    private static final Set<String> RESOURCES = Set.of("META-INF/plugin.xml", "META-INF/MANIFEST.MF", "META-INF/LICENSE",
            "META-INF/pluginIcon.svg", "icons/diffquill.svg");
    static boolean allowed(String path) {
        return RESOURCES.contains(path) || path.matches("dev/diffquill/(core|ide)/[A-Za-z0-9_$]+\\.class");
    }
    static boolean hasSecret(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        return Pattern.compile("-----BEGIN (?:[A-Z ]*PRIVATE KEY)-----|\\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}\\b|"
                + "\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b|\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b|"
                + "\\bxox[baprs]-[A-Za-z0-9-]{12,}\\b").matcher(text).find();
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-test")) {
            for (String path : List.of("META-INF/private.pem", "META-INF/credentials.yml", "META-INF/.env", "icons/account.key",
                    "dev/diffquill/secret.txt", "dev/diffquill/core/auth.json")) {
                if (allowed(path)) throw new AssertionError("Unsafe release resource accepted.");
            }
            if (!allowed("dev/diffquill/core/CodexRunner.class") || !allowed("META-INF/plugin.xml")) throw new AssertionError("Expected release file rejected.");
            if (!hasSecret(("sk-proj-" + "A".repeat(40)).getBytes(StandardCharsets.UTF_8))) throw new AssertionError("Synthetic key was not detected.");
            System.out.println("Release allowlist tests: 9 passed, 0 failed.");
            return;
        }
        if (args.length != 1) throw new IllegalArgumentException("Usage: java tools/ReleaseAudit.java <plugin.zip>");
        Path artifact = Path.of(args[0]);
        byte[] archive = Files.readAllBytes(artifact);
        if (archive.length > 5 * 1024 * 1024) throw new IOException("Unexpectedly large plugin archive.");
        Map<String, byte[]> outer = unzip(archive);
        if (outer.size() != 1) throw new IOException("Expected one project JAR and no bundled runtime/dependency artifacts.");
        String name = outer.keySet().iterator().next();
        if (!name.matches("DiffQuill/lib/[^/]+\\.jar")) throw new IOException("Unexpected distribution layout: " + name);
        Map<String, byte[]> inner = unzip(outer.get(name));
        if (!inner.containsKey("META-INF/plugin.xml")) throw new IOException("Missing descriptor.");
        boolean actionFound = false;
        for (var entry : inner.entrySet()) {
            String path = entry.getKey();
            if (!allowed(path))
                throw new IOException("Unexpected packaged namespace: " + path);
            if (hasSecret(entry.getValue())) throw new IOException("Possible secret in packaged file: " + path);
            if (path.contains("Tests") || path.contains("Fake") || path.contains("TestChild"))
                throw new IOException("Test class was packaged: " + path);
            if (path.equals("dev/diffquill/ide/GenerateCommitMessageAction.class")) actionFound = true;
            String text = new String(entry.getValue(), StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
            String privateHome = System.getProperty("user.home").toLowerCase(Locale.ROOT);
            if (text.contains(privateHome) || text.contains(privateHome.replace('\\', '/')))
                throw new IOException("Machine-specific or unrelated project data was found: " + path);
            if (path.endsWith(".exe") || path.endsWith(".dll") || path.endsWith(".so") || path.endsWith(".dylib") || path.endsWith("auth.json"))
                throw new IOException("Unexpected native or authentication artifact: " + path);
        }
        if (!actionFound) throw new IOException("Missing commit-message action class.");
        String descriptor = new String(inner.get("META-INF/plugin.xml"), StandardCharsets.UTF_8);
        if (!descriptor.contains("local.codex.commitmessage") || !descriptor.contains("Vcs.MessageActionGroup"))
            throw new IOException("Plugin identity or toolbar hook changed.");
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive));
        System.out.println("PASS: archive contains only project classes/resources, no bundled runtimes or tests.");
        System.out.println("PASS: personal-path and unrelated-project checks.");
        System.out.println("SHA256: " + sha);
        System.out.println("JAR entries: " + inner.size());
        System.out.println("Still required: live IDE acceptance, publisher metadata, license review, and publication approval.");
    }
    private static Map<String, byte[]> unzip(byte[] data) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("..") || name.contains("\\")) throw new IOException("Unsafe ZIP path.");
                if (entry.isDirectory()) continue;
                byte[] content = zip.readNBytes(2 * 1024 * 1024 + 1);
                if (content.length > 2 * 1024 * 1024 || entries.size() > 256) throw new IOException("Unexpected archive size.");
                if (entries.put(name, content) != null) throw new IOException("Duplicate ZIP entry.");
            }
        }
        return entries;
    }
}
