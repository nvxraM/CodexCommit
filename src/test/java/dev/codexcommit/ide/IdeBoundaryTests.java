package dev.codexcommit.ide;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListChange;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.ex.ExclusionState;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import java.lang.reflect.Proxy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Account-free regressions. Every Git mutation below is confined to a newly created synthetic directory. */
public final class IdeBoundaryTests {
    private static int passed;
    private static int skipped;
    private static Path fixture;
    private static Path git;
    @FunctionalInterface interface Check { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        git = findGit();
        fixture = Files.createTempDirectory("codexcommit-ide-test-").toRealPath();
        try {
            command(fixture, "", "init", "--quiet", "--template=");
            test("blocks sensitive names and research/binary formats", () -> {
                for (String value : List.of(".env", "src/.env.production", "sub/id_rsa", ".codex/auth.json", "private/secrets.py",
                        "keys/key.pem", "raw/contact.npy", "genomes/assembly.fa", "backup.sqlite")) {
                    require(ChangeSnapshot.sensitive(Path.of(value)), "Sensitive path accepted.");
                }
                require(!ChangeSnapshot.sensitive(Path.of("src/Feature.java")), "Ordinary source rejected.");
            });
            test("excluded IDE paths identify the file and the recovery action", () -> {
                for (String path : List.of(".idea/workspace.xml", "nested/.IDEA/workspace.xml")) {
                    String message = filenameFailure(Path.of(path));
                    require(message.contains(path) && message.startsWith("Uncheck ") && message.contains("Commit window"),
                            "Blocked file or recovery action missing.");
                    require(message.endsWith("IDE settings files are excluded."), "IDE settings reason missing.");
                }
                ChangeSnapshot.checkSensitivePath(Path.of("src/feature.py"));
                ChangeSnapshot.checkSensitivePath(Path.of("tests/test_feature.py"));
            });
            test("credential filename errors retain the policy and omit absolute paths", () -> {
                require(filenameFailure(Path.of("config/.env.production")).contains("sensitive-content policy"), "Policy reason missing.");
                String message = filenameFailure(fixture.resolve(".idea/workspace.xml"));
                require(!message.contains(fixture.toString()) && !message.contains("workspace.xml"), "Absolute path disclosed.");
                require(!filenameFailure(Path.of("../.env")).contains("../"), "Parent path disclosed.");
            });
            test("excluded filename notices escape markup and bound display length", () -> {
                require(filenameFailure(Path.of(".idea/a&b.xml")).contains("a&amp;b.xml"), "Filename entity was not escaped.");
                String longMessage = filenameFailure(Path.of(".idea/" + "x".repeat(300) + ".xml"));
                require(longMessage.length() < 300 && longMessage.contains("..."), "Filename notice is unbounded.");
                if (!System.getProperty("os.name").startsWith("Windows")) {
                    String message = filenameFailure(Path.of(".idea/<b>&file.xml"));
                    require(message.contains("&lt;b&gt;&amp;file.xml") && !message.contains("<b>"), "Filename markup was not escaped.");
                    String controls = filenameFailure(Path.of(".idea/bad\n\u202ename.xml"));
                    require(!controls.contains("\n") && !controls.contains("\u202e"), "Filename changed notice direction or layout.");
                }
            });
            test("strict UTF-8 text rejects binary and malformed content", () -> {
                require(LocalGitReader.text("λ\r\nnext\r".getBytes(StandardCharsets.UTF_8)).equals("λ\nnext\n"), "UTF-8 text mismatch.");
                rejects(() -> LocalGitReader.text(new byte[]{'a', 0, 'b'}));
                rejects(() -> LocalGitReader.text(new byte[]{(byte) 0xc3, 0x28}));
            });
            test("current file reads enforce byte cap and project boundary", () -> {
                Path file = fixture.resolve("bounded.txt");
                Files.write(file, new byte[LocalGitReader.MAX_FILE_BYTES]);
                require(ChangeSnapshot.readBounded(fixture, file).length == LocalGitReader.MAX_FILE_BYTES, "Limit boundary rejected.");
                Files.write(file, new byte[LocalGitReader.MAX_FILE_BYTES + 1]);
                rejects(() -> ChangeSnapshot.readBounded(fixture, file));
                rejects(() -> ChangeSnapshot.validateLocation(fixture, fixture.getParent().resolve("outside.txt")));
                rejects(() -> ChangeSnapshot.validateLocation(fixture, fixture.resolve("bad\nname.txt")));
            });
            test("Git object preflight and bounded reads preserve literal filenames", () -> {
                String name = "odd [draft] file.txt";
                Files.writeString(fixture.resolve(name), "synthetic baseline λ\n", StandardCharsets.UTF_8);
                command(fixture, "", "add", "--", name);
                command(fixture, "", "-c", "user.name=CodexCommit Tests", "-c", "user.email=tests@example.invalid",
                        "-c", "commit.gpgsign=false", "commit", "--quiet", "--no-verify", "-m", "Synthetic fixture");
                String revision = command(fixture, "", "rev-parse", "HEAD").strip();
                LocalGitReader reader = new LocalGitReader(git, () -> false);
                LocalGitReader.Blob object = reader.inspect(fixture, revision, name);
                require(reader.read(fixture, object).equals("synthetic baseline λ\n"), "Wrong baseline content.");
                rejects(() -> reader.inspect(fixture, "HEAD", name));
                rejects(() -> reader.inspect(fixture, revision, "odd*"));
                LocalGitReader cancelled = new LocalGitReader(git, () -> true);
                try { cancelled.inspect(fixture, revision, name); throw new AssertionError("Cancellation ignored."); }
                catch (InterruptedException expected) { }
            });
            test("oversized historical baseline is rejected even when current file is small", () -> {
                Path file = fixture.resolve("historical.txt");
                Files.writeString(file, "x".repeat(LocalGitReader.MAX_FILE_BYTES + 1));
                command(fixture, "", "add", "--", "historical.txt");
                command(fixture, "", "-c", "user.name=CodexCommit Tests", "-c", "user.email=tests@example.invalid",
                        "-c", "commit.gpgsign=false", "commit", "--quiet", "--no-verify", "-m", "Oversized synthetic baseline");
                String revision = command(fixture, "", "rev-parse", "HEAD").strip();
                Files.writeString(file, "small\n");
                require(ChangeSnapshot.readBounded(fixture, file).length == 6, "Small current file rejected.");
                rejects(() -> new LocalGitReader(git, () -> false).inspect(fixture, revision, "historical.txt"));
            });
            test("large UTF-8 source survives bounded disk and Git reads", () -> {
                String content = "# λ\n".repeat(LocalGitReader.MAX_FILE_BYTES / 5) + "#".repeat(LocalGitReader.MAX_FILE_BYTES % 5);
                byte[] expected = content.getBytes(StandardCharsets.UTF_8);
                require(expected.length == LocalGitReader.MAX_FILE_BYTES, "Fixture must reach the file byte cap.");
                Path file = fixture.resolve("large.py");
                Files.write(file, expected);
                require(LocalGitReader.text(ChangeSnapshot.readBounded(fixture, file)).equals(content), "Large current source changed.");
                command(fixture, "", "add", "--", "large.py");
                command(fixture, "", "-c", "user.name=CodexCommit Tests", "-c", "user.email=tests@example.invalid",
                        "-c", "commit.gpgsign=false", "commit", "--quiet", "--no-verify", "-m", "Large synthetic baseline");
                String revision = command(fixture, "", "rev-parse", "HEAD").strip();
                LocalGitReader reader = new LocalGitReader(git, () -> false);
                LocalGitReader.Blob blob = reader.inspect(fixture, revision, "large.py");
                require(blob.size() == expected.length && reader.read(fixture, blob).equals(content), "Large baseline changed.");
            });
            test("streamed file verification preserves hashes and honours cancellation", () -> {
                Path file = fixture.resolve("streamed.txt");
                byte[] content = "synthetic text\n".repeat(20_000).getBytes(StandardCharsets.UTF_8);
                Files.write(file, content);
                String expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
                require(ChangeSnapshot.diskDigest(fixture, file, () -> false).equals(expected), "Streamed hash mismatch.");
                for (boolean hashing : List.of(false, true)) {
                    var calls = new java.util.concurrent.atomic.AtomicInteger();
                    try {
                        if (hashing) ChangeSnapshot.diskDigest(fixture, file, () -> calls.incrementAndGet() > 2);
                        else ChangeSnapshot.readBounded(fixture, file, () -> calls.incrementAndGet() > 2);
                        throw new AssertionError("File cancellation ignored.");
                    } catch (InterruptedException expectedCancellation) { }
                }
            });
            test("large patch formatting stops at the cap and accepts cancellation inside a hunk", () -> {
                String added = "new line\n".repeat(70_000);
                rejects(() -> UnifiedPatch.format(null, "too-large.txt", "", added,
                        List.of(new UnifiedPatch.Part(0, 0, 0, 70_000)), () -> false));
                var calls = new java.util.concurrent.atomic.AtomicInteger();
                try {
                    UnifiedPatch.format(null, "cancelled.txt", "", "new\n".repeat(500),
                            List.of(new UnifiedPatch.Part(0, 0, 0, 500)), () -> calls.incrementAndGet() > 5);
                    throw new AssertionError("Patch cancellation ignored.");
                } catch (InterruptedException expectedCancellation) { }
            });
            test("unified patch applies exact modification with multiple changes", () -> {
                String before = "one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten\n";
                String after = "one\nTWO\nthree\nfour\nfive\nsix\nseven\neight\nNINE\nten\n";
                applies("multiple.txt", "multiple.txt", before, after,
                        List.of(new UnifiedPatch.Part(1, 2, 1, 2), new UnifiedPatch.Part(8, 9, 8, 9)));
            });
            test("unified patch preserves absent final newline and quoted filenames", () -> {
                applies("quoted space [a].txt", "quoted space [a].txt", "old\nline\n", "new\nline",
                        List.of(new UnifiedPatch.Part(0, 2, 0, 2)));
                applies("newline.txt", "newline.txt", "same\n", "same", List.of(new UnifiedPatch.Part(0, 1, 0, 1)));
            });
            test("unified patch represents additions deletions and renames", () -> {
                applies(null, "new.txt", "", "new\n", List.of(new UnifiedPatch.Part(0, 0, 0, 1)));
                applies("deleted.txt", null, "deleted\n", "", List.of(new UnifiedPatch.Part(0, 1, 0, 0)));
                applies("rename-old.txt", "rename-new.txt", "unchanged\n", "unchanged\n", List.of());
                applies(null, "empty-new.txt", "", "", List.of());
            });
            test("whole-file changelist selection works without an editor tracker", () -> {
                ChangeSnapshot.checkPartial(selectedChange(), null);
            });
            test("whole-file selection works with an operational tracker", () -> {
                ChangeSnapshot.checkPartial(selectedChange(), tracker(true, List.of("selected"), false, ExclusionState.ALL_INCLUDED));
            });
            test("excluded hunks, split changelists, and unready trackers are rejected", () -> {
                Change change = selectedChange();
                rejects(() -> ChangeSnapshot.checkPartial(change, tracker(false, List.of("selected"), false, ExclusionState.ALL_INCLUDED)));
                rejects(() -> ChangeSnapshot.checkPartial(change, tracker(true, List.of("selected", "other"), false, ExclusionState.ALL_INCLUDED)));
                rejects(() -> ChangeSnapshot.checkPartial(change, tracker(true, List.of("selected"), true, ExclusionState.ALL_INCLUDED)));
                rejects(() -> ChangeSnapshot.checkPartial(change, tracker(true, List.of("selected"), false, ExclusionState.ALL_EXCLUDED)));
                rejects(() -> ChangeSnapshot.checkPartial(change, tracker(true, List.of("other"), false, ExclusionState.ALL_INCLUDED)));
            });
            symlinkTest();
            System.out.println("IDE boundary tests: " + passed + " passed, 0 failed, " + skipped + " skipped. Synthetic local fixtures only; no model request.");
        } finally { cleanup(); }
    }

