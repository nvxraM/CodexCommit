package dev.codexcommit.ide;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListChange;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.ex.ExclusionState;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.accessibility.ScreenReader;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
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
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.Element;
import javax.swing.text.ElementIterator;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

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
                for (String path : List.of(".idea/misc.xml", "nested/.IDEA/modules.xml")) {
                    String message = filenameFailure(Path.of(path));
                    require(message.contains(path) && message.startsWith("Uncheck ") && message.contains("Commit window"),
                            "Blocked file or recovery action missing.");
                    require(message.endsWith("IDE settings files are excluded."), "IDE settings reason missing.");
                }
                ChangeSnapshot.checkSensitivePath(Path.of("src/feature.py"));
                ChangeSnapshot.checkSensitivePath(Path.of("tests/test_feature.py"));
            });
            test("workspace settings use metadata only at root and nested project paths", () -> {
                for (String path : List.of(".idea/workspace.xml", "nested/.idea/workspace.xml", "nested/.IDEA/WORKSPACE.XML")) {
                    require(ChangeSnapshot.workspaceSettings(Path.of(path)), "Workspace path was not recognised.");
                    ChangeSnapshot.checkSensitivePath(Path.of(path));
                }
                for (String path : List.of("workspace.xml", "src/workspace.xml", ".idea/misc.xml", ".idea/modules.xml",
                        ".idea/workspace.xml/child.py", ".aws/.idea/workspace.xml", "secrets/.idea/workspace.xml",
                        "nested/.git/.idea/workspace.xml", "../.idea/workspace.xml", "nested/../.idea/workspace.xml",
                        ".idea/./workspace.xml")) {
                    require(!ChangeSnapshot.workspaceSettings(Path.of(path)), "Unrelated or unsafe workspace path accepted.");
                }
                require(!ChangeSnapshot.workspaceSettings(fixture.resolve(".idea/workspace.xml")), "Absolute workspace path accepted.");
                rejects(() -> ChangeSnapshot.checkSensitivePath(Path.of(".aws/.idea/workspace.xml")));
                rejects(() -> ChangeSnapshot.checkSensitivePath(Path.of("secrets/.idea/workspace.xml")));
            });
            test("workspace additions deletions and renames cannot conceal source changes", () -> {
                Path workspace = Path.of(".idea/workspace.xml");
                Path renamed = Path.of("nested/.idea/workspace.xml");
                Path source = Path.of("src/feature.py");
                require(ChangeSnapshot.workspaceChange(null, workspace), "Workspace addition was not classified.");
                require(ChangeSnapshot.workspaceChange(workspace, null), "Workspace deletion was not classified.");
                require(ChangeSnapshot.workspaceChange(workspace, workspace), "Workspace modification was not classified.");
                require(ChangeSnapshot.workspaceChange(workspace, renamed), "Workspace rename was not classified.");
                require(!ChangeSnapshot.workspaceChange(null, source) && !ChangeSnapshot.workspaceChange(source, null)
                        && !ChangeSnapshot.workspaceChange(source, source), "Source was classified as workspace metadata.");
                rejects(() -> ChangeSnapshot.workspaceChange(source, workspace));
                rejects(() -> ChangeSnapshot.workspaceChange(workspace, source));
            });
            test("workspace metadata validation permits autosave and atomic replacement without body inspection", () -> {
                Path directory = Files.createDirectories(fixture.resolve("workspace-metadata/.idea"));
                Path workspace = directory.resolve("workspace.xml");
                byte[] canary = "SYNTHETIC_SECRET_CANARY_NOT_A_CREDENTIAL\0".getBytes(StandardCharsets.UTF_8);
                Files.write(workspace, canary);
                try (var channel = java.nio.channels.FileChannel.open(workspace, java.nio.file.StandardOpenOption.WRITE)) {
                    channel.position((long) LocalGitReader.MAX_FILE_BYTES + 1);
                    channel.write(java.nio.ByteBuffer.wrap(new byte[]{(byte) 0xff}));
                }
                ChangeSnapshot.validateWorkspaceLocation(fixture, null, workspace);
                ChangeSnapshot.validateWorkspaceLocation(fixture, workspace, workspace);
                Files.write(workspace, new byte[]{0, (byte) 0xc3, 0x28});
                ChangeSnapshot.validateWorkspaceLocation(fixture, workspace, workspace);
                Path replacement = directory.resolve("autosave.tmp");
                Files.write(replacement, canary);
                try {
                    Files.move(replacement, workspace, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException unavailable) {
                    Files.move(replacement, workspace, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                ChangeSnapshot.validateWorkspaceLocation(fixture, workspace, workspace);
            });
            test("workspace metadata validation rejects missing additions directories and project escapes", () -> {
                Path directory = Files.createDirectories(fixture.resolve("workspace-locations/.idea"));
                Path workspace = directory.resolve("workspace.xml");
                rejects(() -> ChangeSnapshot.validateWorkspaceLocation(fixture, null, workspace));
                ChangeSnapshot.validateWorkspaceLocation(fixture, workspace, null);
                Files.writeString(workspace, "synthetic workspace\n");
                rejects(() -> ChangeSnapshot.validateWorkspaceLocation(fixture, workspace, null));
                Files.delete(workspace);
                Files.createDirectory(workspace);
                rejects(() -> ChangeSnapshot.validateWorkspaceLocation(fixture, null, workspace));
                rejects(() -> ChangeSnapshot.validateWorkspaceLocation(fixture, workspace, null));
                Files.delete(workspace);
                Path outside = fixture.getParent().resolve("outside-workspace/.idea/workspace.xml");
                rejects(() -> ChangeSnapshot.validateWorkspaceLocation(fixture, null, outside));
                rejects(() -> ChangeSnapshot.validateWorkspaceLocation(fixture, outside, null));
                rejects(() -> ChangeSnapshot.validateWorkspaceLocation(fixture, null, fixture));
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
            test("BOM-marked UTF-16 baselines decode strictly before patch generation", () -> {
                String original = "# Synthetic project\r\nUnicode: \u03bb \ud83d\ude80\r\n";
                for (var charset : List.of(StandardCharsets.UTF_16LE, StandardCharsets.UTF_16BE)) {
                    byte[] body = original.getBytes(charset);
                    byte[] bytes = new byte[body.length + 2];
                    bytes[0] = (byte) (charset.equals(StandardCharsets.UTF_16LE) ? 0xff : 0xfe);
                    bytes[1] = (byte) (charset.equals(StandardCharsets.UTF_16LE) ? 0xfe : 0xff);
                    System.arraycopy(body, 0, bytes, 2, body.length);
                    require(LocalGitReader.text(bytes).equals(original.replace("\r\n", "\n")), "UTF-16 text changed.");
                    rejects(() -> LocalGitReader.text(java.util.Arrays.copyOf(bytes, bytes.length - 1)));
                }
                require(LocalGitReader.text(new byte[]{(byte)0xef, (byte)0xbb, (byte)0xbf, 'x'}).equals("x"), "UTF-8 BOM leaked.");
                rejects(() -> LocalGitReader.text(new byte[]{(byte)0xff, (byte)0xfe, 0, 0}));
                rejects(() -> LocalGitReader.text(new byte[]{(byte)0xff, (byte)0xfe, 0, (byte)0xd8}));
                rejects(() -> LocalGitReader.text(new byte[]{(byte)0xff, (byte)0xfe, 0, 0, 'x', 0, 0, 0}));
            });
            test("decoded UTF-8 totals retain the 50 MiB combined boundary", () -> {
                require(ChangeSnapshot.checkedDecodedTotal(LocalGitReader.MAX_TOTAL_BYTES - 4, "\u03bb", "\u03bb")
                        == LocalGitReader.MAX_TOTAL_BYTES, "Exact decoded boundary rejected.");
                rejects(() -> ChangeSnapshot.checkedDecodedTotal(LocalGitReader.MAX_TOTAL_BYTES - 3, "\u03bb", "\u03bb"));
                rejects(() -> LocalGitReader.text(("\ufeff" + "\u0800".repeat(LocalGitReader.MAX_FILE_BYTES / 3 + 1))
                        .getBytes(StandardCharsets.UTF_16LE)));
            });
            test("selected unversioned files become exact bounded additions", () -> {
                FilePath one = testFilePath("src/new file.py", false);
                FilePath two = testFilePath("zcombinedtest/.gitkeep", false);
                List<Change> changes = ChangeSnapshot.selectedChanges(List.of(), List.of(one, two));
                require(changes.size() == 2 && changes.get(0).getBeforeRevision() == null
                        && changes.get(0).getAfterRevision().getFile() == one, "Selection was omitted or gained a baseline.");
                rejects(() -> ChangeSnapshot.selectedChanges(changes, List.of(one)));
                rejects(() -> ChangeSnapshot.selectedChanges(List.of(), List.of(testFilePath("directory", true))));
                rejects(() -> ChangeSnapshot.selectedChanges(List.of(), List.of()));
                rejects(() -> ChangeSnapshot.selectedChanges(List.of(), java.util.Collections.nCopies(501, one)));
            });
            test("Git unversioned preflight excludes ignored and tracked paths without staging", () -> {
                LocalGitReader reader = new LocalGitReader(git, () -> false);
                Files.writeString(fixture.resolve(".gitignore"), "ignored.py\n");
                Files.writeString(fixture.resolve("ignored.py"), "ignored synthetic text\n");
                Files.writeString(fixture.resolve("unversioned [literal].py"), "selected synthetic text\n");
                Files.writeString(fixture.resolve("tracked.py"), "tracked synthetic text\n");
                command(fixture, "", "add", "--", "tracked.py");
                String before = command(fixture, "", "write-tree");
                require(reader.isEligibleUnversioned(fixture, "unversioned [literal].py"), "Eligible unversioned file rejected.");
                require(!reader.isEligibleUnversioned(fixture, "ignored.py"), "Ignored path accepted.");
                require(!reader.isEligibleUnversioned(fixture, "tracked.py"), "Tracked path accepted as unversioned.");
                require(!reader.isEligibleUnversioned(fixture, "unversioned*"), "Pathspec was expanded.");
                require(before.equals(command(fixture, "", "write-tree")), "Preflight altered index.");
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
            test("UTF-16 Git README baseline and UTF-8 working text produce a safe text patch", () -> {
                String name = "encoding-readme.md";
                Files.write(fixture.resolve(name), "\ufeff# Sample\r\nold\r\n".getBytes(StandardCharsets.UTF_16LE));
                command(fixture, "", "add", "--", name);
                command(fixture, "", "-c", "user.name=CodexCommit Tests", "-c", "user.email=tests@example.invalid",
                        "-c", "commit.gpgsign=false", "commit", "--quiet", "--no-verify", "-m", "UTF-16 synthetic baseline");
                String revision = command(fixture, "", "rev-parse", "HEAD").strip();
                var reader = new LocalGitReader(git, () -> false);
                String before = reader.read(fixture, reader.inspect(fixture, revision, name));
                Files.writeString(fixture.resolve(name), "# Sample\nnew\n", StandardCharsets.UTF_8);
                String after = LocalGitReader.text(ChangeSnapshot.readBounded(fixture, fixture.resolve(name)));
                require(before.equals("# Sample\nold\n"), "Historical UTF-16 text was not decoded.");
                String patch = UnifiedPatch.format(name, name, before, after,
                        List.of(new UnifiedPatch.Part(1, 2, 1, 2)), () -> false);
                dev.codexcommit.core.PromptBuilder.build(patch);
                require(patch.contains("-old\n+new\n") && patch.indexOf('\0') < 0, "Encoding conversion corrupted patch.");
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
            // The IDE normally precomputes display scale before application startup. This standalone harness has no IDE startup.
            JBUIScale.setSystemScaleFactor(JBUIScale.INSTANCE.computeSystemScaleFactor(UIManager::getDefaults));
            JBUIScale.scale(1f);
            screenshotLayoutTests();
            overviewTests();
            toolbarAnimationTests();
            symlinkTest();
            System.out.println("IDE boundary tests: " + passed + " passed, 0 failed, " + skipped + " skipped. Synthetic local fixtures only; no model request.");
        } finally { cleanup(); }
    }

    private static void toolbarAnimationTests() throws Exception {
        test("outer cube brings opposite corners through the centre during its approved spin plateau", () -> {
            CubeMotion.Vec right = new CubeMotion.Vec(1, -1, -1), left = new CubeMotion.Vec(-1, 1, 1);
            require(right.x() + .4 * right.z() > 0 && left.x() + .4 * left.z() < 0, "Synthetic corners do not start on opposite sides.");
            for (double fraction : new double[]{.3, .5, .7}) {
                var pose = CubeMotion.busyPose(9.6 * fraction);
                checkUnitPose(pose);
                for (var corner : List.of(right, left)) {
                    var point = pose.outer().rotate(corner);
                    require(Math.abs(point.x() + .4 * point.z()) < 1e-9 && Math.abs(point.y() + .4 * point.z()) < 1e-9,
                            "Outer corner missed the projected centre at the spin peak.");
                }
            }
            var sideCorner = new CubeMotion.Vec(1, 1, -1);
            require(!nearVector(CubeMotion.busyPose(2.88).outer().rotate(sideCorner), CubeMotion.busyPose(4.8).outer().rotate(sideCorner), 1e-3),
                    "Outer cube stopped spinning while its opposite corners were centred.");
        });
        test("inner cubes retain opposite horizontal rotations and every cube returns to its exact loop home", () -> {
            var vertical = new CubeMotion.Vec(0, 1, 0);
            var quarter = CubeMotion.busyPose(.8);
            require(nearVector(quarter.middle().rotate(vertical), new CubeMotion.Vec(0, 0, 1), 1e-9)
                    && nearVector(quarter.inner().rotate(vertical), new CubeMotion.Vec(0, 0, -1), 1e-9),
                    "Inner cubes no longer counter-rotate about the horizontal axis.");
            var innerLoop = CubeMotion.busyPose(3.2);
            require(nearVector(innerLoop.middle().rotate(vertical), vertical, 1e-9)
                    && nearVector(innerLoop.inner().rotate(vertical), vertical, 1e-9), "Inner rotation period changed from 3.2 seconds.");
            require(samePose(CubeMotion.busyPose(0), CubeMotion.home()) && samePose(CubeMotion.busyPose(9.6), CubeMotion.home()),
                    "The complete animation loop does not meet its home pose.");
            require(samePose(CubeMotion.busyPose(.8), CubeMotion.busyPose(10.4)), "The outer spin loop has a discontinuous seam.");
        });
        test("toolbar motion follows monotonic elapsed time without resetting on refresh or running unseen", () -> SwingUtilities.invokeAndWait(() -> {
            long start = 1_000_000_000L;
            var clock = new java.util.concurrent.atomic.AtomicLong(start);
            var animation = new ToolbarAnimation(() -> true, () -> true, clock::get);
            var target = new AnimationTarget();
            try {
                int iconWidth = animation.getIconWidth(), iconHeight = animation.getIconHeight();
                require(iconWidth > 0 && iconHeight > 0, "Toolbar icon dimensions are empty.");
                animation.refresh();
                require(!animation.isRunning() && animation.state() == ToolbarAnimation.State.IDLE, "Animation started without a painted visible target.");
                target.showing = false;
                BufferedImage home = paintAnimation(animation, target);
                require(!animation.isRunning(), "Hidden toolbar target animated.");
                target.showing = true;
                paintAnimation(animation, target);
                require(animation.isRunning() && animation.state() == ToolbarAnimation.State.BUSY, "Busy visible toolbar did not start.");
                clock.set(start + 400_000_000L);
                animation.refresh();
                require(samePose(animation.pose(clock.get()), CubeMotion.busyPose(.4)), "A refresh restarted elapsed motion time.");
                require(!sameImage(home, paintAnimation(animation, target)), "Active vector motion did not change the painted icon.");
                clock.set(start + 9_600_000_000L);
                animation.refresh();
                require(animation.isRunning() && samePose(animation.pose(clock.get()), CubeMotion.home()), "Busy motion lost its complete loop seam.");
                require(animation.getIconWidth() == iconWidth && animation.getIconHeight() == iconHeight, "Animation changed toolbar icon dimensions.");
            } finally { animation.dispose(); }
        }));
        test("completed jobs ease to exact home within 800 ms without keeping the job busy", () -> SwingUtilities.invokeAndWait(() -> {
            long start = 1_000_000_000L;
            var clock = new java.util.concurrent.atomic.AtomicLong(start);
            var busy = new java.util.concurrent.atomic.AtomicBoolean(true);
            var animation = new ToolbarAnimation(busy::get, () -> true, clock::get);
            var target = new AnimationTarget();
            try {
                BufferedImage home = paintAnimation(animation, target);
                long finish = start + 1_100_000_000L;
                clock.set(finish);
                var before = animation.pose(finish - 100_000L);
                var current = animation.pose(finish);
                busy.set(false);
                animation.refresh();
                require(!busy.get() && animation.state() == ToolbarAnimation.State.SETTLING && animation.isRunning()
                        && samePose(current, animation.pose(finish)), "Completion delayed the job or snapped its current pose.");
                clock.set(finish + 100_000L);
                animation.refresh();
                checkVelocityContinuation(before, current, animation.pose(clock.get()));
                clock.set(finish + 360_000_000L);
                animation.refresh();
                require(animation.state() == ToolbarAnimation.State.SETTLING && !samePose(animation.pose(clock.get()), CubeMotion.home()),
                        "Completion skipped its smooth return.");
                checkUnitPose(animation.pose(clock.get()));
                clock.set(finish + 719_999_999L);
                animation.refresh();
                require(animation.state() == ToolbarAnimation.State.SETTLING, "Completion return ended before its approved duration.");
                clock.set(finish + 720_000_000L);
                animation.refresh();
                require(animation.state() == ToolbarAnimation.State.IDLE && !animation.isRunning()
                        && samePose(animation.pose(clock.get()), CubeMotion.home()) && sameImage(home, paintAnimation(animation, target)),
                        "Completion did not stop at the exact static home by 720 ms.");
            } finally { animation.dispose(); }
        }));
        test("a new job during the completion return continues from the current pose without a flash", () -> SwingUtilities.invokeAndWait(() -> {
            long start = 1_000_000_000L;
            var clock = new java.util.concurrent.atomic.AtomicLong(start);
            var busy = new java.util.concurrent.atomic.AtomicBoolean(true);
            var animation = new ToolbarAnimation(busy::get, () -> true, clock::get);
            var target = new AnimationTarget();
            try {
                paintAnimation(animation, target);
                clock.set(start + 600_000_000L);
                busy.set(false);
                animation.refresh();
                clock.set(start + 840_000_000L);
                var returning = animation.pose(clock.get());
                busy.set(true);
                animation.refresh();
                require(animation.state() == ToolbarAnimation.State.BUSY && animation.isRunning()
                        && samePose(returning, animation.pose(clock.get())), "A new job flashed to a different pose during completion.");
                clock.addAndGet(240_000_000L);
                animation.refresh();
                require(samePose(animation.pose(clock.get()), CubeMotion.busyPose(.24)), "A resumed job did not join its fresh busy curve after the short blend.");
                checkUnitPose(animation.pose(clock.get()));
            } finally { animation.dispose(); }
        }));
        test("motion policy hidden targets and disposal stop both busy and completing timers without idle work", () -> SwingUtilities.invokeAndWait(() -> {
            var busy = new java.util.concurrent.atomic.AtomicBoolean(true);
            var motion = new java.util.concurrent.atomic.AtomicBoolean(false);
            var clock = new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);
            var animation = new ToolbarAnimation(busy::get, motion::get, clock::get);
            var target = new AnimationTarget();
            try {
                BufferedImage home = paintAnimation(animation, target);
                require(!animation.isRunning() && animation.state() == ToolbarAnimation.State.IDLE, "A disabled motion policy started a timer.");
                motion.set(true);
                animation.refresh();
                require(animation.isRunning(), "Enabling motion did not resume the visible busy job.");
                motion.set(false);
                animation.refresh();
                require(!animation.isRunning() && samePose(animation.pose(clock.get()), CubeMotion.home())
                        && sameImage(home, paintAnimation(animation, target)), "Motion policy did not restore the static home immediately.");
                motion.set(true);
                animation.refresh();
                clock.addAndGet(500_000_000L);
                busy.set(false);
                animation.refresh();
                require(animation.state() == ToolbarAnimation.State.SETTLING, "Synthetic completed job did not enter its return.");
                target.showing = false;
                animation.refresh();
                require(!animation.isRunning() && animation.state() == ToolbarAnimation.State.IDLE, "Hidden completion kept its timer alive.");
                target.showing = true;
                busy.set(true);
                animation.refresh();
                require(!animation.isRunning(), "A removed hidden target was retained for animation.");
                paintAnimation(animation, target);
                require(animation.isRunning(), "A new visible repaint did not reattach its busy target.");
                animation.dispose();
                int repaintCount = target.repaints;
                animation.refresh();
                require(!animation.isRunning() && sameImage(home, paintAnimation(animation, target)) && target.repaints == repaintCount,
                        "Disposed animation restarted or retained repaint work.");
            } finally { animation.dispose(); }
        }));
        test("screen reader changes stop and resume the same busy icon without repaint or refresh calls", () -> SwingUtilities.invokeAndWait(() -> {
            boolean originalScreenReader = ScreenReader.isActive();
            var animation = new ToolbarAnimation(() -> true, () -> !ScreenReader.isActive(), () -> 1_000_000_000L);
            var target = new AnimationTarget();
            try {
                ScreenReader.setActive(false);
                animation.attachScreenReaderPolicyListener();
                animation.attachScreenReaderPolicyListener();
                paintAnimation(animation, target);
                require(animation.isRunning(), "Initial busy icon did not animate.");
                int repaintsBeforePolicy = target.repaints;
                ScreenReader.setActive(true);
                require(!animation.isRunning() && animation.state() == ToolbarAnimation.State.IDLE
                        && target.repaints > repaintsBeforePolicy, "Screen reader activation did not stop and repaint the busy icon.");
                repaintsBeforePolicy = target.repaints;
                ScreenReader.setActive(false);
                require(animation.isRunning() && target.repaints > repaintsBeforePolicy,
                        "The same busy job did not resume when the screen reader was disabled.");
                animation.dispose();
                int repaintsAfterDisposal = target.repaints;
                animation.attachScreenReaderPolicyListener();
                ScreenReader.setActive(true);
                ScreenReader.setActive(false);
                require(!animation.isRunning() && target.repaints == repaintsAfterDisposal,
                        "Disposed screen reader registration restarted animation or retained repaint work.");
            } finally {
                try { animation.dispose(); }
                finally { ScreenReader.setActive(originalScreenReader); }
            }
        }));
    }

    private static boolean nearVector(CubeMotion.Vec first, CubeMotion.Vec second, double tolerance) {
        return Math.abs(first.x() - second.x()) <= tolerance && Math.abs(first.y() - second.y()) <= tolerance
                && Math.abs(first.z() - second.z()) <= tolerance;
    }

    private static List<CubeMotion.Quaternion> rotations(CubeMotion.Pose pose) { return List.of(pose.outer(), pose.middle(), pose.inner()); }
    private static List<CubeMotion.Vec> axes() { return List.of(new CubeMotion.Vec(1, 0, 0), new CubeMotion.Vec(0, 1, 0), new CubeMotion.Vec(0, 0, 1)); }

    private static boolean samePose(CubeMotion.Pose first, CubeMotion.Pose second) {
        for (int cube = 0; cube < 3; cube++) for (var axis : axes()) {
            if (!nearVector(rotations(first).get(cube).rotate(axis), rotations(second).get(cube).rotate(axis), 1e-9)) return false;
        }
        return true;
    }

    private static void checkUnitPose(CubeMotion.Pose pose) {
        for (var rotation : rotations(pose)) require(Double.isFinite(rotation.dot(rotation)) && Math.abs(rotation.dot(rotation) - 1) < 1e-9,
                "Motion produced a nonfinite or nonunit rotation.");
    }

    private static void checkVelocityContinuation(CubeMotion.Pose before, CubeMotion.Pose atFinish, CubeMotion.Pose after) {
        for (int cube = 0; cube < 3; cube++) for (var axis : axes()) {
            var previous = rotations(before).get(cube).rotate(axis);
            var current = rotations(atFinish).get(cube).rotate(axis);
            var next = rotations(after).get(cube).rotate(axis);
            require(nearVector(new CubeMotion.Vec(current.x() - previous.x(), current.y() - previous.y(), current.z() - previous.z()),
                    new CubeMotion.Vec(next.x() - current.x(), next.y() - current.y(), next.z() - current.z()), 2e-5),
                    "Completion changed angular velocity abruptly at the return boundary.");
        }
    }

    private static final class AnimationTarget extends javax.swing.JComponent {
        private boolean showing = true;
        private int repaints;
        @Override public boolean isShowing() { return showing; }
        @Override public void repaint() { repaints++; }
    }

    private static BufferedImage paintAnimation(ToolbarAnimation animation, AnimationTarget target) {
        var image = new BufferedImage(animation.getIconWidth() + 8, animation.getIconHeight() + 8, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { animation.paintIcon(target, graphics, 3, 4); }
        finally { graphics.dispose(); }
        return image;
    }

    private static boolean sameImage(BufferedImage first, BufferedImage second) {
        return first.getWidth() == second.getWidth() && first.getHeight() == second.getHeight()
                && java.util.Arrays.equals(first.getRGB(0, 0, first.getWidth(), first.getHeight(), null, 0, first.getWidth()),
                second.getRGB(0, 0, second.getWidth(), second.getHeight(), null, 0, second.getWidth()));
    }

    private static void overviewTests() throws Exception {
        String picture = "https://raw.githubusercontent.com/nvxraM/CodexCommit/"
                + "3cbb38e74bb64aedf8478d5c85003edfa70661e1/plugin/docs/images/generate-button.png";
        test("Marketplace description without IDs retains the trusted bundled preview", () -> {
            HTMLDocument document = overviewDocument("<div><ul><li>Select your Git changes.</li></ul><p><a href=\""
                    + picture + "\"><img src=\"" + picture + "\" width=\"640\" height=\"135\"></a></p></div>");
            require(document.getElement("codexcommit-overview") == null && document.getElement("codexcommit-button-preview") == null,
                    "Sanitized fixture still contains descriptor IDs.");
            require(CodexCommitOverview.ownDocument(document, false), "Trusted sanitized preview was not recognized.");
        });
        test("Overview ignores foreign missing or altered image sources and links despite copied IDs", () -> {
            for (String foreign : List.of("https://example.invalid/picture.png", picture + "?other=1", picture + "#other",
                    picture.replace("https://", "http://"), picture.replace("raw.githubusercontent.com", "raw.githubusercontent.com.example.invalid"),
                    "data:image/png;base64,AAAA", "images/generate-button.png", "")) {
                HTMLDocument foreignSource = overviewDocument("<div id=\"codexcommit-overview\"><a href=\"" + picture
                        + "\"><img id=\"codexcommit-button-preview\" src=\"" + foreign + "\"></a></div>");
                require(!CodexCommitOverview.ownDocument(foreignSource, true), "Untrusted preview source was recognized.");
                HTMLDocument foreignLink = overviewDocument("<div id=\"codexcommit-overview\"><a href=\"" + foreign
                        + "\"><img id=\"codexcommit-button-preview\" src=\"" + picture + "\"></a></div>");
                require(!CodexCommitOverview.ownDocument(foreignLink, false)
                        && !CodexCommitOverview.ownDocument(foreignLink, true), "Untrusted preview link was recognized.");
            }
            require(!CodexCommitOverview.ownDocument(overviewDocument("<a href=\"" + picture + "\"><img width=\"640\"></a>"), true),
                    "Source removed by sanitization was treated as an arbitrary image.");
        });
        test("Overview replaces only its exact image when a description contains unrelated images", () -> {
            HTMLDocument document = overviewDocument("<img src=\"https://example.invalid/other.png\">"
                    + "<a href=\"" + picture + "\"><img src=\"" + picture + "\"></a>"
                    + "<img id=\"codexcommit-button-preview\" src=\"data:image/png;base64,AAAA\">");
            int recognized = 0;
            ElementIterator elements = new ElementIterator(document);
            for (Element element = elements.next(); element != null; element = elements.next())
                if (CodexCommitOverview.previewElement(element, false)) recognized++;
            require(recognized == 1, "The description changed unrelated or multiple images.");
        });
        test("cached Marketplace image without a source requires the exact legacy tuple and own plugin scope", () -> {
            String oldLink = "https://github.com/nvxraM/CodexCommit/blob/main/plugin/docs/images/generate-button.png";
            String alt = "CodexCommit button. Click to view full screen.";
            String legacy = "<a href=\"" + oldLink + "\"><img width=\"640\" height=\"135\" alt=\"" + alt + "\"></a>";
            HTMLDocument published = overviewDocument(legacy);
            List<HTMLDocument> variants = new ArrayList<>();
            for (String changed : List.of(legacy.replace(oldLink, picture), legacy.replace(oldLink, oldLink + "?raw=true"),
                    legacy.replace(alt, alt + " other"), legacy.replace("width=\"640\"", "width=\"641\""),
                    legacy.replace("height=\"135\"", "height=\"134\""), legacy.replace("<img ", "<img src=\"\" "),
                    legacy.replace("<img ", "<img src=\"data:image/png;base64,AAAA\" ")))
                variants.add(overviewDocument(changed));
            SwingUtilities.invokeAndWait(() -> {
                JEditorPane pane = new JEditorPane();
                pane.setDocument(published);
                JPanel panel = new JPanel();
                panel.add(pane);
                require(!CodexCommitOverview.ownDocument(pane), "Unscoped legacy Marketplace image was accepted.");
                JLabel identifier = new JLabel("Plugin ID: other.publisher.plugin");
                panel.add(identifier);
                require(!CodexCommitOverview.ownDocument(pane), "Another plugin's legacy image was accepted.");
                identifier.setText("Plugin ID: local.codex.commitmessage");
                require(CodexCommitOverview.ownDocument(pane), "The exact published legacy image was not restored in its own plugin.");
                for (HTMLDocument variant : variants) {
                    pane.setDocument(variant);
                    require(!CodexCommitOverview.ownDocument(pane), "An altered legacy image tuple was accepted.");
                }
            });
        });
        test("Overview requires its plugin scope for an unlinked image and rejects another plugin scope", () -> {
            HTMLDocument linked = overviewDocument("<a href=\"" + picture + "\"><img src=\"" + picture + "\"></a>");
            HTMLDocument unlinked = overviewDocument("<img src=\"" + picture + "\">");
            SwingUtilities.invokeAndWait(() -> {
                JEditorPane pane = new JEditorPane();
                pane.setDocument(unlinked);
                JPanel panel = new JPanel();
                panel.add(pane);
                require(!CodexCommitOverview.ownDocument(pane), "Unlinked image was accepted without the plugin scope.");
                JLabel identifier = new JLabel("Plugin ID: local.codex.commitmessage");
                panel.add(identifier);
                require(CodexCommitOverview.ownDocument(pane), "The plugin's unlinked image was not recognized.");
                identifier.setText("Plugin ID: other.publisher.plugin");
                pane.setDocument(linked);
                require(!CodexCommitOverview.ownDocument(pane), "Another plugin's description was enhanced.");
                identifier.setText("Plugin ID: local.codex.commitmessage");
                require(CodexCommitOverview.ownDocument(pane), "The plugin's sanitized linked description was not recognized.");
                panel.add(new JLabel("Plugin ID: other.publisher.plugin"));
                require(!CodexCommitOverview.ownDocument(pane), "Ambiguous plugin scope was accepted.");
            });
        });
        test("Additional Info has only Documentation and Privacy with equal flush-left rows", () -> SwingUtilities.invokeAndWait(() -> {
            JPanel links = CodexCommitOverview.additionalInfoLinks();
            require(links.getComponentCount() == 2, "Native repository or license links were duplicated.");
            JButton documentation = (JButton) links.getComponent(0);
            JButton privacy = (JButton) links.getComponent(1);
            require(documentation.getText().equals("<html><a href=\"\">Documentation</a></html>")
                    && privacy.getText().equals("<html><a href=\"\">Privacy</a></html>"), "Additional Info has unexpected link labels.");
            require(documentation.getHorizontalAlignment() == SwingConstants.LEFT && privacy.getHorizontalAlignment() == SwingConstants.LEFT
                    && links.getAlignmentX() == Component.LEFT_ALIGNMENT, "Additional Info text is not left aligned.");
            Dimension preferred = links.getPreferredSize();
            require(links.getMinimumSize().equals(preferred) && links.getMaximumSize().equals(preferred),
                    "Additional Info allows differently sized outer cells.");
            for (int extra : new int[]{0, 150}) {
                links.setSize(preferred.width + extra, preferred.height);
                links.doLayout();
                require(documentation.getX() == 0 && privacy.getX() == 0
                        && documentation.getWidth() == privacy.getWidth() && documentation.getWidth() == links.getWidth(),
                        "Additional Info forms a staircase or changes row widths.");
                require(documentation.getY() < privacy.getY(), "Additional Info rows overlap or change order.");
            }
            JPanel parent = new JPanel();
            parent.setLayout(new BoxLayout(parent, BoxLayout.Y_AXIS));
            JLabel nativeLink = new JLabel("Source code");
            nativeLink.setAlignmentX(Component.LEFT_ALIGNMENT);
            parent.add(nativeLink);
            parent.add(links);
            parent.setSize(preferred.width + 150, preferred.height + nativeLink.getPreferredSize().height);
            parent.doLayout();
            require(links.getX() == nativeLink.getX(), "Additional Info does not align with native rows.");
        }));
        test("Additional Info fills only missing native documentation and privacy links", () -> {
            HTMLDocument policyLink = overviewDocument("<a href=\"https://github.com/nvxraM/CodexCommit/blob/main/plugin/docs/PRIVACY.md\">Policy</a>");
            SwingUtilities.invokeAndWait(() -> {
                JPanel nativeInfo = new JPanel();
                nativeInfo.add(new JButton("Source code"));
                nativeInfo.add(new JLabel("License"));
                nativeInfo.add(new JButton("Report plugin"));
                require(CodexCommitOverview.additionalInfoLinks(nativeInfo).getComponentCount() == 2,
                        "Native source, license, or report rows suppressed unrelated fallback links.");
                JButton documentation = new JButton("Documentation");
                nativeInfo.add(documentation);
                JPanel privacyOnly = CodexCommitOverview.additionalInfoLinks(nativeInfo);
                require(privacyOnly.getComponentCount() == 1 && ((JButton) privacyOnly.getComponent(0)).getText().contains("Privacy"),
                        "Native Documentation was duplicated or the missing Privacy link disappeared.");
                JLabel privacy = new JLabel("<html><a href=\"\">Privacy Policy</a></html>");
                nativeInfo.add(privacy);
                require(CodexCommitOverview.additionalInfoLinks(nativeInfo) == null, "Existing native documentation and privacy links were duplicated.");
                nativeInfo.remove(documentation);
                JPanel documentationOnly = CodexCommitOverview.additionalInfoLinks(nativeInfo);
                require(documentationOnly.getComponentCount() == 1 && ((JButton) documentationOnly.getComponent(0)).getText().contains("Documentation"),
                        "Native Privacy was duplicated or the missing Documentation link disappeared.");
                nativeInfo.remove(privacy);
                nativeInfo.add(new JLabel("<html><a href=\"https://github.com/nvxraM/CodexCommit/blob/main/plugin/docs/USAGE.md\">Read the guide</a></html>"));
                JEditorPane policy = new JEditorPane();
                policy.setDocument(policyLink);
                nativeInfo.add(policy);
                require(CodexCommitOverview.additionalInfoLinks(nativeInfo) == null, "Exact native target URLs were duplicated under different labels.");
            });
        });
        test("Additional Info responds to late native links and removes its own panel when another plugin opens", () -> {
            CodexCommitOverview[] service = new CodexCommitOverview[1];
            JPanel[] nativeInfo = new JPanel[1];
            JLabel[] identifier = new JLabel[1];
            JButton documentation = new JButton("Documentation");
            JButton privacy = new JButton("Privacy Policy");
            try {
                SwingUtilities.invokeAndWait(() -> {
                    service[0] = new CodexCommitOverview();
                    JPanel panel = new JPanel();
                    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                    nativeInfo[0] = panel;
                    panel.add(new JButton("Source code"));
                    identifier[0] = new JLabel("Plugin ID: local.codex.commitmessage") {
                        @Override public boolean isDisplayable() { return true; }
                    };
                    panel.add(identifier[0]);
                    invokeOverview(service[0], "watch", JLabel.class, identifier[0]);
                    require(panel.getComponent(0) instanceof JPanel, "Fallback links are not grouped above native metadata.");
                    require(overviewFallbackLabels(panel).equals(List.of("Documentation", "Privacy")), "Initial fallback links are missing.");
                    panel.add(documentation);
                    invokeOverview(service[0], "event", java.awt.AWTEvent.class, new java.awt.event.ContainerEvent(panel,
                            java.awt.event.ContainerEvent.COMPONENT_ADDED, documentation));
                });
                SwingUtilities.invokeAndWait(() -> {
                    require(overviewFallbackLabels(nativeInfo[0]).equals(List.of("Privacy")), "Late native Documentation left a duplicate fallback.");
                    nativeInfo[0].add(privacy);
                    invokeOverview(service[0], "event", java.awt.AWTEvent.class, new java.awt.event.ContainerEvent(nativeInfo[0],
                            java.awt.event.ContainerEvent.COMPONENT_ADDED, privacy));
                });
                SwingUtilities.invokeAndWait(() -> {
                    require(overviewFallbackLabels(nativeInfo[0]).isEmpty(), "Late native Privacy left a duplicate fallback.");
                    nativeInfo[0].remove(documentation);
                    invokeOverview(service[0], "event", java.awt.AWTEvent.class, new java.awt.event.ContainerEvent(nativeInfo[0],
                            java.awt.event.ContainerEvent.COMPONENT_REMOVED, documentation));
                });
                SwingUtilities.invokeAndWait(() -> {
                    require(overviewFallbackLabels(nativeInfo[0]).equals(List.of("Documentation")), "Removing a native link did not restore its fallback.");
                    identifier[0].setText("Plugin ID: other.publisher.plugin");
                });
                SwingUtilities.invokeAndWait(() -> {
                    require(overviewFallbackLabels(nativeInfo[0]).isEmpty(), "The fallback panel remained attached to another plugin.");
                    require(privacy.getParent() == nativeInfo[0], "The service removed a native link.");
                });
            } finally {
                if (service[0] != null) SwingUtilities.invokeAndWait(service[0]::dispose);
            }
        });
    }

    private static void invokeOverview(CodexCommitOverview service, String name, Class<?> type, Object argument) {
        try {
            var method = CodexCommitOverview.class.getDeclaredMethod(name, type);
            method.setAccessible(true);
            method.invoke(service, argument);
        } catch (ReflectiveOperationException error) { throw new AssertionError("Cannot exercise the Overview lifecycle.", error); }
    }

    private static List<String> overviewFallbackLabels(JPanel nativeInfo) {
        List<String> result = new ArrayList<>();
        for (Component child : nativeInfo.getComponents()) {
            if (child instanceof JPanel links) for (Component link : links.getComponents())
                if (link instanceof JButton button) result.add(button.getText().replaceAll("<[^>]+>", "").trim());
        }
        return result;
    }

    private static HTMLDocument overviewDocument(String markup) throws Exception {
        HTMLEditorKit kit = new HTMLEditorKit();
        HTMLDocument document = (HTMLDocument) kit.createDefaultDocument();
        kit.read(new java.io.StringReader(markup), document, 0);
        return document;
    }

    private static void screenshotLayoutTests() throws Exception {
        test("screenshot decoder returns complete PNG pixels and rejects malformed image streams", () -> {
            BufferedImage original = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
            int[] pixels = {Color.RED.getRGB(), Color.GREEN.getRGB(), Color.BLUE.getRGB(), 0x80402010, 0xff345678, 0xfffefdfc};
            original.setRGB(0, 0, 3, 2, pixels, 0, 3);
            var output = new java.io.ByteArrayOutputStream();
            require(javax.imageio.ImageIO.write(original, "PNG", output), "Synthetic PNG writer is unavailable.");
            byte[] png = output.toByteArray();
            BufferedImage decoded = ScreenshotViewer.decode(new java.io.ByteArrayInputStream(png));
            require(decoded.getWidth() == 3 && decoded.getHeight() == 2
                    && java.util.Arrays.equals(pixels, decoded.getRGB(0, 0, 3, 2, null, 0, 3)), "Screenshot decoding changed dimensions or pixels.");
            rejects(() -> ScreenshotViewer.decode(new java.io.ByteArrayInputStream(new byte[]{'n', 'o', 't', '-', 'p', 'n', 'g'})));
            rejects(() -> ScreenshotViewer.decode(new java.io.ByteArrayInputStream(java.util.Arrays.copyOf(png, 12))));
        });
        test("screenshot fits wide and narrow viewports without clipping or distortion", () -> {
            Rectangle wide = ScreenshotViewer.fit(1000, 600, 1100, 232);
            Rectangle narrow = ScreenshotViewer.fit(280, 500, 1100, 232);
            checkImageFit(wide, 1000, 600, 1100, 232);
            checkImageFit(narrow, 280, 500, 1100, 232);
            require(wide.width > narrow.width && wide.height > narrow.height, "Screenshot did not shrink with the viewport.");
        });
        test("portrait and exact-size screenshots stay centred and fully visible", () -> {
            Rectangle portrait = ScreenshotViewer.fit(1200, 600, 240, 960);
            checkImageFit(portrait, 1200, 600, 240, 960);
            require(portrait.height > portrait.width && portrait.x > 0 && portrait.y == 0, "Portrait screenshot did not fit the available height.");
            Rectangle exact = ScreenshotViewer.fit(311, 177, 311, 177);
            require(exact.equals(new Rectangle(0, 0, 311, 177)), "Exact-size screenshot changed its size or position.");
        });
        test("empty viewports and invalid screenshot dimensions have no drawable bounds", () -> {
            for (int[] dimensions : List.of(new int[]{0, 100, 80, 40}, new int[]{100, 0, 80, 40},
                    new int[]{-1, 100, 80, 40}, new int[]{100, -1, 80, 40},
                    new int[]{100, 100, 0, 40}, new int[]{100, 100, 80, 0}, new int[]{100, 100, -1, 40})) {
                Rectangle bounds = ScreenshotViewer.fit(dimensions[0], dimensions[1], dimensions[2], dimensions[3]);
                require(bounds.isEmpty() && bounds.x == 0 && bounds.y == 0, "Invalid viewport produced drawable screenshot bounds.");
            }
        });
        test("painted screenshot follows viewport resizing and preserves every image corner inside its insets", () -> {
            SwingUtilities.invokeAndWait(() -> {
                BufferedImage picture = new BufferedImage(160, 80, BufferedImage.TYPE_INT_RGB);
                var source = picture.createGraphics();
                try {
                    source.setColor(Color.WHITE);
                    source.fillRect(0, 0, 160, 80);
                    source.setColor(Color.RED);
                    source.fillRect(0, 0, 12, 12);
                    source.setColor(Color.GREEN);
                    source.fillRect(148, 0, 12, 12);
                    source.setColor(Color.BLUE);
                    source.fillRect(0, 68, 12, 12);
                    source.setColor(Color.YELLOW);
                    source.fillRect(148, 68, 12, 12);
                } finally { source.dispose(); }
                var panel = new ScreenshotViewer.ImagePanel(picture);
                panel.setBorder(BorderFactory.createEmptyBorder(7, 11, 13, 17));
                Rectangle small = checkPaintedImage(panel, 240, 180, 160, 80);
                Rectangle grown = checkPaintedImage(panel, 640, 360, 160, 80);
                Rectangle shrunk = checkPaintedImage(panel, 200, 320, 160, 80);
                require(grown.width > small.width && grown.height > small.height
                        && shrunk.width < small.width && shrunk.height < small.height, "Painted image did not grow and shrink with the viewport.");
            });
        });
    }

    private static void checkImageFit(Rectangle bounds, int width, int height, int pictureWidth, int pictureHeight) {
        require(bounds.width > 0 && bounds.height > 0 && new Rectangle(0, 0, width, height).contains(bounds), "Screenshot leaves the viewport.");
        require(Math.abs(bounds.x - (width - bounds.x - bounds.width)) <= 1
                && Math.abs(bounds.y - (height - bounds.y - bounds.height)) <= 1, "Screenshot is not centred.");
        require(bounds.width == width || bounds.height == height, "Screenshot wastes space on both axes.");
        long aspectError = Math.abs((long) bounds.width * pictureHeight - (long) bounds.height * pictureWidth);
        require(aspectError <= Math.max(pictureWidth, pictureHeight), "Screenshot aspect ratio changed beyond pixel rounding.");
    }

    private static Rectangle checkPaintedImage(ScreenshotViewer.ImagePanel panel, int width, int height, int pictureWidth, int pictureHeight) {
        panel.setSize(width, height);
        BufferedImage painted = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = painted.createGraphics();
        try { panel.paint(graphics); }
        finally { graphics.dispose(); }
        int minimumX = width, minimumY = height, maximumX = -1, maximumY = -1;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (painted.getRGB(x, y) != panel.getBackground().getRGB()) {
                minimumX = Math.min(minimumX, x);
                minimumY = Math.min(minimumY, y);
                maximumX = Math.max(maximumX, x);
                maximumY = Math.max(maximumY, y);
            }
        }
        require(maximumX >= minimumX && maximumY >= minimumY, "Screenshot was not painted.");
        Rectangle bounds = new Rectangle(minimumX, minimumY, maximumX - minimumX + 1, maximumY - minimumY + 1);
        Insets insets = panel.getInsets();
        checkImageFit(new Rectangle(bounds.x - insets.left, bounds.y - insets.top, bounds.width, bounds.height),
                width - insets.left - insets.right, height - insets.top - insets.bottom, pictureWidth, pictureHeight);
        int offset = Math.max(1, Math.min(bounds.width, bounds.height) / 40);
        require(painted.getRGB(bounds.x + offset, bounds.y + offset) == Color.RED.getRGB()
                && painted.getRGB(bounds.x + bounds.width - 1 - offset, bounds.y + offset) == Color.GREEN.getRGB()
                && painted.getRGB(bounds.x + offset, bounds.y + bounds.height - 1 - offset) == Color.BLUE.getRGB()
                && painted.getRGB(bounds.x + bounds.width - 1 - offset, bounds.y + bounds.height - 1 - offset) == Color.YELLOW.getRGB(),
                "Resizing clipped or changed an image corner.");
        return bounds;
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
        test("workspace metadata rejects symlink leaves and linked project ancestors", () -> {
            Path targetDirectory = Files.createDirectories(fixture.resolve("workspace-link-target/.idea"));
            Path workspaceTarget = targetDirectory.resolve("workspace.xml");
            Files.writeString(workspaceTarget, "synthetic workspace\n");
            Path leafDirectory = Files.createDirectories(fixture.resolve("workspace-link-leaf/.idea"));
            Path workspaceLink = leafDirectory.resolve("workspace.xml");
            Files.createSymbolicLink(workspaceLink, workspaceTarget);
            rejects(() -> ChangeSnapshot.validateWorkspaceLocation(fixture, null, workspaceLink));
            rejects(() -> ChangeSnapshot.validateWorkspaceLocation(fixture, workspaceLink, null));
            Path parent = Files.createDirectories(fixture.resolve("workspace-link-parent"));
            Files.createSymbolicLink(parent.resolve(".idea"), targetDirectory);
            rejects(() -> ChangeSnapshot.validateWorkspaceLocation(fixture, null, parent.resolve(".idea/workspace.xml")));
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

    private static FilePath testFilePath(String relative, boolean directory) {
        String path = fixture.resolve(relative).toString();
        return (FilePath) Proxy.newProxyInstance(FilePath.class.getClassLoader(), new Class<?>[]{FilePath.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPath", "toString" -> path;
                    case "isDirectory" -> directory;
                    case "isNonLocal" -> false;
                    case "getVirtualFile" -> null;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
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
