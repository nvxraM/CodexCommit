package dev.codexcommit.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Resolves a native executable without invoking a shell or searching a project tree. */
public final class CodexExecutableLocator {
    private CodexExecutableLocator() { }

    public static Path find(String explicitPath) throws IOException {
        return find(explicitPath, System.getenv(), Path.of(System.getProperty("user.dir")), isWindows());
    }

    static Path find(String explicitPath, Map<String, String> environment, Path cwd, boolean windows) throws IOException {
        if (explicitPath != null && !explicitPath.isBlank()) {
            try {
                return validateNative(Path.of(explicitPath.trim()), windows);
            } catch (InvalidPathException ex) {
                throw new IOException("Choose an absolute path to a native codex executable.");
            }
        }
        String executable = windows ? "codex.exe" : "codex";
        String path = environment.getOrDefault("PATH", environment.getOrDefault("Path", ""));
        String separator = windows ? ";" : ":";
        for (String entry : path.split(separator)) {
            if (entry.isBlank() || entry.indexOf('"') >= 0) continue;
            try {
                Path directory = Path.of(entry);
                if (!directory.isAbsolute() || sameDirectory(directory, cwd)) continue;
                Path candidate = directory.resolve(executable);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return validateNative(candidate, windows);
                }
                // npm launchers are scripts. Resolve only the package's known native binary layout.
                for (Path nested : nativePackageCandidates(directory.resolve("node_modules/@openai"), windows)) {
                    if (Files.isRegularFile(nested) && Files.isExecutable(nested)) return validateNative(nested, windows);
                }
            } catch (InvalidPathException | IOException ignored) {
                // An unusable PATH component is not permission to search elsewhere in that tree.
            }
        }
        // Bounded JetBrains ACP discovery; no username or IDE release is embedded.
        String localData = environment.get("LOCALAPPDATA");
        if (windows && localData != null) {
            Path jetBrains = Path.of(localData).resolve("JetBrains");
            for (Path ide : immediateDirectories(jetBrains, 100)) {
                Path agents = ide.resolve("acp-agents/codex-acp");
                for (Path version : immediateDirectories(agents, 30)) {
                    for (Path candidate : nativePackageCandidates(version.resolve("node_modules/@openai"), true)) {
                        if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return validateNative(candidate, true);
                    }
                }
            }
        }
        String userHome = System.getProperty("user.home", "");
        if (!userHome.isBlank()) {
            for (String relative : windows ? List.of(".cargo/bin/codex.exe")
                    : List.of(".local/bin/codex", ".cargo/bin/codex", ".npm-global/bin/codex")) {
                Path candidate = Path.of(userHome).resolve(relative);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    try { return validateNative(candidate, windows); } catch (IOException ignored) { }
                }
            }
        }
        throw new IOException("Native Codex CLI was not found. Install Codex from its official source, then set its absolute executable path in Settings > Tools > CodexCommit. Windows .cmd/.bat and shell wrappers are not supported.");
    }

    public static Path validateNative(Path candidate) throws IOException {
        return validateNative(candidate, isWindows());
    }

    static Path validateNative(Path candidate, boolean windows) throws IOException {
        String name = candidate.getFileName() == null ? "" : candidate.getFileName().toString();
        if (!candidate.isAbsolute() || !name.equalsIgnoreCase(windows ? "codex.exe" : "codex")) {
            throw new IOException("Choose an absolute path to a native codex" + (windows ? ".exe" : "") + " executable, without arguments or a shell wrapper.");
        }
        Path real = candidate.toRealPath();
        if (!Files.isRegularFile(real) || !Files.isExecutable(real)) throw new IOException("The selected Codex executable is not a usable executable file.");
        byte[] prefix;
        try (var input = Files.newInputStream(real)) { prefix = input.readNBytes(4); }
        boolean nativeBinary = prefix.length >= 2 && prefix[0] == 'M' && prefix[1] == 'Z';
        if (!windows && prefix.length == 4) {
            int magic = (prefix[0] & 255) << 24 | (prefix[1] & 255) << 16 | (prefix[2] & 255) << 8 | prefix[3] & 255;
            nativeBinary = magic == 0x7f454c46 || magic == 0xfeedface || magic == 0xcefaedfe
                    || magic == 0xfeedfacf || magic == 0xcffaedfe || magic == 0xcafebabe || magic == 0xbebafeca;
        }
        if (!nativeBinary) throw new IOException("The selected file is a wrapper or script. Select the native Codex binary instead.");
        return real;
    }

    private static List<Path> nativePackageCandidates(Path openAi, boolean windows) {
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String arch = architecture.contains("aarch") || architecture.contains("arm64") ? "aarch64" : "x86_64";
        String npmArch = arch.equals("aarch64") ? "arm64" : "x64";
        String os = windows ? "win32" : System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac") ? "darwin" : "linux";
        String triple = arch + (windows ? "-pc-windows-msvc" : os.equals("darwin") ? "-apple-darwin" : "-unknown-linux-musl");
        String file = windows ? "codex.exe" : "codex";
        return List.of(openAi.resolve("codex-" + os + "-" + npmArch).resolve("vendor").resolve(triple).resolve("bin").resolve(file),
                openAi.resolve("codex").resolve("vendor").resolve(triple).resolve("codex").resolve(file));
    }

    private static List<Path> immediateDirectories(Path parent, int limit) {
        if (!Files.isDirectory(parent)) return List.of();
        try (Stream<Path> entries = Files.list(parent)) {
            return entries.limit(limit).filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString).reversed()).toList();
        } catch (IOException ignored) { return List.of(); }
    }

    private static boolean sameDirectory(Path first, Path second) throws IOException {
        return first.toRealPath().equals(second.toRealPath());
    }

    static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); }
}