    private static String filenameFailure(Path relative) throws Exception {
        try { ChangeSnapshot.checkSensitivePath(relative); }
        catch (ChangeSnapshot.SelectionException expected) { return expected.getMessage(); }
        throw new AssertionError("Excluded filename accepted.");
    }

    private static Change selectedChange() {
        ContentRevision revision = new ContentRevision() {
            @Override public String getContent() { throw new AssertionError("Tracker validation must not load file content."); }
            @Override public FilePath getFile() { throw new AssertionError("Tracker validation must not access files."); }
            @Override public VcsRevisionNumber getRevisionNumber() { return VcsRevisionNumber.NULL; }
        };
        return new ChangeListChange(new Change(revision, revision, FileStatus.MODIFIED), "Synthetic changelist", "selected");
    }

    private static PartialLocalLineStatusTracker tracker(boolean operational, List<String> ids,
                                                        boolean partial, ExclusionState exclusion) {
        return (PartialLocalLineStatusTracker) Proxy.newProxyInstance(PartialLocalLineStatusTracker.class.getClassLoader(),
                new Class<?>[]{PartialLocalLineStatusTracker.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isOperational" -> operational;
                    case "getAffectedChangeListsIds" -> ids;
                    case "hasPartialChangesToCommit" -> partial;
                    case "getExcludedFromCommitState" -> exclusion;
                    default -> throw new AssertionError("Unexpected tracker access: " + method.getName());
                });
    }

