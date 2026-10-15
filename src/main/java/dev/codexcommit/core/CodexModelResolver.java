package dev.codexcommit.core;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Reads model selection through the local CLI protocol. Never starts a Codex conversation or sends source text. */
final class CodexModelResolver {
    static String resolve(List<String> command, Path workspace, Map<String, String> environment,
                          BooleanSupplier cancelled, long timeoutMillis) throws IOException, InterruptedException {
        if (cancelled.getAsBoolean()) throw new InterruptedException("Cancelled.");
        ProcessBuilder builder = new ProcessBuilder(command).directory(workspace.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear();
        builder.environment().putAll(environment);
        Process process = builder.start();
        var replies = new ArrayBlockingQueue<String>(128);
        AtomicReference<IOException> failure = new AtomicReference<>();
        Thread reader = Thread.ofVirtual().name("CodexCommit model selection").start(() -> {
            try (var input = new BufferedReader(new InputStreamReader(new LimitedInput(process.getInputStream()),
                    StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)))) {
                String line;
                while ((line = input.readLine()) != null) {
                    if (!replies.offer(line)) throw new IOException("Too many Codex model selection responses.");
                }
            } catch (IOException exception) { failure.set(new IOException("Cannot read the Codex model selection.")); }
        });
        List<ProcessHandle> descendants = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        try {
            send(process, "{\"id\":1,\"method\":\"initialize\",\"params\":{\"clientInfo\":{\"name\":\"CodexCommit\",\"version\":\"1\"},\"capabilities\":{\"experimentalApi\":true}}}");
            reply(process, replies, failure, 1, deadline, cancelled, descendants);
            send(process, "{\"method\":\"initialized\",\"params\":{}}");
            send(process, "{\"id\":2,\"method\":\"config/read\",\"params\":{\"includeLayers\":false}}");
            Map<?, ?> config = Json.object(reply(process, replies, failure, 2, deadline, cancelled, descendants).get("config"));
            Object selected = config.get("model");
            if (selected instanceof String model && !model.isBlank()) return checked(model);
            if (selected != null) throw new IOException("Codex returned an invalid saved model.");
            send(process, "{\"id\":3,\"method\":\"model/list\",\"params\":{\"limit\":100}}");
            Map<?, ?> models = reply(process, replies, failure, 3, deadline, cancelled, descendants);
            if (models.get("data") instanceof List<?> data) {
                for (Object value : data) {
                    Map<?, ?> model = Json.object(value);
                    if (Boolean.TRUE.equals(model.get("isDefault")) && model.get("model") instanceof String name) return checked(name);
                }
            }
            throw new IOException("Codex did not report a default model. Select a model in Codex and try again.");
        } finally {
            CodexRunner.terminate(process, descendants, System.nanoTime() + TimeUnit.SECONDS.toNanos(3));
            try { process.getOutputStream().close(); } catch (IOException ignored) { }
            try { process.getInputStream().close(); } catch (IOException ignored) { }
            reader.interrupt();
            boolean interrupted = Thread.interrupted();
            try { reader.join(1000); } catch (InterruptedException ignored) { interrupted = true; }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static String checked(String model) throws IOException {
        try { return CodexRunner.validateModel(model); }
        catch (IllegalArgumentException ignored) { throw new IOException("Codex returned an invalid model ID."); }
    }

    private static void send(Process process, String message) throws IOException {
        process.getOutputStream().write((message + "\n").getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().flush();
    }

    private static Map<?, ?> reply(Process process, ArrayBlockingQueue<String> replies, AtomicReference<IOException> failure,
                                   int id, long deadline, BooleanSupplier cancelled, List<ProcessHandle> descendants)
            throws IOException, InterruptedException {
        while (System.nanoTime() < deadline) {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new InterruptedException("Cancelled.");
            process.descendants().forEach(descendants::add);
            String line = replies.poll(50, TimeUnit.MILLISECONDS);
            if (line != null) {
                Map<?, ?> message = Json.object(Json.parse(line));
                if (message.get("id") instanceof Number number && number.doubleValue() == id) {
                    if (message.containsKey("error")) throw new IOException("Codex could not resolve its current model.");
                    return Json.object(message.get("result"));
                }
            }
            if (failure.get() != null) throw failure.get();
            if (!process.isAlive() && replies.isEmpty()) throw new IOException("Codex stopped before it reported its current model.");
        }
        throw new IOException("Codex model selection timed out.");
    }

    private static final class LimitedInput extends FilterInputStream {
        private int remaining = 1024 * 1024;
        LimitedInput(InputStream input) { super(input); }
        @Override public int read() throws IOException {
            int value = in.read();
            if (value >= 0 && --remaining < 0) throw new IOException("Oversized model selection response.");
            return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = in.read(bytes, offset, Math.min(length, remaining + 1));
            if (count > 0 && (remaining -= count) < 0) throw new IOException("Oversized model selection response.");
            return count;
        }
    }
}
