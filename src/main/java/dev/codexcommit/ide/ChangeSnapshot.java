package dev.codexcommit.ide;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListChange;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.ex.ExclusionState;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManagerI;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.commit.CommitWorkflowUi;
import dev.codexcommit.core.ContentPolicy;
import git4idea.config.GitExecutable;
import git4idea.config.GitExecutableManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/** Captures only checked whole-file Git changes, then verifies the same selection before applying. */
final class ChangeSnapshot {
    static final int MAX_FILES = 500;

    /** Validation text may include an escaped project-relative name, never filesystem exception text. */
    static final class SelectionException extends IOException {
        SelectionException(String message) { super(message); }
    }
    private static final Set<String> SENSITIVE_NAMES = Set.of("credentials", "credentials.json", "secrets.json", "secrets.yml", "secrets.yaml",
            "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519", ".npmrc", ".pypirc", ".netrc", "settings.xml", "kubeconfig",
            "config.json", "terraform.tfstate", "terraform.tfstate.backup");
    private static final Set<String> SENSITIVE_EXTENSIONS = Set.of("pem", "key", "p12", "pfx", "jks", "keystore", "kdbx", "gpg", "asc",
            "env", "sqlite", "sqlite3", "db", "dump", "bak", "log", "fasta", "fa", "fna", "fastq", "fq", "bam", "sam", "cram",
            "pretext", "cool", "mcool", "hic", "npy", "npz", "h5", "hdf5", "pt", "pth", "ckpt", "safetensors", "zip", "gz", "tar");
    private static final Set<String> SENSITIVE_DIRECTORIES = Set.of(".git", ".idea", ".ssh", ".aws", ".azure", ".kube", ".gnupg", ".codex");

    private record Entry(Path before, Path after, Path repository, String revision, String beforeName, String afterName,
                         boolean workspace, Document document, long documentStamp, CharSequence documentText, VirtualFile file, long fileStamp) {}
    private record DiskState(long size, FileTime modified, Object key) {}
    private record Prepared(Entry entry, LocalGitReader.Blob blob, String documentText, DiskState disk, String diskHash) {}

    private final Project project;
    private final CommitWorkflowUi ui;
    private final Path root;
    private final Path git;
    private final String selection;
    private final String originalMessage;
    private final Document messageDocument;
    private final long messageStamp;
    private final List<Entry> entries;
    private List<Prepared> prepared = List.of();

    private ChangeSnapshot(Project project, CommitWorkflowUi ui, Path root, Path git, String selection,
                           String originalMessage, Document messageDocument, List<Entry> entries) {
        this.project = project;
        this.ui = ui;
        this.root = root;
        this.git = git;
        this.selection = selection;
        this.originalMessage = originalMessage;
        this.messageDocument = messageDocument;
        this.messageStamp = messageDocument.getModificationStamp();
        this.entries = entries;
    }

