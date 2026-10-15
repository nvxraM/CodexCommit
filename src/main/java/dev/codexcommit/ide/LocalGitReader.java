package dev.codexcommit.ide;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only object access. No shell, diff driver, checkout, index, hooks, or Git network transport. */
final class LocalGitReader {
    static final int MAX_FILE_BYTES = 50 * 1024 * 1024;
    static final int MAX_TOTAL_BYTES = 50 * 1024 * 1024;
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final Pattern OBJECT = Pattern.compile("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}");
    private static final Pattern TREE_ENTRY = Pattern.compile("(100644|100755) blob ([0-9a-fA-F]{40}|[0-9a-fA-F]{64}) +([0-9]+)\\t([^\\x00]+)\\x00");
    private final Path executable;
    private final BooleanSupplier cancelled;

    record Blob(String object, int size) {}

    LocalGitReader(Path executable, BooleanSupplier cancelled) throws IOException {
        this.executable = requireNativeGit(executable);
        this.cancelled = cancelled;
    }

    static Path requireNativeGit(Path path) throws IOException {
        if (!path.isAbsolute()) throw new IOException("Configure an absolute native Git executable.");
        Path real = path.toRealPath();
        String name = real.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(name.equals("git") || name.equals("git.exe")) || !Files.isRegularFile(real)) {
            throw new IOException("Configure a native Git executable.");
        }
        try (InputStream stream = Files.newInputStream(real)) {
            byte[] magic = stream.readNBytes(4);
            boolean pe = magic.length >= 2 && magic[0] == 'M' && magic[1] == 'Z';
            boolean elf = magic.length == 4 && magic[0] == 0x7f && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F';
            int signature = magic.length == 4 ? ByteBuffer.wrap(magic).getInt() : 0;
            boolean macho = signature == 0xFEEDFACE || signature == 0xFEEDFACF || signature == 0xCEFAEDFE
                    || signature == 0xCFFAEDFE || signature == 0xCAFEBABE || signature == 0xBEBAFECA;
            if (!pe && !elf && !macho) throw new IOException("Git wrappers are not supported.");
        }
        return real;
    }

    Blob inspect(Path repository, String revision, String relativePath) throws IOException, InterruptedException {
        if (!OBJECT.matcher(revision).matches()) throw new IOException("Unsupported Git base revision.");
        String result = decode(run(repository, List.of("ls-tree", "-zl", revision, "--", relativePath), 8192));
        Matcher entry = TREE_ENTRY.matcher(result);
        if (!entry.matches() || !entry.group(4).equals(relativePath)) {
            throw new IOException("Only regular tracked text files are supported.");
        }
        long size;
        try { size = Long.parseLong(entry.group(3)); }
        catch (NumberFormatException ignored) { throw new IOException("File size unavailable."); }
        if (size > MAX_FILE_BYTES) throw new IOException("Selected file exceeds the size limit.");
        return new Blob(entry.group(2), (int) size);
    }

    String read(Path repository, Blob blob) throws IOException, InterruptedException {
        if (!OBJECT.matcher(blob.object()).matches() || blob.size() < 0 || blob.size() > MAX_FILE_BYTES) {
            throw new IOException("Invalid Git object metadata.");
        }
        byte[] bytes = run(repository, List.of("cat-file", "blob", blob.object()), MAX_FILE_BYTES);
        if (bytes.length != blob.size()) throw new IOException("Git content changed during preparation.");
        return text(bytes);
    }

    static String text(byte[] bytes) throws IOException {
        for (byte value : bytes) if (value == 0) throw new IOException("Binary files are not supported.");
        return decode(bytes).replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String decode(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ignored) {
            throw new IOException("Only UTF-8 text is supported.");
        }
    }

    private byte[] run(Path repository, List<String> args, int maximum) throws IOException, InterruptedException {
        if (cancelled.getAsBoolean()) throw new InterruptedException("Cancelled.");
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(List.of("--no-pager", "--no-optional-locks", "--no-replace-objects", "--literal-pathspecs",
                "-c", "core.fsmonitor=false", "-c", "protocol.allow=never", "-c", "protocol.file.allow=never"));
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command).directory(repository.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().keySet().removeIf(name -> name.toUpperCase(Locale.ROOT).startsWith("GIT_"));
        String nullFile = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "NUL" : "/dev/null";
        builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        builder.environment().put("GIT_CONFIG_GLOBAL", nullFile);
        builder.environment().put("GIT_CONFIG_SYSTEM", nullFile);
        builder.environment().put("GIT_CONFIG_COUNT", "0");
        builder.environment().put("GIT_ATTR_NOSYSTEM", "1");
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GIT_NO_REPLACE_OBJECTS", "1");
        builder.environment().put("GIT_NO_LAZY_FETCH", "1");
        builder.environment().put("GIT_OPTIONAL_LOCKS", "0");
        Process process = builder.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 8192));
        AtomicBoolean oversized = new AtomicBoolean();
        AtomicReference<IOException> failure = new AtomicReference<>();
        Thread reader = Thread.ofVirtual().name("CodexCommit Git output").unstarted(() -> {
            try (InputStream stream = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (output.size() + count > maximum) {
                        oversized.set(true);
                        process.destroyForcibly();
                        return;
                    }
                    output.write(buffer, 0, count);
                }
            } catch (IOException exception) { failure.set(exception); }
        });
        reader.start();
        long deadline = System.nanoTime() + TIMEOUT_NANOS;
        try {
            process.getOutputStream().close();
            while (!process.waitFor(50, TimeUnit.MILLISECONDS)) {
                if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedException("Cancelled.");
                if (System.nanoTime() >= deadline) throw new IOException("Git read timed out.");
            }
            reader.join(1000);
            if (reader.isAlive() || oversized.get() || failure.get() != null || process.exitValue() != 0) {
                throw new IOException("Unable to read the selected Git object within limits.");
            }
            return output.toByteArray();
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) process.destroyForcibly();
            process.getInputStream().close();
            if (reader.isAlive()) { reader.interrupt(); reader.join(1000); }
        }
    }
}
