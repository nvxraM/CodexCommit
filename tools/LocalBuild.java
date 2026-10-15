import javax.tools.ToolProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;

/** Offline build using an explicitly selected installed IDE SDK. No SDK/runtime is redistributed. */
public final class LocalBuild {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: java tools/LocalBuild.java <project-root> <IDE-home>");
        Path root = Path.of(args[0]).toRealPath();
        Path ide = Path.of(args[1]).toRealPath();
        if (!Files.isRegularFile(ide.resolve("product-info.json"))) throw new IOException("Select an installed JetBrains IDE SDK.");
        Path output = root.resolve("build/local/" + System.currentTimeMillis());
        Files.createDirectories(output);
        List<Path> sdkJars = new ArrayList<>();
        try (var stream = Files.walk(ide.resolve("lib"))) {
            stream.filter(p -> p.toString().endsWith(".jar")).forEach(sdkJars::add);
        }
        // Bundled Git plugin layout varies between IDE releases; inspect its declared plugin folder too.
        try (var dirs = Files.list(ide.resolve("plugins"))) {
            for (Path plugin : dirs.filter(Files::isDirectory).toList()) {
                if (!plugin.getFileName().toString().toLowerCase(Locale.ROOT).contains("git")) continue;
                try (var stream = Files.walk(plugin)) {
                    stream.filter(p -> p.toString().endsWith(".jar")).forEach(sdkJars::add);
                }
            }
        }
        String cp = String.join(File.pathSeparator, sdkJars.stream().map(Path::toString).toList());
        Path classes = output.resolve("classes");
        compile(root.resolve("src/main/java"), classes, cp);
        Path tests = output.resolve("tests");
        compile(root.resolve("src/test/java"), tests, classes + File.pathSeparator + cp);
        Process test = new ProcessBuilder(javaBinary(), "-cp", tests + File.pathSeparator + classes,
                "dev.diffquill.core.CoreTests").inheritIO().start();
        if (test.waitFor() != 0) throw new IOException("Core regressions failed; no package created.");
        String sdkRuntime = String.join(File.pathSeparator, sdkJars.stream().map(Path::getParent).distinct()
                .map(p -> p.toString() + File.separator + "*").toList());
        Process boundaryTest = new ProcessBuilder(javaBinary(), "-cp",
                tests + File.pathSeparator + classes + File.pathSeparator + sdkRuntime,
                "dev.diffquill.ide.IdeBoundaryTests").inheritIO().start();
        if (boundaryTest.waitFor() != 0) throw new IOException("IDE boundary regressions failed; no package created.");
        Path jar = output.resolve("diffquill.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            addDirectory(zip, classes, "");
            addDirectory(zip, root.resolve("src/main/resources"), "");
            Path license = root.resolve("LICENSE");
            if (Files.isRegularFile(license)) add(zip, license, "META-INF/LICENSE");
        }
        Path distribution = output.resolve("DiffQuill-1.1.1.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(distribution))) {
            add(zip, jar, "DiffQuill/lib/diffquill.jar");
        }
        System.out.println("Built: " + distribution);
        System.out.println("Offline SDK compilation and core tests passed; run official Plugin Verifier and IDE acceptance separately.");
    }
    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
    }
    private static void compile(Path source, Path output, String cp) throws IOException {
        Files.createDirectories(output);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IOException("A JDK is required; a runtime alone cannot compile source.");
        List<String> arguments = new ArrayList<>(List.of("--release", "25", "-encoding", "UTF-8", "-Xlint:unchecked", "-cp", cp, "-d", output.toString()));
        try (var sources = Files.walk(source)) {
            arguments.addAll(sources.filter(p -> p.toString().endsWith(".java")).sorted().map(Path::toString).toList());
        }
        if (compiler.run(null, System.out, System.err, arguments.toArray(String[]::new)) != 0) throw new IOException("Compilation failed.");
    }
    private static void addDirectory(ZipOutputStream zip, Path root, String prefix) throws IOException {
        try (var files = Files.walk(root)) {
            for (Path p : files.filter(Files::isRegularFile).sorted().toList()) add(zip, p, prefix + root.relativize(p).toString().replace('\\', '/'));
        }
    }
    private static void add(ZipOutputStream zip, Path file, String name) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0));
        zip.putNextEntry(entry);
        Files.copy(file, zip);
        zip.closeEntry();
    }
}
