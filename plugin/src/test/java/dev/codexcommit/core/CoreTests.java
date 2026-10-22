package dev.codexcommit.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Standalone synthetic regression suite: no account, repository, or model calls. */
public final class CoreTests {
    private static int passed;

    public static void main(String[] args) throws Exception {
        if ((args.length == 2 || args.length == 3) && args[0].equals("--audit-cli")) {
            OfflineCliAudit.run(Path.of(args[1]), args.length == 3 ? args[2] : "gpt-5.4");
            return;
        }
        check("CLI versions require an exact audited stable release", () -> {
            require(CodexRunner.isAuditedVersion("codex-cli 0.160.1\n"), "Retain the previous audited CLI");
            require(CodexRunner.isAuditedVersion("codex-cli 0.163.0\n"), "Accept the newly audited CLI");
            for (String rejected : List.of("codex-cli 0.163.0-alpha.1", "codex-cli 0.164.0", "codex-cli 0.162.0",
                    "codex-cli 0.163.00", "0.163.0", "codex-cli 0.163.0\nextra")) {
                require(!CodexRunner.isAuditedVersion(rejected), "Reject unverified CLI output");
            }
        });
        check("portable prompt uses exact selection", () -> {
            String patch = "diff --git a/hello.txt b/hello.txt\n--- a/hello.txt\n+++ b/hello.txt\n@@ -1 +1 @@\n-old\n+new\n";
            String prompt = PromptBuilder.build(patch);
            require(prompt.endsWith(patch), "Prompt must preserve the reviewed selection byte for byte");
            require(prompt.contains("untrusted data"), "Prompt must classify diff text as data");
        });
        check("workspace metadata adds only a fixed note before the unchanged source patch", () -> {
            String patch = "diff --git a/hello.py b/hello.py\n-old\n+new\n";
            String note = "IDE workspace settings also changed. Their contents are excluded.\n";
            String ordinary = PromptBuilder.build(patch);
            String mixed = PromptBuilder.build(patch, true);
            require(!ordinary.contains(note), "Ordinary source must not claim workspace changes.");
            require(mixed.equals(ordinary.replace("SELECTED DIFF", note + "SELECTED DIFF")), "Workspace note must be fixed.");
            require(mixed.endsWith(patch) && !mixed.contains("workspace.xml"), "Workspace metadata must not alter or expose paths in the patch.");
            String largestPatch = "x".repeat(ContentPolicy.MAX_PATCH_BYTES);
            require(PromptBuilder.build(largestPatch, true).endsWith(largestPatch), "Workspace note must fit beside the maximum patch.");
        });
        check("blocks sensitive paths and key material", () -> {
            rejects(() -> ContentPolicy.validatePatch("diff --git a/.env b/.env\n+VALUE=demo"), IllegalArgumentException.class);
            rejects(() -> ContentPolicy.validatePatch("+++ b/path/id_rsa\n+data"), IllegalArgumentException.class);
            rejects(() -> ContentPolicy.validatePatch("+++ b/tokens.txt\n+-----BEGIN PRIVATE KEY-----"), IllegalArgumentException.class);
            rejects(() -> ContentPolicy.validatePatch("+api_key = 'abcdefghijk'"), IllegalArgumentException.class);
            rejects(() -> ContentPolicy.validatePatch("+auth sk-" + "a".repeat(30)), IllegalArgumentException.class);
        });
        check("bounds UTF-8 size and rejects hidden controls", () -> {
            rejects(() -> ContentPolicy.validatePatch("é".repeat(ContentPolicy.MAX_PATCH_BYTES / 2 + 1)), IllegalArgumentException.class);
            rejects(() -> ContentPolicy.validatePatch("\u001b[2J"), IllegalArgumentException.class);
            rejects(() -> ContentPolicy.validateMessage("hello\u202eexe"), IllegalArgumentException.class);
            rejects(() -> ContentPolicy.validateMessage("```text\nmessage\n```"), IllegalArgumentException.class);
            ContentPolicy.validateMessage("Fix visible state\n\nClear stale details after selection.");
        });
        check("large reviewed diffs fit the request without truncation", () -> {
            String patch = "+value = 1\n".repeat(20_000);
            require(patch.getBytes(StandardCharsets.UTF_8).length > 120 * 1024, "Fixture must exceed the old patch cap.");
            String prompt = PromptBuilder.build(patch);
            require(prompt.endsWith(patch), "Large request must preserve every selected line.");
            String largestPatch = "x".repeat(ContentPolicy.MAX_PATCH_BYTES);
            require(PromptBuilder.build(largestPatch).endsWith(largestPatch), "Instructions must fit beside a maximum-size patch.");
        });
        check("patch and request caps use UTF-8 bytes at the boundary", () -> {
            ContentPolicy.validatePatch("é".repeat(ContentPolicy.MAX_PATCH_BYTES / 2));
            rejects(() -> ContentPolicy.validatePatch("x".repeat(ContentPolicy.MAX_PATCH_BYTES + 1)), IllegalArgumentException.class);
            ContentPolicy.validatePrompt("é".repeat(ContentPolicy.MAX_PROMPT_BYTES / 2));
            rejects(() -> ContentPolicy.validatePrompt("é".repeat(ContentPolicy.MAX_PROMPT_BYTES / 2) + "x"), IllegalArgumentException.class);
            ContentPolicy.validateMessage("x".repeat(ContentPolicy.MAX_MESSAGE_BYTES));
            rejects(() -> ContentPolicy.validateMessage("x".repeat(ContentPolicy.MAX_MESSAGE_BYTES + 1)), IllegalArgumentException.class);
        });
        check("rejects shell and relative executable paths", () -> {
            rejects(() -> CodexExecutableLocator.validateNative(Path.of("codex.cmd"), true), IOException.class);
            rejects(() -> CodexExecutableLocator.validateNative(Path.of("codex"), false), IOException.class);
            Path folder = Files.createTempDirectory("codexcommit-locator-");
            try {
                Path wrapper = folder.resolve(CodexExecutableLocator.isWindows() ? "codex.exe" : "codex");
                Files.writeString(wrapper, "#!/bin/sh\necho fake\n");
                wrapper.toFile().setExecutable(true);
                rejects(() -> CodexExecutableLocator.validateNative(wrapper), IOException.class);
            } finally { delete(folder); }
        });
        check("PATH entries exclude current and relative directories", () -> {
            Path folder = Files.createTempDirectory("codexcommit-path-");
            try {
                Map<String, String> env = Map.of("PATH", ".;relative;" + folder);
                rejects(() -> CodexExecutableLocator.find("", env, folder, true), IOException.class);
            } finally { delete(folder); }
        });
        check("child environment rejects injected credentials and redirects", () -> {
            Map<String, String> env = CodexRunner.childEnvironment(Map.of("OPENAI_API_KEY", "synthetic", "OPENAI_BASE_URL", "http://invalid",
                    "CODEX_HOME", "untrusted", "HTTP_PROXY", "http://invalid", "PATH", ".;untrusted", "SystemRoot", "C:\\Windows"));
            require(!env.containsKey("OPENAI_API_KEY") && !env.containsKey("OPENAI_BASE_URL") && !env.containsKey("CODEX_HOME"), "No injected provider settings");
            require(!env.containsKey("PATH") && !env.containsKey("HTTP_PROXY"), "No inherited tools or proxy redirects");
            require(env.get("HOME").equals(System.getProperty("user.home")), "Saved auth uses the user's normal home");
        });
        check("npm nested native package resolves without executing a launcher", () -> {
            Path folder = Files.createTempDirectory("codexcommit-npm-");
            try {
                Path bin = Files.createDirectory(folder.resolve("bin"));
                Path cwd = Files.createDirectory(folder.resolve("project"));
                String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
                boolean arm = arch.contains("aarch") || arch.contains("arm64");
                Path nativeFile = bin.resolve("node_modules/@openai/codex/node_modules/@openai/codex-win32-"
                        + (arm ? "arm64" : "x64") + "/vendor/" + (arm ? "aarch64" : "x86_64") + "-pc-windows-msvc/bin/codex.exe");
                Files.createDirectories(nativeFile.getParent());
                Files.write(nativeFile, new byte[]{'M', 'Z', 0, 0});
                nativeFile.toFile().setExecutable(true);
                Files.writeString(bin.resolve("codex.cmd"), "do not execute this launcher");
                require(CodexExecutableLocator.find("", Map.of("PATH", bin.toString()), cwd, true).equals(nativeFile.toRealPath()),
                        "Nested npm binary was missed in favour of unrelated discovery.");
            } finally { delete(folder); }
        });
        check("audited profile closes tools and persistence", () -> {
            List<String> command = CodexRunner.command(Path.of("/native/codex"), Path.of("/private/work"), Path.of("/private/work/models.json"), "gpt-5.4");
            for (String argument : List.of("--ignore-user-config", "--ignore-rules", "--ephemeral", "--strict-config", "read-only",
                    "features.shell_tool=false", "features.view_image=false", "features.hooks=false", "features.apps=false", "features.plugins=false",
                    "features.skip_host_skill_discovery=true", "analytics.enabled=false", "history.persistence=\"none\"", "model_providers={}", "web_search=\"disabled\"")) {
                require(command.contains(argument), "Missing safety argument: " + argument);
            }
            require(command.get(command.size() - 1).equals("-"), "Prompt must use stdin");
            require(CodexRunner.modelCatalog("gpt-5.4").contains("\"apply_patch_tool_type\":null"), "Apply patch must be disabled");
            require(CodexRunner.modelCatalog("gpt-5.4").contains("\"experimental_supported_tools\":[]"), "Model tools must be empty");
        });
        check("model IDs stay data and retain the restricted tool profile", () -> {
            for (String model : List.of("gpt-5.4", "gpt-5.3-codex", "gpt-6.1-sol", "future-account-model")) {
                Map<?, ?> catalog = Json.object(Json.parse(CodexRunner.modelCatalog(model)));
                Map<?, ?> metadata = Json.object(((List<?>) catalog.get("models")).getFirst());
                require(metadata.get("slug").equals(model), "Catalog must match the chosen model exactly.");
                require(metadata.get("apply_patch_tool_type") == null && metadata.get("shell_type").equals("disabled"), "A model change enabled tools.");
                require(metadata.get("default_reasoning_level") == null && Boolean.FALSE.equals(metadata.get("support_verbosity")), "Do not force unsupported model parameters.");
            }
            for (String invalid : List.of("", "--config=x", "gpt\"bad", "gpt\nother", "x".repeat(129))) {
                rejects(() -> CodexRunner.validateModel(invalid), IllegalArgumentException.class);
            }
        });
        check("automatic model selection follows the CLI setting or its default", () -> withWorkspace(folder -> {
            Map<String, String> environment = CodexRunner.childEnvironment(System.getenv());
            require(CodexModelResolver.resolve(helper("model-config"), folder, environment, () -> false, 5000).equals("gpt-5.4"), "Saved model not selected.");
            require(CodexModelResolver.resolve(helper("model-default"), folder, environment, () -> false, 5000).equals("gpt-5.3-codex"), "CLI default not selected.");
        }));
        check("automatic model selection rejects private errors invalid IDs and oversized replies", () -> withWorkspace(folder -> {
            for (String mode : List.of("model-error", "model-injection", "model-overflow")) {
                try {
                    CodexModelResolver.resolve(helper(mode), folder, CodexRunner.childEnvironment(System.getenv()), () -> false, 5000);
                    throw new AssertionError("Invalid model response accepted.");
                } catch (IOException expected) {
                    require(!expected.getMessage().contains("SYNTHETIC_PRIVATE_CONFIG"), "Private CLI response was exposed.");
                }
            }
        }));
        check("automatic model selection honours timeout and cancellation", () -> withWorkspace(folder -> {
            Map<String, String> environment = CodexRunner.childEnvironment(System.getenv());
            rejects(() -> CodexModelResolver.resolve(helper("sleep"), folder, environment, () -> false, 300), IOException.class);
            long start = System.nanoTime();
            rejects(() -> CodexModelResolver.resolve(helper("sleep"), folder, environment,
                    () -> System.nanoTime() - start > 300_000_000, 5000), InterruptedException.class);
        }));
        check("local model JSON rejects duplicate keys malformed input and deep nesting", () -> {
            for (String invalid : List.of("{\"id\":1,\"id\":2}", "{\"model\":\"bad\\q\"}", "[1,]", "[true false]", "{\"a\":null,\"a\":1}", "[".repeat(34) + "]".repeat(34))) {
                rejects(() -> Json.parse(invalid), IOException.class);
            }
            require(Json.object(Json.parse("{\"model\":\"gpt\\u002d5.4\",\"unused\":[null,true,1.5]}")).get("model").equals("gpt-5.4"), "Valid escaped model failed.");
        });
        check("child stdin is exact, private cwd and exit captured", () -> withWorkspace(folder -> {
            var result = child("echo", "only this synthetic selection", folder, () -> false, 5000, 4096);
            require(result.stdout().equals("only this synthetic selection"), "Child input must be exact");
            require(result.exitCode() == 0, "Echo child must succeed");
            var working = child("cwd", "", folder, () -> false, 5000, 4096);
            require(Path.of(working.stdout()).toRealPath().equals(folder.toRealPath()), "Working directory must be isolated");
        }));
        check("child nonzero errors do not expose stderr", () -> withWorkspace(folder -> {
            var result = child("failure", "", folder, () -> false, 5000, 4096);
            require(result.exitCode() == 23 && result.stdout().isEmpty(), "Only return bounded stdout and exit code");
        }));
        check("stdout overflow terminates bounded child", () -> withWorkspace(folder -> {
            rejects(() -> child("overflow", "", folder, () -> false, 5000, 1024), IOException.class);
        }));
        check("stderr overflow and invalid UTF-8 fail closed", () -> withWorkspace(folder -> {
            rejects(() -> child("error-overflow", "", folder, () -> false, 5000, 1024), IOException.class);
            rejects(() -> child("invalid-utf8", "", folder, () -> false, 5000, 1024), IOException.class);
        }));
        check("timeout terminates process", () -> withWorkspace(folder -> {
            long start = System.nanoTime();
            rejects(() -> child("sleep", "", folder, () -> false, 300, 4096), IOException.class);
            require((System.nanoTime() - start) / 1_000_000 < 5000, "Timeout must be bounded");
        }));
        check("cancellation terminates process", () -> withWorkspace(folder -> {
            long start = System.nanoTime();
            rejects(() -> child("sleep", "", folder, () -> (System.nanoTime() - start) > 350_000_000, 5000, 4096), InterruptedException.class);
            require((System.nanoTime() - start) / 1_000_000 < 5000, "Cancellation must be prompt");
        }));
        check("cancellation kills observed descendants", () -> withWorkspace(folder -> {
            Path pid = folder.resolve("child.pid");
            rejects(() -> child("spawn", pid.toString(), folder, () -> Files.exists(pid), 8000, 4096), InterruptedException.class);
            long childPid = Long.parseLong(Files.readString(pid).strip());
            for (int i = 0; i < 30 && ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false); i++) Thread.sleep(100);
            require(!ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false), "Descendant must be terminated");
        }));
        passed += SecurityAuditTests.run();
        System.out.println("Core tests: " + passed + " passed, 0 failed, " + SecurityAuditTests.skippedBranches()
                + " optional branches skipped. All fixtures are synthetic; no model request was made.");
    }

    private static CodexRunner.ProcessResult child(String mode, String input, Path folder, java.util.function.BooleanSupplier cancelled,
                                                    long timeout, int limit) throws Exception {
        return CodexRunner.run(helper(mode), input, folder, CodexRunner.childEnvironment(System.getenv()), cancelled, timeout, limit);
    }

    static List<String> helper(String mode) {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", CodexExecutableLocator.isWindows() ? "java.exe" : "java").toString();
        String classpath = java.util.Arrays.stream(System.getProperty("java.class.path").split(Pattern.quote(System.getProperty("path.separator"))))
                .map(p -> Path.of(p).toAbsolutePath().toString()).collect(java.util.stream.Collectors.joining(System.getProperty("path.separator")));
        return List.of(javaExecutable, "-cp", classpath, "dev.codexcommit.core.FakeChild", mode);
    }

    private static void check(String name, CheckedRunnable test) throws Exception { test.run(); passed++; System.out.println("PASS " + name); }
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(CheckedRunnable test, Class<? extends Exception> expected) throws Exception {
        try { test.run(); } catch (Exception ex) { if (expected.isInstance(ex)) return; throw ex; }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }
    private static void withWorkspace(WorkspaceTask task) throws Exception {
        Path folder = Files.createTempDirectory("codexcommit-test-");
        try { task.run(folder); } finally { delete(folder); }
    }
    static void delete(Path folder) throws IOException {
        try (var paths = Files.walk(folder)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
    }
    @FunctionalInterface private interface CheckedRunnable { void run() throws Exception; }
    @FunctionalInterface private interface WorkspaceTask { void run(Path path) throws Exception; }
}
