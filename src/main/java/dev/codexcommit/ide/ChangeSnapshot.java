package dev.codexcommit.ide;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Captures only checked whole-file Git changes, then verifies the same selection before applying. */
final class ChangeSnapshot {
    static final int MAX_FILES = 32;

    /** Only fixed validation text may be shown to the user; filesystem exception text is not disclosed. */
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
                         Document document, long documentStamp, String documentText, long diskSize, FileTime diskModified, Object fileKey) {}
    private record Prepared(Entry entry, LocalGitReader.Blob blob, String diskHash) {}

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
        if (changes.isEmpty() || changes.size() > MAX_FILES) throw new SelectionException("Select between 1 and 32 tracked files.");
        Path root = Path.of(project.getBasePath()).toRealPath();
        GitExecutable configured = GitExecutableManager.getInstance().getExecutable(project);
        if (!configured.isLocal()) throw new SelectionException("Only local native Git is supported.");
        Path git;
        try { git = LocalGitReader.requireNativeGit(Path.of(configured.getExePath())); }
        catch (IOException | RuntimeException ignored) {
            throw new SelectionException("Open Settings > Version Control > Git. Select the absolute path to a native Git executable.");
        }
        List<Entry> entries = new ArrayList<>();
        long total = 0;
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
            FilePath active = afterFile != null ? afterFile : beforeFile;
            if (active == null) throw new SelectionException("Selected change is unavailable.");
            VcsRoot vcs = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(active);
            if (vcs == null || vcs.getVcs() == null || !"Git".equals(vcs.getVcs().getName())) {
                throw new SelectionException("Only Git changes are supported.");
            }
            Path repository = Path.of(vcs.getPath().getPath()).toRealPath();
            if (!repository.startsWith(root) || (before != null && !before.startsWith(repository))
                    || (after != null && !after.startsWith(repository))) throw new SelectionException("The Git root must be inside this project.");
            checkPartial(project, change, afterFile);
            Document document = afterFile == null || afterFile.getVirtualFile() == null ? null
                    : FileDocumentManager.getInstance().getCachedDocument(afterFile.getVirtualFile());
            String text = null;
            long stamp = -1;
            long size = 0;
            if (after != null) {
                if (!Files.isRegularFile(after, LinkOption.NOFOLLOW_LINKS)) throw new SelectionException("Only regular local files are supported.");
                size = Files.size(after);
                if (size > LocalGitReader.MAX_FILE_BYTES) throw new SelectionException("Selected file exceeds 64 KiB.");
                if (document != null) {
                    if (document.getTextLength() > LocalGitReader.MAX_FILE_BYTES
                            || FileDocumentManager.getInstance().isPartialPreviewOfALargeFile(document)) {
                        throw new SelectionException("Selected document exceeds 64 KiB.");
                    }
                    text = document.getImmutableCharSequence().toString();
                    int bytes = text.getBytes(StandardCharsets.UTF_8).length;
                    if (bytes > LocalGitReader.MAX_FILE_BYTES) throw new SelectionException("Selected document exceeds 64 KiB.");
                    size = Math.max(size, bytes);
                    stamp = document.getModificationStamp();
                }
            }
            total += size;
            if (total > LocalGitReader.MAX_TOTAL_BYTES) throw new SelectionException("Selected content exceeds 256 KiB.");
            String revision = beforeRevision == null ? null : beforeRevision.getRevisionNumber().asString();
            BasicFileAttributes attributes = after == null ? null : Files.readAttributes(after, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            entries.add(new Entry(before, after, repository, revision, relative(root, before), relative(root, after),
                    document, stamp, text, attributes == null ? 0 : attributes.size(),
                    attributes == null ? null : attributes.lastModifiedTime(), attributes == null ? null : attributes.fileKey()));
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
            LocalGitReader.Blob blob = entry.before() == null ? null : reader.inspect(entry.repository(), entry.revision(),
                    relative(entry.repository(), entry.before()));
            total += (blob == null ? 0 : blob.size()) + Math.max(entry.diskSize(),
                    entry.documentText() == null ? 0 : entry.documentText().getBytes(StandardCharsets.UTF_8).length);
            if (total > LocalGitReader.MAX_TOTAL_BYTES) throw new SelectionException("Selected before and after content exceeds 256 KiB.");
            candidates.add(new Prepared(entry, blob, null));
        }
        StringBuilder output = new StringBuilder();
        List<Prepared> completed = new ArrayList<>();
        for (Prepared candidate : candidates) {
            Entry entry = candidate.entry();
            if (cancelled.getAsBoolean()) throw new InterruptedException("Cancelled.");
            String before = candidate.blob() == null ? "" : reader.read(entry.repository(), candidate.blob());
            checkDiskMetadata(entry);
            byte[] disk = entry.after() == null ? new byte[0] : readBounded(root, entry.after());
            checkDiskMetadata(entry);
            String after = entry.after() == null ? "" : entry.documentText() == null ? LocalGitReader.text(disk)
                    : entry.documentText().replace("\r\n", "\n").replace('\r', '\n');
            if (after.indexOf('\0') >= 0) throw new SelectionException("Binary changes are not supported.");
            output.append(UnifiedPatch.build(entry.beforeName(), entry.afterName(), before, after, cancelled));
            if (output.length() > ContentPolicy.MAX_PATCH_BYTES) throw new SelectionException("Patch exceeds 120 KiB.");
            completed.add(new Prepared(entry, candidate.blob(), digest(disk)));
        }
        String result = output.toString();
        ContentPolicy.validatePatch(result);
        prepared = List.copyOf(completed);
        return result;
    }

    /** No Git or network here. At most 256 KiB of local files are rehashed on the EDT. */
    boolean isFresh() {
        try {
            if (project.isDisposed() || !originalMessage.equals(ui.getCommitMessageUi().getText())
                    || messageStamp != messageDocument.getModificationStamp()
                    || !selection.equals(selectionKey(ui.getIncludedChanges())) || prepared.size() != entries.size()) return false;
            for (Prepared candidate : prepared) {
                Entry entry = candidate.entry();
                checkPartial(project, findChange(ui.getIncludedChanges(), entry), entry.after() == null ? null
                        : findChange(ui.getIncludedChanges(), entry).getAfterRevision().getFile());
                if (entry.document() != null && entry.document().getModificationStamp() != entry.documentStamp()) return false;
                if (entry.after() != null) {
                    validateLocation(root, entry.after());
                    if (!Files.isRegularFile(entry.after(), LinkOption.NOFOLLOW_LINKS)
                            || !candidate.diskHash().equals(digest(readBounded(root, entry.after())))) return false;
                    VirtualFile file = findChange(ui.getIncludedChanges(), entry).getAfterRevision().getFile().getVirtualFile();
                    Document current = file == null ? null : FileDocumentManager.getInstance().getCachedDocument(file);
                    if (entry.document() == null && current != null && FileDocumentManager.getInstance().isDocumentUnsaved(current)) return false;
                } else if (Files.exists(entry.before(), LinkOption.NOFOLLOW_LINKS)) return false;
            }
            return true;
        } catch (IOException | RuntimeException ignored) { return false; }
    }

    void apply(String message) { ui.getCommitMessageUi().setText(message); }

    private static Change findChange(List<? extends Change> changes, Entry entry) throws IOException {
        for (Change change : changes) {
            ContentRevision revision = change.getAfterRevision() != null ? change.getAfterRevision() : change.getBeforeRevision();
            if (revision != null && Path.of(revision.getFile().getPath()).toAbsolutePath().normalize()
                    .equals(entry.after() == null ? entry.before() : entry.after())) return change;
        }
        throw new SelectionException("Selection changed.");
    }

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
        validateLocation(root, path);
        if (sensitive(root.relativize(path))) throw new SelectionException("A selected filename is excluded by the sensitive-content policy.");
        return path;
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
                .sorted(Comparator.naturalOrder()).reduce("", (a, b) -> a + "\n" + b);
    }

    private static String revisionKey(ContentRevision revision) {
        return revision == null ? "" : revision.getFile().getPath() + "|" + revision.getRevisionNumber().asString();
    }

    private static void checkDiskMetadata(Entry entry) throws IOException {
        if (entry.after() == null) return;
        BasicFileAttributes attributes = Files.readAttributes(entry.after(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.size() != entry.diskSize() || !attributes.lastModifiedTime().equals(entry.diskModified())
                || !java.util.Objects.equals(attributes.fileKey(), entry.fileKey())) throw new SelectionException("Selected content changed during preparation.");
    }

    static byte[] readBounded(Path root, Path path) throws IOException {
        validateLocation(root, path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > LocalGitReader.MAX_FILE_BYTES) {
            throw new SelectionException("Only regular files up to 64 KiB are supported.");
        }
        try (InputStream stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = stream.readNBytes(LocalGitReader.MAX_FILE_BYTES + 1);
            if (bytes.length > LocalGitReader.MAX_FILE_BYTES) throw new SelectionException("Selected file exceeds 64 KiB.");
            return bytes;
        }
    }

    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable."); }
    }
}
