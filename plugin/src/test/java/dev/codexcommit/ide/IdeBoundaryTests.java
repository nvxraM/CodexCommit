package dev.codexcommit.ide;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListChange;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.ex.ExclusionState;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.ui.accessibility.ScreenReader;
import java.awt.Color;
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
import javax.swing.SwingUtilities;

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
            screenshotLayoutTests();
            toolbarAnimationTests();
            symlinkTest();
            System.out.println("IDE boundary tests: " + passed + " passed, 0 failed, " + skipped + " skipped. Synthetic local fixtures only; no model request.");
        } finally { cleanup(); }
    }

    private static void toolbarAnimationTests() throws Exception {
        test("toolbar animation stays idle without a visible target or complete frames", () -> SwingUtilities.invokeAndWait(() -> {
            var busy = new java.util.concurrent.atomic.AtomicBoolean(false);
            var clock = new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);
            var animation = new ToolbarAnimation(solidIcon(Color.RED), animationFrames(), busy::get, () -> true, clock::get);
            var target = new AnimationTarget();
            try {
                animation.refresh();
                require(!animation.isRunning() && animation.frameIndex(clock.get()) == -1, "Idle animation started a timer.");
                busy.set(true);
                animation.refresh();
                require(!animation.isRunning(), "Animation started without a visible painted target.");
                target.showing = false;
                require(paintAnimation(animation, target) == Color.RED.getRGB() && !animation.isRunning(), "Hidden toolbar target animated.");
                require(animation.getIconWidth() == 16 && animation.getIconHeight() == 16, "Animation changed toolbar icon dimensions.");
            } finally { animation.dispose(); }
            var missingFrames = new ToolbarAnimation(solidIcon(Color.RED), new javax.swing.Icon[0], () -> true, () -> true, clock::get);
            try {
                target.showing = true;
                require(paintAnimation(missingFrames, target) == Color.RED.getRGB() && !missingFrames.isRunning(), "Missing animation frames did not retain the static icon.");
            } finally { missingFrames.dispose(); }
        }));
        test("toolbar animation follows elapsed time and wraps its loop without restarting on refresh", () -> SwingUtilities.invokeAndWait(() -> {
            long start = 1_000_000_000L;
            var clock = new java.util.concurrent.atomic.AtomicLong(start);
            var animation = new ToolbarAnimation(solidIcon(Color.RED), animationFrames(), () -> true, () -> true, clock::get);
            var target = new AnimationTarget();
            try {
                require(paintAnimation(animation, target) == new Color(10, 40, 100).getRGB() && animation.isRunning(), "Busy visible toolbar did not paint its first frame.");
                require(animation.frameIndex(start + 79_999_999L) == 0 && animation.frameIndex(start + 80_000_000L) == 1,
                        "Animation frame boundary differs from the supplied 80 ms timing.");
                clock.set(start + 80_000_000L);
                animation.refresh();
                require(animation.frameIndex(clock.get()) == 1 && paintAnimation(animation, target) == new Color(11, 40, 100).getRGB(), "Refreshing a running animation reset its progress or painted the wrong frame.");
                require(animation.frameIndex(start + 3_199_999_999L) == 39 && animation.frameIndex(start + 3_200_000_000L) == 0,
                        "Animation does not wrap the 40-frame, 3.2-second loop.");
            } finally { animation.dispose(); }
        }));
        test("motion policy keeps the static toolbar icon and stops an active timer", () -> SwingUtilities.invokeAndWait(() -> {
            var motion = new java.util.concurrent.atomic.AtomicBoolean(false);
            var animation = new ToolbarAnimation(solidIcon(Color.RED), animationFrames(), () -> true, motion::get, () -> 1_000_000_000L);
            var target = new AnimationTarget();
            try {
                require(paintAnimation(animation, target) == Color.RED.getRGB() && !animation.isRunning(), "A disabled motion policy started an animation.");
                motion.set(true);
                animation.refresh();
                require(animation.isRunning(), "Enabling motion did not start the visible busy icon.");
                motion.set(false);
                animation.refresh();
                require(!animation.isRunning() && paintAnimation(animation, target) == Color.RED.getRGB(), "Disabling motion did not stop the timer and restore the static icon.");
            } finally { animation.dispose(); }
        }));
        test("screen reader changes stop and resume the same busy icon without repaint or refresh calls", () -> SwingUtilities.invokeAndWait(() -> {
            boolean originalScreenReader = ScreenReader.isActive();
            var animation = new ToolbarAnimation(solidIcon(Color.RED), animationFrames(), () -> true,
                    () -> !ScreenReader.isActive(), () -> 1_000_000_000L);
            var target = new AnimationTarget();
            try {
                ScreenReader.setActive(false);
                animation.attachScreenReaderPolicyListener();
                animation.attachScreenReaderPolicyListener();
                paintAnimation(animation, target);
                require(animation.isRunning(), "Initial busy icon did not animate.");
                int repaintsBeforePolicy = target.repaints;
                ScreenReader.setActive(true);
                require(!animation.isRunning() && animation.frameIndex(1_000_000_000L) == -1
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
        test("job completion hidden targets and disposal stop toolbar animation and further repaints", () -> SwingUtilities.invokeAndWait(() -> {
            var busy = new java.util.concurrent.atomic.AtomicBoolean(true);
            var animation = new ToolbarAnimation(solidIcon(Color.RED), animationFrames(), busy::get, () -> true, () -> 1_000_000_000L);
            var target = new AnimationTarget();
            try {
                paintAnimation(animation, target);
                require(animation.isRunning(), "Visible busy icon did not start.");
                busy.set(false);
                animation.refresh();
                require(!animation.isRunning() && animation.frameIndex(2_000_000_000L) == -1, "Completed job kept animating.");
                busy.set(true);
                animation.refresh();
                require(animation.isRunning(), "A new busy job did not restart its visible icon.");
                target.showing = false;
                animation.refresh();
                require(!animation.isRunning(), "Hidden target kept its timer alive.");
                target.showing = true;
                animation.refresh();
                require(!animation.isRunning(), "A removed hidden target was retained for animation.");
                paintAnimation(animation, target);
                require(animation.isRunning(), "Visible repaint did not reattach the target.");
                animation.dispose();
                int repaintCount = target.repaints;
                animation.refresh();
                require(paintAnimation(animation, target) == Color.RED.getRGB() && !animation.isRunning()
                        && target.repaints == repaintCount, "Disposed animation restarted or retained a repaint target.");
            } finally { animation.dispose(); }
        }));
    }

    private static final class AnimationTarget extends javax.swing.JComponent {
        private boolean showing = true;
        private int repaints;
        @Override public boolean isShowing() { return showing; }
        @Override public void repaint() { repaints++; }
    }

    private static javax.swing.Icon solidIcon(Color color) {
        return new javax.swing.Icon() {
            @Override public int getIconWidth() { return 16; }
            @Override public int getIconHeight() { return 16; }
            @Override public void paintIcon(java.awt.Component component, java.awt.Graphics graphics, int x, int y) {
                graphics.setColor(color);
                graphics.fillRect(x, y, getIconWidth(), getIconHeight());
            }
        };
    }

    private static javax.swing.Icon[] animationFrames() {
        var frames = new javax.swing.Icon[40];
        for (int index = 0; index < frames.length; index++) frames[index] = solidIcon(new Color(10 + index, 40, 100));
        return frames;
    }

    private static int paintAnimation(ToolbarAnimation animation, AnimationTarget target) {
        var image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { animation.paintIcon(target, graphics, 3, 4); }
        finally { graphics.dispose(); }
        return image.getRGB(4, 5);
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
