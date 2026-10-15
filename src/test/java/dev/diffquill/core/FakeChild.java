package dev.diffquill.core;

import java.nio.file.Files;
import java.nio.file.Path;

/** Local Java helper, never a shell or a real Codex process. */
public final class FakeChild {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "echo" -> System.out.write(System.in.readAllBytes());
            case "cwd" -> System.out.print(Path.of("").toAbsolutePath());
            case "failure" -> { System.err.print("private synthetic error, must not be surfaced"); System.exit(23); }
            case "overflow" -> { for (int i = 0; i < 100000; i++) System.out.print("xxxxxxxxxxxxxxxx"); }
            case "error-overflow" -> { for (int i = 0; i < 100000; i++) System.err.print("xxxxxxxxxxxxxxxx"); }
            case "invalid-utf8" -> System.out.write(new byte[] {(byte) 0xc3, (byte) 0x28});
            case "environment-audit" -> {
                if (System.getenv().values().stream().anyMatch(value -> value.startsWith("DQ_CANARY_"))) System.exit(27);
                System.out.print("NO_CANARIES");
            }
            case "stderr-canary" -> {
                System.err.write(System.in.readAllBytes());
                System.out.print("Synthetic safe draft");
            }
            case "sleep" -> Thread.sleep(60000);
            case "spawn" -> {
                Path pid = Path.of(new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                Process descendant = new ProcessBuilder(CoreTests.helper("sleep")).start();
                Files.writeString(pid, Long.toString(descendant.pid()));
                Thread.sleep(60000);
            }
            default -> throw new IllegalArgumentException("Unknown synthetic fixture");
        }
    }
}