    /** Called on the EDT: captures selection, partial-changelist state, and already loaded documents. */
    static ChangeSnapshot capture(Project project, CommitWorkflowUi ui, Document messageDocument) throws IOException {
        if (project.getBasePath() == null) throw new SelectionException("A local project directory is required.");
        List<Change> changes = List.copyOf(ui.getIncludedChanges());
        if (changes.isEmpty() || changes.size() > MAX_FILES) throw new SelectionException("Select between 1 and 500 tracked files.");
        Path root = Path.of(project.getBasePath()).toAbsolutePath().normalize();
        GitExecutable configured = GitExecutableManager.getInstance().getExecutable(project);
        if (!configured.isLocal()) throw new SelectionException("Only local native Git is supported.");
        Path git;
        try {
            git = Path.of(configured.getExePath());
            if (!git.isAbsolute()) throw new IllegalArgumentException();
        } catch (RuntimeException ignored) {
            throw new SelectionException("Open Settings > Version Control > Git. Select the absolute path to a native Git executable.");
        }
        List<Entry> entries = new ArrayList<>();
        for (Change change : changes) {
            ContentRevision beforeRevision = change.getBeforeRevision();
            ContentRevision afterRevision = change.getAfterRevision();
            if (beforeRevision instanceof BinaryContentRevision || afterRevision instanceof BinaryContentRevision) {
                throw new SelectionException("Binary changes are not supported.");
            }
            if (afterRevision != null && !(afterRevision instanceof CurrentContentRevision)) {
                throw new SelectionException("Use the ordinary Commit changes view; staged or custom revisions are not supported.");
            }
            FilePath beforeFile = beforeRevision == null ? null : beforeRevision.getFile();
            FilePath afterFile = afterRevision == null ? null : afterRevision.getFile();
            Path before = checkedPath(root, beforeFile);
            Path after = checkedPath(root, afterFile);
            boolean workspace = workspaceChange(before == null ? null : root.relativize(before),
                    after == null ? null : root.relativize(after));
            FilePath active = afterFile != null ? afterFile : beforeFile;
            if (active == null) throw new SelectionException("Selected change is unavailable.");
            VcsRoot vcs = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(active);
            if (vcs == null || vcs.getVcs() == null || !"Git".equals(vcs.getVcs().getName())) {
                throw new SelectionException("Only Git changes are supported.");
            }
            Path repository = Path.of(vcs.getPath().getPath()).toAbsolutePath().normalize();
            if (!repository.startsWith(root) || (before != null && !before.startsWith(repository))
                    || (after != null && !after.startsWith(repository))) throw new SelectionException("The Git root must be inside this project.");
            checkPartial(project, change, afterFile);
            Document document = workspace || afterFile == null || afterFile.getVirtualFile() == null ? null
                    : FileDocumentManager.getInstance().getCachedDocument(afterFile.getVirtualFile());
            CharSequence text = null;
            long stamp = -1;
            if (document != null) {
                if (document.getTextLength() > LocalGitReader.MAX_FILE_BYTES
                        || FileDocumentManager.getInstance().isPartialPreviewOfALargeFile(document)) {
                    throw new SelectionException("Selected document exceeds 50 MiB or is only partly loaded by the IDE.");
                }
                text = document.getImmutableCharSequence();
                stamp = document.getModificationStamp();
            }
            String revision = beforeRevision == null ? null : beforeRevision.getRevisionNumber().asString();
            VirtualFile file = workspace || afterFile == null ? null : afterFile.getVirtualFile();
            entries.add(new Entry(before, after, repository, revision, relative(root, before), relative(root, after),
                    workspace, document, stamp, text, file, file == null ? -1 : file.getModificationStamp()));
        }
        return new ChangeSnapshot(project, ui, root, git, selectionKey(changes), ui.getCommitMessageUi().getText(), messageDocument, List.copyOf(entries));
    }