    private static void symlinkTest() throws Exception {
        Path target = fixture.resolve("link-target.txt");
        Path link = fixture.resolve("link.txt");
        Files.writeString(target, "synthetic\n");
        try { Files.createSymbolicLink(link, target); }
        catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            skipped++;
            System.out.println("SKIP symbolic-link rejection: host does not allow test symlink creation.");
            return;
        }
        test("symlink path and leaf reads are rejected", () -> {
            rejects(() -> ChangeSnapshot.validateLocation(fixture, link));
            rejects(() -> ChangeSnapshot.readBounded(fixture, link));
        });
    }

    private static void applies(String beforeName, String afterName, String before, String after, List<UnifiedPatch.Part> parts) throws Exception {
        if (beforeName != null) Files.writeString(fixture.resolve(beforeName), before, StandardCharsets.UTF_8);
        String patch = UnifiedPatch.format(beforeName, afterName, before, after, parts, () -> false);
        command(fixture, patch, "apply", "--check", "--whitespace=nowarn", "-");
        command(fixture, patch, "apply", "--whitespace=nowarn", "-");
        if (afterName == null) require(!Files.exists(fixture.resolve(beforeName)), "Deletion not applied.");
        else require(Files.readString(fixture.resolve(afterName), StandardCharsets.UTF_8).equals(after), "Patch changed unexpected content.");
        if (beforeName != null && afterName != null && !beforeName.equals(afterName)) {
            require(!Files.exists(fixture.resolve(beforeName)), "Rename source still exists.");
        }
    }

    private static Path findGit() throws IOException {
        String configured = System.getenv("CODEXCOMMIT_TEST_GIT");
        if (configured != null && !configured.isBlank()) return LocalGitReader.requireNativeGit(Path.of(configured));
        for (String candidate : List.of("C:/Program Files/Git/cmd/git.exe", "/usr/bin/git", "/bin/git")) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) return LocalGitReader.requireNativeGit(path);
        }
        throw new IOException("Set CODEXCOMMIT_TEST_GIT to an absolute native Git binary to run boundary tests.");
    }

    private static String command(Path directory, String input, String... args) throws Exception {
        require(directory.equals(fixture), "Git test command escaped synthetic fixture.");
        List<String> command = new ArrayList<>(List.of(git.toString(), "--no-pager", "--literal-pathspecs"));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.keySet().removeIf(name -> name.toUpperCase(Locale.ROOT).startsWith("GIT_"));
        environment.put("GIT_CONFIG_NOSYSTEM", "1");
        environment.put("GIT_CONFIG_GLOBAL", System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null");
        environment.put("GIT_TERMINAL_PROMPT", "0");
        Process process = builder.start();
        try {
            process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            require(process.waitFor(10, TimeUnit.SECONDS), "Synthetic Git command timed out.");
            String output = new String(process.getInputStream().readNBytes(64 * 1024), StandardCharsets.UTF_8);
            require(process.exitValue() == 0, "Synthetic Git command failed: " + output);
            return output;
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    private static void test(String name, Check check) throws Exception { check.run(); passed++; System.out.println("PASS " + name); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Check check) throws Exception {
        try { check.run(); throw new AssertionError("Expected safe rejection."); }
        catch (IOException | InvalidPathException expected) { }
    }

    private static void cleanup() throws IOException {
        Path temp = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        require(fixture.startsWith(temp) && fixture.getFileName().toString().startsWith("codexcommit-ide-test-"), "Unsafe test cleanup target.");
        try (var paths = Files.walk(fixture)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!Files.isSymbolicLink(path)) {
                    DosFileAttributeView dos = Files.getFileAttributeView(path, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                    if (dos != null) dos.setReadOnly(false); // Git marks synthetic loose objects read-only on Windows.
                }
                Files.deleteIfExists(path);
            }
        }
    }
}
