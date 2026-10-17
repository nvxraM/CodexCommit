import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Account-free check of what will be distributed. Not a vulnerability scanner or legal clearance. */
public final class ReleaseAudit {
    private static final Set<String> RESOURCES = Set.of("META-INF/plugin.xml", "META-INF/MANIFEST.MF", "META-INF/LICENSE",
            "META-INF/pluginIcon.svg", "META-INF/pluginIcon_dark.svg", "icons/codexcommit.svg", "icons/codexcommit_dark.svg",
            "images/generate-button.png");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    static boolean allowed(String path) {
        return RESOURCES.contains(path) || path.matches("icons/loading/frame-0[0-3][0-9]\\.svg")
                || path.matches("dev/codexcommit/(core|ide)/[A-Za-z0-9_$]+\\.class");
    }
    static boolean hasSecret(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        return Pattern.compile("-----BEGIN (?:[A-Z ]*PRIVATE KEY)-----|\\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}\\b|"
                + "\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b|\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b|"
                + "\\bxox[baprs]-[A-Za-z0-9-]{12,}\\b").matcher(text).find();
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-test")) {
            int passed = 0;
            for (String path : List.of("META-INF/private.pem", "META-INF/credentials.yml", "META-INF/.env", "icons/account.key",
                    "dev/codexcommit/secret.txt", "dev/codexcommit/core/auth.json", "images/private.png",
                    "icons/loading/frame-040.svg", "icons/loading/private.svg")) {
                if (allowed(path)) throw new AssertionError("Unsafe release resource accepted.");
                passed++;
            }
            for (String path : List.of("dev/codexcommit/core/CodexRunner.class", "META-INF/plugin.xml",
                    "META-INF/pluginIcon_dark.svg", "icons/loading/frame-000.svg", "icons/loading/frame-039.svg")) {
                if (!allowed(path)) throw new AssertionError("Expected release file rejected.");
                passed++;
            }
            if (!hasSecret(("sk-proj-" + "A".repeat(40)).getBytes(StandardCharsets.UTF_8))) throw new AssertionError("Synthetic key was not detected.");
            passed++;
            passed += metadataSelfTests();
            passed += descriptionSelfTests();
            byte[] png = samplePng();
            if (!validPng(png) || validPng(new byte[0])) throw new AssertionError("PNG header validation failed.");
            passed += 2;
            png[0] = 0;
            if (validPng(png)) throw new AssertionError("Invalid PNG signature accepted.");
            passed++;
            png[0] = PNG_SIGNATURE[0];
            java.nio.ByteBuffer.wrap(png).putInt(16, 0);
            if (validPng(png)) throw new AssertionError("Empty PNG dimensions accepted.");
            passed++;
            System.out.println("Release guard tests: " + passed + " passed, 0 failed.");
            return;
        }
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Usage: java tools/ReleaseAudit.java <plugin.zip> [project-root]");
        Path project = Path.of(args.length == 2 ? args[1] : ".").toRealPath();
        String version = projectVersion(project);
        byte[] sourceDescriptor = metadataFile(project.resolve("src/main/resources/META-INF/plugin.xml"));
        byte[] buttonPng = metadataFile(project.resolve("docs/images/generate-button.png"));
        Path artifact = Path.of(args[0]);
        byte[] archive = Files.readAllBytes(artifact);
        if (archive.length > 5 * 1024 * 1024) throw new IOException("Unexpectedly large plugin archive.");
        Map<String, byte[]> outer = unzip(archive);
        if (outer.size() != 1) throw new IOException("Expected one project JAR and no bundled runtime/dependency artifacts.");
        String name = outer.keySet().iterator().next();
        if (!name.matches("CodexCommit/lib/[^/]+\\.jar")) throw new IOException("Unexpected distribution layout: " + name);
        Map<String, byte[]> inner = unzip(outer.get(name));
        if (!Arrays.equals(inner.get("images/generate-button.png"), buttonPng))
            throw new IOException("The bundled viewer image differs from the current button screenshot.");
        for (String resource : RESOURCES) {
            if (!resource.endsWith(".svg")) continue;
            if (!Arrays.equals(inner.get(resource), metadataFile(project.resolve("src/main/resources").resolve(resource))))
                throw new IOException("The bundled icon differs from source artwork: " + resource);
        }
        for (int index = 0; index < 40; index++) {
            String resource = String.format(Locale.ROOT, "icons/loading/frame-%03d.svg", index);
            if (!Arrays.equals(inner.get(resource), metadataFile(project.resolve("src/main/resources").resolve(resource))))
                throw new IOException("A loading frame is missing or differs from source artwork: " + resource);
        }
        if (!inner.containsKey("META-INF/plugin.xml")) throw new IOException("Missing descriptor.");
        boolean actionFound = false;
        for (var entry : inner.entrySet()) {
            String path = entry.getKey();
            if (!allowed(path))
                throw new IOException("Unexpected packaged namespace: " + path);
            if (hasSecret(entry.getValue())) throw new IOException("Possible secret in packaged file: " + path);
            if (path.contains("Tests") || path.contains("Fake") || path.contains("TestChild"))
                throw new IOException("Test class was packaged: " + path);
            if (path.equals("dev/codexcommit/ide/GenerateCommitMessageAction.class")) actionFound = true;
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
        auditMetadata(inner.get("META-INF/plugin.xml"), sourceDescriptor, version);
        auditDescription(inner.get("META-INF/plugin.xml"), sourceDescriptor, buttonPng);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive));
        System.out.println("PASS: archive contains only project classes/resources, no bundled runtimes or tests.");
        System.out.println("PASS: personal-path and unrelated-project checks.");
        System.out.println("PASS: source and packaged versions match pluginVersion; current release notes contain 1-3 bullets and at most 60 words.");
        System.out.println("PASS: source and packaged descriptions match; their inline PNG matches the current button screenshot.");
        System.out.println("SHA256: " + sha);
        System.out.println("JAR entries: " + inner.size());
        System.out.println("Still required: live IDE acceptance, publisher metadata, license review, and publication approval.");
    }
    private static String projectVersion(Path project) throws IOException {
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(metadataFile(project.resolve("gradle.properties"))));
        String version = properties.getProperty("pluginVersion", "").strip();
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?")) throw new IOException("Missing or invalid pluginVersion property.");
        return version;
    }
    private static byte[] metadataFile(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 1024 * 1024)
            throw new IOException("Missing or oversized release metadata: " + file.getFileName());
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] content = input.readNBytes(1024 * 1024 + 1);
            if (content.length > 1024 * 1024) throw new IOException("Oversized release metadata.");
            return content;
        }
    }
    static void auditMetadata(byte[] packaged, byte[] source, String expectedVersion) throws IOException {
        String packagedNotes = checkedNotes(packaged, expectedVersion);
        String sourceNotes = checkedNotes(source, expectedVersion);
        if (!canonicalMarkup(packagedNotes).equals(canonicalMarkup(sourceNotes))) throw new IOException("Packaged release notes differ from source notes.");
    }
    private static String checkedNotes(byte[] descriptor, String expectedVersion) throws IOException {
        Element plugin = parseXml(descriptor);
        if (!plugin.getTagName().equals("idea-plugin")) throw new IOException("Invalid plugin metadata root.");
        if (!childText(plugin, "version").strip().equals(expectedVersion)) throw new IOException("Descriptor version differs from pluginVersion.");
        String notes = childText(plugin, "change-notes");
        Element fragment = parseXml(("<notes>" + notes + "</notes>").getBytes(StandardCharsets.UTF_8));
        List<Element> sections = elementChildren(fragment);
        if (sections.size() != 2 || !sections.get(0).getTagName().matches("h[1-6]")
                || !canonical(sections.get(0).getTextContent()).equals("Version " + expectedVersion)
                || !sections.get(1).getTagName().equals("ul")) throw new IOException("Release notes need one current-version heading and one bullet list.");
        List<Element> bullets = elementChildren(sections.get(1));
        if (bullets.isEmpty() || bullets.size() > 3 || fragment.getElementsByTagName("li").getLength() != bullets.size())
            throw new IOException("Release notes need 1-3 bullet items.");
        StringBuilder visible = new StringBuilder(sections.get(0).getTextContent());
        for (Element bullet : bullets) {
            if (!bullet.getTagName().equals("li") || canonical(bullet.getTextContent()).isEmpty()) throw new IOException("Release notes need nonempty HTML bullet items.");
            visible.append('\n').append(bullet.getTextContent());
        }
        if (canonical(visible.toString()).split(" ").length > 60) throw new IOException("Release notes exceed 60 words.");
        return notes;
    }
    private static String canonical(String text) { return text.replaceAll("(?U)\\s+", " ").strip(); }
    private static String canonicalMarkup(String text) { return canonical(text).replace("> <", "><"); }
    static void auditDescription(byte[] packaged, byte[] source, byte[] expectedPng) throws IOException {
        if (!validPng(expectedPng)) throw new IOException("Invalid source button PNG header.");
        String packagedDescription = childText(parseXml(packaged), "description");
        String sourceDescription = childText(parseXml(source), "description");
        if (!canonicalMarkup(packagedDescription).equals(canonicalMarkup(sourceDescription)))
            throw new IOException("Packaged description differs from source description.");
        for (String description : List.of(packagedDescription, sourceDescription)) {
            var images = Pattern.compile("(?i)<img\\b").matcher(description);
            if (!images.find() || images.find()) throw new IOException("Description needs exactly one button image element.");
            var urls = Pattern.compile("(?i)data:image/png;base64,").matcher(description);
            if (!urls.find() || urls.find()) throw new IOException("Description needs exactly one inline button PNG.");
            var image = Pattern.compile("(?i)<img\\b[^>]*\\bsrc\\s*=\\s*([\"'])data:image/png;base64,([A-Za-z0-9+/]+={0,2})\\1[^>]*>").matcher(description);
            if (!image.find()) throw new IOException("Description needs an inline PNG image element.");
            byte[] actual;
            try { actual = Base64.getDecoder().decode(image.group(2)); }
            catch (IllegalArgumentException error) { throw new IOException("Description PNG data is invalid.", error); }
            if (!validPng(actual) || !Arrays.equals(actual, expectedPng)) throw new IOException("Description PNG differs from the current button screenshot.");
        }
    }
    private static String childText(Element root, String tag) throws IOException {
        String result = null;
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && element.getTagName().equals(tag)) {
                if (result != null) throw new IOException("Duplicate plugin metadata: " + tag);
                result = element.getTextContent();
            }
        }
        if (result == null) throw new IOException("Missing plugin metadata: " + tag);
        return result;
    }
    private static List<Element> elementChildren(Element root) throws IOException {
        List<Element> result = new ArrayList<>();
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) result.add(element);
            else if ((child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE)
                    && !child.getTextContent().isBlank()) throw new IOException("Release notes contain text outside their heading or bullets.");
        }
        return result;
    }
    private static Element parseXml(byte[] bytes) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException error) throws SAXException { throw error; }
                @Override public void fatalError(SAXParseException error) throws SAXException { throw error; }
            });
            return builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
        } catch (ParserConfigurationException | SAXException error) { throw new IOException("Invalid plugin metadata XML.", error); }
    }
    static boolean validPng(byte[] bytes) {
        if (bytes == null || bytes.length < 33 || !Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(bytes, PNG_SIGNATURE.length))) return false;
        var buffer = java.nio.ByteBuffer.wrap(bytes);
        return buffer.getInt(8) == 13 && buffer.getInt(12) == 0x49484452 && buffer.getInt(16) > 0 && buffer.getInt(20) > 0;
    }
    private static byte[] descriptor(String version, String notes) {
        return ("<idea-plugin><version>" + version + "</version><change-notes><![CDATA[" + notes
                + "]]></change-notes></idea-plugin>").getBytes(StandardCharsets.UTF_8);
    }
    private static int metadataSelfTests() throws IOException {
        String version = "1.1.6";
        String notes = "<h3>Version " + version + "</h3><ul><li>Show the setup button.</li></ul>";
        byte[] current = descriptor(version, notes);
        auditMetadata(current, current, version);
        auditMetadata(current, descriptor(version, "\n" + notes.replace("</h3>", "</h3>\n") + "\n"), version);
        byte[] threeBullets = descriptor(version, notes.replace("</ul>", "<li>Keep drafts brief.</li><li>Keep source content private.</li></ul>"));
        auditMetadata(threeBullets, threeBullets, version);
        int passed = 3;
        List<byte[][]> invalid = List.of(
                new byte[][]{descriptor("1.1.5", notes), current},
                new byte[][]{current, descriptor("1.1.5", notes)},
                new byte[][]{descriptor(version, notes.replace("Version 1.1.6", "Version 1.1.5")), current},
                new byte[][]{current, descriptor(version, notes.replace("Version 1.1.6", "Version 1.1.5"))},
                new byte[][]{current, descriptor(version, notes.replace("Show the setup button.", "Show the commit field."))},
                new byte[][]{descriptor(version, "<h3>Version " + version + "</h3><ul>"
                        + "<li>" + "word ".repeat(20) + "</li>"
                        + "<li>" + "word ".repeat(20) + "</li><li>" + "word ".repeat(20) + "</li></ul>"), current},
                new byte[][]{descriptor(version, notes.replace("</ul>", "<li>Two.</li><li>Three.</li><li>Four.</li></ul>")), current},
                new byte[][]{descriptor(version, "<h3>Version " + version + "</h3><ul></ul>"), current},
                new byte[][]{descriptor(version, "Version " + version + ". Show the setup button."), current},
                new byte[][]{descriptor(version, notes + notes.replace(version, "1.1.5")), current});
        for (byte[][] pair : invalid) {
            try { auditMetadata(pair[0], pair[1], version); throw new AssertionError("Stale or oversized release notes accepted."); }
            catch (IOException expected) { passed++; }
        }
        byte[] boundary = descriptor(version, "<h3>Version " + version + "</h3><ul><li>" + "word ".repeat(58) + "</li></ul>");
        auditMetadata(boundary, boundary, version);
        passed++;
        try {
            byte[] oversized = descriptor(version, "<h3>Version " + version + "</h3><ul><li>" + "word ".repeat(59) + "</li></ul>");
            auditMetadata(oversized, oversized, version);
            throw new AssertionError("Release notes word limit was not enforced.");
        } catch (IOException expected) { passed++; }
        try {
            byte[] doctype = ("<!DOCTYPE idea-plugin [<!ENTITY external SYSTEM 'file:///synthetic-unavailable-release-metadata'>]>"
                    + new String(current, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
            auditMetadata(doctype, current, version);
            throw new AssertionError("External-entity metadata accepted.");
        } catch (IOException expected) { passed++; }
        return passed;
    }
    private static byte[] samplePng() {
        return java.nio.ByteBuffer.allocate(33).put(PNG_SIGNATURE).putInt(13).put(new byte[]{'I', 'H', 'D', 'R'})
                .putInt(1).putInt(1).array();
    }
    private static byte[] described(String description) {
        return ("<idea-plugin><description><![CDATA[" + description + "]]></description></idea-plugin>").getBytes(StandardCharsets.UTF_8);
    }
    private static int descriptionSelfTests() throws IOException {
        byte[] png = samplePng();
        String image = "<img src=\"data:image/png;base64," + Base64.getEncoder().encodeToString(png) + "\" width=\"1\" height=\"1\"/>";
        byte[] current = described("<p>Use the button.</p>" + image);
        auditDescription(current, current, png);
        auditDescription(current, described("\n<p>Use the button.</p>\n" + image + "\n"), png);
        int passed = 2;
        List<byte[][]> invalid = List.of(
                new byte[][]{described("<p>Use the button.</p>"), described("<p>Use the button.</p>")},
                new byte[][]{described(image + image), described(image + image)},
                new byte[][]{described(image + "<img src=\"https://example.invalid/button.png\"/>"),
                        described(image + "<img src=\"https://example.invalid/button.png\"/>")},
                new byte[][]{current, described("<p>Use another button.</p>" + image)},
                new byte[][]{described(image.replace("data:image/png;base64,", "https://example.invalid/")),
                        described(image.replace("data:image/png;base64,", "https://example.invalid/"))},
                new byte[][]{described(image.replace(Base64.getEncoder().encodeToString(png), "invalid")),
                        described(image.replace(Base64.getEncoder().encodeToString(png), "invalid"))});
        for (byte[][] pair : invalid) {
            try { auditDescription(pair[0], pair[1], png); throw new AssertionError("Missing, remote, or changed inline screenshot accepted."); }
            catch (IOException expected) { passed++; }
        }
        byte[] changed = png.clone();
        java.nio.ByteBuffer.wrap(changed).putInt(16, 2);
        try { auditDescription(current, current, changed); throw new AssertionError("Stale inline screenshot accepted."); }
        catch (IOException expected) { passed++; }
        return passed;
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
