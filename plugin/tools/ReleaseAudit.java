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
    private static final String BUTTON_URL = "https://raw.githubusercontent.com/nvxraM/CodexCommit/3cbb38e74bb64aedf8478d5c85003edfa70661e1/plugin/docs/images/generate-button.png";
    private static final String BUTTON_SHA256 = "882418f2e776c524b141f661580b2e0d9f442d322d0ca7b265ed152eb0be1df2";
    static boolean allowed(String path) {
        return RESOURCES.contains(path)
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
                    "icons/loading/frame-000.svg", "icons/loading/private.svg")) {
                if (allowed(path)) throw new AssertionError("Unsafe release resource accepted.");
                passed++;
            }
            for (String path : List.of("dev/codexcommit/core/CodexRunner.class", "META-INF/plugin.xml",
                    "META-INF/pluginIcon_dark.svg", "dev/codexcommit/ide/CubeMotion.class")) {
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
        if (!inner.containsKey("dev/codexcommit/ide/CubeMotion.class"))
            throw new IOException("Missing vector motion renderer.");
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
        System.out.println("PASS: source and packaged descriptions match; their pinned HTTPS image and bundled screenshot match the reviewed PNG.");
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
        auditDescription(packaged, source, expectedPng, BUTTON_SHA256);
    }
    private static void auditDescription(byte[] packaged, byte[] source, byte[] expectedPng, String expectedSha) throws IOException {
        if (!validPng(expectedPng)) throw new IOException("Invalid source button PNG header.");
        if (!sha256(expectedPng).equals(expectedSha)) throw new IOException("Source button screenshot differs from the pinned HTTPS image.");
        String packagedDescription = childText(parseXml(packaged), "description");
        String sourceDescription = childText(parseXml(source), "description");
        if (!canonicalMarkup(packagedDescription).equals(canonicalMarkup(sourceDescription)))
            throw new IOException("Packaged description differs from source description.");
        for (String description : List.of(packagedDescription, sourceDescription)) {
            if (Pattern.compile("(?i)\\bdata\\s*:").matcher(description).find()) throw new IOException("Inline data URLs are excluded from the Marketplace description.");
            description = description.replaceAll("(?s)<!--.*?-->", "");
            var images = Pattern.compile("(?i)<img\\b").matcher(description);
            if (!images.find() || images.find()) throw new IOException("Description needs exactly one button image element.");
            var image = Pattern.compile("(?i)<img\\b([^<>]*)>").matcher(description);
            if (!image.find()) throw new IOException("Description needs a valid button image element.");
            Map<String, String> imageAttributes = tagAttributes(image.group(1));
            if (!Set.of("src", "width", "height", "id", "alt", "title").containsAll(imageAttributes.keySet())
                    || !BUTTON_URL.equals(imageAttributes.get("src"))) throw new IOException("Description image must use the exact pinned HTTPS URL.");
            if (!"640".equals(imageAttributes.get("width")) || !"135".equals(imageAttributes.get("height")))
                throw new IOException("Description button image dimensions must be 640 by 135.");
            var link = Pattern.compile("(?is)<a\\b([^<>]*)>\\s*<img\\b[^<>]*>\\s*</a\\s*>").matcher(description);
            if (!link.find()) throw new IOException("Description button image needs a clickable link.");
            Map<String, String> linkAttributes = tagAttributes(link.group(1));
            if (!Set.of("href", "title").containsAll(linkAttributes.keySet()) || !BUTTON_URL.equals(linkAttributes.get("href")))
                throw new IOException("Description button link must match the pinned HTTPS image.");
        }
    }
    private static Map<String, String> tagAttributes(String attributes) throws IOException {
        String input = attributes.stripTrailing();
        if (input.endsWith("/")) input = input.substring(0, input.length() - 1);
        var matcher = Pattern.compile("\\s+([A-Za-z][A-Za-z0-9:-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')").matcher(input);
        Map<String, String> result = new LinkedHashMap<>();
        int position = 0;
        while (position < input.length() && !input.substring(position).isBlank()) {
            matcher.region(position, input.length());
            if (!matcher.lookingAt()) throw new IOException("Malformed description image or link attribute.");
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            if (result.putIfAbsent(name, value) != null) throw new IOException("Duplicate description image or link attribute.");
            position = matcher.end();
        }
        return result;
    }
    private static String sha256(byte[] bytes) throws IOException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IOException("SHA-256 is unavailable.", error); }
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
        String expectedSha = sha256(png);
        String img = "<img src=\"" + BUTTON_URL + "\" width=\"640\" height=\"135\"/>";
        String image = "<a href=\"" + BUTTON_URL + "\">" + img + "</a>";
        byte[] current = described("<p>Use the button.</p>" + image);
        auditDescription(current, current, png, expectedSha);
        auditDescription(current, described("\n<p>Use the button.</p>\n" + image + "\n"), png, expectedSha);
        String alternate = image.replace("src=", "SRC=").replace("href=", "HREF=").replace('"', '\'');
        auditDescription(described(alternate), described(alternate), png, expectedSha);
        int passed = 3;
        List<String> invalid = List.of(
                "<p>Use the button.</p>",
                image + image,
                image + "<img src=\"https://example.invalid/button.png\"/>",
                image.replace("src=\"" + BUTTON_URL + "\"", ""),
                image.replace("src=", "data-src="),
                image.replace(BUTTON_URL, "http://example.invalid/button.png"),
                image.replace(BUTTON_URL, "https://example.invalid/button.png"),
                image.replace(BUTTON_URL, "javascript:alert(1)"),
                image.replace(BUTTON_URL, "file:///synthetic-test-button.png"),
                image.replace(BUTTON_URL, BUTTON_URL.substring("https:".length())),
                image.replace("3cbb38e74bb64aedf8478d5c85003edfa70661e1", "main"),
                image.replace("href=\"" + BUTTON_URL, "href=\"https://example.invalid/button.png"),
                img,
                image.replace("src=", "SRC=\"https://example.invalid/button.png\" src="),
                image.replace("href=", "HREF=\"https://example.invalid/button.png\" href="),
                image.replace("width=\"640\"", "width=\"640\" width=\"640\""),
                image.replace("width=\"640\"", "width=\"1\""),
                image.replace("width=", "onerror=\"alert(1)\" width="),
                image.replace("width=", "srcset=\"https://example.invalid/button.png 2x\" width="),
                image.replace(BUTTON_URL, "data:image/png;base64," + Base64.getEncoder().encodeToString(png)),
                image + "<p>data:image/png;base64,unused</p>",
                "<!--" + image + "-->");
        for (String description : invalid) {
            byte[] candidate = described(description);
            try { auditDescription(candidate, candidate, png, expectedSha); throw new AssertionError("Missing, unsafe, unpinned, or duplicate description image accepted."); }
            catch (IOException expected) { passed++; }
        }
        try {
            auditDescription(current, described("<p>Use another button.</p>" + image), png, expectedSha);
            throw new AssertionError("Changed packaged description accepted.");
        } catch (IOException expected) { passed++; }
        byte[] changed = png.clone();
        java.nio.ByteBuffer.wrap(changed).putInt(16, 2);
        try { auditDescription(current, current, changed, expectedSha); throw new AssertionError("Changed source screenshot accepted."); }
        catch (IOException expected) { passed++; }
        try { auditDescription(current, current, png); throw new AssertionError("Synthetic PNG accepted as the reviewed public screenshot."); }
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