    /** Preflights every baseline object before reading any file body, then builds a bounded text patch. */
    String build(BooleanSupplier cancelled) throws Exception {
        LocalGitReader reader = new LocalGitReader(git, cancelled);
        List<Prepared> candidates = new ArrayList<>();
        long total = 0;
        for (Entry entry : entries) {
            if (cancelled.getAsBoolean()) throw new InterruptedException("Cancelled.");
            progress("Check selected files", candidates.size(), entries.size(), 0, 0.25);
            if (entry.before() != null) validateLocation(root, entry.before());
            if (entry.after() != null) validateLocation(root, entry.after());
            Path repository = entry.repository().toRealPath();
            if (!repository.startsWith(root.toRealPath())) throw new SelectionException("The Git root must be inside this project.");
            if (entry.workspace()) {
                validateWorkspaceLocation(root, entry.before(), entry.after());
                candidates.add(new Prepared(entry, null, null, null, null));
                continue;
            }
            DiskState disk = null;
            if (entry.after() != null) {
                BasicFileAttributes attributes = Files.readAttributes(entry.after(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isRegularFile()) throw new SelectionException("Only regular local files are supported.");
                if (attributes.size() > LocalGitReader.MAX_FILE_BYTES) throw new SelectionException("Selected file exceeds 50 MiB.");
                disk = new DiskState(attributes.size(), attributes.lastModifiedTime(), attributes.fileKey());
            }
            String documentText = entry.documentText() == null ? null : entry.documentText().toString();
            int documentBytes = documentText == null ? 0 : documentText.getBytes(StandardCharsets.UTF_8).length;
            if (documentBytes > LocalGitReader.MAX_FILE_BYTES) throw new SelectionException("Selected document exceeds 50 MiB.");
            LocalGitReader.Blob blob = entry.before() == null ? null : reader.inspect(entry.repository(), entry.revision(),
                    relative(entry.repository(), entry.before()));
            total += (blob == null ? 0 : blob.size()) + Math.max(disk == null ? 0 : disk.size(), documentBytes);
            if (total > LocalGitReader.MAX_TOTAL_BYTES) throw new SelectionException("Selected before and after content exceeds 50 MiB.");
            candidates.add(new Prepared(entry, blob, documentText, disk, null));
        }
        StringBuilder output = new StringBuilder();
        int patchBytes = 0;
        List<Prepared> completed = new ArrayList<>();
        for (Prepared candidate : candidates) {
            Entry entry = candidate.entry();
            if (cancelled.getAsBoolean()) throw new InterruptedException("Cancelled.");
            progress("Compare selected files", completed.size(), candidates.size(), 0.25, 0.75);
            if (entry.workspace()) {
                validateWorkspaceLocation(root, entry.before(), entry.after());
                completed.add(candidate);
                continue;
            }
            String before = candidate.blob() == null ? "" : reader.read(entry.repository(), candidate.blob());
            checkDiskMetadata(candidate);
            byte[] disk = entry.after() == null ? new byte[0] : readBounded(root, entry.after(), cancelled);
            checkDiskMetadata(candidate);
            String after = entry.after() == null ? "" : candidate.documentText() == null ? LocalGitReader.text(disk)
                    : candidate.documentText().replace("\r\n", "\n").replace('\r', '\n');
            if (after.indexOf('\0') >= 0) throw new SelectionException("Binary changes are not supported.");
            String patch = UnifiedPatch.build(entry.beforeName(), entry.afterName(), before, after, cancelled);
            patchBytes += patch.getBytes(StandardCharsets.UTF_8).length;
            if (patchBytes > ContentPolicy.MAX_PATCH_BYTES) throw new SelectionException("Patch exceeds 512 KiB. Select fewer changes.");
            output.append(patch);
            completed.add(new Prepared(entry, candidate.blob(), null, candidate.disk(), digest(disk)));
        }
        String result = output.toString();
        if (!result.isEmpty() || !workspaceOnly()) ContentPolicy.validatePatch(result);
        prepared = List.copyOf(completed);
        return result;
    }

    boolean workspaceOnly() { return !entries.isEmpty() && entries.stream().allMatch(Entry::workspace); }

    boolean hasWorkspaceSettings() { return entries.stream().anyMatch(Entry::workspace); }

    /** EDT-only selection, tracker, and document checks. No filesystem reads or process starts. */
    boolean isFresh() {
        try {
            if (project.isDisposed() || !originalMessage.equals(ui.getCommitMessageUi().getText())
                    || messageStamp != messageDocument.getModificationStamp()
                    || !selection.equals(selectionKey(ui.getIncludedChanges())) || prepared.size() != entries.size()) return false;
            var currentChanges = new HashMap<Path, Change>();
            for (Change change : ui.getIncludedChanges()) {
                ContentRevision revision = change.getAfterRevision() != null ? change.getAfterRevision() : change.getBeforeRevision();
                if (revision != null) currentChanges.put(Path.of(revision.getFile().getPath()).toAbsolutePath().normalize(), change);
            }
            for (Prepared candidate : prepared) {
                Entry entry = candidate.entry();
                Change change = currentChanges.get(entry.after() == null ? entry.before() : entry.after());
                if (change == null) return false;
                checkPartial(project, change, entry.after() == null ? null : change.getAfterRevision().getFile());
                // Workspace contents never enter the request. Ignore routine IDE autosaves.
                if (entry.workspace()) continue;
                if (entry.document() != null && entry.document().getModificationStamp() != entry.documentStamp()) return false;
                if (entry.after() != null) {
                    VirtualFile file = change.getAfterRevision().getFile().getVirtualFile();
                    if (file != entry.file() || (file != null && (!file.isValid() || file.getModificationStamp() != entry.fileStamp()))) return false;
                    Document current = file == null ? null : FileDocumentManager.getInstance().getCachedDocument(file);
                    if (entry.document() == null && current != null && FileDocumentManager.getInstance().isDocumentUnsaved(current)) return false;
                }
            }
            return true;
        } catch (IOException | RuntimeException ignored) { return false; }
    }

    /** Worker-only bounded content check. Does not read IDE UI or mutable document state. */
    boolean contentIsFresh(BooleanSupplier cancelled) throws InterruptedException {
        try {
            if (prepared.size() != entries.size()) return false;
            int checked = 0;
            for (Prepared candidate : prepared) {
                if (cancelled.getAsBoolean()) throw new InterruptedException("Cancelled.");
                progress("Verify selected files", checked++, prepared.size(), 0, 1);
                Entry entry = candidate.entry();
                if (entry.workspace()) {
                    validateWorkspaceLocation(root, entry.before(), entry.after());
                    continue;
                }
                if (entry.after() != null) {
                    checkDiskMetadata(candidate);
                    if (!candidate.diskHash().equals(diskDigest(root, entry.after(), cancelled))) return false;
                    checkDiskMetadata(candidate);
                } else if (Files.exists(entry.before(), LinkOption.NOFOLLOW_LINKS)) return false;
            }
            return true;
        } catch (IOException | RuntimeException ignored) { return false; }
    }

    void apply(String message) { ui.getCommitMessageUi().setText(message); }

    private static void checkPartial(Project project, Change change, FilePath file) throws IOException {
        if (file == null) return;
        VirtualFile virtual = file.getVirtualFile();
        LineStatusTrackerManagerI manager = LineStatusTrackerManager.getInstance(project);
        LineStatusTracker<?> tracker = virtual == null ? null : manager.getLineStatusTracker(virtual);
        checkPartial(change, tracker);
    }

    static void checkPartial(Change change, LineStatusTracker<?> tracker) throws IOException {
        // PyCharm wraps ordinary whole-file changes in ChangeListChange too. No tracker means
        // no retained per-line exclusions; the IDE also treats this case as a whole-file change.
        if (tracker instanceof PartialLocalLineStatusTracker partial) {
            List<String> ids = partial.getAffectedChangeListsIds();
            if (!partial.isOperational() || ids.size() > 1 || partial.hasPartialChangesToCommit()) {
                throw new SelectionException("Partial file or changelist selection is not supported. Select the whole file in one changelist.");
            }
            for (String id : ids) if (partial.getExcludedFromCommitState(id) != ExclusionState.ALL_INCLUDED) {
                throw new SelectionException("Some hunks are excluded. Select the whole file before generating.");
            }
            if (change instanceof ChangeListChange selected && !ids.contains(selected.getChangeListId())) {
                throw new SelectionException("Partial changelist state is unavailable.");
            }
        }
    }

    private static Path checkedPath(Path root, FilePath file) throws IOException {
        if (file == null) return null;
        if (file.isNonLocal() || file.isDirectory() || file.getFileType().isBinary()) throw new SelectionException("Only local text files are supported.");
        VirtualFile virtual = file.getVirtualFile();
        if (virtual != null && virtual.is(VFileProperty.SYMLINK)) throw new SelectionException("Symbolic links are excluded.");
        Path path = Path.of(file.getPath()).toAbsolutePath().normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new SelectionException("Selected files must be inside the project.");
        checkSensitivePath(root.relativize(path));
        return path;
    }

    static void checkSensitivePath(Path relative) throws SelectionException {
        if (relative.isAbsolute() || !relative.equals(relative.normalize()) || relative.normalize().startsWith("..")) {
            throw new SelectionException("Selected files must be inside the project.");
        }
        if (workspaceSettings(relative)) return;
        if (!sensitive(relative)) return;
        boolean ideSettings = false;
        for (Path part : relative) if (part.toString().equalsIgnoreCase(".idea")) ideSettings = true;
        String name = relative.toString().replace('\\', '/');
        StringBuilder display = new StringBuilder();
        name.codePoints().limit(160).forEach(c -> display.appendCodePoint(
                Character.isISOControl(c) || Character.getType(c) == Character.FORMAT || c == 0x2028 || c == 0x2029 ? '?' : c));
        if (name.codePointCount(0, name.length()) > 160) display.append("...");
        // IDE notifications accept HTML. Filenames must remain text, including quotes and markup.
        String escaped = display.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
        throw new SelectionException("Uncheck &quot;" + escaped + "&quot; in the Commit window. "
                + (ideSettings ? "IDE settings files are excluded." : "This filename is excluded by the sensitive-content policy."));
    }

    /** Only this IDE file has a metadata-only path through selection preparation. */
    static boolean workspaceSettings(Path relative) {
        int count = relative.getNameCount();
        if (relative.isAbsolute() || !relative.equals(relative.normalize()) || relative.startsWith("..") || count < 2
                || !relative.getName(count - 2).toString().equalsIgnoreCase(".idea")
                || !relative.getName(count - 1).toString().equalsIgnoreCase("workspace.xml")) return false;
        return count == 2 || !sensitive(relative.subpath(0, count - 2));
    }

    static boolean workspaceChange(Path beforeRelative, Path afterRelative) throws SelectionException {
        boolean before = beforeRelative != null && workspaceSettings(beforeRelative);
        boolean after = afterRelative != null && workspaceSettings(afterRelative);
        if ((before && afterRelative != null && !after) || (after && beforeRelative != null && !before)) {
            throw new SelectionException("Select workspace settings separately from renames to or from source files.");
        }
        return before || after;
    }

    /** Checks location and existence only. Never opens workspace XML or inspects its saved values. */
    static void validateWorkspaceLocation(Path root, Path before, Path after) throws IOException {
        if (before != null) validateLocation(root, before);
        if (after != null) {
            validateLocation(root, after);
            if (!Files.readAttributes(after, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile()) {
                throw new SelectionException("Only regular local files are supported.");
            }
        } else if (before == null || Files.exists(before, LinkOption.NOFOLLOW_LINKS)) {
            throw new SelectionException("Selected workspace settings changed. Run CodexCommit again.");
        }
    }

    static void validateLocation(Path root, Path path) throws IOException {
        if (!path.startsWith(root) || path.equals(root)) throw new SelectionException("Selected files must be inside the project.");
        Path current = root;
        for (Path segment : root.relativize(path)) {
            String name = segment.toString();
            if (name.chars().anyMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029)) throw new SelectionException("Unsupported filename.");
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) throw new SelectionException("Symbolic links are excluded.");
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && !current.toRealPath().startsWith(root)) {
                throw new SelectionException("Selected path resolves outside the project.");
            }
        }
    }

    static boolean sensitive(Path relative) {
        for (Path part : relative) {
            String value = part.toString().toLowerCase(Locale.ROOT);
            if (SENSITIVE_DIRECTORIES.contains(value) || value.equals(".env") || value.startsWith(".env.")
                    || SENSITIVE_NAMES.contains(value) || value.contains("secret") || value.contains("credential")
                    || value.endsWith(".tfvars") || value.endsWith(".tfstate")) return true;
        }
        String name = relative.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && SENSITIVE_EXTENSIONS.contains(name.substring(dot + 1));
    }

    private static String relative(Path root, Path path) { return path == null ? null : root.relativize(path).toString().replace('\\', '/'); }

    private static String selectionKey(List<? extends Change> changes) {
        return changes.stream().map(change -> change.getClass().getName() + "|" + revisionKey(change.getBeforeRevision())
                        + "|" + revisionKey(change.getAfterRevision()) + "|" + (change instanceof ChangeListChange item ? item.getChangeListId() : ""))
                .sorted(Comparator.naturalOrder()).collect(Collectors.joining("\n"));
    }

    private static String revisionKey(ContentRevision revision) {
        return revision == null ? "" : revision.getFile().getPath() + "|" + revision.getRevisionNumber().asString();
    }

    private static void checkDiskMetadata(Prepared candidate) throws IOException {
        Entry entry = candidate.entry();
        if (entry.after() == null) return;
        BasicFileAttributes attributes = Files.readAttributes(entry.after(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.size() != candidate.disk().size()
                || !attributes.lastModifiedTime().equals(candidate.disk().modified())
                || !java.util.Objects.equals(attributes.fileKey(), candidate.disk().key())) throw new SelectionException("Selected content changed during preparation.");
    }

    static byte[] readBounded(Path root, Path path) throws IOException, InterruptedException {
        return readBounded(root, path, () -> false);
    }

    static byte[] readBounded(Path root, Path path, BooleanSupplier cancelled) throws IOException, InterruptedException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        readChunks(root, path, cancelled, output::write);
        return output.toByteArray();
    }

    static String diskDigest(Path root, Path path, BooleanSupplier cancelled) throws IOException, InterruptedException {
        MessageDigest digest = sha256();
        readChunks(root, path, cancelled, digest::update);
        return HexFormat.of().formatHex(digest.digest());
    }

    @FunctionalInterface private interface Chunk { void accept(byte[] bytes, int offset, int count); }

    private static void readChunks(Path root, Path path, BooleanSupplier cancelled, Chunk chunk) throws IOException, InterruptedException {
        validateLocation(root, path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > LocalGitReader.MAX_FILE_BYTES) {
            throw new SelectionException("Only regular files up to 50 MiB are supported.");
        }
        try (InputStream stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = new byte[64 * 1024];
            long total = 0;
            while (true) {
                if (cancelled.getAsBoolean()) throw new InterruptedException("Cancelled.");
                int count = stream.read(bytes);
                if (count == -1) break;
                total += count;
                if (total > LocalGitReader.MAX_FILE_BYTES) throw new SelectionException("Selected file exceeds 50 MiB.");
                chunk.accept(bytes, 0, count);
            }
        }
    }

    private static String digest(byte[] bytes) {
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable."); }
    }

    private static void progress(String text, int completed, int total, double start, double span) {
        var indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null) {
            indicator.setIndeterminate(false);
            indicator.setText2(text + ": " + (completed + 1) + " of " + total);
            indicator.setFraction(start + span * completed / Math.max(1, total));
        }
    }
}
