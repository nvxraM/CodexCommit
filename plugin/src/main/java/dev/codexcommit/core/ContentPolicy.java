package dev.codexcommit.core;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/** Conservative local checks; these patterns cannot establish that a diff contains no secrets. */
public final class ContentPolicy {
    public static final int MAX_PATCH_BYTES = 512 * 1024;
    public static final int MAX_PROMPT_BYTES = MAX_PATCH_BYTES + 8 * 1024;
    public static final int MAX_MESSAGE_BYTES = 16 * 1024;

    private static final Pattern SENSITIVE_PATH = Pattern.compile(
            "(?im)^(?:diff --git .*|--- .*|\\+\\+\\+ .*|Index: .*|File: .*|Path: .*)"
                    + "(?:[\\\\/]|^)(?:\\.env(?:\\.[^/\\\\\\s]*)?|id_(?:rsa|dsa|ecdsa|ed25519)(?:\\.pub)?|"
                    + "credentials(?:\\.[^/\\\\\\s]*)?|auth\\.json|secrets?(?:\\.[^/\\\\\\s]*)?|"
                    + "[^/\\\\\\s]*\\.(?:pem|p12|pfx|key|keystore))(?=\\s|$|\")");
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("-----BEGIN (?:[A-Z ]*PRIVATE KEY|OPENSSH PRIVATE KEY)-----"),
            Pattern.compile("\\b(?:sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b"),
            Pattern.compile("\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b"),
            Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{12,}\\b"),
            Pattern.compile("(?i)\\b(?:Bearer\\s+)[A-Za-z0-9._~+/-]{20,}={0,2}"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\b"),
            Pattern.compile("(?im)^[-+ ]?\\s*[\"']?(?:api[_-]?key|access[_-]?token|client[_-]?secret|password|passwd|private[_-]?key)[\"']?\\s*[:=]\\s*[\"']?[^\\s\"']{8,}")
    );

    private ContentPolicy() { }

    public static void validatePatch(String patch) {
        requireText(patch, MAX_PATCH_BYTES, "The selected diff is empty or exceeds the 512 KiB limit. Select fewer changes.");
        rejectControls(patch);
        if (SENSITIVE_PATH.matcher(patch).find()) {
            throw new IllegalArgumentException("A selected path appears to contain credentials or key material. Exclude it before generating.");
        }
        rejectSecrets(patch);
    }

    public static void validateMessage(String message) {
        requireText(message, MAX_MESSAGE_BYTES, "Codex returned an empty or oversized draft.");
        rejectControls(message);
        rejectSecrets(message);
        if (message.startsWith("```") || message.lines().count() > 100) {
            throw new IllegalArgumentException("Codex returned an unexpected draft format. Review the selection and try again.");
        }
    }

    static void validatePrompt(String prompt) {
        requireText(prompt, MAX_PROMPT_BYTES, "The generation prompt exceeds the 520 KiB limit.");
        rejectControls(prompt);
        rejectSecrets(prompt);
    }

    private static void requireText(String value, int maxBytes, String message) {
        if (value == null || value.isBlank() || value.length() > maxBytes
                || value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void rejectSecrets(String value) {
        if (SECRET_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(value).find())) {
            throw new IllegalArgumentException("Possible credentials detected. Remove them from the selection before generating. Pattern checks are incomplete.");
        }
    }

    private static void rejectControls(String value) {
        if (value.codePoints().anyMatch(c -> (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')
                || (c >= 0x202a && c <= 0x202e) || (c >= 0x2066 && c <= 0x2069))) {
            throw new IllegalArgumentException("The text contains unsupported control or direction-changing characters.");
        }
    }
}
